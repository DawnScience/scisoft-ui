/*
 * Copyright (c) 2012 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.sda.navigator.hdf5;

import org.eclipse.core.resources.IFile;
import org.eclipse.dawnsci.analysis.api.tree.Attribute;
import org.eclipse.dawnsci.analysis.api.tree.DataNode;
import org.eclipse.dawnsci.analysis.api.tree.Node;
import org.eclipse.dawnsci.analysis.api.tree.NodeLink;
import org.eclipse.dawnsci.nexus.NexusConstants;
import org.eclipse.january.dataset.Dataset;
import org.eclipse.january.dataset.ILazyDataset;
import org.eclipse.jface.viewers.ILabelDecorator;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.navigator.IDescriptionProvider;

/**
 * Provides a label and icon for objects of type {@HDF5NodeLink}.
 */
public class HDF5LabelProvider extends LabelProvider implements ILabelProvider, ILabelDecorator, IDescriptionProvider {
	
	public static final String ID = "uk.ac.diamond.sda.navigator.hdf5Decorator";
	
	@Override
	public Image getImage(Object element) {	
		if (element instanceof Attribute) {
			return  new Image(Display.getCurrent(), getClass().getResourceAsStream("/icons/hdf5/dataset.gif"));
		}		
		NodeLink link = (NodeLink) element;
		Node node = link.getDestination();
		if (node instanceof DataNode) {
			DataNode dataset = (DataNode) node;
			if (dataset.isString()) {
				return new Image(Display.getCurrent(), getClass().getResourceAsStream("/icons/hdf5/text.gif"));
			}
			//ILazyDataset data = dataset.getDataset();
			// data
			//if (data instanceof Dataset) {
			return  new Image(Display.getCurrent(), getClass().getResourceAsStream("/icons/hdf5/dataset.gif"));
			//} else {
			//	return new Image(Display.getCurrent(), getClass().getResourceAsStream("/icons/hdf5/dataset.gif"));
			//}				
		}
		return  new Image(Display.getCurrent(), getClass().getResourceAsStream("/icons/hdf5/folderopen.gif"));
	}

	@Override
	public String getText(Object element) {
		return (element instanceof Attribute)==true ? ((Attribute) element).getName()+" ":((NodeLink) element).getName()+" ";
	}

	@Override
	public String getDescription(Object element) {
		String msg = "";
		if (element instanceof IFile) {
			IFile file = (IFile) element;
			return file.getName() + " " + file.getFullPath();
		}
		if (element instanceof Attribute) {
			Attribute attr = (Attribute) element;
			// name
			msg = attr.getName();
			return msg;
		}
		assert element instanceof NodeLink : "Not an attribute or a link";
		NodeLink link = (NodeLink) element;
		Node node = link.getDestination();
		msg = link.getName();
		if (node instanceof DataNode) {
			DataNode dataset = (DataNode) node;
			if (!dataset.isSupported()) {
				return "Not supported";
			}
		}
		return msg;
	}

	@Override
	public Image decorateImage(Image image, Object element) {
		return image;
	}

	@Override
	public String decorateText(String label, Object element) {

		if (element instanceof Attribute) {
			Attribute attr = (Attribute) element;
			// class
			label += ("Attr ");
			// dimensions
			if (attr.getSize() > 1) {
				for (int i : attr.getShape()) {
					label += i + ", ";
				}
				if (label.length() > 2)
					label += label.substring(0, label.length() - 2)+" ";
			}
			// type
			label += attr.getTypeName()+" ";
			// data
			label += attr.getSize() == 1 ? attr.getFirstElement() +" " : attr.toString()+" ";
			return label;
		}
		assert element instanceof NodeLink : "Not an attribute or a link";
		NodeLink link = (NodeLink) element;
		Node node = link.getDestination();
		// class
		Attribute attr = node.getAttribute(NexusConstants.NXCLASS);
		if(attr!=null)
			label += attr.getFirstElement()+" ";
		//label += attr.getFirstElement()+" ";
		if (node instanceof DataNode) {
			DataNode dataset = (DataNode) node;
			// class
			label+="SDS ";
			if (dataset.isString()) {
				label += dataset.getTypeName()+" ";
				label += dataset.getString()+" ";
				if (label.length() > 100) // restrict to 100 characters
					label = label.substring(0, 100) + "...";
				return label;
			}
			if (!dataset.isSupported()) {
				return label+"Not supported";
			}
			ILazyDataset data = dataset.getDataset();
			if (data == null) {
				return label + " is empty";
			}
			int[] shape = data.getShape();
			// dimensions
			for (int i : shape) {
				label += i + ", ";
			}
			if (label.length() > 2)
				label += label.substring(0, label.length()-2)+" ";
			// type
			label += dataset.getTypeName()+" ";
			// data
			if (data instanceof Dataset) {
				// show a single value
				label += ((Dataset) data).getString(0)+" ";
			} else {
				label += "Select to view";
			}
		}
		return label;
	}
}
