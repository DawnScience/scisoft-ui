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

package uk.ac.diamond.scisoft.analysis.rcp.plotting.compositing;

import uk.ac.diamond.scisoft.analysis.rcp.plotting.enums.CompositeOp;

/**
 *
 */
public class CompositeEntry {

	private String name;
	private float weight;
	private CompositeOp operation;
	private byte channelMask;
	
	public CompositeEntry(String name, float weight, CompositeOp op,
						  byte channelMask) {
		this.name = name;
		this.weight = weight;
		this.operation = op;
		this.channelMask = channelMask;
	}
	
	public final String getName() {
		return name;
	}
	
	public final float getWeight() {
		return weight;
	}
	
	public final CompositeOp getOperation() {
		return operation;
	}
	
	public final byte getChannelMask() {
		return channelMask;
	}
}
