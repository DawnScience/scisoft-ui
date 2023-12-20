/*
 * Copyright (c) 2012 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package uk.ac.diamond.scisoft.feedback.attachment;

import java.io.File;
import java.util.List;

import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.jface.viewers.CheckboxCellEditor;
import org.eclipse.jface.viewers.EditingSupport;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.widgets.Display;

/**
 * Editing Support of the table cells (the boolean delete icon)
 */
public class AttachedFileEditingSupport extends EditingSupport {
	private TableViewer tv;
	private int column;

	public AttachedFileEditingSupport(TableViewer viewer, int col) {
		super(viewer);
		tv = viewer;
		this.column = col;
	}

	@Override
	protected CellEditor getCellEditor(Object element) {
		return new CheckboxCellEditor(null, SWT.CHECK);
	}

	@Override
	protected boolean canEdit(Object element) {
		return column == 2 || column == 3;
	}

	@Override
	protected Object getValue(Object element) {
		return true;
	}

	@SuppressWarnings("unchecked")
	@Override
	protected void setValue(Object element, Object value) {
		if (column == 2) {
			List<File> files = (List<File>) tv.getInput();
			files.remove(element);
			tv.setInput(files);
			tv.refresh();
			tv.getControl().getParent().requestLayout();
		} else if (column == 3) {
			Clipboard cb = new Clipboard(Display.getDefault());
			cb.setContents(new Object[] { ((File) element).getAbsolutePath() },
					new Transfer[] { TextTransfer.getInstance()});
			cb.dispose();
		}
	}
}