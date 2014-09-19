/*
 * Copyright (c) 2012 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.analysis.rcp.plotting;

import java.io.Serializable;

import uk.ac.diamond.scisoft.analysis.plotserver.GuiBean;
import uk.ac.diamond.scisoft.analysis.plotserver.GuiParameters;

/**
 * The <code>IGuiInfoManager</code> interface provides protocol
 * for saving and restoring GUI state typically between a client and multiple
 * subscribers. 
 * 
 * A concrete gui update manager implementation can be found,
 *  {@link uk.ac.diamond.scisoft.analysis.rcp.views.PlotView <code>PlotView</code>}
 * 
 */
public interface IGuiInfoManager {

	/**
	 * This method allows for interested parties to get relevant GUI information
	 * from the client
	 * 
	 * @return The data which specifies the gui state
	 */
	public GuiBean getGUIInfo();

	/**
	 * This method allows interested parties to push relevant GUI information to the 
	 * client. Information can later be retrieved using <code>getGuiState</code>
	 * 
	 * @param key key with which the specified value is to be associated
	 * @param value value to be associated with the specified key
	 */
	public void putGUIInfo(GuiParameters key, Serializable value);

	/**
	 * This method allows interested parties to remove GUI information from the client
	 *  
	 * @param key key with which the specified value is to be associated
	 */
	public void removeGUIInfo(GuiParameters key);

	/**
	 * Push latest information back to plot server
	 */
	public void sendGUIInfo(GuiBean guiBean);
	
}
