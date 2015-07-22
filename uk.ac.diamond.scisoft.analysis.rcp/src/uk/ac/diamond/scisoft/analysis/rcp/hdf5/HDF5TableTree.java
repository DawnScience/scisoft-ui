/*
 * Copyright (c) 2012 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.analysis.rcp.hdf5;

import java.util.Iterator;

import org.dawb.common.ui.selection.SelectedTreeItemInfo;
import org.dawb.common.ui.selection.SelectionUtils;
import org.eclipse.dawnsci.analysis.api.dataset.IDataset;
import org.eclipse.dawnsci.analysis.api.dataset.ILazyDataset;
import org.eclipse.dawnsci.analysis.api.tree.Attribute;
import org.eclipse.dawnsci.analysis.api.tree.DataNode;
import org.eclipse.dawnsci.analysis.api.tree.GroupNode;
import org.eclipse.dawnsci.analysis.api.tree.Node;
import org.eclipse.dawnsci.analysis.api.tree.NodeLink;
import org.eclipse.dawnsci.analysis.api.tree.SymbolicNode;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.ILazyTreeContentProvider;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.ITreeSelection;
import org.eclipse.jface.viewers.TreePath;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.TreeViewerColumn;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeColumn;

import uk.ac.diamond.scisoft.analysis.io.NexusTreeUtils;
import uk.ac.diamond.scisoft.analysis.rcp.adapters.HDF5Adaptable;

/**
 * Class to contain a table-tree view of a HDF5 tree
 */
public class HDF5TableTree extends Composite implements ISelectionProvider {

	private TreeViewer tViewer = null;
	private Listener clistener, slistener, dlistener;
	private TreeFilter treeFilter;
	private Menu headerMenu;
	private Menu treeMenu;
	private String filename;

	private static final String MSG_ENABLED  = "Use this item as comparison value";
	private static final String MSG_DISABLED = "Cannot use the item";

