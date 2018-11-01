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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.dawnsci.plotting.api.IPlottingSystem;
import org.eclipse.dawnsci.plotting.api.trace.ILineStackTrace;
import org.eclipse.dawnsci.plotting.api.trace.ITrace;
import org.eclipse.dawnsci.plotting.api.trace.IWaterfallTrace;
import org.eclipse.january.dataset.Dataset;
import org.eclipse.january.dataset.DatasetUtils;
import org.eclipse.january.dataset.IDataset;
import org.eclipse.swt.widgets.Display;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.plotserver.AxisMapBean;
import uk.ac.diamond.scisoft.analysis.plotserver.DataBean;
import uk.ac.diamond.scisoft.analysis.plotserver.DatasetWithAxisInformation;
import uk.ac.diamond.scisoft.analysis.plotserver.GuiBean;
import uk.ac.diamond.scisoft.analysis.plotserver.GuiParameters;

/**
 * Class to create a 3D Stack plotting
 * TODO: correctly set axes name
 */
class Plotting1DStackUI extends AbstractPlotConnection {

	public final static String STATUSITEMID = "uk.ac.diamond.scisoft.analysis.rcp.plotting.Plotting1DStackUI";
	private static final Logger logger = LoggerFactory.getLogger(Plotting1DStackUI.class);

	private IPlottingSystem<?> plottingSystem;
	/**
	 * Constructor of a plotting 1D 3D stack
	 * @param plottingSystem plotting system
	 */
	public Plotting1DStackUI(IPlottingSystem<?> plottingSystem) {
		this.plottingSystem = plottingSystem;
	}

	@Override
	public void processPlotUpdate(final DataBean dbPlot, boolean isUpdate) {

		Display.getDefault().asyncExec(new Runnable() {
			@Override
			public void run() {
				List<DatasetWithAxisInformation> plotData = dbPlot.getData();
				if (plotData == null)
					return;

				final int n = plotData.size();
				if (n == 0)
					return;

				// single stack trace with multiple plots

				// work out if more than one x axis supplied
				String xName = null;
				Set<String> xNames = new HashSet<String>();
				for (int i = 0; i < n; i++) {
					DatasetWithAxisInformation d = plotData.get(i);
					xNames.add(d.getAxisMap().getAxisID()[0]);
				}
				if (xNames.size() == 1)
					xName = xNames.iterator().next();

				GuiBean gb = dbPlot.getGuiParameters();
				String plotOperation = gb == null ? null : (String) gb.get(GuiParameters.PLOTOPERATION);

				// check for number of stack traces
				ITrace oldTrace = null;
				Collection<ITrace> oldTraces = plottingSystem.getTraces();
				for (ITrace t : oldTraces) {
					if (t instanceof ILineStackTrace) {
						 ILineStackTrace s = (ILineStackTrace) t;
						 if (oldTrace == null) {
							 oldTrace = s;
						 } else if (!GuiParameters.PLOTOP_UPDATE.equals(plotOperation)) {
							 plottingSystem.removeTrace(s);
						 }
					} else {
						logger.warn("Trace is not a line stack trace: {}", t);
					}
				}

				ITrace trace;
				if (oldTrace != null) {
					trace = oldTrace;
				} else {
					plottingSystem.reset();
					trace = plottingSystem.createTrace("Plots");
				}

				Dataset[] ys = new Dataset[n];
				List<IDataset> axes = new ArrayList<IDataset>();
				Map<String, Dataset> axisData = dbPlot.getAxisData();
				for (int i = 0; i < n; i++) {
					DatasetWithAxisInformation d = plotData.get(i);
					ys[i] = d.getData();
					if (xName == null)
						axes.add(axisData.get(d.getAxisMap().getAxisID()[0]));
				}
				if (xName != null) {
					axes.add(axisData.get(xName));
				}
				axes.add(null);
				axes.add(axisData.get(AxisMapBean.ZAXIS));
				if (trace instanceof ILineStackTrace) {
					((ILineStackTrace) trace).setData(axes, ys);
				} else if (trace instanceof IWaterfallTrace) {
					for (int i = 0; i < n; i++) {
						ys[i] =   ys[i].reshape(1, -1);
					}
					IDataset data = DatasetUtils.concatenate(ys, 0);
					((IWaterfallTrace) trace).setData(data , axes.toArray(new IDataset[axes.size()]));
				}

				if (trace == oldTrace) {
					logger.debug("Plot 1D 3D updated");
				} else {
					plottingSystem.addTrace(trace);
					logger.debug("Plot 1D 3D created");
				}
			}
		});
	}

}
