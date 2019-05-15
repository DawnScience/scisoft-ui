/*
 * Copyright (c) 2012 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.ui.PlatformUI;
import org.python.pydev.ui.pythonpathconf.InterpreterNewCustomEntriesAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PyDevAdditionalInterpreterSettings extends InterpreterNewCustomEntriesAdapter {
	private static Logger logger = LoggerFactory.getLogger(PyDevAdditionalInterpreterSettings.class);

	@Override
	public Collection<String> getAdditionalEnvVariables() {
		if (!PlatformUI.isWorkbenchRunning()) return super.getAdditionalEnvVariables();
		List<String> entriesToAdd = new ArrayList<String>();
		entriesToAdd.add("SCISOFT_RPC_PORT=${scisoft_rpc_port}");
		entriesToAdd.add("SCISOFT_RMI_PORT=${scisoft_rmi_port}");
		entriesToAdd.add("SCISOFT_RPC_TEMP=${scisoft_rpc_temp}");
		return entriesToAdd;
	}

	@Override
	public Collection<String> getAdditionalLibraries() {
		
		if (!PlatformUI.isWorkbenchRunning()) return super.getAdditionalLibraries(); // Headless mode, for instance workflows!
		
		List<String> entriesToAdd = new ArrayList<String>();

		boolean isRunningInEclipse = Boolean.getBoolean(JythonCreator.RUN_IN_ECLIPSE);
		URL scisoftpyInitURL = null;
		try { // Try to add the scisoftpy location when in dev or deployed
			scisoftpyInitURL = FileLocator.find(new URL(isRunningInEclipse ?
					"platform:/plugin/uk.ac.diamond.scisoft.python/src/scisoftpy/__init__.py"
					: "platform:/plugin/uk.ac.diamond.scisoft.python/scisoftpy/__init__.py"));
		} catch (MalformedURLException e) {
			// unreachable as it is a constant string
		}

		if (scisoftpyInitURL != null) {
			try {
				scisoftpyInitURL = FileLocator.toFileURL(scisoftpyInitURL);
				IPath scisoftpyInitPath = new Path(scisoftpyInitURL.getPath());
				IPath rootPath = scisoftpyInitPath.removeLastSegments(2); // remove scisoftpy and __init__.py
				IPath path = rootPath.removeTrailingSeparator();
				entriesToAdd.add(path.toOSString());
			} catch (IOException e) {
				logger.debug("Failed to convert scisoft URL into a file URL", e);
			}
		} else {
			logger.debug("Failed to find location of scisfotpy to add the python path");
		}
		
		// Add Fabio to the path
		try {
			IPath path = new Path(System.getProperty("eclipse.home.location").replace("file:", ""));
			//path = path.append("fabio");
			logger.debug("Fabio python path is : " + path.toOSString());
			if (path.toFile().exists()) {
				entriesToAdd.add(path.toOSString());
				logger.debug("Fabio python path added");
			}
		} catch (Exception e) {
			logger.warn("Failed to add Fabio to add the python path");
		}		

		return entriesToAdd;
	}

}
