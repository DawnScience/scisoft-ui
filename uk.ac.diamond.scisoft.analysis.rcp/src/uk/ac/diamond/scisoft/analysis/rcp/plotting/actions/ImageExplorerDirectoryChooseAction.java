/*-
 * Copyright © 2009 Diamond Light Source Ltd.
 *
 * This file is part of GDA.
 *
 * GDA is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License version 3 as published by the Free
 * Software Foundation.
 *
 * GDA is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along
 * with GDA. If not, see <http://www.gnu.org/licenses/>.
 */

package uk.ac.diamond.scisoft.analysis.rcp.plotting.actions;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.rcp.util.FileComparator;
import uk.ac.diamond.scisoft.analysis.rcp.util.FileCompareMode;
import uk.ac.diamond.scisoft.analysis.rcp.views.ImageExplorerView;
import uk.ac.gda.common.rcp.util.EclipseUtils;

/**
 *
 */
public class ImageExplorerDirectoryChooseAction extends AbstractHandler {

	public static final String LISTOFSUFFIX[] = {".png",".jpg",".tif",".tiff",".mar",".cbf",".dat",
        ".img",".raw",".mccd",".cif",".imgcif",".jpeg"};
	
	private static final Logger logger = LoggerFactory.getLogger(ImageExplorerDirectoryChooseAction.class);
	
	public static ArrayList<File> filterImages(File[] files, List<String> excludeList) {
		ArrayList<File> listOfImages = new ArrayList<File>();
		for (int i = 0; i < files.length; i++) {
			for (int suffix = 0; suffix < LISTOFSUFFIX.length; suffix++) {
				if (files[i].getAbsolutePath().toLowerCase().endsWith(LISTOFSUFFIX[suffix])) {
					if (excludeList != null) {
						Iterator<String> iter = excludeList.iterator();
						boolean ignoreFile = false;
						while (iter.hasNext()) {
							if (files[i].getAbsolutePath().toLowerCase().endsWith(iter.next())) {
								ignoreFile = true;
								break;
							}
						}
						if (!ignoreFile)
							listOfImages.add(files[i]);
					} else
						listOfImages.add(files[i]);
				}
			}
		}
		return listOfImages;
	}
	

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		
		ImageExplorerView view = (ImageExplorerView)PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().findView(ImageExplorerView.ID);
		if (view != null) {
			DirectoryDialog dirDialog = new DirectoryDialog(PlatformUI.getWorkbench().getDisplay().getActiveShell(), SWT.OPEN);
			dirDialog.setFilterPath(view.getDirPath());
			final String filepath = dirDialog.open();
			if (filepath != null) {
				setImageFolder(filepath,view.getExtensionsFilter());
				view.setLocationText(filepath);
			}		
		} else {
			logger.info("Couldn't find view to load for");
		}

		return Boolean.FALSE;
	}

	public static boolean setImageFolder(String filepath) {

		return setImageFolder(filepath,null);
	}

	public static boolean setImageFolder(String filepath, List<String> excludeFileEndings) {
		
		final IWorkbenchPage    page = EclipseUtils.getPage();
		if (page == null) return false;
		boolean returnValue = true;
		final ImageExplorerView view = (ImageExplorerView)page.findView(ImageExplorerView.ID);
		if (view!=null) {
			view.setDirPath(filepath);
			java.io.File file = new java.io.File(filepath);
			java.io.File[] files = file.listFiles();
			if (files != null && files.length > 0) {
				ArrayList<File> actualFiles = filterImages(files,excludeFileEndings);
				Collections.sort(actualFiles,new FileComparator(FileCompareMode.name));
				if (actualFiles.size() > 0) {
					final ArrayList<String> fileNames = new ArrayList<String>(actualFiles.size());
					Iterator<File> iter = actualFiles.iterator();
					while (iter.hasNext()) {
						fileNames.add(iter.next().getAbsolutePath());
					}
					view.getSite().getShell().getDisplay().asyncExec(new Runnable() {
	
						@Override
						public void run() {
							view.update(ImageExplorerView.FOLDER_UPDATE_MARKER, fileNames);	
						}
						
					});
					
				}
			} else 
				returnValue = false;
		} else
			returnValue = false;
		return returnValue;
		
	}
	
}
