/*-
 * Copyright (c) 2018 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.rixs.rcp.view;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;

import org.dawnsci.datavis.model.DataOptions;
import org.dawnsci.datavis.model.FileControllerStateEvent;
import org.dawnsci.datavis.model.FileControllerStateEventListener;
import org.dawnsci.datavis.model.FileControllerUtils;
import org.dawnsci.datavis.model.IFileController;
import org.dawnsci.datavis.model.LabelValueMetadata;
import org.dawnsci.datavis.model.LoadedFile;
import org.eclipse.dawnsci.analysis.api.processing.IOperationService;
import org.eclipse.dawnsci.analysis.api.tree.Node;
import org.eclipse.dawnsci.plotting.api.IPlottingService;
import org.eclipse.dawnsci.plotting.api.IPlottingSystem;
import org.eclipse.dawnsci.plotting.api.trace.ILineTrace;
import org.eclipse.dawnsci.plotting.api.trace.ILineTrace.PointStyle;
import org.eclipse.january.DatasetException;
import org.eclipse.january.dataset.Dataset;
import org.eclipse.january.dataset.DatasetFactory;
import org.eclipse.january.dataset.DatasetUtils;
import org.eclipse.january.dataset.ILazyDataset;
import org.eclipse.january.dataset.IntegerDataset;
import org.eclipse.january.dataset.SliceND;
import org.eclipse.january.dataset.SliceNDIterator;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.jface.viewers.CheckboxCellEditor;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ComboViewer;
import org.eclipse.jface.viewers.EditingSupport;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.processing.operations.MetadataUtils;
import uk.ac.diamond.scisoft.rixs.rcp.PostRIXSPerspective;
import uk.ac.diamond.scisoft.rixs.rcp.dialog.AlignDialog;

/*
 * TODO labels are 1D so won't be sufficient for aggregation of nD scans
 * 
 * Tools for spectra
 *  fitting to peaks in selected region and plot FWHM
 *  align peaks or turning points in selected region
 *  
 * Wishlist
 *  ability to load up entire set of scan files from just one (via qscan's metadata)
 *  trigger analysis jobs
 */

public class PostRIXSAggregator {

	protected static final Logger logger = LoggerFactory.getLogger(PostRIXSAggregator.class);

	@Inject IFileController fileController;
	private FileControllerStateEventListener fileStateListener;
	private IPlottingSystem<?> plottingSystem;
	private Map<String, Set<String>> processData = new LinkedHashMap<>();

	private ComboViewer processCombo;
	private TableViewer dataTable;

	private static final String PLUGIN_ID = "uk.ac.diamond.scisoft.rixs.rcp";
	private Image ticked;
	private Image unticked;

	private Map<String, Set<String>> oldSelections = new HashMap<>();
	private List<NameSelect> currentSelection = new ArrayList<>();
	private String currentProcess;

	private AlignDialog dialog;

	@PostConstruct
	public void createComposite(final Composite parent, IPlottingService plottingService, IOperationService opService) {
		fileStateListener = new FileControllerStateEventListener() {

			@Override
			public void stateChanged(FileControllerStateEvent event) {
				if (!PostRIXSPerspective.LOADED_FILE_ID.equals(fileController.getID())) {
					return; // ignore other sources of state changes
				}
				if (!event.isSelectedDataChanged() && !event.isSelectedFileChanged()) return;

				updateGUI();
				plotSelected(false);
			}
		};
	
		fileController.addStateListener(fileStateListener);
		plottingSystem = plottingService.getPlottingSystem(PostRIXSPerspective.PLOT_NAME, true);

		// Create GUI
		parent.setLayout(new FillLayout());

		Composite plotComp = new Composite(parent, SWT.NONE);
		plotComp.setLayout(new GridLayout(1, false));

		Label label;
		// spacer row
		label = new Label(plotComp, SWT.NONE);

		Composite comboComp = new Composite(plotComp, SWT.NONE);
		comboComp.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		comboComp.setLayout(new GridLayout(2, false));
		label = new Label(comboComp, SWT.NONE);
		label.setText("Process:");
		label.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
		processCombo = new ComboViewer(comboComp, SWT.READ_ONLY | SWT.DROP_DOWN);
		processCombo.addSelectionChangedListener(new ISelectionChangedListener() {
			
			@Override
			public void selectionChanged(SelectionChangedEvent event) {
				ISelection s = event.getSelection();
				if (s instanceof IStructuredSelection) {
					updateTable((String) ((IStructuredSelection) s).getFirstElement());
				}
			}
		});
		processCombo.setContentProvider(ArrayContentProvider.getInstance());
		processCombo.getControl().setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		dataTable = new TableViewer(plotComp, SWT.NONE);
		dataTable.setContentProvider(new ArrayContentProvider());
		dataTable.getTable().setHeaderVisible(true);

		ticked = AbstractUIPlugin.imageDescriptorFromPlugin(PLUGIN_ID, "icons/ticked.png").createImage();
		unticked = AbstractUIPlugin.imageDescriptorFromPlugin(PLUGIN_ID, "icons/unticked.gif").createImage();

		TableViewerColumn check   = new TableViewerColumn(dataTable, SWT.CENTER, 0);
		check.setEditingSupport(new CheckBoxEditSupport(dataTable));
		check.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				return "";
			}
			
