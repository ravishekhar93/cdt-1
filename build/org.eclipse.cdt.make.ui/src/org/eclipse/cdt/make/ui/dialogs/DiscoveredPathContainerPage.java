/**********************************************************************
 * Copyright (c) 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors: 
 * IBM - Initial API and implementation
 **********************************************************************/
package org.eclipse.cdt.make.ui.dialogs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;

import org.eclipse.cdt.core.model.CModelException;
import org.eclipse.cdt.core.model.CoreModel;
import org.eclipse.cdt.core.model.ICProject;
import org.eclipse.cdt.core.model.IContainerEntry;
import org.eclipse.cdt.core.model.IPathEntry;
import org.eclipse.cdt.core.model.IPathEntryContainer;
import org.eclipse.cdt.internal.ui.CPluginImages;
import org.eclipse.cdt.internal.ui.util.PixelConverter;
import org.eclipse.cdt.internal.ui.wizards.dialogfields.DialogField;
import org.eclipse.cdt.internal.ui.wizards.dialogfields.IDialogFieldListener;
import org.eclipse.cdt.internal.ui.wizards.dialogfields.ITreeListAdapter;
import org.eclipse.cdt.internal.ui.wizards.dialogfields.LayoutUtil;
import org.eclipse.cdt.internal.ui.wizards.dialogfields.TreeListDialogField;
import org.eclipse.cdt.make.core.MakeCorePlugin;
import org.eclipse.cdt.make.core.scannerconfig.IDiscoveredPathManager.IDiscoveredPathInfo;
import org.eclipse.cdt.make.internal.core.scannerconfig.DiscoveredPathContainer;
import org.eclipse.cdt.make.internal.core.scannerconfig.ScannerInfoCollector;
import org.eclipse.cdt.make.internal.core.scannerconfig.util.ScannerConfigUtil;
import org.eclipse.cdt.make.internal.core.scannerconfig.util.SymbolEntry;
import org.eclipse.cdt.make.internal.ui.MakeUIPlugin;
import org.eclipse.cdt.make.internal.ui.scannerconfig.DiscoveredElement;
import org.eclipse.cdt.make.internal.ui.scannerconfig.DiscoveredElementLabelProvider;
import org.eclipse.cdt.make.internal.ui.scannerconfig.DiscoveredElementSorter;
import org.eclipse.cdt.ui.wizards.ICPathContainerPage;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.widgets.Composite;

/**
 * A dialog page to manage discovered scanner configuration
 * 
 * @author vhirsl
 */
public class DiscoveredPathContainerPage extends WizardPage	implements ICPathContainerPage {
	private static final String PREFIX = "DiscoveredScannerConfigurationContainerPage"; //$NON-NLS-1$

	private static final String DISC_COMMON_PREFIX = "ManageScannerConfigDialogCommon"; //$NON-NLS-1$
	private static final String UP = DISC_COMMON_PREFIX + ".discoveredGroup.buttons.up.label"; //$NON-NLS-1$
	private static final String DOWN = DISC_COMMON_PREFIX + ".discoveredGroup.buttons.down.label"; //$NON-NLS-1$
	private static final String DISABLE = DISC_COMMON_PREFIX + ".discoveredGroup.buttons.disable.label"; //$NON-NLS-1$
	private static final String ENABLE = DISC_COMMON_PREFIX + ".discoveredGroup.buttons.enable.label"; //$NON-NLS-1$
	private static final String DELETE = DISC_COMMON_PREFIX + ".discoveredGroup.buttons.delete.label"; //$NON-NLS-1$
	
	private static final String CONTAINER_LABEL = PREFIX + ".title"; //$NON-NLS-1$
	private static final String CONTAINER_DESCRIPTION = PREFIX + ".description"; //$NON-NLS-1$
	private static final String CONTAINER_LIST_LABEL = PREFIX + ".list.title"; //$NON-NLS-1$
	
	private final int IDX_UP = 0;
	private final int IDX_DOWN = 1;
	private final int IDX_ENABLE = 2;
	private final int IDX_DISABLE = 3;

	private final int IDX_DELETE = 5;

	private static final int DISC_UP = 0;
	private static final int DISC_DOWN = 1;
	
