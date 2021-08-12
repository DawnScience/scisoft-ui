/*-
 * Copyright 2020 Diamond Light Source Ltd.
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

package uk.ac.diamond.scisoft.analysis.rcp.plotting;

import java.util.List;

import org.dawnsci.multidimensional.ui.imagecuts.PerpendicularCutsHelper;
import org.dawnsci.multidimensional.ui.imagecuts.PerpendicularImageCutsComposite;
import org.eclipse.dawnsci.plotting.api.IPlottingService;
import org.eclipse.dawnsci.plotting.api.IPlottingSystem;
import org.eclipse.dawnsci.plotting.api.PlotType;
import org.eclipse.dawnsci.plotting.api.PlottingFactory;
import org.eclipse.dawnsci.plotting.api.trace.ColorOption;
import org.eclipse.january.dataset.Dataset;
import org.eclipse.january.dataset.DatasetUtils;
import org.eclipse.january.dataset.DoubleDataset;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IWorkbenchPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.plotclient.IPlotWindowManager;
import uk.ac.diamond.scisoft.analysis.plotclient.PlotWindowManager;
import uk.ac.diamond.scisoft.analysis.plotserver.AxisMapBean;
import uk.ac.diamond.scisoft.analysis.plotserver.DataBean;
import uk.ac.diamond.scisoft.analysis.plotserver.DatasetWithAxisInformation;
import uk.ac.diamond.scisoft.analysis.plotserver.IBeanScriptingManager;
import uk.ac.diamond.scisoft.analysis.rcp.AnalysisRCPActivator;


/**
 * Window for a plot view containing two additional plot views showing
 * perpendicular cuts of the image from the main view.
 * <p>
 * Also has the option to incrementally sum the incoming data.
 * 
 */
public class ARPESPlotWindow extends AbstractPlotWindow {
	
	private static final Logger logger = LoggerFactory.getLogger(ARPESPlotWindow.class);

	private PerpendicularImageCutsComposite composite;
	
	//Dont touch this outside of the update method!
	private ImageWithAxes sum;
	
	private boolean isSum = false;

	private PerpendicularCutsHelper helper;
	
	public static IPlotWindowManager getManager() {
		// get the private manager for use only within the framework and
		// "upcast" it to IPlotWindowManager
		return PlotWindowManager.getPrivateManager();
	}

	public ARPESPlotWindow(final Composite parent, IBeanScriptingManager manager, IActionBars bars, IWorkbenchPart part, String name) {
		super(parent, manager, bars, part, name);
		PlotWindowManager.getPrivateManager().registerPlotWindow(this);
		//Dont want to share Region updates across clients
		getRoiManager().acquireLock();
	}

	@Override
	public void createPlotControl(Composite composite) {
		
		SashForm inner = new SashForm(composite, SWT.HORIZONTAL);
		inner.setLayout(new GridLayout(2,true));
		Composite lComp = new Composite(inner,SWT.None);
		lComp.setLayout(new GridLayout());
		
		try {
			Composite buttonComp = new Composite(lComp, SWT.None);
			buttonComp.setLayoutData(GridDataFactory.fillDefaults().grab(true, false).create());
			buttonComp.setLayout(new GridLayout(3,false));
			
			this.composite = new PerpendicularImageCutsComposite(inner, SWT.None, AnalysisRCPActivator.getService(IPlottingService.class));
			this.composite.setLayoutData(GridDataFactory.fillDefaults().grab(false, true).create());
			
			IPlottingSystem<Composite> system = PlottingFactory.createPlottingSystem();
			system.setColorOption(ColorOption.NONE);
			system.createPlotPart(lComp, getName(), null, PlotType.XY, getPart());
			system.repaint();
			setPlottingSystem(system);
			system.getPlotComposite().setLayoutData(GridDataFactory.fillDefaults().grab(true, true).create());
			
			this.helper = new PerpendicularCutsHelper(system);
			helper.activate(this.composite);
			
			Button live = new Button(buttonComp, SWT.RADIO);
			live.setText("Live");
			live.setSelection(!isSum);
			live.addSelectionListener(new SelectionAdapter() {
				
				@Override
				public void widgetSelected(SelectionEvent e) {
					isSum = !live.getSelection();
					updateSum(null, null, null, true);
				}
				
			});
			
			Button sumb = new Button(buttonComp, SWT.RADIO);
			sumb.setText("Sum");
			sumb.setSelection(isSum);
			
			Button clear = new Button(buttonComp, SWT.PUSH);
			clear.setText("Clear");
			clear.addSelectionListener(new SelectionAdapter() {
				
				@Override
				public void widgetSelected(SelectionEvent e) {
					updateSum(null, null, null, true);
				}
				
			});
			
			
		} catch (Exception e) {
			logger.error("Cannot locate any plotting System!", e);
		}
	}
	
	public void processPlotUpdate(DataBean dbPlot) {
		
		List<DatasetWithAxisInformation> data = dbPlot.getData();
		Dataset d = data.get(0).getData();
		
		if (isSum) {
			final Dataset xAxisValues = dbPlot.getAxis(AxisMapBean.XAXIS);
			final Dataset yAxisValues = dbPlot.getAxis(AxisMapBean.YAXIS);
			d = updateSum(d,xAxisValues, yAxisValues, false);
			data.get(0).setData(d);
		}
		
		// there may be some gui information in the databean, if so this also needs to be updated
		if (dbPlot.getGuiParameters() != null) {
			if (plottingSystem!=null && plottingSystem.isDisposed()) {
				// this can be caused by the same plot view shown on 2 difference perspectives.
				throw new IllegalStateException("parentComp is already disposed");
			}

			processGUIUpdate(dbPlot.getGuiParameters());
		}
		
		if (plotConnection != null) {
			plotConnection.processPlotUpdate(dbPlot, isUpdatePlot());
			setDataBean(dbPlot);
			createRegion();
		}
	}
	
	synchronized private Dataset updateSum(Dataset d, Dataset xaxis, Dataset yaxis, boolean forceClear) {
		
		if (forceClear) {
			sum = null;
			return null;
		}
		
		if (sum == null) {
			sum = new ImageWithAxes(d, xaxis, yaxis);
			return d;
		}
		
		if (sum.sameAxes(xaxis, yaxis)) {
			return sum.sumImage(d);
		} else {
			sum = new ImageWithAxes(d, xaxis, yaxis);
			return d;
		}
		
	}
	
	@Override
	public void dispose() {
		composite.dispose();
		if (!plottingSystem.isDisposed()) {
			plottingSystem.dispose();
		}
	}
	
	private class ImageWithAxes {
		
		private Dataset image;
		private Dataset xaxis;
		private Dataset yaxis;
		
		public ImageWithAxes(Dataset image, Dataset xaxis, Dataset yaxis) {
			this.image = DatasetUtils.cast(DoubleDataset.class, image);
			this.xaxis = xaxis;
			this.yaxis = yaxis;
		}
		
		public boolean sameAxes(Dataset xaxis, Dataset yaxis) {
			return this.xaxis.equals(xaxis) && this.yaxis.equals(yaxis);
		}
		
		public Dataset sumImage(Dataset image) {
			return this.image.iadd(image);
		}
		
	}
	
}
