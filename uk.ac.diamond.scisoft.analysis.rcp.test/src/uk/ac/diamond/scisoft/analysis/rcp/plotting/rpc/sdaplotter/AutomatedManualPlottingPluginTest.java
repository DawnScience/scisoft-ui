/*
 * Copyright (c) 2012 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.analysis.rcp.plotting.rpc.sdaplotter;

import org.apache.commons.lang3.ArrayUtils;
import org.junit.Assert;
import org.junit.Test;

import uk.ac.diamond.scisoft.analysis.PlotServerProvider;
import uk.ac.diamond.scisoft.analysis.rcp.plotting.RcpPlottingTestBase;

/**
 * There are a couple of manual plotting tests in python test. Runs these automatically here. This doesn't remove
 * requirement to run them manually because this test is only able to check for exceptions and unexpected output, not
 * for everything.
 */
public class AutomatedManualPlottingPluginTest extends RcpPlottingTestBase {

	@Test
	public void testManualPlotTestOverRpcPython() throws Exception {
		// Launch the AnalysisRpc server that receives our requests and sends them back to us
		Assert.assertTrue(ArrayUtils.indexOf(PlotServerProvider.getPlotServer().getGuiNames(), "Plot 1 RPC Python") == -1);
		runPythonFile("manual_plot_test_over_rpc.py", true);
		Assert.assertTrue(ArrayUtils.indexOf(PlotServerProvider.getPlotServer().getGuiNames(), "Plot 1 RPC Python") != -1);
	}

	@Test
	public void testManualPlotTestPython() throws Exception {
		// Launch the AnalysisRpc server that receives our requests and sends them back to us
		Assert.assertTrue(ArrayUtils.indexOf(PlotServerProvider.getPlotServer().getGuiNames(), "Plot 1 DNP Python") == -1);
		runPythonFile("manual_plot_test.py", true);
		Assert.assertTrue(ArrayUtils.indexOf(PlotServerProvider.getPlotServer().getGuiNames(), "Plot 1 DNP Python") != -1);
	}

}
