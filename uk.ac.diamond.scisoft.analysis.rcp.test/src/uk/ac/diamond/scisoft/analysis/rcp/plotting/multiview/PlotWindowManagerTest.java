/*
 * Copyright (c) 2012 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.analysis.rcp.plotting.multiview;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.dawnsci.analysis.dataset.roi.LinearROI;
import org.eclipse.dawnsci.analysis.dataset.roi.LinearROIList;
import org.eclipse.january.dataset.DatasetFactory;
import org.eclipse.january.dataset.IntegerDataset;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IViewReference;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PartInitException;
import org.junit.Assert;
import org.junit.Test;

import uk.ac.diamond.scisoft.analysis.PlotServer;
import uk.ac.diamond.scisoft.analysis.plotclient.IPlotWindow;
import uk.ac.diamond.scisoft.analysis.plotclient.IPlotWindowManager;
import uk.ac.diamond.scisoft.analysis.plotclient.PlotWindowManager;
import uk.ac.diamond.scisoft.analysis.plotserver.DataBean;
import uk.ac.diamond.scisoft.analysis.plotserver.GuiBean;
import uk.ac.diamond.scisoft.analysis.plotserver.GuiParameters;
import uk.ac.diamond.scisoft.analysis.rcp.plotting.PlotWindow;
import uk.ac.diamond.scisoft.analysis.rcp.plotting.multiview.mock.MockAttribute;
import uk.ac.diamond.scisoft.analysis.rcp.plotting.multiview.mock.MockConfigElem;
import uk.ac.diamond.scisoft.analysis.rcp.views.PlotView;

/**
 * Test the PlotWindowManager as a unit.
 */
public class PlotWindowManagerTest {

	/**
	 * A version of the class that throws exceptions if unintended code is called. Some of the methods are not testable
	 * in a unit way, so we provide mock versions throughout these tests.
	 */
	private static class PlotWindowManagerUnderTest extends PlotWindowManager {
		public PlotWindowManagerUnderTest() {
			super(null);
		}

		public PlotWindowManagerUnderTest(List<IConfigurationElement> viewsConfigElements) {
			super(viewsConfigElements);
		}

		@Override
		protected IWorkbenchPage getPage(IWorkbenchPage page) {
			Assert.assertNotNull(page);
			return page;
		}

		@Override
		protected PlotServer getPlotServer() {
			return new MockPlotServer();
		}
	}

	@Test
	public void testPlotManagerCreation1() {
		PlotWindowManager plotWindowManager = new PlotWindowManagerUnderTest(null);
		Assert.assertTrue(plotWindowManager.getOpenViews().length == 0);
	}

	final static String MOCK_ID = "uk.ac.diamond.test.view.";

	@Test
	public void testPlotManagerCreation2() {
		List<IConfigurationElement> configElements = new ArrayList<IConfigurationElement>();
		MockConfigElem config1 = new MockConfigElem("view");
		config1.addAttribute(new MockAttribute("class", IPlotWindowManager.PLOTVIEW_PATH));
		config1.addAttribute(new MockAttribute("id", MOCK_ID + "Plot 1"));
		config1.addAttribute(new MockAttribute("name", "Plot 1"));

		MockConfigElem config2 = new MockConfigElem("view");
		config2.addAttribute(new MockAttribute("class", IPlotWindowManager.PLOTVIEW_PATH));
		config2.addAttribute(new MockAttribute("id", MOCK_ID + "Plot 2"));
		config2.addAttribute(new MockAttribute("name", "Plot 2"));

		configElements.add(config1);
		configElements.add(config2);
		PlotWindowManager plotWindowManager = new PlotWindowManagerUnderTest(configElements);
		Assert.assertTrue(plotWindowManager.getOpenViews().length == 0);
	}

	final String[] lastShowedView = new String[1];

