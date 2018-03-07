/*
 * Copyright (c) 2012 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.analysis.rcp.editors;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.PlatformObject;
import org.eclipse.dawnsci.analysis.api.io.IDataHolder;
import org.eclipse.dawnsci.analysis.api.tree.Node;
import org.eclipse.january.DatasetException;
import org.eclipse.january.IMonitor;
import org.eclipse.january.MetadataException;
import org.eclipse.january.dataset.AggregateDataset;
import org.eclipse.january.dataset.DTypeUtils;
import org.eclipse.january.dataset.Dataset;
import org.eclipse.january.dataset.DatasetFactory;
import org.eclipse.january.dataset.DatasetUtils;
import org.eclipse.january.dataset.DoubleDataset;
import org.eclipse.january.dataset.IDataset;
import org.eclipse.january.dataset.ILazyDataset;
import org.eclipse.january.dataset.IMetadataProvider;
import org.eclipse.january.dataset.IndexIterator;
import org.eclipse.january.dataset.IntegerDataset;
import org.eclipse.january.dataset.LazyDataset;
import org.eclipse.january.dataset.Maths;
import org.eclipse.january.dataset.PositionIterator;
import org.eclipse.january.dataset.ShapeUtils;
import org.eclipse.january.dataset.SliceND;
import org.eclipse.january.io.ILazyLoader;
import org.eclipse.january.metadata.IMetadata;
import org.eclipse.january.metadata.MetadataType;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.jface.viewers.CellLabelProvider;
import org.eclipse.jface.viewers.CheckboxCellEditor;
import org.eclipse.jface.viewers.ComboBoxViewerCellEditor;
import org.eclipse.jface.viewers.EditingSupport;
import org.eclipse.jface.viewers.ICellEditorListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.TextCellEditor;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerCell;
import org.eclipse.jface.viewers.ViewerDropAdapter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.FileTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.dnd.TransferData;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.ui.IEditorDescriptor;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorRegistry;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.IPersistableElement;
import org.eclipse.ui.IWorkbenchPartSite;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.forms.events.ExpansionAdapter;
import org.eclipse.ui.forms.events.ExpansionEvent;
import org.eclipse.ui.forms.widgets.ExpandableComposite;
import org.eclipse.ui.part.EditorPart;
import org.nfunk.jep.JEP;
import org.nfunk.jep.ParseException;
import org.nfunk.jep.SymbolTable;
import org.nfunk.jep.Variable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.axis.AxisChoice;
import uk.ac.diamond.scisoft.analysis.io.AbstractFileLoader;
import uk.ac.diamond.scisoft.analysis.io.Utils;
import uk.ac.diamond.scisoft.analysis.rcp.AnalysisRCPActivator;
import uk.ac.diamond.scisoft.analysis.rcp.explorers.AbstractExplorer;
import uk.ac.diamond.scisoft.analysis.rcp.explorers.MetadataSelection;
import uk.ac.diamond.scisoft.analysis.rcp.hdf5.HDF5Selection;
import uk.ac.diamond.scisoft.analysis.rcp.inspector.AxisSelection;
import uk.ac.diamond.scisoft.analysis.rcp.inspector.DatasetSelection;
import uk.ac.diamond.scisoft.analysis.rcp.inspector.DatasetSelection.InspectorType;

/**
 * This editor allows a set of files which can be loaded by one type of loader to be compared. It
 * lets the user select the dataset per file and which (metadata) value(s) to use per dataset. This
 * selection is pushed onto the dataset inspector.
 */
public class CompareFilesEditor extends EditorPart implements ISelectionChangedListener, ISelectionProvider {
	/**
	 * Name of index dataset
	 */
	public final static String INDEX = "index";

	/**
	 * Factory to create proper input object for this editor
	 * @param sel
	 * @return compare files editor input
	 */
	public static IEditorInput createComparesFilesEditorInput(IStructuredSelection sel) {
		return new CompareFilesEditorInput(sel);
	}

	private static final Logger logger = LoggerFactory.getLogger(CompareFilesEditor.class);

	public final static String ID = "uk.ac.diamond.scisoft.analysis.rcp.editors.CompareFilesEditor";
	private SashForm sashComp;
	private List<SelectedFile> fileList;
	private List<SelectedNode> expressionList;
	private SortedMap<String, VariableMapping> variableMap;
	private TableViewer viewer, expressionViewer, variableViewer;
	private Class<? extends AbstractExplorer> expClass = null;
	private AbstractExplorer explorer;
	private String firstFileName;

	private boolean useRowIndexAsValue = true;
	private DatasetSelection currentDatasetSelection; // from explorer
	private DatasetSelection multipleSelection; // from this editor

	private TableColumn valueColumn, variableColumn;

	private FileDialog fileDialog;

	private String editorName;

	private CFEditingSupport variableEditor;

	private final static String VALUE_DEFAULT_TEXT = "Index";
	private final static String DEFAULT_EXPRESSION = "a + b";

	@Override
	public void doSave(IProgressMonitor monitor) {
	}

	@Override
	public void doSaveAs() {
	}

	@Override
	public void init(IEditorSite site, IEditorInput input) throws PartInitException {
		if (!(input instanceof CompareFilesEditorInput))
			throw new PartInitException("Invalid input for comparison");

		setSite(site);
		try {
			setInput(input);
		} catch (Exception e) {
			throw new PartInitException("Invalid input for comparison", e);
		}
	}

	@Override
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public void setInput(IEditorInput input) {
		if (!(input instanceof CompareFilesEditorInput)) {
			return;
		}
		super.setInput(input);
		CompareFilesEditorInput filesInput = (CompareFilesEditorInput) input;
		fileList = new ArrayList<SelectedFile>();
		expressionList = new ArrayList<SelectedNode>();
		variableMap = new TreeMap<String, VariableMapping>();
		
		int n = 0;
		int l = 0;
		while (l < filesInput.list.length) {
			Object o = filesInput.list[l++];
			if (o instanceof IFile) {
				IFile f = (IFile) o;
				try {
					fileList.add(new SelectedFile(n, f));
					n++;
					break;
				} catch (IllegalArgumentException e) {
					logger.warn("Problem with selection: ", e);
				}
			} else if (o instanceof File) {
				File pf = (File) o;
				try {
					fileList.add(new SelectedFile(n, pf));
					n++;
					break;
				} catch (IllegalArgumentException e) {
					logger.warn("Problem with selection: ", e);
				}
				
			}
			
		}
		if (n == 0) {
			// TODO error
			return;
		}

		firstFileName = fileList.get(0).getAbsolutePath();
		List<String> eList = getEditorCls(firstFileName);
		editorName = null;
		for (String e : eList) {
			try {
				Class edClass = Class.forName(e);
				Method m = edClass.getMethod("getExplorerClass");
				editorName = e;
				expClass = (Class) m.invoke(null);
				break;
			} catch (Exception e1) {
			}
		}
		if (expClass == null) {
			throw new IllegalArgumentException("No explorer available to read " + firstFileName);
		}

		while (l < filesInput.list.length) {
			Object o = filesInput.list[l++];
			if (o instanceof IFile) {
				IFile f = (IFile) o;
				try {
					SelectedFile sf = new SelectedFile(n, f);
					String name = sf.getAbsolutePath();
					if (!getEditorCls(name).contains(editorName)) {
						logger.warn("Editor cannot read file: {}", name);
					}

					fileList.add(sf);
					n++;
				} catch (IllegalArgumentException e) {
					logger.warn("Problem with selection: ", e);
				}
			} else if (o instanceof File) {
				File pf = (File) o;
				try {
					SelectedFile sf = new SelectedFile(n, pf);
					String name = sf.getAbsolutePath();
					if (!getEditorCls(name).contains(editorName)) {
						logger.warn("Editor cannot read file: {}", name);
					}

					fileList.add(sf);
					n++;
				} catch (IllegalArgumentException e) {
					logger.warn("Problem with selection: ", e);
				}
			}
		}

		if (n != filesInput.list.length) {
			// TODO warning
		}

		setPartName(input.getToolTipText());
	}

	/**
	 * Add new file to comparison list
	 * @param path
	 * @return true, if file can be added
	 */
	public boolean addFile(String path) {
		return addFile(path, getNewIndex());
	}

