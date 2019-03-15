/*
 * Copyright (c) 2012 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package uk.ac.diamond.scisoft.qstatmonitor.views;

import java.util.ArrayList;
import java.util.Arrays;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.dawnsci.plotting.api.IPlottingSystem;
import org.eclipse.dawnsci.plotting.api.PlotType;
import org.eclipse.dawnsci.plotting.api.PlottingFactory;
import org.eclipse.january.dataset.Dataset;
import org.eclipse.january.dataset.DatasetFactory;
import org.eclipse.january.dataset.DoubleDataset;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferenceDialog;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.IViewSite;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.PreferencesUtil;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.ui.progress.UIJob;

import uk.ac.diamond.scisoft.qstatmonitor.Activator;
import uk.ac.diamond.scisoft.qstatmonitor.api.QStatMonitorAPI;
import uk.ac.diamond.scisoft.qstatmonitor.preferences.QStatMonitorPreferenceConstants;
import uk.ac.diamond.scisoft.qstatmonitor.preferences.QStatMonitorPreferencePage;

public class QStatMonitorView extends ViewPart {
	public QStatMonitorView() {
	}

	public static final String ID = "uk.ac.diamond.scisoft.qstatmonitor.views.QStatMonitorView";

	// Table and plot views fill sashform with ratio 2:1
	private static final int[] SASH_FORM_RATIO = {2, 1};
	
	// Query string constants
	private static final String QSTAT_COMMAND = "qstat";
	private static final String RESOURCE_FLAG = "-l";
	private static final String ALL_USERS = "*";
	private static final String CURR_USER = "";

	/* UI Components */
	private Table table;
	private SashForm sashForm;
	private static final String[] TABLE_COL_LABELS = {"Job Number", "Priority",
			"Job Name", "Owner", "State", "Submission Time", "Queue Name", "Slots",
			"Tasks"};

	/* Table data */
	private ArrayList<String> jobNumberList = new ArrayList<String>();
	private ArrayList<String> priorityList = new ArrayList<String>();
	private ArrayList<String> jobNameList = new ArrayList<String>();
	private ArrayList<String> ownerList = new ArrayList<String>();
	private ArrayList<String> stateList = new ArrayList<String>();
	private ArrayList<String> submissionTimeList = new ArrayList<String>();
	private ArrayList<String> queueNameList = new ArrayList<String>();
	private ArrayList<String> slotsList = new ArrayList<String>();
	private ArrayList<String> tasksList = new ArrayList<String>();

	/* Plot data */
	private ArrayList<Double> timeList = new ArrayList<Double>();
	private ArrayList<Integer> suspendedList = new ArrayList<Integer>();
	private ArrayList<Integer> runningList = new ArrayList<Integer>();
	private ArrayList<Integer> queuedList = new ArrayList<Integer>();

	/* Preference values */
	private int refreshInterval; // in milliseconds
	private String qStatQuery;
	private String userArg;
	private boolean refreshOption;

	private boolean plotOption = false; // Plot not displayed by default

	// TODO: Shouldn't this variable be private
	long startTime = System.nanoTime();
	
	/* Actions */
	private Action refreshAction = new RefreshAction();
	private Action openPreferencesAction = new OpenPreferencesAction();
	private Action showPlotAction = new ShowPlotAction();

	/* Jobs */
	private Job fetchQStatInfoJob = new FetchQStatInfoJob();
	private UIJob fillTableJob = new FillTableJob();
	private UIJob plotDataJob = new PlotDataJob();

	private IPlottingSystem<Composite> plottingSystem;
	
	private IPropertyChangeListener preferenceListener = new IPropertyChangeListener() {
		public void propertyChange(PropertyChangeEvent event) {
			if (event.getProperty().equals(QStatMonitorPreferenceConstants.P_SLEEP)) {
				setRefreshInterval((float) event.getNewValue());
			} else if (event.getProperty().equals(
					QStatMonitorPreferenceConstants.P_REFRESH)) {
				refreshOption = getBooleanValue(event.getNewValue());
			} else if (event.getProperty()
					.equals(QStatMonitorPreferenceConstants.P_RESOURCES_ALL)) {
				if (getBooleanValue(event.getNewValue())) {
					qStatQuery = QSTAT_COMMAND;
				}
			} else if (event.getProperty()
					.equals(QStatMonitorPreferenceConstants.P_RESOURCE)) {
				String resource = String.valueOf(event.getNewValue());
				if (!resource.equals("")) {
					qStatQuery = QSTAT_COMMAND + " " + RESOURCE_FLAG + " " + resource;
				}
			} else if (event.getProperty().equals(QStatMonitorPreferenceConstants.P_USER_ALL)) {
				if (getBooleanValue(event.getNewValue())) {
					userArg = ALL_USERS;
				}
			} else if (event.getProperty().equals(QStatMonitorPreferenceConstants.P_USER_CURR)) {
				if (getBooleanValue(event.getNewValue())) {
					userArg = CURR_USER;
				}
			} else if (event.getProperty().equals(QStatMonitorPreferenceConstants.P_USER_CUST)) {
				if (getBooleanValue(event.getNewValue())) {
					userArg = String.valueOf(event.getNewValue());
				}
			} else if (event.getProperty().equals(QStatMonitorPreferenceConstants.P_USER)) {
				String user = String.valueOf(event.getNewValue());
				if (!user.equals("")) {
					userArg = user;
				}
			} else {
				// Should never reach here
				throw new IllegalArgumentException("Unrecognised property change event");
			}

			startQStatService();
		}
	};
	
	/**
	 * Converts object to boolean, used by PropertyChangeListener
	 * @param object
	 * @return
	 */
	private boolean getBooleanValue(Object object) {
		return Boolean.parseBoolean(String.valueOf(object));
	}

	private ISchedulingRule rule = new ISchedulingRule() {

		@Override
		public boolean isConflicting(ISchedulingRule rule) {
			return rule == this;
		}

		@Override
		public boolean contains(ISchedulingRule rule) {
			return rule == this;
		}
	};

	@Override
	public void init(IViewSite site) throws PartInitException {
		super.init(site);

		try {
			plottingSystem = PlottingFactory.getLightWeightPlottingSystem();
		} catch (Exception e) {
			e.printStackTrace();
		}

		// Check if plotting system successfully obtained
		if (plottingSystem == null) {
			// Plotting system unavailable
			// Display error message to user
			// Close plug-in
		}

		// TODO: Is this really needed?
		// Ensures jobs do not run concurrently
		fetchQStatInfoJob.setRule(rule);
		fillTableJob.setRule(rule);
		plotDataJob.setRule(rule);

		// On completion of FetchQStatInfoJob, schedules FillTableJob and plotDataJob
		fetchQStatInfoJob.addJobChangeListener(new JobChangeAdapter() {
			@Override
			public void done(IJobChangeEvent event) {
				super.done(event);
				fillTableJob.schedule();
				plotDataJob.schedule();
			}
		});

		// Initialise preference variables and establish callback on-change
		IPreferenceStore preferenceStore = Activator.getDefault().getPreferenceStore();
		initialisePreferenceVariables(preferenceStore);
		preferenceStore.addPropertyChangeListener(preferenceListener);

		startQStatService();
	}

	/**
	 * Starts the QStat service by scheduling the getQstatInfoJob
	 */
	private void startQStatService() {
		// Stops any on-going jobs
		cancelJob(fetchQStatInfoJob);
		cancelJob(plotDataJob);

		resetPlot();

		fetchQStatInfoJob.schedule();
	}

	@Override
	public void createPartControl(Composite parent) {
		setupActionBar();

		sashForm = new SashForm(parent, SWT.VERTICAL);
		setupTable(sashForm);

		plottingSystem.createPlotPart(sashForm, "QStat Monitor Plot Sash", null,
				PlotType.XY, null);

		// Set sashform layout
		sashForm.setWeights(SASH_FORM_RATIO);
		
		// Plot view not shown by default
		sashForm.setMaximizedControl(table);
	}

	/**
	 * Initialise preference variables with values from preference store
	 * 
	 * @param preferences
	 */
	private void initialisePreferenceVariables(IPreferenceStore preferences) {
		refreshOption = preferences.getBoolean(QStatMonitorPreferenceConstants.P_REFRESH);
		setRefreshInterval(preferences.getFloat(QStatMonitorPreferenceConstants.P_SLEEP));
		initialiseQStatQuery(preferences);
		initialiseUserArg(preferences);
	}
	
	/**
	 * Initialise qStatQuery with value from preference store
	 * @param preferences
	 */
	private void initialiseQStatQuery(IPreferenceStore preferences) {
		if (preferences.getBoolean(QStatMonitorPreferenceConstants.P_RESOURCES_ALL)) {
			qStatQuery = QSTAT_COMMAND;
		} else {
			qStatQuery = QSTAT_COMMAND + " " + RESOURCE_FLAG + " " + preferences.getString(QStatMonitorPreferenceConstants.P_RESOURCE);
		}
	}
	
	/**
	 * Initialise userArg with value from preference store
	 * @param preferences
	 */
	private void initialiseUserArg(IPreferenceStore preferences) {
		if (preferences.getBoolean(QStatMonitorPreferenceConstants.P_USER_ALL)) {
			userArg = ALL_USERS;
		} else if (preferences.getBoolean(QStatMonitorPreferenceConstants.P_USER_CURR)) {
			userArg = CURR_USER;
		} else {
			userArg = preferences.getString(QStatMonitorPreferenceConstants.P_USER);
		}
	}

	/**
	 * Creates action bar, instantiates actions and adds them to the action bar
	 */
	private void setupActionBar() {
		IActionBars bars = getViewSite().getActionBars();
		bars.getMenuManager().add(openPreferencesAction);
		bars.getToolBarManager().add(refreshAction);
		bars.getToolBarManager().add(showPlotAction);
	}

	/**
	 * Creates table in view and fills column headings
	 * 
	 * @param parent
	 */
	private void setupTable(Composite parent) {
		table = new Table(parent, SWT.MULTI | SWT.BORDER | SWT.FULL_SELECTION);
		table.setLinesVisible(true);
		table.setHeaderVisible(true);
		for (int i = 0; i < TABLE_COL_LABELS.length; i++) {
			TableColumn column = new TableColumn(table, SWT.NONE);
			column.setText(TABLE_COL_LABELS[i]);
			// Required to display columns (with labels) before data loaded
			column.pack();
		}
	}

	/**
	 * Resets the time and clears the plot lists
	 */
	public void resetPlot() {
		startTime = System.nanoTime();
		timeList.clear();
		suspendedList.clear();
		runningList.clear();
		queuedList.clear();
	}

	/**
	 * Setter for plotOption If option is true; opens the plot view
	 * 
	 * @param option
	 */
	public void setPlotOption() {
		this.plotOption = !this.plotOption;
		if (plotOption) {
			plotDataJob.schedule();
			sashForm.setMaximizedControl(null);
		} else {
			sashForm.setMaximizedControl(table);
			// Reset sashform layout to default
			sashForm.setWeights(SASH_FORM_RATIO);
		}
	}

	/**
	 * Gets the time in minutes since the time was last reset
	 * 
	 * @return
	 */
	private double getElapsedMinutes() {
		long estimatedTime = System.nanoTime() - startTime;
		return estimatedTime / 60000000000.0;
	}

	/**
	 * Blocks current thread until job has completely ended it execution
	 * 
	 * @param job
	 */
	private void cancelJob(Job job) {
		if (!job.cancel()) {
			try {
				job.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public void setFocus() {
		// Inherited abstract method - must be overridden
	}

	@Override
	public void dispose() {
		cancelJob(fetchQStatInfoJob);
		plottingSystem.dispose();
		super.dispose();
	}

	/**
	 * Converts given refresh interval in seconds to milliseconds
	 * 
	 * @param seconds
	 *            new value in seconds
	 */
	private void setRefreshInterval(float seconds) {
		refreshInterval = (int) Math.round(seconds * 1000);
	}

	/**
	 * Runs QStat query and stores retrieved items in corresponding arrays
	 */
	class FetchQStatInfoJob extends Job {

		// QStat data returned as array of lists
		@SuppressWarnings("unchecked")
		private final ArrayList<String>[] lists = (ArrayList<String>[]) new ArrayList[9];

		public FetchQStatInfoJob() {
			super("Fetching QStat Info");
		}

		@SuppressWarnings("deprecation")
		@Override
		protected IStatus run(IProgressMonitor monitor) {
			try {
				Thread fetchTableListsThread = new Thread() {
					public void run() {
						QStatMonitorAPI.getTableLists(qStatQuery, userArg, lists);
					}
				};

				fetchTableListsThread.start();

				// Loop until thread has retrieved table lists
				while (fetchTableListsThread.isAlive()) {
					if (monitor.isCanceled()) {
						// Temporary fix to immediately end job on cancel request
						fetchTableListsThread.stop();
						return Status.CANCEL_STATUS;
					}
				}

				assignTableData();
			} catch (StringIndexOutOfBoundsException e) {
				return Status.CANCEL_STATUS;
				// TODO: Not catching invalid queries
			} catch (NullPointerException npe) {
				displayDescInvalidQuery();
				return Status.CANCEL_STATUS;
			}

			// Reschedule job if automatic refresh enabled
			if (refreshOption) {
				schedule(refreshInterval);
			}

			return Status.OK_STATUS;
		}

		private void assignTableData() {
			jobNumberList = lists[0];
			priorityList = lists[1];
			jobNameList = lists[2];
			ownerList = lists[3];
			stateList = lists[4];
			submissionTimeList = lists[5];
			queueNameList = lists[6];
			slotsList = lists[7];
			tasksList = lists[8];
		}

		/**
		 * Updates content description to indicate query is invalid
		 */
		private void displayDescInvalidQuery() {
			// Ensures setContentDescription() executed on UI thread
			PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable() {
				@Override
				public void run() {
					setContentDescription("Invalid QStat query entered.");
				}
			});
		}

	}

	/**
	 * Removes all current items from the table, then adds the contents of the arrays to
	 * the relevant columns, then packs the table
	 */
	class FillTableJob extends UIJob {

		public FillTableJob() {
			super("Drawing Table");
		}

		@Override
		public IStatus runInUIThread(IProgressMonitor monitor) {
			try {
				table.removeAll();
				fillTable();
				packTable();
				updateContentDescription();
			} catch (SWTException e) {
				return Status.CANCEL_STATUS;
			}
			return Status.OK_STATUS;
		}

		private void fillTable() {
			for (int i = 0; i < jobNumberList.size(); i++) {
				TableItem item = new TableItem(table, SWT.NONE);
				item.setText(0, jobNumberList.get(i));
				item.setText(1, priorityList.get(i));
				item.setText(2, jobNameList.get(i));
				item.setText(3, ownerList.get(i));
				item.setText(4, stateList.get(i));
				item.setText(5, submissionTimeList.get(i));
				item.setText(6, queueNameList.get(i));
				item.setText(7, slotsList.get(i));
				item.setText(8, tasksList.get(i));
			}
		}

		/**
		 * Packs each column of the table so that all titles and items are visible without
		 * resizing
		 */
		private void packTable() {
			for (int i = 0; i < TABLE_COL_LABELS.length; i++) {
				table.getColumn(i).pack();
			}
		}

		/**
		 * Updates content description to show number of tasks displayed in the table
		 */
		private void updateContentDescription() {
			int numItems = jobNumberList.size();
			if (numItems == 1) {
				setContentDescription("Showing 1 task.");
			} else {
				setContentDescription("Showing " + numItems + " tasks.");
			}
		}

	}

	class PlotDataJob extends UIJob {

		public PlotDataJob() {
			super("Plotting Graph");
		}

		@Override
		public IStatus runInUIThread(IProgressMonitor monitor) {
			updatePlotLists();
			if (plotOption) {
				plotResults();
			}
			return Status.OK_STATUS;
		}

		/**
		 * Updates the plot lists
		 */
		private void updatePlotLists() {
			Double test = getElapsedMinutes();
			timeList.add(test);
			int suspended = 0;
			int running = 0;
			int queued = 0;
			for (int i = 0; i < jobNumberList.size(); i++) {
				if (stateList.get(i).equalsIgnoreCase("s")) {
					suspended += Integer.parseInt(slotsList.get(i));
				} else {
					if (stateList.get(i).equalsIgnoreCase("r")) {
						running += Integer.parseInt(slotsList.get(i));
					} else {
						if (stateList.get(i).contains("q")
								|| stateList.get(i).contains("Q")) {
							queued += Integer.parseInt(slotsList.get(i));
						}
					}
				}
			}
			suspendedList.add(suspended);
			runningList.add(running);
			queuedList.add(queued);
		}

		/**
		 * Plots the plot list values to the plot view
		 */
		private void plotResults() {
			if (!timeList.isEmpty()) {
				DoubleDataset timeDataset = DatasetFactory.createFromList(DoubleDataset.class, timeList);
				timeDataset.setName("Time (mins)");

				Dataset[] datasetArr = getDataToPlot();

				plotData(timeDataset, datasetArr);
			}
		}

		private Dataset getIntegerDataset(ArrayList<Integer> list, String name) {
			Dataset dataset = DatasetFactory.createFromList(list);
			dataset.setName(name);
			return dataset;
		}

		private Dataset[] getDataToPlot() {
			Dataset suspendedDataset = getIntegerDataset(suspendedList, "Suspended");
			Dataset queuedDataset = getIntegerDataset(queuedList, "Queued");
			Dataset runningDataset = getIntegerDataset(runningList, "Running");

			Dataset[] datasetArr = {suspendedDataset, queuedDataset, runningDataset};

			return datasetArr;
		}

		private void plotData(DoubleDataset timeDataset, Dataset[] datasetArr) {
			try {
				// Required in the situation where plotData() is called after
				// plottingSystem is disposed
				if (!plottingSystem.isDisposed()) {
					// Reset axes and clear plot
					plottingSystem.reset();
					// Quick fix to prevent horizontal line trace at y = 0 from being hidden
					plottingSystem.getSelectedYAxis().setRange(0, 0);
					plottingSystem.createPlot1D(timeDataset, Arrays.asList(datasetArr),
							null);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

	}

	class RefreshAction extends Action {
		RefreshAction() {
			setText("Refresh table");
			setImageDescriptor(Activator.getDefault().getWorkbench().getSharedImages()
					.getImageDescriptor(ISharedImages.IMG_ELCL_SYNCED));
		}

		public void run() {
			startQStatService();
		}
	}

	class OpenPreferencesAction extends Action {
		OpenPreferencesAction() {
			setText("Preferences");
		}

		public void run() {
			PreferenceDialog pref = PreferencesUtil.createPreferenceDialogOn(PlatformUI
					.getWorkbench().getActiveWorkbenchWindow().getShell(),
					QStatMonitorPreferencePage.ID, null, null);
			if (pref != null) {
				pref.open();
			}
		}
	}

	class ShowPlotAction extends Action {
		ShowPlotAction() {
			setText("Show Plot");
			setImageDescriptor(Activator.getDefault().getWorkbench().getSharedImages()
					.getImageDescriptor(ISharedImages.IMG_DEF_VIEW));
		}

		public void run() {
			setPlotOption();
		}
	}

}