/*
 * Copyright (c) 2012-2016 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.analysis.rcp.plotting;

import org.eclipse.dawnsci.plotting.api.IPlottingSystem;
import org.eclipse.dawnsci.plotting.api.PlotType;
import org.eclipse.dawnsci.plotting.api.PlottingFactory;
import org.eclipse.dawnsci.plotting.api.trace.ColorOption;
import org.eclipse.jface.action.IContributionItem;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.menus.CommandContributionItem;
import org.eclipse.ui.menus.CommandContributionItemParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.plotclient.IPlotWindow;
import uk.ac.diamond.scisoft.analysis.plotclient.PlotWindowManager;
import uk.ac.diamond.scisoft.analysis.plotclient.ScriptingConnection;
import uk.ac.diamond.scisoft.analysis.plotserver.GuiPlotMode;
import uk.ac.diamond.scisoft.analysis.plotserver.IBeanScriptingManager;
import uk.ac.diamond.scisoft.analysis.rcp.AnalysisRCPActivator;
import uk.ac.diamond.scisoft.analysis.rcp.plotting.actions.ClearPlottingSystemAction;
import uk.ac.diamond.scisoft.analysis.rcp.plotting.actions.DuplicatePlotAction;

/**
 * Abstract Class used to implement PlotWindows that implement IObservable
 * 
 * Class creates and manages a plotting system for you.
 * 
 */
public abstract class AbstractPlotWindow extends ScriptingConnection implements IPlotWindow {

	private static Logger logger = LoggerFactory.getLogger(AbstractPlotWindow.class);

	protected Composite parentComp;
	protected Composite plotSystemComposite;
	private IWorkbenchPart part = null;
	protected IActionBars bars;

	/**
	 * PlotWindow may be given toolbars exclusive to the workbench part. In this case, there is no need to remove
	 * actions from the part.
	 */
	private boolean exclusiveToolars = false;

	private CommandContributionItem duplicateWindowCCI;
	private CommandContributionItem clearPlotCCI;

	/**
	 * Constructor
	 * @param parent
	 * @param manager
	 * @param bars
	 * @param part
	 * @param name
	 */
	public AbstractPlotWindow(final Composite       parent, 
			                  IBeanScriptingManager manager, 
			                  IActionBars           bars, 
			                  IWorkbenchPart        part, 
			                  String                name) {
		
		super(manager, name);
		this.parentComp = parent;
		this.bars = bars;
		this.part = part;

		createPlotControl(parent);
	}
	

	/**
	 * Create the IPlottingSystem<Composite> and other controls if necessary<br>
	 * The IPlottingSystem<Composite> should be created the following way:<br>
	 * {@code 	plottingSystem = PlottingFactory.createPlottingSystem();}<br>
	 * {@code	plottingSystem.setColorOption(ColorOption.NONE);}<br>
	 * {@code	plottingSystem.createPlotPart(parent, getName(), bars, PlotType.XY, (IViewPart) getGuiManager());}<br>
	 * {@code	plottingSystem.repaint();}<br>
	 * {@code	plottingSystem.addRegionListener(getRoiManager());}
	 * {@code	plottingSystem.addTraceListener(getRoiManager().getTraceListener());}<br>
	 * <br>
	 * 
	 * @param composite
	 */
	public void createPlotControl(Composite composite) {
		try {
			IPlottingSystem<Composite> system = PlottingFactory.createPlottingSystem();
			system.setColorOption(ColorOption.NONE);
			system.createPlotPart(composite, getName(), bars, PlotType.XY, part);
			system.repaint();
			setPlottingSystem(system);
		} catch (Exception e) {
			logger.error("Cannot locate any plotting System!", e);
		}
	}

	/**
	 * Return current page.
	 * @return current page
	 */
	@Override
	public IWorkbenchPart getPart() {
		return part;
	}

	/**
	 * Method to create the PlotView duplicating actions
	 * for DatasetPlotter and LightWeightPlottingSystem
	 */
	protected void addDuplicateAction(){
		if (duplicateWindowCCI == null) {
			CommandContributionItemParameter ccip = new CommandContributionItemParameter(PlatformUI.getWorkbench()
					.getActiveWorkbenchWindow(), null, DuplicatePlotAction.COMMAND_ID,
					CommandContributionItem.STYLE_PUSH);
			ccip.label = "Create Duplicate Plot";
			ccip.icon = AnalysisRCPActivator.getImageDescriptor("icons/chart_curve_add.png");
			duplicateWindowCCI = new CommandContributionItem(ccip);
		}
		bars.getMenuManager().add(duplicateWindowCCI);
	}

	/**
	 * Method to create the PlotView plotting system clear action
	 * for the LightWeightPlottingSystem
	 */
	protected void addClearAction(){
		if (clearPlotCCI == null) {
			CommandContributionItemParameter ccip = new CommandContributionItemParameter(PlatformUI.getWorkbench()
					.getActiveWorkbenchWindow(), null, ClearPlottingSystemAction.COMMAND_ID,
					CommandContributionItem.STYLE_PUSH);
			ccip.label = "Clear Plot";
			ccip.icon = AnalysisRCPActivator.getImageDescriptor("icons/clear.gif");
			clearPlotCCI = new CommandContributionItem(ccip);
		}
		bars.getMenuManager().add(clearPlotCCI);
	}

	/**
	 * Remove the actions previously created
	 */
	protected void removePreviousActions() {

		IContributionItem[] items = bars.getToolBarManager().getItems();
		for (int i = 0; i < items.length; i++)
			items[i].dispose();
		bars.getToolBarManager().removeAll();

		bars.getMenuManager().removeAll();
		bars.getStatusLineManager().removeAll();
	}

	public boolean isExclusiveToolars() {
		return exclusiveToolars;
	}

	public void setExclusiveToolars(boolean exclusiveToolars) {
		this.exclusiveToolars = exclusiveToolars;
	}

	@Override
	protected void changePlotMode(GuiPlotMode plotMode) {
		super.changePlotMode(plotMode);
		addDuplicateAction();
		addClearAction();
	}

	public void setFocus() {
		parentComp.setFocus();
	}
	
	@Override
	public void dispose() {
		PlotWindowManager.getPrivateManager().unregisterPlotWindow(this);
		super.dispose();
	}
}
