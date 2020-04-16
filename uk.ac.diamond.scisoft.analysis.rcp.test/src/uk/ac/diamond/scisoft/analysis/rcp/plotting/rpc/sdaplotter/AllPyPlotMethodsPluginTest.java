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
import org.eclipse.january.dataset.DatasetFactory;
import org.eclipse.january.dataset.IDataset;
import org.eclipse.january.dataset.IntegerDataset;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import uk.ac.diamond.scisoft.analysis.PlotServerProvider;
import uk.ac.diamond.scisoft.analysis.PythonHelper.PythonRunInfo;
import uk.ac.diamond.scisoft.analysis.SDAPlotter;
import uk.ac.diamond.scisoft.analysis.plotserver.GuiBean;
import uk.ac.diamond.scisoft.analysis.plotserver.rpc.sdaplotter.ReDirectOverRpcPlotterImpl;
import uk.ac.diamond.scisoft.analysis.rcp.plotting.RcpPlottingTestBase;

/**
 * This runs each of the plot functions available in SDAPlotter as a plugin test. The purpose of this test is to make
 * sure nothing "blows" up. It is very hard to see if the correct thing has been plotted, and we already verify that
 * SDAPlotter is being called correctly (see uk.ac.diamond.scisoft.analysis.rpc.sdaplotter.AllPyPlotMethodsTest)
 * 
 */
public class AllPyPlotMethodsPluginTest extends RcpPlottingTestBase {
	private static PythonRunInfo pythonRunInfo;
	private static ReDirectOverRpcPlotterImpl redirectPlotter;

	static int plotIndex = 3;
	
	
	// Parameters for each test
	private IDataset sizes;
	private IDataset data, xAxis, yAxis, zAxis, image, xCoords, yCoords, zCoords;
	private IDataset[] xAxes, yAxes, images;
	private String plotName, viewName;
	private String pathname, regex;
	private int order, gridColumns;
	private boolean rowMajor;
	private String[] suffices;
	private GuiBean bean;

	/** 
	 * For the tests that we can we make sure the viewName/plotName has ended up in the plotserver.
	 */
	private boolean excludePlotServerCheck = false;
	
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {

		redirectPlotter = new ReDirectOverRpcPlotterImpl();

		// Launch the AnalysisRpc server that receives our requests and sends them back to us
		runPythonFileBackground("loopback.py");
	}

	public AllPyPlotMethodsPluginTest() {
		// create some data sets and other objects to use, this test does not use
		// the contents of the data set, except they are flattened
		// and unflattened. The type of the object is more important
		xCoords = yCoords = zCoords = xAxis = yAxis = zAxis = DatasetFactory.createRange(IntegerDataset.class, 100);
		data = image = DatasetFactory.createRange(IntegerDataset.class, 100).reshape(10, 10);
		xAxes = yAxes = new IDataset[] { xAxis, DatasetFactory.createRange(100) };
		images = new IDataset[] { image, DatasetFactory.createRange(100) };
		sizes = DatasetFactory.createRange(IntegerDataset.class, 100);
		pathname = "/tmp/dir";
		regex = "a.*b";
		order = SDAPlotter.IMAGEORDERNONE;
		suffices = SDAPlotter.LISTOFSUFFIX;
		gridColumns = 2;
		rowMajor = true;
		bean = new GuiBean();
		viewName = plotName = "Plot " + plotIndex;
		plotIndex++;
	}

	private void checkPythonState() {
		// Before and after each test make sure the server is still there
		if (pythonRunInfo != null && pythonRunInfo.hasTerminated()) {
			// It has disappeared, so dump the stdout and stderr
			pythonRunInfo.getStdout(true);
			throw new RuntimeException("Python script unexpectedly terminated");
		}
	}

	@Before
	public void checkBefore() throws Exception {
		Assert.assertTrue(ArrayUtils.indexOf(PlotServerProvider.getPlotServer().getGuiNames(), viewName) == -1);
		checkPythonState();
	}

	@After
	public void checkAfter() throws Exception {
		checkPythonState();
		if (!excludePlotServerCheck) {
			Assert.assertTrue(ArrayUtils.indexOf(PlotServerProvider.getPlotServer().getGuiNames(), viewName) != -1);
		}
	}

	@AfterClass
	public static void tearDownAfterClass() {

		// Stop the server making sure no unexpected output is there
		if (pythonRunInfo != null) {
			pythonRunInfo.terminate();
			pythonRunInfo.getStdout(true);
		}
		pythonRunInfo = null;
	}

	@Test
	public void testPlotStringIDataset() throws Exception {
		redirectPlotter.plot(plotName, null, null, new IDataset[] {yAxis}, null, null, null);
	}

	@Test
	public void testPlotStringIDatasetIDataset() throws Exception {
		redirectPlotter.plot(plotName, null, new IDataset[] {xAxis}, new IDataset[] {yAxis}, null, null, null);
	}

	@Test
	public void testPlotStringIDatasetIDatasetArray() throws Exception {
		redirectPlotter.plot(plotName, null, new IDataset[] {xAxis}, yAxes, null, null, null);
	}

