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

import gda.observable.IObserver;

import java.io.Serializable;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.PlotServer;
import uk.ac.diamond.scisoft.analysis.plotserver.DataBean;
import uk.ac.diamond.scisoft.analysis.plotserver.GuiBean;
import uk.ac.diamond.scisoft.analysis.plotserver.GuiParameters;
import uk.ac.diamond.scisoft.analysis.plotserver.GuiUpdate;
import uk.ac.diamond.scisoft.analysis.plotserver.IBeanScriptingManager;

/**
 * A class which connects an AbstractPlottingConnection to a plot server. 
 * 
 */
public class BeanScriptingManagerImpl implements IBeanScriptingManager, IObserver {
	
	private static final Logger logger = LoggerFactory.getLogger(BeanScriptingManagerImpl.class);
	
	private final PlotServer    server;
	private ScriptingConnection window;
	private String              viewName;
	private BlockingDeque<PlotEvent> queue;
	private UUID                plotID;

	private Set<IObserver>   dataObservers;

	public BeanScriptingManagerImpl(PlotServer server) {
	    this(server, "Plot View");	
	}
	
	BeanScriptingManagerImpl(PlotServer server, String viewName) {

		this.server   = server;
		this.viewName = viewName;
		
		this.plotID = UUID.randomUUID();
		logger.info("Plot view uuid: {}", plotID);

		// Blocking queue to which we add plot update events.
		this.queue = new LinkedBlockingDeque<PlotEvent>(25);
		
		this.dataObservers = Collections.synchronizedSet(new LinkedHashSet<IObserver>());
	}

	private Thread plotThread;
	
	private void start() {

		// We have a thread which processes the queue
		this.plotThread = createPlotEventThread();
		plotThread.setDaemon(true);
		plotThread.start();

		server.addIObserver(this);
	}

	public ScriptingConnection getConnection() {
		return window;
	}

	public void setConnection(ScriptingConnection window) {
		this.window = window;
		if (plotThread==null) start();
	}

	private Thread createPlotEventThread() {
		return new Thread(new Runnable() {
			@Override
			public void run() {
				while (!window.getPlottingSystem().isDisposed()) {
					try {
						PlotEvent event = queue.take();
						if (event.getStashedGuiBean()==null && event.getGuiBean()==null) {
							// This event is not of interest
							continue;
						}
						
						GuiBean bean = event.getStashedGuiBean();
						DataBean dataBean = event.getDataBean();
	
						// if there is a stashedGUIBean to update then do that update first
						if (bean != null) {
							event.setStashedGuiBean(null);
							if (window != null) {
								window.processGUIUpdate(bean);
							}
						}

						// once the guiBean has been sorted out, see if there is any need to update the dataBean
						if (dataBean != null) {
							event.setDataBean(null);
							if (window != null) {
								window.processPlotUpdate(dataBean);
							}
							notifyDataObservers(dataBean, null);
						}
					} catch (Throwable ne) {
						logger.debug("Exception raised in plot server job", ne);
						continue; // We still keep going until the part is disposed
					}
				}
			}
		}, "Bean Scripting Manager for '"+plotID+"'");
	}

	/**
	 * Allow another observer to see plot data.
	 * <p>
	 * A data observer gets an update with a data bean.
	 * 
	 * @param observer
	 */
	public void addDataObserver(IObserver observer) {
		synchronized (dataObservers) {
			dataObservers.add(observer);
		}
	}

	/**
	 * Remove a data observer
	 * 
	 * @param observer
	 */
	public void deleteDataObserver(IObserver observer) {
		synchronized (dataObservers) {
			dataObservers.remove(observer);
		}
	}

	/**
	 * Remove all data observers
	 */
	public void deleteDataObservers() {
		synchronized (dataObservers) {
			dataObservers.clear();
		}
	}

	public void notifyDataObservers(DataBean bean, IObserver source) {
		synchronized (dataObservers) {
			Iterator<IObserver> iter = dataObservers.iterator();
			while (iter.hasNext()) {
				IObserver ob = iter.next();
				if (ob == source) { // skip updating source
					continue;
				}
				ob.update(this, bean);
			}
		}
	}

