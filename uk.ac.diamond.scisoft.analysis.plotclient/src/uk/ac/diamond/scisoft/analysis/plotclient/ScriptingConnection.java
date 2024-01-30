/*-
 * Copyright 2014 Diamond Light Source Ltd.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.ac.diamond.scisoft.analysis.plotclient;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.dawnsci.plotting.api.IPlottingSystem;
import org.eclipse.dawnsci.plotting.api.PlotType;
import org.eclipse.dawnsci.plotting.api.axis.AxisUtils;
import org.eclipse.dawnsci.plotting.api.axis.IAxis;
import org.eclipse.dawnsci.plotting.api.region.IRegion;
import org.eclipse.swt.widgets.Composite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gda.observable.IObservable;
import gda.observable.IObserver;
import uk.ac.diamond.scisoft.analysis.PlotServerProvider;
import uk.ac.diamond.scisoft.analysis.plotclient.connection.AbstractPlotConnection;
import uk.ac.diamond.scisoft.analysis.plotclient.connection.IPlotConnection;
import uk.ac.diamond.scisoft.analysis.plotclient.connection.PlotConnectionFactory;
import uk.ac.diamond.scisoft.analysis.plotserver.AxisOperation;
import uk.ac.diamond.scisoft.analysis.plotserver.DataBean;
import uk.ac.diamond.scisoft.analysis.plotserver.GuiBean;
import uk.ac.diamond.scisoft.analysis.plotserver.GuiParameters;
import uk.ac.diamond.scisoft.analysis.plotserver.GuiPlotMode;
import uk.ac.diamond.scisoft.analysis.plotserver.IBeanScriptingManager;


/**
 * Class for connecting a plotting system with the plot server.
 */
public class ScriptingConnection implements IObservable {
	
	private static final Logger logger = LoggerFactory.getLogger(ScriptingConnection.class);

	protected IPlottingSystem<Composite> plottingSystem;
	protected String name;

	private List<IObserver> observers = Collections.synchronizedList(new LinkedList<IObserver>());
	private IBeanScriptingManager manager = null;
	private IUpdateNotificationListener notifyListener = null;
	
	protected DataBean myBeanMemory;
	protected ROIManager roiManager;

	protected IPlotConnection plotConnection = null;
	private boolean isUpdatePlot = false;

	private GuiPlotMode previousMode = GuiPlotMode.EMPTY;

	/**
	 * 
	 * @param manager
	 * @param name
	 */
	public ScriptingConnection(String name) {
		this(new BeanScriptingManagerImpl(PlotServerProvider.getPlotServer(), name), name);
	}

	/**
	 * 
	 * @param manager
	 * @param name
	 */
	public ScriptingConnection(IBeanScriptingManager manager, String name) {
		
		this.manager = manager;
		this.name    = name;
		roiManager = new ROIManager(manager, name);
	}
	
	/**
	 * Set to null not to receive updates
	 * @param listener
	 */
	public void setNotifyListener(IUpdateNotificationListener listener) {
		this.notifyListener = listener;
	}
	
	/**
	 * Call to set the plotting system to which we will connect.
	 * 
	 * This must be called!
	 * 
	 * @param system
	 */
	public void setPlottingSystem(IPlottingSystem<Composite> system) {
		if (plottingSystem!=null) throw new IllegalArgumentException("The plotting system has already been set!");

		plottingSystem = system;

		system.addRegionListener(getRoiManager());
		system.addTraceListener(getRoiManager().getTraceListener());

		if (manager instanceof BeanScriptingManagerImpl man) {
			man.setConnection(this);
			GuiBean bean = man.getGUIInfo();
			updatePlotMode(bean);
			
			final PlotEvent evt = new PlotEvent();
			try {
				evt.setDataBean(man.getPlotServer().getData(name));
			} catch (Exception e) {
				logger.warn("Could not retrieve plot data from server - {}", name, e);
			}
			logger.trace("Stashing gui bean - {}: {}", name, manager.getGUIInfo());
			evt.setStashedGuiBean(manager.getGUIInfo());
			man.offer(evt);
		}

		GuiPlotMode plotMode = getPlotMode();
		changePlotMode(plotMode == null ? GuiPlotMode.ONED : plotMode);
	}


	/**
	 * Set the default plot mode
	 * @return gui plotmode
	 */
	public GuiPlotMode getPlotMode() {
		return PlotConnectionFactory.getPlotMode(plottingSystem);
	}

	/**
	 * @return plot UI
	 */
	public IPlotConnection getPlotUI() {
		return plotConnection;
	}

	/**
	 * Cleaning of the plotting system and its composite before the setting up of a datasetPlotter
	 */
	protected void cleanUpPlottingSystem() {
		if (!plottingSystem.isDisposed()) {
			plottingSystem.clearRegions();
			plottingSystem.removeRegionListener(getRoiManager());
			plottingSystem.removeTraceListener(getRoiManager().getTraceListener());
			plottingSystem.dispose();
		}
	}