	/**
	 * Add new file to comparison list at index
	 * @param path
	 * @param index
	 * @return true, if file can be added
	 */
	private boolean addFile(String path, int index) {
		if (path == null) {
			return false;
		}
		SelectedFile sf = createSelectedFile(path);
		if (sf == null) {
			return false;
		}
		if (index == 0) {
			logger.warn("Cannot add file to top of order");
			index = 1;
		}
		sf.setIndex(index);
		fileList.add(sf);
		if (currentDatasetSelection != null) {
			changeSelection();
		} else {
			viewer.refresh();
		}
		return true;
	}

	private SelectedFile createSelectedFile(String path) {
		try {
			SelectedFile sf = new SelectedFile(fileList.size(), path);
			String name = sf.getAbsolutePath();
			if (!getEditorCls(name).contains(editorName)) {
				logger.warn("Editor cannot read file: {}", name);
				return null;
			}

			return sf;
		} catch (IllegalArgumentException e) {
			logger.warn("Problem with new file: ", e);
			return null;
		}
	}

	/**
	 * call to bring up a file dialog
	 */
	public void addFileUsingFileDialog() {
		if (fileDialog == null) {
			fileDialog = new FileDialog(getSite().getShell(), SWT.OPEN);
		}

		final String path = fileDialog.open();

		if (path != null) {
			addFile(path);
		}
	}

	@Override
	public boolean isDirty() {
		return false;
	}

	@Override
	public boolean isSaveAsAllowed() {
		return false;
	}

	@Override
	public void dispose() {
		if (explorer != null)
			explorer.dispose();
		if (sashComp != null)
			sashComp.dispose();
		if (viewer != null)
			viewer.getControl().dispose();
		if (expressionViewer != null)
			expressionViewer.getControl().dispose();
		if (variableViewer != null)
			variableViewer.getControl().dispose();

		super.dispose();
	}

	private enum Column {
		TICK, COMBO, PATH, VALUE, EXPRESSION, VARIABLE;
	}

	private class TickLabelProvider extends CellLabelProvider {
		private final Image TICK = AnalysisRCPActivator.getImageDescriptor("icons/tick.png").createImage();
		private Display display;

		public TickLabelProvider(Display display) {
			this.display = display;
		}

		@Override
		public void update(ViewerCell cell) {
			Object obj = cell.getElement();
			if (obj instanceof SelectedObject) {
				SelectedObject sf = (SelectedObject) obj;
				if (sf.doUse()) {
					cell.setImage(TICK);
				} else {
					cell.setImage(null);
				}
				Color colour = null;
				if (currentDatasetSelection != null && (!sf.hasData() || !sf.isDataOK())) {
					colour = display.getSystemColor(SWT.COLOR_YELLOW);
				}
				cell.setBackground(colour);
			}
		}
	}

	private class VariableLabelProvider extends CellLabelProvider {
		@Override
		public void update(ViewerCell cell) {
			Object element = cell.getElement();
			if (element instanceof SelectedFile) {
				SelectedFile sf = (SelectedFile) element;
				String var = sf.getVariableName();
				if (var != null) {
					cell.setText(var);
				}
			}
		}
	}

	private static class PathLabelProvider extends CellLabelProvider {
		private Display display;

		public PathLabelProvider(Display display) {
			this.display = display;
		}

		@Override
		public void update(ViewerCell cell) {
			SelectedFile sf = (SelectedFile) cell.getElement();
			String path = sf.getAbsolutePath(); 
			if (path != null) {
				cell.setText(new File(path).getName());
			}
			cell.setForeground(sf.doUse() ? null : display.getSystemColor(SWT.COLOR_GRAY));
		}

	}

	private class ValueLabelProvider extends CellLabelProvider {
		private Display display;

		public ValueLabelProvider(Display display) {
			this.display = display;
		}

		@Override
		public void update(ViewerCell cell) {
			SelectedObject sf = (SelectedObject) cell.getElement();
			Color colour = null;
			if (useRowIndexAsValue) {
				cell.setText(String.valueOf(sf.getIndex()));
			} else {
				cell.setText(sf.toString());
				if (!sf.hasMetadataValue()) {
					colour = display.getSystemColor(SWT.COLOR_YELLOW);
				}
			}
			cell.setBackground(colour);
			cell.setForeground(sf.doUse() ? null : display.getSystemColor(SWT.COLOR_GRAY));
		}
	}

	private class ExpressionLabelProvider extends CellLabelProvider {
		@Override
		public void update(ViewerCell cell) {
			SelectedNode expr = (SelectedNode) cell.getElement();
			cell.setText(expr.toString());
		}
	}

	private class VariableNameLabelProvider extends CellLabelProvider {
		@Override
		public void update(ViewerCell cell) {
			VariableMapping var = (VariableMapping) cell.getElement();
			cell.setText(var.getName());
		}
	}

	private class ComboLabelProvider extends CellLabelProvider {
		@Override
		public void update(ViewerCell cell) {
			VariableMapping vm = (VariableMapping) cell.getElement();
			cell.setText(vm.getMathOp().name());
		}
	}

	private class IndexLabelProvider extends CellLabelProvider {
		@Override
		public void update(ViewerCell cell) {
			VariableMapping var = (VariableMapping) cell.getElement();
			String name = var.getName();
			List<String> idx = new ArrayList<String>();
			if (variableMap.containsKey(name)) {
				VariableMapping vm = variableMap.get(name);
				for (SelectedObject obj: vm.getDatasets()) {
					idx.add(String.valueOf(obj.getIndex()));
				}
			}
			Collections.sort(idx);
			cell.setText(idx.toString());
		}
	}

	private class FileSelection extends StructuredSelection implements IMetadataProvider {
		private IMetadata metadata = null;

		public FileSelection(SelectedFile f) {
			super(f.f);
			if (!f.hasDataHolder()) {
				try {
					IDataHolder holder = explorer.loadFile(f.getAbsolutePath(), null);
					if (holder != null) {
						f.setDataHolder(holder);
					}
				} catch (Exception e) {
				}
			}
			if (f.hasMetadata()) {
				metadata = f.m;
			}
		}

		@Override
		public IMetadata getMetadata() throws MetadataException {
			return metadata;
		}

		@SuppressWarnings("unchecked")
		@Override
		public <S extends MetadataType, T extends S> List<S> getMetadata(Class<T> clazz) throws MetadataException {
			if (IMetadata.class.isAssignableFrom(clazz)) {
				List<S> result = new ArrayList<S>();
				result.add((T) getMetadata());
				return result;
			}
			throw new MetadataException("getMetadata(clazz) does not currently support anything other than IMetadata");
			// If it should only support this, simply return null here, otherwise implement the method fully
		}
		
		@Override
		public <S extends MetadataType, T extends S> S getFirstMetadata(Class<T> clazz) {
			try {
				List<S> ml = getMetadata(clazz);
				if (ml == null) return null;
				return ml.isEmpty() ? null : ml.get(0);
			} catch (Exception e) {
				logger.error("Get metadata failed!",e);
			}

			return null;
		}
	}

