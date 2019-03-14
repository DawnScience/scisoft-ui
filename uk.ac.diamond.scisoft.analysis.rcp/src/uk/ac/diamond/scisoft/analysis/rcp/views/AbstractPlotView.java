/*
 * Copyright (c) 2012 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.analysis.rcp.views;

import java.io.Serializable;
import java.util.ArrayList;

import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.Platform;
import org.eclipse.dawnsci.analysis.api.fitting.functions.IPeak;
import org.eclipse.dawnsci.plotting.api.IPlottingSystem;
import org.eclipse.dawnsci.plotting.api.tool.IToolPage;
import org.eclipse.dawnsci.plotting.api.tool.IToolPageSystem;
import org.eclipse.dawnsci.plotting.api.views.ISettablePlotView;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.part.ViewPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gda.observable.IObserver;
import uk.ac.diamond.scisoft.analysis.PlotServerProvider;
import uk.ac.diamond.scisoft.analysis.plotclient.BeanScriptingManagerImpl;
import uk.ac.diamond.scisoft.analysis.plotserver.DataBean;
import uk.ac.diamond.scisoft.analysis.plotserver.GuiBean;
import uk.ac.diamond.scisoft.analysis.plotserver.GuiParameters;
import uk.ac.diamond.scisoft.analysis.plotserver.GuiPlotMode;
import uk.ac.diamond.scisoft.analysis.plotserver.IBeanScriptingManager;
import uk.ac.diamond.scisoft.analysis.rcp.plotting.AbstractPlotWindow;
import uk.ac.diamond.scisoft.analysis.rcp.plotting.ExamplePlotWindow;

/**
 * Abstract Class from which PlotView and ROIProfilePlotView both extend 
 * Used to create the main Analysis panel that can display any n-D scalar data 
 * it is the replacement of the Data Vector panel inside the new RCP framework
 * (different from uk.ac.diamond.scisoft.analysis.rcp.views.plot.AbstractPlotView)
 */
public abstract class AbstractPlotView extends ViewPart implements ISettablePlotView {

	// Adding in some logging to help with getting this running
	private static final Logger logger = LoggerFactory.getLogger(AbstractPlotView.class);

	/**
	 * the ID of the view
	 */
	protected String        id;
	protected AbstractPlotWindow plotWindow; // scripting connection

	private boolean                  isDisposed;
	
	protected BeanScriptingManagerImpl manager;

	/**
	 * Default Constructor of the plot view
	 */

	public AbstractPlotView() {
		super();
		init();
	}

	/**
	 * Constructor which must be called by 3rd party extension to extension point
	 * "uk.ac.diamond.scisoft.analysis.rcp.plotView"
	 * 
	 * @param id
	 */
	public AbstractPlotView(String id) {
		super();
		this.id = id;
		init();
	}

	private void init() {
		this.manager = new BeanScriptingManagerImpl(PlotServerProvider.getPlotServer());
	}


	protected boolean isDisposed() {
		return isDisposed;
	}

	@Override
	public void createPartControl(Composite parent) {
		if (getId() != null) {
			// process extension configuration
			logger.info("ID: {}", getId());
			final PlotViewConfig config = new PlotViewConfig(id);
			manager.setViewName(config.getName());
			setPartName(config.getName());
		} else {
			// default to the view name
			manager.setViewName( getViewSite().getRegisteredName() );
			String secondaryId = getViewSite().getSecondaryId();
			if (secondaryId != null) {
				manager.setViewName(secondaryId);
				setPartName(secondaryId);
			}
		}
		logger.info("View name is {}", manager.getViewName());

		parent.setLayout(new FillLayout());

		plotWindow = createPlotWindow(parent, manager, getViewSite().getActionBars(), this, manager.getViewName());

		//catch any errors from addTool and ignore so view is always created cleanly
		try {
			addToolIfRequired();
		} catch (Exception e) {
			//do nothing here but log
			logger.warn(e.getMessage());
		}
	}

	/**
	 * Create a custom Plot Window which will enable to create a custom Plot View with custom 
	 * controls side by side with an IPlottingSystem<Composite> linked to the plot server.<br>
	 * (See {@link ExamplePlotWindow} for more info).
	 * @return AbstractPlotWindow
	 */
	public abstract AbstractPlotWindow createPlotWindow(Composite parent, 
														IBeanScriptingManager manager,
														IActionBars bars, 
														IWorkbenchPart part, 
														String name);

	@Override
	public void setFocus() {
		plotWindow.setFocus();
	}

