/*
 * Copyright (c) 2012 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.analysis.plotclient;

import java.util.Collection;
import java.util.concurrent.locks.ReentrantLock;

import org.eclipse.dawnsci.analysis.api.roi.IROI;
import org.eclipse.dawnsci.analysis.dataset.roi.ROIList;
import org.eclipse.dawnsci.analysis.dataset.roi.ROIUtils;
import org.eclipse.dawnsci.plotting.api.IPlottingSystem;
import org.eclipse.dawnsci.plotting.api.PlottingFactory;
import org.eclipse.dawnsci.plotting.api.region.IROIListener;
import org.eclipse.dawnsci.plotting.api.region.IRegion;
import org.eclipse.dawnsci.plotting.api.region.IRegionListener;
import org.eclipse.dawnsci.plotting.api.region.ROIEvent;
import org.eclipse.dawnsci.plotting.api.region.RegionEvent;
import org.eclipse.dawnsci.plotting.api.trace.ITraceListener;
import org.eclipse.dawnsci.plotting.api.trace.TraceEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.plotserver.GuiBean;
import uk.ac.diamond.scisoft.analysis.plotserver.GuiParameters;
import uk.ac.diamond.scisoft.analysis.plotserver.IBeanScriptingManager;

/**
 * Class to deal with interactions of regions and ROIs
 */
public class ROIManager implements IROIListener, IRegionListener {
	private static Logger logger = LoggerFactory.getLogger(ROIManager.class);

	private IROI roi;
	private IBeanScriptingManager server; // usually plot window
	private String plotName;
	private IPlottingSystem<?> plottingSystem;
	private ReentrantLock lock;

	ROIManager(IBeanScriptingManager guiManager, String plotName) {
		server = guiManager;
		this.plotName = plotName;
		lock = new ReentrantLock();
	}

	private ITraceListener traceListener;
	/**
	 * Return the Trace Listener to be added to the PlottingSystem
	 * @return ITraceListener
	 */
	public ITraceListener getTraceListener() {
		if (traceListener==null) traceListener = new ITraceListener.Stub() {
			@Override
			public void traceUpdated(TraceEvent evt) {
				addCurrentROI(true, true);
			}

			@Override
			public void traceAdded(TraceEvent evt) {
				addCurrentROI(true, true);
			}
		};
		return traceListener;
	}

	private IPlottingSystem<?> getPlottingSystem() {
		if (plottingSystem == null) {
			plottingSystem = PlottingFactory.getPlottingSystem(plotName);
		}
		if (plottingSystem == null) {
			logger.error("Plotting system is null");
		} else if (plottingSystem.isDisposed()) {
			logger.error("Plotting system is disposed");
		}
		return plottingSystem;
	}

	/**
	 * @return name of ROI (can be null)
	 */
	public IROI getROI() {
		return roi;
	}

	@Override
	public void regionRemoved(RegionEvent evt) {
		IRegion region = evt.getRegion();
		if (region == null)
			return;

		region.removeROIListener(this);
		removeROI(region.getROI());
	}

	@Override
	public void regionsRemoved(RegionEvent evt) {
		if (!evt.isDisposed()) {
			clearGUIBean();
		}
		roi = null;
	}

	@Override
	public void regionNameChanged(RegionEvent evt, String oldName) {
		IRegion region = evt.getRegion();
		if (region == null)
			return;
		
		addCurrentROI(!region.fromServer(), false);
		region.setFromServer(false);
	}

	@Override
	public void regionAdded(RegionEvent evt) {
		IRegion region = evt.getRegion();
		if (region == null)
			return;

		region.addROIListener(this);
		IROI eroi = region.getROI();
		if (eroi != null && !eroi.equals(roi)) {
			roi = eroi;
		}
		addCurrentROI(!region.fromServer(), false);
		region.setFromServer(false);
	}

	@Override
	public void regionCreated(RegionEvent evt) {
		// do nothing
	}

	@Override
	public void regionCancelled(RegionEvent evt) {
	}

	@Override
	public void roiDragged(ROIEvent evt) {
	}

	@Override
	public void roiChanged(ROIEvent evt) {
		IRegion reg = (IRegion) evt.getSource();
		if (reg == null)
			return;
		IROI eroi = evt.getROI();
		if (eroi == null)
			return;

		roi = eroi;

		addCurrentROI(!reg.fromServer(), false);
		reg.setFromServer(false);
	}

	@Override
	public void roiSelected(ROIEvent evt) {
		// do same as if ROI changed
		roiChanged(evt);
	}

	private static final long CLIENT_WAIT_PERIOD = 40;