	private static final int DO_DISABLE = 0;
	private static final int DO_ENABLE = 1;

	private ICProject fCProject;
	private IContainerEntry fPathEntry;

	private TreeListDialogField fDiscoveredContainerList;
	private boolean dirty;
	
	public DiscoveredPathContainerPage() {
		super("DiscoveredScannerConfigurationContainerPage"); //$NON-NLS-1$

		setTitle(MakeUIPlugin.getResourceString(CONTAINER_LABEL));
		setDescription(MakeUIPlugin.getResourceString(CONTAINER_DESCRIPTION));
		setImageDescriptor(CPluginImages.DESC_WIZBAN_ADD_LIBRARY);

		String[] buttonLabels = new String[]{
				/* IDX_UP */	MakeUIPlugin.getResourceString(UP),
				/* IDX_DOWN */	MakeUIPlugin.getResourceString(DOWN),
				/* IDX_ENABLE */MakeUIPlugin.getResourceString(ENABLE),
				/* IDX_DISABLE */MakeUIPlugin.getResourceString(DISABLE),
				null,
				/* IDX_DELETE */MakeUIPlugin.getResourceString(DELETE),
		};

		DiscoveredContainerAdapter adapter = new DiscoveredContainerAdapter();

		fDiscoveredContainerList = new TreeListDialogField(adapter, buttonLabels, new DiscoveredElementLabelProvider());
		fDiscoveredContainerList.setDialogFieldListener(adapter);
		fDiscoveredContainerList.setLabelText(MakeUIPlugin.getResourceString(CONTAINER_LIST_LABEL)); //$NON-NLS-1$

		fDiscoveredContainerList.setViewerSorter(new DiscoveredElementSorter());
		dirty = false;
	}
	/* (non-Javadoc)
	 * @see org.eclipse.cdt.ui.wizards.ICPathContainerPage#initialize(org.eclipse.cdt.core.model.ICProject, org.eclipse.cdt.core.model.IPathEntry[])
	 */
	public void initialize(ICProject project, IPathEntry[] currentEntries) {
		fCProject = project;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.cdt.ui.wizards.ICPathContainerPage#finish()
	 */
	public boolean finish() {
		if (!dirty) {
			return true;
		}
		IDiscoveredPathInfo info;
		try {
			info = MakeCorePlugin.getDefault().
					getDiscoveryManager().getDiscoveredInfo(fCProject.getProject());
			
			LinkedHashMap includes = new LinkedHashMap();
			LinkedHashMap symbols = new LinkedHashMap();
			
			DiscoveredElement container = (DiscoveredElement) fDiscoveredContainerList.getElement(0);
			if (container != null && container.getEntryKind() == DiscoveredElement.CONTAINER) {
				Object[] cChildren = container.getChildren();
				if (cChildren != null) {
					for (int i = 0; i < cChildren.length; ++i) {
						DiscoveredElement group = (DiscoveredElement) cChildren[i];
						switch (group.getEntryKind()) {
							case DiscoveredElement.PATHS_GROUP: {
								// get the include paths
								Object[] gChildren = group.getChildren();
								if (gChildren != null) {
									for (int j = 0; j < gChildren.length; ++j) {
										DiscoveredElement include = (DiscoveredElement) gChildren[j];
										includes.put(include.getEntry(), Boolean.valueOf(include.isRemoved()));
									}
								}
							}
							break;
							case DiscoveredElement.SYMBOLS_GROUP: {
								// get the symbol definitions
								Object[] gChildren = group.getChildren();
								if (gChildren != null) {
									for (int j = 0; j < gChildren.length; ++j) {
										DiscoveredElement symbol = (DiscoveredElement) gChildren[j];
										ScannerConfigUtil.scAddSymbolString2SymbolEntryMap(symbols, symbol.getEntry(), !symbol.isRemoved());
									}
								}
							}
							break;
						}
					}
				}
			}
			info.setIncludeMap(includes);
			info.setSymbolMap(symbols);
			
			try {
				// update scanner configuration
				MakeCorePlugin.getDefault().getDiscoveryManager().updateDiscoveredInfo(info);
				return true;
			} catch (CoreException e) {
				MakeCorePlugin.log(e);
			}
		} catch (CoreException e) {
			MakeCorePlugin.log(e);
		}
		return false;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.cdt.ui.wizards.ICPathContainerPage#getContainerEntries()
	 */
	public IPathEntry[] getContainerEntries() {
		return new IPathEntry[] { fPathEntry }; 
	}

	/* (non-Javadoc)
	 * @see org.eclipse.cdt.ui.wizards.ICPathContainerPage#setSelection(org.eclipse.cdt.core.model.IPathEntry)
	 */
	public void setSelection(IPathEntry containerEntry) {
		if (containerEntry != null) {
			if (containerEntry.getEntryKind() == IPathEntry.CDT_CONTAINER) {
				fPathEntry = (IContainerEntry) containerEntry;
			}
		}
		else {
			fPathEntry = CoreModel.newContainerEntry(DiscoveredPathContainer.CONTAINER_ID);		
		}
		if (fPathEntry != null) {
			DiscoveredElement element = populateDiscoveredElements(fPathEntry);
			ArrayList elements = new ArrayList();
			elements.add(element);
			fDiscoveredContainerList.addElements(elements);
		}
	}

	/**
	 * @param pathEntry
	 * @return
	 */
	private DiscoveredElement populateDiscoveredElements(IContainerEntry pathEntry) {
		IDiscoveredPathInfo info;
		DiscoveredElement container = null;
		try {
			info = MakeCorePlugin.getDefault().
					getDiscoveryManager().getDiscoveredInfo(fCProject.getProject());
			container = DiscoveredElement.createNew(null, fCProject.getProject(), null,
					DiscoveredElement.CONTAINER, false, false);
			try {
				IPathEntryContainer peContainer = CoreModel.getPathEntryContainer(pathEntry.getPath(), fCProject);
				if (peContainer != null) {
					container.setEntry(peContainer.getDescription());
				}
				// get include paths
				LinkedHashMap paths = info.getIncludeMap();
				for (Iterator i = paths.keySet().iterator(); i.hasNext(); ) {
					String include = (String) i.next();
					Boolean removed = (Boolean) paths.get(include);
					removed = (removed == null) ? Boolean.FALSE : removed;
					DiscoveredElement.createNew(container, fCProject.getProject(), include, 
							DiscoveredElement.INCLUDE_PATH, removed.booleanValue(), false);
				}
				// get defined symbols 
				LinkedHashMap symbols = info.getSymbolMap();
				for (Iterator i = symbols.keySet().iterator(); i.hasNext(); ) {
					String symbol = (String) i.next();
					SymbolEntry se = (SymbolEntry) symbols.get(symbol);
					for (Iterator j = se.getActiveRaw().iterator(); j.hasNext();) {
						String value = (String) j.next();
						DiscoveredElement.createNew(container, fCProject.getProject(), value, 
								DiscoveredElement.SYMBOL_DEFINITION, false, false);
					}
					for (Iterator j = se.getRemovedRaw().iterator(); j.hasNext();) {
						String value = (String) j.next();
						DiscoveredElement.createNew(container, fCProject.getProject(), value, 
								DiscoveredElement.SYMBOL_DEFINITION, true, false);
					}
				}
			} catch (CModelException e) {
				MakeUIPlugin.log(e.getStatus());
			}
		} catch (CoreException e) {
			MakeUIPlugin.log(e);
		}
		return container;
	}
	/* (non-Javadoc)
	 * @see org.eclipse.jface.dialogs.IDialogPage#createControl(org.eclipse.swt.widgets.Composite)
	 */
	public void createControl(Composite parent) {
		PixelConverter converter = new PixelConverter(parent);

		Composite composite = new Composite(parent, SWT.NONE);

		LayoutUtil.doDefaultLayout(composite, new DialogField[]{fDiscoveredContainerList}, true);
		LayoutUtil.setHorizontalGrabbing(fDiscoveredContainerList.getTreeControl(null));

		int buttonBarWidth = converter.convertWidthInCharsToPixels(24);
		fDiscoveredContainerList.setButtonsMinWidth(buttonBarWidth);

		fDiscoveredContainerList.getTreeViewer().addFilter(new ViewerFilter() {
			public boolean select(Viewer viewer, Object parentElement, Object element) {
				if (element instanceof DiscoveredElement) {
					DiscoveredElement elem = (DiscoveredElement) element;
					switch (elem.getEntryKind()) {
						case DiscoveredElement.PATHS_GROUP:
						case DiscoveredElement.SYMBOLS_GROUP:
							return elem.getChildren().length != 0;
					}
				}
				return true;
			}
		});

		setControl(composite);
	}

	/**
	 * @author vhirsl
	 */
	private class DiscoveredContainerAdapter implements IDialogFieldListener, ITreeListAdapter {
		private final Object[] EMPTY_ARR = new Object[0];

		// ---------- IDialogFieldListener --------
		/* (non-Javadoc)
		 * @see org.eclipse.cdt.internal.ui.wizards.dialogfields.IDialogFieldListener#dialogFieldChanged(org.eclipse.cdt.internal.ui.wizards.dialogfields.DialogField)
		 */
		public void dialogFieldChanged(DialogField field) {
			// TODO Auto-generated method stub
			
		}

		// -------- IListAdapter --------
		/* (non-Javadoc)
		 * @see org.eclipse.cdt.internal.ui.wizards.dialogfields.ITreeListAdapter#customButtonPressed(org.eclipse.cdt.internal.ui.wizards.dialogfields.TreeListDialogField, int)
		 */
		public void customButtonPressed(TreeListDialogField field, int index) {
			containerPageCustomButtonPressed(field, index);
		}

		/* (non-Javadoc)
		 * @see org.eclipse.cdt.internal.ui.wizards.dialogfields.ITreeListAdapter#selectionChanged(org.eclipse.cdt.internal.ui.wizards.dialogfields.TreeListDialogField)
		 */
		public void selectionChanged(TreeListDialogField field) {
			containerPageSelectionChanged(field);
		}

		/* (non-Javadoc)
		 * @see org.eclipse.cdt.internal.ui.wizards.dialogfields.ITreeListAdapter#doubleClicked(org.eclipse.cdt.internal.ui.wizards.dialogfields.TreeListDialogField)
		 */
		public void doubleClicked(TreeListDialogField field) {
			// TODO Auto-generated method stub

		}

		/* (non-Javadoc)
		 * @see org.eclipse.cdt.internal.ui.wizards.dialogfields.ITreeListAdapter#keyPressed(org.eclipse.cdt.internal.ui.wizards.dialogfields.TreeListDialogField, org.eclipse.swt.events.KeyEvent)
		 */
		public void keyPressed(TreeListDialogField field, KeyEvent event) {
			// TODO Auto-generated method stub

		}

		/* (non-Javadoc)
		 * @see org.eclipse.cdt.internal.ui.wizards.dialogfields.ITreeListAdapter#getChildren(org.eclipse.cdt.internal.ui.wizards.dialogfields.TreeListDialogField, java.lang.Object)
		 */
		public Object[] getChildren(TreeListDialogField field, Object element) {
			if (element instanceof DiscoveredElement) {
				DiscoveredElement elem = (DiscoveredElement) element;
				return elem.getChildren();
			}
			return EMPTY_ARR;
		}

		/* (non-Javadoc)
		 * @see org.eclipse.cdt.internal.ui.wizards.dialogfields.ITreeListAdapter#getParent(org.eclipse.cdt.internal.ui.wizards.dialogfields.TreeListDialogField, java.lang.Object)
		 */
		public Object getParent(TreeListDialogField field, Object element) {
			if (element instanceof DiscoveredElement) {
				DiscoveredElement elem = (DiscoveredElement) element;
				return elem.getParent();
			}
			return null;
		}

		/* (non-Javadoc)
		 * @see org.eclipse.cdt.internal.ui.wizards.dialogfields.ITreeListAdapter#hasChildren(org.eclipse.cdt.internal.ui.wizards.dialogfields.TreeListDialogField, java.lang.Object)
		 */
		public boolean hasChildren(TreeListDialogField field, Object element) {
			if (element instanceof DiscoveredElement) {
				DiscoveredElement elem = (DiscoveredElement) element;
				return elem.hasChildren();
			}
			return false;
		}
	}

	/**
	 * @param field
	 * @param index
	 */
	private void containerPageCustomButtonPressed(TreeListDialogField field, int index) {
		DiscoveredElement[] containers = null;
		switch (index) {
			case IDX_UP:
				/* move entry up */
				dirty |= moveUp();
				break;
			case IDX_DOWN:
				/* move entry down */
				dirty |= moveDown();
				break;
			case IDX_DISABLE:
				/* remove */
				dirty |= enableDisableEntry(DO_DISABLE);
				break;
			case IDX_ENABLE:
				/* restore */
				dirty |= enableDisableEntry(DO_ENABLE);
				break;
			case IDX_DELETE:
				/* delete */
				dirty |= deleteEntry();
				break;
		}
	}
	/**
	 * 
	 */
	private boolean moveUp() {
		boolean rc = false;
		List selElements = fDiscoveredContainerList.getSelectedElements();
		for (Iterator i = selElements.iterator(); i.hasNext(); ) {
			DiscoveredElement elem = (DiscoveredElement) i.next();
			DiscoveredElement parent = elem.getParent();
			Object[] children = parent.getChildren();
			for (int j = 0; j < children.length; ++j) {
				DiscoveredElement child = (DiscoveredElement) children[j];
				if (elem.equals(child)) {
					int prevIndex = j - 1;
					if (prevIndex >= 0) {
						// swap the two
						children[j] = children[prevIndex];
						children[prevIndex] = elem;
						rc = true;
						break;
					}
				}
			}
			parent.setChildren(children);
		}
		fDiscoveredContainerList.refresh();
		fDiscoveredContainerList.postSetSelection(new StructuredSelection(selElements));
		fDiscoveredContainerList.setFocus();
		return rc;
	}
	/**
	 * 
	 */
	private boolean moveDown() {
		boolean rc = false;
		List selElements = fDiscoveredContainerList.getSelectedElements();
		List revSelElements = new ArrayList(selElements);
		Collections.reverse(revSelElements);
		for (Iterator i = revSelElements.iterator(); i.hasNext(); ) {
			DiscoveredElement elem = (DiscoveredElement) i.next();
			DiscoveredElement parent = elem.getParent();
			Object[] children = parent.getChildren();
			for (int j = children.length - 1; j >= 0; --j) {
				DiscoveredElement child = (DiscoveredElement) children[j];
				if (elem.equals(child)) {
					int prevIndex = j + 1;
					if (prevIndex < children.length) {
						// swap the two
						children[j] = children[prevIndex];
						children[prevIndex] = elem;
						rc = true;
						break;
					}
				}
			}
			parent.setChildren(children);
		}
		fDiscoveredContainerList.refresh();
		fDiscoveredContainerList.postSetSelection(new StructuredSelection(selElements));
		fDiscoveredContainerList.setFocus();
		return rc;
	}
	/**
	 * 
	 * @param remove
	 */
	private boolean enableDisableEntry(int action) {
		boolean rc = false;
		boolean remove = (action == DO_DISABLE);
		List selElements = fDiscoveredContainerList.getSelectedElements();
		for (int i = selElements.size() - 1; i >= 0; --i) {
			DiscoveredElement elem = (DiscoveredElement) selElements.get(i);
			switch (elem.getEntryKind()) {
				case DiscoveredElement.INCLUDE_PATH:
				case DiscoveredElement.SYMBOL_DEFINITION:
					elem.setRemoved(remove);
					rc = true;
			}
		}
		fDiscoveredContainerList.refresh();
		fDiscoveredContainerList.setFocus();
		return rc;
	}
	/**
	 * 
	 */
	private boolean deleteEntry() {
		boolean rc = false;
		List newSelection = new ArrayList();
		List selElements = fDiscoveredContainerList.getSelectedElements();
		for (int i = 0; i < selElements.size(); ++i) {
			DiscoveredElement elem = (DiscoveredElement) selElements.get(i);
			if (elem.getEntryKind() != DiscoveredElement.CONTAINER) {
				DiscoveredElement parent = elem.getParent();
				if (parent != null) {
					Object[] children = parent.getChildren();
					if (elem.delete()) {
						switch (elem.getEntryKind()) {
							case DiscoveredElement.PATHS_GROUP:
								ScannerInfoCollector.getInstance().deleteAllPaths(fCProject.getProject());
								break;
							case DiscoveredElement.SYMBOLS_GROUP:
								ScannerInfoCollector.getInstance().deleteAllSymbols(fCProject.getProject());
								break;
							case DiscoveredElement.INCLUDE_PATH:
								ScannerInfoCollector.getInstance().deletePath(fCProject.getProject(), elem.getEntry());
								break;
							case DiscoveredElement.SYMBOL_DEFINITION:
								ScannerInfoCollector.getInstance().deleteSymbol(fCProject.getProject(), elem.getEntry());
								break;
						}
						rc = true;
						// set new selection
						for (int j = 0; j < children.length; ++j) {
							DiscoveredElement child = (DiscoveredElement) children[j];
							if (elem.equals(child)) {
								newSelection.clear();
								if (j + 1 < children.length) {
									newSelection.add(children[j + 1]);
								}
								else if (j - 1 >= 0) {
									newSelection.add(children[j - 1]);
								}
								else {
									newSelection.add(parent);
								}
								break;
							}
						}
					}
				}
			}
		}
		fDiscoveredContainerList.refresh();
		fDiscoveredContainerList.postSetSelection(new StructuredSelection(newSelection));
		fDiscoveredContainerList.setFocus();
		return rc;
	}

	/**
	 * @param field
	 */
	private void containerPageSelectionChanged(TreeListDialogField field) {
		List selElements = fDiscoveredContainerList.getSelectedElements();
		fDiscoveredContainerList.enableButton(IDX_UP, canMoveUpDown(selElements, DISC_UP));
		fDiscoveredContainerList.enableButton(IDX_DOWN, canMoveUpDown(selElements, DISC_DOWN));
		fDiscoveredContainerList.enableButton(IDX_DISABLE, canRemoveRestore(selElements));
		fDiscoveredContainerList.enableButton(IDX_ENABLE, canRemoveRestore(selElements));
		fDiscoveredContainerList.enableButton(IDX_DELETE, canDelete(selElements));
	}
	/**
	 * @param selElements
	 * @return
	 */
	private boolean canMoveUpDown(List selElements, int direction) {
		if (selElements.size() == 0) {
			return false;
		}
		for (int i = 0; i < selElements.size(); i++) {
			DiscoveredElement elem = (DiscoveredElement) selElements.get(i);
			switch (elem.getEntryKind()) {
				case DiscoveredElement.CONTAINER:
				case DiscoveredElement.PATHS_GROUP:
				case DiscoveredElement.SYMBOLS_GROUP:
				case DiscoveredElement.SYMBOL_DEFINITION:
					return false;
			}
			DiscoveredElement parent = elem.getParent();
			DiscoveredElement borderElem = null;
			int borderElementIndex = (direction == DISC_UP) ? 0 : parent.getChildren().length - 1;
			if (parent.getEntryKind() == DiscoveredElement.PATHS_GROUP) {
				borderElem = (DiscoveredElement)(parent.getChildren())[borderElementIndex];
			}
			if (borderElem != null) {
				if (borderElem.equals(elem)) {
					return false;
				}
			}
		}
		return true;
	}
	/**
	 * @param selElements
	 * @return
	 */
	private boolean canRemoveRestore(List selElements) {
		if (selElements.size() == 0) {
			return false;
		}
		for (int i = 0; i < selElements.size(); i++) {
			DiscoveredElement elem = (DiscoveredElement) selElements.get(i);
			switch (elem.getEntryKind()) {
				case DiscoveredElement.CONTAINER:
				case DiscoveredElement.PATHS_GROUP:
				case DiscoveredElement.SYMBOLS_GROUP:
					return false;
			}
		}
		return true;
	}
	/**
	 * @param selElements
	 * @return
	 */
	private boolean canDelete(List selElements) {
		if (selElements.size() == 0) {
			return false;
		}
		for (int i = 0; i < selElements.size(); i++) {
			DiscoveredElement elem = (DiscoveredElement) selElements.get(i);
			if (elem.getEntryKind() == DiscoveredElement.CONTAINER) {
					return false;
			}
		}
		return true;
	}
}