	/**
	 * Return the name of the Window
	 * @return name
	 */
	public String getName() {
		return name;
	}

	/**
	 * Returns the IBeanScriptingManager of the window
	 * @return manager
	 */
	protected IBeanScriptingManager getGuiManager() {
		return manager;
	}

	/**
	 * Returns the notifyListener of the window
	 * @return notifyListener
	 */
	protected IUpdateNotificationListener getNotififyListener() {
		return notifyListener;
	}

	@Override
	public void addIObserver(IObserver observer) {
		synchronized (observers) {
			if (!observers.contains(observer)) {
				observers.add(observer);
			}
		}
	}

	@Override
	public void deleteIObserver(IObserver observer) {
		synchronized (observers) {
			observers.remove(observer);
		}
	}

	@Override
	public void deleteIObservers() {
		synchronized (observers) {
			observers.clear();
		}
	}

	protected List<IObserver> getObservers() {
		return observers;
	}

	public void notifyUpdateFinished() {
		if (notifyListener != null)
			notifyListener.updateProcessed();
	}

	/**
	 * Process a plot with data packed in bean - remember to update plot mode first if you do not know the current mode
	 * or if it is to change
	 * @param dbPlot
	 */
	public void processPlotUpdate(DataBean dbPlot) {
		// there may be some gui information in the databean, if so this also needs to be updated
		GuiBean guiBean = dbPlot.getGuiParameters();
		if (guiBean != null && !guiBean.isEmpty()) {
			if (plottingSystem!=null && plottingSystem.isDisposed()) {
				// this can be caused by the same plot view shown on 2 difference perspectives.
				throw new IllegalStateException("parentComp is already disposed");
			}

			logger.trace("Processing GUI bean - {}: {}", name, guiBean);
			processGUIUpdate(dbPlot.getGuiParameters());
		}
		
		if (plotConnection != null) {
			logger.trace("Processing dBean - {}: {}", name, dbPlot.getData());
			plotConnection.processPlotUpdate(dbPlot, isUpdatePlot());
			setDataBean(dbPlot);
			createRegion();
		}
	}

	/**
	 * Creates a region if needed
	 */
	public void createRegion() {
		// do nothing
	}

	/**
	 * Update the GuiBean
	 * @param bean
	 */
	public void processGUIUpdate(GuiBean bean) {
		setUpdatePlot(false);
		if (bean.containsKey(GuiParameters.PLOTMODE)) {
			updatePlotMode(bean);
		}

		if (bean.containsKey(GuiParameters.AXIS_OPERATION)) {
			AxisOperation operation = (AxisOperation) bean.get(GuiParameters.AXIS_OPERATION);
			processAxisOperation(operation);
		}

		if (bean.containsKey(GuiParameters.PLOTOPERATION)) {
			String opStr = (String) bean.get(GuiParameters.PLOTOPERATION);
			if (opStr.equals(GuiParameters.PLOTOP_UPDATE)) {
				setUpdatePlot(true);
			}
		}

		if (plotConnection != null && (bean.containsKey(GuiParameters.ROICLEARALL) || bean.containsKey(GuiParameters.ROIDATA) || bean.containsKey(GuiParameters.ROIDATALIST))) {
			try {
				// lock ROI manager
				getRoiManager().acquireLock();
				plotConnection.processGUIUpdate(bean);
			} finally {
				// release ROI manager
				getRoiManager().releaseLock();
			}
		}

		if (bean.containsKey(GuiParameters.QUIET_UPDATE)) {
			manager.sendGUIInfo(bean);
		}
	}

	private void processAxisOperation(final AxisOperation operation) {
		if (plottingSystem == null || plottingSystem.isDisposed())
			return;

		/** Reverted part of commit ce03f352d29 as this bundle is required by the GDA Server
		 * to support the Jython plotting. Using the org.dawb.common.ui runInDisplayThread method
		 * instead of calling asyncExec directly introduces lots of UI/client dependencies 
		 * into the GDA server which we are trying to avoid, hence the roll back.
		 *  
		 * @see https://github.com/DawnScience/scisoft-ui/commit/ce03f352d29c16ef5c3a67191b929ed7699c616c
		 * for the original change
		 */
		plottingSystem.getPlotComposite().getDisplay().asyncExec(
			() -> {
				String title = operation.getTitle();
				String type = operation.getOperationType();
				IAxis a = null;
				if (type.equals(AxisOperation.CREATE)) {
					boolean isYAxis = operation.isYAxis();
					a = AxisUtils.findAxis(isYAxis, title, plottingSystem);
					int side = operation.getSide();
					if (a != null) {
						logger.warn("Axis already exists - {}: {}", name, title);
						return;
					}
					plottingSystem.createAxis(title, isYAxis, side);
					logger.trace("Created - {}: {}", name, title);
				} else if (type.equals(AxisOperation.RENAMEX)) {
					a = plottingSystem.getSelectedXAxis();
					a.setTitle(title);
					logger.trace("Renamed x - {}: {}", name, title);
				} else if (type.equals(AxisOperation.RENAMEY)) {
					a = plottingSystem.getSelectedYAxis();
					a.setTitle(title);
					logger.trace("Renamed y - {}: {}", name, title);
				}
			}
		);
	}

