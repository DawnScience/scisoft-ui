/*-
 * Copyright 2017 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.sda.navigator.util;

import org.eclipse.dawnsci.analysis.api.io.ILoaderService;

/**
 * OSGi Service holder
 * 
 * @author Baha El Kassaby
 */
public class ServiceHolder {

	private static ILoaderService loaderservice;

	public ServiceHolder() {
		
	}

	public static ILoaderService getLoaderService() {
		return loaderservice;
	}

	public void setLoaderService(ILoaderService loaderservice) {
		ServiceHolder.loaderservice = loaderservice;
	}
}
