/*-
 * Copyright 2012 Diamond Light Source Ltd.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
import org.python.pydev.ui.pythonpathconf.InterpreterNewCustomEntriesAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PyDevAdditionalInterpreterSettings extends InterpreterNewCustomEntriesAdapter {
	private static Logger logger = LoggerFactory.getLogger(PyDevAdditionalInterpreterSettings.class);

	@Override
	public Collection<String> getAdditionalEnvVariables() {
		List<String> entriesToAdd = new ArrayList<String>();
		entriesToAdd.add("SCISOFT_RPC_PORT=${scisoft_rpc_port}");
		entriesToAdd.add("SCISOFT_RMI_PORT=${scisoft_rmi_port}");
		entriesToAdd.add("SCISOFT_RPC_TEMP=${scisoft_rpc_temp}");
		return entriesToAdd;
	}

	@Override
	public Collection<String> getAdditionalLibraries() {
		List<String> entriesToAdd = new ArrayList<String>();

		// Try to add the scisoftpy location when in dev
		URL scisoftpyInitURL= null;
		try {
			scisoftpyInitURL = FileLocator.toFileURL(FileLocator.find(new URL(
					"platform:/plugin/uk.ac.diamond.scisoft.python/src/scisoftpy/__init__.py")));
		} catch (MalformedURLException e) {
			// unreachable as it is a constant string
		} catch (IOException e) {
			// Don't add if cannot be found
		}

		// Try to add the scisoftpy location when in dev
		if (scisoftpyInitURL == null) {
			try {
				scisoftpyInitURL = FileLocator.toFileURL(FileLocator.find(new URL(
						"platform:/plugin/uk.ac.diamond.scisoft.python/scisoftpy/__init__.py")));
			} catch (MalformedURLException e) {
				// unreachable as it is a constant string
			} catch (IOException e) {
				// Don't add if cannot be found
			}
		}
		if (scisoftpyInitURL != null){
			IPath scisoftpyInitPath = new Path(scisoftpyInitURL.getPath());
			IPath rootPath = scisoftpyInitPath.removeLastSegments(2); // remove scisoftpy and __init__.py
			IPath path = rootPath.removeTrailingSeparator();
			entriesToAdd.add(path.toOSString());
		} else {		
			logger.debug("Failed to find location of scisfotpy to add the python path");
		}
		

		return entriesToAdd;
	}

}
