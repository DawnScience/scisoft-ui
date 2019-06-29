/*-
 * Copyright (c) 2016 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.arpes.calibration.functions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.eclipse.dawnsci.analysis.api.fitting.functions.IFunction;
import org.eclipse.dawnsci.analysis.api.message.DataMessageComponent;
import org.eclipse.dawnsci.plotting.api.IPlottingSystem;
import org.eclipse.january.IMonitor;
import org.eclipse.january.dataset.Dataset;
import org.eclipse.january.dataset.DatasetFactory;
import org.eclipse.january.dataset.DatasetUtils;
import org.eclipse.january.dataset.DoubleDataset;
import org.eclipse.january.dataset.IDataset;
import org.eclipse.january.dataset.IndexIterator;
import org.eclipse.january.dataset.Maths;
import org.eclipse.january.dataset.Slice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.fitting.Fitter;
import uk.ac.diamond.scisoft.analysis.fitting.functions.AFunction;
import uk.ac.diamond.scisoft.analysis.fitting.functions.FermiGauss;
import uk.ac.diamond.scisoft.arpes.calibration.utils.ARPESCalibrationConstants;

public class FermiGaussianFitter {

	private static final Logger logger = LoggerFactory.getLogger(FermiGaussianFitter.class);

	private static final Integer FIT_DIRECTION = 1;

	private DataMessageComponent calibrationData;
	
	private boolean isFinished = false;

	public FermiGaussianFitter(DataMessageComponent calibrationData) {
		this.calibrationData = calibrationData;
	}

	public void fit(IMonitor monitor) {
		Integer fitDim = FIT_DIRECTION;

		Dataset dataDS = DatasetFactory.createFromObject(calibrationData.getList(ARPESCalibrationConstants.REGION_DATANAME));

		int[] shape = dataDS.getShape();
		IFunction fitFunction = calibrationData.getFunction(ARPESCalibrationConstants.FUNCTION_NAME);
		Dataset xAxisDS = DatasetFactory.createFromObject(calibrationData.getList(ARPESCalibrationConstants.ENERGY_AXIS));
		if (xAxisDS == null)
			xAxisDS = DatasetFactory.createRange(shape[fitDim], 0, -1.);

		Dataset anglesAxisDS = DatasetFactory.createFromObject(calibrationData.getList(ARPESCalibrationConstants.ANGLE_AXIS));
		if (anglesAxisDS == null)
			anglesAxisDS = DatasetFactory.createRange(shape[Math.abs(fitDim - 1)], 0, -1.);

		anglesAxisDS.setName("Angles");
//		calibrationData.addList(GoldCalibrationWizard.XAXIS_NAME, anglesAxisDS);

		ArrayList<Slice> slices = new ArrayList<Slice>();
		for (int i = 0; i < shape.length; i++) {
			if (i == fitDim) {
				slices.add(new Slice(0, shape[i], 1));
			} else {
				slices.add(new Slice(0, 1, 1));
			}
		}

		ArrayList<Dataset> parametersDS = new ArrayList<Dataset>(fitFunction.getNoOfParameters());

		int[] lshape = shape.clone();
		lshape[fitDim] = 1;

		for (int i = 0; i < fitFunction.getNoOfParameters(); i++) {
			DoubleDataset parameterDS = (DoubleDataset) DatasetFactory.zeros(lshape);
			parameterDS.fill(Double.NaN);
			parameterDS.squeeze();
			parameterDS.setName(fitFunction.getParameter(i).getName());
			parametersDS.add(parameterDS);
		}

		Dataset functionsDS = DatasetFactory.zeros(shape);
		Dataset residualDS = DatasetFactory.zeros(lshape);
		residualDS.squeeze();

		int[] starts = shape.clone();
		starts[fitDim] = 1;
		DoubleDataset ind = DatasetFactory.ones(starts);
		IndexIterator iter = ind.getIterator(true);

		int maxthreads = Runtime.getRuntime().availableProcessors();

		ExecutorService executorService = new ThreadPoolExecutor(maxthreads, maxthreads, 1, TimeUnit.MINUTES,
				new ArrayBlockingQueue<Runnable>(10000, true), new ThreadPoolExecutor.CallerRunsPolicy());

		int[] pos = iter.getPos();
		while (iter.hasNext()) {
			logger.debug(Arrays.toString(pos));
			int[] start = pos.clone();
			int[] stop = start.clone();
			for (int i = 0; i < stop.length; i++) {
				stop[i] = stop[i] + 1;
			}
			stop[fitDim] = shape[fitDim];
			Dataset slice = dataDS.getSlice(start, stop, null);
			slice.squeeze();

			FermiGauss localFitFunction = new FermiGauss(fitFunction.getParameters());
			int dSlength = shape.length;
			executorService.submit(new Worker(localFitFunction, xAxisDS, anglesAxisDS, slice, dSlength, start, stop,
					fitDim, parametersDS, functionsDS, residualDS, monitor));
		}
		shutDownExecutor(executorService, monitor);

		// Now have a look at the residuals, and see if any are particularly bad
		// (or zero)
		double resMean = (Double) residualDS.mean();
		double resStd = (Double) residualDS.stdDeviation();
		iter.reset();

		executorService = new ThreadPoolExecutor(maxthreads, maxthreads, 1, TimeUnit.MINUTES,
				new ArrayBlockingQueue<Runnable>(10000, true), new ThreadPoolExecutor.CallerRunsPolicy());

		while (iter.hasNext()) {
			double value = residualDS.getDouble(pos[0]);
			double disp = Math.abs(value - resMean);
			if (disp > resStd * 3 || value <= 0) {
				logger.debug(Arrays.toString(pos));
				int[] start = pos.clone();
				int[] stop = start.clone();
				for (int i = 0; i < stop.length; i++) {
					stop[i] = stop[i] + 1;
				}
				stop[fitDim] = shape[fitDim];
				Dataset slice = dataDS.getSlice(start, stop, null);
				slice.squeeze();

				FermiGauss localFitFunction = new FermiGauss(fitFunction.getParameters());
				int dSlength = shape.length;
				executorService.submit(new Worker(localFitFunction, xAxisDS, anglesAxisDS, slice, dSlength, start, stop,
						fitDim, parametersDS, functionsDS, residualDS, monitor));
			}
		}
		shutDownExecutor(executorService, monitor);

		calibrationData.addList(ARPESCalibrationConstants.FIT_IMAGE, functionsDS);
		calibrationData.addList(ARPESCalibrationConstants.FIT_RESIDUALS, residualDS);
		for (int i = 0; i < fitFunction.getNoOfParameters(); i++) {
			calibrationData.addList(ARPESCalibrationConstants.FIT_PARAMETER + i, parametersDS.get(i));
		}
	}

	private void shutDownExecutor(ExecutorService executorService, IMonitor monitor) {
		try {
			logger.debug("attempt to shutdown executor");
			executorService.shutdown();
			while (!executorService.awaitTermination(200, TimeUnit.MILLISECONDS)) {
				// executorService.awaitTermination(10, TimeUnit.HOURS);
				if (monitor != null && monitor.isCancelled())
					break;
			}
		} catch (InterruptedException e) {
			logger.debug("tasks interrupted:" + e.getMessage());
		} finally {
			if (!executorService.isTerminated()) {
				logger.debug("cancel non-finished tasks");
			}
			executorService.shutdownNow();
			logger.debug("shutdown finished");
			isFinished = true;
		}
	}

	public boolean isFinished() {
		return isFinished;
	}

	private class Worker implements Runnable {

		private AFunction fitFunction;
		private Dataset xAxisDS;
		private Dataset anglesAxisDS;
		private Dataset slice;
		private int DSlength;
		private int[] start;
		private int[] stop;
		private int fitDim;
		private ArrayList<Dataset> parametersDS;
		private Dataset functionsDS;
		private Dataset residualsDS;
		private IMonitor monitor;

		public Worker(AFunction fitFunction, Dataset xAxisDS, Dataset anglesAxisDS,
				Dataset slice, int dSlength, int[] start, int[] stop,
				int fitDim, ArrayList<Dataset> parametersDS,
				Dataset functionsDS, Dataset residualsDS, IMonitor monitor) {
			super();
			this.fitFunction = fitFunction;
			this.xAxisDS = xAxisDS;
			this.anglesAxisDS = anglesAxisDS;
			this.slice = slice;
			DSlength = dSlength;
			this.start = start;
			this.stop = stop;
			this.fitDim = fitDim;
			this.parametersDS = parametersDS;
			this.functionsDS = functionsDS;
			this.residualsDS = residualsDS;
			this.monitor = monitor;
		}

		@Override
		public void run() {
			try {
				AFunction fitResult = null;
				try {
					fitResult = FitGaussianConvFermi(xAxisDS, slice, fitFunction, monitor);
				} catch (InterruptedException e) {
					throw e;
				} catch (Exception e) {
					logger.error("Error instanciating  FitGaussianConFermi: " + e.getMessage());
				}
				int[] position = new int[DSlength - 1];
				int count = 0;
				for (int i = 0; i < DSlength; i++) {
					if (i != fitDim) {
						position[count] = start[i];
						count++;
					}
				}

				for (int p = 0; p < fitResult.getNoOfParameters(); p++) {
					parametersDS.get(p).set(fitResult.getParameter(p).getValue(), position);
				}

				// mu
				try {
					plot(ARPESCalibrationConstants.MU_SYSTEM, anglesAxisDS, parametersDS.get(0));
					calibrationData.addList(ARPESCalibrationConstants.MU_DATA, parametersDS.get(0));
				} catch (Exception e) {
					logger.debug("Something happend during the Mu update process", e);
				}
				// temperature
				calibrationData.addList(ARPESCalibrationConstants.TEMPERATURE, parametersDS.get(1));
				// background slope
				calibrationData.addList(ARPESCalibrationConstants.BACKGROUND_SLOPE, parametersDS.get(2));
				// fermi edge step height
				calibrationData.addList(ARPESCalibrationConstants.FERMI_EDGE_STEP_HEIGHT, parametersDS.get(3));
				// background
				calibrationData.addList(ARPESCalibrationConstants.BACKGROUND, parametersDS.get(4));
				// fwhm
				try {
					plot(ARPESCalibrationConstants.RESOLUTION_SYSTEM, anglesAxisDS, parametersDS.get(5));
					calibrationData.addList(ARPESCalibrationConstants.FWHM_DATA, parametersDS.get(5));
				} catch (Exception e) {
					logger.debug("Something happend during the resolution update process", e);
				}

				DoubleDataset resultFunctionDS = fitResult.calculateValues(xAxisDS);
				functionsDS.setSlice(resultFunctionDS, start, stop, null);

				// residuals
				Dataset residual = Maths.subtract(slice, resultFunctionDS);
				residual.ipower(2);
				residualsDS.set(residual.sum(), position);
				try {
					IDataset residuals = DatasetUtils.cast(residualsDS, residualsDS.getDType());
					residuals.setName("residuals");
					plot(ARPESCalibrationConstants.RESIDUALS_SYSTEM, anglesAxisDS, residuals);
					calibrationData.addList(ARPESCalibrationConstants.RESIDUALS_DATA, residuals);
				} catch (Exception e) {
					logger.debug("Something happend during the residuals update process", e);
				}
				// fitted data/image
				calibrationData.addList(ARPESCalibrationConstants.FITTED, functionsDS);

				if (monitor != null && monitor.isCancelled()) {
					throw new InterruptedException("Thread interrupted");
				}
			} catch (InterruptedException e) {
				// interrupt thread
				Thread.currentThread().interrupt();
			}
		}
	}

	private AFunction FitGaussianConvFermi(final Dataset xAxis,
			final Dataset values, final AFunction fitFunction, IMonitor monitor) throws Exception, InterruptedException {

		if (!(fitFunction instanceof FermiGauss)) {
			throw new IllegalArgumentException(
					"Input function must be of type FermiGauss");
		}

		final double temperature = fitFunction.getParameterValue(1);

		FermiGauss fittedFunction = new FermiGauss(fitFunction.getParameters()); // "mu", "temperature", "BG_slope", "FE_step_height", "Constant", "FWHM"

		double lowerLimitForFWHM = fittedFunction.getParameter(5).getLowerLimit();
		fittedFunction.getParameter(5).setLowerLimit(0.0);
		fittedFunction.getParameter(5).setValue(0.0);
		
		fitFermiGauss(xAxis, values, fittedFunction, false, false, false, false, false, true);
		
		if (monitor != null && monitor.isCancelled()) {
			throw new InterruptedException("Thread interrupted");
		}

		int count = 0;
		while (functionsSimilarIgnoreFWHM(fittedFunction,(FermiGauss)fitFunction, 0.0) && count < 5) {
			logger.debug("Function not fitted, trying again :" + count);
			count++;
			
			fitFermiGauss(xAxis, values, fittedFunction, false, false, false, false, false, true);
			
			if (monitor != null && monitor.isCancelled()) {
				throw new InterruptedException("Thread interrupted");
			}
		}
		if (count >= 5) {
			logger.debug("Fitting Failed");
		}
		
//		plotFunction(fittedFunction, xAxis, values);
		
		// now reset the minimum value for the FWHM
		fittedFunction.approximateFWHM(temperature);
		
		//plotFunction(fittedFunction, xAxis, values);
		
		// Now fit the system quickly using several assumptions

		fittedFunction.getParameter(5).setLowerLimit(lowerLimitForFWHM);
		
		fitFermiGauss(xAxis, values, fittedFunction, false, true, true, true, true, false);

		if (monitor != null && monitor.isCancelled()) {
			throw new InterruptedException("Thread interrupted");
		}

		fitFermiGauss(xAxis, values, fittedFunction, false, true, false, false, false, false);
		
		if (monitor != null && monitor.isCancelled()) {
			throw new InterruptedException("Thread interrupted");
		}
		values.setName("Final");
		plotFunction(fittedFunction, xAxis, values);
		logger.debug("temperature is {}",fittedFunction.getParameters()[1].getValue());
		return fittedFunction;
	}

	private void fitFermiGauss(final Dataset xAxis, final Dataset values, FermiGauss fittedFunction, 
			boolean fixMu, boolean fixTemperature, boolean fixBG_slope, boolean fixFE_step_height, 
			boolean fixConstant, boolean fixFWHM) {
		fittedFunction.getParameter(0).setFixed(fixMu);
		fittedFunction.getParameter(1).setFixed(fixTemperature);
		fittedFunction.getParameter(2).setFixed(fixBG_slope);
		fittedFunction.getParameter(3).setFixed(fixFE_step_height);
		fittedFunction.getParameter(4).setFixed(fixConstant);
		fittedFunction.getParameter(5).setFixed(fixFWHM);

		values.setName("Setup");
//		plotFunction(fittedFunction, xAxis, values);

		// fit with a fixed fwhm letting the temperature vary
		try {
			Fitter.ApacheNelderMeadFit(new Dataset[] {xAxis}, values, fittedFunction);
			values.setName("Fixed temperature fit");
//			plotFunction(fittedFunction, xAxis, values);
		} catch (Exception e) {
			logger.debug("Exception occured while running ApacheNelderMeadFit with fixed fwhm: " + e.getMessage());
		}
	}

	private boolean functionsSimilarIgnoreFWHM(FermiGauss initialFit,
			FermiGauss fitFunction, double tollerence) {
		for (int i = 0; i < 5; i++) {
			if (Math.abs(fitFunction.getParameterValue(i)-initialFit.getParameterValue(i)) <= tollerence) return true;
			if (Math.abs(fitFunction.getParameter(i).getLowerLimit()-initialFit.getParameterValue(i)) <= tollerence) return true;
			if (Math.abs(fitFunction.getParameter(i).getUpperLimit()-initialFit.getParameterValue(i)) <= tollerence) return true;
		}
		return false;
	}

	private void plotFunction(AFunction fitFunction, IDataset xAxis, IDataset values) {
		try {
			Dataset fermiDS = fitFunction.calculateValues(xAxis);
			String plotName = ARPESCalibrationConstants.FIT_UPDATE_SYSTEM;
			IPlottingSystem<?> fitUpdateSystem = (IPlottingSystem<?>) calibrationData.getUserObject(plotName);
			if (fitUpdateSystem != null) {
				fitUpdateSystem.clear();
				fitUpdateSystem.updatePlot1D(xAxis, Arrays.asList(new IDataset[] { fermiDS, values }), null);
			}
		} catch (Exception e) {
			// Not an important issue, as its just for display, and doesn't
			// affect the result.
		}
	}

	private void plot(String plotName, IDataset xdata, IDataset data) {
		IPlottingSystem<?> system = (IPlottingSystem<?>) calibrationData.getUserObject(plotName);
		if (system != null) {
			system.updatePlot1D(xdata, Arrays.asList(new IDataset[] { data }), null);
			system.repaint();
		}
	}
}
