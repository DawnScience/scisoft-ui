/*
 * Copyright (c) 2015 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package uk.ac.diamond.scisoft.analysis.plotclient.connection;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.dawnsci.analysis.api.roi.IROI;
import org.eclipse.dawnsci.analysis.dataset.roi.ROIList;
import org.eclipse.dawnsci.plotting.api.IPlottingSystem;
import org.eclipse.dawnsci.plotting.api.region.IRegion;
import org.eclipse.dawnsci.plotting.api.region.IRegion.RegionType;
import org.eclipse.dawnsci.plotting.api.region.IRegionService;
import org.eclipse.dawnsci.plotting.api.region.RegionUtils;
import org.eclipse.swt.widgets.Display;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.plotclient.Activator;
import uk.ac.diamond.scisoft.analysis.plotserver.GuiBean;
import uk.ac.diamond.scisoft.analysis.plotserver.GuiParameters;

/**
 * AbstractPlotConnection class with gui update for regions
 *
 */
public class PlottingGUIUpdate extends AbstractPlotConnection {

	private static final Logger logger = LoggerFactory.getLogger(PlottingGUIUpdate.class);

	protected IPlottingSystem<?> plottingSystem;

	public PlottingGUIUpdate(IPlottingSystem<?> plottingSystem) {
		this.plottingSystem = plottingSystem;
	}

	@Override
	public void processGUIUpdate(final GuiBean guiBean) {
		
		logger.debug("There is a guiBean update: {}", guiBean);

		Display.getDefault().syncExec(new Runnable() {
			@Override
			public void run() {
//				TODO investigate why this is called when there is a really small time between 2 plot updates
//				System.err.println("Should not be called!!!");
				final Boolean clearAll = (Boolean) guiBean.remove(GuiParameters.ROICLEARALL);
				if (clearAll != null && clearAll) {
					plottingSystem.clearRegions();
				}

				String rName = (String) guiBean.get(GuiParameters.ROIDATA);
				IROI croi = manager.getROI();
				ROIList<? extends IROI> list = (ROIList<?>) guiBean.get(GuiParameters.ROIDATALIST);

				IROI roi = null;
				final Set<String> names = new HashSet<String>(); // names of ROIs
				if (rName != null) {
					names.add(rName);
					logger.trace("R: {}", rName);
				}
				if (list != null) {
					for (IROI r : list) {
						String n = r.getName();
						if (n != null) {
							n = n.trim();
							if (n.length() > 0) {
								logger.trace("L: {}", r.getName());
								names.add(n);
								if (n.equals(rName) && roi == null) {
									roi = r;
								}
							}
						}
					}
				}

				if (rName == null) { // this indicates to remove the current ROI
					if (croi != null) {
						final IRegion r = plottingSystem.getRegion(croi.getName());
						if (r != null) {
							rName = croi.getName(); // set so it is ignored later
							plottingSystem.removeRegion(r);
						}
						croi = null;
					}
				} else {
					if (rName.trim().length() == 0) {
						rName = null;
					}
				}

				final Collection<IRegion> regions = plottingSystem.getRegions();
				Set<String> regNames = new HashSet<String>(); // regions not removed
				if (regions != null) {
					for (IRegion reg : regions) { // clear all regions not listed
						String regName = reg.getName();
						if (!names.contains(regName)) {
							plottingSystem.removeRegion(reg);
						} else {
							regNames.add(regName);
						}
					}
				}
				if (list != null) {
					for (IROI r : list) {
						String n = r.getName();
						if (n != null) {
							n = n.trim();
							if (n.length() == 0) {
								n = null;
							}
						}
						
						if (r == croi || r == roi || (n != null && n.equals(rName))) {
							continue; // no need to update current region
						}
						if (n != null && !names.contains(n)) {
							continue;
						}

						if (regNames.contains(n)) { // update ROI
							IRegion region = plottingSystem.getRegion(n);
							if (region != null)
								region.setROI(r);
						} else { // or add new region that has not been listed
							IRegion reg = n == null ? null : plottingSystem.getRegion(n);
							if (reg == null) {
								createRegion(r, guiBean);
							} else {
								reg.setFromServer(true);
								reg.setROI(r);
							}
						}
					}
				}

				if (roi != null) { // create new region
					IRegion region = plottingSystem.getRegion(rName);
					if (region == null) {
						createRegion(roi, guiBean);
					} else {
						region.setROI(roi);
					}
				} else if (croi != null) {
					IRegion region = plottingSystem.getRegion(croi.getName());
					if (region != null) {
						region.setROI(croi);
					}
				}
			}
		});
	}

	private IRegion createRegion(IROI roib, GuiBean guiBean) {
		if (roib == null)
			return null;
		try {
			final IRegionService rservice = (IRegionService)Activator.getService(IRegionService.class);
			RegionType type = rservice.getRegion(roib.getClass());
			String name = roib.getName();
			if (name == null || name.trim().length() == 0) {
				name = RegionUtils.getUniqueName(type.getName(), plottingSystem);
				logger.warn("Blank or unset name in ROI now set to {}", name);
				roib.setName(name);
				guiBean.put(GuiParameters.QUIET_UPDATE, ""); // mark bean to be sent back 
			}
			IRegion region = plottingSystem.createRegion(name, type);
			region.setFromServer(true);
			region.setROI(roib);
			plottingSystem.addRegion(region);
			if (roib.isFixed()) {
				region.setMobile(false);
				region.setFill(false);
			}
			return region;
		} catch (Exception e) {
			logger.error("Problem creating new region from ROI", e);
		}
		return null;
	}
}
