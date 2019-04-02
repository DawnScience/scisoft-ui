/*-
 * Copyright (c) 2016 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package uk.ac.diamond.scisoft.arpes.calibration.wizards;

import java.util.Map;
import java.util.Set;
import org.dawnsci.plotting.actions.ActionBarWrapper;
import org.dawnsci.plotting.tools.fitting.FunctionFittingTool;
import org.eclipse.dawnsci.analysis.api.fitting.functions.IFunction;
import org.eclipse.dawnsci.analysis.api.message.DataMessageComponent;
import org.eclipse.dawnsci.analysis.dataset.roi.RectangularROI;
import org.eclipse.dawnsci.plotting.api.IPlottingSystem;
import org.eclipse.dawnsci.plotting.api.PlotType;
import org.eclipse.dawnsci.plotting.api.PlottingFactory;
import org.eclipse.dawnsci.plotting.api.region.IRegion;
import org.eclipse.dawnsci.plotting.api.tool.IToolPageSystem;
import org.eclipse.dawnsci.plotting.api.tool.ToolPageFactory;
import org.eclipse.january.dataset.IDataset;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;

public abstract class FunctionFittingCalibrationWizardPage extends CalibrationWizardPage {
	protected DataMessageComponent calibrationData;
	protected IPlottingSystem<Composite> system;
	protected FunctionFittingTool functionFittingTool;
	private String title;

	public FunctionFittingCalibrationWizardPage(DataMessageComponent calibrationData, String title, String description) {
		super(title);
		this.title = title;
		setTitle(title);
		setDescription(description);
		this.calibrationData = calibrationData;
	}

	@Override
	public void createControl(Composite parent) {
		SashForm sashForm = new SashForm(parent, SWT.HORIZONTAL);
		sashForm.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		
		// create plotting system
		Composite sysComp = new Composite(sashForm, SWT.NONE);
		sysComp.setLayout(new GridLayout());
		try {
			system = PlottingFactory.createPlottingSystem();
		} catch (Exception e1) {
			e1.printStackTrace();
		}
		ActionBarWrapper actionBarWrapper = ActionBarWrapper.createActionBars(sysComp, null);
		system.createPlotPart(sysComp, title, actionBarWrapper, PlotType.XY, null);
		system.getPlotComposite().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		// create Function fitting tool
		Composite toolComp = new Composite(sashForm, SWT.NONE);
		toolComp.setLayout(new GridLayout());

		IToolPageSystem tps = (IToolPageSystem) system.getAdapter(IToolPageSystem.class);
		try {
			functionFittingTool = (FunctionFittingTool) ToolPageFactory
					.getToolPage("org.dawb.workbench.plotting.function_fitting_tool");
		} catch (Exception e1) {
			e1.printStackTrace();
		}
//		ActionBarWrapper toolActionBarWrapper = ActionBarWrapper.createActionBars(toolComp, null);
//		functionFittingTool.setActionBars(toolActionBarWrapper);
		functionFittingTool.setToolSystem(tps);
		functionFittingTool.setPlottingSystem(system);
		functionFittingTool.setTitle(getName() + "_fit1");
		functionFittingTool.setToolId(String.valueOf(functionFittingTool.hashCode()));
		functionFittingTool.createControl(toolComp);
		functionFittingTool.getControl().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		functionFittingTool.activate();
		functionFittingTool.addFunctionUpdatedListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				Map<String, IFunction> functions = functionFittingTool.getFunctions();
				Set<String> keySet = functions.keySet();
				String functionName = "";
				if (keySet.iterator().hasNext())
					functionName = keySet.iterator().next();
				IFunction function = functions.get(functionName);
				calibrationData.addFunction(getFunctionName(), function);
				saveFittedMuData(function);
				setPageComplete(true);
			}
		});
		setControl(sashForm);
		setPageComplete(false);
		getShell().pack();
	}

	/**
	 * 
	 * @return the function name to save
	 */
	public abstract String getFunctionName();

	/**
	 * To override if necessary
	 * @param function
	 */
	protected void saveFittedMuData(IFunction function) {
		
	}

	/**
	 * Set the correct length for Fitregion given the Xaxis data
	 * @param xaxisData
	 */
	protected void setFitRegion(IDataset xaxisData) {
		int[] minPos = xaxisData.minPos();
		double min = xaxisData.getDouble(minPos);
		int[] maxPos = xaxisData.maxPos();
		double max = xaxisData.getDouble(maxPos);
		if (system.getRegions().iterator().hasNext()) {
			IRegion fitRegion = system.getRegions().iterator().next();
			RectangularROI roi = (RectangularROI) fitRegion.getROI();
			roi.setPoint(new double[] {min, 0} );
			roi.setLengths(new double[] {max - min, 0} );
			fitRegion.setROI(roi);
		}
	}
}
