package org.eclipse.cdt.ui.grid;

import java.util.List;

/** Interface for string values that must be amoung some predetermined
 *  set.
 * @since 5.7
 */
public interface ISelectionPresentationModel extends IStringPresentationModel {
	
	/* Returns the list of possible values. It is often advisable that this list
	 * is obtained from some metadata, and not stored in the domain model. 
	 */
	public List<String> getPossibleValues();

}
