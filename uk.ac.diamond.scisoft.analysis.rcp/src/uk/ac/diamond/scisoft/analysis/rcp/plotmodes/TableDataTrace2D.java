package uk.ac.diamond.scisoft.analysis.rcp.plotmodes;

import org.eclipse.dawnsci.plotting.api.axis.IAxis;
import org.eclipse.dawnsci.plotting.api.trace.ITableDataTrace;
import org.eclipse.january.dataset.IDataset;

public class TableDataTrace2D implements ITableDataTrace {
	private final IDataset d;
	private Object userObject = null;

	public TableDataTrace2D(IDataset d) {
		this.d = d;
	}

	@Override
	public void initialize(IAxis... axes) {
	}

	@Override
	public void setName(String name) {
		
	}

	@Override
	public String getName() {
		return null;
	}

	@Override
	public void setVisible(boolean isVisible) {
		
	}

	@Override
	public void setUserTrace(boolean isUserTrace) {
		
	}

	@Override
	public void setUserObject(Object userObject) {
		this.userObject = userObject;
		
	}

	@Override
	public void setDataName(String name) {
		
	}

	@Override
	public boolean isVisible() {
		return false;
	}

	@Override
	public boolean isUserTrace() {
		return false;
	}

	@Override
	public boolean is3DTrace() {
		return false;
	}

	@Override
	public Object getUserObject() {
		return userObject;
	}

	@Override
	public int getRank() {
		return 2;
	}

	@Override
	public String getDataName() {
		return null;
	}

	@Override
	public IDataset getData() {
		return d;
	}

	@Override
	public void dispose() {
		
	}
}