	protected void changePlotMode(GuiPlotMode plotMode) {
		
		clearPlot(!GuiPlotMode.EMPTY.equals(getPreviousMode()));
		
		// TODO Baha check that this is ok - it seems to work and is
		// better than creating infinitely many plotUIs and not disposing
		// them, one would have imagined...
		if (plotConnection != null) {
			plotConnection.deactivate(false);
			plotConnection.dispose();
		}

		AbstractPlotConnection pc = PlotConnectionFactory.getConnection(plotMode, plottingSystem);
		plotConnection = pc;
		if (pc != null) pc.setManager(getRoiManager());

		if (plotMode.equals(GuiPlotMode.EMPTY)) {
			resetAxes();
		}
		setVisibleByPlotType(plottingSystem.getPlotType());
	}

	/**
	 * Update the Plot Mode
	 * @param plotMode
	 */
	public void updatePlotMode(final GuiPlotMode plotMode) {
		logger.debug("Update plot mode - {}: {}", name, plotMode);
		try {
			if (plotMode.equals(GuiPlotMode.EXPORT)) {
				GuiBean bean = getGuiManager().getGUIInfo();
				plottingSystem.savePlotting((String)bean.get(GuiParameters.SAVEPATH),
						(String)bean.get(GuiParameters.FILEFORMAT));
			} else if (plotMode.equals(GuiPlotMode.RESETAXES)) {
				resetAxes();
			} else {
				GuiPlotMode oldMode = getPreviousMode();
				if (!plotMode.equals(oldMode)) {
					logger.debug("Changing plot mode - {}: {} to {}", name, oldMode, plotMode);
					changePlotMode(plotMode);
					setPreviousMode(plotMode);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			logger.error("Error exporting plot - {}: {}", name, e.getMessage());
		}
	}

	/**
	 * Update the Plot mode with a GuiBean
	 * @param bean
	 */
	public void updatePlotMode(GuiBean bean) {
		if (bean != null) {
			// bean does not necessarily have a plot mode (eg, it contains ROIs only)
			GuiPlotMode plotMode = (GuiPlotMode) bean.get(GuiParameters.PLOTMODE);
			if (plotMode != null)
				updatePlotMode(plotMode);
		}
	}

	/**
	 * Clear the Plot Window and its components
	 */
	public void clearPlot() {
		clearPlot(true);
	}

	private void clearPlot(boolean resetAxes) {
		if (plottingSystem != null) {
			plottingSystem.clear();
			if (resetAxes)
				plottingSystem.resetAxes();
		}
	}

	/**
	 * Sets the visibility of region according to Plot type
	 * @param type
	 */
	protected void setVisibleByPlotType(final PlotType type) {
		final Collection<IRegion> regions = plottingSystem.getRegions();
		if (regions == null)
			return;

		plottingSystem.getPlotComposite().getDisplay().syncExec(() -> {
			for (IRegion iRegion : regions) {
				iRegion.setVisible(iRegion.getPlotType().equals(type));
			}
		});

	}

	/**
	 * Reset the axes in the Plot Window
	 */
	private void resetAxes() {
		if (plottingSystem != null) {
			plottingSystem.resetAxes();
		}
	}

	public DataBean getDataBean() {
		return myBeanMemory;
	}

	public void setDataBean(DataBean bean) {
		myBeanMemory = bean;
	}

	public boolean isUpdatePlot() {
		return isUpdatePlot;
	}

	public void setUpdatePlot(boolean isUpdatePlot) {
		this.isUpdatePlot = isUpdatePlot;
	}

	public GuiPlotMode getPreviousMode() {
		return previousMode;
	}

	public void setPreviousMode(GuiPlotMode previousMode) {
		this.previousMode = previousMode;
	}

	public ROIManager getRoiManager() {
		return roiManager;
	}

	public IPlottingSystem<Composite> getPlottingSystem() {
		return plottingSystem;
	}

	/**
	 * Required if you want to make tools work with Abstract Plotting System.
	 */
	public Object getAdapter(final Class<?> clazz) {
		return plottingSystem != null ? plottingSystem.getAdapter(clazz) : null;
	}

	public void dispose() {
		
		if (plotConnection != null) {
			plotConnection.deactivate(false);
			plotConnection.dispose();
		}
		try {
			if (plottingSystem != null && !plottingSystem.isDisposed()) {
				plottingSystem.removeTraceListener(getRoiManager().getTraceListener());
				plottingSystem.removeRegionListener(getRoiManager());
			}
		} catch (Exception ne) {
			logger.debug("Cannot clean up plotter - {}!", name, ne);
		}
		deleteIObservers();
		plotConnection = null;
	}
}
