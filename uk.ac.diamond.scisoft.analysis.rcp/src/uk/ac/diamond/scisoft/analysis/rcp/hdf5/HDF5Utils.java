/*
 * Copyright (c) 2012 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.analysis.rcp.hdf5;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.dawnsci.analysis.api.tree.Attribute;
import org.eclipse.dawnsci.analysis.api.tree.DataNode;
import org.eclipse.dawnsci.analysis.api.tree.GroupNode;
import org.eclipse.dawnsci.analysis.api.tree.Node;
import org.eclipse.dawnsci.analysis.api.tree.NodeLink;
import org.eclipse.january.dataset.Dataset;
import org.eclipse.january.dataset.DatasetFactory;
import org.eclipse.january.dataset.ILazyDataset;
import org.eclipse.january.dataset.IntegerDataset;
import org.eclipse.january.metadata.AxesMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.axis.AxisChoice;
import uk.ac.diamond.scisoft.analysis.io.NexusTreeUtils;
import uk.ac.diamond.scisoft.analysis.rcp.explorers.AbstractExplorer;
import uk.ac.diamond.scisoft.analysis.rcp.inspector.AxisSelection;
import uk.ac.diamond.scisoft.analysis.rcp.inspector.DatasetSelection.InspectorType;

public class HDF5Utils {
	
	private static final Logger logger = LoggerFactory.getLogger(HDF5Utils.class);

	/**
	 * Create a (HDF5) dataset selection from given node link. It defaults the inspector type
	 * to a line and leaves file name null
	 * @param filePath
	 * @param nodePath
	 * @param link
	 * @return HDF5 selection
	 */
	public static HDF5Selection createDatasetSelection(String filePath, String nodePath, NodeLink link) {
		Node node = link.getDestination();
		DataNode dNode = null;
		if (link.isDestinationGroup()) {
			GroupNode gNode = (GroupNode) node;
			// see if chosen node is a NXdata class
			Attribute attr;
			if (NexusTreeUtils.isNXClass(gNode, NexusTreeUtils.NX_DATA)) {
				// check if group has signal attribute
				String n = NexusTreeUtils.getFirstString(gNode.getAttribute(NexusTreeUtils.NX_SIGNAL));
				if (n != null) {
					if (gNode.containsDataNode(n)) {
						dNode = gNode.getDataNode(n);
					} else {
						logger.warn("Signal attribute in group points to a missing dataset called {}", n);
					}
				} else {
					logger.warn("Signal attribute in group is not a string");
				}
				if (dNode == null) {
					// find data (@signal=1)
					for (NodeLink l : gNode) {
						if (l.isDestinationData()) {
							dNode = (DataNode) l.getDestination();
							attr = dNode.getAttribute(NexusTreeUtils.NX_SIGNAL);
							if (attr != null && NexusTreeUtils.parseFirstInt(attr) == 1 && dNode.isSupported()) {
								link = l;
								break; // only one signal per NXdata item
							}
							dNode = null;
						}
					}
				}
			}
			if (dNode == null && gNode.containsDataNode(NexusTreeUtils.DATA)) {
				// fallback to "data" when no signal attribute is found
				dNode = gNode.getDataNode(NexusTreeUtils.DATA);
			}
		} else if (link.isDestinationData()) {
			dNode = (DataNode) link.getDestination();
		}

		if (dNode == null) return null;
		if (!dNode.isAugmented()) {
			try {
				NexusTreeUtils.augmentNodeLink(filePath, link, true);
			} catch (Exception e) {
				logger.debug("Problem augmenting node: {}", link, e);
			}
		}
		ILazyDataset cData = dNode.getDataset(); // chosen dataset

		if (cData == null || cData.getSize() == 0) {
			logger.warn("Chosen data {}, has zero size", dNode);
			return null;
		}

		if (cData.getSize() == 1) {
			logger.warn("Chosen data {}, has only one value", dNode);
			return null;
		}

		int[] shape = cData.getShape();
		int rank = shape.length;
		List<AxisSelection> axes = null;
		try {
			List<AxesMetadata> lamd = cData.getMetadata(AxesMetadata.class);
			if (lamd != null && lamd.size() > 0) {
				axes = new ArrayList<AxisSelection>();
				AxesMetadata amd = lamd.get(0);
				for (int i = 0; i < rank; i++) {
					ILazyDataset[] a = amd.getAxis(i);
					int len = shape[i];
					AxisSelection sel = new AxisSelection(len, i);
					axes.add(sel);
					if (a != null && a.length != 0) {
						sel.setChoices(a);
					}
					// add in an automatically generated axis with top order so it appears after primary axes
					Dataset axis = DatasetFactory.createRange(IntegerDataset.class, len);
					int[] ashape = new int[rank];
					Arrays.fill(ashape, 1);
					ashape[i] = len;
					axis.setShape(ashape);
					axis.setName(AbstractExplorer.DIM_PREFIX + (i + 1));
					AxisChoice newChoice = new AxisChoice(axis);
					newChoice.setAxisNumber(i);
					sel.addChoice(newChoice, 0);
				}
			}
		} catch (Exception e) {
			logger.error("Problem retrieving axes metadata", e);
		}

		InspectorType itype = InspectorType.LINE;
		if (rank > 1) {
			if (shape[rank - 2] > 1) // only set image type in this case
				itype = InspectorType.IMAGE;
		}
		return new HDF5Selection(itype, filePath, nodePath, axes, cData);
	}
}
