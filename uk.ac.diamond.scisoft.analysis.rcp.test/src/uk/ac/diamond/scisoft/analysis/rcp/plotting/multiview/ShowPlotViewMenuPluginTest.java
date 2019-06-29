/*
 * Copyright (c) 2012 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.analysis.rcp.plotting.multiview;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.eclipse.january.dataset.DatasetFactory;
import org.eclipse.january.dataset.IntegerDataset;
import org.eclipse.jface.action.IContributionItem;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.menus.CommandContributionItem;
import org.junit.Assert;
import org.junit.Test;

import uk.ac.diamond.scisoft.analysis.SDAPlotter;
import uk.ac.diamond.scisoft.analysis.rcp.plotting.PlotWindow;
import uk.ac.diamond.scisoft.analysis.rcp.plotting.actions.ShowPlotViewHandler;
import uk.ac.diamond.scisoft.analysis.rcp.plotting.actions.ShowPlotViewMenu;

public class ShowPlotViewMenuPluginTest extends MultiPlotViewTestBase {

	private static final String TEST_PLOT_NAME = "My Test Plot 1";
	private static final String TEST_PLOT_NAME_IN_MENU = TEST_PLOT_NAME + ShowPlotViewHandler.IN_PLOT_SERVER_SUFFIX;

	// used to store items to dispose when done with menu
	private ShowPlotViewMenu showPlotViewMenu = null;
	private Shell parent = null;
	private Menu menu = null;

	/**
	 * Return a map of display strings to Contribution Items.
	 * <p>
	 * You must (in a finally perhaps) call disposeShowPlotViewMenuContentsWithActions when you are done with the
	 * CommandContributionItems
	 * 
	 * @return map
	 */
	private Map<String, CommandContributionItem> getShowPlotViewMenuContentsWithActions() {
		showPlotViewMenu = new ShowPlotViewMenu();
		parent = new Shell();
		menu = new Menu(parent);
		return refreshShowPlotViewMenuContentsWithActions();
	}

	/**
	 * Return a map of display strings to Contribution Items. Must be called after
	 * {@link #getShowPlotViewMenuContentsWithActions()} and can be called repeatedly. Must be called before
	 * {@link #disposeShowPlotViewMenuContentsWithActions()}
	 * <p>
	 * This method calls fill again
	 * 
	 * @return map
	 */
	private Map<String, CommandContributionItem> refreshShowPlotViewMenuContentsWithActions() {
		Map<String, CommandContributionItem> ret = new HashMap<String, CommandContributionItem>();
		if (showPlotViewMenu.isDirty()) {
			showPlotViewMenu.fill(menu, 0);
		}
		MenuItem[] items = menu.getItems();
		for (MenuItem item : items) {
			Object data = item.getData();
			if (data instanceof CommandContributionItem) {
				ret.put(item.getText(), (CommandContributionItem) data);
			}
		}
		return ret;
	}

	private void disposeShowPlotViewMenuContentsWithActions() {
		if (parent != null) {
			parent.dispose();
			parent = null;
		}
		if (showPlotViewMenu != null) {
			showPlotViewMenu.dispose();
			showPlotViewMenu = null;
		}
		if (menu != null) {
			menu.dispose();
			menu = null;
		}
	}

	/**
	 * Return a set of display names in the Show Plot View menu
	 * 
	 * @return map
	 */
	private Set<String> getShowPlotViewMenuContents() {
		try {
			return getShowPlotViewMenuContentsWithActions().keySet();
		} finally {
			disposeShowPlotViewMenuContentsWithActions();
		}
	}

	/**
	 * This test is intended to verify that the contents of the fragment xml for where the show plot view menu item is
	 * located has worked as expected.
	 * <p>
	 * The path for the menu item is "menu:window?after=showView" so this test tests against the hardcoded strings by
	 * drilling down the menus. There may be some API violations here (see the casts) but the whole idea of the test is
	 * to make sure that in case we have violated something in the fragment.xml, we get notified as soon as a upgrade
	 * happens.
	 * <p>
	 * This test also makes sure that {@link ShowPlotViewHandler#COMMAND_ID} matches what is in the fragment.xml
	 */
	@Test
	public void testMenuPresent() {
		boolean foundShowView = false, foundShowPlotView = false;
		Menu menuBar = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell().getMenuBar();
		MenuItem[] items = menuBar.getItems();
		OUT: for (MenuItem menuItem : items) {
			Object data = menuItem.getData();
			if (data instanceof MenuManager) {
				MenuManager manager = (MenuManager) data;
				if (manager.getId().equals("window")) { // toolbar we should have added showPlotView in
					IContributionItem[] windowItems = manager.getItems();
					for (IContributionItem windowItem : windowItems) {
						if (windowItem instanceof MenuManager) {
							MenuManager windowManager = (MenuManager) windowItem;
							if (windowManager.getId().equals("showView")) { // menu item we should be inserted after
								foundShowView = true;
								continue;
							} else if (windowManager.getId().equals(ShowPlotViewHandler.COMMAND_ID)) {
								// if we haven't found showView then the show plot view item is in the wrong place
								Assert.assertTrue(foundShowView);
								foundShowPlotView = true;
								break OUT;
							} else if (foundShowView) {
								Assert.fail("Found showView, but next item was not " + ShowPlotViewHandler.COMMAND_ID);
							}
						} else if (foundShowView) {
							Assert.fail("Found showView, but next item was not " + ShowPlotViewHandler.COMMAND_ID);
						}

					}
				}
			}
		}
		Assert.assertTrue(foundShowView);
		Assert.assertTrue(foundShowPlotView);
	}

	@Test
	public void testItemPresentAfterAddingToPlotServer() throws Exception {
		Assert.assertFalse(getShowPlotViewMenuContents().contains(
				"My Test Plot 12" + ShowPlotViewHandler.IN_PLOT_SERVER_SUFFIX));
		SDAPlotter.plot("My Test Plot 12", DatasetFactory.createRange(IntegerDataset.class, 100));
		Assert.assertTrue(getShowPlotViewMenuContents().contains(
				"My Test Plot 12" + ShowPlotViewHandler.IN_PLOT_SERVER_SUFFIX));
	}

	@Test
	public void openViewAfterAddingToPlotServer() throws Exception {
		// Create a plot data to open with
		SDAPlotter.plot(TEST_PLOT_NAME, DatasetFactory.createRange(IntegerDataset.class, 100));

		try {
			Map<String, CommandContributionItem> contents = getShowPlotViewMenuContentsWithActions();
			Assert.assertTrue(contents.containsKey(TEST_PLOT_NAME_IN_MENU));
			CommandContributionItem cci = contents.get(TEST_PLOT_NAME_IN_MENU);

			// Execute the open view, making sure the view isn't open before and is open after
			Assert.assertFalse(isMultiplePlotViewReferenced(TEST_PLOT_NAME));
			cci.getCommand().executeWithChecks(null, null);
			Assert.assertTrue(isMultiplePlotViewReferenced(TEST_PLOT_NAME));
		} finally {
			disposeShowPlotViewMenuContentsWithActions();
		}
	}

	@Test
	public void testItemPresentAfterDuplicatingPlot() throws Exception {
		SDAPlotter.plot(TEST_PLOT_NAME, DatasetFactory.createRange(IntegerDataset.class, 100));

		String dupViewName = PlotWindow.getManager().openDuplicateView(null, TEST_PLOT_NAME);
		Assert.assertFalse(TEST_PLOT_NAME.equals(dupViewName));

		Set<String> contents = getShowPlotViewMenuContents();
		// the " *" is to indicate that there is corresponding
		// data in the plot server for this plot
		Assert.assertTrue(contents.contains(dupViewName + " *"));
	}

	@Test
	public void testItemPresentAfterOpeningNewView() {
		String viewName = PlotWindow.getManager().openView(null, "My View With No Data");
		Assert.assertEquals("My View With No Data", viewName);

		Set<String> contents = getShowPlotViewMenuContents();
		Assert.assertTrue(contents.contains("My View With No Data"));
	}

	@Test
	public void testDefaultPlotInMenu() {
		Set<String> contents = getShowPlotViewMenuContents();
		// Plot is the name of the Plot View that can be opened multiple times
		Assert.assertTrue(contents.contains("Plot"));
	}

	@Test
	public void testOpenNewPlotInMenu() {
		Set<String> contents = getShowPlotViewMenuContents();
		Assert.assertTrue(contents.contains(ShowPlotViewHandler.NEW_PLOT_VIEW));
	}

	/**
	 * This test is to make sure the dirty flag is properly set on the menu manager
	 */
	@Test
	public void testAdditionToPlotServerUpdatesExistingMenu() throws Exception {

		try {
			// Start off by verifying clean state
			Set<String> initialContents = getShowPlotViewMenuContents();
			Assert.assertFalse(initialContents.contains("Plot Updates Menu 1"
					+ ShowPlotViewHandler.IN_PLOT_SERVER_SUFFIX));
			Assert.assertFalse(initialContents.contains("Plot Updates Menu 2"
					+ ShowPlotViewHandler.IN_PLOT_SERVER_SUFFIX));

			// Plot entry 1
			SDAPlotter.plot("Plot Updates Menu 1", DatasetFactory.createRange(IntegerDataset.class, 100));
			Map<String, CommandContributionItem> actions = getShowPlotViewMenuContentsWithActions();

			// make sure entry 1 is in the menu (and for good measure 2 isn't!)
			Assert.assertTrue(actions.containsKey("Plot Updates Menu 1" + ShowPlotViewHandler.IN_PLOT_SERVER_SUFFIX));
			Assert.assertFalse(actions.containsKey("Plot Updates Menu 2" + ShowPlotViewHandler.IN_PLOT_SERVER_SUFFIX));

			// Plot entry 2
			SDAPlotter.plot("Plot Updates Menu 2", DatasetFactory.createRange(IntegerDataset.class, 100));
			// Now using the same menu as we created above, check that entry 2 is in the list
			actions = refreshShowPlotViewMenuContentsWithActions();
			Assert.assertTrue(actions.containsKey("Plot Updates Menu 1" + ShowPlotViewHandler.IN_PLOT_SERVER_SUFFIX));
			Assert.assertTrue(actions.containsKey("Plot Updates Menu 2" + ShowPlotViewHandler.IN_PLOT_SERVER_SUFFIX));

		} finally {
			disposeShowPlotViewMenuContentsWithActions();
		}
	}

	/**
	 * This test is to make sure the dirty flag is properly set on the menu manager
	 */
	@Test
	public void testAdditionalOpenViewUpdatesExistingMenu() {

		try {
			// Start off by verifying clean state
			Set<String> initialContents = getShowPlotViewMenuContents();
			Assert.assertFalse(initialContents.contains("Plot Open Menu 1"));
			Assert.assertFalse(initialContents.contains("Plot Open Menu 2"));

			// Plot entry 1
			PlotWindow.getManager().openView(null, "Plot Open Menu 1");
			Map<String, CommandContributionItem> actions = getShowPlotViewMenuContentsWithActions();

			// make sure entry 1 is in the menu (and for good measure 2 isn't!)
			Assert.assertTrue(actions.containsKey("Plot Open Menu 1"));
			Assert.assertFalse(actions.containsKey("Plot Open Menu 2"));

			// Plot entry 2
			PlotWindow.getManager().openView(null, "Plot Open Menu 2");
			// Now using the same menu as we created above, check that entry 2 is in the list
			actions = refreshShowPlotViewMenuContentsWithActions();
			Assert.assertTrue(actions.containsKey("Plot Open Menu 1"));
			Assert.assertTrue(actions.containsKey("Plot Open Menu 2"));

		} finally {
			disposeShowPlotViewMenuContentsWithActions();
		}
	}
}