	private void removeROI(IROI or) {
//		roiMap.remove(or.getName());
		Class<? extends IROI> clazz = or == null ? null : or.getClass();
		if (roi != null) {
			if (!roi.getClass().equals(clazz)) {
				// switch current roi (and list), then delete
				updateGuiBean(clazz, or, false);
				try {
					Thread.sleep(CLIENT_WAIT_PERIOD); // allow time for clients to update
				} catch (InterruptedException e) {
				}
				updateROIMap();
				updateGuiBean(clazz, null, false);
				try {
					Thread.sleep(CLIENT_WAIT_PERIOD); // allow time for clients to update
				} catch (InterruptedException e) {
				}
			} else {
				updateROIMap();
			}

			if (or != null && or.equals(roi.getName())) { // replace current ROI
				roi = getFromROIMap(roi.getClass());
			}
			roi = updateGuiBean(roi.getClass(), roi, false);
		} else {
			updateROIMap();
			roi = updateGuiBean(clazz, or, false);
		}
	}

	private void addCurrentROI(boolean updateServer, boolean quietUpdate) {
		updateROIMap();
		if (updateServer && roi != null)
			updateGuiBean(roi.getClass(), roi, quietUpdate);
	}

	private IROI updateGuiBean(Class<? extends IROI> clazz, IROI r, boolean quietUpdate) {
		// if locked then do not update server
		if (lock.isLocked()) {
			logger.trace("Silent update: {}", r);
			return r;
		}
		logger.trace("Broadcasting update: {}", r);

		GuiBean bean = server.getGUIInfo();
		bean.remove(GuiParameters.ROICLEARALL);
		bean.remove(GuiParameters.ROIDATALIST);
		bean.put(GuiParameters.ROIDATA, null);
		if (quietUpdate) {
			bean.put(GuiParameters.QUIET_UPDATE, "");
		}
		ROIList<?> list = buildNewROIList(clazz);
		if (list != null && list.size() > 0) {
			bean.put(GuiParameters.ROIDATALIST, list);
			if (r != null && !list.contains(r)) {
				r = list.get(0);
			}
			if (r != null) {
				bean.put(GuiParameters.ROIDATA, r.getName());
			}
		} else {
			r = null;
		}
		server.sendGUIInfo(bean);
		return r;
	}

	private void clearGUIBean() {
		// if locked then do not update server
		if (lock.isLocked()) {
			logger.trace("Silent clear");
			return;
		}
		logger.trace("Broadcasting clear");

		GuiBean bean = server.getGUIInfo();
		bean.put(GuiParameters.ROIDATA, null);
		bean.remove(GuiParameters.ROIDATALIST);
		bean.put(GuiParameters.ROICLEARALL, true);
		server.sendGUIInfo(bean);
	}

	private ROIList<?> buildNewROIList(Class<? extends IROI> clazz) {
		ROIList<? extends IROI> list = ROIUtils.createNewROIList(clazz);
		if (list == null) {
			return null;
		}
		final Collection<IRegion> regions = getPlottingSystem().getRegions();
		if (regions != null) {
			for (IRegion iRegion : regions) {
				IROI r = iRegion.getROI();
				if (r != null && r.getClass().equals(clazz)) {
					list.add(r);
				}
			}
		}
		return list;
	}

	private IROI getFromROIMap(Class<? extends IROI> clazz) {
		final Collection<IRegion> regions = getPlottingSystem().getRegions();
		if (regions != null) {
			for (IRegion iRegion : regions) {
				IROI r = iRegion.getROI();
				if (r != null && r.getClass().equals(clazz)) {
					return r;
				}
			}
		}
		return null;
	}

	/**
	 * This stops ROI manager from broadcasting ROIs back to the plot server
	 */
	public void acquireLock() {
		lock.lock();
	}

	/**
	 * This allows ROI manager to broadcast ROIs back to the plot server
	 */
	public void releaseLock() {
		lock.unlock();
	}

	/**
	 * Method to update roiMap when region name is changed through the UI
	 * This is done by comparing the list of regions in the plottingSystem
	 * and the regions in roiMap
	 */
	private void updateROIMap() {
		// this no longer does anything!!! but leave in place
		// TODO update bean with a  map
//		if (plottingSystem == null)
//			plottingSystem = PlottingFactory.getPlottingSystem(plotName);
//		if (plottingSystem == null)
//			return;
//		Collection<IRegion> regions = plottingSystem.getRegions();
//		if (regions == null)
//			return;
//		Set<String> regNames = new HashSet<String>();
//		for (IRegion iRegion : regions) {
//			regNames.add(iRegion.getName());
//		}
	}
}
