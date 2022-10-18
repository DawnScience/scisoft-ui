/*
 * Copyright (c) 2012 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.analysis.rcp.util;

import org.eclipse.swt.widgets.Composite;

import uk.ac.diamond.daq.util.logging.deprecation.DeprecationLogger;

/**
 * A spinner class that supports floating point numbers of fixed precision
 * @deprecated Use {@link org.dawnsci.common.widgets.spinner.FloatSpinner} instead
 */
@Deprecated(since="Dawn 2.5")
public class FloatSpinner extends org.dawnsci.common.widgets.spinner.FloatSpinner {
	
	private static final DeprecationLogger logger = DeprecationLogger.getLogger(FloatSpinner.class);
	/**
	 * Create a fixed float spinner
	 * 
	 * @param parent
	 * @param style
	 */
	public FloatSpinner(Composite parent, int style) {
		this(parent, style, 3, 1);
	}

	/**
	 * Create a fixed float spinner
	 * 
	 * @param parent
	 * @param style
	 * @param width
	 * @param precision
	 */
	public FloatSpinner(Composite parent, int style, int width, int precision) {
		super(parent, style, width, precision);
		logger.deprecatedClass(null, "org.dawnsci.common.widgets.spinner.FloatSpinner");
	}
}
