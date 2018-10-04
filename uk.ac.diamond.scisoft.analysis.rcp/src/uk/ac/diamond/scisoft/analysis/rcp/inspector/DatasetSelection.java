/*
 * Copyright (c) 2012 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.analysis.rcp.inspector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.eclipse.january.dataset.ILazyDataset;
import org.eclipse.jface.viewers.IStructuredSelection;

/**
 * Class to encapsulate selection of items for dataset inspector
 */
public class DatasetSelection implements IStructuredSelection {

	/**
	 * Enumerate types supported by inspector
	 */
	public enum InspectorType {
		/**
		 * Single line plot of 1D dataset
		 */
		LINE,
		/**
		 * Points plot of 1D dataset 
		 */
		POINTS1D,
		/**
		 * Multiple lines plot of 2D dataset
		 */
		LINESTACK,
		/**
		 * Data table of 1D dataset
		 */
		DATA1D,
		/**
		 * Points plot of 2D dataset
		 */
		POINTS2D,
		/**
		 * Image plot of 2D dataset
		 */
		IMAGE,
		/**
		 * Surface plot of 2D dataset
		 */
		SURFACE,
		/**
		 * Data table of 2D dataset
		 */
		DATA2D,
		/**
		 * Image explorer of 3D dataset
		 */
		IMAGEXP,
		/**
		 * Points plot of 3D dataset
		 */
		POINTS3D,
		/**
		 * Volume plot of 3D dataset
		 */
		VOLUME,
		/**
		 * View 3D dataset slices
		 */
		HYPER,
		/**
		 * Clear plot
		 */
		EMPTY;
		private static int index = 0;
		private int value;
		private static int getIndex() {
			return index++;
		}

		private InspectorType() {
			value = getIndex();
		}

		public int getValue() {
			return value;
		}

		static public InspectorType getType(int type) {
			for (InspectorType t : InspectorType.values())
				if (t.getValue() == type)
					return t;
			return EMPTY;
		}
	}

	final private List<AxisSelection> axes;
	final private ILazyDataset[] datasets;
	private InspectorType view;
	final protected String filePath;

	/**
	 * Null constructor to not show any plot or view
	 */
	public DatasetSelection() {
		axes = new ArrayList<AxisSelection>();
		datasets = new ILazyDataset[0];
		view = InspectorType.EMPTY;
		filePath = null;
	}

	/**
	 * Show a line plot
	 * @param path
	 * @param axes (can be null)
	 * @param dataset
	 */
	public DatasetSelection(String path, List<AxisSelection> axes, ILazyDataset... dataset) {
		this(InspectorType.LINE, path, axes, dataset);
	}

	/**
	 * @param type inspector used
	 * @param path
	 * @param axes
	 * @param dataset
	 */
	public DatasetSelection(InspectorType type, String path, List<AxisSelection> axes, ILazyDataset... dataset) {
		filePath = path;
		this.axes = axes;
		this.datasets = dataset;
		this.view = type;
	}

	@Override
	public String toString() {
		if (isEmpty())
			return "Null selection";
		return Arrays.toString(datasets);
	}

	@Override
	public boolean isEmpty() {
		return datasets == null || datasets.length == 0;
	}

	@Override
	public ILazyDataset getFirstElement() {
		return isEmpty() ? null : datasets[0];
	}

	@Override
	public Iterator<ILazyDataset> iterator() {
		return toList().iterator();
	}

	@Override
	public int size() {
		return isEmpty() ? 0 : datasets.length;
	}

	@Override
	public ILazyDataset[] toArray() {
		return datasets;
	}

	@Override
	public List<ILazyDataset> toList() {
		List<ILazyDataset> c = new ArrayList<ILazyDataset>();
		if (!isEmpty())
			Collections.addAll(c, datasets);
		return c;
	}

	/**
	 * @return true if (lazy) datasets are same shape and axes are the same (in value)  
	 */
	@Override
	public boolean equals(Object other) {
		return almostEquals(other);
	}

	/**
	 * @return true if (lazy) datasets are same shape and axes are the same (in value)  
	 */
	final public boolean almostEquals(Object other) {
		if (other instanceof DatasetSelection) {
			DatasetSelection that = (DatasetSelection) other;
			if (!areAxesAllEqual(axes, that.axes))
				return false;
			if (areDatasetsAllEqual(datasets, that.datasets))
				return true;
		}
		return false;
	}

	private boolean areDatasetsAllEqual(ILazyDataset[] aList, ILazyDataset[] bList) {
		if (aList == bList)
			return true;
		if (aList == null || bList == null)
			return false;
		int size = aList.length;
		if (bList.length != size)
			return false;
		for (int i = 0; i < size; i++) {
			ILazyDataset a = aList[i];
			ILazyDataset b = bList[i];
			if (a == b)
				continue;
			if (!Arrays.equals(a.getShape(), b.getShape()))
				return false;
			if (!a.getName().equals(b.getName()))
				return false;
			if (a.hashCode() != b.hashCode())
				return false;
		}
		return true;
	}

	private boolean areAxesAllEqual(List<AxisSelection> aList, List<AxisSelection> bList) {
		if (aList == bList)
			return true;
		if (aList == null || bList == null)
			return false;
		int size = aList.size();
		if (bList.size() != size)
			return false;
		for (int i = 0; i < size; i++) {
			AxisSelection a = aList.get(i);
			AxisSelection b = bList.get(i);
			if (!a.equals(b))
				return false;
		}
		return true;
	}

	@Override
	public int hashCode() {
		int hash = view.getValue();
		for (int i = 0, imax = datasets.length; i < imax; i++) {
			hash = hash * 31 + datasets[i].hashCode();
		}
		for (int i = 0, imax = axes == null ? 0 : axes.size(); i < imax; i++) {
			hash = hash * 31 + axes.get(i).hashCode();
		}
		return hash;
	}

	/**
	 * @return full file path
	 */
	public String getFileName() {
		return filePath;
	}

	/**
	 * @return list of axes (can be null)
	 */
	public List<AxisSelection> getAxes() {
		return axes;
	}

	/**
	 * @return type of inspection
	 */
	public InspectorType getType() {
		return view;
	}

	/**
	 * @param type inspector type
	 */
	public void setType(InspectorType type) {
		view = type;
	}
}
