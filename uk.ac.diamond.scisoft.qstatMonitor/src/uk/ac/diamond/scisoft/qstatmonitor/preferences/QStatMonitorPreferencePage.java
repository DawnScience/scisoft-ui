/*
 * Copyright (c) 2012 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package uk.ac.diamond.scisoft.qstatmonitor.preferences;

import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import uk.ac.diamond.scisoft.qstatmonitor.Activator;

public class QStatMonitorPreferencePage extends PreferencePage
		implements
			IWorkbenchPreferencePage {

	public static final String ID = "uk.ac.diamond.scisoft.qstatmonitor.preferences.QStatMonitorPreferencePage";

	private static final int SPN_DEC_PLACES = 1;
	// Must not exceed SPN_DEC_PLACES number of decimal places
	private static final float REF_INC_VAL = 0.5f;
	private static final float REF_MIN_VAL = 2.0f;
	private static final float REF_MAX_VAL = 100.0f;
	
	// List of items for resource combo
	private static final String[] RESOURCE_LIST = new String[] {"tesla", "tesla64"};
	
	/**
	 * Converts refresh interval value to spinner compatible value (integer)
	 * 
	 * @param 
	 * 		value
	 * @return
	 * 		spinner value
	 */
	private static int convertSpinnerValue(float value) {
		int multFactor = (int) Math.pow(10, SPN_DEC_PLACES);
		return (int) (value * multFactor);
	}
	
	// Widgets
	private Button btnAutoRefresh;
	private Button btnAllResources;
	private Button btnUsersAll;
	private Button btnUsersCurr;
	private Button btnUsersCust;
	private Spinner spnInterval;
	private Combo cboResource;
	private Text txtUser;
	
	// Validation checks
	// Required to determine overall state
	private boolean validResource = true;
	private boolean validUser = true;
	
	public QStatMonitorPreferencePage() {
	}

	@Override
	public void init(IWorkbench workbench) {
		setPreferenceStore(Activator.getDefault().getPreferenceStore());
	}

	@Override
	protected Control createContents(Composite parent) {
		final IPreferenceStore preferences = Activator.getDefault().getPreferenceStore();
		
		Composite mainComposite = new Composite(parent, SWT.NONE);
		mainComposite.setLayout(new GridLayout(1, false));
		mainComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		
		setupRefreshPart(mainComposite, preferences);

		// Empty label to create gap between Refresh and Query parts
		new Label(mainComposite, SWT.NONE);
		
		setupQueryPart(mainComposite, preferences);
	
		initialiseWidgets(preferences);
		
		return mainComposite;
	}
	
	/**
	 * Sets up widgets for refresh settings
	 * 
	 * @param parent
	 * @param preferences
	 */
	private void setupRefreshPart(Composite parent, final IPreferenceStore preferences) {
		// Place all widgets inside Group
		Group grpRefresh = new Group(parent, SWT.NONE);
		grpRefresh.setText("Refresh Settings");
		grpRefresh.setLayout(new GridLayout(2, false));
		grpRefresh.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
		
		(new Label(grpRefresh, SWT.NONE)).setText("Enable automatic refreshing");
		btnAutoRefresh = new Button(grpRefresh, SWT.CHECK | SWT.NO_FOCUS);		
		btnAutoRefresh.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				// Enables/disables interval spinner
				spnInterval.setEnabled(!spnInterval.getEnabled());
				//spnInterval.setSelection(convertSpinnerValue(preferences.getFloat(QStatMonitorPreferenceConstants.P_SLEEP)));
			}
		});
		
		(new Label(grpRefresh, SWT.NONE)).setText("Interval (seconds)");
		spnInterval = new Spinner(grpRefresh, SWT.READ_ONLY | SWT.BORDER);
		spnInterval.setDigits(SPN_DEC_PLACES);
		spnInterval.setIncrement(convertSpinnerValue(REF_INC_VAL));
		spnInterval.setMinimum(convertSpinnerValue(REF_MIN_VAL));
		spnInterval.setMaximum(convertSpinnerValue(REF_MAX_VAL));		
	}
	
	/**
	 * Sets up widgets for query configuration
	 * 
	 * @param parent
	 * @param preferences
	 */
	private void setupQueryPart(Composite parent, final IPreferenceStore preferences) {
		(new Label(parent, SWT.NONE)).setText("Query Configuration");
		setupResourcesSubPart(parent, preferences);
		setupUsersSubPart(parent, preferences);
	}
	
	/**
	 * Sets up widgets for resource part of query configuration
	 * 
	 * @param parent
	 * @param preferences
	 */
	private void setupResourcesSubPart(Composite parent, final IPreferenceStore preferences) {
		// Put widgets inside group
		Group grpResources = new Group(parent, SWT.NONE);
		grpResources.setText("Resources");
		grpResources.setLayout(new GridLayout(2, false));
		grpResources.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));

		(new Label(grpResources, SWT.NONE)).setText("Show all resources");
		btnAllResources = new Button(grpResources, SWT.CHECK | SWT.NO_FOCUS);
		btnAllResources.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				// Enable/disable combobox
				cboResource.setEnabled(!cboResource.getEnabled());
				
				// All resources checkbox selected
				if (btnAllResources.getSelection()) {
					validResource = true;
					setValidState();
				} else {
					validateResourceText();
				}
			}
		});
		
		
		(new Label(grpResources, SWT.NONE)).setText("Custom resource");
		cboResource = new Combo(grpResources, SWT.DROP_DOWN);
		cboResource.setItems(RESOURCE_LIST);
		cboResource.addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent e) {
				if (!btnAllResources.getSelection()) {
					validateResourceText();
				}
			}
		});
	}
	
	/**
	 * Sets up widgets for user part of query configuration
	 * 
	 * @param parent
	 * @param preferences
	 */
	private void setupUsersSubPart(Composite parent, final IPreferenceStore preferences) {
		// Put widgets inside group
		Group grpUsers = new Group(parent, SWT.NONE);
		grpUsers.setText("Users");
		grpUsers.setLayout(new GridLayout(2, false));
		grpUsers.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
		
		// Set up radio buttons
	    btnUsersAll = new Button(grpUsers, SWT.RADIO);
	    btnUsersAll.setText("All");
	    btnUsersAll.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				txtUser.setEnabled(false);
				validUser = true;
				setValidState();
			}
		});
	    new Label(grpUsers, SWT.NONE); // Empty label to fill 2nd cell

	    btnUsersCurr = new Button(grpUsers, SWT.RADIO);
	    btnUsersCurr.setText("Current");
	    btnUsersCurr.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				txtUser.setEnabled(false);
				validUser = true;
				setValidState();
			}
		});
	    new Label(grpUsers, SWT.NONE); // Empty label to fill 2nd cell

	    btnUsersCust = new Button(grpUsers, SWT.RADIO);
	    btnUsersCust.setText("Custom");
	    btnUsersCust.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				txtUser.setEnabled(true);
				validateUserText();
			}
		});
		
	    // Custom user text box
	    txtUser = new Text(grpUsers, SWT.BORDER);
	    txtUser.setLayoutData(new GridData(80, SWT.DEFAULT));
	    txtUser.addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent e) {
				if (btnUsersCust.getSelection()) {
					validateUserText();
				}
			}
		});
	}
	
	/**
	 * Attempt to make state valid
	 */
	private void setValidState() {
		setErrorMessage(null);
		
		// Both must be true
		if (validResource && validUser) {
			setValid(true);
		}
		
		showErrorMessage();
	}
	
	/**
	 * Reprint error message if still invalid
	 */
	private void showErrorMessage() {
		if (!validResource) {
			setErrorMessage("Resource name cannot be blank");
		}
		
		if (!validUser) {
			setErrorMessage("Username cannot be blank");
		}
	}
	
	/**
	 * Validates resource text value
	 */
	private void validateResourceText() {
		// Text value not empty
		if (!cboResource.getText().equals("")) {
			validResource = true;
			setValidState();
		} else {
			validResource = false;
			setErrorMessage("Resource name cannot be blank");
			setValid(false);
		}
	}
	
	/**
	 * Validates user text value
	 */
	private void validateUserText() {
		// Text value not empty
		if (!txtUser.getText().equals("")) {
			validUser = true;
			setValidState();
		} else {
			validUser = false;
			setErrorMessage("Username cannot be blank");
			setValid(false);
		}
	}
	
	/**
	 * Initialises widgets start-up configuration
	 * 
	 * @param preferences
	 */
	private void initialiseWidgets(final IPreferenceStore preferences) {
		setWidgetValues(preferences);
		disableWidgets();
	}
	
	/**
	 * Initialises widgets start-up values
	 * 
	 * @param preferences
	 */
	private void setWidgetValues(final IPreferenceStore preferences) {
		btnAutoRefresh.setSelection(preferences.getBoolean(QStatMonitorPreferenceConstants.P_REFRESH));
		spnInterval.setSelection(convertSpinnerValue(preferences.getFloat(QStatMonitorPreferenceConstants.P_SLEEP)));
		
		btnAllResources.setSelection(preferences.getBoolean(QStatMonitorPreferenceConstants.P_RESOURCES_ALL));		
		cboResource.setText(preferences.getString(QStatMonitorPreferenceConstants.P_RESOURCE));
		
		btnUsersAll.setSelection(preferences.getBoolean(QStatMonitorPreferenceConstants.P_USER_ALL));
		btnUsersCurr.setSelection(preferences.getBoolean(QStatMonitorPreferenceConstants.P_USER_CURR));
		btnUsersCust.setSelection(preferences.getBoolean(QStatMonitorPreferenceConstants.P_USER_CUST));
		txtUser.setText(preferences.getString(QStatMonitorPreferenceConstants.P_USER));
	}
	
	/**
	 * Disables widgets depending on button selection
	 */
	private void disableWidgets() {
		if (!btnAutoRefresh.getSelection()) {
			spnInterval.setEnabled(false);
		}
		if (btnAllResources.getSelection()) {
			cboResource.setEnabled(false);
		}
		if (btnUsersAll.getSelection() | btnUsersCurr.getSelection()) {
			txtUser.setEnabled(false);
		}
	}
	
	@Override
	public boolean performOk() {
		IPreferenceStore preferences = Activator.getDefault().getPreferenceStore();
		
		preferences.setValue(QStatMonitorPreferenceConstants.P_REFRESH, btnAutoRefresh.getSelection());
		if (btnAutoRefresh.getSelection()) {
			preferences.setValue(QStatMonitorPreferenceConstants.P_SLEEP, Float.parseFloat(spnInterval.getText()));
		}
		
		preferences.setValue(QStatMonitorPreferenceConstants.P_RESOURCES_ALL, btnAllResources.getSelection());
		if (!btnAllResources.getSelection()) {
			preferences.setValue(QStatMonitorPreferenceConstants.P_RESOURCE, cboResource.getText());
		} else {
			preferences.setValue(QStatMonitorPreferenceConstants.P_RESOURCE, "");
		}
		
		preferences.setValue(QStatMonitorPreferenceConstants.P_USER_ALL, btnUsersAll.getSelection());
		preferences.setValue(QStatMonitorPreferenceConstants.P_USER_CURR, btnUsersCurr.getSelection());
		preferences.setValue(QStatMonitorPreferenceConstants.P_USER_CUST, btnUsersCust.getSelection());
		if (btnUsersCust.getSelection()) {
			preferences.setValue(QStatMonitorPreferenceConstants.P_USER, txtUser.getText());
		} else {
			preferences.setValue(QStatMonitorPreferenceConstants.P_USER, "");
		}
		
		return super.performOk();
	}
	
	@Override
	protected void performDefaults() {
		IPreferenceStore preferences = Activator.getDefault().getPreferenceStore();
		setWidgetsDefaultValues(preferences);
		disableWidgets();
		resetState();
	}
	
	/**
	 * Sets page state to valid after reset to default preference values
	 */
	private void resetState() {
		validResource = true;
		validUser = true;
		setErrorMessage(null);
		setValid(true);
	}
	
	/**
	 * Initialises widgets to default values
	 * 
	 * @param preferences
	 */
	private void setWidgetsDefaultValues(final IPreferenceStore preferences) {
		btnAutoRefresh.setSelection(preferences.getDefaultBoolean(QStatMonitorPreferenceConstants.P_REFRESH));
		spnInterval.setSelection(convertSpinnerValue(preferences.getDefaultFloat(QStatMonitorPreferenceConstants.P_SLEEP)));
		
		btnAllResources.setSelection(preferences.getDefaultBoolean(QStatMonitorPreferenceConstants.P_RESOURCES_ALL));		
		cboResource.setText(preferences.getDefaultString(QStatMonitorPreferenceConstants.P_RESOURCE));
		
		btnUsersAll.setSelection(preferences.getDefaultBoolean(QStatMonitorPreferenceConstants.P_USER_ALL));
		btnUsersCurr.setSelection(preferences.getDefaultBoolean(QStatMonitorPreferenceConstants.P_USER_CURR));
		btnUsersCust.setSelection(preferences.getDefaultBoolean(QStatMonitorPreferenceConstants.P_USER_CUST));
		txtUser.setText(preferences.getDefaultString(QStatMonitorPreferenceConstants.P_USER));
	}

}
