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

package uk.ac.diamond.scisoft.analysis.rcp.inspector;

import java.util.Arrays;

import org.apache.commons.lang.ArrayUtils;

import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.analysis.dataset.DatasetUtils;
import uk.ac.diamond.scisoft.analysis.dataset.ILazyDataset;

/**
 * Class to hold a possible choice of axis for a dataset, the signal dataset, that is to be
 * plotted. It contains an axis name, order (or primacy) and dataset for a specified set of
 * dimensions (or index mapping) of a signal dataset. It also holds the dimension (or axis number)
 * of the signal dataset that this choice can represent
 */
public class AxisChoice {
	private ILazyDataset values = null;
	private int primary; // possible order in a list of choices (0 signifies leave to end of list)
	private int number;  // which dimension does this axis represent for signal dataset
	private int[] indexMapping = null; // array of dimensions of chosen dataset which map to the values dataset
	private String name; // long name if available  

	/**
	 * @param values
	 */
	public AxisChoice(ILazyDataset values) {
		this(values, 0);
	}

	/**
	 * @param values
	 * @param primary
	 */
	public AxisChoice(ILazyDataset values, int primary) {
		setValues(values);
		setPrimary(primary);
	}

	/**
	 * @return Returns the name
	 */
	public String getName() {
		return values != null ? values.getName(): null;
	}

	/**
	 * Set long name
	 * @param longName
	 */
	public void setLongName(String longName) {
		name = longName;
	}

	/**
	 * Get long name if it is defined or else get name
	 * @return name
	 */
	public String getLongName() {
		if (name != null)
			return name;
		return getName();
	}

	/**
	 * @param values The values to set
	 */
	public void setValues(ILazyDataset values) {
		if (this.values != null && this.values.getRank() != values.getRank()) {
			throw new IllegalArgumentException("Replacement axis values dataset must have the same rank");
		}
		this.values = values;
	}

	/**
	 * @return Returns the values
	 */
	public AbstractDataset getValues() {
		try {
			return DatasetUtils.convertToAbstractDataset(values.getSlice());
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * @param primary The order to set
	 */
	public void setPrimary(int primary) {
		this.primary = primary;
	}

	/**
	 * @return Returns the order
	 */
	public int getPrimary() {
		return primary;
	}

	/**
	 * @param mapping array that maps dimension indexes of signal dataset to values dataset
	 */
	public void setIndexMapping(int... mapping) {
		if (mapping.length != values.getRank()) {
			throw new IllegalArgumentException("Index mapping array must have a length that matches axis rank");
		}
		indexMapping = mapping;
	}

	/**
	 * @return Returns the mapping from axis dataset to signal dataset
	 */
	public int[] getIndexMapping() {
		return indexMapping;
	}

	/**
	 * @param dimension
	 * @return true if dimension used in mapping
	 */
	public boolean isDimensionUsed(int dimension) {
		return ArrayUtils.contains(indexMapping, dimension);
	}

	/**
	 * Set which axis number this choice represents (sets index mapping too, if it is null, to
	 * a single-element array containing that number)
	 */
	public void setAxisNumber(int axisNumber) {
		number = axisNumber;
		if (indexMapping == null)
			indexMapping = new int[] { number };
	}

	/**
	 * @return Returns the axis number represented by this choice
	 */
	public int getAxisNumber() {
		return number;
	}

	/**
	 * @return rank of values dataset
	 */
	public int getRank() {
		return values.getRank();
	}

	/**
	 * @return size of values dataset
	 */
	public int getSize() {
		return values.getSize();
	}

	@Override
	public String toString() {
		return String.format("Choice %s: %s #%d = %s", getName(), Arrays.toString(values.getShape()), number, Arrays.toString(indexMapping));
	}

	/**
	 * @param obj
	 * @return true if name matches axis name
	 */
	@Override
	public boolean equals(Object obj) {
		if (values == null)
			return false;
		
		if (obj instanceof String)
			return values.getName().equals(obj);
		
		if (obj instanceof AxisChoice) {
			if (!values.getName().equals(((AxisChoice) obj).getValues().getName()))
				return false;
			if (!Arrays.equals(getIndexMapping(), ((AxisChoice) obj).getIndexMapping()))
				return false;
			if (!values.equals(((AxisChoice) obj).getValues()))
				return false;
			
			return true;
		}
		
		return false;
	}

	@Override
	public int hashCode() {
		int hash = values.getSize();
		for (int d : indexMapping)
			hash = hash * 17 + d;

		String name = values.getName();
		if (name != null)
			hash = hash * 17 + name.hashCode();
		else 
			hash *= 17;
		
		hash = hash * 17 + values.hashCode();
		return hash;
	}

	/**
	 * Clone everything but values
	 */
	@Override
	protected AxisChoice clone() throws CloneNotSupportedException {
		AxisChoice choice = new AxisChoice(values, primary);
		choice.setIndexMapping(indexMapping);
		choice.setAxisNumber(number);
		return choice;
	}
}
