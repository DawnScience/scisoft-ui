/*
 * Copyright (c) 2012 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.sda.navigator.views;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import org.dawb.common.ui.util.EclipseUtils;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;

public class ExplorerOSHandler extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		
		final IFileView fileView = (IFileView)EclipseUtils.getActivePage().getActivePart();
		
		Path fileObject = fileView.getSelectedPath();
		
		if (!Files.isDirectory(fileObject)) {
			fileObject = fileObject.getParent();
		}
		
		final Path path = fileObject;
		Job openfile = new Job("open file") {
			
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				
				try {
					//Desktop.getDesktop().open() should work for all OSs
					// but in testing the odd windows system didnt open explorer
					// when given a path deep into the C drive
					//This is also why this is in a job
					if (System.getProperty("os.name").toLowerCase().contains("win")) {
						new ProcessBuilder("explorer.exe",path.toAbsolutePath().toString()).start();
						return Status.OK_STATUS;
					}
					
					
					Desktop desktop = Desktop.getDesktop();
					desktop.open(path.toFile());
				} catch (IOException e) {
					// do nothing
				}
				return Status.OK_STATUS;
			}
		};
		openfile.schedule();
		return Boolean.TRUE;
	}
	
	@Override
	public boolean isEnabled() {
		if (System.getProperty("os.name").toLowerCase(Locale.ENGLISH).contains("linux")) {
			ProcessBuilder whichProcessBuilder = new ProcessBuilder("which", "nautilus");
			Process whichProcess = null;
			try {
				whichProcess = whichProcessBuilder.start();
			} catch (IOException ioE) {
				return false;
			}
			// wait for which to return
			int whichResult = 1;
			try {
				whichResult = whichProcess.waitFor();
			} catch (InterruptedException iE) {
				return false;
			}
			return (whichResult == 0); 
				
		}
		return true;
		
	}


}
