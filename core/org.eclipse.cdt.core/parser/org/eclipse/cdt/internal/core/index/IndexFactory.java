/*******************************************************************************
 * Copyright (c) 2007 Wind River Systems, Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Markus Schorn - initial API and implementation
 *    Andrew Ferguson (Symbian)
 *******************************************************************************/ 

package org.eclipse.cdt.internal.core.index;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.cdt.core.CCorePlugin;
import org.eclipse.cdt.core.index.IIndex;
import org.eclipse.cdt.core.index.IIndexManager;
import org.eclipse.cdt.core.model.CoreModel;
import org.eclipse.cdt.core.model.ICProject;
import org.eclipse.cdt.core.parser.util.ArrayUtil;
import org.eclipse.cdt.core.settings.model.ICConfigurationDescription;
import org.eclipse.cdt.internal.core.index.provider.IndexProviderManager;
import org.eclipse.cdt.internal.core.pdom.PDOMManager;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;

/**
 * Class that creates indexes based on pdoms
 * @since 4.0
 */
public class IndexFactory {
	private static final int ADD_DEPENDENCIES = IIndexManager.ADD_DEPENDENCIES;
	private static final int ADD_DEPENDENT = IIndexManager.ADD_DEPENDENT;
	private static final int SKIP_PROVIDED = IIndexManager.SKIP_PROVIDED;

	private PDOMManager fPDOMManager;
	
	public IndexFactory(PDOMManager manager) {
		fPDOMManager= manager;
	}
	
	public IIndex getIndex(ICProject[] projects, int options) throws CoreException {
		projects = (ICProject[]) ArrayUtil.removeNulls(ICProject.class, projects);
		
		boolean addDependencies= (options & ADD_DEPENDENCIES) != 0;
		boolean addDependent=    (options & ADD_DEPENDENT) != 0;
		boolean skipProvided= (options & SKIP_PROVIDED) != 0;
		
		IndexProviderManager m = ((PDOMManager)CCorePlugin.getPDOMManager()).getIndexProviderManager();
		HashMap map= new HashMap();
		Collection selectedProjects= getProjects(projects, addDependencies, addDependent, map, new Integer(1));
		
		HashMap fragments= new LinkedHashMap();
		for (Iterator iter = selectedProjects.iterator(); iter.hasNext(); ) {
			ICProject cproject = (ICProject) iter.next();
			IWritableIndexFragment pdom= (IWritableIndexFragment) fPDOMManager.getPDOM(cproject);
			if (pdom != null) {
				fragments.put(pdom.getProperty(IIndexFragment.PROPERTY_FRAGMENT_ID), pdom);
				
				if(!skipProvided) {
					ICConfigurationDescription activeCfg= CoreModel.getDefault().getProjectDescription(cproject.getProject()).getActiveConfiguration();
					IIndexFragment[] pFragments= m.getProvidedIndexFragments(activeCfg);
					for(int i=0; i<pFragments.length; i++) {
						fragments.put(pFragments[i].getProperty(IIndexFragment.PROPERTY_FRAGMENT_ID), pFragments[i]);
					}
				}
			}
		}
		if (fragments.isEmpty()) {
			return EmptyCIndex.INSTANCE;
		}
		
		int primaryFragmentCount= fragments.size();
		
		if (!addDependencies) {
			projects= (ICProject[]) selectedProjects.toArray(new ICProject[selectedProjects.size()]);
			selectedProjects.clear();
			// don't clear the map, so projects are not selected again
			selectedProjects= getProjects(projects, true, false, map, new Integer(2));
			for (Iterator iter = selectedProjects.iterator(); iter.hasNext(); ) {
				ICProject cproject = (ICProject) iter.next();
				IWritableIndexFragment pdom= (IWritableIndexFragment) fPDOMManager.getPDOM(cproject);
				if (pdom != null) {
					fragments.put(pdom.getProperty(IIndexFragment.PROPERTY_FRAGMENT_ID), pdom);
				}
				if(!skipProvided) {
					ICConfigurationDescription activeCfg= CoreModel.getDefault().getProjectDescription(cproject.getProject()).getActiveConfiguration();
					IIndexFragment[] pFragments= m.getProvidedIndexFragments(activeCfg);
					for(int i=0; i<pFragments.length; i++) {
						fragments.put(pFragments[i].getProperty(IIndexFragment.PROPERTY_FRAGMENT_ID), pFragments[i]);
					}
				}
			}
		}
		
		Collection pdoms= fragments.values();
		return new CIndex((IIndexFragment[]) pdoms.toArray(new IIndexFragment[pdoms.size()]), primaryFragmentCount); 
	}