	/**
	 * Fake an implementation of {@link IWorkbenchPage} that is able to open a view, assume it is a plot view, and
	 * register that new view back with the manger. This simulates a huge amount of code between
	 * {@link IWorkbenchPage#showView(String, String, int)} and creation of {@link PlotView}, {@link PlotWindow}, all
	 * the side plots etc...
	 * 
	 * @param windowManager
	 *            to register view with
	 * @return newly created page
	 */
	public IWorkbenchPage createTestPage(final PlotWindowManager windowManager) {
		final IWorkbenchPage page = new MockWorkbenchPage() {
			@Override
			public IViewPart showView(final String viewId, final String secondaryId, int mode) throws PartInitException {
				// Make sure that we are only called once
				Assert.assertNull(lastShowedView[0]);
				lastShowedView[0] = viewId + ":" + secondaryId;
				// contract is to register views as they are fully opened
				windowManager.registerPlotWindow(new IPlotWindow() {

					@Override
					public IWorkbenchPart getPart() {
						return null;
					}

					@Override
					public String getName() {
						if (secondaryId != null)
							return secondaryId;
						return viewId.substring(0, viewId.lastIndexOf('.') - 1);
					}
				});
				return null;
			}

			@Override
			public IViewReference[] getViewReferences() {
				return new IViewReference[0];
			}
		};
		return page;
	}

	@Test
	public void testCreateUniqueName() {
		final PlotWindowManager plotWindowManager = new PlotWindowManagerUnderTest(null);
		final IWorkbenchPage page = createTestPage(plotWindowManager);

		Set<String> knownNames = new HashSet<String>();
		for (int i = 0; i < 10; i++) {
			lastShowedView[0] = null;
			String newView = plotWindowManager.openView(page, null);
			Assert.assertFalse(knownNames.contains(newView));
			Assert.assertEquals(IPlotWindowManager.PLOT_VIEW_MULTIPLE_ID + ":" + newView, lastShowedView[0]);
			knownNames.add(newView);
		}
	}

	@Test
	public void testCreateUniqueDupName() {
		final MockPlotServer plotServer = new MockPlotServer();
		final PlotWindowManager plotWindowManager = new PlotWindowManagerUnderTest(null) {
			@Override
			protected PlotServer getPlotServer() {
				return plotServer;
			}
		};
		final IWorkbenchPage page = createTestPage(plotWindowManager);

		Set<String> knownNames = new HashSet<String>();
		for (int i = 0; i < 10; i++) {
			lastShowedView[0] = null;
			String newView = plotWindowManager.openDuplicateView(page, "Plot");
			Assert.assertFalse(knownNames.contains(newView));
			Assert.assertEquals(IPlotWindowManager.PLOT_VIEW_MULTIPLE_ID + ":" + newView, lastShowedView[0]);
			knownNames.add(newView);
		}
	}

	@Test
	public void testCreateUniqueNameNoClashWithGlobalViews() {
		List<IConfigurationElement> configElements = new ArrayList<IConfigurationElement>();
		MockConfigElem config1 = new MockConfigElem("view");
		config1.addAttribute(new MockAttribute("class", IPlotWindowManager.PLOTVIEW_PATH));
		config1.addAttribute(new MockAttribute("id", MOCK_ID + "Plot 1"));
		config1.addAttribute(new MockAttribute("name", "Plot 1"));

		MockConfigElem config2 = new MockConfigElem("view");
		config2.addAttribute(new MockAttribute("class", IPlotWindowManager.PLOTVIEW_PATH));
		config2.addAttribute(new MockAttribute("id", MOCK_ID + "Plot 2"));
		config2.addAttribute(new MockAttribute("name", "Plot 2"));

		configElements.add(config1);
		configElements.add(config2);
		final PlotWindowManager plotWindowManager = new PlotWindowManagerUnderTest(configElements);
		final IWorkbenchPage page = createTestPage(plotWindowManager);

		Set<String> knownNames = new HashSet<String>();
		knownNames.add("Plot 1");
		knownNames.add("Plot 2");
		for (int i = 0; i < 10; i++) {
			lastShowedView[0] = null;
			String newView = plotWindowManager.openView(page, null);
			Assert.assertFalse(knownNames.contains(newView));
			Assert.assertEquals(IPlotWindowManager.PLOT_VIEW_MULTIPLE_ID + ":" + newView, lastShowedView[0]);
			knownNames.add(newView);
		}
	}