	/**
	 * @param parent
	 * @param slistener for single clicks
	 * @param dlistener for double clicks
	 * @param clistener for context click
	 */
	public HDF5TableTree(Composite parent, Listener slistener, Listener dlistener, Listener clistener) {
		super(parent, SWT.NONE);
		setLayout(new FillLayout());
		this.slistener = slistener;
		this.dlistener = dlistener;
		this.clistener = clistener;

		// set up tree filter to omit following node names
		treeFilter = new TreeFilter(new String[] { "target", NexusTreeUtils.NX_CLASS});

		// set up tree and its columns
		tViewer = new TreeViewer(this, SWT.BORDER|SWT.VIRTUAL);
		tViewer.setUseHashlookup(true);

		final Tree tree = tViewer.getTree();
		tree.setHeaderVisible(true);

		String[] titles = { "Name", "Class", "Dims", "Type", "Data" };
		int[] widths = { 250, 120, 80, 60, 300 };

		TreeViewerColumn tVCol;
		headerMenu = new Menu(parent.getShell(), SWT.POP_UP);

		for (int i = 0; i < titles.length; i++) {
			tVCol = new TreeViewerColumn(tViewer, SWT.NONE);
			final TreeColumn tCol = tVCol.getColumn();
			tCol.setText(titles[i]);
			tCol.setWidth(widths[i]);
			tCol.setMoveable(true);
			final MenuItem item = new MenuItem(headerMenu, SWT.CHECK);
			item.setText(titles[i]);
			item.setSelection(true);
			item.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					if (!item.getSelection()) {
						int width = tCol.getWidth();
						tCol.setData("restoredWidth", Integer.valueOf(width));
						tCol.setWidth(0);
					} else {
						int width = (Integer) tCol.getData("restoredWidth");
						tCol.setWidth(width);
					}
				}
			});
		}

		treeMenu = new Menu(parent.getShell(), SWT.POP_UP);

		// TODO make context menu dependent on node (use a SWT.Show listener on menu)
		if (clistener != null) {
			treeMenu.addListener(SWT.Show, new Listener() {
				@Override
				public void handleEvent(Event event) {
					// get selection and decide
					ITreeSelection sel = (ITreeSelection) tViewer.getSelection();
					Object obj = sel.getFirstElement();
					boolean enable = false;
					if (obj instanceof NodeLink) {
						NodeLink link = (NodeLink) obj;
						enable = link.isDestinationData() && !((DataNode) link.getDestination()).isString();
					} else if (obj instanceof Attribute) {
						enable = !((Attribute) obj).isString();
					}

					for (MenuItem m : treeMenu.getItems()) {
						m.setEnabled(enable);
						m.setText(enable ? MSG_ENABLED : MSG_DISABLED);
					}
				}
			});
			MenuItem item = new MenuItem(treeMenu, SWT.PUSH);
			item.addListener(SWT.Selection, clistener);
		}

		tree.addListener(SWT.MenuDetect, new Listener() {
			@Override
			public void handleEvent(Event event) {
				Point pt = getDisplay().map(null, tree, new Point(event.x, event.y));
				Rectangle clientArea = tree.getClientArea();
				boolean header = clientArea.y <= pt.y && pt.y < (clientArea.y + tree.getHeaderHeight());
				tree.setMenu(header ? headerMenu : treeMenu);
			}
		});

		tViewer.setContentProvider(new HDF5LazyContentProvider(tViewer, treeFilter));
		tViewer.setLabelProvider(new HDF5LabelProvider());
		if (slistener != null)
			tree.addListener(SWT.MouseUp, slistener);
		if (dlistener != null)
			tree.addListener(SWT.MouseDoubleClick, dlistener);
	}
	
	public Viewer getViewer() {
		return tViewer;
	}

	@Override
	public void dispose() {
		if (slistener != null)
			tViewer.getTree().removeListener(SWT.MouseUp, slistener);
		if (dlistener != null)
			tViewer.getTree().removeListener(SWT.MouseDoubleClick, dlistener);

		if (clistener != null) {
			for (MenuItem m : treeMenu.getItems())
				m.removeListener(SWT.Selection, clistener);
		}

		tViewer.getTree().dispose();

		if (headerMenu != null && !headerMenu.isDisposed())
			headerMenu.dispose();

		if (treeMenu != null && !treeMenu.isDisposed())
			treeMenu.dispose();

		super.dispose();
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


	/**
	 * @param tree given by a node link
	 */
	public void setInput(NodeLink tree) {
		if (tViewer != null && tViewer.getContentProvider() != null) {
		    tViewer.setInput(tree);
//		    TODO decide whether this is needed
//		    tViewer.getTree().setItemCount(countChildren(tree, treeFilter));
		}
	}

	/**
	 * Refresh viewer's content
	 */
	public void refresh() {
		if (tViewer != null && !isDisposed()) {
			tViewer.refresh();
		}
	}


	/**
	 * @return selection
	 */
	@Override
	public IStructuredSelection getSelection() {
		IStructuredSelection selection = (IStructuredSelection) tViewer.getSelection();
		if (selection instanceof ITreeSelection) {
			SelectedTreeItemInfo[] results = SelectionUtils.parseAsTreeSelection((ITreeSelection) selection);
			if (results.length > 0 && results[0].getNode() != null) {
				return new HDF5TreeSelection(new HDF5Adaptable(filename, results[0].getNode(), results[0].getItem()));
			}
		}
		return selection;
	}

	@Override
	public void addSelectionChangedListener(ISelectionChangedListener listener) {
	}

	@Override
	public void removeSelectionChangedListener(ISelectionChangedListener listener) {
	}

	@Override
	public void setSelection(ISelection selection) {
		if (selection instanceof IStructuredSelection) {
			tViewer.setSelection(selection);
		}
	}

	public void expandAll() {
		tViewer.expandAll();
	}

	public void expandToLevel(int level) {
		tViewer.expandToLevel(level);
	}
	
	public void expandToLevel(Object link, int level) {
		tViewer.expandToLevel(link, level);
	}
	
	public TreePath[] getExpandedTreePaths() {
		return tViewer.getExpandedTreePaths();
	}

	public void setFilename(String filename) {
		this.filename = filename;
	}

}

class HDF5LazyContentProvider implements ILazyTreeContentProvider {
	private TreeViewer viewer;
	private TreeFilter filter;

	public HDF5LazyContentProvider(TreeViewer treeViewer, TreeFilter treeFilter) {
		filter = treeFilter;
		viewer = treeViewer;
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
	public void updateChildCount(Object element, int currentChildCount) {
		// count number of nodes that will not be filtered out
		int count = HDF5TableTree.countChildren(element, filter);
		if (count != currentChildCount)
			viewer.setChildCount(element, count);
	}

	@Override
	public void updateElement(Object parent, final int index) {
		if (parent instanceof Attribute) {
			return;
		}

		assert parent instanceof NodeLink : "Not an attribute or a link";

		Node pNode = ((NodeLink) parent).getDestination();

		int count = 0;
		Iterator<String> iter = pNode.getAttributeNameIterator();
		while (iter.hasNext()) {
			String name = iter.next();
			if (filter.select(name)) {
				if (index == count) {
					Attribute a = pNode.getAttribute(name);
					viewer.replace(parent, index, a);
					updateChildCount(a, -1);
					return;
				}
				count++;
			}
		}

		if (pNode instanceof GroupNode) {
			for (NodeLink link : (GroupNode) pNode) {
				if (link.isDestinationSymbolic()) {
					SymbolicNode slink = (SymbolicNode) link.getDestination();
					link = slink.getNodeLink();
				}
				if (link.isDestinationGroup()) {
					String name = link.getName();
					if (filter.select(name)) {
						if (index == count) {
							viewer.replace(parent, index, link);
							updateChildCount(link, -1);
							return;
						}
					}
					count++;
				}
			}

			for (NodeLink link : (GroupNode) pNode) {
				if (link.isDestinationSymbolic()) {
					SymbolicNode slink = (SymbolicNode) link.getDestination();
					link = slink.getNodeLink();
				}
				if (link.isDestinationData()) {
					String name = link.getName();
					if (filter.select(name)) {
						if (index == count) {
							viewer.replace(parent, index, link);
							updateChildCount(link, -1);
							return;
						}
					}
					count++;
				}
			}

		} else if (pNode instanceof DataNode) {
			// do nothing
		}
	}

	@Override
	public void dispose() {
	}

	@Override
	public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
	}
}

