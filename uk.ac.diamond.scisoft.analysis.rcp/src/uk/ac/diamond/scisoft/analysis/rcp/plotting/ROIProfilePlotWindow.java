/*
 * Copyright (c) 2012 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.analysis.rcp.plotting;

import org.dawb.common.ui.util.DisplayUtils;
import org.dawnsci.plotting.roi.ROIWidget;
import org.eclipse.dawnsci.analysis.api.roi.IROI;
import org.eclipse.dawnsci.analysis.dataset.roi.PerimeterBoxROI;
import org.eclipse.dawnsci.plotting.api.IPlottingSystem;
import org.eclipse.dawnsci.plotting.api.region.IRegion;
import org.eclipse.dawnsci.plotting.api.region.IRegion.RegionType;
import org.eclipse.dawnsci.plotting.api.tool.AbstractToolPage;
import org.eclipse.dawnsci.plotting.api.tool.IProfileToolPage;
import org.eclipse.dawnsci.plotting.api.tool.IToolPageSystem;
import org.eclipse.dawnsci.plotting.api.tool.ToolPageFactory;
import org.eclipse.january.dataset.IDataset;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.ActionContributionItem;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.forms.events.ExpansionAdapter;
import org.eclipse.ui.forms.events.ExpansionEvent;
import org.eclipse.ui.forms.widgets.ExpandableComposite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.plotclient.IPlotWindowManager;
import uk.ac.diamond.scisoft.analysis.plotclient.PlotWindowManager;
import uk.ac.diamond.scisoft.analysis.plotclient.connection.PlotConnectionFactory;
import uk.ac.diamond.scisoft.analysis.plotserver.GuiBean;
import uk.ac.diamond.scisoft.analysis.plotserver.GuiPlotMode;
import uk.ac.diamond.scisoft.analysis.plotserver.IBeanScriptingManager;
import uk.ac.diamond.scisoft.analysis.rcp.AnalysisRCPActivator;

/**
 * PlotWindow equivalent with two side plots which display boxline profiles of a Rectangular ROI on the main plot
 */
public class ROIProfilePlotWindow extends AbstractPlotWindow {

	static private Logger logger = LoggerFactory.getLogger(ROIProfilePlotWindow.class);

	private static final String REGION_NAME = "Perimeter Box";

	private IProfileToolPage sideProfile1;
	private IProfileToolPage sideProfile2;

	private SashForm sashForm;
	private SashForm sashForm2;
	private SashForm sashForm3;

	private ROIWidget myROIWidget;
	private ROIWidget verticalProfileROIWidget;
	private ROIWidget horizontalProfileROIWidget;
	private IPlottingSystem<Composite> verticalProfilePlottingSystem;
	private IPlottingSystem<Composite> horizontalProfilePlottingSystem;
	private AbstractToolPage roiSumProfile;
	private Action plotEdge;
	private Action plotAverage;

	private ActionContributionItem plotEdgeACI;
	private ActionContributionItem plotAverageACI;
	private IRegion region;

	/**
	 * Obtain the IPlotWindowManager for the running Eclipse.
	 * 
	 * @return singleton instance of IPlotWindowManager
	 */
	public static IPlotWindowManager getManager() {
		// get the private manager for use only within the framework and
		// "upcast" it to IPlotWindowManager
		return PlotWindowManager.getPrivateManager();
	}

	public ROIProfilePlotWindow(Composite parent, IActionBars bars, IWorkbenchPart part, String name) {
		this(parent, null, bars, part, name);
	}

	public ROIProfilePlotWindow(final Composite parent, IBeanScriptingManager manager,
			                     IActionBars bars, IWorkbenchPart part, String name) {
		super(parent, manager, bars, part, name);
		PlotWindowManager.getPrivateManager().registerPlotWindow(this);
	}

	@Override
	public GuiPlotMode getPlotMode(){
		return GuiPlotMode.TWOD;
	}

