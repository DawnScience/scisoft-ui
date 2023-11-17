/*
 * Copyright (c) 2012 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package uk.ac.diamond.scisoft.feedback.attachment;

import static uk.ac.diamond.scisoft.feedback.utils.FeedbackConstants.KIBI_MULTIPLIER;

import java.io.File;

import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.swt.graphics.Image;

import uk.ac.diamond.scisoft.feedback.Activator;

/**
 * Label Provider of the Table Viewer
 */
public class AttachedFileLabelProvider extends ColumnLabelProvider {
	public final Image delete = Activator.getImageDescriptor("icons/delete_obj.png").createImage();
	public final Image copy = Activator.getImageDescriptor("icons/copy_edit_on.gif").createImage();

	private int column;
	public AttachedFileLabelProvider(int column) {
		this.column = column;
	}

	@Override
	public void dispose() {
		delete.dispose();
		copy.dispose();
	}

	@Override
	public Image getImage(Object element) {
		if (element != null) {
			if (column == 2) {
				return delete;
			}
			if (column == 3) {
				return copy;
			}
		}
		return null;
	}

	/**
	 * size unit
	 * @param value
	 * @return the string value with unit
	 */
	private static String getValueWithUnit(long value){
		if (value < KIBI_MULTIPLIER) {
			return Long.toString(value) + "B";
		}
		value /= KIBI_MULTIPLIER;
		if (value < KIBI_MULTIPLIER) {
			return Long.toString(value) + "kB";
		}
		value /= KIBI_MULTIPLIER;
		return Long.toString(value) + "MB";
	}

	@Override
	public String getText(Object element) {
		if (element == null)
			return null;
		File file = (File) element;
		if (column == 0) {
			return file.getName();
		} else if (column == 1) {
			return getValueWithUnit(file.length());
		}
		return null;
	}

	@Override
	public String getToolTipText(Object element) {
		if (column == 2) {
			return "Click to delete file"; 
		} else if (column == 3) {
			return "Copy to clipboard";
		}
		return null;
	}
}