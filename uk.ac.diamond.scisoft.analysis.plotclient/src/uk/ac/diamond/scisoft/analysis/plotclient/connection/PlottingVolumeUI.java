/*
 * Copyright (c) 2012, 2015 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package uk.ac.diamond.scisoft.analysis.plotclient.connection;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.dawnsci.plotting.api.IPlottingSystem;
import org.eclipse.dawnsci.plotting.api.trace.IVolumeTrace;
import org.eclipse.january.dataset.Dataset;
import org.eclipse.january.dataset.IDataset;
import org.eclipse.swt.widgets.Display;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.plotserver.AxisMapBean;
import uk.ac.diamond.scisoft.analysis.plotserver.DataBean;
import uk.ac.diamond.scisoft.analysis.plotserver.DatasetWithAxisInformation;

/**
 * Class to create the a 2D/image plotting
 */
class PlottingVolumeUI extends PlottingGUIUpdate {
	
	private static final Logger logger = LoggerFactory.getLogger(PlottingVolumeUI.class);

	public PlottingVolumeUI(IPlottingSystem<?> plotter) {
		super(plotter);
	}

	@Override
	public void processPlotUpdate(final DataBean dbPlot, boolean isUpdate) {
		Display.getDefault().asyncExec(new Runnable() {
			@Override
			public void run() {
				Collection<DatasetWithAxisInformation> plotData = dbPlot.getData();
				if (plotData != null) {
					Iterator<DatasetWithAxisInformation> iter = plotData.iterator();
					final List<Dataset> yDatasets = Collections.synchronizedList(new LinkedList<Dataset>());

					final Dataset xAxisValues = dbPlot.getAxis(AxisMapBean.XAXIS);
					final Dataset yAxisValues = dbPlot.getAxis(AxisMapBean.YAXIS);
					final Dataset zAxisValues = dbPlot.getAxis(AxisMapBean.ZAXIS);

					if (iter.hasNext()) {
						DatasetWithAxisInformation dataSetAxis = iter.next();
						Dataset data = dataSetAxis.getData();
						yDatasets.add(data);
					}
					
					sanitizeName(xAxisValues,"X");
					sanitizeName(yAxisValues,"Y");
					sanitizeName(zAxisValues,"Z");
					
					IDataset[] axesArray = new IDataset[3];
					axesArray[0] = xAxisValues;
					axesArray[1] = yAxisValues;
					axesArray[2] = zAxisValues;
 					Dataset data = yDatasets.get(0);
 					plottingSystem.clear();
 					
					if (data != null) {
						
						IVolumeTrace volumeTrace = plottingSystem.createTrace("volume", IVolumeTrace.class);
						
						volumeTrace.setData(data, axesArray, data.min(), data.max());
						
						plottingSystem.addTrace(volumeTrace);
						
					} else {
						logger.debug("No data to plot");
					}
				}
				
				
			}
			
			private void sanitizeName(IDataset d, String axis) {
				if (d == null) return;
				String name = d.getName();
				if (name == null || name.isEmpty()) d.setName(axis);
				
			}
		});
	}
}
