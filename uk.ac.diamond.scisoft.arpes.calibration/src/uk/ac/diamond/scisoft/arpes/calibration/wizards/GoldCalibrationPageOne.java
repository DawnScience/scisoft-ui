/*-
 * Copyright (c) 2016 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package uk.ac.diamond.scisoft.arpes.calibration.wizards;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.dawnsci.plotting.actions.ActionBarWrapper;
import org.dawnsci.plotting.tools.profile.PerimeterBoxProfileTool;
import org.eclipse.dawnsci.analysis.api.message.DataMessageComponent;
import org.eclipse.dawnsci.analysis.api.roi.IROI;
import org.eclipse.dawnsci.analysis.dataset.roi.PerimeterBoxROI;
import org.eclipse.dawnsci.analysis.dataset.roi.RectangularROI;
import org.eclipse.dawnsci.plotting.api.IPlottingSystem;
import org.eclipse.dawnsci.plotting.api.PlotType;
import org.eclipse.dawnsci.plotting.api.PlottingFactory;
import org.eclipse.dawnsci.plotting.api.region.IROIListener;
import org.eclipse.dawnsci.plotting.api.region.IRegion;
import org.eclipse.dawnsci.plotting.api.region.IRegion.RegionType;
import org.eclipse.dawnsci.plotting.api.region.ROIEvent;
import org.eclipse.dawnsci.plotting.api.tool.IToolPageSystem;
import org.eclipse.dawnsci.plotting.api.tool.ToolPageFactory;
import org.eclipse.january.dataset.Dataset;
import org.eclipse.january.dataset.DatasetFactory;
import org.eclipse.january.dataset.DatasetUtils;
import org.eclipse.january.dataset.IDataset;
import org.eclipse.january.dataset.IntegerDataset;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;

import uk.ac.diamond.scisoft.analysis.roi.ROIProfile;
import uk.ac.diamond.scisoft.arpes.calibration.utils.ARPESCalibrationConstants;

public class GoldCalibrationPageOne extends CalibrationWizardPage {
	private IPlottingSystem<Composite> system;
	private DataMessageComponent calibrationData;
	private PerimeterBoxProfileTool perimeterProfile;
	private IRegion perimeterRegion;

	public GoldCalibrationPageOne(DataMessageComponent calibrationData) {
		super("Select Region Page");
		setTitle("Select Region Page");
		setDescription("Please select the region you wish to perform the fit within");
		this.calibrationData = calibrationData;
	}

	@Override
	public void createControl(Composite parent) {
		SashForm sashForm = new SashForm(parent, SWT.HORIZONTAL);
		sashForm.setBackgroundMode(SWT.INHERIT_FORCE);
//		sashForm.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		
		// create plotting system
		Composite sysComp = new Composite(sashForm, SWT.NONE);
		sysComp.setLayout(new GridLayout());
		try {
			system = PlottingFactory.createPlottingSystem();
		} catch (Exception e1) {
			e1.printStackTrace();
		}
		ActionBarWrapper actionBarWrapper = ActionBarWrapper.createActionBars(sysComp, null);
		system.createPlotPart(sysComp, "Page One System", actionBarWrapper, PlotType.IMAGE, null);
		system.getPlotComposite().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		
		IDataset data = (IDataset) calibrationData.getList(ARPESCalibrationConstants.DATANAME);
		IDataset xAxis = (IDataset) calibrationData.getList(ARPESCalibrationConstants.XAXIS_DATANAME);
		IDataset yAxis = (IDataset) calibrationData.getList(ARPESCalibrationConstants.YAXIS_DATANAME);
		List<IDataset> axes = new ArrayList<IDataset>();
		axes.add(xAxis);
		axes.add(yAxis);
		system.createPlot2D(data, axes, null);
		try {
			perimeterRegion = system.createRegion("Perimeter Profile", RegionType.PERIMETERBOX);
			perimeterRegion.setROI(new PerimeterBoxROI(100, 100, 150, 150, 0));
			perimeterRegion.setUserRegion(true);
			perimeterRegion.addROIListener(roiListener);
			system.addRegion(perimeterRegion);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		// create perimeter tool
		Composite toolComp = new Composite(sashForm, SWT.NONE);
		toolComp.setLayout(new GridLayout(1, false));
//		toolComp.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		IToolPageSystem tps = (IToolPageSystem)system.getAdapter(IToolPageSystem.class);
		try {
			perimeterProfile = (PerimeterBoxProfileTool)ToolPageFactory.getToolPage("org.dawb.workbench.plotting.tools.perimeterBoxProfileTool");
		} catch (Exception e1) {
			e1.printStackTrace();
		}
		perimeterProfile.setToolSystem(tps);
		perimeterProfile.setPlottingSystem(system);
		perimeterProfile.setTitle(getName()+"_profile1");
		perimeterProfile.setToolId(String.valueOf(perimeterProfile.hashCode()));
		perimeterProfile.createControl(toolComp);
		perimeterProfile.getControl().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
//		perimeterProfile.setPlotAveraging(true);
		perimeterProfile.activate();

		setControl(sashForm);
		setPageComplete(false);
		getShell().pack();
	}

	private IROIListener roiListener = new IROIListener.Stub() {
		@Override
		public void roiChanged(ROIEvent event) {
			IROI roi = event.getROI();
			updateModel(roi);
		}
	};

	@Override
	public void dispose() {
		if (perimeterRegion != null)
			perimeterRegion.removeROIListener(roiListener);
	}

	@Override
	public void setVisible(boolean visible) {
		if (visible) {
		}
		super.setVisible(visible);
	}

	private void updateModel(IROI roi) {
		calibrationData.addROI(ARPESCalibrationConstants.REGION_NAME, roi);
		IDataset data = (IDataset) calibrationData.getList(ARPESCalibrationConstants.DATANAME);
		Dataset id = DatasetUtils.convertToDataset(data);
		IDataset averageData = ROIProfile.boxMean(id, null, (RectangularROI) roi, true)[0];
		averageData.setName("Intensity");
		calibrationData.addList(ARPESCalibrationConstants.AVERAGE_DATANAME, averageData);

		IDataset regionDataset = getRegionData((RectangularROI)roi, calibrationData);
		calibrationData.addList(ARPESCalibrationConstants.REGION_DATANAME, regionDataset);
		IDataset xaxis = (IDataset) calibrationData.getList(ARPESCalibrationConstants.XAXIS_DATANAME);
		IDataset yaxis = (IDataset) calibrationData.getList(ARPESCalibrationConstants.YAXIS_DATANAME);
		List<IDataset> dataAxes = Arrays.asList(new IDataset[] { xaxis, yaxis });
		List<IDataset> slicedAxes = getSliceAxes(dataAxes, (RectangularROI)roi);
		IDataset xaxisData = slicedAxes.get(0);
		IDataset yaxisData = slicedAxes.get(1);
		calibrationData.addList(ARPESCalibrationConstants.ENERGY_AXIS, xaxisData);
		calibrationData.addList(ARPESCalibrationConstants.ANGLE_AXIS, yaxisData);
		setPageComplete(true);
	}

	public static List<IDataset> getSliceAxes(List<IDataset> dataAxes, RectangularROI bounds) {
		int yInc = bounds.getPoint()[1] < bounds.getEndPoint()[1] ? 1 : -1;
		int xInc = bounds.getPoint()[0] < bounds.getEndPoint()[0] ? 1 : -1;
		Dataset yLabels = null;
		Dataset xLabels = null;
		if (dataAxes != null && dataAxes.size() > 0) {
			Dataset xl = DatasetUtils.convertToDataset(dataAxes.get(0));
			if (xl!=null) xLabels = getLabelsFromLabels(xl, bounds, 0);
			Dataset yl = DatasetUtils.convertToDataset(dataAxes.get(1));
			if (yl!=null) yLabels = getLabelsFromLabels(yl, bounds, 1);
		}

		if (yLabels==null) yLabels = DatasetFactory.createRange(IntegerDataset.class, bounds.getPoint()[1], bounds.getEndPoint()[1], yInc);
		yLabels.setName("angle");
		if (xLabels==null) xLabels = DatasetFactory.createRange(IntegerDataset.class, bounds.getPoint()[0], bounds.getEndPoint()[0], xInc);
		xLabels.setName("energy");
		List<IDataset> axes = new ArrayList<IDataset>();
		axes.add(xLabels);
		axes.add(yLabels);
		return axes;
	}

	private static Dataset getLabelsFromLabels(Dataset xl, RectangularROI bounds, int axisIndex) {
		try {
			int fromIndex = (int)bounds.getPoint()[axisIndex];
			int toIndex   = (int)bounds.getEndPoint()[axisIndex];
			int step      = toIndex>fromIndex ? 1 : -1;
			final Dataset slice = xl.getSlice(new int[]{fromIndex}, new int[]{toIndex}, new int[]{step});
			return slice;
		} catch (Exception ne) {
			return null;
		}
	}

	/**
	 * Returns the sliced region data given a ROI and the calibrationData (where
	 * the data is)
	 * 
	 * @param roi
	 * @param calibrationData
	 * @return
	 */
	public static IDataset getRegionData(RectangularROI roi, DataMessageComponent calibrationData) {
		final int yInc = roi.getPoint()[1] < roi.getEndPoint()[1] ? 1 : -1;
		final int xInc = roi.getPoint()[0] < roi.getEndPoint()[0] ? 1 : -1;

		int yStart = (int) roi.getPoint()[1];
		int xStart = (int) roi.getPoint()[0];
		int yStop = (int) roi.getEndPoint()[1];
		int xStop = (int) roi.getEndPoint()[0];
		
		IDataset data = (IDataset)calibrationData.getList(ARPESCalibrationConstants.DATANAME);
		IDataset dataRegion = data.clone();

		dataRegion = dataRegion.getSlice(new int[] { yStart, xStart }, new int[] { yStop, xStop },
				new int[] { yInc, xInc });

		dataRegion.setName(dataRegion.getName() + "_region");
		return dataRegion;
	}

	@Override
	public boolean runProcess() throws InterruptedException {
		System.out.println("Page 1");
		return true;
	}

	@Override
	public int getPageNumber() {
		return 1;
	}

}