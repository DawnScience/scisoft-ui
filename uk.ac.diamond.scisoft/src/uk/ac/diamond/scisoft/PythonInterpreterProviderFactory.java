/*
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

import org.python.pydev.core.REF;
import org.python.pydev.ui.pythonpathconf.AbstractInterpreterProviderFactory;
import org.python.pydev.ui.pythonpathconf.AlreadyInstalledInterpreterProvider;
import org.python.pydev.ui.pythonpathconf.IInterpreterProvider;
import org.python.pydev.ui.pythonpathconf.IInterpreterProviderFactory;

/**
 * Provide a python from PATH if not running MS Windows
 */
public class PythonInterpreterProviderFactory extends AbstractInterpreterProviderFactory {

	@Override
	public IInterpreterProvider[] getInterpreterProviders(InterpreterType type) {
		if (type != IInterpreterProviderFactory.InterpreterType.PYTHON) {
			return null;
		}

		if (REF.isWindowsPlatform()) {
			return null;
		}

		// this resolves eventually to python on PATH
		return AlreadyInstalledInterpreterProvider.create("python from PATH", "python");
	}
}
