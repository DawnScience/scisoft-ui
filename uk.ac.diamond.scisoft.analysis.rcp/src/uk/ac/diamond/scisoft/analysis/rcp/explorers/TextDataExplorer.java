/*
 * Copyright (c) 2012 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.analysis.rcp.explorers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.lang3.ArrayUtils;
import org.eclipse.dawnsci.analysis.api.io.IDataHolder;
import org.eclipse.january.IMonitor;
import org.eclipse.january.dataset.Dataset;
import org.eclipse.january.dataset.DatasetFactory;
import org.eclipse.january.dataset.IDataset;
import org.eclipse.january.dataset.ILazyDataset;
import org.eclipse.january.dataset.IntegerDataset;
import org.eclipse.january.metadata.IMetadata;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MenuEvent;
import org.eclipse.swt.events.MenuListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.IWorkbenchPartSite;
import org.eclipse.ui.PlatformUI;

import uk.ac.diamond.scisoft.analysis.axis.AxisChoice;
import uk.ac.diamond.scisoft.analysis.io.LoaderFactory;
import uk.ac.diamond.scisoft.analysis.rcp.inspector.AxisSelection;
import uk.ac.diamond.scisoft.analysis.rcp.inspector.DatasetSelection;
import uk.ac.diamond.scisoft.analysis.rcp.inspector.DatasetSelection.InspectorType;

public class TextDataExplorer extends AbstractExplorer implements ISelectionProvider {

	private TableViewer viewer;
	private IDataHolder data = null;
	private ISelectionChangedListener listener;
	private Display display = null;
	private SelectionAdapter contextListener = null;
	private Menu contextMenu;

	public TextDataExplorer(Composite parent, IWorkbenchPartSite partSite, ISelectionChangedListener valueSelect) {
		super(parent, partSite, valueSelect);

		display = parent.getDisplay();
		setLayout(new FillLayout());

		viewer = new TableViewer(this, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL);
		viewer.getTable().setHeaderVisible(true);

		TableColumn tc = new TableColumn(viewer.getTable(), SWT.LEFT);
		tc.setText("Name");
		tc.setWidth(200);
		tc = new TableColumn(viewer.getTable(), SWT.LEFT);
		tc.setText("min");
		tc.setWidth(100);
		tc = new TableColumn(viewer.getTable(), SWT.LEFT);
		tc.setText("max");
		tc.setWidth(100);
		tc = new TableColumn(viewer.getTable(), SWT.LEFT);
		tc.setText("Class");
		tc.setWidth(100);

		viewer.setContentProvider(new ViewContentProvider());
		viewer.setLabelProvider(new ViewLabelProvider());
		// viewer.setInput(getEditorSite());

		listener = new ISelectionChangedListener() {
			@Override
			public void selectionChanged(SelectionChangedEvent arg0) {
				selectItemSelection();
			}
		};

		viewer.addSelectionChangedListener(listener);
		final SelectionAdapter slistener = new SelectionAdapter() {
			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
				super.widgetDefaultSelected(e);
			}
		};

		// add context menu
		contextMenu = new Menu(viewer.getControl());
		contextMenu.addMenuListener(new MenuListener() {
			@Override
			public void menuShown(MenuEvent e) {
				// populate
//				contextMenu.
			}

			@Override
			public void menuHidden(MenuEvent e) {
			}
		});

		// set selection first and then omit in SHOW listener

		// NB preserve this order according to constants above
		MenuItem errorsItem = new MenuItem(contextMenu, SWT.PUSH);
		errorsItem.addSelectionListener(slistener);
		errorsItem.setText("Assign as errors for...");
		viewer.getTable().setMenu(contextMenu);

		if (metaValueListener != null) {
			final TextDataExplorer provider = this;
			contextListener = new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					int i = viewer.getTable().getMenu().indexOf((MenuItem) e.widget);
					SelectionChangedEvent ce = new SelectionChangedEvent(provider, new MetadataSelection(metaNames.get(i)));
					metaValueListener.selectionChanged(ce);
				}
			};
		}
	}

	@Override
	public TableViewer getViewer() {
		return viewer;
	}

	private class ViewContentProvider implements IStructuredContentProvider {
		@Override
		public void inputChanged(Viewer v, Object oldInput, Object newInput) {
		}

		@Override
		public void dispose() {
		}

		@Override
		public Object[] getElements(Object parent) {
			return data.getList().toArray();
		}
	}

	private class ViewLabelProvider extends LabelProvider implements ITableLabelProvider {
		@Override
		public String getColumnText(Object obj, int index) {
			if (obj instanceof IDataset) {
				IDataset dataset = (IDataset) obj;
				if (index == 0)
					return dataset.getName();
				try {
					if (index == 1)
						return dataset.min().toString();
					if (index == 2)
						return dataset.max().toString();
				} catch (NullPointerException e) {
				}
				if (index == 3) {
					String[] parts = dataset.getElementClass().toString().split("\\.");
					return parts[parts.length - 1];
				}
			}
			if (obj instanceof ILazyDataset) {
				ILazyDataset dataset = (ILazyDataset) obj;
				if (index == 0)
					return dataset.getName();
				if (index == 1)
					return "-";
				if (index == 2)
					return "-";
				if (index == 3)
					return "Lazy";
			}

			return null;
		}

		@Override
		public Image getColumnImage(Object obj, int index) {
			if (index == 0)
				return getImage(obj);
			return null;
		}

		@Override
		public Image getImage(Object obj) {
			return PlatformUI.getWorkbench().getSharedImages().getImage(ISharedImages.IMG_OBJ_ELEMENT);
		}
	}

	private List<ISelectionChangedListener> listeners = new ArrayList<ISelectionChangedListener>();
	private DatasetSelection dSelection = null;
	private ArrayList<String> metaNames;
	private String fileName;

	@Override
	public void addSelectionChangedListener(ISelectionChangedListener listener) {
		if (!listeners.contains(listener)) {
			listeners.add(listener);
		}
	}

	@Override
	public ISelection getSelection() {
		if (dSelection == null)
			return new StructuredSelection(); // Eclipse requires that we do not return null
		return dSelection;
	}

	@Override
	public void removeSelectionChangedListener(ISelectionChangedListener listener) {
		listeners.remove(listener);
	}

	@Override
	public void setSelection(ISelection selection) {
		if (selection instanceof DatasetSelection)
			dSelection = (DatasetSelection) selection;
		else return;

		SelectionChangedEvent e = new SelectionChangedEvent(this, dSelection);
		for (ISelectionChangedListener listener : listeners)
			listener.selectionChanged(e);
	}

	@Override
	public void dispose() {
		viewer.removeSelectionChangedListener(listener);
		data = null;
		Table table = viewer.getTable();
		if (!table.isDisposed()) {
			for (MenuItem i : table.getMenu().getItems()) {
				i.removeSelectionListener(contextListener);
			}
		}
	}

	@Override
	public IDataHolder loadFile(String fileName, IMonitor mon) throws Exception {
		if (fileName == this.fileName)
			return data;

//		return LoaderFactory.getData(fileName, true, false, true, mon);
		return LoaderFactory.getData(fileName, true, mon);
	}

	@Override
	public void loadFileAndDisplay(String fileName, IMonitor mon) throws Exception {
		this.fileName = fileName;

//		data = LoaderFactory.getData(fileName, true, false, true, mon);
		data = LoaderFactory.getData(fileName, true, mon);

		if (data != null) {
			if (display != null) {
				final IMetadata meta = data.getMetadata();

				display.asyncExec(new Runnable() {
					
					@Override
					public void run() {
						viewer.setInput(data);
						display.update();
						if (metaValueListener != null) {
							addMenu(meta);
						}
					}
				});
			}

			selectItemSelection();
		}
	}

	private ILazyDataset getActiveData() {
		ISelection selection = viewer.getSelection();
		Object obj = ((IStructuredSelection) selection).getFirstElement();
		if (obj == null) {
			return data.getLazyDataset(0);
		}
		return (ILazyDataset) obj;
	}

	private List<AxisSelection> getAxes(ILazyDataset d) {
		List<AxisSelection> axes = new ArrayList<AxisSelection>();
	
		int[] shape = d.getShape();
	
		for (int j = 0; j < shape.length; j++) {
			final int len = shape[j];
			AxisSelection axisSelection = new AxisSelection(len, j);
			axes.add(axisSelection);
	
			for (int i = 0, imax = data.size(); i < imax; i++) {
				ILazyDataset ldataset = data.getLazyDataset(i);
				if (ldataset.equals(d))
					continue;
				if (ArrayUtils.contains(ldataset.getShape(), len)) {
					AxisChoice newChoice = new AxisChoice(ldataset);
					newChoice.setAxisNumber(j);
					axisSelection.addChoice(newChoice, i + 1);
				}
			}

			Dataset autoAxis = DatasetFactory.createRange(IntegerDataset.class, len);
			autoAxis.setName(AbstractExplorer.DIM_PREFIX + (j+1));
			AxisChoice newChoice = new AxisChoice(autoAxis);
			newChoice.setAxisNumber(j);
			axisSelection.addChoice(newChoice, 0);
	
		}
	
		return axes;
	}

	public void selectItemSelection() {
		ILazyDataset d = getActiveData();
		if (d == null) return;
		d.setMetadata(data.getMetadata());
		DatasetSelection datasetSelection = new DatasetSelection(fileName, getAxes(d), d);
		if (d.getRank() > 1) {
			datasetSelection.setType(InspectorType.IMAGE);
		}
		setSelection(datasetSelection);
	}

	private void addMenu(IMetadata meta) {
		// create context menu and handling
		if (meta != null) {
			Collection<String> names = null;
			try {
				names = meta.getMetaNames();
			} catch (Exception e1) {
				return;
			}
			if (names == null) {
				return;
			}
			try {
				Menu context = new Menu(viewer.getControl());
				metaNames = new ArrayList<String>();
				for (String n : meta.getMetaNames()) {
					try {
						String v = meta.getMetaValue(n).toString();
						Double.parseDouble(v);
						metaNames.add(n);
						MenuItem item = new MenuItem(context, SWT.PUSH);
						item.addSelectionListener(contextListener);
						item.setText(n + " = " + v);
					} catch (NumberFormatException e) {
						
					}
				}

				viewer.getTable().setMenu(context);
			} catch (Exception e) {
			}
		}
	}
}
