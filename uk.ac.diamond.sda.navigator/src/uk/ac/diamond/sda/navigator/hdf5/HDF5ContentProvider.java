/*
 * Copyright (c) 2012 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.sda.navigator.hdf5;

import java.util.Iterator;

import org.eclipse.core.resources.IFile;
import org.eclipse.dawnsci.analysis.api.tree.Attribute;
import org.eclipse.dawnsci.analysis.api.tree.DataNode;
import org.eclipse.dawnsci.analysis.api.tree.GroupNode;
import org.eclipse.dawnsci.analysis.api.tree.Node;
import org.eclipse.dawnsci.analysis.api.tree.NodeLink;
import org.eclipse.dawnsci.analysis.api.tree.TreeFile;
import org.eclipse.dawnsci.nexus.NexusConstants;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.Viewer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.io.HDF5Loader;

public class HDF5ContentProvider implements ITreeContentProvider {
	private TreeFilter treeFilter;
	public static final String H5_EXT = "h5"; //$NON-NLS-1$
	public static final String HDF5_EXT = "hdf5"; //$NON-NLS-1$
	public static final String NXS_EXT = "nxs"; //$NON-NLS-1$
	private HDF5Loader loader;
	private String fileName;
	private IFile modelFile;
	private TreeFile hdf5Tree;
	private static final Object[] NO_CHILDREN = new Object[0];
	private static final Logger logger = LoggerFactory.getLogger(HDF5ContentProvider.class);

	public HDF5ContentProvider() {
		this.treeFilter = new TreeFilter(new String[] { "target", NexusConstants.NXCLASS});
	}

	@Override
	public Object[] getChildren(Object parent) {
		Object[] children = NO_CHILDREN;
		
		if (parent instanceof IFile) {
			modelFile = (IFile) parent;
		
			if (H5_EXT.equals(modelFile.getFileExtension())||HDF5_EXT.equals(modelFile.getFileExtension())||NXS_EXT.equals(modelFile.getFileExtension())) {
				loadHDF5Data(modelFile);
				GroupNode pNode = hdf5Tree.getGroupNode();

				children = new Object[pNode.getNumberOfNodelinks()];
				int count = 0;
				for (NodeLink link : pNode) {
					children[count] = link;
					count++;
				}
				return children;
			}
		}
		if (parent instanceof Attribute) {
			return null;
		}
		assert parent instanceof NodeLink : "Not an attribute or a link";
		Node pNode = ((NodeLink) parent).getDestination();
		
		int count = 0;
		Iterator<String> iter = pNode.getAttributeNameIterator();
		children = new Object[countChildren(parent, treeFilter)];

		while (iter.hasNext()) {
			String name = iter.next();
			if (treeFilter.select(name)) {
				Attribute a = pNode.getAttribute(name);
				children[count] = a;
				count++;
			}
		}
		if (pNode instanceof GroupNode) {
			for (NodeLink link : (GroupNode) pNode) {
				if (link.isDestinationGroup()) {
					String name = link.getName();
					if (treeFilter.select(name)) {
						children[count] = link;
						count++;
					}
				}
			}
			for (NodeLink link : (GroupNode) pNode) {
				if (link.isDestinationData()) {
					String name = link.getName();
					if (treeFilter.select(name)) {
						children[count] = link;
						count++;
					}
				}
			}

		} else if (pNode instanceof DataNode) {
			// do nothing
		}
		return children;
	}

	@Override
	public Object[] getElements(Object inputElement) {
		return getChildren(inputElement);
	}

	@Override
	public boolean hasChildren(Object element) {
		if((element instanceof NodeLink) && ((countChildren(element, treeFilter) > 0)) || (element instanceof IFile))
			return true;
		return false;
	}

	@Override
	public Object getParent(Object element) {
		if (element == null || !(element instanceof NodeLink)) {
			return null;
		}
		Node node = ((NodeLink) element).getSource();
		if (node == null)
			return element;
		return node;
	}

	@Override
	public void dispose() {
//		cachedModelMap.clear();
	}

	@Override
	public void inputChanged(Viewer aViewer, Object oldInput, Object newInput) {
//		if (oldInput != null && !oldInput.equals(newInput)) {
//			cachedModelMap.clear();
//		}
//		viewer = (StructuredViewer) aViewer;
	}

	/**
	 * Load the HDF5 tree from the given file, if possible.
	 * 
	 * @param file
	 *            The IFile which contains the hdf5 tree
	 */
	private void loadHDF5Data(IFile file) {
		fileName = file.getLocation().toString();
		try {
			loader = new HDF5Loader(fileName);
			loader.setAsyncLoad(true);
			hdf5Tree = loader.loadTree(null);
		} catch (Exception e) {
			logger.warn("Could not load NeXus file {}", fileName);
		}
	}

	public static int countChildren(Object element, TreeFilter filter) {
		int count = 0;
		if (element instanceof Attribute) {
			return 0;
		}
		if (element instanceof NodeLink) {
			Node node = ((NodeLink) element).getDestination();
			Iterator<String> iter = node.getAttributeNameIterator();
			while (iter.hasNext()) {
				if (filter.select(iter.next()))
					count++;
			}
			if (node instanceof GroupNode) {
				GroupNode group = (GroupNode) node;
				Iterator<String> nIter = group.getNodeNameIterator();
				while (nIter.hasNext()) {
					if (filter.select(nIter.next()))
						count++;
				}
			}
			if (node instanceof DataNode) {
				// do nothing?
			}
		}
		return count;
	}
}
