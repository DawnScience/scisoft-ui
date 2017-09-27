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

import org.dawb.common.ui.util.EclipseUtils;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.IReusableEditor;
import org.eclipse.ui.IWorkbenchPartSite;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.part.EditorPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.rcp.explorers.AbstractExplorer;
import uk.ac.diamond.scisoft.analysis.rcp.explorers.ImageExplorer;

public class ImageEditor extends EditorPart implements IReusableEditor {

	private static final Logger logger = LoggerFactory.getLogger(ImageEditor.class);
	
	private ImageExplorer imgxp;
	private File file;

	public ImageEditor() {
	}

	@Override
	public void doSave(IProgressMonitor monitor) {

	}

	@Override
	public void doSaveAs() {

	}

	@Override
	public void init(IEditorSite site, IEditorInput input) throws PartInitException {
		
		setSite(site);
		setInput(input);
		try {
			createFile();
		} catch (Exception e) {
			logger.error("Cannot create file!", e);
		}
		setPartName(input.getName());
	}

	private void createFile() throws Exception{
		file = EclipseUtils.getFile(getEditorInput());
		if (file == null || !file.exists()) {
			throw new Exception("Input is not a file or file does not exist");
		} else if (!file.canRead()) {
			throw new Exception("Cannot read file (are permissions correct?)");
		}
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
		imgxp = new ImageExplorer(parent, site, null);
		try {
			imgxp.loadFileAndDisplay(file.getPath(), null);
		} catch (Exception e) {
			return;
		}

		site.setSelectionProvider(imgxp);
	}

	@Override
	public void setInput(IEditorInput input) {
		super.setInput(input);
		try {
			createFile();
		} catch (Exception e) {
			logger.error("Cannot create file!", e);
		}

		if (imgxp == null)
			return;

		try {
			imgxp.loadFileAndDisplay(file.getPath(), null);
		} catch (Exception e) {
			return;
		}
	}
	
	@Override
	public void setFocus() {
		imgxp.setFocus();
	}

	@Override
	public void dispose() {
		if (imgxp != null && !imgxp.isDisposed())
			imgxp.dispose();
		super.dispose();
	}

	/**
	 * This editor uses an SRimage explorer
	 * @return explorer class
	 */
	public static Class<? extends AbstractExplorer> getExplorerClass() {
		return ImageExplorer.class;
	}
}