	private Collection getProjects(ICProject[] projects, boolean addDependencies, boolean addDependent, HashMap map, Integer markWith) {
		List projectsToSearch= new ArrayList();
		
		for (int i = 0; i < projects.length; i++) {
			ICProject cproject = projects[i];
			IProject project= cproject.getProject();
			checkAddProject(project, map, projectsToSearch, markWith);
			projectsToSearch.add(project);
		}
		
		if (addDependencies || addDependent) {
			for (int i=0; i<projectsToSearch.size(); i++) {
				IProject project= (IProject) projectsToSearch.get(i);
				IProject[] nextLevel;
				try {
					if (addDependencies) {
						nextLevel = project.getReferencedProjects();
						for (int j = 0; j < nextLevel.length; j++) {
							checkAddProject(nextLevel[j], map, projectsToSearch, markWith);
						}
					}
					if (addDependent) {
						nextLevel= project.getReferencingProjects();
						for (int j = 0; j < nextLevel.length; j++) {
							checkAddProject(nextLevel[j], map, projectsToSearch, markWith);
						}
					}
				} catch (CoreException e) {
					// silently ignore
					map.put(project, new Integer(0));
				}
			}
		}
		
		CoreModel cm= CoreModel.getDefault();
		Collection result= new ArrayList();
		for (Iterator iter= map.entrySet().iterator(); iter.hasNext(); ) {
			Map.Entry entry= (Map.Entry) iter.next();
			if (entry.getValue() == markWith) {
				ICProject cproject= cm.create((IProject) entry.getKey());
				if (cproject != null) {
					result.add(cproject);
				}
			}
		}
		return result;
	}

	private void checkAddProject(IProject project, HashMap map, List projectsToSearch, Integer markWith) {
		if (map.get(project) == null) {
			if (project.isOpen()) {
				map.put(project, markWith);
				projectsToSearch.add(project);
			}
			else {
				map.put(project, new Integer(0));
			}
		}
	}

	public IWritableIndex getWritableIndex(ICProject project) throws CoreException {
// mstodo to support dependent projects: Collection selectedProjects= getSelectedProjects(new ICProject[]{project}, false);
		
		Collection selectedProjects= Collections.singleton(project);
		Map readOnlyFrag= new LinkedHashMap();
		Map fragments= new LinkedHashMap();
		for (Iterator iter = selectedProjects.iterator(); iter.hasNext(); ) {
			ICProject p = (ICProject) iter.next();
			IWritableIndexFragment pdom= (IWritableIndexFragment) fPDOMManager.getPDOM(p);
			if (pdom != null) {
				fragments.put(pdom.getProperty(IIndexFragment.PROPERTY_FRAGMENT_ID), pdom);
				IndexProviderManager m = ((PDOMManager)CCorePlugin.getPDOMManager()).getIndexProviderManager();
				ICConfigurationDescription activeCfg= CoreModel.getDefault().getProjectDescription(p.getProject()).getActiveConfiguration();
				IIndexFragment[] pFragments= m.getProvidedIndexFragments(activeCfg);
				for(int i=0; i<pFragments.length; i++) {
					readOnlyFrag.put(pFragments[i].getProperty(IIndexFragment.PROPERTY_FRAGMENT_ID), pFragments[i]);
				}
			}
		}
		
		selectedProjects= getProjects(new ICProject[] {project}, true, false, new HashMap(), new Integer(1));		
		selectedProjects.remove(project);
		
		for (Iterator iter = selectedProjects.iterator(); iter.hasNext(); ) {
			ICProject cproject = (ICProject) iter.next();
			IWritableIndexFragment pdom= (IWritableIndexFragment) fPDOMManager.getPDOM(cproject);
			if (pdom != null) {
				readOnlyFrag.put(pdom.getProperty(IIndexFragment.PROPERTY_FRAGMENT_ID), pdom);
			}
		}
				
		if (fragments.isEmpty()) {
			throw new CoreException(CCorePlugin.createStatus(
					MessageFormat.format(Messages.IndexFactory_errorNoSuchPDOM0, new Object[]{project.getElementName()})));
		}
		
		Collection pdoms= fragments.values();
		Collection roPdoms= readOnlyFrag.values();
		return new WritableCIndex((IWritableIndexFragment[]) pdoms.toArray(new IWritableIndexFragment[pdoms.size()]),
				(IIndexFragment[]) roPdoms.toArray(new IIndexFragment[roPdoms.size()]) );
	}
}
