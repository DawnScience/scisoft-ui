/*
 * Copyright © 2011 Diamond Light Source Ltd.
 * Contact :  ScientificSoftware@diamond.ac.uk
 * 
 * This is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License version 3 as published by the Free
 * Software Foundation.
 * 
 * This software is distributed in the hope that it will be useful, but 
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along
 * with this software. If not, see <http://www.gnu.org/licenses/>.
 */

package uk.ac.diamond.scisoft.analysis.rcp.views;



/**
 * Contains all the configuration elements for a JythonTerminalView
 */
public class PlotViewConfig {
	private String name;
	
	/**
	 * Create a config for this Jython Terminal Configuration
	 * @param id The ID of the view that the config is for
	 */
	public PlotViewConfig(String id) {
		// cache the name as it isn't configurable by the preferences
		name = PlotViewRegistry.getDefault().getConfigs().get(id).name;
	
	}

	/**
	 * @return the name of the view
	 */
	public String getName() {
		return name;
	}


}
