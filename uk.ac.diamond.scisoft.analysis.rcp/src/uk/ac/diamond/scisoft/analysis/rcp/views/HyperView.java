/*-
 * Copyright 2012 Diamond Light Source Ltd.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.ac.diamond.scisoft.analysis.rcp.views;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.dawb.common.ui.plot.PlottingFactory;
import org.dawb.common.ui.util.GridUtils;
import org.dawb.common.ui.widgets.ActionBarWrapper;
import org.dawnsci.plotting.api.IPlottingSystem;
import org.dawnsci.plotting.api.PlotType;
import org.dawnsci.plotting.api.region.IROIListener;
import org.dawnsci.plotting.api.region.IRegion;
import org.dawnsci.plotting.api.region.IRegion.RegionType;
import org.dawnsci.plotting.api.region.IRegionListener;
import org.dawnsci.plotting.api.region.ROIEvent;
import org.dawnsci.plotting.api.region.RegionEvent;
import org.dawnsci.plotting.api.region.RegionUtils;
import org.dawnsci.plotting.api.trace.ColorOption;
import org.dawnsci.plotting.api.trace.ILineTrace;
import org.dawnsci.plotting.api.trace.ITrace;
import org.dawnsci.plotting.api.trace.TraceUtils;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.ActionContributionItem;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IContributionItem;
import org.eclipse.jface.action.Separator;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.ui.part.ViewPart;

import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.analysis.dataset.DatasetUtils;
import uk.ac.diamond.scisoft.analysis.dataset.IDataset;
import uk.ac.diamond.scisoft.analysis.dataset.ILazyDataset;
import uk.ac.diamond.scisoft.analysis.io.ASCIIDataWithHeadingSaver;
import uk.ac.diamond.scisoft.analysis.io.DataHolder;
import uk.ac.diamond.scisoft.analysis.rcp.AnalysisRCPActivator;
import uk.ac.diamond.scisoft.analysis.rcp.inspector.AxisChoice;
import uk.ac.diamond.scisoft.analysis.roi.IROI;
import uk.ac.diamond.scisoft.analysis.roi.ROISliceUtils;
import uk.ac.diamond.scisoft.analysis.roi.RectangularROI;
import uk.ac.diamond.scisoft.analysis.roi.XAxisBoxROI;

/**
 * Display a 3D dataset across two plots with ROI slicing
 */
public class HyperView extends ViewPart {
	
	public static final String ID = "uk.ac.diamond.scisoft.analysis.rcp.views.HyperPlotView";
	private IPlottingSystem mainSystem;
	private IPlottingSystem sideSystem;
	private ILazyDataset lazy;
	private List<AxisChoice> daxes;
	private IRegionListener regionListenerLeft;
	private IROIListener roiListenerLeft;
	private IROIListener roiListenerRight;
	private HyperJob leftJob;
	private HyperSideJob rightJob;
	private IAction reselect;
	private IAction baseline;
	private int traceDim;
	private IRegion windowRegion;
	private Composite mainComposite;
	