	/**
	 * Create a plotting system layout with a main plotting system and two side plot profiles
	 */
	@Override
	public void createPlotControl(Composite composite){
		
		sashForm = new SashForm(composite, SWT.HORIZONTAL);
		sashForm.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		sashForm.setBackground(new Color(parentComp.getDisplay(), 192, 192, 192));
		
		sashForm2 = new SashForm(sashForm, SWT.VERTICAL);
		sashForm2.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		sashForm2.setBackground(new Color(parentComp.getDisplay(), 192, 192, 192));
		sashForm3 = new SashForm(sashForm, SWT.VERTICAL);
		sashForm3.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		sashForm3.setBackground(new Color(parentComp.getDisplay(), 192, 192, 192));
		try {
			// Creates the PlottingSystem
			super.createPlotControl(sashForm2);

			IToolPageSystem tps = plottingSystem.getAdapter(IToolPageSystem.class);
			sideProfile1 = (IProfileToolPage)ToolPageFactory.getToolPage("org.dawb.workbench.plotting.tools.boxLineProfileTool");
			sideProfile1.setLineOrientation(false);
			sideProfile1.setPlotEdgeProfile(true);
			sideProfile1.setPlotAverageProfile(false);
			sideProfile1.setToolSystem(tps);
			sideProfile1.setPlottingSystem(plottingSystem);
			sideProfile1.setTitle(getName()+"_profile1");
			sideProfile1.setPart(getPart());
			sideProfile1.setToolId(String.valueOf(sideProfile1.hashCode()));
			sideProfile1.createControl(sashForm2);
			sideProfile1.activate();
			
			sideProfile2 = (IProfileToolPage)ToolPageFactory.getToolPage("org.dawb.workbench.plotting.tools.boxLineProfileTool");
			sideProfile2.setLineOrientation(true);
			sideProfile2.setPlotEdgeProfile(true);
			sideProfile2.setPlotAverageProfile(false);
			sideProfile2.setToolSystem(tps);
			sideProfile2.setPlottingSystem(plottingSystem);
			sideProfile2.setTitle(getName()+"_profile2");
			sideProfile2.setPart(getPart());
			sideProfile2.setToolId(String.valueOf(sideProfile2.hashCode()));
			sideProfile2.createControl(sashForm3);
			sideProfile2.activate();

			verticalProfilePlottingSystem = sideProfile2.getToolPlottingSystem();
			horizontalProfilePlottingSystem = sideProfile1.getToolPlottingSystem();

			//start metadata
			final ScrolledComposite scrollComposite = new ScrolledComposite(sashForm3, SWT.H_SCROLL | SWT.V_SCROLL);
			final Composite contentComposite = new Composite(scrollComposite, SWT.FILL);
			contentComposite.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, true, 1, 1));
			contentComposite.setLayout(new GridLayout(1, false));
			
			ExpansionAdapter expansionAdapter = new ExpansionAdapter() {
				@Override
				public void expansionStateChanged(ExpansionEvent e) {
					logger.trace("regionsExpander");
					Rectangle r = scrollComposite.getClientArea();
					scrollComposite.setMinSize(contentComposite.computeSize(r.width, SWT.DEFAULT));
					contentComposite.layout();
				}
			};
			
