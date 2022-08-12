/*
 * Copyright (c) 2012 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.analysis.rcp.views;

import gda.observable.IObserver;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

import org.dawb.common.ui.menu.CheckableActionGroup;
import org.dawb.common.ui.menu.MenuAction;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.dawnsci.analysis.api.io.IDataHolder;
import org.eclipse.dawnsci.plotting.api.IPlottingSystem;
import org.eclipse.dawnsci.plotting.api.PlottingFactory;
import org.eclipse.dawnsci.plotting.api.histogram.IPaletteService;
import org.eclipse.dawnsci.plotting.api.trace.IPaletteTrace;
import org.eclipse.dawnsci.plotting.api.trace.ITrace;
import org.eclipse.january.dataset.IDataset;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferenceDialog;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Scale;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.IViewSite;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.PreferencesUtil;
import org.eclipse.ui.part.ViewPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.PlotServer;
import uk.ac.diamond.scisoft.analysis.PlotServerProvider;
import uk.ac.diamond.scisoft.analysis.io.LoaderFactory;
import uk.ac.diamond.scisoft.analysis.plotserver.DataBean;
import uk.ac.diamond.scisoft.analysis.plotserver.DatasetWithAxisInformation;
import uk.ac.diamond.scisoft.analysis.plotserver.FileOperationBean;
import uk.ac.diamond.scisoft.analysis.plotserver.GuiBean;
import uk.ac.diamond.scisoft.analysis.plotserver.GuiParameters;
import uk.ac.diamond.scisoft.analysis.plotserver.GuiPlotMode;
import uk.ac.diamond.scisoft.analysis.plotserver.RMIPlotServer;
import uk.ac.diamond.scisoft.analysis.rcp.AnalysisRCPActivator;
import uk.ac.diamond.scisoft.analysis.rcp.imagegrid.ImagePlayBack;
import uk.ac.diamond.scisoft.analysis.rcp.imagegrid.PlotServerSWTImageGrid;
import uk.ac.diamond.scisoft.analysis.rcp.plotting.actions.ImageExplorerDirectoryChooseAction;
import uk.ac.diamond.scisoft.analysis.rcp.preference.ImageExplorerPreferencePage;
import uk.ac.diamond.scisoft.analysis.rcp.preference.PreferenceConstants;
import uk.ac.diamond.scisoft.analysis.rcp.util.CommandExecutor;
import uk.ac.diamond.scisoft.imagegrid.gridentry.GridImageEntry;
import uk.ac.diamond.scisoft.imagegrid.gridentry.SWTGridEntry;

public class ImageExplorerView extends ViewPart implements IObserver, SelectionListener {

	// Adding in some logging to help with getting this running
	transient private static final Logger logger = LoggerFactory.getLogger(ImageExplorerView.class);

	public static final String ID = "uk.ac.diamond.scisoft.analysis.rcp.views.ImageExplorerView";

	public static final Object FOLDER_UPDATE_MARKER = new Object();

	private String plotViewName = "Image Explorer";
	private PlotServer plotServer = null;
	private Canvas canvas;
	private Group compHUD = null;
	private Composite locationRow;
	private Button btnHistoryBack;
	private Button btnHistoryForw;
	private Button btnPlay;
	private Button btnRewind;
	private Button btnStop;
	private Button btnForward;
	private Button btnPlayLoop;
	private Scale sldProgress;
	private Combo cmbDirectoryLocation;
	private Image imgPlay;
	private Image imgStill;
	private Label lblLocation;
	private PlotServerSWTImageGrid imageGrid;
	private GuiBean guiBean = null;
	private UUID plotID = null;
	private final Semaphore locker = new Semaphore(1);
	private Action[] imgExtensions;
	private MenuAction colorMenu;
	private List<String> filter = new ArrayList<String>();
	private List<String> filesToLoad = null;
	private List<String> history = new ArrayList<String>();
	private int historyPointer = -1;
	private String currentDir = null;
	private ImagePlayBack playback = null;
	private ExecutorService execSvc = null;
	private boolean isLive = false;
	private boolean liveActive = false;
	private boolean monActive = false;
	private int curPosition = -1;

	/**
	 * The last dir path is saved so that when the user
	 * uses this view again, it initiates to the last known folder.
	 */
	private String dirPath;

	private boolean firstBack = false;

	private boolean isDisposed = true;

	private Composite parent;

	private boolean stopLoading = false;

	private Job updateDirectory;

	public ImageExplorerView() {
		plotServer = PlotServerProvider.getPlotServer();
		plotID = UUID.randomUUID();
		logger.info("Image explorer view uuid: {}", plotID);
		execSvc = Executors.newFixedThreadPool(2);

		//initialise job
		updateDirectory = new Job("Update directory") {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				ImageExplorerDirectoryChooseAction.setImageFolder(dirPath, filter);
				return Status.OK_STATUS;
			}
		};
	}

	/**
	 * Get a list of plot views from extension register
	 * 
	 * @return list of views
	 */
	public static List<String> getRegisteredViews() {
		List<String> plotViews = new LinkedList<String>();

		IExtensionRegistry registry = Platform.getExtensionRegistry();

		// Add the default point first, this is where the double click action will go
		IExtensionPoint point = registry.getExtensionPoint("uk.ac.diamond.scisoft.analysis.rcp.ExplorerViewDefault");
		IExtension[] extensions = point.getExtensions();
		for (int i = 0; i < extensions.length; i++) {
			IConfigurationElement[] elements = extensions[i].getConfigurationElements();
			for (int i1 = 0; i1 < elements.length; i1++) {
				if (elements[i1].getName().equals("ViewDefaultRegister")) {
					if (!plotViews.contains(elements[i1].getAttribute("ViewName")))
						plotViews.add(elements[i1].getAttribute("ViewName"));
				}
			}
		}

		// now add all the other contributions to the list
		point = registry.getExtensionPoint("uk.ac.diamond.scisoft.analysis.rcp.ExplorerViewRegister");
		extensions = point.getExtensions();
		for (int i = 0; i < extensions.length; i++) {
			IConfigurationElement[] elements = extensions[i].getConfigurationElements();
			for (int i1 = 0; i1 < elements.length; i1++) {
				if (elements[i1].getName().equals("ViewRegister")) {
					if (!plotViews.contains(elements[i1].getAttribute("ViewName")))
						plotViews.add(elements[i1].getAttribute("ViewName"));
				}
			}
		}
		return plotViews;
	}

	@Override
	public void createPartControl(Composite parent) {
		plotViewName = getViewSite().getRegisteredName();
		plotServer.addIObserver(this);
		setPartName(plotViewName);
		this.parent = parent;
		parent.setLayout(new GridLayout(1, true));

		locationRow = new Composite(parent, SWT.NONE);
		locationRow.setLayout(new GridLayout(4, false));
		{
			btnHistoryBack = new Button(locationRow, SWT.LEFT);
			btnHistoryBack.setEnabled(false);
			btnHistoryBack.setToolTipText("Go back in history");
			Image backImage = AnalysisRCPActivator.getImageDescriptor("icons/arrow_left.png").createImage();
			btnHistoryBack.setImage(backImage);
			btnHistoryBack.addDisposeListener(e -> backImage.dispose());
			btnHistoryBack.addSelectionListener(this);
			btnHistoryForw = new Button(locationRow, SWT.LEFT);
			btnHistoryForw.setEnabled(false);
			btnHistoryForw.setToolTipText("Go forward in history");
			btnHistoryForw.addSelectionListener(this);
			Image forwardImage = AnalysisRCPActivator.getImageDescriptor("icons/arrow_right.png").createImage();
			btnHistoryForw.setImage(forwardImage);
			btnHistoryForw.addDisposeListener(e -> forwardImage.dispose());
			lblLocation = new Label(locationRow, SWT.LEFT);
			lblLocation.setText("Location:");

			cmbDirectoryLocation = new Combo(locationRow, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
			cmbDirectoryLocation.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
			cmbDirectoryLocation.addSelectionListener(this);
		}
		locationRow.setLayoutData(new GridData(SWT.FILL, SWT.NONE, false, false));

		canvas = new Canvas(parent, SWT.DOUBLE_BUFFERED | SWT.V_SCROLL | SWT.H_SCROLL);
		canvas.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		compHUD = new Group(parent, SWT.NONE);
		compHUD.setLayout(new GridLayout(6, false));
		compHUD.setText("Image Playback");
		{
			btnRewind = new Button(compHUD, SWT.PUSH);
			Image rewindImage = AnalysisRCPActivator.getImageDescriptor("icons/control_start.png").createImage();
			btnRewind.setImage(rewindImage);
			btnRewind.addDisposeListener(e -> rewindImage.dispose());
			btnRewind.addSelectionListener(this);
			btnRewind.setToolTipText("Rewind");
			btnPlay = new Button(compHUD, SWT.TOGGLE);
			imgPlay = AnalysisRCPActivator.getImageDescriptor("icons/control_play.png").createImage();
			imgStill = AnalysisRCPActivator.getImageDescriptor("icons/control_pause.png").createImage();
			btnPlay.setImage(imgPlay);
			btnPlay.addSelectionListener(this);
			btnPlay.setToolTipText("Play/Pause");
			btnStop = new Button(compHUD, SWT.PUSH);
			Image stopImage = AnalysisRCPActivator.getImageDescriptor("icons/control_stop.png").createImage();
			btnStop.setImage(stopImage);
			btnStop.addDisposeListener(e -> stopImage.dispose());
			btnStop.addSelectionListener(this);
			btnStop.setToolTipText("Stop playback");
			btnForward = new Button(compHUD, SWT.PUSH);
			Image endImage = AnalysisRCPActivator.getImageDescriptor("icons/control_end.png").createImage();
			btnForward.setImage(endImage);
			btnForward.addDisposeListener(e -> endImage.dispose());
			btnForward.addSelectionListener(this);
			btnForward.setToolTipText("Forward");
			btnPlayLoop = new Button(compHUD, SWT.TOGGLE);
			Image playImage = AnalysisRCPActivator.getImageDescriptor("icons/control_repeat.png").createImage();
			btnPlayLoop.setImage(playImage);
			btnPlayLoop.addDisposeListener(e -> playImage.dispose());
			btnPlayLoop.setToolTipText("Playback loop (On/off)");
			btnPlayLoop.addSelectionListener(this);
			sldProgress = new Scale(compHUD, SWT.HORIZONTAL);
			sldProgress.setPageIncrement(1);
			sldProgress.addSelectionListener(this);
			sldProgress.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		}
		compHUD.setLayoutData(new GridData(SWT.FILL, SWT.NONE, true, false));

		imageGrid = new PlotServerSWTImageGrid(canvas, plotViewName);
		imageGrid.setThumbnailSize(getPreferenceImageSize());
		retainStateFromServer();

		if (dirPath != null && dirPath.length() > 0) {
			dirPath.trim();
			updateDirectory.setUser(true);
			updateDirectory.setPriority(Job.DECORATE);
			updateDirectory.schedule(1000);
			cmbDirectoryLocation.setText(dirPath);
			currentDir = dirPath;
		}
		playback = new ImagePlayBack(parent, getPreferencePlaybackView(), getViewSite().getPage(), sldProgress,
				getPreferenceTimeDelay(), getPreferencePlaybackRate());

		isDisposed = false;

		// listen to preference changes to update the Live plot play back view and colour map
		AnalysisRCPActivator.getDefault().getPreferenceStore().addPropertyChangeListener(new IPropertyChangeListener() {
			@Override
			public void propertyChange(PropertyChangeEvent event) {
				if (event.getProperty().equals(PreferenceConstants.IMAGEEXPLORER_PLAYBACKVIEW)) {
					playback.setPlotView(getPreferencePlaybackView());
				}
				if (event.getProperty().equals(PreferenceConstants.IMAGEEXPLORER_COLOURMAP)
						|| event.getProperty().equals(PreferenceConstants.IMAGEEXPLORER_IMAGESIZE)) {
					List<GridImageEntry> images = imageGrid.getListOfEntries();
					imageGrid.setThumbnailSize(getPreferenceImageSize());
					String colourScheme = getPreferenceColourMapChoice();
					for (GridImageEntry entry : images) {
						IDataset data = entry.getData();
						SWTGridEntry gridEntry = new SWTGridEntry(entry.getFilename(), data, canvas, 
								colourScheme, getPreferenceAutoContrastLo(), getPreferenceAutoContrastHi());
						imageGrid.addEntry(gridEntry, entry.getGridColumnPos(), entry.getGridRowPos());
					}
					// Check the colour menu accordingly
					IAction currentColour = colorMenu.findAction(colourScheme);
					if (currentColour != null)
						currentColour.setChecked(true);
				}
			}
		});
	}

	@Override
	public void init(IViewSite site, IMemento memento) throws PartInitException {
		init(site);
		String storedExtensions = "";
		if (memento != null) { // Open dir path
			this.dirPath = memento.getString("DIR");
			storedExtensions = memento.getString("FILTERS");
			if (storedExtensions == null)
				storedExtensions = "";
		}
		// Filter Extensions
		imgExtensions = new Action[ImageExplorerDirectoryChooseAction.LISTOFSUFFIX.length];
		MenuManager filterMenu = new MenuManager("File Filters");
		for (int i = 0; i < ImageExplorerDirectoryChooseAction.LISTOFSUFFIX.length; i++) {
			final int number = i;
			imgExtensions[i] = new Action("", IAction.AS_CHECK_BOX) {
				@Override
				public void run() {
					if (this.isChecked()) {
						filter.remove(ImageExplorerDirectoryChooseAction.LISTOFSUFFIX[number]);
					} else {
						filter.add(ImageExplorerDirectoryChooseAction.LISTOFSUFFIX[number]);
					}
					// reload the directory
					updateDirectory.setUser(true);
					updateDirectory.setPriority(Job.DECORATE);
					updateDirectory.schedule(1000);
				}
			};
			imgExtensions[i].setText(ImageExplorerDirectoryChooseAction.LISTOFSUFFIX[i]);
			imgExtensions[i].setDescription("Filter " + ImageExplorerDirectoryChooseAction.LISTOFSUFFIX[i] + " on/off");
			if (storedExtensions.contains(ImageExplorerDirectoryChooseAction.LISTOFSUFFIX[i])) {
				imgExtensions[i].setChecked(false);
				filter.add(ImageExplorerDirectoryChooseAction.LISTOFSUFFIX[i]);
			} else
				imgExtensions[i].setChecked(true);
			filterMenu.add(imgExtensions[i]);
		}

		site.getActionBars().getMenuManager().add(filterMenu);

		//color submenus actions
		final IPaletteService pservice = PlatformUI.getWorkbench().getService(IPaletteService.class);
		final Collection<String> names = pservice.getColorSchemes();
		String schemeName = getPreferenceColourMapChoice();

		colorMenu = new MenuAction("Color");
		colorMenu.setId(getClass().getName()+colorMenu.getText());
		colorMenu.setImageDescriptor(AnalysisRCPActivator.getImageDescriptor("icons/color_wheel.png"));

		final Map<String, IAction> paletteActions = new HashMap<String, IAction>(11);
		CheckableActionGroup group      = new CheckableActionGroup();
		for (final String paletteName : names) {
			final Action action = new Action(paletteName, IAction.AS_CHECK_BOX) {
				@Override
				public void run() {
					try {
						setPreferenceColourMapChoice(paletteName);
						
						IPlottingSystem<Composite> system = PlottingFactory.getPlottingSystem(getPreferencePlaybackView());
						if (system != null) {
							final Collection<ITrace> traces = system.getTraces();
							if (traces!=null) for (ITrace trace: traces) {
								if (trace instanceof IPaletteTrace) {
									IPaletteTrace paletteTrace = (IPaletteTrace) trace;
									paletteTrace.setPalette(paletteName);
								}
							}
						}
						
					} catch (Exception ne) {
						logger.error("Cannot create palette data!", ne);
					}
				}
			};
			action.setId(paletteName);
			group.add(action);
			colorMenu.add(action);
			action.setChecked(paletteName.equals(schemeName));
			paletteActions.put(paletteName, action);
		}
		colorMenu.setToolTipText("Histogram");
		site.getActionBars().getMenuManager().add(colorMenu);

		// ImageExplorer preferences
		final Action openPreferences = new Action("Image Explorer Preferences...") {
			@Override
			public void run() {
				PreferenceDialog pref = PreferencesUtil.createPreferenceDialogOn(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), ImageExplorerPreferencePage.ID, null, null);
				if (pref != null) pref.open();
			}
		};
		site.getActionBars().getMenuManager().add(openPreferences);
	}

	@Override
	public void saveState(IMemento memento) {
		if (dirPath != null && memento != null) {// save dir path
			memento.putString("DIR", dirPath);
		}
		// save filter settings from last session
		if (memento != null) {
			String storeExtensions = "";
			Iterator<String> filterIter = filter.iterator();
			while (filterIter.hasNext()) {
				if (storeExtensions.length() == 0)
					storeExtensions += filterIter.next();
				else
					storeExtensions += "|" + filterIter.next();
			}
			if (storeExtensions.length() > 0)
				memento.putString("FILTERS", storeExtensions);
		}
	}

	public String getPlotViewName() {
		return plotViewName;
	}

	public void setDirPath(final String filepath) {
		this.dirPath = filepath;
	}

	public String getDirPath() {
		return dirPath;
	}

	@Override
	public void setFocus() {

	}

	public void setOverviewMode(boolean overview) {
		if (imageGrid != null)
			imageGrid.setOverviewMode(overview);
	}

	public boolean getOverviewMode() {
		if (imageGrid != null)
			return imageGrid.getOverviewMode();
		return false;
	}

	public ArrayList<String> getSelection() {
		return imageGrid.getSelection();
	}

	private void processNewFile(DataBean bean) {
		try {
			locker.acquire();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		playback.addFile((String) bean.getGuiParameters().get(GuiParameters.FILENAME));
		String colourScheme = getPreferenceColourMapChoice();

		SWTGridEntry entry = new SWTGridEntry((String) bean.getGuiParameters().get(GuiParameters.FILENAME), bean.getData().get(0), canvas,
				colourScheme, getPreferenceAutoContrastLo(), getPreferenceAutoContrastHi());
		Integer xPos = (Integer) bean.getGuiParameters().get(GuiParameters.IMAGEGRIDXPOS);
		Integer yPos = (Integer) bean.getGuiParameters().get(GuiParameters.IMAGEGRIDYPOS);
		if (xPos != null && yPos != null)
			imageGrid.addEntry(entry, xPos, yPos);
		else
			imageGrid.addEntry(entry);
		imageGrid.setThumbnailSize(getPreferenceImageSize());
		locker.release();
		if (liveActive)
			playback.moveToLast();
	}

	private void processNewGrid(DataBean bean) {
		final Integer[] gridDims = (Integer[]) bean.getGuiParameters().get(GuiParameters.IMAGEGRIDSIZE);

		if (gridDims != null && gridDims.length > 0) {
			try {
				locker.acquire();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			canvas.getDisplay().asyncExec(new Runnable() {
				@Override
				public void run() {
					imageGrid.dispose();
					if (gridDims.length == 1)
						imageGrid = new PlotServerSWTImageGrid(gridDims[0], gridDims[0], canvas, plotViewName);
					else
						imageGrid = new PlotServerSWTImageGrid(gridDims[1], gridDims[0], canvas, plotViewName);
					imageGrid.setThumbnailSize(getPreferenceImageSize());
					locker.release();
					canvas.redraw();
				}
			});
		}
		cleanUpOnServer();
	}

	private void processUpdate(DataBean bean) {
		if (bean == null)
			return;
		if (bean.getGuiParameters() == null)
			return;
		UUID id = (UUID) bean.getGuiParameters().get(GuiParameters.PLOTID);
		if (id == null || plotID.compareTo(id) != 0) { // filter out own beans
			if (guiBean == null)
				guiBean = bean.getGuiParameters().copy(); // cache a local copy
			else
				guiBean.merge(bean.getGuiParameters()); // or merge it

			if (bean.getGuiParameters().containsKey(GuiParameters.FILEOPERATION))
				return;

			if (bean.getGuiParameters().containsKey(GuiParameters.PLOTMODE)) {
				GuiPlotMode plotMode = (GuiPlotMode) bean.getGuiParameters().get(GuiParameters.PLOTMODE);
				if (plotMode.equals(GuiPlotMode.IMGEXPL)) {
					if (bean.getGuiParameters().containsKey(GuiParameters.FILENAME)) {
						if (filesToLoad == null)
							filesToLoad = new ArrayList<String>();
						filesToLoad.add((String) bean.getGuiParameters().get(GuiParameters.FILENAME));
						processNewFile(bean);
					} else if (bean.getGuiParameters().containsKey(GuiParameters.IMAGEGRIDSIZE)) {
						if (filesToLoad == null)
							filesToLoad = new ArrayList<String>();
						else
							filesToLoad.clear();
						btnPlay.getDisplay().asyncExec(new Runnable() {
							@Override
							public void run() {
								resetPlaying(true);
							}
						});
						processNewGrid(bean);
					}
				}
			} else if (bean.getGuiParameters().containsKey(GuiParameters.IMAGEGRIDLIVEVIEW)) {
				String directory = (String) bean.getGuiParameters().get(GuiParameters.IMAGEGRIDLIVEVIEW);
				spawnLoadJob(directory);
				currentDir = directory;
				isLive = true;
				liveActive = true;
			}
		}
	}

	private void processClientLocalUpdate() {
		DataBean bean = new DataBean();
		int gridDim = (int) Math.ceil(Math.sqrt(filesToLoad.size()));
		if (imageGrid != null)
			imageGrid.dispose();

		imageGrid = new PlotServerSWTImageGrid(gridDim, gridDim, canvas, plotViewName);
		imageGrid.setThumbnailSize(getPreferenceImageSize());
		Iterator<String> iter = filesToLoad.iterator();
		while (iter.hasNext()) {
			String filename = iter.next();
			try {
				IDataHolder holder = LoaderFactory.getData(filename);
				IDataset data = holder.getDataset(0);
				List<DatasetWithAxisInformation> datalist = new ArrayList<DatasetWithAxisInformation>();
				DatasetWithAxisInformation d = new DatasetWithAxisInformation();
				if (data != null) {
					d.setData(data);
					datalist.add(d);
					bean.setData(datalist);
				}
			} catch (Exception e) {
				logger.error("Error loading data with filename " + filename, e);
			}
			bean.putGuiParameter(GuiParameters.FILENAME, filename);
			processNewFile(bean);
		}
		if (liveActive) {
			sldProgress.getDisplay().asyncExec(new Runnable() {
				@Override
				public void run() {
					playback.moveToLast();
				}
			});
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public void update(Object source, Object changeCode) {
		if (stopLoading)
			return;

		if (source == ImageExplorerView.FOLDER_UPDATE_MARKER) { // Folder Update
			if (changeCode instanceof List<?>) {
				playback.clearPlayback();
				btnPlay.getDisplay().asyncExec(new Runnable() {
					@Override
					public void run() {
						resetPlaying(false);
						if (isLive) {
							playback.setDelay(50);
							playback.setStepping(1);
							playback.start();
							btnPlay.setSelection(true);
							btnPlay.setImage(imgStill);
							execSvc.execute(playback);
							if (!monActive)
								CommandExecutor.executeCommand(getViewSite(),
										"uk.ac.diamond.scisoft.analysis.rcp.MontorDirectoryAction");
							cmbDirectoryLocation.setText(currentDir);
							isLive = false;
						}
					}
				});
				filesToLoad = (List<String>) changeCode;
				processClientLocalUpdate();
			}
		} else {
			if (source instanceof RMIPlotServer && changeCode instanceof String) {
				String viewName = (String) changeCode;
				if (!viewName.startsWith("Image Explorer"))
					return;
				RMIPlotServer server = (RMIPlotServer) source;
				try {
					String[] names = server.getGuiNames();
					final DataBean bean = server.getData(names[0]);
					// when imagegrid size is set it means we need to reset the grid
					if(bean != null && bean.getGuiParameters().get(GuiParameters.IMAGEGRIDSIZE) != null) {
						Display.getDefault().syncExec(new Runnable() {
							@Override
							public void run() {
								Integer[] gridDim = (Integer[])bean.getGuiParameters().get(GuiParameters.IMAGEGRIDSIZE);
								if (imageGrid != null)
									imageGrid.dispose();
								imageGrid = new PlotServerSWTImageGrid(gridDim[0], gridDim[1], canvas, plotViewName);
								imageGrid.setThumbnailSize(getPreferenceImageSize());
							}
						});
					}
					if (bean != null) {
						if (bean.getData().isEmpty()) {
							// We try to load the data from the filename if data not in the databean
							String filename = (String) bean.getGuiParameters().get(GuiParameters.FILENAME);
							if (filename == null)
								return;
							List<DatasetWithAxisInformation> data = new ArrayList<>();
							DatasetWithAxisInformation dwai = new DatasetWithAxisInformation();
							IDataHolder holder = LoaderFactory.getData(filename);
							IDataset loadedData = holder.getDataset(0);
							dwai.setData(loadedData);
							data.add(dwai);
							bean.setData(data);
						}
						processUpdate(bean);
					} else {
						throw new Exception("Databean is null. This image grid use is not supported.");
					}
				} catch (Exception e) {
					logger.error("Error updating data from server: ", e);
				}
			}
		}
	}

	private void cleanUpOnServer() {
		FileOperationBean fopBean = new FileOperationBean(FileOperationBean.DELETEGRIDIMGTEMPDIR);
		GuiBean bean = new GuiBean();
		bean.put(GuiParameters.FILEOPERATION, fopBean);
		// plotServer.deleteIObserver(this);
		try {
			plotServer.updateGui(plotViewName, bean);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@SuppressWarnings("unchecked")
	private void retainStateFromServer() {
		try {
			GuiBean guiBean = plotServer.getGuiState(plotViewName);
			if (guiBean != null && guiBean.containsKey(GuiParameters.IMAGEGRIDSTORE)) {
				ArrayList<GridImageEntry> entries = (ArrayList<GridImageEntry>) guiBean
						.get(GuiParameters.IMAGEGRIDSTORE);
				Iterator<GridImageEntry> iter = entries.iterator();
				String colourScheme = getPreferenceColourMapChoice();
				while (iter.hasNext()) {
					GridImageEntry entry = iter.next();
					SWTGridEntry gridEntry = new SWTGridEntry(entry.getFilename(), null, canvas, colourScheme,
							getPreferenceAutoContrastLo(), getPreferenceAutoContrastHi());
					imageGrid.addEntry(gridEntry, entry.getGridColumnPos(), entry.getGridRowPos());
				}
			}
		} catch (Exception e) {
			logger.warn("Problem with getting GUI data from plot server");
		}
	}

	private GuiBean saveStateToServer() {
		plotServer.deleteIObserver(this);
		if (imageGrid == null)
			return null;
		ArrayList<GridImageEntry> entries = imageGrid.getListOfEntries();
		GuiBean bean = new GuiBean();
		bean.put(GuiParameters.PLOTMODE, GuiPlotMode.IMGEXPL);
		bean.put(GuiParameters.IMAGEGRIDSTORE, entries);
		return bean;
	}

	@Override
	public void dispose() {
		isDisposed = true;
		GuiBean finalBean = saveStateToServer();

		if (imageGrid != null) {
			imageGrid.dispose();
		}
		if (execSvc!=null) {
			execSvc.shutdown();
		}
		if (finalBean != null) {
			try {
				plotServer.updateGui(plotViewName, finalBean);
			} catch (Exception e) {
				logger.warn("Problem with updating plot server with GUI data");
				e.printStackTrace();
			}
		}

		playback.dispose();
	}

	/**
	 * @return true if it has been disposed
	 */
	public boolean isDisposed() {
		return isDisposed;
	}

	/**
	 * Push gui information back to plot server
	 * @param key 
	 * @param value 
	 */
	public void pushGUIUpdate(GuiParameters key, Serializable value) {
		if (guiBean == null) {
			try {
				guiBean = plotServer.getGuiState(plotViewName);
			} catch (Exception e) {
				logger.warn("Problem with getting GUI data from plot server");
			}
			if (guiBean == null)
				guiBean = new GuiBean();
		}

		guiBean.put(GuiParameters.PLOTID, plotID); // put plotID in bean

		guiBean.put(key, value);

		try {
			logger.info("Pushing bean to server: {}", guiBean);
			plotServer.updateGui(plotViewName, guiBean);
		} catch (Exception e) {
			logger.warn("Problem with updating plot server with GUI data");
			e.printStackTrace();
		}
	}

	/**
	 * Push a list of filenames back to server
	 * @param selectedFiles
	 */
	public void pushSelectedFiles(ArrayList<String> selectedFiles) {
		pushGUIUpdate(GuiParameters.FILESELECTEDLIST, selectedFiles);
	}

	private void spawnLoadJob(final String newDirectory) {
		final Job updateDirectory = new Job("Update directory") {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				if (!ImageExplorerDirectoryChooseAction.setImageFolder(newDirectory,filter)) {
					return new Status(IStatus.ERROR,"ERROR","Couldn't load directory it either doesn't exist or has no files");
				}
				return Status.OK_STATUS;
			}
		};
		updateDirectory.setUser(true);
		updateDirectory.setPriority(Job.DECORATE);
		updateDirectory.schedule(100);
	}

	private void addToHistory() {
		if (currentDir != null)
			history.add(0, new String(currentDir));
	}

	private void resetHistory() {
		historyPointer = 0;
		firstBack = true;
		btnHistoryBack.setEnabled(true);
		btnHistoryForw.setEnabled(false);
	}

	@Override
	public void widgetDefaultSelected(SelectionEvent e) {
		if (e.getSource().equals(cmbDirectoryLocation)) {
			final String newDirectory = cmbDirectoryLocation.getText();
			if (newDirectory != null && newDirectory.length() > 0) {
				newDirectory.trim();
				addToHistory();
				resetHistory();
				String[] oldDirs = cmbDirectoryLocation.getItems();
				boolean foundEntry = false;
				for (int i = 0; i < oldDirs.length; i++) {
					if (newDirectory.equals(oldDirs[i])) {
						foundEntry = true;
						break;
					}
				}
				if (!foundEntry) {
					if (cmbDirectoryLocation.getItemCount() > 0)
						cmbDirectoryLocation.add(newDirectory, 0);
					else
						cmbDirectoryLocation.add(newDirectory);
				}
				resetPlaying(true);
				spawnLoadJob(newDirectory);
				currentDir = newDirectory;
			}
		}
	}

	public void setLocationText(String location) {
		if (cmbDirectoryLocation != null && !cmbDirectoryLocation.isDisposed())
			cmbDirectoryLocation.setText(location);
	}

	public List<String> getExtensionsFilter() {
		return filter;
	}

	private void resetPlaying(boolean reset) {
		playback.stop();
		if (reset)
			playback.clearPlayback();
		btnPlay.setSelection(false);
		btnPlay.setImage(imgPlay);
		sldProgress.setSelection(0);
		curPosition = -1;
	}

	@Override
	public void widgetSelected(SelectionEvent e) {
		if (e.getSource().equals(cmbDirectoryLocation)) {
			final String newDirectory = cmbDirectoryLocation.getItem(cmbDirectoryLocation.getSelectionIndex());
			if (newDirectory != null && newDirectory.length() > 0) {
				addToHistory();
				resetHistory();
				resetPlaying(true);
				spawnLoadJob(newDirectory);
				currentDir = newDirectory;
			}
		} else if (e.getSource().equals(btnHistoryBack)) {
			String newDir = history.get(historyPointer);
			btnHistoryForw.setEnabled(true);
			cmbDirectoryLocation.setText(newDir);
			resetPlaying(true);
			spawnLoadJob(newDir);
			historyPointer++;
			if (historyPointer >= history.size()) {
				btnHistoryBack.setEnabled(false);
				historyPointer = history.size() - 1;
			}
			if (firstBack) {
				addToHistory();
				firstBack = false;
				historyPointer++;
			}
		} else if (e.getSource().equals(btnHistoryForw)) {
			historyPointer--;
			String newDir = history.get(historyPointer);
			resetPlaying(true);
			spawnLoadJob(newDir);
			cmbDirectoryLocation.setText(newDir);
			btnHistoryBack.setEnabled(true);
			if (historyPointer <= 0) {
				btnHistoryForw.setEnabled(false);
				historyPointer = 1;
			}
		} else if (e.getSource().equals(btnPlay)) {
			if (btnPlay.getSelection()) {
				boolean isPaused = playback.isPaused();
				btnPlay.setImage(imgStill);
				playback.start();
				if (!isPaused) {
					playback.setSelection(getSelection());
					playback.setDelay(getPreferenceTimeDelay());
					playback.setStepping(getPreferencePlaybackRate());
					execSvc.execute(playback);
				}
			} else {
				playback.pause();
				btnPlay.setImage(imgPlay);
			}
		} else if (e.getSource().equals(btnStop)) {
			sldProgress.setSelection(0);
			curPosition = -1;
			btnPlay.setSelection(false);
			btnPlay.setImage(imgPlay);
			playback.stop();
		} else if (e.getSource().equals(btnRewind)) {
			sldProgress.setSelection(0);
			curPosition = -1;
			playback.rewind();
		} else if (e.getSource().equals(btnForward)) {
			sldProgress.setSelection(sldProgress.getMaximum());
			playback.forward();
		} else if (e.getSource().equals(sldProgress)) {
			int p = sldProgress.getSelection();
			if (p != curPosition) {
				playback.setPlayPos(p);
				curPosition = p;
			}
		} else if (e.getSource().equals(btnPlayLoop)) {
			playback.setAutoRewind(btnPlayLoop.getSelection());
		}
	}

	public void setLocationBarVisible(boolean visible) {
		locationRow.setVisible(visible);
		((GridData) locationRow.getLayoutData()).exclude = !visible;
		parent.layout();
	}

	public void setImageGridVisible(boolean visible) {
		canvas.setVisible(visible);
		((GridData) canvas.getLayoutData()).exclude = !visible;
		parent.layout();
	}

	public void stopLoading(boolean stop) {
		if (stop) {
			imageGrid.stopLoading();
		}
		stopLoading  = stop;
	}

	public boolean isStopped() {
		return stopLoading;
	}

	public List<String> getLoadedFiles() {
		return filesToLoad;
	}

	private void setPreferenceColourMapChoice(String colorMapChoice) {
		IPreferenceStore preferenceStore = AnalysisRCPActivator.getDefault().getPreferenceStore();
		preferenceStore.setValue(PreferenceConstants.IMAGEEXPLORER_COLOURMAP, colorMapChoice);
	}

	private String getPreferenceColourMapChoice() {
		IPreferenceStore preferenceStore = AnalysisRCPActivator.getDefault().getPreferenceStore();
		return preferenceStore.isDefault(PreferenceConstants.IMAGEEXPLORER_COLOURMAP)
				? preferenceStore.getDefaultString(PreferenceConstants.IMAGEEXPLORER_COLOURMAP)
				: preferenceStore.getString(PreferenceConstants.IMAGEEXPLORER_COLOURMAP);
	}

	private int getPreferenceTimeDelay() {
		IPreferenceStore preferenceStore = AnalysisRCPActivator.getDefault().getPreferenceStore();
		return preferenceStore.isDefault(PreferenceConstants.IMAGEEXPLORER_TIMEDELAYBETWEENIMAGES)
				? preferenceStore.getDefaultInt(PreferenceConstants.IMAGEEXPLORER_TIMEDELAYBETWEENIMAGES)
				: preferenceStore.getInt(PreferenceConstants.IMAGEEXPLORER_TIMEDELAYBETWEENIMAGES);
	}

	private int getPreferencePlaybackRate() {
		IPreferenceStore preferenceStore = AnalysisRCPActivator.getDefault().getPreferenceStore();
		return preferenceStore.isDefault(PreferenceConstants.IMAGEEXPLORER_PLAYBACKRATE)
				? preferenceStore.getDefaultInt(PreferenceConstants.IMAGEEXPLORER_PLAYBACKRATE)
				: preferenceStore.getInt(PreferenceConstants.IMAGEEXPLORER_PLAYBACKRATE);
	}

	private double getPreferenceAutoContrastLo() {
		IPreferenceStore preferenceStore = AnalysisRCPActivator.getDefault().getPreferenceStore();
		int v = preferenceStore.isDefault(PreferenceConstants.IMAGEEXPLORER_AUTOCONTRAST_LOTHRESHOLD)
				? preferenceStore.getDefaultInt(PreferenceConstants.IMAGEEXPLORER_AUTOCONTRAST_LOTHRESHOLD)
				: preferenceStore.getInt(PreferenceConstants.IMAGEEXPLORER_AUTOCONTRAST_LOTHRESHOLD);
		return v/100.0;
	}

	private double getPreferenceAutoContrastHi() {
		IPreferenceStore preferenceStore = AnalysisRCPActivator.getDefault().getPreferenceStore();
		int v = preferenceStore.isDefault(PreferenceConstants.IMAGEEXPLORER_AUTOCONTRAST_HITHRESHOLD)
				? preferenceStore.getDefaultInt(PreferenceConstants.IMAGEEXPLORER_AUTOCONTRAST_HITHRESHOLD)
				: preferenceStore.getInt(PreferenceConstants.IMAGEEXPLORER_AUTOCONTRAST_HITHRESHOLD);
				return v/100.0;
	}

	/**
	 * @return plot name that playback uses
	 */
	public String getPreferencePlaybackView() {
		IPreferenceStore preferenceStore = AnalysisRCPActivator.getDefault().getPreferenceStore();
		return preferenceStore.isDefault(PreferenceConstants.IMAGEEXPLORER_PLAYBACKVIEW)
				? preferenceStore.getDefaultString(PreferenceConstants.IMAGEEXPLORER_PLAYBACKVIEW)
				: preferenceStore.getString(PreferenceConstants.IMAGEEXPLORER_PLAYBACKVIEW);
	}

	/**
	 * @return thumbnail size
	 */
	private int getPreferenceImageSize() {
		IPreferenceStore preferenceStore = AnalysisRCPActivator.getDefault().getPreferenceStore();
		int size = preferenceStore.isDefault(PreferenceConstants.IMAGEEXPLORER_IMAGESIZE)
				? preferenceStore.getDefaultInt(PreferenceConstants.IMAGEEXPLORER_IMAGESIZE)
				: preferenceStore.getInt(PreferenceConstants.IMAGEEXPLORER_IMAGESIZE);
		return size;
	}

	public void setMonitorActive(boolean monitorActive) {
		monActive = monitorActive;
	}

	/**
	 * Play button action of ImageExplorer
	 */
	public void play(){
		boolean isPaused = playback.isPaused();
		btnPlay.setImage(imgStill);
		playback.start();
		if (!isPaused) {
			playback.setSelection(getSelection());
			playback.setDelay(getPreferenceTimeDelay());
			playback.setStepping(getPreferencePlaybackRate());
			execSvc.execute(playback);
		}
	}
}
