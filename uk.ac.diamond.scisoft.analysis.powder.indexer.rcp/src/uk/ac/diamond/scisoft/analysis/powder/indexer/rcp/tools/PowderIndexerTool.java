package uk.ac.diamond.scisoft.analysis.powder.indexer.rcp.tools;

import org.dawnsci.plotting.actions.ActionBarWrapper;
import org.eclipse.dawnsci.plotting.api.tool.AbstractToolPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.part.IPageSite;

import uk.ac.diamond.scisoft.analysis.powder.indexer.rcp.PowderIndexerManager;
import uk.ac.diamond.scisoft.analysis.powder.indexer.rcp.views.CellParameterDelegate;
import uk.ac.diamond.scisoft.analysis.powder.indexer.rcp.views.PowderIndexerActions;
import uk.ac.diamond.scisoft.analysis.powder.indexer.rcp.widget.PowderIndexerSetupWidget;

public class PowderIndexerTool extends AbstractToolPage  {

	// Page Components
	private Composite composite;

	private CellParameterDelegate cells;
	
	private PowderIndexerSetupWidget widget;
	
	private PowderIndexerActions actions;
	
	private PowderIndexerManager controller; 
	
	@Override
	public ToolPageRole getToolPageRole() {
		return ToolPageRole.ROLE_1D;
	}

	@Override
	public Control getControl() {
		return composite;
	}
	
	@Override
	public void createControl(Composite parent) {
		this.composite = new Composite(parent, SWT.NONE);
		composite.setLayout(new GridLayout(1, false));
		composite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		
		this.controller = new PowderIndexerManager();

		final IPageSite site = getSite();
		IActionBars actionbars = site != null ? site.getActionBars() : generateActionBar(parent);
		
		actions = new PowderIndexerActions(controller);
		actions.createActions(actionbars.getToolBarManager());
		
		cells = new CellParameterDelegate(controller);
		cells.createControl(composite);
		
		widget = new PowderIndexerSetupWidget(controller); 
		widget.createControl(composite);

		controller.setMainWidget(widget); //TODO: do want this back propagation
		
		super.createControl(parent);
	}
	
	private ActionBarWrapper generateActionBar(Composite parent) {
		ActionBarWrapper actionBarWrapper = null;
		parent = new Composite(composite, SWT.RIGHT);
		parent.setLayout(new GridLayout(1, false));
		actionBarWrapper = ActionBarWrapper.createActionBars(parent, null);
		actionBarWrapper.update(true);
		return actionBarWrapper;
	}

	@Override
	public void setFocus() {
		this.composite.setFocus();
	}

}
