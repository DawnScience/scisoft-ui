/*
 * Copyright (c) 2012 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft;

import org.eclipse.ui.PlatformUI;
import org.python.pydev.ast.interpreter_managers.IInterpreterProvider;
import org.python.pydev.ast.interpreter_managers.IInterpreterProviderFactory;
import org.python.pydev.shared_core.utils.PlatformUtils;
import org.python.pydev.ui.pythonpathconf.AbstractInterpreterProviderFactory;
import org.python.pydev.ui.pythonpathconf.AlreadyInstalledInterpreterProvider;

/**
 * Provide a python from PATH if not running MS Windows
 */
public class PythonInterpreterProviderFactory extends AbstractInterpreterProviderFactory {

	@Override
	public IInterpreterProvider[] getInterpreterProviders(InterpreterType type) {
		
		if (!PlatformUI.isWorkbenchRunning()) return null;
		
		if (type != IInterpreterProviderFactory.InterpreterType.PYTHON) {
			return null;
		}

		if (PlatformUtils.isWindowsPlatform()) {
			return null;
		}

		// this resolves eventually to python on PATH
		return AlreadyInstalledInterpreterProvider.create("python from PATH", "python");
	}
}
