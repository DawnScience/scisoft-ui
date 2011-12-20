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

package uk.ac.diamond.scisoft.analysis.rcp.histogram.mapfunctions;

/**
 * Map function that returns the square of the scaled and offset input
 */
public class SquareScaleOffsetMapFunction extends AbstractMapFunction {
	private String functionName;
	private double scaleFactor;
	private double offset;
	
	/**
	 * Constructor for a square scale offset map function
	 *
	 * (scaleFactor*x + offset)^2
	 * @param functionName name of the function
	 * @param scaleFactor scaling factor
	 * @param offset offset factor
	 */
	public SquareScaleOffsetMapFunction(String functionName, double scaleFactor,
										double offset)
	{
		this.functionName = functionName;
		this.scaleFactor = scaleFactor;
		this.offset = offset;
	}
	
	@Override
	public String getMapFunctionName() {
		return functionName;
	}

	@Override
	public double mapFunction(double input) {
		double returnValue = input * scaleFactor + offset;
		returnValue *= returnValue;
		return returnValue;
	}
}
