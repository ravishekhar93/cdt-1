package org.eclipse.cdt.ui.grid;

/**
 * @since 5.7
 */
public class StringPresentationModel extends PresentationModel implements IStringPresentationModel {

	public StringPresentationModel(String name)
	{
		super(name);
		this.value = "Test";
	}
	
	@Override
	public String getValue() {
		return doGetValue();
	}
	
	protected String doGetValue() {
		return value;
	}
	
	@Override
	public void setValue(String value) {
		doSetValue(value);
		notifyListeners(PresentationModel.CHANGED, this);
	}
	
	protected void doSetValue(String value) {
		this.value = value;
	}
	
	// FIXME: get rid of this one.
	private String value;
}
