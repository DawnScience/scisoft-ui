/*
 * Copyright (c) 2012 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.analysis.rcp.views;

import java.io.File;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.UUID;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.dawnsci.analysis.api.io.ScanFileHolderException;
import org.eclipse.dawnsci.analysis.api.tree.Tree;
import org.eclipse.dawnsci.plotting.api.ProgressMonitorWrapper;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.ui.IWorkbenchPartSite;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.ui.progress.IProgressService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gda.observable.IObserver;
import uk.ac.diamond.scisoft.analysis.PlotServer;
import uk.ac.diamond.scisoft.analysis.PlotServerProvider;
import uk.ac.diamond.scisoft.analysis.plotserver.DataBean;
import uk.ac.diamond.scisoft.analysis.plotserver.GuiBean;
import uk.ac.diamond.scisoft.analysis.plotserver.GuiParameters;
import uk.ac.diamond.scisoft.analysis.plotserver.GuiUpdate;
import uk.ac.diamond.scisoft.analysis.rcp.hdf5.HDF5Selection;
import uk.ac.diamond.scisoft.analysis.rcp.hdf5.HDF5TreeExplorer;

public class HDF5TreeView extends ViewPart implements IObserver {
	HDF5TreeExplorer hdfxp;
	Display display;
	FileDialog fileDialog = null;

	// Variables for the plotServer
	private PlotServer plotServer;
	private GuiBean guiBean;

	private static final String NAME = "hdf5TreeViewer";

	private UUID plotID;
	private Object filename;

	/**
	 * 
	 */
	public static final String ID = "uk.ac.diamond.scisoft.analysis.rcp.views.HDF5TreeView"; //$NON-NLS-1$

	private static final Logger logger = LoggerFactory.getLogger(HDF5TreeView.class);

	public HDF5TreeView() {
		plotID = UUID.randomUUID();
		plotServer = PlotServerProvider.getPlotServer();
		// generate the bean that will contain all the information about this GUI
		guiBean = new GuiBean();
		guiBean.put(GuiParameters.PLOTID, plotID); // put plotID in bean
		// publish this to the server
		try {
			plotServer.updateGui(NAME, guiBean);
		} catch (Exception e) {
			logger.error("Problem pushing initial GUI bean to plot server");
			e.printStackTrace();
		}

	}

	@Override
	public void createPartControl(Composite parent) {
		plotServer.addIObserver(this);
		display = parent.getDisplay();
		IWorkbenchPartSite site = getSite();
		hdfxp = new HDF5TreeExplorer(parent, site, null);
		site.setSelectionProvider(hdfxp);

		// set up the help context
		PlatformUI.getWorkbench().getHelpSystem().setHelp(hdfxp, "uk.ac.diamond.scisoft.analysis.rcp.hdf5View");

		createActions();

		initializeToolBar();
		initializeMenu();
		getTreeFromServer();
		hdfxp.addSelectionChangedListener(new ISelectionChangedListener() {
			
			@Override
			public void selectionChanged(SelectionChangedEvent e) {
				if (e.getSource() == hdfxp) {
					ISelection s = e.getSelection();
					if (s instanceof HDF5Selection) {
						HDF5Selection h = (HDF5Selection) s;
						// notify other clients that a node has been selected
						pushGUIUpdate(GuiParameters.TREENODEPATH, h.getFileName()
								+ HDF5TreeExplorer.HDF5FILENAME_NODEPATH_SEPARATOR + h.getNode());
					}
				}
			}
		});
	}

	/**
	 * Load file given by path into view
	 * @param path
	 */
	public void loadTree(final String path) {
		IProgressService service = getSite().getService(IProgressService.class);

		try {
			// Changed to cancellable as sometimes loading the tree takes ages and you
			// did not mean to choose the file.
			service.run(true, true, new IRunnableWithProgress() {

				@Override
				public void run(final IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
					try {
						loadTree(path, monitor);
					} catch (ScanFileHolderException e) {
						logger.error("Could not load HDF5 file: {}", e);
					} catch (Exception ee) {
						logger.error("Problem with loader: is the library path set correctly?", ee);
					} finally {
						monitor.done();
					}
				}
			});
		} catch (Exception e) {
			logger.error("Could not open HDF5 file", e);
		}
	}

	private void loadTree(final String path, IProgressMonitor monitor) throws Exception {
		monitor.beginTask("Opening HDF5 file " + path, 10);
		monitor.worked(1);
		if (monitor.isCanceled()) return;

		long start = System.nanoTime();
		hdfxp.loadFileAndDisplay(path, new ProgressMonitorWrapper(monitor));
		if (hdfxp.getTree() != null) {
			display.syncExec(new Runnable() {
				@Override
				public void run() {
					setPartName((new File(path)).getName());
				}
			});
		}
		logger.info("Setting tree took {}s", (System.nanoTime() - start)*1e-9);
	}

	/**
	 * call to bring up a file dialog
	 */
	public void loadTreeUsingFileDialog() {
		if (fileDialog == null) {
			fileDialog = new FileDialog(getSite().getShell(), SWT.OPEN);
		}

		String [] filterNames = new String [] {"HDF5 files", "All Files (*)"};
		String [] filterExtensions = new String [] {"*.nxs;*.h5;*.hdf5", "*"};
		fileDialog.setFilterNames(filterNames);
		fileDialog.setFilterExtensions(filterExtensions);
		final String path = fileDialog.open();

		if (path != null) {
			loadTree(path);
		}
	}
	

	@Override
	public void setFocus() {
		hdfxp.setFocus();
	}

	@Override
	public void dispose() {
		plotServer.deleteIObserver(this);
		super.dispose();
	}

	/**
	 * Create the actions
	 */
	private void createActions() {
//		IToolBarManager tbManager = getViewSite().getActionBars().getToolBarManager();
	}

	/**
	 * Initialize the toolbar
	 */
	private void initializeToolBar() {
		@SuppressWarnings("unused")
		IToolBarManager toolbarManager = getViewSite().getActionBars().getToolBarManager();
	}

	/**
	 * Initialize the menu
	 */
	private void initializeMenu() {
		@SuppressWarnings("unused")
		IMenuManager menuManager = getViewSite().getActionBars().getMenuManager();
	}

	public void expandAll() {
		hdfxp.expandAll();
	}

	@Override
	public void update(Object theObserved, Object changeCode) {
		if (changeCode instanceof GuiUpdate) {
			GuiUpdate gu = (GuiUpdate) changeCode;

			if (gu.getGuiName().contains(NAME)) {
				guiBean = gu.getGuiData();
				syncGuiToBean();
				GuiBean bean = gu.getGuiData();
				UUID id = (UUID) bean.get(GuiParameters.PLOTID);

				if (id == null || plotID.compareTo(id) != 0) { // filter out own beans
					if (guiBean == null)
						guiBean = bean.copy(); // cache a local copy
					else
						guiBean.merge(bean);   // or merge it

					logger.debug("Processing update received from {}: {}", theObserved, changeCode);

					// now update GUI
					processGUIUpdate(bean);
				}
			}
		} else if (changeCode instanceof String) {
			String guiName = (String) changeCode;
			if (guiName.equals(NAME)) {
				getTreeFromServer();
			}
		}

	}

	/**
	 * Push gui information back to plot server
	 * @param key 
	 * @param value 
	 */
	public void pushGUIUpdate(GuiParameters key, Serializable value) {
		if (guiBean == null) {
			try {
				guiBean = plotServer.getGuiState(NAME);
			} catch( Exception e) {
				logger.error("Problem with getting GUI data from plot server");
			}
			if (guiBean == null)
				guiBean = new GuiBean();
		}

		guiBean.put(GuiParameters.PLOTID, plotID); // put plotID in bean

		guiBean.put(key, value);

		try {
			plotServer.updateGui(NAME, guiBean);
		} catch (Exception e) {
			logger.error("Problem with updating plot server with GUI data");
			e.printStackTrace();
		}
	}

	private void getTreeFromServer() {
		DataBean dataBean;

		try {
			logger.debug("Pulling data to client");
			long start = System.nanoTime();
			dataBean = plotServer.getData(NAME);
			start = System.nanoTime() - start;
			logger.debug("Data pushed to client: {} in {} s", dataBean, String.format("%.3g", start*1e-9));
			syncTreeToBean(dataBean);
		} catch (Exception e) {
			logger.error("Problem pushing data to plot server");
			e.printStackTrace();
		}
	}

	private void syncGuiToBean() {
		if (display != null)
			display.asyncExec(new Runnable() {
				@Override
				public void run() {
					display.update();
				}
			});
	}

	private void syncTreeToBean(DataBean bean) {
		if (bean == null) {
			logger.warn("Plot server has no info for NTV");
			return;
		}

		// grab first metadata tree
		List<Tree> htList = bean.getTrees();
		if (htList == null) {
			logger.warn("Plot server did not push a list of trees");
			return;
		}
		if (htList.size() == 0) {
			logger.warn("Plot server pushed an empty list of trees");
			return;
		}
		hdfxp.setTree(htList.get(0));
	}

	/**
	 * @param bean
	 */
	private void processGUIUpdate(GuiBean bean) {
		if (bean.containsKey(GuiParameters.TREENODEPATH)) {
			String fullname = (String) bean.get(GuiParameters.TREENODEPATH);
			int i = fullname.indexOf(HDF5TreeExplorer.HDF5FILENAME_NODEPATH_SEPARATOR);

			if (i > 0) {
				String file = fullname.substring(0, i);

				if (filename.equals(file)) {
					String path = fullname.substring(i + 1);
					hdfxp.selectHDF5Node(path);
				}
//				logger.debug("File from selected node does not match: {}", file);
			}
//			logger.warn("Could not process update of selected node: {}", fullname);
		}
	}

	public void collapseAll() {
		if (hdfxp != null && hdfxp.getTableTree().getViewer() instanceof TreeViewer)
			((TreeViewer)hdfxp.getTableTree().getViewer()).collapseAll();
	}
}
