/*
 * Copyright (c) 2012 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.analysis.rcp.views.plot;

import org.eclipse.ui.IWorkbenchPartSite;

/**
 * Implement to enable a StaticScanPlot to be constructed from the implementor.
 */
@Deprecated(since="Dawn 1.9")
public interface PlotView {

	/**
	 * Implemented to set up the plotter from this class.
	 * Could probably copy default implementation of this from
	 * GDA to Sci-soft and reduce code copying. Will do this as
	 * soon (if) another class extends AbstractPlotView.
	 * 
	 * Not needed to be implemented if plot cannot be saved in a 
	 * static plot.
	 * @return f
	 */
	public PlotBean getPlotBean();

	/**
	 * @return d
	 */
	public String getPartName();

	/**
	 * @return d
	 */
	public IWorkbenchPartSite getSite();

}
