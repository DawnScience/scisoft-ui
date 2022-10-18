/*
 * Copyright (c) 2012 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.analysis.rcp.util;


import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.january.dataset.Dataset;
import org.eclipse.january.dataset.DatasetFactory;
import org.eclipse.january.dataset.DatasetUtils;
import org.eclipse.january.dataset.DoubleDataset;
import org.eclipse.january.dataset.IDataset;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PlatformUI;

import uk.ac.diamond.daq.util.logging.deprecation.DeprecationLogger;
import uk.ac.diamond.scisoft.analysis.plotserver.AxisMapBean;
import uk.ac.diamond.scisoft.analysis.plotserver.DataBean;
import uk.ac.diamond.scisoft.analysis.plotserver.DataBeanException;
import uk.ac.diamond.scisoft.analysis.plotserver.DatasetWithAxisInformation;
import uk.ac.diamond.scisoft.analysis.plotserver.GuiBean;
import uk.ac.diamond.scisoft.analysis.plotserver.GuiParameters;
import uk.ac.diamond.scisoft.analysis.plotserver.GuiPlotMode;
import uk.ac.diamond.scisoft.analysis.rcp.plotting.AbstractPlotWindow;

@Deprecated(since="Dawn 1.7.1")
public class PlotUtils {

	private static final DeprecationLogger logger = DeprecationLogger.getLogger(PlotUtils.class);
	
	private PlotUtils() {
		
	}
	
	
	/**
	 * Thread safe
	 * @param plotWindow 
	 * @param monitor 
	 * @param plotMode 
	 * @param xDataSet 
	 * @param yDataSets 
	 */
	public static void create1DPlot(final Dataset         xDataSet, 
			                        final List<Dataset>   yDataSets, 
			                        final PlotMode         plotMode, 
			                        final AbstractPlotWindow       plotWindow, 
			                        final IProgressMonitor monitor) {
		logger.deprecatedMethod("create1DPlot(Dataset, List<Dataset>, PlotMode, AbstractPlotWindow, IProgressMonitor)");
		if (xDataSet.getRank() != 1) return;

		// We allow yDataSets to be null if they like.
		final Dataset x;
		final List<Dataset> ys;
		if (yDataSets==null) {
			ys = new ArrayList<Dataset>(1);
			ys.add(xDataSet);
			x = DatasetFactory.createRange(DoubleDataset.class, ys.get(0).getSize());
		} else {
			x  = xDataSet;
			ys = yDataSets;
		}
		
		final Display display = PlatformUI.getWorkbench().getDisplay();
		if (display.isDisposed()) return;
		
		display.asyncExec(new Runnable() {
			@Override
			public void run() {
				try {
					plotWindow.updatePlotMode(plotMode.getGuiPlotMode());

					if (monitor!=null&&monitor.isCanceled()) return;
					
					// generate the bean to send the Gui information
					GuiBean guiBean = new GuiBean();
					guiBean.put(GuiParameters.PLOTMODE, plotMode.getGuiPlotMode());
					guiBean.put(GuiParameters.TITLE,    getTitle(x, ys, true));
					plotWindow.processGUIUpdate(guiBean);
					if (monitor!=null&&monitor.isCanceled()) return;

					DataBean dataBean = new DataBean(plotMode.getGuiPlotMode());
					dataBean.addAxis(AxisMapBean.XAXIS, x);
					
					// TODO use PM3D for z, currently hard codes something, in process of fixing.
					if (PlotMode.PM3D==plotMode) {
						final Dataset z = DatasetFactory.createFromObject(new double[]{-15,1,200});
						dataBean.addAxis(AxisMapBean.ZAXIS, z);
					}

					for (int i = 0; i < ys.size(); i++) {
						// now add it to the plot data
						try {
							dataBean.addData(DatasetWithAxisInformation.createAxisDataSet(ys.get(i)));
							if (monitor!=null&&monitor.isCanceled()) return;
						} catch (DataBeanException e) {
							logger.error("Problem adding data to bean as axis key does not exist", e);
						}
					}

					if (monitor!=null&&monitor.isCanceled()) return;
					plotWindow.processPlotUpdate(dataBean);
					
				} catch (Exception ne) {
					logger.error("Cannot create plot required.", ne);
				}
			}
			
		});
	}
	
	/**
	 * Attempts to create a plot with the data passed in.
	 * 
	 * Thread safe.
	 * 
	 * @param data
	 * @param axes
	 * @param mode
	 * @param plotWindow
	 * @param monitor
	 */
	public static void createPlot(final Dataset       data,
			                      final List<Dataset> axes,
			                      final GuiPlotMode           mode, 
			                      final AbstractPlotWindow    plotWindow, 
			                      final IProgressMonitor monitor) {
		
		logger.deprecatedMethod("createPlot(Dataset, List<Dataset>, GuiPlotMode, AbstractPlotWindow, IProgressMonitor)");
		final Display display = PlatformUI.getWorkbench().getDisplay();
		if (display.isDisposed()) return;
		
		display.asyncExec(new Runnable() {
			@Override
			public void run() {
				try {
					plotWindow.updatePlotMode(mode);

					if (monitor!=null&&monitor.isCanceled()) return;
					
					// generate the bean to send the Gui information
					GuiBean guiBean = new GuiBean();
					guiBean.put(GuiParameters.PLOTMODE, mode);
					guiBean.put(GuiParameters.TITLE,    data.getName());
					plotWindow.processGUIUpdate(guiBean);
					if (monitor!=null&&monitor.isCanceled()) return;

					DataBean dataBean = new DataBean(mode);
					DatasetWithAxisInformation axisData = new DatasetWithAxisInformation();
					AxisMapBean axisMapBean = new AxisMapBean();
					
					dataBean.addAxis(AxisMapBean.XAXIS, axes.get(0));
					dataBean.addAxis(AxisMapBean.YAXIS, axes.get(1));
					// note that the DataSet plotter's 2D mode is row-major
					axisData.setData(DatasetUtils.transpose(data, new int[] {1, 0}));
					axisData.setAxisMap(axisMapBean);

					dataBean.addData(axisData);

					if (monitor!=null&&monitor.isCanceled()) return;
					plotWindow.processPlotUpdate(dataBean);
					
				} catch (Exception ne) {
					logger.error("Cannot create plot required.", ne);
				}
			}
			
		});
	}


	private static Serializable getTitle(Dataset x, List<Dataset> ys, final boolean isFileName) {
		logger.deprecatedMethod("getTitle(Dataset, List<Dataset>, boolean)");

		final StringBuilder buf = new StringBuilder();
		buf.append("Plot of");
		final Set<String> used = new HashSet<String>(7);
		for (IDataset dataSet : ys) {
			String name = dataSet.getName();
			
			if (isFileName) {
			    // Strip off file name
				final Matcher matcher = Pattern.compile("(.*) \\(.*\\)").matcher(name);
				if (matcher.matches()) name = matcher.group(1);
			}
			
			if (used.contains(name)) continue;
			used.add(name);
			buf.append(" ");
			buf.append(name);
			buf.append(",");
		}
		final int index = buf.length()-1;
		buf.delete(index, index+1);
		buf.append(" against ");
		buf.append(x.getName());
		return buf.toString();
	}



}
