/*
 * Copyright (c) 2012 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.analysis.rcp.preference;

import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import uk.ac.diamond.scisoft.analysis.rcp.AnalysisRCPActivator;

public class FileAttributesPreferencePage extends PreferencePage implements IWorkbenchPreferencePage {

	private Text filterInput;

	@Override
	public void init(IWorkbench workbench) {
		setPreferenceStore(AnalysisRCPActivator.getDefault().getPreferenceStore());
	}

	@Override
	protected Control createContents(Composite parent) {
		Composite comp = new Composite(parent, SWT.NONE);
		comp.setLayout(new GridLayout(2, false));
		GridData gdc = new GridData(SWT.FILL, SWT.FILL, true, true);
		comp.setLayoutData(gdc);

		Label filterLabel = new Label(comp, SWT.LEFT);
		filterLabel.setText("Attribute filters:");
		filterInput = new Text(comp, SWT.SINGLE | SWT.BORDER | SWT.LEFT);
		filterInput.setToolTipText("Set the attributes to filter out as comma-seperated list. Wildcards can be used.");
		GridData filterGridData = new GridData(SWT.FILL, SWT.NONE, true, false);
		filterInput.setLayoutData(filterGridData);

		filterInput.setText(getFilterPreference());

		return comp;
	}

	public String getFilterPreference() {
		if (getPreferenceStore().isDefault(PreferenceConstants.FILE_ATTRIBUTE_FILTERS)){
			return getPreferenceStore().getDefaultString(PreferenceConstants.FILE_ATTRIBUTE_FILTERS);
		}
		return getPreferenceStore().getString(PreferenceConstants.FILE_ATTRIBUTE_FILTERS);
	}

	public void setFilterPreference(String value) {
		getPreferenceStore().setValue(PreferenceConstants.FILE_ATTRIBUTE_FILTERS, value);
	}

	public static String getFilterValue() {
		return AnalysisRCPActivator.getDefault().getPreferenceStore().getString(PreferenceConstants.FILE_ATTRIBUTE_FILTERS);
	}

	@Override
	public boolean performOk() {
		storePreferences();
		return true;
	}

	private void storePreferences() {
		setFilterPreference(filterInput.getText());
	}
}
