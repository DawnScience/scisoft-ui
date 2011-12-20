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

package uk.ac.diamond.scisoft.analysis.rcp.util;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Spinner;

/**
 * A spinner class that supports floating point numbers of fixed precision
 */
public class FloatSpinner extends Composite {

	private int width;
	private int precision;
	private int maximumValue;
	private double factor;
	private Spinner spinner;
	private List<SelectionListener> listeners;
	private SelectionAdapter sListener;

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
		super(parent, SWT.NONE);
		setLayout(new FillLayout());
		spinner = new Spinner(this, style);
		setFormat(width, precision);
		listeners = new ArrayList<SelectionListener>();
		sListener = new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				notifySelectionListeners(e);
			}
		};
		spinner.addSelectionListener(sListener);

	}

	protected void notifySelectionListeners(SelectionEvent e) {
		for (SelectionListener s : listeners) {
			s.widgetSelected(e);
		}
	}

	/**
	 * Set the format and automatically set minimum and maximum allowed values
	 * 
	 * @param width
	 *            of displayed value as total number of digits
	 * @param precision
	 *            of value in decimal places
	 */
	public void setFormat(int width, int precision) {
		this.precision = precision;
		this.setWidth(width);
		maximumValue = (int) Math.pow(10, width);
		factor = Math.pow(10, precision);

		spinner.setDigits(precision);
		spinner.setMinimum(-maximumValue);
		spinner.setMaximum(maximumValue);
		spinner.setIncrement(1);
		spinner.setPageIncrement(5);
		spinner.setSelection(0);
	}

	/**
	 * @return Returns the precision.
	 */
	public int getPrecision() {
		return precision;
	}

	/**
	 * @param width
	 *            The width to set.
	 */
	public void setWidth(int width) {
		this.width = width;
	}

	/**
	 * @return Returns the width.
	 */
	public int getWidth() {
		return width;
	}

	/**
	 * @param value
	 */
	public void setDouble(double value) {
		spinner.setSelection((int) (value * factor));
	}

	/**
	 * @return value
	 */
	public double getDouble() {
		return spinner.getSelection() / factor;
	}

	public double getRange() {
		double min = spinner.getMinimum() / factor;
		double max = spinner.getMaximum() / factor;
		return max-min;
	}
	
	public double getMinimum() {
		return spinner.getMinimum() / factor;
	}
	
	public double getMaximum() {
		return spinner.getMaximum() / factor;
	}
	
	/**
	 * @param listener
	 * @see Spinner#addSelectionListener(SelectionListener)
	 */
	public void addSelectionListener(SelectionListener listener) {
		listeners.add(listener);
	}

	/**
	 * @param listener
	 * @see Spinner#removeSelectionListener(SelectionListener)
	 */
	public void removeSelectionListener(SelectionListener listener) {
		listeners.remove(listener);
	}

	/**
	 * @param minimum
	 */
	public void setMinimum(double minimum) {
		spinner.setMinimum((int) (minimum * factor));
	}

	/**
	 * @param maximum
	 */
	public void setMaximum(double maximum) {
		spinner.setMaximum((int) (maximum * factor));
	}

	@Override
	public void dispose() {
		listeners = null;
		if (!spinner.isDisposed())
			spinner.removeSelectionListener(sListener);
	}
	
	public Composite getControl() {
		return spinner;
	}

	@Override
	public void setEnabled(boolean enabled) {
		super.setEnabled(enabled);
		spinner.setEnabled(enabled);
	}
}