	/**
	 * Required if you want to make tools work with Abstract Plotting System.
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public Object getAdapter(final Class clazz) {
		if (clazz == IToolPageSystem.class) {
			return plotWindow.getAdapter(clazz);
		}else if (clazz == IPlottingSystem.class) {
			return getPlottingSystem();
		}
		return super.getAdapter(clazz);
	}

	public void clearPlot() {
		plotWindow.clearPlot();
	}

	@Override
	public void dispose() {
		
		this.isDisposed = true;
				
		if (plotWindow != null) plotWindow.dispose();
		if (manager    !=null)  manager.dispose();

		deleteDataObservers();
	}

	public void updatePlotMode(final GuiPlotMode mode) {
		plotWindow.updatePlotMode(mode);
	}

	public void processPlotUpdate(DataBean dBean) {
		processPlotUpdate(dBean, null);
	}

	public void processPlotUpdate(final DataBean dBean, IObserver source) {
		plotWindow.processPlotUpdate(dBean);
		notifyDataObservers(dBean, source);
	}

	public void processGUIUpdate(GuiBean bean) {
		plotWindow.processGUIUpdate(bean);
	}

	public IPlottingSystem<Composite> getPlottingSystem() {
		return plotWindow.getPlottingSystem();
	}

	/**
	 * Allow another observer to see plot data.
	 * <p>
	 * A data observer gets an update with a data bean.
	 * 
	 * @param observer
	 */
	public void addDataObserver(IObserver observer) {
		manager.addDataObserver(observer);
	}

	/**
	 * Remove a data observer
	 * 
	 * @param observer
	 */
	public void deleteDataObserver(IObserver observer) {
		manager.deleteDataObserver(observer);
	}

	/**
	 * Remove all data observers
	 */
	public void deleteDataObservers() {
		manager.deleteDataObservers();
	}

	public void notifyDataObservers(DataBean bean, IObserver source) {
		manager.notifyDataObservers(bean, source);
	}

	@Override
	public void updateData(Serializable data, Class<?> clazz) {
		if (data instanceof ArrayList<?>) {
			ArrayList<?> list = (ArrayList<?>) data;
			if (clazz.getName().equals(IPeak.class.getName())) {
				if (list.isEmpty()) {
					manager.removeGUIInfo(GuiParameters.FITTEDPEAKS);
				} else {
					manager.putGUIInfo(GuiParameters.FITTEDPEAKS, list);
				}
			}
		}
	}

	public String getPlotViewName() {
		return manager.getViewName();
	}

	/**
	 * @return id
	 */
	private String getId() {
		return id;
	}

	public AbstractPlotWindow getPlotWindow() {
		return this.plotWindow;
	}

	private void addToolIfRequired() throws Exception {
		IExtension[] extensions = getExtensions("uk.ac.diamond.scisoft.analysis.rcp.views.PlotViewWithTool");
		if (extensions == null) return;

		for(int i=0; i<extensions.length; i++) {

			IExtension extension = extensions[i];
			IConfigurationElement[] configElements = extension.getConfigurationElements();	

			for(int j=0; j<configElements.length; j++) {
				IConfigurationElement config = configElements[j];
				config.toString();

				String view = config.getAttribute("view_id");
				String tool = config.getAttribute("tool_id");
				String id = getViewSite().getId();

				if (id.equals(view)){

					final IToolPageSystem system = (IToolPageSystem)getAdapter(IToolPageSystem.class);
					
					IToolPage toolpage = system.getToolPage(tool);
					String toolViewId = "";
					
					switch (toolpage.getToolPageRole()) {
					case ROLE_1D:
						toolViewId ="org.dawb.workbench.plotting.views.toolPageView.1D";
						break;
					case ROLE_2D:
						toolViewId ="org.dawb.workbench.plotting.views.toolPageView.2D";
						break;
					case ROLE_3D:
						toolViewId ="org.dawb.workbench.plotting.views.toolPageView.3D";
						break;
					default:
						break;
					}
					
					if (!toolViewId.isEmpty()) {
						system.setToolVisible(tool, toolpage.getToolPageRole(), toolViewId);
					}
				}
			}
		}
	}

	private IExtension[] getExtensions(String extensionPointId) {
		IExtensionRegistry registry = Platform.getExtensionRegistry();
		IExtensionPoint point = registry.getExtensionPoint(extensionPointId);
		IExtension[] extensions = point.getExtensions();
		return extensions;
	}

	@Override
	public void saveState(IMemento memento) {
		plotWindow.getPlottingSystem().savePreferences(memento);
	}
}
