/*
 * Copyright (c) 2012 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.analysis.rcp.editors;

import java.io.File;

import org.dawb.common.ui.selection.SelectedTreeItemInfo;
import org.dawb.common.ui.selection.SelectionUtils;
import org.dawb.common.ui.util.EclipseUtils;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.dawnsci.analysis.api.io.IDataHolder;
import org.eclipse.dawnsci.analysis.api.io.IFileLoader;
import org.eclipse.dawnsci.analysis.api.tree.NodeLink;
import org.eclipse.dawnsci.analysis.api.tree.Tree;
import org.eclipse.jface.dialogs.IPageChangedListener;
import org.eclipse.jface.dialogs.PageChangedEvent;
import org.eclipse.jface.viewers.IContentProvider;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ITreeSelection;
import org.eclipse.jface.viewers.TreePath;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Cursor;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.IReusableEditor;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.ISelectionService;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchPartSite;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.part.EditorPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.rcp.explorers.AbstractExplorer;
import uk.ac.diamond.scisoft.analysis.rcp.hdf5.HDF5TreeExplorer;

public class HDF5TreeEditor extends EditorPart implements IPageChangedListener, IReusableEditor {

	private HDF5TreeExplorer hdfxp;
	private File file;
	private Tree tree;

	private ISelectionListener selectionListener;

	public HDF5TreeEditor() {
	}

	/**
	 * 
	 */
	public static final String ID = "uk.ac.diamond.scisoft.analysis.rcp.editors.HDF5TreeEditor"; //$NON-NLS-1$

	private static final Logger logger = LoggerFactory.getLogger(HDF5TreeEditor.class);

	@Override
	public void init(IEditorSite site, IEditorInput input) throws PartInitException {
		file = EclipseUtils.getFile(input instanceof HDF5Input ? ((HDF5Input) input).getInput() : input);
		if (file == null || !file.exists()) {
			logger.warn("File does not exist: {}", input.getName());
			throw new PartInitException("Input is not a file or file does not exist");
		} else if (!file.canRead()) {
			logger.warn("Cannot read file: {}", input.getName());
			throw new PartInitException("Cannot read file (are permissions correct?)");
		}

		setSite(site);
		setInput(input);
	}

	/**
	 * H5MultiEditor requires this to be public
	 */
	@Override
	public void setInput(IEditorInput input) {
		super.setInput(input);
		tree = input instanceof HDF5Input ? ((HDF5Input) input).getTree() : null;
	}

	protected boolean loadHDF5Tree() {
		if (tree != null && hdfxp != null) {
			hdfxp.setTree(tree);
			return true;
		}

		final String fileName = file.getAbsolutePath();
		try {
			if (hdfxp != null) {
				hdfxp.loadFileAndDisplay(fileName, null);
				return true;
			}
		} catch (Exception e) {
			if (e.getCause() != null)
				logger.warn("Could not load NeXus file {}: {}", fileName, e.getCause().getMessage());
			else
				logger.warn("Could not load NeXus file {}: {}", fileName, e.getMessage());
		}
		return false;
	}

	@Override
	public boolean isDirty() {
		return false;
	}

	@Override
	public boolean isSaveAsAllowed() {
		return false;
	}

	@Override
	public void createPartControl(Composite parent) {
		IWorkbenchPartSite site = getSite();
		hdfxp = new HDF5TreeExplorer(parent, site, null);
		if (!loadHDF5Tree()) {
			return;
		}
		site.setSelectionProvider(hdfxp);
		setPartName(file.getName());
//		registerSelectionListener();
	}

	@Override
	public void pageChanged(PageChangedEvent event) {
		if (event.getSelectedPage() == this) { // Just selected this page
			loadHDF5Tree();
		}
	}

	@Override
	public void doSave(IProgressMonitor monitor) {
		// do nothing
	}

	@Override
	public void doSaveAs() {
		// do nothing
	}

	@Override
	public void setFocus() {
		hdfxp.setFocus();
	}

	public void expandAll() {
		hdfxp.expandAll();
	}

	@Override
	public void dispose() {
		file = null;
		tree = null;
//		unregisterSelectionListener();
		if (hdfxp != null && !hdfxp.isDisposed())
			hdfxp.dispose();
		super.dispose();
	}

	public HDF5TreeExplorer getHDF5TreeExplorer() {
		return hdfxp;
	}
	
	public Tree getHDF5Tree() {
		if (tree != null)
			return tree;
		if (hdfxp == null)
			return null;
		tree = hdfxp.getTree();
		return tree;
	}

	/**
	 * This editor uses a HDF5 explorer
	 * @return explorer class
	 */
	public static Class<? extends AbstractExplorer> getExplorerClass() {
		return HDF5TreeExplorer.class;
	}

	/* Setting up of the editor as a Selection listener on the navigator selectionProvider */
	@SuppressWarnings("unused")
	private void registerSelectionListener() {

		final ISelectionService selectionService = getSite().getWorkbenchWindow().getSelectionService();
		if (selectionService == null)
			throw new IllegalStateException("Cannot acquire the selection service!");
		selectionListener = new ISelectionListener() {

			@Override
			public void selectionChanged(final IWorkbenchPart part, final ISelection selection) {
				if (selection instanceof ITreeSelection) {
					ITreeSelection tSelection = (ITreeSelection) selection;
					SelectedTreeItemInfo[] results = SelectionUtils.parseAsTreeSelection(tSelection);
					if (results.length > 0) {
						String filePath = results[0].getFile();
						File f = new File(filePath);
						String name = f.getName();
						//update only the relevant hdf5editor
						if (name.equals(getSite().getPart().getTitle())) {
								update(part, results[0].getNode(), (NodeLink) results[0].getItem(), tSelection);
						}
					}

				}
			}
		};
		selectionService.addSelectionListener(selectionListener);
	}

	@SuppressWarnings("unused")
	private void unregisterSelectionListener() {
		if (selectionListener == null)
			return;

		final ISelectionService selectionService = getSite().getWorkbenchWindow().getSelectionService();
		if (selectionService == null)
			throw new IllegalStateException("Cannot acquire the selection service!");

		selectionService.removeSelectionListener(selectionListener);
	}

	public void update(final IWorkbenchPart original, final String path, final NodeLink link, ITreeSelection tSelection) {

		// Make Display to wait until current focus event is finish, and then execute new focus event
		Display.getDefault().asyncExec(new Runnable() {
			@Override
			public void run() {
				while (Display.getDefault().readAndDispatch()) {
					//wait for events to finish before continue
				}
				hdfxp.forceFocus();
			}
		});
		//EclipseUtils.getActivePage().activate(this);

		// selection of hdf5 tree element no working
		final Cursor cursor = hdfxp.getCursor();
		Cursor tempCursor = hdfxp.getDisplay().getSystemCursor(SWT.CURSOR_WAIT);
		if (tempCursor != null)
			hdfxp.setCursor(tempCursor);

		try {
			TreePath navTreePath1 = tSelection.getPaths()[0];
			hdfxp.expandToLevel(navTreePath1, 2);
			hdfxp.setSelection(tSelection);
			
			hdfxp.selectHDF5Node(path, link);
		} catch (Exception e) {
			logger.error("Error processing selection: {}", e.getMessage());
		} finally {
			if (tempCursor != null)
				hdfxp.setCursor(cursor);
		}

		// new focus event
		EclipseUtils.getActivePage().activate(original);
	}

	/**
	 * The Value view uses adapters to get an IContentProvider for its content.
	 * 
	 * This is used on the workflow perspective to show selected value in the tree.
	 */
	@SuppressWarnings("unchecked")
	@Override
	public Object getAdapter(@SuppressWarnings("rawtypes") final Class clazz) {
		
		if (clazz == IContentProvider.class) {
			return new HDF5ValuePage();
		}
		
		return super.getAdapter(clazz);
	}

	public void startUpdateThread(IDataHolder holder, IFileLoader loader) {
		getHDF5TreeExplorer().startUpdateThread(holder, loader);
	}
}
