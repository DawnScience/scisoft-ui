package uk.ac.diamond.scisoft.rixs.rcp.view;

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
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.processing.operations.MetadataUtils;
import uk.ac.diamond.scisoft.rixs.rcp.PostRIXSPerspective;

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

	@PostConstruct
	public void createComposite(Composite parent, IPlottingService plottingService, IOperationService opService) {
		fileStateListener = new FileControllerStateEventListener() {

			@Override
			public void stateChanged(FileControllerStateEvent event) {
				if (!PostRIXSPerspective.LOADED_FILE_ID.equals(fileController.getID())) {
					return; // ignore other sources of state changes
				}
				if (!event.isSelectedDataChanged() && !event.isSelectedFileChanged()) return;

				updateGUI();
				plotSelected(true);
			}

		};
	
		fileController.addStateListener(fileStateListener);
		plottingSystem = plottingService.getPlottingSystem(PostRIXSPerspective.PLOT_NAME, true);

		// Create GUI
		parent.setLayout(new GridLayout());

		Composite plotComp = new Composite(parent, SWT.NONE);
		plotComp.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		plotComp.setLayout(new GridLayout(2, false));

		Label label;
		// spacer row
		label = new Label(plotComp, SWT.NONE);
		label = new Label(plotComp, SWT.NONE);

		label = new Label(plotComp, SWT.NONE);
		label.setText("Process:");
		label.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
		processCombo = new ComboViewer(plotComp, SWT.READ_ONLY | SWT.DROP_DOWN);
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

		dataTable.getControl().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1));
	}

	@PreDestroy
	public void preDestroy() {
		ticked.dispose();
		unticked.dispose();
		fileController.removeStateListener(fileStateListener);
	}

	private void plotSelected(boolean reset) {
		List<LoadedFile> files = fileController.getSelectedFiles();
		if (files.isEmpty()) {
			plottingSystem.clear();
			return;
		}

		Map<String, Dataset> plots = createPlotData(files);
		if (plots.isEmpty()) {
			return;
		}

		if (reset) {
			plottingSystem.reset();
		} else if (!plottingSystem.getTraces().isEmpty()) {
			plottingSystem.clear();
		} else {
			reset = true;
		}

		String xName = null;
		for (String n : plots.keySet()) {
			Dataset r = plots.get(n);
			Dataset[] axes = MetadataUtils.getAxesAndMakeMissing(r);
			Dataset x = axes.length > 0 ? axes[0] : null;

			if (x == null || x.peakToPeak(true).doubleValue() <= Double.MIN_NORMAL) {
				x = DatasetFactory.createRange(IntegerDataset.class, r.getSize());
			}
			if (xName == null) {
				xName = x.getName();
			}
			ILineTrace l = plottingSystem.createLineTrace(n);
			if (r.getSize() == 1) {
				l.setPointStyle(PointStyle.XCROSS);
			}
			plottingSystem.addTrace(l);
			l.setData(x, r);
		}
		plottingSystem.getSelectedXAxis().setTitle(xName);
		plottingSystem.repaint(reset);
	}

	private Map<String, Dataset> createPlotData(List<LoadedFile> files) {
		Map<String, Dataset> plots = new LinkedHashMap<>();

		if (currentProcess != null) {
			List<String> selected = currentSelection.stream()
					.filter(n -> n.isSelected())
					.map(n -> currentProcess + Node.SEPARATOR + n.getName())
					.collect(Collectors.toList());

			List<ILazyDataset> lp = new ArrayList<>();
			int b = currentProcess.length() + 1;
			for (LoadedFile f : files) {
				LabelValueMetadata lv = f.getLabelValue() == null ? null : new LabelValueMetadata(f.getLabelValue());
				String fn = files.size() > 1 ? f.getName() : null;
				for (DataOptions dop : f.getDataOptions(true)) {
					String n = dop.getName();
					for (String s : selected) {
						if (n.contains(s)) {
							ILazyDataset l = dop.getLazyDataset().getSliceView();
							l.setName(fn == null ? s.substring(b) : fn + ":" + s.substring(b));
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

			System.err.println("Plotting:");
			for (ILazyDataset l : lp) {
				addPlotData(plots, l);
			}
		}

		return plots;
	}

	private void addPlotData(Map<String, Dataset> plots, ILazyDataset l) {
		LabelValueMetadata lv = l.getFirstMetadata(LabelValueMetadata.class);
		Dataset v = lv == null ? null : lv.getLabelValue();
		String n = l.getName();
		int i = -1;
		for (Dataset d : create1DPlotData(l)) {
			i++;
			if (d == null) {
				continue;
			}
			String name;
			if (v == null) {
				name = String.format("%s:%d", n, i);
			} else {
				name = String.format("%s:%d (%s)", n, i, v.getRank() == 0 ? v.getObject() : v.getObject(i));
			}
			plots.put(name, d);
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
					lz = l.getSliceView().squeezeEnds(); // workaround January #302 bug TODO remove when 2.2 is released
				}
			} else {
				pd.add(DatasetUtils.sliceAndConvertLazyDataset(lz));
			}
		} catch (DatasetException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return pd;
	}

	// capture /entry/(auxiliary|summary)/%d-PROCESS_NAME/DATA_NAME
	static final Pattern PROCESS_REGEX = Pattern.compile("/[^/]+/[^/]+/\\d+-([^/]+)/(.+)");
	static final String DATA = "/data";

	private void updateGUI() {
		List<LoadedFile> files = fileController.getSelectedFiles();
		processData.clear();
		for (LoadedFile f : files) {
			logger.debug(f.getFilePath());
			List<DataOptions> opts = f.getDataOptions(true);
			for (DataOptions o : opts) {
				String n = o.getName();
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
				} else {
					logger.debug("Ignoring {}", n);
				}
			}
		}

		// parse for processes
		List<String> ps =  new ArrayList<>(processData.keySet());
		final int last = ps.size() - 1;
		Display.getDefault().asyncExec(new Runnable() {
			@Override
			public void run() { // update combo and select last process
				processCombo.setInput(ps);
				processCombo.getCombo().select(last);
			}
		});

		updateTable(last >= 0 ? ps.get(last) : null);
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
		Set<String> old = process == null || !oldSelections.containsKey(process )? Collections.emptySet() : oldSelections.get(process);
		currentProcess = process;

		Set<String> names = process == null ? null : processData.get(process);
		if (names != null) {
			for (String n : names) {
				NameSelect ns = new NameSelect(n);
				ns.setSelected(old.contains(n)); // preselect from old selection
				currentSelection.add(ns);
			}
		}
		Display.getDefault().asyncExec(new Runnable() {
			@Override
			public void run() {
				dataTable.setInput(currentSelection);
				dataTable.refresh();
			}
		});
	}

	private class NameSelect {
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
			plotSelected(false);
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
				getViewer().update(element, null);
			}
		}
	}
}