			Label metadataLabel = new Label(contentComposite, SWT.NONE);
			metadataLabel.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, false, 1, 1));
			metadataLabel.setAlignment(SWT.CENTER);
			metadataLabel.setText("Region Of Interest Information");

			//main
			ExpandableComposite mainRegionInfoExpander = new ExpandableComposite(contentComposite, SWT.NONE);
			mainRegionInfoExpander.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 2, 1));
			mainRegionInfoExpander.setLayout(new GridLayout(1, false));
			mainRegionInfoExpander.setText("Main Region Of Interest");
			
			Composite mainRegionComposite = new Composite(mainRegionInfoExpander, SWT.NONE);
			GridData gridData = new GridData(SWT.FILL, SWT.FILL, true, true);
			mainRegionComposite.setLayout(new GridLayout(1, false));
			mainRegionComposite.setLayoutData(gridData);
			
			myROIWidget = new ROIWidget(mainRegionComposite, plottingSystem, "Perimeter Box region editor");
			myROIWidget.createWidget();
			myROIWidget.addSelectionChangedListener(new ISelectionChangedListener() {
				
				@Override
				public void selectionChanged(SelectionChangedEvent event) {
					IROI newRoi = myROIWidget.getROI();
					String regionName = myROIWidget.getRegionName();
					
					IRegion region = plottingSystem.getRegion(regionName);
					if(region != null){
						region.setROI(newRoi);
					}
				}
			});

			Group regionSumGroup = new Group(mainRegionComposite, SWT.NONE);
			regionSumGroup.setText("Sum");
			gridData = new GridData(SWT.FILL, SWT.FILL, true, true);
			regionSumGroup.setLayout(new GridLayout(1, false));
			regionSumGroup.setLayoutData(gridData);
			roiSumProfile = (AbstractToolPage)ToolPageFactory.getToolPage("org.dawb.workbench.plotting.tools.regionSumTool");
			roiSumProfile.setToolSystem(tps);
			roiSumProfile.setPlottingSystem(plottingSystem);
			roiSumProfile.setTitle(getName()+"_Region_Sum");
			roiSumProfile.setPart(getPart());
			roiSumProfile.setToolId(String.valueOf(roiSumProfile.hashCode()));
			roiSumProfile.createControl(regionSumGroup);
			roiSumProfile.activate();

			mainRegionInfoExpander.setClient(mainRegionComposite);
			mainRegionInfoExpander.addExpansionListener(expansionAdapter);
			mainRegionInfoExpander.setExpanded(true);
			
			//vertical
			ExpandableComposite verticalRegionInfoExpander = new ExpandableComposite(contentComposite, SWT.NONE);
			verticalRegionInfoExpander.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 2, 1));
			verticalRegionInfoExpander.setLayout(new GridLayout(1, false));
			verticalRegionInfoExpander.setText("Vertical Profile ROI");
			
			Composite verticalProfileComposite = new Composite(verticalRegionInfoExpander, SWT.NONE);
			verticalProfileComposite.setLayout(new GridLayout(1, false));
			verticalProfileComposite.setLayoutData(gridData);

			final Button displayVerticalProfileROI = new Button(verticalProfileComposite, SWT.CHECK);
			displayVerticalProfileROI.addSelectionListener(new SelectionListener() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					sideProfile2.setXAxisROIVisible(displayVerticalProfileROI.getSelection());
				}

				@Override
				public void widgetDefaultSelected(SelectionEvent e) {}
			});
			displayVerticalProfileROI.setText("Display Vertical Profile Region of Interest");
			displayVerticalProfileROI.setSelection(true);
			verticalProfileROIWidget = new ROIWidget(verticalProfileComposite, verticalProfilePlottingSystem, "Left/Right region editor");
			verticalProfileROIWidget.setIsProfile(true);
			verticalProfileROIWidget.createWidget();
			verticalProfileROIWidget.addSelectionChangedListener(new ISelectionChangedListener() {
				
				@Override
				public void selectionChanged(SelectionChangedEvent event) {
					IROI newRoi = verticalProfileROIWidget.getROI();
					String regionName = verticalProfileROIWidget.getRegionName();
					
					IRegion region = verticalProfilePlottingSystem.getRegion(regionName);
					if(region != null){
						region.setROI(newRoi);
					}
				}
			});

			verticalRegionInfoExpander.setClient(verticalProfileComposite);
			verticalRegionInfoExpander.addExpansionListener(expansionAdapter);
			verticalRegionInfoExpander.setExpanded(false);
			
			//horizontal
			ExpandableComposite horizontalRegionInfoExpander = new ExpandableComposite(contentComposite, SWT.NONE);
			horizontalRegionInfoExpander.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 2, 1));
			horizontalRegionInfoExpander.setLayout(new GridLayout(1, false));
			horizontalRegionInfoExpander.setText("Horizontal Profile ROI");
			
			Composite horizontalProfileComposite = new Composite(horizontalRegionInfoExpander, SWT.NONE);
			horizontalProfileComposite.setLayout(new GridLayout(1, false));
			horizontalProfileComposite.setLayoutData(gridData);

			final Button displayHorizontalProfileROI = new Button(horizontalProfileComposite, SWT.CHECK);
			displayHorizontalProfileROI.addSelectionListener(new SelectionListener() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					sideProfile1.setXAxisROIVisible(displayHorizontalProfileROI.getSelection());
				}

				@Override
				public void widgetDefaultSelected(SelectionEvent e) {}
			});
			displayHorizontalProfileROI.setText("Display Horizontal Profile Region of Interest");
			displayHorizontalProfileROI.setSelection(true);
			horizontalProfileROIWidget = new ROIWidget(horizontalProfileComposite, horizontalProfilePlottingSystem, "Bottom/Up region editor");
			horizontalProfileROIWidget.setIsProfile(true);
			horizontalProfileROIWidget.createWidget();
			horizontalProfileROIWidget.addSelectionChangedListener(new ISelectionChangedListener() {
				
				@Override
				public void selectionChanged(SelectionChangedEvent event) {
					IROI newRoi = horizontalProfileROIWidget.getROI();
					String regionName = horizontalProfileROIWidget.getRegionName();
					
					IRegion region = horizontalProfilePlottingSystem.getRegion(regionName);
					if(region != null){
						region.setROI(newRoi);
					}
				}
			});

			horizontalRegionInfoExpander.setClient(horizontalProfileComposite);
			horizontalRegionInfoExpander.addExpansionListener(expansionAdapter);
			horizontalRegionInfoExpander.setExpanded(false);
			
			scrollComposite.setContent(contentComposite);
			scrollComposite.setExpandHorizontal(true);
			scrollComposite.setExpandVertical(true);
			scrollComposite.addControlListener(new ControlAdapter() {
				@Override
				public void controlResized(ControlEvent e) {
					Rectangle r = scrollComposite.getClientArea();
					scrollComposite.setMinSize(contentComposite.computeSize(r.width, SWT.DEFAULT));
				}
			});
			//end metadata

			sashForm.setWeights(new int[]{1, 1});
			plottingSystem.addRegionListener(getRoiManager());
			plottingSystem.addTraceListener(getRoiManager().getTraceListener());

		} catch (Exception e) {
			logger.error("Cannot locate any Abstract plotting System!", e);
		}
	}

	@Override
	public void createRegion() {
		Display.getDefault().syncExec(new Runnable() {
			@Override
			public void run() {
				try {
					IRegion region = getPlottingSystem().getRegion(REGION_NAME);
					if (region == null) {
						region = getPlottingSystem().createRegion(REGION_NAME, RegionType.PERIMETERBOX);
						double width, height;
						IDataset data = getDataBean() != null && !getDataBean().getData().isEmpty() ? getDataBean().getData().get(0).getData() : null;
						PerimeterBoxROI proi = null;
						if (data != null) {
							width = data.getShape()[0];
							double newWidth = (80 * width)/100; 
							height = data.getShape()[1];
							double newHeight = (80 * height)/100;
							double startX = (width - newWidth)/2;
							double startY = (height - newHeight)/2;
							proi = new PerimeterBoxROI(startX, startY, newWidth, newHeight, 0);
						} else {
							width = plottingSystem.getAxes().get(0).getUpper()/2;
							height = plottingSystem.getAxes().get(1).getUpper()/2;
							proi = new PerimeterBoxROI(0, 0, width, height, 0);
						}
						proi.setName(REGION_NAME);
						proi.setPlot(true);
						region.setROI(proi);
						region.setUserRegion(true);
						region.setMobile(true);
						plottingSystem.addRegion(region);
					}
				} catch (Exception e) {
					logger.error("Cannot create region for perimeter PlotView!");
				}
			}
		});
	}

	/**
	 * Method to create the PlotView toggle actions to switch 
	 * between perimeter and average profiles
	 */
	private void addToggleActions() {
		if (plotAverageACI == null) {
			plotAverage = new Action("Plot Average Box Profiles", IAction.AS_CHECK_BOX) {
				@Override
				public void run() {
					if(isChecked()){
						sideProfile1.setPlotAverageProfile(true);
						sideProfile2.setPlotAverageProfile(true);
					}
					else{
						sideProfile1.setPlotAverageProfile(false);
						sideProfile2.setPlotAverageProfile(false);
					}
					sideProfile1.update(region);
					sideProfile2.update(region);
				}
			};
			plotAverage.setToolTipText("Toggle On/Off Average Profiles");
			plotAverage.setText("Toggle On/Off Average Profiles");
			plotAverage.setImageDescriptor(AnalysisRCPActivator.getImageDescriptor("icons/average.png"));
			plotAverageACI = new ActionContributionItem(plotAverage);
		}

		if(plotEdgeACI == null){
			plotEdge = new Action("Plot Edge Box Profiles", IAction.AS_CHECK_BOX) {
				@Override
				public void run() {
					if(isChecked()){
						sideProfile1.setPlotEdgeProfile(true);
						sideProfile2.setPlotEdgeProfile(true);
					}
					else{
						sideProfile1.setPlotEdgeProfile(false);
						sideProfile2.setPlotEdgeProfile(false);
					}
					sideProfile1.update(region);
					sideProfile2.update(region);
				}
			};
			plotEdge.setToolTipText("Toggle On/Off Perimeter Profiles");
			plotEdge.setText("Toggle On/Off Perimeter Profiles");
			plotEdge.setChecked(true);
			plotEdge.setImageDescriptor(AnalysisRCPActivator.getImageDescriptor("icons/edge-color-box.png"));
			plotEdgeACI = new ActionContributionItem(plotEdge);
		}

		plottingSystem.getActionBars().getToolBarManager().add(new Separator());
		plottingSystem.getActionBars().getToolBarManager().add(plotEdgeACI);
		plottingSystem.getActionBars().getToolBarManager().add(plotAverageACI);
		plottingSystem.getActionBars().getToolBarManager().add(new Separator());
//		bars.getMenuManager().add(new Separator());
//		bars.getMenuManager().add(plotEdgeACI);
//		bars.getMenuManager().add(plotAverageACI);
//		bars.getMenuManager().add(new Separator());
	}

	@Override
	public void updatePlotMode(final GuiPlotMode plotMode) {
		DisplayUtils.asyncExec(parentComp, new Runnable() {
			@Override
			public void run() {
				GuiPlotMode oldMode = getPreviousMode();
				if (plotMode.equals(GuiPlotMode.ONED) && oldMode != GuiPlotMode.ONED) {
					plotConnection = PlotConnectionFactory.getConnection(plotMode, plottingSystem);
					setPreviousMode(GuiPlotMode.ONED);
				} else if (plotMode.equals(GuiPlotMode.TWOD) && oldMode != GuiPlotMode.TWOD) {
					plotConnection = PlotConnectionFactory.getConnection(plotMode, plottingSystem);
					addToggleActions();
					setPreviousMode(GuiPlotMode.TWOD);
				} else if (plotMode.equals(GuiPlotMode.SCATTER2D) && oldMode != GuiPlotMode.SCATTER2D) {
					plotConnection = PlotConnectionFactory.getConnection(plotMode, plottingSystem);
					setPreviousMode(GuiPlotMode.SCATTER2D);
				} else if (plotMode.equals(GuiPlotMode.EMPTY) && oldMode != GuiPlotMode.EMPTY) {
					clearPlot();
					setPreviousMode(GuiPlotMode.EMPTY);
				}
//				addScriptingAction();
				addDuplicateAction();
			}
		});
	}

	@Override
	public void clearPlot() {
		if (plottingSystem != null) {
			plottingSystem.clearRegions();
			plottingSystem.reset();
			plottingSystem.repaint();
		}
	}

	@Override
	public void processGUIUpdate(GuiBean bean) {
		if (parentComp != null && !parentComp.isDisposed()) {
			super.processGUIUpdate(bean);
		}
	}

	@Override
	public void dispose() {
		PlotWindowManager.getPrivateManager().unregisterPlotWindow(this);
		if (plotConnection != null) {
			plotConnection.deactivate(false);
			plotConnection.dispose();
		}
		try {
			plottingSystem.getActionBars().getToolBarManager().remove(plotEdgeACI);
			plottingSystem.getActionBars().getToolBarManager().remove(plotAverageACI);
			bars.getMenuManager().remove(plotEdgeACI);
			bars.getMenuManager().remove(plotAverageACI);
			plottingSystem.removeRegionListener(getRoiManager());
			plottingSystem.removeTraceListener(getRoiManager().getTraceListener());
			plottingSystem.dispose();
			sideProfile1.dispose();
			sideProfile2.dispose();
			myROIWidget.dispose();
			roiSumProfile.dispose();
			verticalProfileROIWidget.dispose();
			horizontalProfileROIWidget.dispose();
		} catch (Exception ne) {
			logger.debug("Cannot clean up plotter!", ne);
		}
		deleteIObservers();
		plotConnection = null;
		System.gc();
	}
}
