/*-
 * Copyright (c) 2016 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package uk.ac.diamond.scisoft.arpes.calibration.wizards;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.dawnsci.analysis.api.fitting.functions.IFunction;
import org.eclipse.dawnsci.analysis.api.message.DataMessageComponent;
import org.eclipse.january.dataset.Dataset;
import org.eclipse.january.dataset.DatasetUtils;
import org.eclipse.january.dataset.IDataset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.arpes.calibration.functions.PrepareFermiGaussianFunction;
import uk.ac.diamond.scisoft.arpes.calibration.utils.ARPESCalibrationConstants;

public class GoldCalibrationPageTwo extends FunctionFittingCalibrationWizardPage {
	
	private static final Logger logger = LoggerFactory.getLogger(GoldCalibrationPageTwo.class);	

	public GoldCalibrationPageTwo(DataMessageComponent calibrationData) {
		super(calibrationData, "Fermi Fitting", "Set up the Fermi function fitting. Press the \"Update All\" button "
				+ "to update the fitted parameters. This will save the Fitted Function parameters and use them "
				+ "in the Convolution process of the next page.");
	}

	@Override
	public void setVisible(boolean visible) {
		if (visible) {
			setFunction();
			setMean();
			// when page is visible, update plot with correct data, axis and region
			IDataset xaxisData = (IDataset)calibrationData.getList(ARPESCalibrationConstants.ENERGY_AXIS);
			IDataset data = (IDataset)calibrationData.getList(ARPESCalibrationConstants.MEAN_DATANAME);
			if (system.getTraces().isEmpty())
				setFitRegion(xaxisData);
			system.updatePlot1D(xaxisData,  Arrays.asList(new IDataset[] { data }), null);
		}
		super.setVisible(visible);
	}

	/**
	 * Set mean dataset based on regionDataset
	 */
	private void setMean() {
		IDataset iregionDataset = (IDataset)calibrationData.getList(ARPESCalibrationConstants.REGION_DATANAME);
		Dataset regionDataset = DatasetUtils.convertToDataset(iregionDataset);
		Dataset meanDataset = regionDataset.mean(0);
		meanDataset.setName(ARPESCalibrationConstants.MEAN_DATANAME);
		calibrationData.addList(ARPESCalibrationConstants.MEAN_DATANAME, meanDataset);
	}

	private void setFunction() {
		Map<String, IFunction> functions = new HashMap<String, IFunction>();
		PrepareFermiGaussianFunction pFunc = new PrepareFermiGaussianFunction(calibrationData);
		IFunction fermiGaussianFunc = pFunc.getPreparedFunction();
		functions.put("_initial_", fermiGaussianFunc);

		functionFittingTool.setFunctions(functions);
	}

	@Override
	public boolean runProcess() throws InterruptedException {
		logger.debug("Page 2");
		getShell().redraw();
		return true;
	}

	@Override
	public int getPageNumber() {
		return 2;
	}

	@Override
	public String getFunctionName() {
		return ARPESCalibrationConstants.FUNCTION_NAME;
	}
}