/*-
 * Copyright (c) 2016 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package uk.ac.diamond.scisoft.arpes.calibration.wizards;

import java.io.File;
import java.lang.reflect.InvocationTargetException;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.dawnsci.analysis.api.message.DataMessageComponent;
import org.eclipse.emf.common.ui.dialogs.WorkspaceResourceDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.fieldassist.ContentProposalAdapter;
import org.eclipse.jface.fieldassist.TextContentAdapter;
import org.eclipse.richbeans.widgets.content.FileContentProposalProvider;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.PlatformUI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.arpes.calibration.Activator;
import uk.ac.diamond.scisoft.arpes.calibration.utils.ARPESCalibrationConstants;
import uk.ac.diamond.scisoft.arpes.calibration.utils.CalibrationSaver;

public class GoldCalibrationPageFive extends CalibrationWizardPage {
	
	private static final Logger logger = LoggerFactory.getLogger(GoldCalibrationPageFive.class);
	private DataMessageComponent calibrationData;
	private String path;
	private Text txtPath;
	private CalibrationSaver saveWithProgress;

	public GoldCalibrationPageFive(DataMessageComponent calibrationData) {
		super("Save calibrated data");
		setTitle("Save Calibration");
		setDescription("Select the location and name of file where to save calibrated data");
		this.calibrationData = calibrationData;
		saveWithProgress = new CalibrationSaver(calibrationData);
	}

	@Override
	public void createControl(Composite parent) {
		Composite container = new Composite(parent, SWT.NULL);
		container.setLayout(new GridLayout(3, false));

		Label txtLabel = new Label(container, SWT.NULL);
		txtLabel.setText("Calibration file path and name to save:");
		txtLabel.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, false, false, 3, 1));

		txtPath = new Text(container, SWT.BORDER);
		txtPath.setEditable(false);
		// set default name
		String filepath = (String) calibrationData.getUserObject(ARPESCalibrationConstants.FILE_PATH);
// TODO this seems to fail on windows, and it would be nice to have a better default.
//		String[] tmp = filepath.split(File.separator);
//		String name = tmp.length > 0 ? tmp[tmp.length - 1] : filepath;
//		String savedname = name.split("\\.")[0] + "_calib.nxs";
//		String folder = filepath.substring(0, filepath.lastIndexOf(File.separator) + 1);
		path = filepath+"_calib.nxs";
		FileContentProposalProvider prov = new FileContentProposalProvider();
		ContentProposalAdapter ad = new ContentProposalAdapter(txtPath, new TextContentAdapter(), prov, null, null);
		ad.setProposalAcceptanceStyle(ContentProposalAdapter.PROPOSAL_REPLACE);
		if (path != null) {
			txtPath.setText(path);
			calibrationData.addUserObject(ARPESCalibrationConstants.SAVE_PATH, path);
		}
		GridData gridData = new GridData(SWT.LEFT, SWT.FILL, false, false, 2, 1);
		gridData.widthHint = 650;
		txtPath.setLayoutData(gridData);
		txtPath.addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent e) {
				path = txtPath.getText();
			}
		});

//		Button resourceButton = new Button(container, SWT.PUSH);
//		resourceButton.setLayoutData(new GridData(SWT.LEFT, SWT.FILL, false, false, 1, 1));
//		resourceButton.setImage(Activator.getImageDescriptor("icons/Project-data.png").createImage());
//		resourceButton.setToolTipText("Browse to file inside a project");
//		resourceButton.addSelectionListener(new SelectionAdapter() {
//			@Override
//			public void widgetSelected(SelectionEvent e) {
//				handleResourceBrowse();
//			}
//		});
//		resourceButton.setEnabled(true);

		Button fileButton = new Button(container, SWT.PUSH);
		fileButton.setLayoutData(new GridData(SWT.LEFT, SWT.FILL, false, false, 1, 1));
		fileButton.setImage(Activator.getImageAndAddDisposeListener(fileButton, "icons/folder.png"));
		fileButton.setToolTipText("Browse to an external file");
		fileButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				handleFileBrowse(path);
			}
		});

		final Button overwrite = new Button(container, SWT.CHECK);
		overwrite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1));
		overwrite.setToolTipText("Overwrite existing file(s) of the same name during processing.");
		overwrite.setText("Overwrite file if it already exists");
		overwrite.setSelection(true);
		overwrite.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				calibrationData.addUserObject(ARPESCalibrationConstants.OVERWRITE, overwrite.getSelection());
			}
		});
		calibrationData.addUserObject(ARPESCalibrationConstants.OVERWRITE, overwrite.getSelection());

		setControl(container);

		setPageComplete(false);
		getShell().pack();
	}

	@SuppressWarnings("unused")
	private void handleResourceBrowse() {
		IResource[] res = null;
//		if (newFile) {
			final IResource cur = getIResource();
			final IPath path = cur != null ? cur.getFullPath() : null;
			IFile file = WorkspaceResourceDialog.openNewFile(PlatformUI.getWorkbench().getDisplay().getActiveShell(),
					"File location", "Please choose a location.", path, null);
			res = file != null ? new IResource[] { file } : null;
//		} else {
//			res = WorkspaceResourceDialog.openFileSelection(PlatformUI.getWorkbench().getDisplay().getActiveShell(),
//					"File location", "Please choose a location.", false, new Object[] { getIResource() }, null);
//		}

		if (res != null && res.length > 0) {
			this.path = res[0].getFullPath().toOSString();
			txtPath.setText(this.path);
		}
		calibrationData.addUserObject(ARPESCalibrationConstants.SAVE_PATH, getAbsoluteFilePath());
	}

	private IResource getIResource() {
		IResource res = null;
		if (path != null) {
			res = ResourcesPlugin.getWorkspace().getRoot().findMember(path);
		}
		if (res == null && path != null) {
			final String workspace = ResourcesPlugin.getWorkspace().getRoot().getLocation().toOSString();
			if (path.startsWith(workspace)) {
				String relPath = path.substring(workspace.length());
				res = ResourcesPlugin.getWorkspace().getRoot().findMember(relPath);
			}
		}
		return res;
	}

	private void handleFileBrowse(String filePath) {
		String path = null;
		final FileDialog dialog = new FileDialog(Display.getDefault().getActiveShell(), SWT.SAVE);
		dialog.setText("Choose file");
		if (filePath != null) {
			final File file = new File(filePath);
			if (file.exists()) {
				if (file.isDirectory()) {
					dialog.setFilterPath(file.getAbsolutePath());
				} else {
					dialog.setFilterPath(file.getParent());
					dialog.setFileName(file.getName());
				}
			}

		}
		String savefolder = filePath.substring(0, filePath.lastIndexOf(File.separator));
		dialog.setFilterPath(savefolder);
		String savename = filePath.substring(filePath.lastIndexOf(File.separator) + 1);
		dialog.setFileName(savename);
		path = dialog.open();
		if (path != null) {
			this.path = path;
			txtPath.setText(this.path);
			calibrationData.addUserObject(ARPESCalibrationConstants.SAVE_PATH, path);
		}
	}

	/**
	 * 
	 * @return the output file path
	 */
	private String getAbsoluteFilePath() {
		try {
			IResource res = ResourcesPlugin.getWorkspace().getRoot().findMember(path);
			if (res != null)
				return res.getLocation().toOSString();
			String savepath = path.substring(0, path.lastIndexOf(File.separator));
			final File file = new File(savepath);
			String parDir = file.getParent();
			IContainer folder = (IContainer) ResourcesPlugin.getWorkspace().getRoot().findMember(parDir);
			if (folder != null) {
				final IFile newFile = folder.getFile(new Path(file.getName()));
				if (newFile.exists())
					newFile.touch(null);
				return newFile.getLocation().toOSString();
			}
			return path;
		} catch (Throwable ignored) {
			return null;
		}
	}

	@Override
	public boolean runProcess() throws InterruptedException {
		System.out.println("Page 5");
		try {
			getContainer().run(true, true, saveWithProgress);
		} catch (InvocationTargetException e) {
			logger.error(e.getMessage());
			return false;
		} catch (InterruptedException e) {
			MessageDialog dialog = new MessageDialog(getShell(), "Saving process interrupted", null, e.getMessage(),
					MessageDialog.ERROR, new String[] { "OK" }, 0);
			dialog.open();
			return false;
		}
		return true;
	}

	@Override
	public int getPageNumber() {
		return 5;
	}

}