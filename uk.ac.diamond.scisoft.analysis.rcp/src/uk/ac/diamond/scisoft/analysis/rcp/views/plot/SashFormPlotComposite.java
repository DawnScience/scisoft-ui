/*
 * Copyright (c) 2012 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.analysis.rcp.views.plot;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.dawnsci.plotting.actions.ActionBarWrapper;
import org.eclipse.dawnsci.plotting.api.IPlottingSystem;
import org.eclipse.dawnsci.plotting.api.PlotType;
import org.eclipse.dawnsci.plotting.api.PlottingFactory;
import org.eclipse.dawnsci.plotting.api.region.IROIListener;
import org.eclipse.dawnsci.plotting.api.region.IRegion;
import org.eclipse.dawnsci.plotting.api.region.IRegion.RegionType;
import org.eclipse.dawnsci.plotting.api.tool.IToolPage.ToolPageRole;
import org.eclipse.january.dataset.IDataset;
import org.eclipse.jface.action.IAction;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PlatformUI;
import org.slf4j.Logger;

/**
 * A class with a left, right and status. The right contains a plot above the 
 * status:
 *      |	(Left)	|(Right)	Toolbars			|
 *      |			|								|
 *      |			|			Plot				|
 *      |			|								|
 *      |			|_______________________________|
 *      |			|		Status					|
 */
public class SashFormPlotComposite {

	public static final String DEFAULT_PLOTTING_SYSTEM_NAME = "SashFormPlot";
	
	protected static int plottingSystemUID = 0;

	protected IWorkbenchPart part;
	protected SashForm sashForm;
	protected ScrolledComposite leftScroll, rightScroll;
	protected Composite left, right;
	protected IPlottingSystem<Composite> plottingSystem;
	protected ActionBarWrapper actionBarWrapper;
	protected IDataset[] datasets;
	protected String xAxisLabel, yAxisLabel;
	protected String plotTitle;
	protected SashForm rightSash;
	protected Text statusLabel;
	protected IRegion regionOnDisplay;

	/**
	 * @param parent
	 *            The Composite which will contain this SashFormPlotComposite
	 * @param part
	 *            The IWorkbenchPart which holds this SashFormPlotComposite, or <code>null</code> if not known
	 * @throws Exception
	 */
	public SashFormPlotComposite(Composite parent, IWorkbenchPart part) throws Exception {

		this.part = part;
		
		createSashFormPlotComposite(parent);
	}

	/**
	 * Original constructor kept for backward compatibility, but better to use the simpler constructor and then call
	 * addRegionListener() and addActions() if necessary.
	 */
	@Deprecated
	public SashFormPlotComposite(Composite parent, final IWorkbenchPart part, IROIListener regionListener,
			final IAction... actions) throws Exception {

		this(parent, part);

		if (regionListener != null) {
			addRegionListener(regionListener);
		}

		if (actions != null) {
			addActions(actions);
		}
	}
	
	private void createSashFormPlotComposite(Composite parent) throws Exception {
		createSashFormLayout(parent);
		createPlottingSystem();
		createStatusPanel();
	}

	private void createSashFormLayout(Composite parent) {
		this.sashForm = new SashForm(parent, SWT.HORIZONTAL);

		this.leftScroll = new ScrolledComposite(sashForm, SWT.H_SCROLL | SWT.V_SCROLL);
		leftScroll.setExpandHorizontal(true);
		leftScroll.setExpandVertical(true);

		this.left = new Composite(leftScroll, SWT.NONE);
		left.setLayout(new GridLayout());

		this.rightScroll = new ScrolledComposite(sashForm, SWT.H_SCROLL | SWT.V_SCROLL);
		rightScroll.setExpandHorizontal(true);
		rightScroll.setExpandVertical(true);

		this.rightSash = new SashForm(rightScroll, SWT.VERTICAL);

		this.right = new Composite(rightSash, SWT.NONE);
		GridLayout gl_right = new GridLayout(1, false);
		gl_right.marginHeight = 1;
		gl_right.verticalSpacing = 0;
		right.setLayout(gl_right);
	}

	private void createPlottingSystem() throws Exception {
		// Do this first to ensure action bar is placed at the top
		actionBarWrapper = ActionBarWrapper.createActionBars(right, null);
		
		plottingSystem = PlottingFactory.createPlottingSystem();
		plottingSystem.createPlotPart(right, getNewPlottingSystemName(), null, PlotType.XY, part);
		plottingSystem.setRescale(true);
		plottingSystem.getPlotComposite().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		createRegionToDisplay();

		plottingSystem.getPlotActionSystem().fillZoomActions(actionBarWrapper.getToolBarManager());
		plottingSystem.getPlotActionSystem().fillPrintActions(actionBarWrapper.getToolBarManager());
		plottingSystem.getPlotActionSystem().fillToolActions(actionBarWrapper.getToolBarManager(), ToolPageRole.ROLE_1D);
		actionBarWrapper.update(true);
	}
	
