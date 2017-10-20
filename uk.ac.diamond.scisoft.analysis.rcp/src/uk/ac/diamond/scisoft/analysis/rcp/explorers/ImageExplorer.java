/*
 * Copyright (c) 2012 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.analysis.rcp.explorers;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import org.dawb.common.util.list.SortNatural;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.dawnsci.analysis.api.io.IDataHolder;
import org.eclipse.january.IMonitor;
import org.eclipse.january.dataset.Dataset;
import org.eclipse.january.dataset.DatasetFactory;
import org.eclipse.january.dataset.DoubleDataset;
import org.eclipse.january.dataset.IDataset;
import org.eclipse.january.dataset.ILazyDataset;
import org.eclipse.january.dataset.IntegerDataset;
import org.eclipse.january.dataset.LazyDataset;
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
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.IWorkbenchPartSite;
import org.eclipse.ui.PlatformUI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.axis.AxisChoice;
import uk.ac.diamond.scisoft.analysis.io.AbstractFileLoader;
import uk.ac.diamond.scisoft.analysis.io.DataHolder;
import uk.ac.diamond.scisoft.analysis.io.ImageStackLoader;
import uk.ac.diamond.scisoft.analysis.io.LoaderFactory;
import uk.ac.diamond.scisoft.analysis.rcp.inspector.AxisSelection;
import uk.ac.diamond.scisoft.analysis.rcp.inspector.DatasetSelection;
import uk.ac.diamond.scisoft.analysis.rcp.inspector.DatasetSelection.InspectorType;
import uk.ac.diamond.scisoft.analysis.rcp.monitor.ProgressMonitorWrapper;

public class ImageExplorer extends AbstractExplorer implements ISelectionProvider {

	private static final Logger logger = LoggerFactory.getLogger(ImageExplorer.class);
	
	private static final String FOLDER_STACK = "Image Stack";
	private TableViewer viewer;
	private DataHolder data = null;
	private ISelectionChangedListener listener;
	private Display display = null;
	private SelectionAdapter contextListener = null;

	public ImageExplorer(Composite parent, IWorkbenchPartSite partSite, ISelectionChangedListener valueSelect) {
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

		if (metaValueListener != null) {
			final ImageExplorer provider = this;
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
				if (index == 1)
					return dataset.min(true).toString();
				if (index == 2)
					return dataset.max(true).toString();
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
		MenuItem[] items = viewer.getTable().getMenu().getItems();
		for (MenuItem i : items) {
			i.removeSelectionListener(contextListener);
		}
	}

	@Override
	public IDataHolder loadFile(String fileName, IMonitor mon) throws Exception {
		if (fileName == this.fileName)
			return data;

		return LoaderFactory.getData(fileName, true, false, true, mon);
	}

	@Override
	public void loadFileAndDisplay(String fileName, IMonitor mon) throws Exception {
		this.fileName = fileName;

		IDataHolder loadedData = LoaderFactory.getData(fileName, true, false, true, mon);
		data = new DataHolder();
		if (loadedData != null) {
			for (String name : loadedData.getNames()) {
				data.addDataset(name, loadedData.getLazyDataset(name), loadedData.getMetadata());
			} 
			
			// Add a placeholder to let the user know they can do this.
			addPlaceholderFolderStack();
			
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

	private void addPlaceholderFolderStack() {
		DoubleDataset dataset = DatasetFactory.zeros(DoubleDataset.class, 1);
		dataset.setName(FOLDER_STACK);
		data.addDataset(dataset.getName(), dataset);
	}

	/**
	 * Has a look at the folder to see if there are any other images there of the same type, and if so it creates a virtual stack with them all in.
	 * @param mon a monitor for the initial stack creation
	 * @throws Exception if there is a problem with the creation of the stack.
	 */
	private void addAllFolderStack(IMonitor mon) throws Exception {
		List<String> imageFilenames = new ArrayList<String>();
		File file = new File(fileName);
		int index = fileName.lastIndexOf(".");
		String ext = fileName.substring(index);
		File parent = new File(file.getParent());
		if(parent.isDirectory()) {
			for (String fName : parent.list()) {
				if (fName.endsWith(ext)) imageFilenames.add((new File(parent,fName)).getAbsolutePath());
			}
		}
		
		if (imageFilenames.size() > 1) {
 		    Collections.sort(imageFilenames, new SortNatural<String>(true));
			ImageStackLoader loader = new ImageStackLoader(imageFilenames , mon);
			LazyDataset lazyDataset = new LazyDataset(FOLDER_STACK, loader.getDType(), loader.getShape(), loader);
			data.addDataset(lazyDataset.getName(), lazyDataset);
		}
		
	}

	private ILazyDataset getActiveData() {
		ISelection selection = viewer.getSelection();
		Object obj = ((IStructuredSelection) selection).getFirstElement();
		ILazyDataset d;
		if (obj == null) {
			d = data.getLazyDataset(0);
		} else if (obj instanceof ILazyDataset) {
			d = (ILazyDataset) obj;
		} else
			return null;
	
		if (d.getRank() == 1) {
			d.setShape(1, d.getShape()[0]);
		}
		return d;
	}

	private List<AxisSelection> getAxes(ILazyDataset d) {
		List<AxisSelection> axes = new ArrayList<AxisSelection>();
	
		int[] shape = d.getShape();
	
		for (int j = 0; j < shape.length; j++) {
			final int len = shape[j];
			AxisSelection axisSelection = new AxisSelection(len, j);
			axes.add(axisSelection);
	
			Dataset autoAxis = DatasetFactory.createRange(IntegerDataset.class, len);
			autoAxis.setName(AbstractExplorer.DIM_PREFIX + (j+1));
			AxisChoice newChoice = new AxisChoice(autoAxis);
			newChoice.setAxisNumber(j);
			axisSelection.addChoice(newChoice, 0);
	
			for (int i = 0, imax = data.size(); i < imax; i++) {
				ILazyDataset ldataset = data.getLazyDataset(i);
				if (ldataset.equals(d))
					continue;
			}
		}
	
		return axes;
	}

	public void selectItemSelection() {
		ILazyDataset d = getActiveData();
		if (d == null)
			return;
		
		if (d.getName().contains(FOLDER_STACK)) {
			if (d.getShape().length < 3 ) {
				data.remove(1);
				
				Job job = new Job("Load Image Stack") {

					@Override
					protected IStatus run(IProgressMonitor monitor) {
						try {
							addAllFolderStack(new ProgressMonitorWrapper(monitor));
						} catch (Exception e) {
							logger.error("Failed to load Image Stack sucsesfully", e);
						}
						
						
						PlatformUI.getWorkbench().getDisplay().syncExec(new Runnable() {
							
							@Override
							public void run() {
								viewer.refresh();
								ILazyDataset ds = data.getLazyDataset(FOLDER_STACK);
								if (ds != null) {
									ds = ds.clone();
									ds.setName(new File(fileName).getName() + AbstractFileLoader.FILEPATH_DATASET_SEPARATOR + ds.getName());
									DatasetSelection datasetSelection = new DatasetSelection(InspectorType.IMAGE, fileName, getAxes(ds), ds);
									setSelection(datasetSelection);
								}
							}
						});
						return Status.OK_STATUS;
					}
				};
				
				job.setUser(true);
				job.setPriority(Job.INTERACTIVE);
				job.schedule();
			}
		}
		
		d = d.getSliceView();
		d.setName(new File(fileName).getName() + AbstractFileLoader.FILEPATH_DATASET_SEPARATOR + d.getName());
		DatasetSelection datasetSelection = new DatasetSelection(InspectorType.IMAGE, fileName, getAxes(d), d);
		setSelection(datasetSelection);
	}

	private final static Pattern SPACES = Pattern.compile("\\s+");

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
				for (String n : names) {
					try { // find entries with numerical values (and drop its units)
						String[] vs = SPACES.split(meta.getMetaValue(n).toString());
						for (String v : vs) {
							Double.parseDouble(v);
							metaNames.add(n);
							MenuItem item = new MenuItem(context, SWT.PUSH);
							item.addSelectionListener(contextListener);
							item.setText(n + " = " + v);
							break;
						}
					} catch (NumberFormatException e) {
						
					}
				}

				viewer.getTable().setMenu(context);
			} catch (Exception e) {
			}
		}
	}
}
