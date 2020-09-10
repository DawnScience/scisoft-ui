/*
 * Copyright (c) 2012 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.analysis.rcp.plotting;

import java.net.URL;
import java.util.HashSet;
import java.util.Set;

import org.dawb.common.ui.util.EclipseUtils;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.ui.IViewReference;
import org.eclipse.ui.PlatformUI;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;

import uk.ac.diamond.scisoft.analysis.AnalysisRpcServerProvider;
import uk.ac.diamond.scisoft.analysis.PlotServer;
import uk.ac.diamond.scisoft.analysis.PlotServerProvider;
import uk.ac.diamond.scisoft.analysis.PlotService;
import uk.ac.diamond.scisoft.analysis.PlotServiceProvider;
import uk.ac.diamond.scisoft.analysis.PythonHelper;
import uk.ac.diamond.scisoft.analysis.plotclient.IPlotWindowManager;
import uk.ac.diamond.scisoft.analysis.plotserver.DataBean;
import uk.ac.diamond.scisoft.analysis.plotserver.GuiBean;
//import uk.ac.diamond.scisoft.analysis.rcp.views.HistogramView;
//import uk.ac.diamond.scisoft.analysis.rcp.views.SidePlotView;

abstract public class RcpPlottingTestBase {

	// try and clean up the open plot view windows that are left around
	// and clear out the plot server contents of data
	private static final Set<String> viewIDsToClose = new HashSet<String>();
	static {
		viewIDsToClose.add(IPlotWindowManager.PLOT_VIEW_MULTIPLE_ID);
		// DatasetPlotter and Sideplot are deprecated
		// viewIDsToClose.add(SidePlotView.ID);
		// viewIDsToClose.add(HistogramView.ID);
	}

	// Make sure there is a plot server and service available before running this test
	@BeforeClass
	public static void beforeClass() throws Exception {
		// give everything a chance to catch up, should return immediately if there is nothing to do
		EclipseUtils.delay(30000, true);

		PlotServer plotServer = PlotServerProvider.getPlotServer();
		Assert.assertNotNull(plotServer);

		PlotService plotService = PlotServiceProvider.getPlotService();
		Assert.assertNotNull(plotService);

		IPlotWindowManager manager = PlotWindow.getManager();
		Assert.assertNotNull(manager);

		clearPlotServer();
		closeAllPlotRelatedViews();

		// give everything a chance to catch up, should return immediately if there is nothing to do
		EclipseUtils.delay(30000, true);
	}

	@AfterClass
	public static void afterClass() throws Exception {
		closeAllPlotRelatedViews();
		clearPlotServer();

		// give everything a chance to catch up, should return immediately if there is nothing to do
		EclipseUtils.delay(30000, true);
	}

	/**
	 * Remote all data from plot server.
	 * <p>
	 * There is actually no way to do this fully today, ie. all the guiNames are left behind. Instead set DataBean and
	 * GuiBean to new empty ones.
	 * 
	 * @throws Exception
	 */
	public static void clearPlotServer() throws Exception {
		PlotServer plotServer = PlotServerProvider.getPlotServer();
		String[] guiNames = plotServer.getGuiNames();
		for (String name : guiNames) {
			plotServer.setData(name, new DataBean());
			plotServer.updateGui(name, new GuiBean());
		}
	}

	/**
	 * Close all Plot related views
	 */
	public static void closeAllPlotRelatedViews() {
		IViewReference[] viewReferences = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage()
				.getViewReferences();
		for (IViewReference ref : viewReferences) {
			if (viewIDsToClose.contains(ref.getId())) {
				ref.getPage().hideView(ref);
			}
		}
	}

	@Before
	public void before() {
		while (Job.getJobManager().currentJob() != null)
			EclipseUtils.delay(1000);
		// give everything a chance to catch up, should return immediately if there is nothing to do
		EclipseUtils.delay(5000, true);
	}

	@After
	public void after() {
		// give everything a chance to catch up, should return immediately if there is nothing to do
		EclipseUtils.delay(5000, true);
	}
	
	private static String getScriptPath(String file) throws Exception {
		URL script = FileLocator.toFileURL(FileLocator.find(new URL("platform:/plugin/uk.ac.diamond.scisoft.python/test/scisoftpy/" + file)));
		return script.getPath();
	}

	private static String getScisoftPyPath() throws Exception {
		URL src = FileLocator.toFileURL(FileLocator.find(new URL("platform:/plugin/uk.ac.diamond.scisoft.python/src")));
		return src.getPath();
	}

	private static String[] makeEnv(int loopbackPort) {
		int port = AnalysisRpcServerProvider.getInstance().getPort();
		Assert.assertFalse(port == 0); // Server must be started ok by now, otherwise the script won't successfully connect to anything
		String[] envp = loopbackPort < 0 ? new String[] { "SCISOFT_RPC_PORT=" + port } :
			new String[] { "SCISOFT_RPC_PORT=" + port, "LOOPBACK_SERVER_PORT=" + loopbackPort };
		return envp;
	}

	protected static void runPythonFile(String file, boolean failOnAnyOutput) throws Exception {
		try {
			PythonHelper.runPythonFile(getScriptPath(file), new String[] {getScisoftPyPath()}, makeEnv(-1), failOnAnyOutput);
		} catch (Throwable t) {
			t.printStackTrace();
			throw t;
		}
	}

	protected static void runPythonFileBackground(String file, int port) throws Exception {
		try {
			PythonHelper.runPythonFileBackground(getScriptPath(file), new String[] {getScisoftPyPath()}, makeEnv(port));
		} catch (Throwable t) {
			t.printStackTrace();
			throw t;
		}
	}
}