	private String getNewPlottingSystemName() {
		String plottingSystemName;
		if (part != null) {
			plottingSystemName = part.getTitle();
		} else {
			plottingSystemName = DEFAULT_PLOTTING_SYSTEM_NAME;
		}
		plottingSystemName += ++plottingSystemUID;
		return plottingSystemName;
	}

	private void createRegionToDisplay() throws Exception {
		this.regionOnDisplay = getPlottingSystem().createRegion("ROI", RegionType.XAXIS);
		regionOnDisplay.setRegionColor(Display.getDefault().getSystemColor(SWT.COLOR_BLUE));
		getPlottingSystem().addRegion(regionOnDisplay);
		regionOnDisplay.setMobile(true);
	}

	private void createStatusPanel() {
		this.statusLabel = new Text(rightSash, SWT.WRAP | SWT.V_SCROLL);
		statusLabel.setEditable(false);
		rightSash.setWeights(new int[] { 100, 10 });
	}

	/**
	 * @param actions May be empty
	 */
	public void addActions(IAction[] actions) {
		for (int i = 0; i < actions.length; i++) {
			actionBarWrapper.getToolBarManager().add(actions[i]);
		}
		actionBarWrapper.update(true);
	}

	/**
	 * @param regionListener Should not be null
	 */
	public void addRegionListener(IROIListener regionListener) {
		regionOnDisplay.addROIListener(regionListener);
	}

	public IPlottingSystem<Composite> getPlottingSystem() {
		return plottingSystem;
	}

	public IRegion getRegionOnDisplay() {
		return regionOnDisplay;
	}

	/**
	 * Call once all ui has been added
	 */
	public void computeSizes() {
		leftScroll.setContent(left);
		leftScroll.setMinSize(left.computeSize(SWT.DEFAULT, SWT.DEFAULT));

		rightScroll.setContent(rightSash);
		rightScroll.setMinSize(rightSash.computeSize(SWT.DEFAULT, SWT.DEFAULT));
	}

	public ScrolledComposite getLeftScroll() {
		return leftScroll;
	}

	public ScrolledComposite getRightScroll() {
		return rightScroll;
	}

	public Composite getLeft() {
		return left;
	}

	public Composite getRight() {
		return right;
	}

	public void setWeights(int[] is) {
		this.sashForm.setWeights(is);
	}

	public void setDatasets(IDataset... datasets) {
		this.datasets = datasets;
	}

	public void setXAxisLabel(String label) {
		this.xAxisLabel = label;
	}

	public void setYAxisLabel(String label) {
		this.yAxisLabel = label;
		plottingSystem.getSelectedYAxis().setTitle(label);
	}

	public void setPlotTitle(String plotTitle) {
		this.plotTitle = plotTitle;
	}

	public String getXAxisLabel() {
		return xAxisLabel;
	}

	public String getYAxisLabel() {
		return yAxisLabel;
	}

	public String getPlotTitle() {
		return plotTitle;
	}

	public SashForm getSashForm() {
		return sashForm;
	}

	public void dispose() {
		if (plottingSystem != null) {
			plottingSystem.dispose();
			plottingSystem = null;
		}
	}

	
	/**
	 * Adds status to the status field (scrolling history) and logs a message to the given logger at <code>info</code>
	 * level. SWT thread safe
	 * 
	 * @deprecated Unclear why this method should have anything to do with loggers. Use appendStatus(text) instead and
	 *             do logging in the calling code where the intent is clearer.
	 * @param text
	 * @param logger
	 */
	@Deprecated
	public void appendStatus(final String text, Logger logger) {
		if (logger != null) {
			logger.info(text);
		}

		appendStatus(text);
	}

	/**
	 * Adds status to the status field (scrolling history). SWT thread safe
	 * 
	 * @param text
	 */
	public void appendStatus(final String text) {
		PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable() {
			@Override
			public void run() {
				statusLabel.append(DateFormat.getDateTimeInstance().format(new Date()));
				statusLabel.append(" ");
				statusLabel.append(text);
				statusLabel.append("\n");
			}
		});
	}
	
	public void clearStatus() {
		statusLabel.setText("");
	}

	/**
	 * Plots the data given by setDatasets, and sets the title and axis labels
	 */
	public void plotData() {
		plottingSystem.clear();
		List<IDataset> ys = new ArrayList<IDataset>();
		for (IDataset ds : datasets) {
			ys.add(ds);
		}
		plottingSystem.createPlot1D(null, ys, null);

		plottingSystem.setTitle(plotTitle);
		if (xAxisLabel != null) {
			plottingSystem.getSelectedXAxis().setTitle(xAxisLabel);
		}
		if (yAxisLabel != null) {
			plottingSystem.getSelectedYAxis().setTitle(yAxisLabel);
		}
	}
}