	/**
	 * Make sure the openView doesn't mangle plot names
	 */
	@Test
	public void testOpensPlotView() {
		List<IConfigurationElement> configElements = new ArrayList<IConfigurationElement>();
		MockConfigElem config1 = new MockConfigElem("view");
		config1.addAttribute(new MockAttribute("class", IPlotWindowManager.PLOTVIEW_PATH));
		config1.addAttribute(new MockAttribute("id", MOCK_ID + "Plot 1"));
		config1.addAttribute(new MockAttribute("name", "Plot 1"));

		configElements.add(config1);
		final PlotWindowManager plotWindowManager = new PlotWindowManagerUnderTest(configElements);
		final IWorkbenchPage page = createTestPage(plotWindowManager);

		// Test opening pre-existing plot view
		lastShowedView[0] = null;
		String plot1 = plotWindowManager.openView(page, "Plot 1");
		Assert.assertEquals("Plot 1", plot1);
		Assert.assertEquals(MOCK_ID + "Plot 1" + ":" + null, lastShowedView[0]);

		// Test opening a multiple plot view
		lastShowedView[0] = null;
		String plot5 = plotWindowManager.openView(page, "Plot 5");
		Assert.assertEquals("Plot 5", plot5);
		Assert.assertEquals(IPlotWindowManager.PLOT_VIEW_MULTIPLE_ID + ":" + "Plot 5", lastShowedView[0]);
	}

	@Test
	public void testDuplicateView() throws Exception {
		final MockPlotServer plotServer = new MockPlotServer();
		final PlotWindowManager plotWindowManager = new PlotWindowManagerUnderTest(null) {
			@Override
			protected PlotServer getPlotServer() {
				return plotServer;
			}
		};
		final IWorkbenchPage page = createTestPage(plotWindowManager);

		// open a view and fill it with "stuff"
		lastShowedView[0] = null;
		String plot1 = plotWindowManager.openView(page, "Plot 1");
		Assert.assertEquals("Plot 1", plot1);
		Assert.assertEquals(IPlotWindowManager.PLOT_VIEW_MULTIPLE_ID + ":" + "Plot 1", lastShowedView[0]);
		GuiBean guiBean = new GuiBean();
		LinearROI roi = new LinearROI(10, 0.4);
		roi.setName("lroi 1");
		LinearROIList list = new LinearROIList();
		list.add(roi);
		guiBean.put(GuiParameters.ROIDATA, roi.getName());
		guiBean.put(GuiParameters.ROIDATALIST, list);
		plotServer.updateGui(plot1, guiBean);
		DataBean dataBean = new DataBean();
		dataBean.addAxis("X-Axis", DatasetFactory.createRange(IntegerDataset.class, 100));
		plotServer.setData(plot1, dataBean);

		// duplicate plot 1
		lastShowedView[0] = null;
		String plot1Dup = plotWindowManager.openDuplicateView(page, "Plot 1");
		Assert.assertFalse("Plot 1".equals(plot1Dup)); // it should be a duplicate with a new name!
		Assert.assertEquals(IPlotWindowManager.PLOT_VIEW_MULTIPLE_ID + ":" + plot1Dup, lastShowedView[0]);

		// now make sure plotserver has duplicated info
		DataBean dataBeanDup = plotServer.getData(plot1Dup);
		Assert.assertNotSame(dataBean, dataBeanDup);
		Assert.assertTrue(EqualsBuilder.reflectionEquals(dataBean, dataBeanDup));
		GuiBean guiBeanDup = plotServer.getGuiState(plot1Dup);
		Assert.assertNotSame(guiBean, guiBeanDup);
		Assert.assertEquals(guiBean, guiBeanDup);

	}
}
