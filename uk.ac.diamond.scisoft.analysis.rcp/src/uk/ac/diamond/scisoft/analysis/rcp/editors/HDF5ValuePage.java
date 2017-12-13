/*
 * Copyright (c) 2012 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package uk.ac.diamond.scisoft.analysis.rcp.editors;

import java.util.Iterator;

import org.eclipse.dawnsci.analysis.api.tree.Attribute;
import org.eclipse.dawnsci.analysis.api.tree.DataNode;
import org.eclipse.dawnsci.analysis.api.tree.GroupNode;
import org.eclipse.dawnsci.analysis.api.tree.Node;
import org.eclipse.dawnsci.analysis.api.tree.NodeLink;
import org.eclipse.jface.text.source.SourceViewer;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.IPartListener;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HDF5ValuePage extends Page  implements ISelectionListener, IPartListener {

	private static Logger logger = LoggerFactory.getLogger(HDF5ValuePage.class);
	
	protected CLabel       label;
	protected SourceViewer sourceViewer;
	protected StructuredSelection lastSelection;

	protected Composite container;

	/**
	 * Create contents of the view part.
	 * @param parent
	 */
	@Override
	public void createControl(Composite parent) {
		
		this.container = new Composite(parent, SWT.NONE);
		container.setLayout(new GridLayout(1, false));
		final GridLayout layout = (GridLayout)container.getLayout();
		layout.horizontalSpacing=0;
		layout.verticalSpacing  =0;
		layout.marginBottom     =0;
		layout.marginTop        =0;
		layout.marginLeft       =0;
		layout.marginRight      =0;
		layout.marginHeight     =0;
		layout.marginWidth      =0;
		container.setBackground(parent.getDisplay().getSystemColor(SWT.COLOR_WHITE));
		
		this.label  = new CLabel(container, SWT.LEFT);
		label.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false));
		label.setBackground(parent.getDisplay().getSystemColor(SWT.COLOR_WHITE));
		
		this.sourceViewer = new SourceViewer(container, null, SWT.H_SCROLL | SWT.V_SCROLL | SWT.READ_ONLY );
		sourceViewer.setEditable(false);
		sourceViewer.getTextWidget().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
	    		
		getSite().getPage().getWorkbenchWindow().getSelectionService().addSelectionListener(this);
		getSite().getPage().addPartListener(this);
		
		final IWorkbenchPage page = getActivePage();
		if (page != null)
			updateSelection(page.getSelection());
	}

	@Override
	public Control getControl() {
		return container;
	}
	
	@Override
	public void setFocus() {
		sourceViewer.getTextWidget().setFocus();
	}

	@Override
	public void dispose() {
		super.dispose();
		getSite().getPage().getWorkbenchWindow().getSelectionService().removeSelectionListener(this);
		getSite().getPage().removePartListener(this);
		lastSelection=null;
	}

	@Override
	public void selectionChanged(IWorkbenchPart part, ISelection selection) {
		try {
			updateSelection(selection);
		} catch (Exception e) {
			logger.error("Cannot update value", e);
		}
	}

	protected void updateSelection(ISelection selection) {
		
		if (selection instanceof StructuredSelection) {
			this.lastSelection = (StructuredSelection)selection;
			final Object sel = lastSelection.getFirstElement();
			
			updateObjectSelection(sel);				
			
			sourceViewer.refresh();
			label.getParent().layout(new Control[]{label, sourceViewer.getTextWidget()});
			
			return;
		}
		
		clear();
	}

	/**
	 * Set it back to blank
	 */
	private void clear() {
		label.setText("");
		sourceViewer.getTextWidget().setText("");
	}

	@Override
	public void partActivated(IWorkbenchPart part) {
		if (part == this) {
			try {
				updateSelection(lastSelection);
			} catch (Throwable ignored) {
				// There might not be a selection or page.
			}
		}
	}

	@Override
	public void partBroughtToTop(IWorkbenchPart part) {
		
	}

	@Override
	public void partClosed(IWorkbenchPart part) {
		
	}

	@Override
	public void partDeactivated(IWorkbenchPart part) {
		
	}

	@Override
	public void partOpened(IWorkbenchPart part) {
		
	}

	public void updateObjectSelection(Object sel) {
		
		if (sel instanceof NodeLink) {
			final NodeLink node = (NodeLink)sel;
			createH5Value(node);
		}
	}
	
	private void createH5Value(NodeLink ob) {
		
		if (ob.isDestinationData()) {
			final DataNode  set   = (DataNode)ob.getDestination();
			
			final StringBuilder buf = new StringBuilder();
			label.setText("Dataset name of '"+ob.getName()+"' value:");
			buf.append(set.toString());
			appendAttributes(set, buf);
			sourceViewer.getTextWidget().setText(buf.toString());
			
		} if (ob.isDestinationGroup()) {
			final GroupNode  grp   = (GroupNode)ob.getDestination();
			label.setText("Group name of '"+ob.getName()+"' children:");
			
			final StringBuilder buf = new StringBuilder();
			buf.append("[");
			for (Iterator<String> it = grp.getNodeNameIterator(); it.hasNext() ; ) {
				final String name = it.next();
				buf.append(name);
				
				if (it.hasNext()) {
					buf.append(", ");
					
				}
			}
			buf.append("]\n");
			
			appendAttributes(grp, buf);
			sourceViewer.getTextWidget().setText(buf.toString());

		}
	}
	
	private void appendAttributes(Node set, StringBuilder buf) {
		buf.append("\n\nAttributes:\n");
		for (Iterator<? extends Attribute> it = set.getAttributeIterator(); it.hasNext(); ) {
			final Attribute attribute = it.next();
			buf.append(attribute.getName());
			buf.append(" = ");
			buf.append(attribute.getValue().toString());
			buf.append("\n");
		}
	}

	private static IWorkbenchPage getActivePage() {
		final IWorkbench bench = PlatformUI.getWorkbench();
		if (bench == null) return null;
		final IWorkbenchWindow window = bench.getActiveWorkbenchWindow();
		if (window == null) return null;
		return window.getActivePage();
	}
	

}
