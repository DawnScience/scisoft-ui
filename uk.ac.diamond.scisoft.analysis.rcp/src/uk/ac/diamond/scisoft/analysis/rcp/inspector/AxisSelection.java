/*
 * Copyright (c) 2012 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.analysis.rcp.inspector;

import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.collections4.IteratorUtils;
import org.apache.commons.collections4.Predicate;
import org.eclipse.january.dataset.ILazyDataset;

import uk.ac.diamond.scisoft.analysis.axis.AxisChoice;
import uk.ac.diamond.scisoft.analysis.rcp.explorers.AbstractExplorer;

/**
 * Class to hold a list of axis names and dataset from which an axis can be selected
 */
public class AxisSelection extends InspectorProperty implements Iterable<String> {
	class AxisSelData implements Comparable<AxisSelData> {
		private boolean selected;
		private int order; // possible order in a list of choices (0 signifies leave to end of list) 
		private AxisChoice data;

		public AxisSelData(AxisChoice axisData, boolean axisSelected) {
			setData(axisData);
			setSelected(axisSelected);
		}

		/**
		 * @param selected The selected to set.
		 */
		public void setSelected(boolean selected) {
			this.selected = selected;
		}

		/**
		 * @return Returns the selected.
		 */
		public boolean isSelected() {
			return selected;
		}

		/**
		 * @param order The order to set.
		 */
		public void setOrder(int order) {
			this.order = order;
		}

		/**
		 * @return Returns the order.
		 */
		public int getOrder() {
			return order;
		}

		/**
		 * @param data The data to set.
		 */
		public void setData(AxisChoice data) {
			this.data = data;
		}

		/**
		 * @return Returns the data.
		 */
		public AxisChoice getData() {
			return data;
		}

		@Override
		public int compareTo(AxisSelData axisSelData) {
			int cOrder = axisSelData.getOrder();
			if (order == 0)
				return cOrder == 0 ? 0 : 1;
			if (cOrder == 0)
				return -1;
			return order - cOrder;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((data == null) ? 0 : data.hashCode());
			result = prime * result + order;
			result = prime * result + (selected ? 1231 : 1237);
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			AxisSelData other = (AxisSelData) obj;
			if (data == null) {
				if (other.data != null)
					return false;
			} else if (!data.equals(other.data))
				return false;
			if (order != other.order)
				return false;
			if (selected != other.selected)
				return false;
			return true;
		}
	}

	private final static String propName = "axisselection";
	private int dim;    // dimension (or position index) of dataset
	private int length; // length of axis
	private List<AxisSelData> asData;
	private List<String> names;
	private final String suffix;

	Predicate<AxisSelData> axisSelectionPredicate = new Predicate<AxisSelData>() {
		@Override
		public boolean evaluate(AxisSelData o) {
			return o.isSelected();
		}
	};

	class OrderPredicate implements Predicate<AxisSelData> {
		int order;
		public void setOrder(int order) {
			this.order = order;
		}

		@Override
		public boolean evaluate(AxisSelData a) {
			return order < a.getOrder();
		}
	}

	OrderPredicate orderPredicate = new OrderPredicate();

	/**
	 * Create an axis selection that corresponds to a dataset dimension of given length
	 * @param length 
	 * @param dimension
	 */
	public AxisSelection(int length, int dimension) {
		dim = dimension;
		this.length = length;
		asData = new ArrayList<AxisSelData>();
		names = new ArrayList<String>();
		suffix = ":" + (dim + 1);
	}

	/**
	 * @return Returns the length
	 */
	public int getLength() {
		return length;
	}

	@Override
	public String toString() {
		StringBuilder text = new StringBuilder();
		text.append('(');
		for (AxisSelData a : asData) {
			text.append(a.getData().getName());
			if (a.isSelected()) {
				text.append('*');
			}
			text.append(", ");
		}
		if (text.length() > 0) {
			text.deleteCharAt(text.length()-1);
			text.deleteCharAt(text.length()-1);
		}
		text.append(')');
		return text.toString();
	}

	/**
	 * Add axis choice with given order and sets choice to first
	 * @param axis
	 * @param order (can be zero to denote first)
	 */
	public void addChoice(AxisChoice axis, int order) {
		String name = axis.getName();
		if (axis.getRank() > 1 && !name.startsWith(AbstractExplorer.DIM_PREFIX))
			name += suffix;
		addChoice(name, axis, order);
	}

	/**
	 * Add axis choice with given name and order and sets choice to first
	 * @param name
	 * @param axis
	 * @param order (can be zero to denote first)
	 */
	public void addChoice(String name, AxisChoice axis, int order) {
		AxisSelData a;
		int i = names.indexOf(name);
		if (i >= 0) { // existing axis so replace
			a = asData.get(i);
			if (axis != a.getData())
				a.setData(axis);
			int o = a.getOrder();
			if (o == order)
				return;

			names.remove(i);
			asData.remove(i);
		} else {
			a = new AxisSelData(axis, false);
		}

		a.setOrder(order);
		if (order == 0) {
			names.add(0, name);
			asData.add(0, a);
		} else {
			orderPredicate.setOrder(order); // find first >= order
			AxisSelData first = IteratorUtils.find(asData.iterator(), orderPredicate);
			if (first == null) {
				names.add(name);
				asData.add(a);
			} else {
				int j = asData.indexOf(first);
				names.add(j, name);
				asData.add(j, a);
			}
		}
		selectAxis(0);
	}