	@Override
	public void update(Object theObserved, Object changeCode) {
		final Thread thd = Thread.currentThread();

		if (changeCode instanceof String && changeCode.equals(viewName)) {
			logger.debug("Getting a plot data update for {}; thd {} {}",  viewName, thd.getId(), thd.getName());
			GuiBean guiBean = getGUIBean();
			final PlotEvent evt = new PlotEvent();
			try {
				DataBean dataBean = getPlotServer().getData(viewName);
				if (dataBean != null) {
					dataBean = dataBean.copy(); // need to make a (shallow) copy otherwise changes get out of sync
					if (logger.isTraceEnabled()) {
						// don't pass databean itself to logger to prevent slow loggers holding references to it
						// for longer than necessary
						String beanString = dataBean.toString();
						logger.trace("BSM copied data bean ({}) {}", dataBean.getData().size(), beanString);
					}
				}
				evt.setDataBean(dataBean);
			} catch (Exception e) {
				logger.error("There has been an issue retrieving the databean from the plotserver", e);
			}
			evt.setGuiBean(guiBean);
			offer(evt);
		} else if (changeCode instanceof GuiUpdate) {
			GuiUpdate gu = (GuiUpdate) changeCode;
			if (gu.getGuiName().contains(viewName)) {
				
				GuiBean        bean = gu.getGuiData();
				final PlotEvent evt = new PlotEvent();
				GuiBean     guiBean = getGUIBean();
				
				UUID id = (UUID) bean.get(GuiParameters.PLOTID);
				if (id == null || plotID.compareTo(id) != 0) { // filter out own beans
					logger.debug("Getting a plot gui update for {}; thd {} {}; bean {}", new Object[] {viewName, thd.getId(), thd.getName(), bean});
					if (guiBean == null) {
						guiBean = bean.copy(); // cache a local copy
					} else {
						guiBean.merge(bean); // or merge it
					}
					guiBean.remove(GuiParameters.ROICLEARALL); // this parameter must not persist
					evt.setStashedGuiBean(bean);
					evt.setGuiBean(guiBean);
					offer(evt);
				}
			}
		}
	}


	/**
	 * Get gui information from plot server
	 */
	@Override
	public GuiBean getGUIInfo() {
		return getGUIBean();
	}

	private GuiBean getGUIBean() {
		GuiBean guiBean = null;
		try {
			guiBean = getPlotServer().getGuiState(viewName);
		} catch (Exception e) {
			logger.warn("Problem with getting GUI data from plot server", e);
		}
		if (guiBean == null) {
			logger.error("This should not happen! View name '{}' is not found.", viewName);
			guiBean = new GuiBean();
		}
		return guiBean;
	}

	public PlotServer getPlotServer() {
		return server;
	}
	
	/**
	 * Push GUI information back to plot server
	 * 
	 * @param key
	 * @param value
	 */
	@Override
	public void putGUIInfo(GuiParameters key, Serializable value) {
		GuiBean guiBean = getGUIBean();

		guiBean.put(key, value);

		sendGUIInfo(guiBean);
	}

	/**
	 * Remove GUI information from plot server
	 * 
	 * @param key
	 */
	@Override
	public void removeGUIInfo(GuiParameters key) {
		GuiBean guiBean = getGUIBean();

		guiBean.remove(key);

		sendGUIInfo(guiBean);
	}

	@Override
	public void sendGUIInfo(GuiBean guiBean) {
		guiBean.put(GuiParameters.PLOTID, plotID); // put plotID in bean

		try {
			getPlotServer().updateGui(viewName, guiBean);
		} catch (Exception e) {
			logger.warn("Problem with updating plot server with GUI data", e);
		}
	}

	public String getViewName() {
		return viewName;
	}

	public void setViewName(String plotViewName) {
		this.viewName = plotViewName;
	}

	public void dispose() {
		synchronized (dataObservers) {
			dataObservers.clear();
		}
		queue.clear();
		queue.add(new PlotEvent());
		getPlotServer().deleteIObserver(this);
	}

	/**
	 * Tries to put plot event into queue or 
	 * drops some events from the queue
	 * 
	 * TODO dropping of events should be done if
	 * the event is a plot update. Region events
	 * should never be dropped.
	 * 
	 * @param evt
	 */
	public synchronized void offer(PlotEvent evt) {
		if (queue.offer(evt)) return;
		queue.remove(); // drop the head - TODO FIXME not region events!
		if (!queue.offer(evt)) {
			throw new RuntimeException("Cannot offer plot events to queue!");
		}
	}

}
