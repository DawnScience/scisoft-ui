package uk.ac.diamond.scisoft.arpes.calibration.wizards;

import java.lang.reflect.InvocationTargetException;

import org.dawb.common.ui.monitor.ProgressMonitorWrapper;
import org.dawb.common.ui.widgets.ActionBarWrapper;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.dawnsci.analysis.api.message.DataMessageComponent;
import org.eclipse.dawnsci.plotting.api.IPlottingSystem;
import org.eclipse.dawnsci.plotting.api.PlotType;
import org.eclipse.dawnsci.plotting.api.PlottingFactory;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;

import uk.ac.diamond.scisoft.arpes.calibration.functions.FermiGaussianFitter;

public class GoldCalibrationPageThree extends CalibrationWizardPage {
	private DataMessageComponent calibrationData;
	private IPlottingSystem<Composite> fitUpdateSystem;
	private IPlottingSystem<Composite> resolutionSystem;
	private IPlottingSystem<Composite> muSystem;
	private IPlottingSystem<Composite> residualsSystem;
	private FermiGaussianFitter fitter;
	private IRunnableWithProgress fitterWithProgress;

	public GoldCalibrationPageThree(DataMessageComponent calibrationData) {
		super("Fit Gaussian Convoluted Fermi Page");
		setTitle("Fit Gaussian Convoluted Fermi Page");
		setDescription("Fit Gaussian Convoluted Fermi Page");
		this.calibrationData = calibrationData;
		fitter = new FermiGaussianFitter(calibrationData);
		fitterWithProgress = new IRunnableWithProgress() {
			@Override
			public void run(IProgressMonitor monitor) {
				monitor.beginTask("Running Fermi-Gaussian convoluted...", -1);
				fitter.fit(new ProgressMonitorWrapper(monitor));
			}
		};
	}

	@Override
	public void createControl(Composite parent) {
		SashForm sashForm = new SashForm(parent, SWT.HORIZONTAL);
		sashForm.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		
		SashForm sashForm2 = new SashForm(sashForm, SWT.VERTICAL);
		sashForm2.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		SashForm sashForm3 = new SashForm(sashForm, SWT.VERTICAL);
		sashForm3.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		// create fitupdate plotting system
		Composite sysComp = new Composite(sashForm2, SWT.NONE);
		sysComp.setLayout(new GridLayout());
		try {
			fitUpdateSystem = PlottingFactory.createPlottingSystem();
		} catch (Exception e1) {
			e1.printStackTrace();
		}
		ActionBarWrapper actionBarWrapper = ActionBarWrapper.createActionBars(sysComp, null);
		fitUpdateSystem.createPlotPart(sysComp, GoldCalibrationWizard.FIT_UPDATE_SYSTEM, actionBarWrapper, PlotType.XY, null);
		fitUpdateSystem.getPlotComposite().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		fitUpdateSystem.setTitle("FitUpdate");
		calibrationData.addUserObject(GoldCalibrationWizard.FIT_UPDATE_SYSTEM, fitUpdateSystem);

		// create Resolution plotting system
		sysComp = new Composite(sashForm2, SWT.NONE);
		sysComp.setLayout(new GridLayout());
		try {
			resolutionSystem = PlottingFactory.createPlottingSystem();
		} catch (Exception e1) {
			e1.printStackTrace();
		}
		actionBarWrapper = ActionBarWrapper.createActionBars(sysComp, null);
		resolutionSystem.createPlotPart(sysComp, GoldCalibrationWizard.RESOLUTION_SYSTEM, actionBarWrapper, PlotType.XY, null);
		resolutionSystem.getPlotComposite().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		resolutionSystem.setTitle("Resolution");
		calibrationData.addUserObject(GoldCalibrationWizard.RESOLUTION_SYSTEM, resolutionSystem);

		// create fitupdate plotting system
		sysComp = new Composite(sashForm3, SWT.NONE);
		sysComp.setLayout(new GridLayout());
		try {
			muSystem = PlottingFactory.createPlottingSystem();
		} catch (Exception e1) {
			e1.printStackTrace();
		}
		actionBarWrapper = ActionBarWrapper.createActionBars(sysComp, null);
		muSystem.createPlotPart(sysComp, GoldCalibrationWizard.MU_SYSTEM, actionBarWrapper, PlotType.XY, null);
		muSystem.getPlotComposite().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		muSystem.setTitle("Mu");
		calibrationData.addUserObject(GoldCalibrationWizard.MU_SYSTEM, muSystem);

		// create Resolution plotting system
		sysComp = new Composite(sashForm3, SWT.NONE);
		sysComp.setLayout(new GridLayout());
		try {
			residualsSystem = PlottingFactory.createPlottingSystem();
		} catch (Exception e1) {
			e1.printStackTrace();
		}
		actionBarWrapper = ActionBarWrapper.createActionBars(sysComp, null);
		residualsSystem.createPlotPart(sysComp, GoldCalibrationWizard.RESIDUALS_SYSTEM, actionBarWrapper, PlotType.XY, null);
		residualsSystem.getPlotComposite().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		residualsSystem.setTitle("Residuals");
		calibrationData.addUserObject(GoldCalibrationWizard.RESIDUALS_SYSTEM, residualsSystem);

		setControl(sashForm);
	}

	@Override
	public boolean runProcess() {
		System.out.println("Page 3");
		try {
			getContainer().run(true, true, fitterWithProgress);
		} catch (InvocationTargetException | InterruptedException e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}

	@Override
	public int getPageNumber() {
		return 3;
	}

}