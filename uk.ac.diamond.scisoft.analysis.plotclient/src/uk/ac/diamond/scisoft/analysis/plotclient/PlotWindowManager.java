/*
 * Copyright (c) 2012 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.analysis.plotclient;

import gda.observable.IIsBeingObserved;
import gda.observable.IObservable;
import gda.observable.IObserver;
import gda.observable.ObservableComponent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.Platform;
import org.eclipse.dawnsci.analysis.api.RMIServerProvider;
import org.eclipse.dawnsci.analysis.api.rpc.IAnalysisRpcHandler;
import org.eclipse.dawnsci.plotting.api.IPlottingSystem;
import org.eclipse.ui.IViewReference;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.AnalysisRpcServerProvider;
import uk.ac.diamond.scisoft.analysis.PlotServer;
import uk.ac.diamond.scisoft.analysis.PlotServerProvider;
import uk.ac.diamond.scisoft.analysis.plotclient.rpc.AnalysisRpcSyncExecDispatcher;
import uk.ac.diamond.scisoft.analysis.plotserver.DataBean;
import uk.ac.diamond.scisoft.analysis.plotserver.GuiBean;
import org.apache.commons.lang.StringUtils;

public class PlotWindowManager implements IPlotWindowManager, IObservable, IIsBeingObserved {
	
	
	static private Logger logger = LoggerFactory.getLogger(PlotWindowManager.class);

	private static PlotWindowManager manager;

	/**
	 * Use this method to obtain a handle to the manager singleton.
	 * <p>
	 * This method isn't to be used outside of the PlotWindow framework. To obtain an {@link IPlotWindowManager} call
	 * {@link PlotWindow#getManager()}
	 * 
	 * @return PlotWindowManager
	 */
	public synchronized static PlotWindowManager getPrivateManager() {
		if (manager == null) {
			manager = new PlotWindowManager();

			// register as an RMI service
			try {
				RMIServerProvider.getInstance().exportAndRegisterObject(RMI_WINDOW_SERVICE_NAME,
						new RMIPlotWindowManger());
			} catch (Exception e) {
				logger.warn("Unable to register PlotWindowManager for use over RMI - it might be disabled", e);
			}

			try {
				// register as an RPC service
				IAnalysisRpcHandler dispatcher = new AnalysisRpcSyncExecDispatcher(IPlotWindowManager.class, manager);
				AnalysisRpcServerProvider.getInstance().addHandler(RPC_WINDOW_SERVICE_NAME, dispatcher);
			} catch (Exception e) {
				logger.warn("Not registered PlotWindowManager as RPC service - but might be disabled");
			}
		}
		return manager;
	}

	private Map<String, IPlotWindow> viewMap = new HashMap<String, IPlotWindow>();
	private Map<String, String> knownViews = new HashMap<String, String>();
	private Map<String, String> knownPlotViews = new HashMap<String, String>();
	private ObservableComponent observable = new ObservableComponent();

	private PlotWindowManager() {
		this(getPlotViewsConfigElements());
	}

	/**
	 * Constructor is protected for testing purposes only
	 * 
	 * @param configs
	 *            a list of all extension points available
	 */
	protected PlotWindowManager(List<IConfigurationElement> configs) {
		addPlotViews(configs);
	}

	/**
	 * 
	 * @return a list of IConfigurationElements which are Plot Views
	 */
	protected static List<IConfigurationElement> getPlotViewsConfigElements() {
		IExtensionPoint[] points = Platform.getExtensionRegistry().getExtensionPoints();
		List<IConfigurationElement> plotViews = new ArrayList<IConfigurationElement>();
		for (int i = 0; i < points.length; i++) {
			// if Views extensions points
			if (points[i].getUniqueIdentifier().equals("org.eclipse.ui.views")){
				IExtension[] viewsExt = points[i].getExtensions();
				for (int j = 0; j < viewsExt.length; j++) {
					IConfigurationElement[] config = viewsExt[j].getConfigurationElements();
					for (int k = 0; k < config.length; k++) {
						String name = config[k].getName();
						if (name.equals("view")) {
							final String className = getClassName(config[k]);
							if (className == null) {
								// If id is available, use this to help identify the view in question
								final String id = config[k].getAttribute("id");
								final String message = "View"
										+ (!StringUtils.isEmpty(id) ? " with id \"" + id + "\"": "")
										+ " has no class name: ignoring";
								logger.warn(message);
							} else if (className.equals(PLOTVIEW_PATH)) {
								// Plot view - add to list
								plotViews.add(config[k]);
							}
						}
					}
				}
			}
		}
		return plotViews;
	}
	
	private static String getClassName(final IConfigurationElement configElement) {
		// First, try to find class as an attribute. 
		String className = configElement.getAttribute("class");

		if (StringUtils.isEmpty(className)) {
			// In some circumstances, such as when creating a view class with parameters
			// with <class class=...>, the class attribute can be null.
			// For this reason, it may be better to create the view using a factory class,
			// but we can try to drill down and see if there is a class name anyway.
			for (IConfigurationElement child : configElement.getChildren("class")) {
				className = child.getAttribute("class");
				if (!StringUtils.isEmpty(className)) {
					break;
				}
			}
		}
		
		return className;
	}

	/**
	 * Add IConfigurationElement to the HashMap of known plot views
	 * @param configs
	 */
	private void addPlotViews(List<IConfigurationElement> configs) {
		if (configs == null)
			return;
		for (IConfigurationElement config : configs) {
			String name = config.getName();
			if (name.equals("view")) {
				String className = config.getAttribute("class");
				// if a PlotView
				if (className.equals(PLOTVIEW_PATH)) {
					knownPlotViews.put(config.getAttribute("name"), config.getAttribute("id"));
				}
			}
		}
	}

	public void registerPlotWindow(IPlotWindow window) {
		viewMap.put(window.getName(), window);
		observable.notifyIObservers(this, null);
	}

	public void unregisterPlotWindow(IPlotWindow window) {
		viewMap.remove(window.getName());
		observable.notifyIObservers(this, null);
	}

	@Override
	public String openDuplicateView(IWorkbenchPage page, String viewName) {
		try {

			// Open the view, try to use the same page as the view
			// being duplicated
			if (page == null) {
				IPlotWindow window = viewMap.get(viewName);
				if (window != null) {
					page = window.getPart().getSite().getPage();
				}
			}

			// Create a new, unique name automatically
			String uniqueName = getUniqueName(viewName, page);

			// Perform the open
			openViewInternal(getPage(page), uniqueName);

			// Duplicate the data bean and (deeply) gui bean
			PlotServer plotServer = getPlotServer();
			GuiBean guiBean = plotServer.getGuiState(viewName);
			if (guiBean != null) {
				plotServer.updateGui(uniqueName, guiBean.copy());
			}
			DataBean dataBean = plotServer.getData(viewName);
			if (dataBean != null) {
				plotServer.setData(uniqueName, dataBean.copy());
			}

			return uniqueName;
		} catch (Exception e) {
			logger.error("Unable to duplicate plot view " + viewName, e);
			return null;
		}
	}

	@Override
	public void clearPlottingSystem(IPlottingSystem<?> plottingSystem, String viewName) {
		try {
			plottingSystem.reset();
			plottingSystem.setTitle("");
		} catch (Exception e) {
			logger.error("Unable to clear plot view " + viewName, e);
		}
	}

	@Override
	public String openView(IWorkbenchPage page, String viewName) {
		try {
			if (viewName == null) viewName = getUniqueName("Plot 0", page);
			openViewInternal(page, viewName);
			return viewName;
		} catch (PartInitException e) {
			logger.error("Unable to open new plot view " + viewName, e);
			return null;
		}
	}

	protected void openViewInternal(IWorkbenchPage page, String viewName) throws PartInitException {
		if (knownPlotViews.containsKey(viewName)) {
			getPage(page).showView(knownPlotViews.get(viewName));
		} else {
			getPage(page).showView(PLOT_VIEW_MULTIPLE_ID, viewName, IWorkbenchPage.VIEW_ACTIVATE);
		}
	}

	@Override
	public String[] getOpenViews() {
		Set<String> keys = viewMap.keySet();
		return keys.toArray(new String[keys.size()]);
	}

	protected IWorkbenchPage getPage(IWorkbenchPage page) throws NullPointerException {
		if (page == null) {
			try {
				return PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
			} catch (NullPointerException e) {
				Collection<IPlotWindow> values = viewMap.values();
				Iterator<IPlotWindow> iterator = values.iterator();
				if (iterator.hasNext())
					return iterator.next().getPart().getSite().getPage();
				throw new NullPointerException("Unable to obtain a workbench page to open view from");
			}
		}
		return page;
	}

	protected PlotServer getPlotServer() {
		return PlotServerProvider.getPlotServer();
	}

	private String getUniqueName(String base, IWorkbenchPage page) {
		try {
			Set<String> knownNames = new HashSet<String>(Arrays.asList(getAllPossibleViews(page, true)));
			int lastSpaceIndex = base.lastIndexOf(' ');
			if (lastSpaceIndex >= 0) {
				String numString = base.substring(lastSpaceIndex + 1);
				int viewNum = Integer.parseInt(numString);
				String prefix = base.substring(0, lastSpaceIndex + 1);
				String winner;
				do {
					viewNum++;
					winner = prefix + viewNum;
					if (viewNum > 1000000 || viewNum <= 0) {
						throw new NumberFormatException();
					}
				} while (knownViews.containsKey(winner) || knownNames.contains(winner));
				return winner;
			}
		} catch (NumberFormatException e) {
			// no number at end of string, fall through
		}
		return getUniqueName(base + " 0", page);
	}

	/**
	 * Return all the possible plot views that are either open, or can be opened because they are defined in plugin.xml
	 * or have data already in the Plot Server
	 * 
	 * @param page
	 *            workbench page to get list of view references from, can be <code>null</code> to automatically load
	 *            default page from Platform
	 * @param returnAll
	 *            if true, returns all the existing plot views, else only the default ones
	 * @return list of plot views
	 */
	public String[] getAllPossibleViews(IWorkbenchPage page, boolean returnAll) {
		Set<String> views = new HashSet<String>();
		views.addAll(Arrays.asList(getOpenViews()));
		if (returnAll) {
			views.addAll(knownPlotViews.keySet());
		} else {
			addDefaultPlotViews(views);
		}
		if (page != null) {
			try {
				IViewReference[] viewReferences = getPage(page).getViewReferences();
				for (IViewReference ref : viewReferences) {
					if (PLOT_VIEW_MULTIPLE_ID.equals(ref.getId())) {
						views.add(ref.getSecondaryId());
					}
				}
			} catch (NullPointerException e) {
				// Not a fatal error, but shouldn't happen
				logger.error("Failed to add list of view references", e);
			}
		}

		try {
			String[] names = getPlotServer().getGuiNames();
			if (names != null)
				views.addAll(Arrays.asList(names));
		} catch (Exception e) {
			logger.error("Failed to get list of GUI names from Plot Server", e);
		}

		return views.toArray(new String[views.size()]);
	}

	/**
	 * Return all default plot views that are either open, or can be opened because they are defined in plugin.xml
	 * @param views the set of views
	 */
	private void addDefaultPlotViews(Set<String> views) {
		Set<String> keys = knownPlotViews.keySet();
		for (String key : keys) {
			if (knownPlotViews.get(key).startsWith(ID)) {
				views.add(key);
			}
		}
	}

	@Override
	public boolean isBeingObserved() {
		return observable.isBeingObserved();
	}

	@Override
	public void addIObserver(IObserver observer) {
		observable.addIObserver(observer);
	}

	@Override
	public void deleteIObserver(IObserver observer) {
		observable.deleteIObserver(observer);
	}

	@Override
	public void deleteIObservers() {
		observable.deleteIObservers();
	}
}
