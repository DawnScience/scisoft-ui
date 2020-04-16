/*
 * Copyright (c) 2012 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.analysis.rcp.plotting.multiview;

import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang3.ArrayUtils;
import org.dawb.common.ui.util.EclipseUtils;
import org.eclipse.ui.IViewReference;
import org.eclipse.ui.PlatformUI;

import uk.ac.diamond.scisoft.analysis.plotclient.IPlotWindowManager;
import uk.ac.diamond.scisoft.analysis.rcp.plotting.PlotWindow;
import uk.ac.diamond.scisoft.analysis.rcp.plotting.RcpPlottingTestBase;

/**
 * All the plot view tests in this package have some common code located here in their common super class
 */
abstract public class MultiPlotViewTestBase extends RcpPlottingTestBase {

	/**
	 * @param plotViewName
	 *            plot name to test
	 * @return true if there is a Plot Window open with the given secondary ID.
	 */
	public static boolean isMultiplePlotViewReferenced(String plotViewName) {
		IViewReference[] viewReferences = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage()
				.getViewReferences();
		for (IViewReference ref : viewReferences) {
			if (IPlotWindowManager.PLOT_VIEW_MULTIPLE_ID.equals(ref.getId())) {
				if (plotViewName.equals(ref.getSecondaryId())) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * @return set of all multi plot views open according to view references
	 */
	public static Set<String> getAllMultiPlotViews() {
		Set<String> set = new HashSet<String>();
		IViewReference[] viewReferences = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage()
				.getViewReferences();
		for (IViewReference ref : viewReferences) {
			if (IPlotWindowManager.PLOT_VIEW_MULTIPLE_ID.equals(ref.getId())) {
				set.add(ref.getSecondaryId());
			}
		}
		return set;
	}

	/**
	 * @param plotName
	 *            plot name to test
	 * @return true if there is a {@link IPlotWindowManager#getOpenViews()} contains plotName
	 */
	public static boolean isPlotWindowManagerHave(String plotName) {
		String[] openViews = PlotWindow.getManager().getOpenViews();
		return ArrayUtils.indexOf(openViews, plotName) != -1;
	}


	/**
	 * A simple class that allows a task to be run in a different thread while processing UI updates
	 * <p>
	 * This class does a number of things you shouldn't do outside of test, such as wrapping all exceptions in
	 * RuntimeException, and hard coding the 30 second join time.
	 */
	public static class ThreadRunner {
		public interface ThreadRunnable {
			public Object run() throws Exception;
		}

		private final ThreadRunnable runnable;

		public ThreadRunner(ThreadRunnable runnable) {
			this.runnable = runnable;
		}

		public Object run() {
			try {
				final Object[] ret = new Object[1];
				final Exception[] exp = new Exception[1];
				Thread thread = new Thread(new Runnable() {

					@Override
					public void run() {
						try {
							ret[0] = runnable.run();
						} catch (Exception e) {
							// Wrap in unchecked
							exp[0] = e;
						}
					}
				});
				thread.start();
				EclipseUtils.threadJoin(thread, 30000);
				if (thread.isAlive()) {
					thread.interrupt();
					throw new Exception("Thread took too long");
				}

				if (exp[0] != null) {
					throw exp[0];
				}
				return ret[0];
			} catch (Exception e) {
				// Wrap in unchecked
				throw new RuntimeException(e);
			}
		}
	}

}
