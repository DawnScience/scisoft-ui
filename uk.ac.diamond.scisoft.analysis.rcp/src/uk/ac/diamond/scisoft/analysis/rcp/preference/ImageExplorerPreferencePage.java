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
import java.util.List;

import org.eclipse.dawnsci.plotting.api.IPlottingSystem;
import org.eclipse.dawnsci.plotting.api.PlottingFactory;
import org.eclipse.dawnsci.plotting.api.histogram.IPaletteService;
import org.eclipse.dawnsci.plotting.api.trace.IPaletteTrace;
import org.eclipse.dawnsci.plotting.api.trace.ITrace;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.PlatformUI;

import uk.ac.diamond.scisoft.analysis.rcp.AnalysisRCPActivator;
import uk.ac.diamond.scisoft.analysis.rcp.views.ImageExplorerView;

/**
 *
 */
public class ImageExplorerPreferencePage extends PreferencePage implements IWorkbenchPreferencePage {

	public static final String ID = "uk.ac.diamond.scisoft.analysis.rcp.imageExplorerPreferencePage";
	private Combo cmbColourMap;
	private Spinner spnAutoLoThreshold;
	private Spinner spnAutoHiThreshold;
	private Spinner spnWaitTime;
	private Spinner spnSkipImages;
	private Spinner spnImageSize;
	private Combo cmbDisplayViews;

	private IPaletteService pservice = PlatformUI.getWorkbench().getService(IPaletteService.class);
	private String schemeName;

	public ImageExplorerPreferencePage() {
	}

	public ImageExplorerPreferencePage(String title) {
		super(title);
	}

	public ImageExplorerPreferencePage(String title, ImageDescriptor image) {
		super(title, image);
	}