class HDF5LabelProvider implements ITableLabelProvider {
//	private static final Logger logger = LoggerFactory.getLogger(HDF5LabelProvider.class);
	
	@Override
	public Image getColumnImage(Object element, int columnIndex) {
		return null;
	}

	
	@Override
	public String getColumnText(Object element, int columnIndex) {
		String msg = "";

		if (element instanceof Attribute) {
			Attribute attr = (Attribute) element;
			switch (columnIndex) {
			case 0: // name
				msg = attr.getName();
				break;
			case 1: // class
				msg = "Attr";
				break;
			case 2: // dimensions
				if (attr.getSize() > 1) {
					for (int i : attr.getShape()) {
						msg += i + ", ";
					}
					if (msg.length() > 2)
						msg = msg.substring(0, msg.length() - 2);
				}
				break;
			case 3: // type
				msg = attr.getTypeName();
				break;
			case 4: // data
				msg = attr.getSize() == 1 ? attr.getFirstElement() : attr.toString();
				break;
			}

			return msg;
		}

		assert element instanceof NodeLink : "Not an attribute or a link";

		NodeLink link = (NodeLink) element;
		Node node = link.getDestination();

		switch (columnIndex) {
		case 0: // name
			msg = link.getName();
			break;
		case 1: // class
			Attribute attr = node.getAttribute(NexusTreeUtils.NX_CLASS);
			msg = attr != null ? attr.getFirstElement() : "Group";
			break;
		}

		if (node instanceof DataNode) {
			DataNode dataset = (DataNode) node;

			if (columnIndex == 1) { // class
				return "SDS";
			}

			if (dataset.isString()) {
				switch (columnIndex) {
				case 3:
					msg = dataset.getTypeName();
					break;
				case 4:
					msg = dataset.getString();
					if (msg.length() > 100) // restrict to 100 characters
						msg = msg.substring(0, 100) + "...";
					break;
				}
				return msg;
			}

			if (!dataset.isSupported()) {
				return columnIndex == 4 ? "Not supported" : msg;
			}

			ILazyDataset data = dataset.getDataset();
			switch (columnIndex) {
			case 2: // dimensions
				if (data == null) {
					int rank = dataset.getMaxShape().length;
					for (int i = 0; i < rank; i++) {
						msg += "0, ";
					}
				} else {
					int[] shape = data.getShape();
					for (int i : shape) {
						msg += i + ", ";
					}
				}
				if (msg.length() > 2)
					msg = msg.substring(0, msg.length() - 2);
				break;
			case 3: // type
				msg = dataset.getTypeName();
				break;
			case 4: // data
				if (data instanceof IDataset) {
					// show a single value
					msg = data.getRank() == 0 ? ((IDataset) data).getString() :
						((IDataset) data).getString(0);
					Attribute units = dataset.getAttribute("units");
					if (units != null && units.isString()) {
						msg += " " + units.getFirstElement();
					}
				} else if (data == null || data.getSize() == 0) {
					msg = "none available as dataset is zero-sized";
				} else if (data.getSize() == 1) {
					msg = data.getSlice().getString(0);
					Attribute units = dataset.getAttribute("units");
					if (units != null && units.isString()) {
						msg += " " + units.getFirstElement();
					}
				} else {
					msg = "double-click to view";
				}
				break;
			}
		}
		return msg;
	}

	@Override
	public void addListener(ILabelProviderListener listener) {
	}

	@Override
	public void dispose() {
	}

	@Override
	public boolean isLabelProperty(Object element, String property) {
		return false;
	}

	@Override
	public void removeListener(ILabelProviderListener listener) {
	}
	
}
