/*
 * Copyright (c) 2012 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.analysis.rcp.preference;

import java.util.Collection;

import org.eclipse.dawnsci.plotting.api.histogram.IPaletteService;
import org.eclipse.dawnsci.plotting.api.preferences.BasePlottingConstants;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.PlatformUI;

import uk.ac.diamond.scisoft.analysis.rcp.AnalysisRCPActivator;

public class PlotViewPreferencePage extends PreferencePage implements IWorkbenchPreferencePage {

	private Combo cmbColourMap;
	private Combo cmbColourScale;
	private Combo cmbCameraPerspective;
	private Button chkExpertMode;
	private Button chkAutoContrast;
	private Button chkScrollbars;
	private Spinner spnAutoLoThreshold;
	private Spinner spnAutoHiThreshold;

	private IPaletteService pservice = PlatformUI.getWorkbench().getService(IPaletteService.class);
	private String schemeName;
	private Button chkAspectRatio;

	public PlotViewPreferencePage() {
	}

	public PlotViewPreferencePage(String title) {
		super(title);
	}

	public PlotViewPreferencePage(String title, ImageDescriptor image) {
		super(title, image);
	}

	@Override
	protected Control createContents(Composite parent) {
		Composite comp = new Composite(parent, SWT.NONE);
		comp.setLayout(new GridLayout(1, false));

		Group plotMulti1DGroup = new Group(comp, SWT.NONE);
		plotMulti1DGroup.setText("Plot 1DStack");
		plotMulti1DGroup.setLayout(new GridLayout(2, false));
		GridData gd = new GridData(SWT.FILL, SWT.FILL, true, false);
		plotMulti1DGroup.setLayoutData(gd);

		Label lblCameraType = new Label(plotMulti1DGroup, SWT.LEFT);
		lblCameraType.setText("Camera projection: ");
		cmbCameraPerspective = new Combo(plotMulti1DGroup, SWT.RIGHT | SWT.READ_ONLY);
		cmbCameraPerspective.add("Orthographic");
		cmbCameraPerspective.add("Perspective");

		Group plot2DGroup = new Group(comp, SWT.NONE);
		plot2DGroup.setText("Plot 2D");
		plot2DGroup.setLayout(new GridLayout(2, false));
		gd = new GridData(SWT.FILL, SWT.FILL, true, false);
		plot2DGroup.setLayoutData(gd);

		Label lblColourMap = new Label(plot2DGroup, SWT.LEFT);
		lblColourMap.setText("Default colour mapping");
		cmbColourMap = new Combo(plot2DGroup, SWT.RIGHT | SWT.READ_ONLY);
		// Get all information from the IPalette service
		final Collection<String> colours = pservice.getColorSchemes();
		schemeName = AnalysisRCPActivator.getDefault().getPreferenceStore().getString(PreferenceConstants.PLOT_VIEW_PLOT2D_COLOURMAP);
		int i = 0;
		for (String colour : colours) {
			cmbColourMap.add(colour);
			if (!colour.equals(schemeName)) i++;
		}
		cmbColourMap.select(i);
		cmbColourMap.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				schemeName = cmbColourMap.getText();
			}
		});

		Label lblExpertMode = new Label(plot2DGroup, SWT.LEFT);
		lblExpertMode.setText("Colour map expert mode");
		chkExpertMode = new Button(plot2DGroup, SWT.CHECK | SWT.RIGHT);

		Label lblAutoHisto = new Label(plot2DGroup, SWT.LEFT);
		lblAutoHisto.setText("Auto contrast");
		chkAutoContrast = new Button(plot2DGroup, SWT.CHECK | SWT.RIGHT);

		Label lblLThreshold = new Label(plot2DGroup, SWT.LEFT);
		lblLThreshold.setText("Auto-contrast lower threshold (in %)");
		spnAutoLoThreshold = new Spinner(plot2DGroup, SWT.BORDER | SWT.RIGHT);
		spnAutoLoThreshold.setMinimum(0);
		spnAutoLoThreshold.setMaximum(99);
		spnAutoLoThreshold.setIncrement(1);
		spnAutoLoThreshold.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				int l = spnAutoLoThreshold.getSelection() + PreferenceConstants.MINIMUM_CONTRAST_DELTA;
				if (spnAutoHiThreshold.getSelection() < l)
					spnAutoHiThreshold.setSelection(l);
				spnAutoHiThreshold.setMinimum(l);
			}
		});

		Label lblHThreshold = new Label(plot2DGroup, SWT.LEFT);
		lblHThreshold.setText("Auto-contrast upper threshold (in %)");
		spnAutoHiThreshold = new Spinner(plot2DGroup, SWT.BORDER | SWT.RIGHT);
		spnAutoHiThreshold.setMinimum(1);
		spnAutoHiThreshold.setMaximum(100);
		spnAutoHiThreshold.setIncrement(1);
		spnAutoHiThreshold.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				int h = spnAutoHiThreshold.getSelection() - PreferenceConstants.MINIMUM_CONTRAST_DELTA;
				if (spnAutoLoThreshold.getSelection() > h)
					spnAutoLoThreshold.setSelection(h);
				spnAutoLoThreshold.setMaximum(h);
			}
		});

		Label lblScaling = new Label(plot2DGroup, SWT.LEFT);
		lblScaling.setText("Colour scaling");
		cmbColourScale = new Combo(plot2DGroup, SWT.RIGHT | SWT.READ_ONLY);
		cmbColourScale.add("Linear");
		cmbColourScale.add("Logarithmic");

		Label lblScrollbars = new Label(plot2DGroup, SWT.LEFT);
		lblScrollbars.setText("Show scrollbars");
		chkScrollbars = new Button(plot2DGroup, SWT.CHECK | SWT.RIGHT);

		Label lblAspectRatio = new Label(plot2DGroup, SWT.LEFT);
		lblAspectRatio.setText("Keep Aspect Ratio");
		chkAspectRatio = new Button(plot2DGroup, SWT.CHECK | SWT.RIGHT);

		initializePage();

		return comp;
	}

	@Override
	public void init(IWorkbench workbench) {
		setPreferenceStore(AnalysisRCPActivator.getDefault().getPreferenceStore());
	}

	@Override
	public boolean performOk() {
		storePreferences();
		return true;
	}

	@Override
	protected void performDefaults() {
		loadDefaultPreferences();
	}

	/**
	 * Load the resolution value
	 */
	private void initializePage() {
		cmbColourMap.select(cmbColourMap.indexOf(getColourMapChoicePreference()));
		chkExpertMode.setSelection(getExpertModePreference());
		chkAutoContrast.setSelection(getAutoContrastPreference());
		spnAutoLoThreshold.setSelection(getAutoContrastLoPreference());
		spnAutoHiThreshold.setSelection(getAutoContrastHiPreference());
		cmbColourScale.select(getColourScaleChoicePreference());
		cmbCameraPerspective.select(getPerspectivePreference());
		chkScrollbars.setSelection(getScrollBarPreference());
		chkAspectRatio.setSelection(getAspectRatioPreference());
	}

	/**
	 * Load the default resolution value
	 */
	private void loadDefaultPreferences() {
		cmbColourMap.select(cmbColourMap.indexOf(getDefaultColourMapChoicePreference()));
		chkExpertMode.setSelection(getDefaultExpertModePreference());
		chkAutoContrast.setSelection(getDefaultAutoContrastPreference());
		spnAutoLoThreshold.setSelection(getDefaultAutoContrastLoPreference());
		spnAutoHiThreshold.setSelection(getDefaultAutoContrastHiPreference());
		cmbColourScale.select(getDefautColourScaleChoicePreference());
		cmbCameraPerspective.select(getDefaultPerspectivePreference());
		chkScrollbars.setSelection(getDefaultScrollBarPreference());
		chkAspectRatio.setSelection(getDefaultAspectRatioPreference());
	}

	/**
	 * Store the resolution value
	 */
	private void storePreferences() {
		setColourMapChoicePreference(cmbColourMap.getItem(cmbColourMap.getSelectionIndex()));
		setExpertModePreference(chkExpertMode.getSelection());
		setAutoContrastPreference(chkAutoContrast.getSelection());
		setAutoContrastLoPreference(spnAutoLoThreshold.getSelection());
		setAutoContrastHiPreference(spnAutoHiThreshold.getSelection());
		setColourScaleChoicePreference(cmbColourScale.getSelectionIndex());
		setCameraPerspective(cmbCameraPerspective.getSelectionIndex());
		setScrollBarPreference(chkScrollbars.getSelection());
		setAspectRatioPreference(chkAspectRatio.getSelection());
	}

	private int getDefaultPerspectivePreference() {
		return getPreferenceStore().getDefaultInt(PreferenceConstants.PLOT_VIEW_MULTI1D_CAMERA_PROJ);
	}

	private boolean getDefaultAspectRatioPreference() {
		return AnalysisRCPActivator.getPlottingPreferenceStore().getDefaultBoolean(BasePlottingConstants.ASPECT);
	}

	private String getDefaultColourMapChoicePreference() {
		return getPreferenceStore().getDefaultString(PreferenceConstants.PLOT_VIEW_PLOT2D_COLOURMAP);
	}

	private int getDefautColourScaleChoicePreference() {
		return getPreferenceStore().getDefaultInt(PreferenceConstants.PLOT_VIEW_PLOT2D_SCALING);
	}

	private boolean getDefaultExpertModePreference() {
		return getPreferenceStore().getDefaultBoolean(PreferenceConstants.PLOT_VIEW_PLOT2D_CMAP_EXPERT);
	}

	private boolean getDefaultAutoContrastPreference() {
		return getPreferenceStore().getDefaultBoolean(PreferenceConstants.PLOT_VIEW_PLOT2D_AUTOCONTRAST);
	}

	private int getDefaultAutoContrastLoPreference() {
		return getPreferenceStore().getDefaultInt(PreferenceConstants.PLOT_VIEW_PLOT2D_AUTOCONTRAST_LOTHRESHOLD);
	}

	private int getDefaultAutoContrastHiPreference() {
		return getPreferenceStore().getDefaultInt(PreferenceConstants.PLOT_VIEW_PLOT2D_AUTOCONTRAST_HITHRESHOLD);
	}

	private boolean getDefaultScrollBarPreference() {
		return getPreferenceStore().getDefaultBoolean(PreferenceConstants.PLOT_VIEW_PLOT2D_SHOWSCROLLBAR);
	}

	private int getPerspectivePreference() {
		if (getPreferenceStore().isDefault(PreferenceConstants.PLOT_VIEW_MULTI1D_CAMERA_PROJ)) {
			return getPreferenceStore().getDefaultInt(PreferenceConstants.PLOT_VIEW_MULTI1D_CAMERA_PROJ);
		}
		return getPreferenceStore().getInt(PreferenceConstants.PLOT_VIEW_MULTI1D_CAMERA_PROJ);
	}

	private boolean getAspectRatioPreference() {
		if (AnalysisRCPActivator.getPlottingPreferenceStore().isDefault(BasePlottingConstants.ASPECT)) {
			return AnalysisRCPActivator.getPlottingPreferenceStore().getDefaultBoolean(BasePlottingConstants.ASPECT);
		}
		return AnalysisRCPActivator.getPlottingPreferenceStore().getBoolean(BasePlottingConstants.ASPECT);
	}

	private String getColourMapChoicePreference() {
		if (getPreferenceStore().isDefault(PreferenceConstants.PLOT_VIEW_PLOT2D_COLOURMAP)) {
			return getPreferenceStore().getDefaultString(PreferenceConstants.PLOT_VIEW_PLOT2D_COLOURMAP);
		}
		return getPreferenceStore().getString(PreferenceConstants.PLOT_VIEW_PLOT2D_COLOURMAP);
	}

	private boolean getExpertModePreference() {
		if (getPreferenceStore().isDefault(PreferenceConstants.PLOT_VIEW_PLOT2D_CMAP_EXPERT)) {
			return getPreferenceStore().getDefaultBoolean(PreferenceConstants.PLOT_VIEW_PLOT2D_CMAP_EXPERT);
		}
		return getPreferenceStore().getBoolean(PreferenceConstants.PLOT_VIEW_PLOT2D_CMAP_EXPERT);
	}

	private boolean getAutoContrastPreference() {
		if (getPreferenceStore().isDefault(PreferenceConstants.PLOT_VIEW_PLOT2D_AUTOCONTRAST)) {
			return getPreferenceStore().getDefaultBoolean(PreferenceConstants.PLOT_VIEW_PLOT2D_AUTOCONTRAST);
		}
		return getPreferenceStore().getBoolean(PreferenceConstants.PLOT_VIEW_PLOT2D_AUTOCONTRAST);
	}

	private int getAutoContrastLoPreference() {
		if (getPreferenceStore().isDefault(PreferenceConstants.PLOT_VIEW_PLOT2D_AUTOCONTRAST_LOTHRESHOLD)) {
			return getPreferenceStore().getDefaultInt(PreferenceConstants.PLOT_VIEW_PLOT2D_AUTOCONTRAST_LOTHRESHOLD);
		}
		return getPreferenceStore().getInt(PreferenceConstants.PLOT_VIEW_PLOT2D_AUTOCONTRAST_LOTHRESHOLD);
	}

	private int getAutoContrastHiPreference() {
		if (getPreferenceStore().isDefault(PreferenceConstants.PLOT_VIEW_PLOT2D_AUTOCONTRAST_HITHRESHOLD)) {
			return getPreferenceStore().getDefaultInt(PreferenceConstants.PLOT_VIEW_PLOT2D_AUTOCONTRAST_HITHRESHOLD);
		}
		return getPreferenceStore().getInt(PreferenceConstants.PLOT_VIEW_PLOT2D_AUTOCONTRAST_HITHRESHOLD);
	}

	private int getColourScaleChoicePreference() {
		if (getPreferenceStore().isDefault(PreferenceConstants.PLOT_VIEW_PLOT2D_SCALING)) {
			return getPreferenceStore().getDefaultInt(PreferenceConstants.PLOT_VIEW_PLOT2D_SCALING);
		}
		return getPreferenceStore().getInt(PreferenceConstants.PLOT_VIEW_PLOT2D_SCALING);
	}

	private boolean getScrollBarPreference() {
		if (getPreferenceStore().isDefault(PreferenceConstants.PLOT_VIEW_PLOT2D_SHOWSCROLLBAR)) {
			return getPreferenceStore().getDefaultBoolean(PreferenceConstants.PLOT_VIEW_PLOT2D_SHOWSCROLLBAR);
		}
		return getPreferenceStore().getBoolean(PreferenceConstants.PLOT_VIEW_PLOT2D_SHOWSCROLLBAR);
	}

	private void setColourMapChoicePreference(String value) {
		getPreferenceStore().setValue(PreferenceConstants.PLOT_VIEW_PLOT2D_COLOURMAP, value);
		AnalysisRCPActivator.getPlottingPreferenceStore().setValue(BasePlottingConstants.COLOUR_SCHEME, value);
	}

	private void setAspectRatioPreference(boolean value) {
		AnalysisRCPActivator.getPlottingPreferenceStore().setValue(BasePlottingConstants.ASPECT, value);
	}

	private void setColourScaleChoicePreference(int value) {
		getPreferenceStore().setValue(PreferenceConstants.PLOT_VIEW_PLOT2D_SCALING, value);
	}

	private void setExpertModePreference(boolean value) {
		getPreferenceStore().setValue(PreferenceConstants.PLOT_VIEW_PLOT2D_CMAP_EXPERT, value);
	}

	private void setAutoContrastPreference(boolean value) {
		getPreferenceStore().setValue(PreferenceConstants.PLOT_VIEW_PLOT2D_AUTOCONTRAST, value);
	}

	private void setAutoContrastLoPreference(int value) {
		getPreferenceStore().setValue(PreferenceConstants.PLOT_VIEW_PLOT2D_AUTOCONTRAST_LOTHRESHOLD, value);
	}

	private void setAutoContrastHiPreference(int value) {
		getPreferenceStore().setValue(PreferenceConstants.PLOT_VIEW_PLOT2D_AUTOCONTRAST_HITHRESHOLD, value);
	}

	private void setCameraPerspective(int value) {
		getPreferenceStore().setValue(PreferenceConstants.PLOT_VIEW_MULTI1D_CAMERA_PROJ, value);
	}

	private void setScrollBarPreference(boolean value) {
		getPreferenceStore().setValue(PreferenceConstants.PLOT_VIEW_PLOT2D_SHOWSCROLLBAR, value);
	}

}