			@Override
			public Image getImage(Object element) {
				return ((NameSelect) element).isSelected() ? ticked : unticked;
			}
		});
		check.getColumn().setWidth(28);

		TableViewerColumn name = new TableViewerColumn(dataTable, SWT.LEFT);
		name.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				return ((NameSelect) element).getName();
			}
		});
		name.getColumn().setText("Dataset Name");
		name.getColumn().setWidth(200);

		dataTable.getControl().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		// align
		Composite alignComp = new Composite(plotComp, SWT.NONE);
		alignComp.setLayout(new RowLayout());

		Button b = new Button(alignComp, SWT.PUSH);
		b.setText("Align...");
		b.setToolTipText("Open align dialog");
		b.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (dialog == null) {
					dialog = new AlignDialog(parent.getShell(), plottingSystem);
				}
				dialog.open();
			}
		});
		
	}

	@PreDestroy
	public void preDestroy() {
		ticked.dispose();
		unticked.dispose();
		fileController.removeStateListener(fileStateListener);
	}

	private void plotSelected(boolean reset) {
		if (dialog != null) {
			dialog.setRegionVisible(false);
			dialog.resetPlotItems();
		}

		if (reset) {
			plottingSystem.reset();
		} else if (!plottingSystem.getTraces().isEmpty()) {
			plottingSystem.clear();
		} else {
			reset = true;
		}

		List<LoadedFile> files = FileControllerUtils.getSelectedFiles(fileController);

		Map<String, Dataset> plots = createPlotData(files);
		if (plots.isEmpty()) {
			return;
		}

		String xName = null;
		boolean forceReset = reset;
		for (String n : plots.keySet()) {
			Dataset r = plots.get(n);
			Dataset[] axes = MetadataUtils.getAxesAndMakeMissing(r);
			Dataset x = axes.length > 0 ? axes[0] : null;

			if (x == null) {
				x = DatasetFactory.createRange(IntegerDataset.class, r.getSize());
			} else {
				if (xName == null) {
					xName = x.getName();
				}
				forceReset = reset || (x.getSize() > 1 && x.peakToPeak(true).doubleValue() <= Double.MIN_NORMAL);
			}
			ILineTrace l = plottingSystem.createLineTrace(n);
			l.setData(x, r);
			if (r.getSize() == 1) {
				l.setPointStyle(PointStyle.XCROSS);
			}
			plottingSystem.addTrace(l);
		}
		if (xName != null) {
			plottingSystem.getSelectedXAxis().setTitle(xName);
		}
		plottingSystem.repaint(forceReset);
		if (dialog != null) {
			dialog.refreshRegion();
		}
	}

	private Map<String, Dataset> createPlotData(List<LoadedFile> files) {
		if (files.isEmpty()) {
			return Collections.emptyMap();
		}

		Map<String, Dataset> plots = new LinkedHashMap<>();

		List<NameSelect> selection = currentSelection.stream()
				.filter(n -> n.isSelected()).collect(Collectors.toList());

		if (selection.size() > 0 && currentProcess != null) {
			List<String> selected;
			int b;
			if (RESULT.equals(currentProcess)) {
				b = -1;
				selected = new ArrayList<>();
				selected.add(RESULT_SUFFIX);
			} else {
				b = currentProcess.length() + 1;
				selected = selection.stream().map(n -> currentProcess + Node.SEPARATOR + n.getName())
					.collect(Collectors.toList());
			}

			List<ILazyDataset> lp = new ArrayList<>();
			for (LoadedFile f : files) {
				LabelValueMetadata lv = f.getLabelValue() == null ? null : new LabelValueMetadata(f.getLabelValue());
				String fn = files.size() > 1 ? f.getName() : null;
				for (DataOptions dop : f.getDataOptions(true)) {
					String n = dop.getName();
					for (String s : selected) {
						if (n.contains(s)) {
							ILazyDataset l = dop.getLazyDataset().getSliceView();
							String suffix = b >= 0 ? s.substring(b) : RESULT;
							l.setName(fn == null ? suffix : fn + File.pathSeparator + suffix);
							if (lv != null) {
								l.addMetadata(lv);
							}
							lp.add(l);
							break;
						}
					}
				}
			}
//			files.stream().map(f -> f.getDataOptions(true))
//					.flatMap(Collection::stream)
//					.filter(o -> selected.stream().anyMatch(s -> o.getName().contains(s)))
//					.map(o -> o.getLazyDataset())
//					.collect(Collectors.toList());

			Map<String, List<Object>> singles = new LinkedHashMap<>();
			for (ILazyDataset l : lp) {
				addPlotData(plots, singles, l);
			}
			for (String n : singles.keySet()) { // synthesize line plot data from lists
				List<Object> objs = singles.get(n);
				int imax = objs.size();
				if (imax < 1) {
					continue;
				}
				List<Object> a = new ArrayList<>();
				List<Object> v = new ArrayList<>();
				for (int i = 0; i < imax; i += 2) {
					a.add(objs.get(i));
					v.add(objs.get(i + 1));
				}
				String[] names = n.split(File.pathSeparator);
				Dataset y = DatasetFactory.createFromList(v);
				y.setName(names[0]);
				Dataset x = DatasetFactory.createFromList(a);
				if (names.length > 1) {
					x.setName(names[1]);
				}
				MetadataUtils.setAxes(y, x);
				plots.put(names[0], y);
			}
		}

		return plots;
	}

	private void addPlotData(Map<String, Dataset> plots, Map<String, List<Object>> singles, ILazyDataset l) {
		LabelValueMetadata lv = l.getFirstMetadata(LabelValueMetadata.class);
		Dataset v = lv == null ? null : lv.getLabelValue();
		String vn = v == null ? "" : v.getName();

		String n = l.getName();
		int i = -1;
		for (Dataset d : create1DPlotData(l)) {
			i++;
			if (d == null) {
				continue;
			} else if (d.getRank() == 0) { // accumulate single-point data
				int j = n.lastIndexOf(File.pathSeparator) + 1; // remove file name
				String name = (j > 0 ? n.substring(j) : n) + File.pathSeparator + vn;
				System.err.println(name);
				List<Object> single = singles.get(name);
				if (single == null) {
					single = new ArrayList<>();
					singles.put(name, single);
				}
				single.add(v == null ? i : v.getRank() == 0 ? v.getObject() : v.getObject(i));
				single.add(d.getObject());
			} else {
				String name;
				if (v == null) {
					name = String.format("%s%s%d", n, File.pathSeparator, i);
				} else {
					name = String.format("%s%s%d (%s)", n, File.pathSeparator, i, v.getRank() == 0 ? v.getObject() : v.getObject(i));
				}
				plots.put(name, d);
			}
		}
	}

	public List<Dataset> create1DPlotData(ILazyDataset l) {
		List<Dataset> pd = new ArrayList<>();
		ILazyDataset lz = l.getSliceView().squeezeEnds();
		int[] shape = lz.getShape();
		try {
			if (shape.length > 1) {
				SliceNDIterator it = new SliceNDIterator(new SliceND(shape), shape.length - 1);
				SliceND s = it.getCurrentSlice();
				while (it.hasNext()) {
					pd.add(DatasetUtils.convertToDataset(lz.getSlice(s)).squeeze());
				}
			} else {
				Dataset d = DatasetUtils.sliceAndConvertLazyDataset(lz);
				d.setName(lz.getName());
				pd.add(d);
			}
		} catch (DatasetException e) {
			logger.warn("Could not create 1D plot data", e);
		}
		return pd;
	}

	// capture /entry/(auxiliary|summary)/%d-PROCESS_NAME/DATA_NAME
	static final Pattern PROCESS_REGEX = Pattern.compile("/[^/]+/[^/]+/\\d+-([^/]+)/(.+)");
	static final String DATA = "/data";
	static final String RESULT = "result";
	static final String RESULT_SUFFIX = RESULT + DATA;

	private void updateGUI() {
		List<LoadedFile> files = FileControllerUtils.getSelectedFiles(fileController);
		processData.clear();
		for (LoadedFile f : files) {
			logger.debug(f.getFilePath());
			List<DataOptions> opts = f.getDataOptions(true);
			for (DataOptions o : opts) {
				filterProcessDataByName(o.getName());
			}
			for (String n : f.getLabelOptions()) {
				if (f.isSignal(n)) {
					filterProcessDataByName(n);
				}
			}
		}

		// parse for processes
		List<String> ps =  new ArrayList<>(processData.keySet());
		int previous = ps.indexOf(currentProcess);
		if (previous < 0 && !ps.isEmpty()) {
			previous = ps.size() - 1;
			if (RESULT.equals(ps.get(previous)) && previous > 0) { // do not choose RESULT
				previous--;
			}
		}
		final int choice = previous;
		Display.getDefault().asyncExec(new Runnable() {
			@Override
			public void run() { // update combo and select last process
				processCombo.setInput(ps);
				processCombo.getCombo().select(choice);
			}
		});

		updateTable(choice >= 0 ? ps.get(choice) : null);
	}

	private void filterProcessDataByName(String n) {
		Matcher m = PROCESS_REGEX.matcher(n);
		if (m.matches()) {
			String p = m.group(1);
			String d = m.group(2);
			if (d.endsWith(DATA)) {
				d = d.substring(0, d.length() - DATA.length());
			}
			logger.debug("\t{} : {}", p, d);
			Set<String> s = processData.get(p);
			if (s == null) {
				s = new LinkedHashSet<>();
				processData.put(p, s);
			}
			s.add(d);
		} else if (n.endsWith(RESULT_SUFFIX)) {
			logger.debug("\t{}", n);
			processData.put(RESULT, null);
		} else {
			logger.debug("\tIgnoring {}", n);
		}
	}

	private void updateTable(String process) {
		if (currentProcess != null) { // store old selection
			Set<String> old = oldSelections.get(currentProcess);
			if (old == null) {
				old = new HashSet<>();
				oldSelections.put(currentProcess, old);
			} else {
				old.clear();
			}

			for (NameSelect ns : currentSelection) {
				if (ns.isSelected()) {
					old.add(ns.getName());
				}
			}
		}

		currentSelection.clear();
		Set<String> old = process == null || !oldSelections.containsKey(process)? Collections.emptySet() : oldSelections.get(process);
		currentProcess = process;

		Set<String> names = process == null ? null : processData.get(process);
		if (names != null) {
			for (String n : names) {
				NameSelect ns = new NameSelect(n);
				ns.setSelected(old.contains(n)); // preselect from old selection
				currentSelection.add(ns);
			}
		} else if (RESULT.equals(process)) {
			NameSelect ns = new NameSelect(RESULT);
			ns.setSelected(old.contains(RESULT));
			currentSelection.add(ns);
		}

		Display.getDefault().asyncExec(new Runnable() {
			@Override
			public void run() {
				dataTable.setInput(currentSelection);
				dataTable.refresh();
			}
		});
	}

	private static class NameSelect {
		private String name;
		private boolean selected;
		public NameSelect(String name) {
			this.name = name;
			this.selected = false;
		}

		public String getName() {
			return name;
		}

		public boolean isSelected() {
			return selected;
		}

		public void setSelected(boolean selected) {
			this.selected = selected;
		}
	}

	private class CheckBoxEditSupport extends EditingSupport {
		private final CheckboxCellEditor edit;

		public CheckBoxEditSupport(TableViewer viewer) {
			super(viewer);
			this.edit = new CheckboxCellEditor(dataTable.getTable());
		}

		@Override
		protected CellEditor getCellEditor(Object element) {
			return edit;
		}

		@Override
		protected boolean canEdit(Object element) {
			return true;
		}

		@Override
		protected Object getValue(Object element) {
			if (element instanceof NameSelect) {
				return ((NameSelect) element).isSelected();
			}
			return null;
		}

		@Override
		protected void setValue(Object element, Object value) {
			if (element instanceof NameSelect && value instanceof Boolean) {
				((NameSelect) element).setSelected((Boolean) value);
				plotSelected(false);
				getViewer().update(element, null);
			}
		}
	}
}
