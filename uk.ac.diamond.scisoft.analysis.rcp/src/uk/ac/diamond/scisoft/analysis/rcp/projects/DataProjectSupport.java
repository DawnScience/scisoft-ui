/*
 * Copyright (c) 2012 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.analysis.rcp.projects;

import java.net.URI;

import org.apache.commons.lang3.ArrayUtils;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility methods for a project listing data files. Used by SDA and GDA clients.
 */
public class DataProjectSupport {
	
	private static final Logger logger = LoggerFactory.getLogger(DataProjectSupport.class);
	
	public static IProject createProject(String projectName, String dirname, IProgressMonitor monitor) {
		return createProject(projectName, dirname, monitor, null);
	}

	/**
	 * Version of createProject in which certain sub-folders can be ignored and so not have links made to them
	 * 
	 * @param projectName
	 * @param dirname
	 * @param monitor
	 * @param foldersToIgnore
	 * @return IProject
	 */
	public static IProject createProject(String projectName, String dirname, IProgressMonitor monitor, String[] foldersToIgnore) {
		
		// handle null case to avoid NPE
		if (foldersToIgnore == null){
			foldersToIgnore = new String[]{};
		}
		
		IProject project = createBaseProject(projectName, null);
		java.io.File file = new java.io.File(dirname);
		java.io.File[] files = file.listFiles(new DataFilesFilter());
		// doing top level
		for (int i = 0; i < files.length; i++) {
			if (files[i].isDirectory()) {
				if (!ArrayUtils.contains(foldersToIgnore,files[i].getName())){
					monitor.subTask("Importing directory: "+files[i].getAbsolutePath());
					addToProjectStructure(project, files[i],monitor);
				}
			} else {
				monitor.subTask("Import file: "+files[i].getName());
				addFilesToProjectStructure(project, files[i],monitor);
			}
		}
		return project;
	}
	
	private static void addFilesToProjectStructure(IProject project, java.io.File file, IProgressMonitor monitor) {
		IFile projFile = project.getFile(file.getName());
		if (!projFile.exists()) {
			try {
				projFile.createLink(new Path(file.getAbsolutePath()), 0, monitor);
			} catch (CoreException e) {
				e.printStackTrace();
			}
		}
	}
	
	private static void addToProjectStructure(IProject project, java.io.File file, IProgressMonitor monitor) {
		String folderName = file.getName();
		IFolder etcFolders = project.getFolder(folderName);
		try {
			createFolder(etcFolders,file,monitor);
		} catch (CoreException e) {
			logger.warn("Exception trying to create link to folder " + folderName, e);
		}		
	}

	private static void createFolder(IFolder folder, java.io.File file, IProgressMonitor monitor) throws CoreException {
		IContainer parent = folder.getParent();
		if (parent instanceof IFolder) {
			createFolder((IFolder)parent,file,monitor);
		}
		if (!folder.exists()) {
			folder.createLink(new Path(file.getAbsolutePath()), 0,monitor);
		}
	}
	
	private static IProject createBaseProject(String projectName, URI location) {
		IProject newProject = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
		if (!newProject.exists()) {
			URI projectLocation = location;
			IProjectDescription desc = newProject.getWorkspace().newProjectDescription(projectName);
			desc.setLocationURI(projectLocation);
			try {
				newProject.create(desc,null);
				if (!newProject.isOpen()) {
					newProject.open(null);
				}
			} catch (CoreException e) {
				logger.warn("Exception trying to create new workspace project " + projectName, e);
			}
		}
		return newProject;
	}

}