	@Test
	public void testPlotStringIDatasetArrayIDatasetArray() throws Exception {
		redirectPlotter.plot(plotName, null, xAxes, yAxes, null, null, null);
	}

	@Test
	public void testUpdatePlotStringIDataset() throws Exception {
		redirectPlotter.updatePlot(plotName, null, null, new IDataset[] {yAxis}, null, null);
	}

	@Test
	public void testUpdatePlotStringIDatasetIDataset() throws Exception {
		redirectPlotter.updatePlot(plotName, null, new IDataset[] {xAxis}, new IDataset[] {yAxis}, null, null);
	}

	@Test
	public void testUpdatePlotStringIDatasetIDatasetArray() throws Exception {
		redirectPlotter.updatePlot(plotName, null, new IDataset[] {xAxis}, yAxes, null, null);
	}

	@Test
	public void testUpdatePlotStringIDatasetArrayIDatasetArray() throws Exception {
		redirectPlotter.updatePlot(plotName, null, xAxes, yAxes, null, null);
	}

	@Test
	public void testImagePlotStringIDataset() throws Exception {
		redirectPlotter.imagePlot(plotName, null, null, image, null, null);
	}

	@Test
	public void testImagesPlotStringIDatasetArray() throws Exception {
		redirectPlotter.imagesPlot(plotName, null, null, images);
	}

	@Test
	public void testImagePlotStringIDatasetIDatasetIDataset() throws Exception {
		redirectPlotter.imagePlot(plotName, xAxis, yAxis, image, null, null);
	}

	@Test
	public void testImagesPlotStringIDatasetIDatasetIDatasetArray() throws Exception {
		redirectPlotter.imagesPlot(plotName, xAxis, yAxis, images);
	}

	@Test
	public void testScatter2DPlotStringIDatasetIDatasetIDataset() throws Exception {
		redirectPlotter.scatter2DPlot(plotName, xCoords, yCoords, sizes);
	}

	@Test
	public void testScatter2DPlotOverStringIDatasetIDatasetIDataset() throws Exception {
		redirectPlotter.scatter2DPlotOver(plotName, xCoords, yCoords, sizes);
	}

	@Test
	public void testScatter3DPlotStringIDatasetIDatasetIDatasetIDataset() throws Exception {
		redirectPlotter.scatter3DPlot(plotName, xCoords, yCoords, zCoords, sizes);
	}

	@Test
	public void testScatter3DPlotOverStringIDatasetIDatasetIDatasetIDataset() throws Exception {
		redirectPlotter.scatter3DPlotOver(plotName, xCoords, yCoords, zCoords, sizes);
	}

	@Test
	public void testSurfacePlotStringIDataset() throws Exception {
		redirectPlotter.surfacePlot(plotName, null, null, data);
	}

	@Test
	public void testSurfacePlotStringIDatasetIDataset() throws Exception {
		// XXX: xAxis is discarded in this call by plot.py#surface
		redirectPlotter.surfacePlot(plotName, xAxis, null, data);
	}

	@Test
	public void testSurfacePlotStringIDatasetIDatasetIDataset() throws Exception {
		redirectPlotter.surfacePlot(plotName, xAxis, yAxis, data);
	}

	@Test
	public void testStackPlotStringIDatasetIDatasetArray() throws Exception {
		redirectPlotter.stackPlot(plotName, new IDataset[] {xAxis}, yAxes, null);
	}

	@Test
	public void testStackPlotStringIDatasetIDatasetArrayIDataset() throws Exception {
		redirectPlotter.stackPlot(plotName, new IDataset[] {xAxis}, yAxes, zAxis);
	}

	@Test
	public void testStackPlotStringIDatasetArrayIDatasetArray() throws Exception {
		redirectPlotter.stackPlot(plotName, xAxes, yAxes, null);
	}

	@Test
	public void testStackPlotStringIDatasetArrayIDatasetArrayIDataset() throws Exception {
		redirectPlotter.stackPlot(plotName, xAxes, yAxes, zAxis);
	}

	@Test
	public void testUpdateStackPlotStringIDatasetArrayIDatasetArrayIDataset() throws Exception {
		redirectPlotter.updateStackPlot(plotName, xAxes, yAxes, zAxis);
	}

	@Test
	public void testScanForImagesStringStringIntStringStringArrayIntBoolean() throws Exception {
		redirectPlotter.scanForImages(viewName, pathname, order, regex, suffices, gridColumns, rowMajor, Integer.MAX_VALUE, 1);
		excludePlotServerCheck = true;
	}

	@Test
	public void testSetGuiBean() throws Exception {
		redirectPlotter.setGuiBean(plotName, bean);
	}

	@Test
	public void testGetGuiBean() throws Exception {
		redirectPlotter.getGuiBean(plotName);
		excludePlotServerCheck = true;
	}

	@Test
	public void testGetGuiNames() throws Exception {
		redirectPlotter.getGuiNames();
		excludePlotServerCheck = true;
	}

}