	/**
	 * @param axes
	 */
	public void setChoices(ILazyDataset[] axes) {
		names.clear();
		asData.clear();
		int i = 1;
		for (ILazyDataset l : axes) {
			AxisSelData a = new AxisSelData(new AxisChoice(l), false);
			int r = l.getRank();
			if (r > 1) {
				int[] map = new int[r];
				for (int j = 0; j < r; j++) {
					map[j] = j;
				}
				a.getData().setIndexMapping(map);
			} else {
				a.getData().setIndexMapping(dim);
			}
			a.setOrder(i++);
			names.add(l.getName());
			asData.add(a);
		}
		selectAxis(0);
	}

	/**
	 * @param name
	 * @return true if name is one of possible selections
	 */
	public boolean containsAxis(String name) {
		return names.contains(name);
	}

	/**
	 * Select an axis with given name
	 * @param name
	 * @param fire
	 */
	public void selectAxis(String name, boolean fire) {
		int i = names.indexOf(name);
		if (i < 0)
			return;

		String oldName = getSelectedName();
		for (AxisSelData d: asData)
			d.setSelected(false);

		AxisSelData a = asData.get(i);
		a.setSelected(true);

		if (fire)
			fire(new PropertyChangeEvent(this, propName, oldName, name));
	}

	/**
	 * Select an axis with given index
	 * @param index 
	 */
	public void selectAxis(int index) {
		selectAxis(index, false);
	}

	/**
	 * Select an axis with given index
	 * @param index 
	 * @param fire
	 */
	public void selectAxis(int index, boolean fire) {
		AxisSelData a = IteratorUtils.find(asData.iterator(), axisSelectionPredicate);
		if (a != null)
			a.setSelected(false);

		asData.get(index).setSelected(true);
		if (!fire) {
			return;
		}

		String oldName = a == null ? null : names.get(asData.indexOf(a));
		fire(new PropertyChangeEvent(this, propName, oldName, names.get(index)));
	}

	/**
	 * @param index 
	 * @return axis name of given index
	 */
	public String getName(int index) {
		return names.get(index);
	}

	/**
	 * @return axis names
	 */
	public List<String> getNames() {
		return names;
	}

	/**
	 * @param index 
	 * @return axis order of given index
	 */
	public int getOrder(int index) {
		AxisSelData a = asData.get(index); 
		return a == null ? -1 : a.getOrder();
	}

	/**
	 * @param index 
	 * @return axis choice of given index
	 */
	public AxisChoice getAxis(int index) {
		AxisSelData a = asData.get(index); 
		return a == null ? null : a.getData();
	}

	/**
	 * @param name
	 * @return axis choice of given name
	 */
	public AxisChoice getAxis(String name) {
		int i = names.indexOf(name);
		return i < 0 ? null : asData.get(i).getData();
	}

	/**
	 * Remove axis choice of given index
	 * @param index
	 */
	public void removeChoice(int index) {
		names.remove(index);
		asData.remove(index);
	}

	/**
	 * Remove axis choice of given name
	 * @param name
	 */
	public void removeChoice(String name) {
		int i = names.indexOf(name);
		if (i < 0)
			return;
		removeChoice(i);
	}

	/**
	 * @return number of choices
	 */
	public int size() {
		return names.size();
	}

	/**
	 * @param index
	 * @return selection status
	 */
	public boolean isSelected(int index) {
		AxisSelData a = asData.get(index); 
		return (a == null) ? false : a.isSelected();
	}

	/**
	 * Get name of selected axis
	 * @return name or null if nothing selected
	 */
	public String getSelectedName() {
		int i = getSelectedIndex();
		return i < 0 ? null : names.get(i);
	}

	/**
	 * Get index of selected axis
	 * @return index or -1 if nothing selected
	 */
	public int getSelectedIndex() {
		AxisSelData a = IteratorUtils.find(asData.iterator(), axisSelectionPredicate);
		return asData.indexOf(a);
	}

	/**
	 * @return selected dimensions
	 */
	public int[] getSelectedIndexMapping() {
		AxisChoice choice = getSelectedAxis();
		if (choice != null)
			return choice.getIndexMapping();	
		return null;
	}

	/**
	 * @return selected choice
	 */
	public AxisChoice getSelectedAxis() {
		AxisSelData a = IteratorUtils.find(asData.iterator(), axisSelectionPredicate);
		return (a == null) ? null : a.getData();
	}

	/**
	 * @return true if names and axis datasets have same values
	 */
	@Override
	public boolean equals(Object other) {
		if (this == other)
			return true;

		if (other instanceof AxisSelection) {
			AxisSelection that = (AxisSelection) other;
			
			if (!that.names.equals(names))
				return false;
			
			if (!asData.equals(that.asData))
				return false;
			
			return true;
			
		}
		return false;
	}

	@Override
	public int hashCode() {
		int hash = length;
		for (String n : names)
				hash = hash * 17 + n.hashCode();
		for (AxisSelData n : asData)
				hash = hash * 17 + n.hashCode();
		return hash;
	}

	/**
	 * Clone everything but axis choice values
	 */
	@Override
	public AxisSelection clone() {
		AxisSelection selection = new AxisSelection(length, dim);
		for (int i = 0, imax = asData.size(); i < imax; i++) {
			AxisSelData a = asData.get(i);
			selection.addChoice(names.get(i), a.getData().clone(), a.getOrder());
			if (a.isSelected())
				selection.selectAxis(i);
		}
		
		return selection;
	}

	/**
	 * @return iterator over names
	 */
	@Override
	public Iterator<String> iterator() {
		return names.iterator();
	}
}
