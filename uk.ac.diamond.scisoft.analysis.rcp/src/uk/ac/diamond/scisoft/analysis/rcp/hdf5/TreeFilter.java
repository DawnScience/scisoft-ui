/*
 * Copyright (c) 2012 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.analysis.rcp.hdf5;

import java.util.Collection;
import java.util.HashSet;

/**
 * Class to act as a filter for nodes of tree
 */
public class TreeFilter {
	Collection<String> unwantedNodeNames;
	Collection<String> unwantedAttributeNames;

	/**
	 * Constructor that needs an array of the names of unwanted nodes. Treated as regular expressions.
	 *
	 * @param names
	 */
	public TreeFilter(String[] attrs) {
		this(attrs, null);
	}

	/**
	 * Constructor that needs an array of the names of unwanted nodes
	 *
	 * @param names
	 */
	public TreeFilter(String[] attrs, String[] names) {
		unwantedAttributeNames = new HashSet<String>();
		unwantedNodeNames = new HashSet<String>();

		if (attrs != null) {
			for (String a: attrs) {
				unwantedAttributeNames.add(a);
			}
		}
		if (names != null) {
			for (String n: names) {
				unwantedNodeNames.add(n);
			}
		}
	}

	/**
	 * @param attr
	 * @return true if attr is not of those unwanted
	 */
	public boolean selectAttribute(String attr) {
		return !unwantedAttributeNames.contains(attr) && unwantedAttributeNames.stream().noneMatch(attr::matches);
	}

	/**
	 * @param node
	 * @return true if node is not of those unwanted
	 */
	public boolean selectNode(String node) {
		return !unwantedNodeNames.contains(node);
	}
}