	@Override
	public void createPartControl(Composite parent) {
		Display display = parent.getDisplay();
		sashComp = new SashForm(parent, SWT.VERTICAL);
		sashComp.setLayout(new FillLayout(SWT.VERTICAL));
		
		try {
			explorer = expClass.getConstructor(Composite.class, IWorkbenchPartSite.class, ISelectionChangedListener.class).newInstance(sashComp, getSite(), this);
		} catch (Exception e) {
			throw new IllegalArgumentException("Cannot make explorer", e);
		}

		try {
			explorer.loadFileAndDisplay(firstFileName, null);
		} catch (Exception e) {
			throw new IllegalArgumentException("Explorer cannot load file", e);
		}
		
		final ExpandableComposite viewerComp = new ExpandableComposite(sashComp, SWT.BORDER);
		viewerComp.setText("Datasets");
		viewerComp.setToolTipText("List of selected datasets");
		viewerComp.setLayout(new FillLayout());
		final Composite vg = new Composite(viewerComp, SWT.NONE);
		vg.setLayout(new FillLayout());
		viewer = new TableViewer(vg, SWT.V_SCROLL);
		viewer.addSelectionChangedListener(new ISelectionChangedListener() {
			@Override
			public void selectionChanged(SelectionChangedEvent event) {
				// inform metadata table view of currently selected file
				ISelection is = event.getSelection();
				if (is instanceof StructuredSelection) {
					Object e = ((StructuredSelection) is).getFirstElement();
					if (e instanceof SelectedFile) {
						SelectedFile f = (SelectedFile) e;
						setSelection(new FileSelection(f));
					}
				}
			}
		});

		TableViewerColumn tVCol;
		TableColumn tCol;

		tVCol = new TableViewerColumn(viewer, SWT.CENTER);
		tCol = tVCol.getColumn();
		tCol.setText("Use");
		tCol.setToolTipText("Toggle to use in dataset inspector (a yellow background indicates a missing or incompatible dataset)");
		tCol.setWidth(40);
		tCol.setMoveable(false);
		tVCol.setEditingSupport(new CFEditingSupport(viewer, Column.TICK, null));
		tVCol.setLabelProvider(new TickLabelProvider(display));

		tVCol = new TableViewerColumn(viewer, SWT.CENTER);
		valueColumn = tVCol.getColumn();
		valueColumn.setText(VALUE_DEFAULT_TEXT);
		valueColumn.setToolTipText("Value of resource (a yellow background indicates a missing value)");
		valueColumn.setWidth(40);
		valueColumn.setMoveable(false);
		tVCol.setEditingSupport(new CFEditingSupport(viewer, Column.VALUE, null));
		tVCol.setLabelProvider(new ValueLabelProvider(display));
		
		tVCol = new TableViewerColumn(viewer, SWT.CENTER);
		tCol = tVCol.getColumn();
		tCol.setText("Var");
		tCol.setToolTipText("Select mathematical operation to apply on this file");
		tCol.setWidth(150);
		tCol.setMoveable(false);
		variableEditor = new CFEditingSupport(viewer, Column.VARIABLE, null);
		tVCol.setEditingSupport(variableEditor);
		tVCol.setLabelProvider(new VariableLabelProvider());
		
		tVCol = new TableViewerColumn(viewer, SWT.LEFT);
		tCol = tVCol.getColumn();
		tCol.setText("File name");
		tCol.setToolTipText("Name of resource");
		tCol.setWidth(100);
		tCol.setMoveable(false);
		tVCol.setEditingSupport(new CFEditingSupport(viewer, Column.PATH, null));
		tVCol.setLabelProvider(new PathLabelProvider(display));

		viewer.setContentProvider(new IStructuredContentProvider() {
			
			@Override
			public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
			}
			
			@Override
			public void dispose() {
			}
			
			@Override
			public Object[] getElements(Object inputElement) {
				return fileList == null ? null : fileList.toArray();
			}
		});

		// drop support
		viewer.addDropSupport(DND.DROP_COPY | DND.DROP_MOVE, new Transfer[] {FileTransfer.getInstance()}, new CFDropAdapter(viewer));

		// add context menus
		final Table table = viewer.getTable();
		table.setHeaderVisible(true);

		final Menu headerMenu = new Menu(sashComp.getShell(), SWT.POP_UP);
		headerMenu.addListener(SWT.Show, new Listener() {
			@Override
			public void handleEvent(Event event) {
				// get selection and decide
				for (MenuItem m : headerMenu.getItems()) {
					m.setEnabled(!useRowIndexAsValue);
				}
			}
		});

