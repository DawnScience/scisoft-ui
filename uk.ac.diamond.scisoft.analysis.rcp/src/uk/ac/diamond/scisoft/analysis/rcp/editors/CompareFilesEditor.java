/*
 * Copyright 2012 Diamond Light Source Ltd.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.ac.diamond.scisoft.analysis.rcp.editors;

import gda.analysis.io.ScanFileHolderException;

import java.io.File;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.PlatformObject;
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
import org.eclipse.ui.part.EditorPart;
import org.nfunk.jep.JEP;
import org.nfunk.jep.ParseException;
import org.nfunk.jep.SymbolTable;
import org.nfunk.jep.Variable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.analysis.dataset.AggregateDataset;
import uk.ac.diamond.scisoft.analysis.dataset.DatasetUtils;
import uk.ac.diamond.scisoft.analysis.dataset.ILazyDataset;
import uk.ac.diamond.scisoft.analysis.dataset.IMetadataProvider;
import uk.ac.diamond.scisoft.analysis.dataset.IntegerDataset;
import uk.ac.diamond.scisoft.analysis.dataset.LazyDataset;
import uk.ac.diamond.scisoft.analysis.hdf5.HDF5Node;
import uk.ac.diamond.scisoft.analysis.io.AbstractFileLoader;
import uk.ac.diamond.scisoft.analysis.io.DataHolder;
import uk.ac.diamond.scisoft.analysis.io.ILazyLoader;
import uk.ac.diamond.scisoft.analysis.io.IMetaData;
import uk.ac.diamond.scisoft.analysis.io.Utils;
import uk.ac.diamond.scisoft.analysis.rcp.AnalysisRCPActivator;
import uk.ac.diamond.scisoft.analysis.rcp.explorers.AbstractExplorer;
import uk.ac.diamond.scisoft.analysis.rcp.explorers.MetadataSelection;
import uk.ac.diamond.scisoft.analysis.rcp.hdf5.HDF5Selection;
import uk.ac.diamond.scisoft.analysis.rcp.inspector.AxisChoice;
import uk.ac.diamond.scisoft.analysis.rcp.inspector.AxisSelection;
import uk.ac.diamond.scisoft.analysis.rcp.inspector.DatasetSelection;
import uk.ac.diamond.scisoft.analysis.rcp.inspector.DatasetSelection.InspectorType;
import uk.ac.gda.monitor.IMonitor;

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
	private TableViewer viewer, expressionViewer;
	private Class<? extends AbstractExplorer> expClass = null;
	private AbstractExplorer explorer;
	private String firstFileName;

	private boolean useRowIndexAsValue = true;
	private DatasetSelection currentDatasetSelection; // from explorer
	private DatasetSelection multipleSelection; // from this editor

	private TableColumn valueColumn;

	private FileDialog fileDialog;

	private String editorName;

	private CFEditingSupport variableEditor;

	private final static String VALUE_DEFAULT_TEXT = "Value";
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
				expClass  = (Class) m.invoke(null);
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
		return addFile(path, fileList.size());
	}

	/**
	 * Add new file to comparison list at index
	 * @param path
	 * @param index
	 * @return true, if file can be added
	 */
	private boolean addFile(String path, int index) {
		if (path == null)
			return false;

		SelectedFile sf = createSelectedFile(path);
		if (sf == null)
			return false;

		if (index == 0) {
			logger.warn("Cannot add file to top of order");
			index = 1;
		}
		fileList.add(index, sf);
		int i = 0;
		for (SelectedFile f : fileList) // update index values
			f.setIndex(i++);

		if (currentDatasetSelection != null)
			changeSelection();
		else
			viewer.refresh();

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
		explorer.dispose();
		sashComp.dispose();
		viewer.getControl().dispose();
		expressionViewer.getControl().dispose();

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
			if (obj instanceof SelectedFile) {
				SelectedFile sf = (SelectedFile) obj;
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

	private class ComboLabelProvider extends CellLabelProvider {
		@Override
		public void update(ViewerCell cell) {
			SelectedFile sf = (SelectedFile) cell.getElement();
			cell.setText(sf.getMathOp().name());
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
			cell.setText(sf.getAbsolutePath());
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
			SelectedFile sf = (SelectedFile) cell.getElement();
			Color colour = null;
			if (useRowIndexAsValue) {
				cell.setText(sf.getIndex());
			} else {
				cell.setText(sf.toString());
				if (!sf.hasMetaValue()) {
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

	class FileSelection extends StructuredSelection implements IMetadataProvider {
		private IMetaData metadata = null;

		public FileSelection(SelectedFile f) {
			super(f.f);
			if (!f.hasDataHolder()) {
				try {
					DataHolder holder = explorer.loadFile(f.getAbsolutePath(), null);
					if (holder != null)
						f.setDataHolder(holder);
				} catch (Exception e) {
				}
			}
			if (f.hasMetadata()) {
				metadata = f.m;
			}
		}

		@Override
		public IMetaData getMetadata() throws Exception {
			return metadata;
		}
	}

	@Override
	public void createPartControl(Composite parent) {
		Display display = parent.getDisplay();
		sashComp = new SashForm(parent, SWT.VERTICAL);
		sashComp.setLayout(new FillLayout(SWT.VERTICAL));
		viewer = new TableViewer(sashComp, SWT.V_SCROLL);
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

		tVCol = new TableViewerColumn(viewer, SWT.NONE);
		tCol = tVCol.getColumn();
		tCol.setText("Use");
		tCol.setToolTipText("Toggle to use in dataset inspector (a yellow background indicates a missing or incompatible dataset)");
		tCol.setWidth(40);
		tCol.setMoveable(false);
		tVCol.setEditingSupport(new CFEditingSupport(viewer, Column.TICK, null));
		tVCol.setLabelProvider(new TickLabelProvider(display));

		tVCol = new TableViewerColumn(viewer, SWT.NONE);
		tCol = tVCol.getColumn();
		tCol.setText("Math");
		tCol.setToolTipText("Select mathematical operation to apply on this file");
		tCol.setWidth(150);
		tCol.setMoveable(false);
		tVCol.setEditingSupport(new CFEditingSupport(viewer, Column.COMBO, null));
		tVCol.setLabelProvider(new ComboLabelProvider());
		
		tVCol = new TableViewerColumn(viewer, SWT.NONE);
		tCol = tVCol.getColumn();
		tCol.setText("Var");
		tCol.setToolTipText("Select mathematical operation to apply on this file");
		tCol.setWidth(150);
		tCol.setMoveable(false);
		variableEditor = new CFEditingSupport(viewer, Column.VARIABLE, null);
		tVCol.setEditingSupport(variableEditor);
		tVCol.setLabelProvider(new VariableLabelProvider());
		
		tVCol = new TableViewerColumn(viewer, SWT.NONE);
		tCol = tVCol.getColumn();
		tCol.setText("File name");
		tCol.setToolTipText("Name of resource");
		tCol.setWidth(100);
		tCol.setMoveable(false);
		tVCol.setEditingSupport(new CFEditingSupport(viewer, Column.PATH, null));
		tVCol.setLabelProvider(new PathLabelProvider(display));

		tVCol = new TableViewerColumn(viewer, SWT.NONE);
		valueColumn = tVCol.getColumn();
		valueColumn.setText(VALUE_DEFAULT_TEXT);
		valueColumn.setToolTipText("Value of resource (a yellow background indicates a missing value)");
		valueColumn.setWidth(40);
		valueColumn.setMoveable(false);
		tVCol.setEditingSupport(new CFEditingSupport(viewer, Column.VALUE, null));
		tVCol.setLabelProvider(new ValueLabelProvider(display));

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

		createExpressionTable(display);
		
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
		explorer.addSelectionChangedListener(this);
		getSite().setSelectionProvider(this);
	}

	private void createExpressionTable(Display display) {
		expressionViewer = new TableViewer(sashComp, SWT.V_SCROLL);
		
		TableViewerColumn tVCol;
		TableColumn tCol;

		tVCol = new TableViewerColumn(expressionViewer, SWT.NONE);
		tCol = tVCol.getColumn();
		tCol.setText("Use");
		tCol.setToolTipText("Toggle to use in dataset inspector (a yellow background indicates a missing or incompatible dataset)");
		tCol.setWidth(40);
		tCol.setMoveable(false);
		tVCol.setEditingSupport(new CFEditingSupport(expressionViewer, Column.TICK, null));
		tVCol.setLabelProvider(new TickLabelProvider(display));

		tVCol = new TableViewerColumn(expressionViewer, SWT.CENTER);
		tCol = tVCol.getColumn();
		tCol.setText("Expression");
		tCol.setToolTipText("Mathematical exprossion evaluated on the input data");
		tCol.setWidth(40);
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
				expressionList.add(new SelectedNode(expressionList.size(), DEFAULT_EXPRESSION));
				expressionViewer.refresh();
			}
		});

		table.setMenu(exprMenu);
		
		expressionViewer.setContentProvider(ArrayContentProvider.getInstance());
		
		expressionList.add(new SelectedNode(0, DEFAULT_EXPRESSION));
		expressionViewer.setInput(expressionList);
	}

	private void changeSelection() {
		if (currentDatasetSelection != null) {
			selectionChanged(new SelectionChangedEvent(this, currentDatasetSelection));
		}
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
			if (index < 0)
				index = fileList.size();

			String[] files = (String[]) data;
			boolean ok = true;
			for (String f : files)
				ok |= addFile(f, index++);

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
				if (listener != null)
					editor.addListener(listener);
			}
			if (column == Column.COMBO) {
				editor = new ComboBoxViewerCellEditor(viewer.getTable(), SWT.READ_ONLY);
		        ((ComboBoxViewerCellEditor) editor).setLabelProvider(new LabelProvider());
		        ((ComboBoxViewerCellEditor) editor).setContentProvider(new ArrayContentProvider());
				((ComboBoxViewerCellEditor) editor).setInput(MathOp.values());
				if (listener != null)
					editor.addListener(listener);
			}
			if (column == Column.VARIABLE) {
				editor = new ComboBoxViewerCellEditor(viewer.getTable(), SWT.READ_ONLY);
		        ((ComboBoxViewerCellEditor) editor).setLabelProvider(new LabelProvider());
		        ((ComboBoxViewerCellEditor) editor).setContentProvider(new ArrayContentProvider());
				if (listener != null)
					editor.addListener(listener);
			}
			if (column == Column.EXPRESSION) {
				editor = new TextCellEditor(viewer.getTable());
				if (listener != null)
					editor.addListener(listener);
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

		@Override
		protected Object getValue(Object element) {
			if (element instanceof SelectedFile) {
				SelectedFile sf = (SelectedFile) element;
				if (column == Column.TICK) {
					return sf.doUse();
				}
				if (column == Column.COMBO) {
					return sf.getMathOp();
				}
				if (column == Column.VARIABLE) {
			        if (expressionList != null) {
			        	Set<Variable> vars = new HashSet<Variable>();
			        	for (SelectedNode tmp : expressionList) {
			        		vars.addAll(tmp.symbolTable.keySet());
			        	}
			        	((ComboBoxViewerCellEditor)variableEditor.getCellEditor(null)).setInput(vars.toArray());
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
			return null;
		}

		@Override
		protected void setValue(Object element, Object value) {
			if (value == null)
				return;
			if (element instanceof SelectedFile) {
				SelectedFile sf = (SelectedFile) element;
				if (column == Column.TICK) {
					sf.setUse((Boolean) value);
				}
				if (column == Column.COMBO) {
					sf.setMathOp((MathOp) value);
				}
			}
			if (element instanceof SelectedObject) {
				SelectedObject so = (SelectedObject) element;
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
				}

			}
			getViewer().update(element, null);
			changeSelection();
		}

	}

	/**
	 * Loop over all expressions and assign to every variable a set of SelectionObjects associated with it
	 */
	private void updateVariableMappings() {
		List<SelectedFile> selFiles = (List<SelectedFile>) viewer.getInput(); 
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
        			}
        		}
        	}
        }
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
			if (id != null && cls != null) {
				if (edId.contains(id))
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
				node = name.substring(0, name.lastIndexOf(HDF5Node.SEPARATOR)+1);
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
			loadAxisSelections(currentDatasetSelection.getAxes(), node);
		} else {
			return;
		}

		if (currentDatasetSelection != null) {
			List<ILazyDataset> dataList = new ArrayList<ILazyDataset>();
			List<ILazyDataset> metaList = new ArrayList<ILazyDataset>();
			List<List<AxisSelection>> axesList = new ArrayList<List<AxisSelection>>();
			List<MathOp> mathList = new ArrayList<MathOp>();

			for (SelectedFile f : fileList) {
				if (f.doUse() && f.hasData() && f.hasMetaValue()) {
					f.setDataOK(true); // blindly set okay (check later)
					dataList.add(f.getData());
					metaList.add(f.getMetaValue());
					axesList.add(new ArrayList<AxisSelection>(f.getAxisSelections()));
					mathList.add(f.getMathOp());
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
				d.squeeze(true);
			
			// remove incompatible data
			int[][] shapes = AggregateDataset.calcShapes(extend, dataList.toArray(new ILazyDataset[0]));
			int j = shapes.length - 1;
			int[] s = shapes[0];
			final int axis = extend ? 0 : -1;
			for (int k = fileList.size() - 1; k >= 0 && j > 0; k--) {
				SelectedFile f = fileList.get(k);
				if (f.doUse() && f.hasData() && f.hasMetaValue()) {
					boolean ok = AbstractDataset.areShapesCompatible(s, shapes[j], axis);
					f.setDataOK(ok);
					if (!ok) {
						dataList.remove(j);
						metaList.remove(j);
						axesList.remove(j);
						mathList.remove(j);
					}
					j--;
				}
			}

			List<ILazyDataset> processedDataList = new ArrayList<ILazyDataset>();
			List<ILazyDataset> processedMetaList = new ArrayList<ILazyDataset>();
			List<List<AxisSelection>> processedAxesList = new ArrayList<List<AxisSelection>>();
			processSelection(dataList, metaList, axesList, mathList, processedDataList, processedMetaList, processedAxesList);
			
			InspectorType itype;
			switch (currentDatasetSelection.getType()) {
			case IMAGE:
				itype = InspectorType.MULTIIMAGES;
				break;
			case LINE:
			default:
				itype = InspectorType.LINESTACK;
				break;
			}

			setSelection(createSelection(itype, extend, processedDataList, processedMetaList, processedAxesList));
		}

		viewer.refresh();
	}
	 
	private void processSelection(final List<ILazyDataset> dataList,
			final List<ILazyDataset> metaList,
			final List<List<AxisSelection>> axesList,
			final List<MathOp> mathList,
			List<ILazyDataset> processedDataList,
			List<ILazyDataset> processedMetaList,
			List<List<AxisSelection>> processedAxesList) {

		final Integer norm;
		
		// Check if we need to do any maths
		if (mathList.contains(MathOp.ADD) || mathList.contains(MathOp.SUB) || mathList.contains(MathOp.AVR)) {
			norm = Collections.frequency(mathList, MathOp.AVR) + 1;
		} else {
			norm = null;
		}
		
		for (int idx = 0; idx < mathList.size(); idx++) {
			MathOp op = mathList.get(idx);
			switch (op) {
			case DAT:
				final ILazyDataset refData = dataList.get(idx);
				ILazyLoader processingLoader = new ILazyLoader() {

					@Override
					public boolean isFileReadable() {
						return true;
					}

					@Override
					public AbstractDataset getDataset(IMonitor mon, int[] shape, int[] start, int[] stop, int[] step)
							throws ScanFileHolderException {
						AbstractDataset data = DatasetUtils.convertToAbstractDataset(refData.getSlice(start, stop, step));
						AbstractDataset accDataset = null;
						for (int idx = 0; idx < mathList.size(); idx++) {
							MathOp op = mathList.get(idx);
							if (op == MathOp.DAT) {
								continue;
							}
							AbstractDataset tmpData = DatasetUtils.convertToAbstractDataset(dataList.get(idx).getSlice(start, stop, step));
							if (accDataset == null) {
								accDataset = AbstractDataset.zeros(tmpData.getShape(), tmpData.getDtype());
							}
							switch (op) {
							case ADD:
							case AVR:
								accDataset.iadd(tmpData);
								break;
							case SUB:
								accDataset.isubtract(tmpData);
								break;
							default:
								break;
							}
						}
						if (accDataset != null) {
							data.iadd(accDataset);
							if (norm != null && norm > 1) {
								data.idivide(norm);
							}
						}
						return data;
					}
				};
				if (norm != null) {
					processedDataList.add(new LazyDataset(refData.getName(), AbstractDataset.getDType(refData), refData.getShape(), processingLoader));
				} else {
					processedDataList.add(dataList.get(idx));
				}
				processedAxesList.add(axesList.get(idx));
				processedMetaList.add(metaList.get(idx));
				break;
			default:
				break;
			}
		}
		
	}

	/**
	 * 
	 */
	private void setMetaValuesAsIndexes() {
		for (SelectedFile f : fileList) {
			f.setMetaValueAsIndex();
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
					DataHolder holder = explorer.loadFile(f.getAbsolutePath(), null);
					f.setDataHolder(holder);
				} catch (Exception e) {
					continue;
				}
			}
			f.setMetaValue(key);
		}
	}

	/**
	 * Load datasets from selected files
	 */
	private void loadDatasets(String key) {
		for (SelectedFile f : fileList) {
			if (!f.hasDataHolder()) {
				try {
					DataHolder holder = explorer.loadFile(f.getAbsolutePath(), null);
					if (holder == null)
						continue;

					f.setDataHolder(holder);
				} catch (Exception e) {
					continue;
				}
			}
			f.setData(key);
		}
	}

	/**
	 * Load axis selections from selected files
	 */
	private void loadAxisSelections(List<AxisSelection> axes, String node) {
		boolean isFirst = true;

		List<AxisSelection> laxes = new ArrayList<AxisSelection>();
		for (AxisSelection as : axes)
			laxes.add(as.clone());

		for (SelectedFile f : fileList) {
			if (f.doUse() && f.hasData() && (useRowIndexAsValue || f.hasMetaValue())) {
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
		int rank = axes.size();
		for (int i = 0; i < rank; i++) {
			choices.clear();
			choices.addAll(axes.get(i).getNames());

			for (SelectedFile f : fileList) {
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

			for (SelectedFile f : fileList) {
				if (f.hasData()) {
					AxisSelection as = f.getAxisSelections().get(i);
					for (String n : as) {
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
	private List<AxisSelection> makeAxes(List<AxisSelection> oldAxes, SelectedFile file, String node) {
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
	 * @return data selection
	 */
	public static DatasetSelection createSelection(InspectorType itype, List<ILazyDataset> datasets, List<ILazyDataset> metavalues, List<List<AxisSelection>> axisSelectionLists) {
		boolean extend = true;
		for (ILazyDataset m : metavalues) { // if all metadata is multi-valued then do not extend aggregate shape
			if (m.getSize() > 1) {
				extend = false;
				break;
			}
		}
		return createSelection(itype, extend, datasets, metavalues, axisSelectionLists);
	}

	/**
	 * Create a data selection from given lists of datasets, metadata value datasets and axis selection lists  
	 * @param itype 
	 * @param extend
	 * @param datasets
	 * @param metavalues
	 * @param axisSelectionLists
	 * @return data selection
	 */
	public static DatasetSelection createSelection(InspectorType itype, boolean extend, List<ILazyDataset> datasets, List<ILazyDataset> metavalues, List<List<AxisSelection>> axisSelectionLists) {
		AggregateDataset allData = new AggregateDataset(extend, datasets.toArray(new ILazyDataset[0]));
		ILazyDataset[] mvs = metavalues.toArray(new ILazyDataset[0]);
		ILazyDataset mv = mvs[0];
		ILazyDataset allMeta;
		if (mv instanceof AbstractDataset && mv.getRank() == 1) { // concatenate 1D meta-values
			AbstractDataset[] mva = new AbstractDataset[mvs.length];
			for (int i = 0; i < mvs.length; i++) {
				ILazyDataset m = mvs[i]; 
				mva[i] = (m instanceof AbstractDataset) ? (AbstractDataset) m : DatasetUtils.convertToAbstractDataset(m);
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
			if (ias == null)
				continue;

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

		return new DatasetSelection(itype, newAxes, allData);
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
		if (multipleSelection == null)
			return new StructuredSelection(); // Eclipse requires that we do not return null
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
		} else if (selection instanceof FileSelection) {
		} else
			return;

		SelectionChangedEvent e = new SelectionChangedEvent(this, selection);
		for (ISelectionChangedListener listener : listeners)
			listener.selectionChanged(e);
	}
	
	private enum MathOp {
		DAT, ADD, SUB, AVR;
	}

	private class SelectedObject {
		boolean use = true;
		boolean canUseData = false;
		Object f;
		IntegerDataset i;
		ILazyDataset d;
		String variable;
		
		public boolean doUse() {
			return use;
		}

		public void setUse(boolean doUse) {
			use = doUse;
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

		public ILazyDataset getData() {
			return d;
		}

		public void setIndex(int index) {
			i = new IntegerDataset(new int[] {index}, null);
			i.setName(CompareFilesEditor.INDEX);
		}

		public String getIndex() {
			return i.getString(0);
		}

		public String getVariableName() {
			return variable;
		}

		public void setVariableName(String name) {
			this.variable = name;
		}
	}

	private class SelectedFile extends SelectedObject {
		boolean hasMV = false;
		DataHolder h;
		IMetaData m;
		Serializable mv;
		List<AxisSelection> asl;
		MathOp operation;

		public SelectedFile(int index, IFile file) {
			f = new File(file.getLocationURI());
			if (f == null || !((File) f).canRead())
				throw new IllegalArgumentException("File '" + file.getName() + "' does not exist or can not be read");
			setIndex(index);
			setMathOp(MathOp.DAT);
		}

		public SelectedFile(int index, File file) {
			f = file;
			if (f == null || !((File) f).canRead())
				throw new IllegalArgumentException("File '" + file.getName() + "' does not exist or can not be read");
			setIndex(index);
			setMathOp(MathOp.DAT);
		}

		public SelectedFile(int index, String file) {
			f = new File(file);
			if (f == null || !((File) f).canRead())
				throw new IllegalArgumentException("File '" + file + "' does not exist or can not be read");
			setIndex(index);
			setMathOp(MathOp.DAT);
		}

		public String getAbsolutePath() {
			return ((File) f).getAbsolutePath();
		}

		public String getName() {
			return ((File) f).getName();
		}

		@Override
		public String toString() {
			if (mv == null)
				return null;
			return mv.toString();
		}

		public boolean hasMetaValue() {
			return hasMV;
		}

		public boolean hasMetadata() {
			return m != null;
		}

		public boolean hasDataHolder() {
			return h != null;
		}

//		public void setMetadata(IMetaData metadata) {
//			m = metadata;
//		}

		public void setDataHolder(DataHolder holder) {
			h = holder;
			if (h != null)
				m = h.getMetadata();
			else
				d = null;
		}

		public void setData(String key) {
			if (h.contains(key))
				d = h.getLazyDataset(key);
			else {
				int n = h.size();
				d = null;
				for (int i = 0; i < n; i++) {
					ILazyDataset l = h.getLazyDataset(i);
					if (key.equals(l.getName())) {
						d = l;
						break;
					}
				}
			}
		}

//		public void resetData() {
//			d = null;
//		}

		public ILazyDataset getAxis(String key) {
			return h != null ? h.getLazyDataset(key) : null;
		}

		public ILazyDataset getMetaValue() {
			if (!hasMV)
				return null;
			if (mv instanceof ILazyDataset)
				return (ILazyDataset) mv;
			return AbstractDataset.array(mv);
		}

		public void setMetaValueAsIndex() {
			hasMV = true;
			mv = i;
		}

		public void setMetaValue(String key) {
			if (m == null) {
				hasMV = false;
				return;
			}

			try {
				mv = m.getMetaValue(key);
				if (mv instanceof String) {
					mv = Utils.parseValue((String) mv); // TODO parse common multiple values string
					if (mv != null) {
						AbstractDataset a = AbstractDataset.array(mv);
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

		public void setAxisSelections(List<AxisSelection> axisSelectionList) {
			asl = axisSelectionList;
		}

		public List<AxisSelection> getAxisSelections() {
			return asl;
		}

		public boolean hasAxisSelections() {
			return asl != null;
		}
		
		public MathOp getMathOp() {
			return operation;
		}

		public void setMathOp(MathOp operation) {
			this.operation = operation;
		}
	}

	private class SelectedNode extends SelectedObject {
		
		SymbolTable symbolTable;
		JEP jepParser;
		
		public SelectedNode(int index, String str) {
			resetJep();
			if (str != null) {
				setExpression(str);
				setIndex(index);
			}
		}
		
		@Override
		public String toString() {
			if (f == null)
				return null;
			return (String) f;
		}

		public void setExpression(String str) {
			try {
				resetJep();
				jepParser.parse(str);
				symbolTable = jepParser.getSymbolTable();
				f = str;
			} catch (ParseException e) {
				throw new IllegalArgumentException("Parsing input expression failed", e);
			}
		}
		
		private void resetJep() {
			jepParser = new JEP();
			jepParser.setAllowUndeclared(true);
			//jepParser.addStandardConstants();
			jepParser.addStandardFunctions();
		}
		
		@Override
		public ILazyDataset getData() {
			return d;
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

