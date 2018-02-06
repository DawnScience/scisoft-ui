/*
 * Copyright (c) 2016 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package uk.ac.diamond.scisoft.imagegrid;

import org.eclipse.dawnsci.plotting.api.histogram.IPaletteService;

public class ServiceHolder {

	private static IPaletteService pservice;

	public ServiceHolder() {
		//do nothing
	}

	public static IPaletteService getPaletteService() {
		return pservice;
	}

	public void setPaletteService(IPaletteService ps) {
		pservice = ps;
	}
}