	@Override
	protected Control createContents(Composite parent) {
		Composite comp = new Composite(parent, SWT.NONE);
		comp.setLayout(new GridLayout(3, false));
		GridData gdc;

		Label lblColourMap = new Label(comp, SWT.LEFT);
		lblColourMap.setText("Default colour mapping");
		cmbColourMap = new Combo(comp, SWT.RIGHT | SWT.READ_ONLY);
		gdc = new GridData();
		gdc.horizontalSpan = 2;
		cmbColourMap.setLayoutData(gdc);

		// Get all information from the IPalette service
		final Collection<String> colours = pservice.getColorSchemes();
		schemeName = getPreferenceStore().getString(PreferenceConstants.IMAGEEXPLORER_COLOURMAP);
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

		Label lblLThreshold = new Label(comp, SWT.LEFT);
		lblLThreshold.setText("Auto-contrast lower threshold (in %)");
		spnAutoLoThreshold = new Spinner(comp, SWT.RIGHT | SWT.BORDER);
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
		gdc = new GridData();
		gdc.horizontalSpan = 2;
		spnAutoLoThreshold.setLayoutData(gdc);

		Label lblHThreshold = new Label(comp, SWT.LEFT);
		lblHThreshold.setText("Auto-contrast upper threshold (in %)");
		spnAutoHiThreshold = new Spinner(comp, SWT.RIGHT | SWT.BORDER);
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
		gdc = new GridData();
		gdc.horizontalSpan = 2;
		spnAutoHiThreshold.setLayoutData(gdc);

		Label lblWaitTime = new Label(comp, SWT.LEFT);
		lblWaitTime.setText("Time delay for next image in play mode");
		spnWaitTime = new Spinner(comp, SWT.RIGHT | SWT.BORDER);
		spnWaitTime.setMinimum(150);
		spnWaitTime.setMaximum(15000);
		spnWaitTime.setIncrement(50);
		Label lblUnits = new Label(comp, SWT.LEFT);
		lblUnits.setText("in ms");

		Label lblPlayback = new Label(comp, SWT.LEFT);
		lblPlayback.setText("View to use for playback");
		cmbDisplayViews = new Combo(comp, SWT.RIGHT | SWT.READ_ONLY);
		List<String> views = ImageExplorerView.getRegisteredViews();
		for (String s : views) {
			cmbDisplayViews.add(s);
		}
		gdc = new GridData();
		gdc.horizontalSpan = 2;
		cmbDisplayViews.setLayoutData(gdc);

		Label lblSkipImages = new Label(comp, SWT.LEFT);
		lblSkipImages.setText("Playback every");
		spnSkipImages = new Spinner(comp, SWT.RIGHT | SWT.BORDER);
		spnSkipImages.setMinimum(1);
		spnSkipImages.setMaximum(100);
		spnSkipImages.setIncrement(1);
		Label lblImages = new Label(comp, SWT.LEFT);
		lblImages.setText("image");

		Label lblImageSize = new Label(comp, SWT.LEFT);
		lblImageSize.setText("Thumbnail Size");
		spnImageSize = new Spinner(comp, SWT.RIGHT | SWT.BORDER);
		spnImageSize.setMinimum(50);
		spnImageSize.setMaximum(300);
		spnImageSize.setIncrement(5);
		gdc = new GridData();
		gdc.horizontalSpan = 2;
		spnImageSize.setLayoutData(gdc);

		initializePage();

		parent.layout();
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

	private void initializePage() {
		cmbColourMap.select(cmbColourMap.indexOf(getColourMapChoicePreference()));
		spnAutoLoThreshold.setSelection(getAutoContrastLoPreference());
		spnAutoHiThreshold.setSelection(getAutoContrastHiPreference());
		spnWaitTime.setSelection(getTimeDelayPreference());
		spnSkipImages.setSelection(getPlaybackRatePreference());
		spnImageSize.setSelection(getImageSizePreference());
		String viewName = getPlaybackViewPreference();
		for (int i = 0; i < cmbDisplayViews.getItems().length; i++) {
			if (cmbDisplayViews.getItems()[i].equals(viewName))
				cmbDisplayViews.select(i);
		}
	}

	private void storePreferences() {
		setColourMapChoicePreference(cmbColourMap.getItem(cmbColourMap.getSelectionIndex()));
		setAutoContrastLoPreference(spnAutoLoThreshold.getSelection());
		setAutoContrastHiPreference(spnAutoHiThreshold.getSelection());
		setTimeDelayPreference(spnWaitTime.getSelection());
		setPlaybackViewPreference(cmbDisplayViews.getItem(cmbDisplayViews.getSelectionIndex()));
		setPlaybackRatePreference(spnSkipImages.getSelection());
		setImageSizePreference(spnImageSize.getSelection());
	}

	private void loadDefaultPreferences() {
		cmbColourMap.select(cmbColourMap.indexOf(getDefaultColourMapChoicePreference()));
		spnAutoLoThreshold.setSelection(getDefaultAutoContrastLoPreference());
		spnAutoHiThreshold.setSelection(getDefaultAutoContrastHiPreference());
		spnWaitTime.setSelection(getDefaultTimeDelayPreference());
		spnSkipImages.setSelection(getDefaultPlaybackRatePreference());
		spnImageSize.setSelection(getDefaultImageSizePreference());
		String viewName = getDefaultPlaybackViewPreference();
		for (int i = 0; i < cmbDisplayViews.getItems().length; i++) {
			if (cmbDisplayViews.getItems()[i].equals(viewName))
				cmbDisplayViews.select(i);
		}
	}

	private String getDefaultColourMapChoicePreference() {
		return getPreferenceStore().getDefaultString(PreferenceConstants.IMAGEEXPLORER_COLOURMAP);
	}

	private int getDefaultAutoContrastLoPreference() {
		return getPreferenceStore().getDefaultInt(PreferenceConstants.IMAGEEXPLORER_AUTOCONTRAST_LOTHRESHOLD);
	}

	private int getDefaultAutoContrastHiPreference() {
		return getPreferenceStore().getDefaultInt(PreferenceConstants.IMAGEEXPLORER_AUTOCONTRAST_HITHRESHOLD);
	}

	private int getDefaultTimeDelayPreference() {
		return getPreferenceStore().getDefaultInt(PreferenceConstants.IMAGEEXPLORER_TIMEDELAYBETWEENIMAGES);
	}

	private String getDefaultPlaybackViewPreference() {
		return getPreferenceStore().getDefaultString(PreferenceConstants.IMAGEEXPLORER_PLAYBACKVIEW);
	}

	private int getDefaultPlaybackRatePreference() {
		return getPreferenceStore().getDefaultInt(PreferenceConstants.IMAGEEXPLORER_PLAYBACKRATE);
	}

	private int getDefaultImageSizePreference() {
		return getPreferenceStore().getDefaultInt(PreferenceConstants.IMAGEEXPLORER_IMAGESIZE);
	}

	private String getColourMapChoicePreference() {
		if (getPreferenceStore().isDefault(PreferenceConstants.IMAGEEXPLORER_COLOURMAP)) {
			return getPreferenceStore().getDefaultString(PreferenceConstants.IMAGEEXPLORER_COLOURMAP);
		}
		return getPreferenceStore().getString(PreferenceConstants.IMAGEEXPLORER_COLOURMAP);
	}

	private int getAutoContrastLoPreference() {
		if (getPreferenceStore().isDefault(PreferenceConstants.IMAGEEXPLORER_AUTOCONTRAST_LOTHRESHOLD)) {
			return getPreferenceStore().getDefaultInt(PreferenceConstants.IMAGEEXPLORER_AUTOCONTRAST_LOTHRESHOLD);
		}
		return getPreferenceStore().getInt(PreferenceConstants.IMAGEEXPLORER_AUTOCONTRAST_LOTHRESHOLD);
	}

	private int getAutoContrastHiPreference() {
		if (getPreferenceStore().isDefault(PreferenceConstants.IMAGEEXPLORER_AUTOCONTRAST_HITHRESHOLD)) {
			return getPreferenceStore().getDefaultInt(PreferenceConstants.IMAGEEXPLORER_AUTOCONTRAST_HITHRESHOLD);
		}
		return getPreferenceStore().getInt(PreferenceConstants.IMAGEEXPLORER_AUTOCONTRAST_HITHRESHOLD);
	}

	private int getTimeDelayPreference() {
		if (getPreferenceStore().isDefault(PreferenceConstants.IMAGEEXPLORER_TIMEDELAYBETWEENIMAGES)) {
			return getPreferenceStore().getDefaultInt(PreferenceConstants.IMAGEEXPLORER_TIMEDELAYBETWEENIMAGES);
		}
		return getPreferenceStore().getInt(PreferenceConstants.IMAGEEXPLORER_TIMEDELAYBETWEENIMAGES);
	}

	private int getPlaybackRatePreference() {
		if (getPreferenceStore().isDefault(PreferenceConstants.IMAGEEXPLORER_PLAYBACKRATE)) {
			return getPreferenceStore().getDefaultInt(PreferenceConstants.IMAGEEXPLORER_PLAYBACKRATE);
		}
		return getPreferenceStore().getInt(PreferenceConstants.IMAGEEXPLORER_PLAYBACKRATE);
	}

	private int getImageSizePreference() {
		if (getPreferenceStore().isDefault(PreferenceConstants.IMAGEEXPLORER_IMAGESIZE)) {
			return getPreferenceStore().getDefaultInt(PreferenceConstants.IMAGEEXPLORER_IMAGESIZE);
		}
		return getPreferenceStore().getInt(PreferenceConstants.IMAGEEXPLORER_IMAGESIZE);
	}

	private String getPlaybackViewPreference() {
		if (getPreferenceStore().isDefault(PreferenceConstants.IMAGEEXPLORER_PLAYBACKVIEW)) {
			return getPreferenceStore().getDefaultString(PreferenceConstants.IMAGEEXPLORER_PLAYBACKVIEW);
		}
		return getPreferenceStore().getString(PreferenceConstants.IMAGEEXPLORER_PLAYBACKVIEW);
	}

	private void setColourMapChoicePreference(String value) {
		// update the preference
		getPreferenceStore().setValue(PreferenceConstants.IMAGEEXPLORER_COLOURMAP, value);
		IPlottingSystem<Composite> system = PlottingFactory.getPlottingSystem(getPlaybackViewPreference());
		if (system == null) return;
		// update the palette data
		final Collection<ITrace> traces = system.getTraces();
		if (traces!=null) for (ITrace trace: traces) {
			if (trace instanceof IPaletteTrace) {
				IPaletteTrace palette = (IPaletteTrace) trace;
				palette.setPalette(schemeName);
			}
		}
	}

	private void setAutoContrastLoPreference(int value) {
		getPreferenceStore().setValue(PreferenceConstants.IMAGEEXPLORER_AUTOCONTRAST_LOTHRESHOLD, value);
	}

	private void setAutoContrastHiPreference(int value) {
		getPreferenceStore().setValue(PreferenceConstants.IMAGEEXPLORER_AUTOCONTRAST_HITHRESHOLD, value);
	}

	private void setTimeDelayPreference(int value) {
		getPreferenceStore().setValue(PreferenceConstants.IMAGEEXPLORER_TIMEDELAYBETWEENIMAGES, value);
	}

	private void setPlaybackViewPreference(String newView) {
		getPreferenceStore().setValue(PreferenceConstants.IMAGEEXPLORER_PLAYBACKVIEW, newView);
	}

	private void setPlaybackRatePreference(int value) {
		getPreferenceStore().setValue(PreferenceConstants.IMAGEEXPLORER_PLAYBACKRATE, value);
	}

	private void setImageSizePreference(int value) {
		getPreferenceStore().setValue(PreferenceConstants.IMAGEEXPLORER_IMAGESIZE, value);
	}
}
