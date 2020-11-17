/*-
 * Copyright (c) 2016 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package uk.ac.diamond.scisoft.arpes.calibration.utils;

import java.io.File;
import java.lang.reflect.InvocationTargetException;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.dawnsci.analysis.api.message.DataMessageComponent;
import org.eclipse.dawnsci.analysis.api.tree.GroupNode;
import org.eclipse.dawnsci.analysis.tree.TreeFactory;
import org.eclipse.dawnsci.nexus.NexusConstants;
import org.eclipse.dawnsci.nexus.NexusException;
import org.eclipse.dawnsci.nexus.NexusFile;
import org.eclipse.january.dataset.IDataset;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CalibrationSaver implements IRunnableWithProgress {

	private static final Logger logger = LoggerFactory.getLogger(CalibrationSaver.class);
	private DataMessageComponent calibrationData;
	private NexusFile nexus;

	public CalibrationSaver(DataMessageComponent calibrationData) {
		this.calibrationData = calibrationData;
	}

	@Override
	public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
		monitor.beginTask("Saving calibration data...", 8);
		try {
			String filePath = (String) calibrationData.getUserObject(ARPESCalibrationConstants.SAVE_PATH);
			File file = new File(filePath);
			// check if overwite flag is true
			boolean isOverwrite = (boolean) calibrationData.getUserObject(ARPESCalibrationConstants.OVERWRITE);
			if (!isOverwrite && file.exists()) {
				throw new InterruptedException("File already exist! please check the 'Overwrite' option.");
			} else if (isOverwrite && file.exists()) {
				file.delete();
			}
			nexus = ServiceHolder.getNexusFactory().newNexusFile(filePath);
			nexus.openToWrite(true);
			// save raw data
			IDataset data = (IDataset) calibrationData.getList(ARPESCalibrationConstants.DATANAME);
			IDataset xaxis = (IDataset) calibrationData.getList(ARPESCalibrationConstants.XAXIS_DATANAME);
			IDataset yaxis = (IDataset) calibrationData.getList(ARPESCalibrationConstants.YAXIS_DATANAME);
			saveData(data, xaxis, yaxis, "/entry/calibration/analyser");
			monitor.worked(1);
			
			IDataset angleaxis = (IDataset) calibrationData.getList(ARPESCalibrationConstants.ANGLE_AXIS);

			// save background
			IDataset backgroundData = (IDataset) calibrationData.getList(ARPESCalibrationConstants.BACKGROUND);
			saveData(backgroundData, angleaxis, null, ARPESCalibrationConstants.BACKGROUND);
			monitor.worked(1);
			// save background slope
			IDataset backgroundSlopeData = (IDataset) calibrationData.getList(ARPESCalibrationConstants.BACKGROUND_SLOPE);
			saveData(backgroundSlopeData, angleaxis, null, ARPESCalibrationConstants.BACKGROUND_SLOPE);
			monitor.worked(1);
			// save fermi_edge_step_height
			IDataset fermiEdgeStepHeightData = (IDataset) calibrationData.getList(ARPESCalibrationConstants.FERMI_EDGE_STEP_HEIGHT);
			saveData(fermiEdgeStepHeightData, angleaxis, null, ARPESCalibrationConstants.FERMI_EDGE_STEP_HEIGHT);
			monitor.worked(1);
			// save fitted
			IDataset fitteddata = (IDataset) calibrationData.getList(ARPESCalibrationConstants.FITTED);
			IDataset energaxis = (IDataset) calibrationData.getList(ARPESCalibrationConstants.ENERGY_AXIS);
			saveData(fitteddata, energaxis, angleaxis, ARPESCalibrationConstants.FITTED);
			monitor.worked(1);
			// save fittedMu
			IDataset fittedMu = (IDataset) calibrationData.getList(ARPESCalibrationConstants.FUNCTION_FITTEDMU_DATA);
			saveData(fittedMu, yaxis, null, ARPESCalibrationConstants.FUNCTION_FITTEDMU_DATA);
			monitor.worked(1);
			// save fwhm
			IDataset fwhmData = (IDataset) calibrationData.getList(ARPESCalibrationConstants.FWHM_DATA);
			saveData(fwhmData, angleaxis, null, ARPESCalibrationConstants.FWHM_DATA);
			monitor.worked(1);
			// save mu
			IDataset muData = (IDataset) calibrationData.getList(ARPESCalibrationConstants.MU_DATA);
			saveData(muData, angleaxis, null, ARPESCalibrationConstants.MU_DATA);
			monitor.worked(1);
			// save residuals
			IDataset residualsData = (IDataset) calibrationData.getList(ARPESCalibrationConstants.RESIDUALS_DATA);
			saveData(residualsData, angleaxis, null, ARPESCalibrationConstants.RESIDUALS_DATA);
			monitor.worked(1);
			// save temperature
			IDataset temperatureData = (IDataset) calibrationData.getList(ARPESCalibrationConstants.TEMPERATURE);
			saveData(temperatureData, angleaxis, null, ARPESCalibrationConstants.TEMPERATURE);
			monitor.worked(1);
		} catch (NexusException e) {
			logger.error("Error writing to Nexus file:" + e.getMessage());
			e.printStackTrace();
		} finally {
			if (nexus != null)
				try {
					nexus.close();
				} catch (NexusException e) {
					e.printStackTrace();
				}
		}
	}

	private void saveData(IDataset data, IDataset xaxisData, IDataset yaxisData, String dataNodePath) throws NexusException  {
		if (data == null)
			return;
		String[] nodes = dataNodePath.split("/");
		String nodePath = "";
		for (int i = 0; i < nodes.length; i++) {
			String node = nodes[i];
			if (!node.equals("")) {
				nodePath = nodePath.concat("/" + node);
				GroupNode group = nexus.getGroup(nodePath, true);
				if (i < nodes.length - 1) {
					nexus.addAttribute(group, TreeFactory.createAttribute(NexusConstants.NXCLASS, NexusConstants.ENTRY));
				} else {
					nexus.addAttribute(group, TreeFactory.createAttribute(NexusConstants.NXCLASS, NexusConstants.DATA));
					data.setName(NexusConstants.DATA_DATA);
					nexus.createData(group, data);
					if (yaxisData != null) {
						nexus.createData(group, yaxisData);
					}
					if (xaxisData != null) {
						nexus.createData(group, xaxisData);
					}
				}
			}
		}
	}
}
