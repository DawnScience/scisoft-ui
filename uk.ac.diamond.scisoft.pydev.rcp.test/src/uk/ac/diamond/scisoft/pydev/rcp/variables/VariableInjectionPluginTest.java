/*
 * Copyright (c) 2012 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.pydev.rcp.variables;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;
import org.python.pydev.ast.interpreter_managers.InterpreterManagersAPI;
import org.python.pydev.core.IInterpreterInfo;
import org.python.pydev.core.IInterpreterManager;

public class VariableInjectionPluginTest {

	public List<IInterpreterInfo> collectAllInterpreterInfos() {
		List<IInterpreterInfo> infos = new ArrayList<IInterpreterInfo>();
		IInterpreterManager[] allInterpreterManagers = InterpreterManagersAPI.getAllInterpreterManagers();
		for (IInterpreterManager iInterpreterManager : allInterpreterManagers) {
			IInterpreterInfo[] interpreterInfos = iInterpreterManager.getInterpreterInfos();
			infos.addAll(Arrays.asList(interpreterInfos));
		}
		return infos;
	}
	
	public void checkInfo(IInterpreterInfo info) {
		String[] envVariables = info.getEnvVariables();
		Set<String> hashSet = new HashSet<String>();
		hashSet.addAll(Arrays.asList(envVariables));
		
		Assert.assertTrue("SCISOFT_RPC_PORT", hashSet.contains("SCISOFT_RPC_PORT=${scisoft_rpc_port}"));
		Assert.assertTrue("SCISOFT_RMI_PORT", hashSet.contains("SCISOFT_RMI_PORT=${scisoft_rmi_port}"));
		Assert.assertTrue("SCISOFT_RPC_TEMP", hashSet.contains("SCISOFT_RPC_TEMP=${scisoft_rpc_temp}"));
	}
	
	@Test
	public void testVariablesArePresent() throws InterruptedException {
		// A bit of a race condition here, let JythonCreator create a configuration
		// and fail if none shows up within 10 seconds
		int retries = 10;
		List<IInterpreterInfo> infos = collectAllInterpreterInfos();
		while (infos.size() == 0 && retries > 0) {
			retries--;
			Thread.sleep(1000);
			infos = collectAllInterpreterInfos();
		}
		for (IInterpreterInfo iInterpreterInfo : infos) {
			checkInfo(iInterpreterInfo);
		}
		Assert.assertTrue("At least one info found", infos.size() >= 1);
	}
}