		MenuItem item = new MenuItem(headerMenu, SWT.PUSH);
		item.setText("Use row index as value");
		item.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				useRowIndexAsValue = true;
				valueColumn.setText(VALUE_DEFAULT_TEXT);
				viewer.refresh();
				changeSelection();
			}
		});

		table.setMenu(headerMenu);

		final Menu tableMenu = null;
		table.addListener(SWT.MenuDetect, new Listener() {
			@Override
			public void handleEvent(Event event) {
				Point pt = sashComp.getDisplay().map(null, table, new Point(event.x, event.y));
				Rectangle clientArea = table.getClientArea();
				boolean header = clientArea.y <= pt.y && pt.y < (clientArea.y + table.getHeaderHeight());
				table.setMenu(header ? headerMenu : tableMenu);
			}
		});

		if (fileList != null) {
			viewer.setInput(fileList);
			for (TableColumn tc: viewer.getTable().getColumns()) {
				tc.pack();
			}
		}

		viewerComp.setClient(vg);
		viewerComp.setExpanded(true);
		viewerComp.addExpansionListener(new ExpansionAdapter() {
			@Override
			public void expansionStateChanged(ExpansionEvent e) {
				sashComp.layout();
			}		
		});
		
		ExpandableComposite expComp = new ExpandableComposite(sashComp, SWT.BORDER);
		expComp.setText("Mathematical Expressions");
		expComp.setToolTipText("Define mathematical expressions using datasets");
		expComp.setLayout(new FillLayout());
		expComp.addExpansionListener(new ExpansionAdapter() {
			@Override
			public void expansionStateChanged(ExpansionEvent e) {
				sashComp.layout();
			}		
		});
		{
			Composite g = new Composite(expComp, SWT.NONE);
			g.setLayout(new FillLayout());
			createExpressionTable(g, display);
			expComp.setClient(g);
			expComp.setExpanded(false);
		}
		
		ExpandableComposite varComp = new ExpandableComposite(sashComp, SWT.BORDER);
		varComp.setText("Variable Assignments");
		varComp.setToolTipText("List of dataset indecies assigned to every variable");
		varComp.setLayout(new FillLayout());
		varComp.addExpansionListener(new ExpansionAdapter() {
			@Override
			public void expansionStateChanged(ExpansionEvent e) {
				sashComp.layout();
			}		
		});
		{
			Composite g = new Composite(varComp, SWT.NONE);
			g.setLayout(new FillLayout());
			createVariableTable(g);
			varComp.setClient(g);
			varComp.setExpanded(false);
		}
		
		sashComp.setWeights(new int[] {5, 1 ,1 ,1});
		
		explorer.addSelectionChangedListener(this);
		getSite().setSelectionProvider(this);
	}

	private void createExpressionTable(Composite g, Display display) {
		expressionViewer = new TableViewer(g, SWT.V_SCROLL);
		
		TableViewerColumn tVCol;
		TableColumn tCol;

		tVCol = new TableViewerColumn(expressionViewer, SWT.CENTER);
		tCol = tVCol.getColumn();
		tCol.setText("Use");
		tCol.setToolTipText("Toggle to use in dataset inspector (a yellow background indicates a missing or incompatible dataset)");
		tCol.setWidth(40);
		tCol.setMoveable(false);
		tVCol.setEditingSupport(new CFEditingSupport(expressionViewer, Column.TICK, null));
		tVCol.setLabelProvider(new TickLabelProvider(display));

		tVCol = new TableViewerColumn(expressionViewer, SWT.CENTER);
		tCol = tVCol.getColumn();
		tCol.setText(VALUE_DEFAULT_TEXT);
		tCol.setToolTipText("Value of resource (a yellow background indicates a missing value)");
		tCol.setWidth(40);
		tCol.setMoveable(false);
		tVCol.setLabelProvider(new ValueLabelProvider(display));

		tVCol = new TableViewerColumn(expressionViewer, SWT.LEFT);
		tCol = tVCol.getColumn();
		tCol.setText("Expression");
		tCol.setToolTipText("Mathematical exprossion evaluated on the input data");
		tCol.setWidth(250);
		tCol.setMoveable(false);
		tVCol.setEditingSupport(new CFEditingSupport(expressionViewer, Column.EXPRESSION, null));
		tVCol.setLabelProvider(new ExpressionLabelProvider());
		
		final Table table = expressionViewer.getTable();
		table.setHeaderVisible(true);
		
		final Menu exprMenu = new Menu(expressionViewer.getControl().getShell(), SWT.POP_UP);
		MenuItem item = new MenuItem(exprMenu, SWT.PUSH);
		item.setText("Add new expression");
		item.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				expressionList.add(new SelectedNode(getNewIndex(), DEFAULT_EXPRESSION));
				updateVariableMappings();
				expressionViewer.refresh();
			}
		});

		table.setMenu(exprMenu);
		
		expressionViewer.setContentProvider(ArrayContentProvider.getInstance());
		expressionViewer.setInput(expressionList);
	}

	private void createVariableTable(Composite g) {
		variableViewer = new TableViewer(g, SWT.V_SCROLL);
		
		TableViewerColumn tVCol;
		TableColumn tCol;
		
		tVCol = new TableViewerColumn(variableViewer, SWT.CENTER);
		variableColumn = tVCol.getColumn();
		variableColumn.setText("Var");
		variableColumn.setToolTipText("Value of resource (a yellow background indicates a missing value)");
		variableColumn.setWidth(40);
		variableColumn.setMoveable(false);
		tVCol.setLabelProvider(new VariableNameLabelProvider());
		
		tVCol = new TableViewerColumn(variableViewer, SWT.CENTER);
		tCol = tVCol.getColumn();
		tCol.setText("Math");
		tCol.setToolTipText("Select mathematical operation to apply on this file");
		tCol.setWidth(150);
		tCol.setMoveable(false);
		tVCol.setEditingSupport(new CFEditingSupport(variableViewer, Column.COMBO, null));
		tVCol.setLabelProvider(new ComboLabelProvider());
		
		tVCol = new TableViewerColumn(variableViewer, SWT.LEFT);
		tCol = tVCol.getColumn();
		tCol.setText("Indices");
		tCol.setToolTipText("List of indicies of datasets mapped to this variable");
		tCol.setWidth(150);
		tCol.setMoveable(false);
		tVCol.setLabelProvider(new IndexLabelProvider());
		
		final Table table = variableViewer.getTable();
		table.setHeaderVisible(true);
		
		variableViewer.setContentProvider(ArrayContentProvider.getInstance());
		
		variableViewer.setInput(variableMap.values());
	}
	
	private void changeSelection() {
		if (currentDatasetSelection != null) {
			selectionChanged(new SelectionChangedEvent(this, currentDatasetSelection));
		}
	}
	
	/**
	 *  Generate new index values from current list of included files and expressions
	 * @return New index equal to the maximum index incremented by one 
	 */
	private int getNewIndex() {
		List<Integer> idxList = new ArrayList<Integer>();
		for (SelectedObject obj : fileList) {
			idxList.add(obj.getIndex());
		}
		for (SelectedObject obj : expressionList) {
			idxList.add(obj.getIndex());
		}
		int idxNew = Collections.max(idxList) + 1;
		return idxNew;
	}

	final private class CFDropAdapter extends ViewerDropAdapter {

		protected CFDropAdapter(Viewer viewer) {
			super(viewer);
		}

		@Override
		public boolean performDrop(Object data) {
			// find position
			SelectedFile file = (SelectedFile) getCurrentTarget();
			int index = file == null ? 0 : fileList.indexOf(file);
			if (index < 0) {
				index = fileList.size();
			}
			String[] files = (String[]) data;
			boolean ok = true;
			for (String f : files) {
				ok |= addFile(f, getNewIndex());
			}
			return ok;
		}

		@Override
		public boolean validateDrop(Object target, int operation, TransferData transferType) {
			return FileTransfer.getInstance().isSupportedType(transferType);
		}
	}

	final private class CFEditingSupport extends EditingSupport {
		private CellEditor editor = null;
		private Column column;

		public CFEditingSupport(TableViewer viewer, Column column, ICellEditorListener listener) {
			super(viewer);
			if (column == Column.TICK) {
				editor = new CheckboxCellEditor(viewer.getTable(), SWT.CHECK);
				if (listener != null) {
					editor.addListener(listener);
				}
			}
			if (column == Column.COMBO) {
				editor = new ComboBoxViewerCellEditor(viewer.getTable(), SWT.READ_ONLY);
		        ((ComboBoxViewerCellEditor) editor).setLabelProvider(new LabelProvider());
		        ((ComboBoxViewerCellEditor) editor).setContentProvider(new ArrayContentProvider());
				((ComboBoxViewerCellEditor) editor).setInput(MathOp.values());
				if (listener != null) {
					editor.addListener(listener);
				}
			}
			if (column == Column.VARIABLE) {
				editor = new ComboBoxViewerCellEditor(viewer.getTable(), SWT.READ_ONLY);
		        ((ComboBoxViewerCellEditor) editor).setLabelProvider(new LabelProvider());
		        ((ComboBoxViewerCellEditor) editor).setContentProvider(new ArrayContentProvider());
				if (listener != null) {
					editor.addListener(listener);
				}
			}
			if (column == Column.EXPRESSION) {
				editor = new TextCellEditor(viewer.getTable());
				if (listener != null) {
					editor.addListener(listener);
				}
			}
			this.column = column;
		}

		@Override
		protected boolean canEdit(Object element) {
			return ((column == Column.TICK)  ||
					(column == Column.COMBO) ||
					(column == Column.VARIABLE) ||
					(column == Column.EXPRESSION));
		}

		@Override
		protected CellEditor getCellEditor(Object element) {
			return editor;
		}

		@SuppressWarnings("unchecked")
		@Override
		protected Object getValue(Object element) {
			if (element instanceof SelectedObject) {
				SelectedObject so = (SelectedObject) element;
				if (column == Column.TICK) {
					return so.doUse();
				}
			}
			if (element instanceof SelectedFile) {
				SelectedFile sf = (SelectedFile) element;
				if (column == Column.VARIABLE) {
					if (expressionList != null) {
						Set<String> vars = new HashSet<String>();
						for (SelectedNode tmp : expressionList) {
							vars.addAll(tmp.symbolTable.keySet());
						}
						List<String> varList = new ArrayList<String>(vars);
						Collections.sort(varList);
						((ComboBoxViewerCellEditor) variableEditor.getCellEditor(null)).setInput(varList.toArray());
					}
					return sf.getVariableName();
				}
			}
			if (element instanceof SelectedNode) {
				if (column == Column.EXPRESSION) {
					SelectedNode expr = (SelectedNode) element;
					return expr.toString();
				}
			}
			if (element instanceof VariableMapping) {
				VariableMapping vm = (VariableMapping) element;
				if (column == Column.COMBO) {
					return vm.getMathOp();
				}
			}
			return null;
		}

		@Override
		protected void setValue(Object element, Object value) {
			if (value == null) {
				return;
			}
			if (element instanceof SelectedObject) {
				SelectedObject so = (SelectedObject) element;
				if (column == Column.TICK) {
					so.setUse((Boolean) value);
				}
				if (column == Column.VARIABLE) {
					String variableName = (String) value; 
    				so.setVariableName(variableName);
    				updateVariableMappings();
				}
			}
			if (element instanceof SelectedNode) {
				if (column == Column.EXPRESSION) {
					int idx = expressionViewer.getTable().getSelectionIndex();
					SelectedNode expr = (SelectedNode) element;
					expr.setExpression(String.valueOf(value));
					expressionList.set(idx, expr);
					updateVariableMappings();
				}

			}
			if (element instanceof VariableMapping) {
				VariableMapping vm = (VariableMapping) element;
				if (column == Column.COMBO) {
					vm.setMathOp((MathOp) value);
					variableMap.get(vm.getName()).setMathOp((MathOp) value);
				}
			}
			getViewer().update(element, null);
			changeSelection();
		}
	}

	/**
	 * Loop over all expressions and assign to every variable a set of SelectionObjects associated with it
	 */
	@SuppressWarnings("unchecked")
	private void updateVariableMappings() {
		List<SelectedFile> selFiles = (List<SelectedFile>) viewer.getInput();
		Set<String> varNames = new HashSet<String>();
        if (expressionList != null) {
        	for (SelectedNode expr : expressionList) {
        		SymbolTable st = expr.symbolTable;
        		if (st != null) {
        			Iterator<String> itr = st.keySet().iterator();
        			while (itr.hasNext()) {
        				String varName = itr.next();
        				Variable var = st.getVar(varName);
        				var.setValue(new HashSet<SelectedObject>());
        				for (SelectedFile sf : selFiles) {
        					String sfVarName = sf.getVariableName(); 
        					if (sfVarName != null && sfVarName.equals(varName)) {
        						((Set<SelectedObject>)var.getValue()).add(sf);
        					}
        				}
        				varNames.add(varName);
        			}
        		}
        	}
        }
        
        Set<String> oldNames = new HashSet<String>(variableMap.keySet());
        for (String oldName : oldNames) {
        	if (!varNames.contains(oldName))
        		variableMap.remove(oldName);
        }
        
        if (expressionList != null) {
        	for (SelectedNode expr : expressionList) {
        		SymbolTable st = expr.symbolTable;
        		if (st != null) {
        			Iterator<String> itr = st.keySet().iterator();
        			while (itr.hasNext()) {
        				String varName = itr.next();
           				Variable var = st.getVar(varName);
       					List<SelectedObject> objList = new ArrayList<SelectedObject>((Set<SelectedObject>) var.getValue());
       					VariableMapping newMap = new VariableMapping(varName);
						newMap.setDatasets(objList);
						newMap.setMathOp(variableMap.containsKey(varName) ? variableMap.get(varName).getMathOp()
								: MathOp.IDX);
						variableMap.put(varName, newMap);
        			}
        		}
        	}
        }
        variableViewer.setInput(variableMap.values());
	}
	
	/**
	 * Get editor classes that can handle given file 
	 * @param fileName
	 * @return list of editor class names
	 */
	public static List<String> getEditorCls(final String fileName) {
		IEditorRegistry reg = PlatformUI.getWorkbench().getEditorRegistry();
		IEditorDescriptor[] eds = reg.getEditors(fileName);
		List<String> edId = new ArrayList<String>();
		for (IEditorDescriptor e : eds) {
			if (e.isInternal()) {
				edId.add(e.getId());
			}
		}

		List<String> edCls = new ArrayList<String>();
		IExtensionPoint ept = Platform.getExtensionRegistry().getExtensionPoint("org.eclipse.ui.editors");
		IConfigurationElement[] cs = ept.getConfigurationElements();
		for (IConfigurationElement l : cs) {
			String id = l.getAttribute("id");
			String cls = l.getAttribute("class");
			if (id != null && cls != null && edId.contains(id)) {
				edCls.add(cls);
			}
		}
		return edCls;
	}

	@Override
	public void setFocus() {
	}

	@Override
	public void selectionChanged(SelectionChangedEvent e) {
		ISelection sel = e.getSelection();

		// TODO this should be in job/progress service
		if (sel instanceof MetadataSelection) {
			final String metaValue = ((MetadataSelection) sel).getPathname();
			loadMetaValues(metaValue);
			useRowIndexAsValue = false;
			valueColumn.setText(metaValue);
		} else if (sel instanceof DatasetSelection) {
			currentDatasetSelection = (DatasetSelection) sel;
			String name;
			String node;
			if (currentDatasetSelection instanceof HDF5Selection) {
				name = ((HDF5Selection) currentDatasetSelection).getNode();
				node = name.substring(0, name.lastIndexOf(Node.SEPARATOR)+1);
			} else {
				name = currentDatasetSelection.getFirstElement().getName();
				int i = name.indexOf(AbstractFileLoader.FILEPATH_DATASET_SEPARATOR);
				if (i >= 0) {
					name = name.substring(i+1);
				}
				node = null;
			}
			logger.debug("Selected data = {}", name);
			loadDatasets(name);
			if (useRowIndexAsValue)
				setMetaValuesAsIndexes();
			loadAxisSelections(fileList, currentDatasetSelection.getAxes(), node);
			loadAxisSelections(expressionList, currentDatasetSelection.getAxes(), node);
		} else {
			return;
		}

		if (currentDatasetSelection != null) {
			List<ILazyDataset> dataList = new ArrayList<ILazyDataset>();
			List<ILazyDataset> metaList = new ArrayList<ILazyDataset>();
			List<List<AxisSelection>> axesList = new ArrayList<List<AxisSelection>>();

			for (SelectedFile f : fileList) {
				if (f.doUse() && f.hasData() && f.hasMetadataValue()) {
					f.setDataOK(true); // blindly set okay (check later)
					dataList.addAll(f.getDataset());
					metaList.add(f.getMetadataValue());
					axesList.add(new ArrayList<AxisSelection>(f.getAxisSelections()));
				}
			}
			boolean extend = true;
			for (ILazyDataset m : metaList) { // if all metadata is multi-valued then do not extend aggregate shape
				if (m.getSize() > 1) {
					extend = false;
					break;
				}
			}

			if (dataList.size() == 0) {
				logger.warn("No datasets found or selected");
				return;
			}

			// squeeze all datasets
			for (ILazyDataset d : dataList)
				d.squeezeEnds();
			
			// remove incompatible data
			int[][] shapes = AggregateDataset.calcShapes(extend, dataList.toArray(new ILazyDataset[0]));
			int j = shapes.length - 1;
			int[] s = shapes[0];
			final int axis = extend ? 0 : -1;
			for (int k = fileList.size() - 1; k >= 0 && j > 0; k--) {
				SelectedFile f = fileList.get(k);
				if (f.doUse() && f.hasData() && f.hasMetadataValue()) {
					boolean ok = ShapeUtils.areShapesCompatible(s, shapes[j], axis);
					f.setDataOK(ok);
					if (!ok) {
						dataList.remove(j);
						metaList.remove(j);
						axesList.remove(j);
					}
					j--;
				}
			}
			
			// Add datasets calculated from the expressions
			for (SelectedNode expr : expressionList) {
				if (expr.doUse() && expr.hasData()) {
					expr.setDataOK(true); // blindly set okay (check later)
					List<ILazyDataset> exprData = expr.getDataset();
					dataList.addAll(exprData);
					Iterator<ILazyDataset> itr = exprData.iterator();
					while (itr.hasNext()) {
						metaList.add(expr.getMetadataValue());
						axesList.add(new ArrayList<AxisSelection>(expr.getAxisSelections()));
						itr.next();
					}
				}
			}
			

			//List<ILazyDataset> processedDataList = new ArrayList<ILazyDataset>();
			//List<ILazyDataset> processedMetaList = new ArrayList<ILazyDataset>();
			//List<List<AxisSelection>> processedAxesList = new ArrayList<List<AxisSelection>>();
			//processSelection(dataList, metaList, axesList, mathList, processedDataList, processedMetaList, processedAxesList);
			
			InspectorType itype;
			switch (currentDatasetSelection.getType()) {
			case IMAGE:
				itype = InspectorType.IMAGE;
				break;
			case LINE:
			default:
				itype = InspectorType.LINESTACK;
				break;
			}

			setSelection(createSelection(itype, extend, dataList, metaList, axesList, firstFileName));
		}

		viewer.refresh();
		expressionViewer.refresh();
	}
	 
	/**
	 * 
	 */
	private void setMetaValuesAsIndexes() {
		for (SelectedFile f : fileList) {
			f.setMetadataValueAsIndex();
		}
		for (SelectedNode expr : expressionList) {
			expr.setMetadataValueAsIndex();
		}
	}

	/**
	 * Load metadata values from selected files
	 */
	private void loadMetaValues(String key) {
		logger.debug("Selected metadata = {}", key);

		for (SelectedFile f : fileList) {
			if (!f.hasMetadata() && !f.hasDataHolder()) {
				try {
					IDataHolder holder = explorer.loadFile(f.getAbsolutePath(), null);
					f.setDataHolder(holder);
				} catch (Exception e) {
					continue;
				}
			}
			f.setMetadataValue(key);
		}
	}

	/**
	 * Load datasets from selected files
	 */
	private void loadDatasets(String key) {
		for (SelectedFile f : fileList) {
			if (!f.hasDataHolder()) {
				try {
					IDataHolder holder = explorer.loadFile(f.getAbsolutePath(), null);
					if (holder == null) {
						continue;
					}
					f.setDataHolder(holder);
				} catch (Exception e) {
					continue;
				}
			}
			f.setDataset(key);
		}
	}

	/**
	 * Load axis selections from selected files
	 */
	@SuppressWarnings("null")
	private void loadAxisSelections(List<? extends SelectedObject> selectedList, List<AxisSelection> axes, String node) {
		boolean isFirst = true;

		List<AxisSelection> laxes = new ArrayList<AxisSelection>();
		if (axes != null) {
			for (AxisSelection as : axes)
				laxes.add(as.clone());
		}

		for (SelectedObject f : selectedList) {
			if (f.doUse() && f.hasData() && (useRowIndexAsValue || f.hasMetadataValue())) {
				if (isFirst) {
					isFirst = false;
					f.setAxisSelections(laxes);
				} else {
					f.setAxisSelections(makeAxes(laxes, f, node));
				}
			}
		}

		// prune missing choices
		List<String> choices = new ArrayList<String>();
		int rank = axes == null ? 0 : axes.size();
		for (int i = 0; i < rank; i++) {
			choices.clear();
			choices.addAll(axes.get(i).getNames());

			for (SelectedObject f : selectedList) {
				if (f.hasData()) {
					AxisSelection as = f.getAxisSelections().get(i);
					for (String n : as) {
						if (as.getAxis(n) == null) {
							logger.warn("Removing choice {} as it is missing in {}", n, f.getName());
							choices.remove(n);
						}
					}
				}
			}

			for (SelectedObject f : selectedList) {
				if (f.hasData()) {
					AxisSelection as = f.getAxisSelections().get(i);
					ArrayList<String> names = new ArrayList<String>(as.getNames());
					for (String n : names) {
						if (!choices.contains(n)) {
							as.removeChoice(n);
						}
					}
				}
			}
		}
	}

	/**
	 * Create axes from file based on other axes
	 * @param oldAxes
	 * @param file
	 * @param node
	 * @return list of axis selections
	 */
	private List<AxisSelection> makeAxes(List<AxisSelection> oldAxes, SelectedObject file, String node) {
		List<AxisSelection> newAxes = new ArrayList<AxisSelection>();
		for (AxisSelection a : oldAxes) {
			AxisSelection n = a.clone();
			for (int i = 0, imax = n.size(); i < imax; i++) {
				AxisChoice c = n.getAxis(i);
				String name = c.getName();
				ILazyDataset d = file.getAxis(node != null ? node + name : name); // can be null (from Index or dim:)
				if (d == null) {
					if (name.startsWith(AbstractExplorer.DIM_PREFIX)) {
						d = c.getValues().clone();
					}
				}
				c.setValues(d);
			}
			newAxes.add(n);
		}
		return newAxes;
	}

	/**
	 * Create a data selection from given lists of datasets, metadata value datasets and axis selection lists  
	 * @param itype
	 * @param datasets
	 * @param metavalues
	 * @param axisSelectionLists
	 * @param path 
	 * @return data selection
	 */
	public static DatasetSelection createSelection(InspectorType itype, List<ILazyDataset> datasets, List<ILazyDataset> metavalues, List<List<AxisSelection>> axisSelectionLists, String path) {
		boolean extend = true;
		for (ILazyDataset m : metavalues) { // if all metadata is multi-valued then do not extend aggregate shape
			if (m.getSize() > 1) {
				extend = false;
				break;
			}
		}
		return createSelection(itype, extend, datasets, metavalues, axisSelectionLists, path);
	}

	/**
	 * Create a data selection from given lists of datasets, metadata value datasets and axis selection lists  
	 * @param itype 
	 * @param extend
	 * @param datasets
	 * @param metavalues
	 * @param axisSelectionLists
	 * @param path 
	 * @return data selection
	 */
	public static DatasetSelection createSelection(InspectorType itype, boolean extend, List<ILazyDataset> datasets, List<ILazyDataset> metavalues, List<List<AxisSelection>> axisSelectionLists, String path) {
		AggregateDataset allData = new AggregateDataset(extend, datasets.toArray(new ILazyDataset[0]));
		ILazyDataset[] mvs = metavalues.toArray(new ILazyDataset[0]);
		ILazyDataset mv = mvs[0];
		ILazyDataset allMeta;
		if (mv instanceof IDataset && mv.getRank() == 1) { // concatenate 1D meta-values
			Dataset[] mva = new Dataset[mvs.length];
			for (int i = 0; i < mvs.length; i++) {
				ILazyDataset m = mvs[i]; 
				mva[i] = m instanceof IDataset ? DatasetUtils.convertToDataset((IDataset) m) : null;
			}
			allMeta = DatasetUtils.concatenate(mva, 0);
			allMeta.setName(mv.getName());
		} else {
			allMeta = new AggregateDataset(extend, mvs);
		}
		List<AxisSelection> newAxes = new ArrayList<AxisSelection>();
		if (extend) { // extra entries as aggregate datasets can have extra dimension
			for (List<AxisSelection> asl : axisSelectionLists) {
				asl.add(0, null);
			}
		}

		// mash together axes
		int[] shape = allData.getShape();
		int rank = shape.length;
		AxisSelection as;
		List<ILazyDataset> avalues = new ArrayList<ILazyDataset>();

		// for each dimension,
		for (int i = 0; i < rank; i++) {
			as = new AxisSelection(rank, i);
			newAxes.add(as);
			if (i == 0) { // add meta values first
				AxisChoice nc = new AxisChoice(allMeta, 1);
				int[] map = new int[allMeta.getRank()];
				for (int j = 0; j < map.length; j++) {
					map[j] = j;
				}
				nc.setIndexMapping(map);
				nc.setAxisNumber(0);
				as.addChoice(nc, 1);
			}

			AxisSelection ias = axisSelectionLists.get(0).get(i); // initial
			if (ias == null) {
				continue;
			}
			for (int k = 0, kmax = ias.size(); k < kmax; k++) { // for each choice
				avalues.clear();
				final AxisChoice c = ias.getAxis(k);
				final int[] map = c.getIndexMapping();
				for (List<AxisSelection> asl : axisSelectionLists) { // for each file
					AxisSelection a = asl.get(i);
					if (a == null)
						break; // this dimension was extended

					ILazyDataset ad = a.getAxis(k).getValues();
					if (ad == null) {
						avalues.clear();
						logger.warn("Missing data for choice {} in dim:{} ", ias.getName(k), i);
						break;
					}
					avalues.add(ad);
				}

				if (avalues.size() == 0)
					continue;

				// consume list for choice
				ILazyDataset allAxis = new AggregateDataset(extend, avalues.toArray(new ILazyDataset[0]));

				AxisChoice nc = new AxisChoice(allAxis, c.getPrimary());
				String name = ias.getName(k);
				if (extend) {
					final int arank = allAxis.getRank();
					if (arank > 1) {
						int[] nmap = new int[arank]; // first entry is zero
						for (int l = 0; l < map.length; l++) {
							nmap[l+1] = map[l] + 1;
						}
						nc.setIndexMapping(nmap);
					}
					if (name.startsWith(AbstractExplorer.DIM_PREFIX)) { // increment dim: number
						int d = Integer.parseInt(name.substring(AbstractExplorer.DIM_PREFIX.length()));
						allAxis.setName(AbstractExplorer.DIM_PREFIX + (d+1));
					}
				} else {
					nc.setIndexMapping(map.clone());
				}
				nc.setAxisNumber(i);
				as.addChoice(name, nc, ias.getOrder(k));
			}
		}

		if (rank == 1) // override when rank-deficit
			itype = InspectorType.LINE;

		return new DatasetSelection(itype, path, newAxes, allData);
	}

	private List<ISelectionChangedListener> listeners = new ArrayList<ISelectionChangedListener>();

	@Override
	public void addSelectionChangedListener(ISelectionChangedListener listener) {
		if (!listeners.contains(listener)) {
			listeners.add(listener);
		}
	}

	@Override
	public ISelection getSelection() {
		if (multipleSelection == null) {
			return new StructuredSelection(); // Eclipse requires that we do not return null
		}
		return multipleSelection;
	}

	@Override
	public void removeSelectionChangedListener(ISelectionChangedListener listener) {
		listeners.remove(listener);
	}

	@Override
	public void setSelection(ISelection selection) {
		if (selection instanceof DatasetSelection) {
			multipleSelection = (DatasetSelection) selection;
		} else {
			if (!(selection instanceof FileSelection)) {
				return;
			}
		}
		SelectionChangedEvent e = new SelectionChangedEvent(this, selection);
		for (ISelectionChangedListener listener : listeners) {
			listener.selectionChanged(e);
		}
	}
	
	private enum MathOp {
		IDX, ADD, AVR, MUL, MAX, MIN;
	}

	private class SelectedObject {
		boolean hasMV = false;
		boolean use = true;
		boolean canUseData = false;
		Object f;
		IntegerDataset i;
		List<ILazyDataset> d;
		IMetadata m;
		Serializable mv;
		String variable;
		List<AxisSelection> asl;
		
		public boolean doUse() {
			return use;
		}

		public void setUse(boolean doUse) {
			use = doUse;
		}

		public String getName() {
			return d.get(0).getName();
		}

		public boolean isDataOK() {
			return canUseData;
		}

		public void setDataOK(boolean dataOK) {
			canUseData = dataOK;
		}
		
		public boolean hasData() {
			return d != null;
		}

		public boolean hasMetadataValue() {
			return hasMV;
		}

		public boolean hasMetadata() {
			return m != null;
		}

		public List<ILazyDataset> getDataset() {
			return d;
		}

		public ILazyDataset getMetadataValue() {
			if (!hasMV) {
				return null;
			}
			if (mv instanceof ILazyDataset) {
				return (ILazyDataset) mv;
			}
			return DatasetFactory.createFromObject(mv);
		}

		public void setMetadataValueAsIndex() {
			hasMV = true;
			mv = i;
		}

		@SuppressWarnings("unused")
		public void setMetadataValue(String key) {
			if (m == null) {
				hasMV = false;
				return;
			}

			try {
				mv = m.getMetaValue(key);
				if (mv instanceof String) {
					mv = Utils.parseValue((String) mv); // TODO parse common multiple values string
					if (mv != null) {
						Dataset a = DatasetFactory.createFromObject(mv);
						a.setName(key);
						mv = a;
					}
				}
			} catch (Exception e) {
			}
			hasMV = mv != null;
		}

		public void setIndex(int index) {
			i = DatasetFactory.createFromObject(IntegerDataset.class, new int[] {index}, null);
			i.setName(CompareFilesEditor.INDEX);
		}

		public int getIndex() {
			return i.get(0);
		}

		public String getVariableName() {
			return variable;
		}

		public void setVariableName(String name) {
			this.variable = name;
		}
		
		public void setAxisSelections(List<AxisSelection> axisSelectionList) {
			asl = axisSelectionList;
		}

		public ILazyDataset getAxis(@SuppressWarnings("unused") String key) {
			return null;
		}

		public List<AxisSelection> getAxisSelections() {
			return asl;
		}

		@SuppressWarnings("unused")
		public boolean hasAxisSelections() {
			return asl != null;
		}
	}

	private class SelectedFile extends SelectedObject {
		IDataHolder h;

		public SelectedFile(int index, IFile file) {
			f = new File(file.getLocationURI());
			if (f == null || !((File) f).canRead()) {
				throw new IllegalArgumentException("File '" + file.getName() + "' does not exist or can not be read");
			}
			setIndex(index);
		}

		public SelectedFile(int index, File file) {
			f = file;
			if (f == null || !((File) f).canRead()) {
				throw new IllegalArgumentException("File '" + file.getName() + "' does not exist or can not be read");
			}
			setIndex(index);
		}

		public SelectedFile(int index, String file) {
			f = new File(file);
			if (f == null || !((File) f).canRead()) {
				throw new IllegalArgumentException("File '" + file + "' does not exist or can not be read");
			}
			setIndex(index);
		}

		public String getAbsolutePath() {
			return ((File) f).getAbsolutePath();
		}

		@Override
		public String getName() {
			return ((File) f).getName();
		}

		@Override
		public String toString() {
			if (mv == null) {
				return null;
			}
			return mv.toString();
		}

		public boolean hasDataHolder() {
			return h != null;
		}

//		public void setMetadata(IMetadata metadata) {
//			m = metadata;
//		}

		public void setDataHolder(IDataHolder holder) {
			h = holder;
			if (h != null) {
				m = h.getMetadata();
			} else {
				d = null;
			}
		}

		public void setDataset(String key) {
			if (h.contains(key)) {
				d = new ArrayList<ILazyDataset>();
				d.add(h.getLazyDataset(key));
			} else {
				int n = h.size();
				d = null;
				for (int i = 0; i < n; i++) {
					ILazyDataset l = h.getLazyDataset(i);
					if (key.equals(l.getName())) {
						d = new ArrayList<ILazyDataset>();
						d.add(l);
						break;
					}
				}
			}
		}

//		public void resetDataset() {
//			d = null;
//		}

		@Override
		public ILazyDataset getAxis(String key) {
			return h != null ? h.getLazyDataset(key) : null;
		}

		@Override
		public void setMetadataValue(String key) {
			if (m == null) {
				hasMV = false;
				return;
			}

			try {
				mv = m.getMetaValue(key);
				if (mv instanceof String) {
					mv = Utils.parseValue((String) mv); // TODO parse common multiple values string
					if (mv != null) {
						Dataset a = DatasetFactory.createFromObject(mv);
						a.setName(key);
						mv = a;
					}
				}
				if (mv == null && h != null) {
					mv = h.getDataset(key);
				}
			} catch (Exception e) {
			}
			hasMV = mv != null;
		}
	}

	private class SelectedNode extends SelectedObject {
		
		private SymbolTable symbolTable;
		private JEP jepParser, eval;
		
		public SelectedNode(int index, String str) {
			resetJep();
			if (str != null) {
				setExpression(str);
				setIndex(index);
			}
		}
		
		@Override
		public String toString() {
			if (f == null) {
				return null;
			}
			return (String) f;
		}

		@SuppressWarnings("unchecked")
		@Override
		public boolean hasData() {
			Set<String> vars = symbolTable.keySet();
			if (vars.isEmpty()) {
				return false;
			}
			Iterator<String> itr = vars.iterator();
			while (itr.hasNext()) {
				String varName = itr.next();
				Variable var = symbolTable.getVar(varName);
				Object val = var.getValue();
				if (val == null) {
					return false;
				}
				Set<SelectedObject> datasets = (Set<SelectedObject>) val; 
				if (datasets.isEmpty()) {
					return false;
				}
				for (SelectedObject data : datasets) {
					if (!(data.hasData())) {
						return false;
					}
				}
			}
			return true;
		}

		@SuppressWarnings("unchecked")
		@Override
		public boolean isDataOK() {
			Iterator<String> itr = symbolTable.keySet().iterator();
			while (itr.hasNext()) {
				String varName = itr.next();
				Variable var = symbolTable.getVar(varName);
				Object val = var.getValue();
				if (val == null) {
					canUseData = false;
					return canUseData;
				}
				Set<SelectedObject> datasets = (Set<SelectedObject>) val; 
				if (datasets.isEmpty()) {
					canUseData = false;
					return canUseData;
				}
				for (SelectedObject data : datasets) {
					if (!(data.isDataOK())) {
						canUseData = false;
						return canUseData;
					}
				}
			}
			canUseData = true;
			return canUseData;
		}

		@SuppressWarnings("unchecked")
		@Override
		public ILazyDataset getAxis(String key) {
			Iterator<String> itr = symbolTable.keySet().iterator();
			ILazyDataset defAxes = null;
			while (itr.hasNext()) {
				String varName = itr.next();
				Variable var = symbolTable.getVar(varName);
				ArrayList<SelectedObject> lzdList = new ArrayList<SelectedObject>((Set<SelectedObject>) var.getValue());
				for (SelectedObject obj : lzdList) {
					if (defAxes == null) {
						defAxes = obj.getAxis(key);
					}
					if (defAxes != null && !defAxes.equals(obj.getAxis(key))) {
						return null;
					}
				}
			}
			return defAxes;
		}

		public void setExpression(String str) {
			resetJep();
			jepParser.parseExpression(str);
			eval.parseExpression(str);
			symbolTable = jepParser.getSymbolTable();
			f = str;
		}
		
		private void resetJep() {
			// This JEP instance is used to keep track of datasets assign to variable
			jepParser = new JEP();
			jepParser.setAllowUndeclared(true);
			//jepParser.addStandardConstants();
			jepParser.addStandardFunctions();
			
			// This JEP instance is used during dataset evaluation to assign double values to variables
			eval = new JEP();
			eval.setAllowUndeclared(true);
			//eval.addStandardConstants();
			eval.addStandardFunctions();
		}
		
		private class VariableLazyLoader implements ILazyLoader {
			
			private HashMap<String, ILazyDataset> varMapping;

			public VariableLazyLoader(HashMap<String, ILazyDataset> varMapping) {
				this.varMapping = varMapping;
			}

			@Override
			public boolean isFileReadable() {
				return hasData();
			}
			
			@SuppressWarnings("unchecked")
			@Override
			public Dataset getDataset(IMonitor mon, SliceND slice) throws IOException {
				
				SymbolTable evalSymbolTable = eval.getSymbolTable();
				HashMap<String, IDataset> dataSlices = new HashMap<String, IDataset>();
				Iterator<String> itr = evalSymbolTable.keySet().iterator();
				int[] sliceShape = null;
				while (itr.hasNext()) {
					String varName = itr.next();
					ILazyDataset lzd = varMapping.get(varName); // TODO: This only works for SelectedFile objects
					IDataset lzdSlice;
					try {
						lzdSlice = lzd.getSlice(slice);
					} catch (DatasetException e) {
						throw new IOException(e);
					} 
					dataSlices.put(varName, lzdSlice);
					// All datasets and slices should have the same shape
					if (sliceShape == null) {
						sliceShape = lzdSlice.getShape();
					}
				}
				
				Dataset ds = DatasetFactory.zeros(DoubleDataset.class, sliceShape);
				IndexIterator iter = ds.getIterator(true);
				int[] idx = iter.getPos();
				while (iter.hasNext()) {
					itr = evalSymbolTable.keySet().iterator();
					while (itr.hasNext()) {
						String varName = itr.next();
						double val = dataSlices.get(varName).getDouble(idx);
						evalSymbolTable.setVarValue(varName, val);
					}
					double res;
					try {
						res = (Double) eval.evaluate(eval.getTopNode());
					} catch (ParseException e) {
						throw new IllegalArgumentException("Parsing input expression failed", e);
					}
					ds.set(res, idx);
				}
				return ds;
			}
		}
		
		private List<ILazyDataset> processSelection(final VariableMapping varMap) {

			final MathOp mathOp = varMap.getMathOp();
			
			final List<ILazyDataset> dataList = new ArrayList<ILazyDataset>();
			for (SelectedObject obj : varMap.getDatasets()) {
				dataList.addAll(obj.getDataset());
			}
			
			switch (mathOp) {
			case IDX:
				return dataList;
			default:
				ILazyDataset refData = dataList.get(0);
				ILazyLoader processingLoader = new ILazyLoader() {

					@Override
					public boolean isFileReadable() {
						return true;
					}

					@Override
					public Dataset getDataset(IMonitor mon, SliceND slice)
							throws IOException {
						Dataset accDataset = null;
						for (int idx = 0; idx < dataList.size(); idx++) {
							Dataset tmpData;
							try {
								tmpData = DatasetUtils.convertToDataset(dataList.get(idx).getSlice(slice));
							} catch (DatasetException e) {
								throw new IOException(e);
							}
							if (accDataset == null) {
								switch (mathOp) {
								case ADD:
								case AVR:
									accDataset = DatasetFactory.zeros(tmpData.getClass(), tmpData.getShapeRef());
									break;
								case MUL:
									accDataset = DatasetFactory.ones(tmpData.getClass(), tmpData.getShapeRef());
									break;
								case MAX:
								case MIN:
									accDataset = DatasetFactory.zeros(tmpData.getClass(), tmpData.getShapeRef());
									IndexIterator iter = accDataset.getIterator();
									tmpData.fillDataset(accDataset, iter);
									break;
								default:
									break;
								}
							}
							if (accDataset == null) {
								return null;
							}
							switch (mathOp) {
							case ADD:
							case AVR:
								accDataset.iadd(tmpData);
								break;
							case MUL:
								accDataset.imultiply(tmpData);
								break;
							case MAX:
								accDataset = Maths.maximum(tmpData, accDataset);
								break;
							case MIN:
								accDataset = Maths.minimum(tmpData, accDataset);
								break;
							default:
								break;
							}
						}
						if (accDataset != null && mathOp.equals(MathOp.AVR)) {
							accDataset.idivide(dataList.size());
						}
						return accDataset;
					}
				};
				List<ILazyDataset> res = new ArrayList<ILazyDataset>();
				res.add(new LazyDataset(varMap.getName(), DTypeUtils.getDType(refData), refData
						.getShape(), processingLoader));
				return res;
			}

		}
		
		@SuppressWarnings("unchecked")
		@Override
		public List<ILazyDataset> getDataset() {
			// Generate all combinations of datasets assigned to variables
			List<String> varNameList = new ArrayList<String>(symbolTable.keySet());
			Map<String, List<ILazyDataset>> varMap = new HashMap<String, List<ILazyDataset>>();
			Iterator<String> itr = symbolTable.keySet().iterator();
			while (itr.hasNext()) {
				String varName = itr.next();
				List<ILazyDataset> processedDataList = processSelection(variableMap.get(varName));
				varMap.put(varName, processedDataList);
			}
			
			
			int[] idxDataset = new int[varNameList.size()];
			for (int idx = 0; idx < idxDataset.length; idx++) {
				String varName = varNameList.get(idx);
				idxDataset[idx] = varMap.get(varName).size();
			}
			
			// Iterate over flat index in idxDataset to loop over all
			// variable assignment combinations
			PositionIterator iter = new PositionIterator(idxDataset);
			d = new ArrayList<ILazyDataset>();
			int[] datasetIdx = iter.getPos();
			while (iter.hasNext()) {
				HashMap<String, ILazyDataset> tmpMap = new HashMap<String, ILazyDataset>();
				for (int idx = 0; idx < datasetIdx.length; idx++) {
					String tmpName = varNameList.get(idx);
					ILazyDataset tmpVar = varMap.get(tmpName).get(datasetIdx[idx]); 
					tmpMap.put(tmpName, tmpVar);
				}
				ILazyLoader dataLoader = new VariableLazyLoader(tmpMap);
				d.add(new LazyDataset("Function " + Arrays.toString(datasetIdx), Dataset.FLOAT64, getShape(), dataLoader));
				
			}
			return d;
		}

		@SuppressWarnings("unchecked")
		private int[] getShape() {
			int [] tmpShape = null;
			Iterator<String> itr = symbolTable.keySet().iterator();
			while (itr.hasNext()) {
				Variable var = symbolTable.getVar(itr.next());
				Set<SelectedFile> sfList = (Set<SelectedFile>) var.getValue();
				if (sfList == null || sfList.isEmpty()) {
					return null;
				}
				for (SelectedFile sf : sfList) {
					if (!sf.hasData()) {
						return null;
					}
					if (tmpShape == null) {
						tmpShape = sf.getDataset().get(0).getShape();	// All datasets should be the same shape 
					} else {
						if (!ShapeUtils.areShapesCompatible(tmpShape, sf.getDataset().get(0).getShape(), -1)) {
							return null;
						}
					}
				}
			}
			return tmpShape;
		}
	}
	
	private class VariableMapping {
		
		private String name;
		private MathOp mathOp;
		private List<SelectedObject> datasets;
		
		public VariableMapping(String name) {
			super();
			this.name = name;
		}

		public String getName() {
			return name;
		}

		public MathOp getMathOp() {
			return mathOp;
		}

		public void setMathOp(MathOp mathOp) {
			this.mathOp = mathOp;
		}

		public List<SelectedObject> getDatasets() {
			return datasets;
		}

		public void setDatasets(List<SelectedObject> datasets) {
			this.datasets = datasets;
		}
	}
}

class CompareFilesEditorInput extends PlatformObject implements IEditorInput {

	Object[] list;
	private String name;

	public CompareFilesEditorInput(IStructuredSelection selection) {
		list = selection.toArray();
		name = createName();
	}

	@Override
	public boolean exists() {
		return false;
	}

	@Override
	public ImageDescriptor getImageDescriptor() {
		return null;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public IPersistableElement getPersistable() {
		return null;
	}

	@Override
	public String getToolTipText() {
		return name;
	}

	private String createName() {
		StringBuilder s = new StringBuilder();
		if (list == null || list.length < 1) {
			s.append("Invalid list");
		} else {
			s.append("Comparing ");
			for (Object o : list) {
				if (o instanceof IFile) {
					IFile f = (IFile) o;
					s.append(f.getFullPath().toString());
					s.append(", ");
				} else if (o instanceof File) {
					File pf = (File) o;
					s.append(pf.getAbsolutePath());
					s.append(", ");
				}
			}
			int end = s.length();
			s.delete(end-2, end);
		}
		return s.toString();
	}
}

