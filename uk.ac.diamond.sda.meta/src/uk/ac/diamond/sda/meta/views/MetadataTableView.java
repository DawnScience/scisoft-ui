/*
 * Copyright (c) 2012 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */ 

package uk.ac.diamond.sda.meta.views;

import org.eclipse.january.metadata.IMetadata;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.part.ViewPart;

public class MetadataTableView extends ViewPart {

	public static final String ID = "fable.imageviewer.views.HeaderView";

	private MetadataComposite metadataComposite;

	/**
	 * 
	 */
	public MetadataTableView() {
	}

	@Override
	public void createPartControl(final Composite parent) {
		
		metadataComposite = new MetadataComposite(parent, SWT.None);
		
	}

	@Override
	public void setFocus() {
		metadataComposite.setFocus();
	}




	public void setMeta(IMetadata meta) {
		metadataComposite.setMeta(meta);
	}
}