	@Override
	public void createPartControl(Composite parent) {
		parent.setLayout(new FillLayout());
		SashForm sashForm = new SashForm(parent, SWT.HORIZONTAL);
		sashForm.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true,2,1));
		sashForm.setBackground(new Color(parent.getDisplay(), 192, 192, 192));
		
		createPlottingSystems(sashForm);
	}
	
	public void setData(ILazyDataset lazy, List<AxisChoice> daxes, int traceDim) {
		
		this.lazy = lazy;
		this.daxes = daxes;
		this.leftJob = new HyperJob();
		this.rightJob = new HyperSideJob();
		this.traceDim = traceDim;
		
		mainSystem.clear();
		
		for (IRegion region : mainSystem.getRegions()) {
			mainSystem.removeRegion(region);
		}
		
		sideSystem.clear();
		
		for (IRegion region : sideSystem.getRegions()) {
			sideSystem.removeRegion(region);
		}

		int[] axPos = getImageAxis();
		int[] imageSize = new int[]{lazy.getShape()[axPos[1]],lazy.getShape()[axPos[0]]};
		
		try {
			IRegion region = mainSystem.createRegion("testRegion", RegionType.BOX);
			
			mainSystem.addRegion(region);
			//TODO make roi positioning a bit more clever
			RectangularROI rroi = new RectangularROI(imageSize[1]/10, imageSize[0]/10, imageSize[1]/10, imageSize[0]/10, 0);
			region.setROI(rroi);
			region.setUserRegion(false);
			region.addROIListener(this.roiListenerLeft);
			
			updateRight(region, rroi);
			
			windowRegion = sideSystem.createRegion("testRegion2", RegionType.XAXIS);
			
			sideSystem.addRegion(windowRegion);
			
			double min = daxes.get(traceDim).getValues().getSlice().min().doubleValue();
			double max = daxes.get(traceDim).getValues().getSlice().max().doubleValue();
			
			XAxisBoxROI broi = new XAxisBoxROI(min,0,(max-min)/10, 0, 0);
			windowRegion.setROI(broi);
			windowRegion.setUserRegion(false);
			windowRegion.addROIListener(this.roiListenerRight);
			updateLeft(broi);
			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}
	
private void saveLineTracesAsAscii(String filename) {
		
		Collection<ITrace> traces = sideSystem.getTraces(ILineTrace.class);
		
		boolean firstTrace = true;
		List<IDataset> datasets = new ArrayList<IDataset>();
		List<String> headings = new ArrayList<String>();
		IDataset data;
		
		int dtype = 0;
		
		int i = 0;
		
		for (ITrace trace : traces ) {
			
			if (firstTrace) {
				int ddtype = AbstractDataset.getDType(((ILineTrace)trace).getData());
				data = ((ILineTrace)trace).getXData();
				int axdtype = AbstractDataset.getDType(data);
				
				if (ddtype == axdtype) {
					dtype = ddtype;
				} else if (ddtype > axdtype) {
					data = DatasetUtils.cast((AbstractDataset)data, ddtype);
					dtype = ddtype;
				} else {
					dtype = axdtype;
				}
				
				data.setShape(data.getShape()[0],1);
				datasets.add(data);
				headings.add("x");
				firstTrace = false;
			}
			
			data = ((ILineTrace)trace).getData();
			
			if (dtype != AbstractDataset.getDType(data)) {
				data = DatasetUtils.cast((AbstractDataset)data, dtype);
			}
			
			data.setShape(data.getShape()[0],1);
			datasets.add(data);
			headings.add("dataset_" + i);
			i++;
		}
		
		AbstractDataset allTraces = DatasetUtils.concatenate(datasets.toArray(new IDataset[datasets.size()]), 1);
		
		ASCIIDataWithHeadingSaver saver = new ASCIIDataWithHeadingSaver(filename);
		DataHolder dh = new DataHolder();
		dh.addDataset("AllTraces", allTraces);
		saver.setHeader("#Traces extracted from Hyperview");
		saver.setHeadings(headings);
		
		try {
			saver.saveFile(dh);
		} catch(Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void setFocus() {
		mainComposite.setFocus();
	}
	
	private void createPlottingSystems(SashForm sashForm) {
		try {
			mainSystem = PlottingFactory.createPlottingSystem();
			mainSystem.setColorOption(ColorOption.NONE);
			mainComposite = new Composite(sashForm, SWT.NONE);
			mainComposite.setLayout(new GridLayout(1, false));
			GridUtils.removeMargins(mainComposite);

			ActionBarWrapper actionBarWrapper = ActionBarWrapper.createActionBars(mainComposite, null);

			reselect = new Action("Create new profile", SWT.TOGGLE) {
				@Override
				public void run() {
					if (reselect.isChecked()) {
						createNewRegion();
					} else {
						IContributionItem item = mainSystem.getActionBars().getToolBarManager().find("org.csstudio.swt.xygraph.undo.ZoomType.NONE");
						if (item != null && item instanceof ActionContributionItem) {
							((ActionContributionItem)item).getAction().run();
						}
					}
				}
			};
			
			reselect.setImageDescriptor(AnalysisRCPActivator.getImageDescriptor("icons/ProfileBox2.png"));
			
			actionBarWrapper.getToolBarManager().add(new Separator("uk.ac.diamond.scisoft.analysis.rcp.views.HyperPlotView.newProfileGroup"));
			actionBarWrapper.getToolBarManager().add(reselect);
			actionBarWrapper.getToolBarManager().add(new Separator("uk.ac.diamond.scisoft.analysis.rcp.views.HyperPlotView.newProfileGroupAfter"));
			
			Composite displayPlotComp  = new Composite(mainComposite, SWT.BORDER);
			displayPlotComp.setLayout(new FillLayout());
			displayPlotComp.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
			mainSystem.createPlotPart(displayPlotComp, 
													 "HyperImage", 
													 actionBarWrapper, 
													 PlotType.IMAGE, 
													 null);
			
			mainSystem.repaint();
			
			sideSystem = PlottingFactory.createPlottingSystem();
			sideSystem.setColorOption(ColorOption.NONE);
			Composite sideComp = new Composite(sashForm, SWT.NONE);
			sideComp.setLayout(new GridLayout(1, false));
			GridUtils.removeMargins(sideComp);
			ActionBarWrapper actionBarWrapper1 = ActionBarWrapper.createActionBars(sideComp, null);
			Composite sidePlotComp  = new Composite(sideComp, SWT.BORDER);
			sidePlotComp.setLayout(new FillLayout());
			sidePlotComp.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
			
			baseline = new Action("Linear baseline", SWT.TOGGLE) {
				@Override
				public void run() {
					IROI roi = windowRegion.getROI();
					updateLeft(roi);
				}
			};
			
			IAction export = new Action("Export...") {
				@Override
				public void run() {
					FileDialog fd = new FileDialog(HyperView.this.getSite().getShell(),SWT.SAVE);
					fd.setFileName("export.dat");
					final String path = fd.open();
					
					if (path == null) return;
					
					File file = new File(path);
					//TODO throw error
					if (file.exists()) return;
					
					Job exportJob = new Job("Export") {
						
						@Override
						protected IStatus run(IProgressMonitor monitor) {
							saveLineTracesAsAscii(path);
							return Status.OK_STATUS;
						}
					};
					exportJob.schedule();
				}
			};
			
			baseline.setImageDescriptor(AnalysisRCPActivator.getImageDescriptor("icons/LinearBase.png"));
			actionBarWrapper1.getToolBarManager().add(new Separator("uk.ac.diamond.scisoft.analysis.rcp.views.HyperPlotView.newBaselineGroup"));
			actionBarWrapper1.getToolBarManager().add(baseline);
			actionBarWrapper1.getToolBarManager().add(export);
			actionBarWrapper1.getToolBarManager().add(new Separator("uk.ac.diamond.scisoft.analysis.rcp.views.HyperPlotView.newBaselineGroup"));
			
			sideSystem.createPlotPart(sidePlotComp, 
													 "HyperTrace", 
													 actionBarWrapper1, 
													 PlotType.XY, 
													 null);
			sideSystem.repaint();
			
			regionListenerLeft = getRegionListenerToLeft();
			mainSystem.addRegionListener(regionListenerLeft);
			roiListenerLeft = getROIListenerToRight();
			roiListenerRight = getROIListenerLeft();
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	protected final void createNewRegion() {
		// Start with a selection of the right type
		try {
			IRegion region = mainSystem.createRegion(RegionUtils.getUniqueName("box", mainSystem), RegionType.BOX);
			region.addROIListener(roiListenerLeft);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private IRegionListener getRegionListenerToLeft() {
		return new IRegionListener() {
			
			@Override
			public void regionsRemoved(RegionEvent evt) {
				
				for(ITrace trace : sideSystem.getTraces(ILineTrace.class)) {
					if (trace.getUserObject() instanceof IRegion) {
						if (((IRegion)trace.getUserObject()).isUserRegion()) {
							sideSystem.removeTrace(trace);
						}
					}
				}
			}
			
			@Override
			public void regionRemoved(RegionEvent evt) {
				
				for(ITrace trace : sideSystem.getTraces(ILineTrace.class)) {
					if (trace.getUserObject() == evt.getSource()) {
						sideSystem.removeTrace(trace);
					}
				}
			}
			
			@Override
			public void regionCreated(RegionEvent evt) {
				
			}
			
			@Override
			public void regionCancelled(RegionEvent evt) {
				// TODO Auto-generated method stub
				
			}
			
			@Override
			public void regionAdded(RegionEvent evt) {
				if (evt.getRegion() != null) {
					evt.getRegion().setUserRegion(true);
					evt.getRegion().addROIListener(roiListenerLeft);
					
					if (reselect.isChecked()) {
						createNewRegion();
					}
					
				}
				
			}
		};
	}
	
	private IROIListener getROIListenerToRight() {
		return new IROIListener() {
			
			@Override
			public void roiSelected(ROIEvent evt) {
				// TODO Auto-generated method stub
				
			}
			
			@Override
			public void roiDragged(ROIEvent evt) {
				updateRight((IRegion)evt.getSource(), evt.getROI());
				
			}
			
			@Override
			public void roiChanged(ROIEvent evt) {
				updateRight((IRegion)evt.getSource(), evt.getROI());
			}
		};
	}
	
	private IROIListener getROIListenerLeft() {
		return new IROIListener() {
			
			@Override
			public void roiSelected(ROIEvent evt) {
				// TODO Auto-generated method stub
				
			}
			
			@Override
			public void roiDragged(ROIEvent evt) {
				updateLeft(evt.getROI());
				
			}
			
			@Override
			public void roiChanged(ROIEvent evt) {
				updateLeft(evt.getROI());
				
			}
		};
	}
	
	private int[] getImageAxis() {
		int[] allDims = new int[]{2,1,0};
		int[] dims = new int[2];
		
		int i =0;
		for(int j : allDims) {
			if (j != traceDim) {
				dims[i] = j;
				i++;
			}
		}
		
		return dims;
	}
	
	protected synchronized void updateRight(IRegion r, IROI rb) {
		
		leftJob.profile(r, rb);
	}
	
	protected synchronized void updateLeft(IROI rb) {

		rightJob.profile(rb);
	}

	private final class HyperJob extends Job {

		private   IRegion                currentRegion;
		private   IROI                currentROI;

		HyperJob() {
			super("update");
			setSystem(true);
			setUser(false);
			setPriority(Job.INTERACTIVE);
		}

		public void profile(IRegion r, IROI rb) {

			this.currentRegion = r;
			this.currentROI    = rb;
	        
          	schedule();		
		}

		@Override
		protected IStatus run(IProgressMonitor monitor) {

			try {
				if (currentROI instanceof RectangularROI) {
					int[] dims = getImageAxis();
					
					//TODO check the dims used in the mean are sensible
					Collection<ITrace> traces = sideSystem.getTraces();
					for (ITrace trace : traces) {
						Object uo = trace.getUserObject();
						if (uo == currentRegion) {
							final IDataset dataset1 = ((AbstractDataset)ROISliceUtils.getDataset(lazy, (RectangularROI)currentROI, dims)).mean(dims[0]).mean(dims[1]);
							dataset1.setName(trace.getName());
							
							Display.getDefault().syncExec(new Runnable() {
								@Override
								public void run() {
									sideSystem.updatePlot1D(daxes.get(traceDim).getValues().getSlice(),Arrays.asList(new IDataset[] {dataset1}), null);
									sideSystem.repaint();						
								}
							});
							
							return Status.OK_STATUS;
						}
					}
					final IDataset dataset1 = ((AbstractDataset)ROISliceUtils.getDataset(lazy, (RectangularROI)currentROI, dims)).mean(dims[0]).mean(dims[1]);
					String name = TraceUtils.getUniqueTrace("trace", sideSystem, (String[])null);
					dataset1.setName(name);
					
					Display.getDefault().syncExec(new Runnable() {
						@Override
						public void run() {
							List<ITrace> traceOut = sideSystem.createPlot1D(daxes.get(traceDim).getValues().getSlice(),Arrays.asList(new IDataset[] {dataset1}), null);
							
							for (ITrace trace : traceOut) {
								trace.setUserObject(currentRegion);
								if (trace instanceof ILineTrace){
									currentRegion.setRegionColor(((ILineTrace)trace).getTraceColor());
								}
							}					
						}
					});
				}
			} catch (Throwable ne) {
				return Status.CANCEL_STATUS;
			}
			return Status.OK_STATUS;
		}
	}
	
	private final class HyperSideJob extends Job {

		private   IROI                currentROI;

		HyperSideJob() {
			super("update");
			setSystem(true);
			setUser(false);
			setPriority(Job.INTERACTIVE);
		}

		public void profile(IROI rb) {

			this.currentROI    = rb;
	        
          	schedule();		
		}

		@Override
		protected IStatus run(IProgressMonitor monitor) {

			try {
				if (currentROI instanceof RectangularROI) {
					
					final AbstractDataset image = ((AbstractDataset)ROISliceUtils.getAxisDatasetTrapzSum(daxes.get(traceDim).getValues().getSlice(),lazy, (RectangularROI)currentROI, traceDim));
					
					if (baseline.isChecked()) {
						final IDataset datasetBasline = ROISliceUtils.getTrapiziumArea(daxes.get(traceDim).getValues().getSlice(),lazy, (RectangularROI)currentROI, traceDim);
						image.isubtract(datasetBasline);
					}
					
					image.setName("image");
					Display.getDefault().syncExec(new Runnable() {
						@Override
						public void run() {
							mainSystem.updatePlot2D(image, null, null);						
						}
					});
				}
			} catch (Throwable ne) {
				ne.printStackTrace();
				return Status.CANCEL_STATUS;
			}
			return Status.OK_STATUS;
		}
	}
}
