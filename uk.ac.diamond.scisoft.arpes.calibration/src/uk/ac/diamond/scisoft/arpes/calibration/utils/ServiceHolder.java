/*-
 * Copyright (c) 2016 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package uk.ac.diamond.scisoft.arpes.calibration.utils;

import org.eclipse.dawnsci.nexus.INexusFileFactory;

public class ServiceHolder {

	private static INexusFileFactory nexusFactory;

	public static INexusFileFactory getNexusFactory() {
		return nexusFactory;
	}

	public void setNexusFactory(INexusFileFactory nexusFactory) {
		ServiceHolder.nexusFactory = nexusFactory;
	}
}
