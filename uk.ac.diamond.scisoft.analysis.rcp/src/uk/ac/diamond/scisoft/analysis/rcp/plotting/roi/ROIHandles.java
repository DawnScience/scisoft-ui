/*
 * Copyright 2012 Diamond Light Source Ltd.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.ac.diamond.scisoft.analysis.rcp.plotting.roi;

import java.util.ArrayList;

import uk.ac.diamond.scisoft.analysis.roi.ROIBase;

/**
 * Abstract class for region of interest handles
 * 
 * Its super class holds the primitive IDs for handle areas
 */
abstract public class ROIHandles extends ArrayList<Integer> {
	protected ROIBase roi;

	/**
	 * @param handle
	 * @param size 
	 * @return handle point
	 */
	abstract public double[] getHandlePoint(int handle, int size);

	/**
	 * @param handle
	 * @param size
	 * @return anchor point for scale invariant display
	 */
	abstract public double[] getAnchorPoint(int handle, int size);

	abstract public ROIBase getROI();


	/**
	 * @param roi The roi to set.
	 */
	public void setROI(ROIBase roi) {
		this.roi = roi;
	}

}
