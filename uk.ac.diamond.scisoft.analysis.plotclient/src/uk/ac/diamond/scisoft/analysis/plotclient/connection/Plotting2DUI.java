/*
 * Copyright (c) 2012, 2015 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package uk.ac.diamond.scisoft.analysis.plotclient.connection;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.dawnsci.plotting.api.IPlottingSystem;
import org.eclipse.dawnsci.plotting.api.trace.IAxesTrace;
import org.eclipse.dawnsci.plotting.api.trace.ITrace;
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
class Plotting2DUI extends PlottingGUIUpdate {
	
	private static final Logger logger = LoggerFactory.getLogger(Plotting2DUI.class);

	public Plotting2DUI(IPlottingSystem<?> plotter) {
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
					List<IDataset> axes = Collections.synchronizedList(new LinkedList<IDataset>());

					String xAxisName = "";
					if (xAxisValues != null) {
						axes.add(xAxisValues);
						xAxisName = xAxisValues.getName();
					} else {
						axes.add(null);
					}
					String yAxisName = "";
					if (yAxisValues != null) {
						axes.add(yAxisValues);
						yAxisName = yAxisValues.getName();
					} else {
						axes.add(null);
					}
					if (axes.get(0) == null && axes.get(1) == null) {
						axes = null;
					}

					while (iter.hasNext()) {
						DatasetWithAxisInformation dataSetAxis = iter.next();
						Dataset data = dataSetAxis.getData();
						yDatasets.add(data);
					}

					Dataset data = yDatasets.get(0);
					if (data != null) {

						final Collection<ITrace> traces = plottingSystem.getTraces();
						if (traces != null && traces.size() > 0) {
							ITrace trace = traces.iterator().next();
							List<IDataset> currentAxes = null;
							int[] shape = trace.getData() != null ? trace.getData().getShape() : null;
							if (trace instanceof IAxesTrace) {
								currentAxes = ((IAxesTrace) trace).getAxes();
							}
							boolean newAxes = true;
							String lastXAxisName = "", lastYAxisName = "";
							if (currentAxes != null && currentAxes.size() > 0) {
								lastXAxisName = currentAxes.get(0).getName();
								lastYAxisName = currentAxes.get(1).getName();
								newAxes = !currentAxes.equals(axes);
							}

							if (shape != null && Arrays.equals(shape, data.getShape())
									&& lastXAxisName.equals(xAxisName) && lastYAxisName.equals(yAxisName)) {
								plottingSystem.updatePlot2D(data, axes, null);
								logger.debug("Plot 2D updated");
							} else {
								plottingSystem.createPlot2D(data, axes, null);
								logger.debug("Plot 2D created");
							}
							if (newAxes) {
								plottingSystem.repaint();
							}
						} else {
							plottingSystem.createPlot2D(data, axes, null);
							logger.debug("Plot 2D created");
						}
						// COMMENTED TO FIX SCI-808: no need for a repaint
						// plottingSystem.repaint();
					} else {
						logger.debug("No data to plot");
					}
				}
			}
		});
	}
}
