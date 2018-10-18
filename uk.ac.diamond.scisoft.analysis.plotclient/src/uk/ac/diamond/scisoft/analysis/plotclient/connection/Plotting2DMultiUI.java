/*
 * Copyright (c) 2012 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.analysis.plotclient.connection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.eclipse.dawnsci.plotting.api.IPlottingSystem;
import org.eclipse.dawnsci.plotting.api.axis.IAxis;
import org.eclipse.dawnsci.plotting.api.trace.IMulti2DTrace;
import org.eclipse.january.dataset.Dataset;
import org.eclipse.january.dataset.IDataset;
import org.eclipse.swt.widgets.Display;

import uk.ac.diamond.scisoft.analysis.plotserver.AxisMapBean;
import uk.ac.diamond.scisoft.analysis.plotserver.DataBean;
import uk.ac.diamond.scisoft.analysis.plotserver.DatasetWithAxisInformation;

/**
 *
 */
class Plotting2DMultiUI extends AbstractPlotConnection {

	private IPlottingSystem<?> plottingSystem;

	/**
	 * @param plottingSystem
	 */
	public Plotting2DMultiUI(IPlottingSystem<?> plottingSystem) {
		this.plottingSystem = plottingSystem;
	}

	@Override
	public void processPlotUpdate(final DataBean dbPlot, final boolean isUpdate) {
		Display.getDefault().asyncExec(new Runnable() {
			@Override
			public void run() {
				Collection<DatasetWithAxisInformation> plotData = dbPlot.getData();
				if (plotData != null) {
					Iterator<DatasetWithAxisInformation> iter = plotData.iterator();
//					final List<Dataset> datasets = Collections.synchronizedList(new LinkedList<Dataset>());
					Dataset xAxisValues = dbPlot.getAxis(AxisMapBean.XAXIS);
					Dataset yAxisValues = dbPlot.getAxis(AxisMapBean.YAXIS);
					IAxis axis = plottingSystem.getSelectedXAxis();
					if (axis != null) {
						axis.setTitle(xAxisValues.getName());
					}
					axis = plottingSystem.getSelectedYAxis();
					if (axis != null) {
						axis.setTitle(yAxisValues.getName());
					}

					IDataset[] datasets = new IDataset[plotData.size()];
					int i = 0;
					while (iter.hasNext()) {
						DatasetWithAxisInformation dataSetAxis = iter.next();
						Dataset data = dataSetAxis.getData();
						datasets[i] = data;
						i++;
					}

					List<IDataset> axes = new ArrayList<IDataset>(2);
					axes.add(xAxisValues);
					axes.add(yAxisValues);
					IMulti2DTrace multi2DTrace = plottingSystem.createMulti2DTrace(yAxisValues.getName());
					multi2DTrace.setName(xAxisValues.getName());
					multi2DTrace.setData(axes, datasets);
					plottingSystem.addTrace(multi2DTrace);
					plottingSystem.setTitle("Plot of " + yAxisValues.getName() + " against "+ xAxisValues.getName());
					plottingSystem.autoscaleAxes();

				}
			}
		});
	}	

	@Override
	public void deactivate(boolean leaveSidePlotOpen) {
		
	}
}
