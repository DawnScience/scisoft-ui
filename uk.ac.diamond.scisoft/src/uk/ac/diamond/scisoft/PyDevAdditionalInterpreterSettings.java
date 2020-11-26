/*
 * Copyright (c) 2012 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.eclipse.ui.PlatformUI;
import org.python.pydev.ui.pythonpathconf.InterpreterNewCustomEntriesAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.jython.JythonPath;

public class PyDevAdditionalInterpreterSettings extends InterpreterNewCustomEntriesAdapter {
	private static Logger logger = LoggerFactory.getLogger(PyDevAdditionalInterpreterSettings.class);

	@Override
	public Collection<String> getAdditionalEnvVariables() {
		if (!PlatformUI.isWorkbenchRunning()) return super.getAdditionalEnvVariables();
		List<String> entriesToAdd = new ArrayList<String>();
		entriesToAdd.add("SCISOFT_RPC_PORT=${scisoft_rpc_port}");
		entriesToAdd.add("SCISOFT_RMI_PORT=${scisoft_rmi_port}");
		entriesToAdd.add("SCISOFT_RPC_TEMP=${scisoft_rpc_temp}");
		entriesToAdd.add("IPYTHONENABLE=True"); // PyDev post 7.1 needs this to make the interactive console use IPython

		addHDF5PluginPath(entriesToAdd);
		return entriesToAdd;
	}

	@Override
	public Collection<String> getAdditionalLibraries() {
		
		if (!PlatformUI.isWorkbenchRunning()) return super.getAdditionalLibraries(); // Headless mode, for instance workflows!
		
		List<String> entriesToAdd = new ArrayList<String>();

		try {
			entriesToAdd.add(findScisoftPyPath());
		} catch (Exception e) {
			logger.debug("Failed to find location of scisfotpy to add the python path");
		}

		return entriesToAdd;
	}

	private static String findScisoftPyPath() {
		File scisoftPy = JythonPath.getScisoftPyDirectory();
		File src = new File(scisoftPy, "src");
		if (src.isDirectory()) {
			scisoftPy = src;
		}
		return scisoftPy.getAbsolutePath();
	}

	private static void addHDF5PluginPath(Collection<String> envVariables) {
		File plugins = JythonPath.getPluginsDirectory();
		if (JythonPath.isRunningInEclipse(plugins)) {
			plugins = new File(plugins, "dawn-hdf" + JythonPath.GIT_REPO_ENDING);
		}
		List<File> allPluginDirs = Arrays.asList(plugins.listFiles());
		JythonCreator.addHDF5PluginPath(envVariables, allPluginDirs);
	}
}
