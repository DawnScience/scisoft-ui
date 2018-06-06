/*-
 * Copyright (c) 2018 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

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
import org.eclipse.dawnsci.analysis.api.fitting.functions.IParameter;
import org.eclipse.dawnsci.analysis.api.processing.IOperationService;
import org.eclipse.dawnsci.analysis.api.roi.IRectangularROI;
import org.eclipse.dawnsci.analysis.api.tree.Node;
import org.eclipse.dawnsci.plotting.api.IPlottingService;
import org.eclipse.dawnsci.plotting.api.IPlottingSystem;
import org.eclipse.dawnsci.plotting.api.axis.IAxis;
import org.eclipse.dawnsci.plotting.api.region.IROIListener;
import org.eclipse.dawnsci.plotting.api.region.IRegion;
import org.eclipse.dawnsci.plotting.api.region.IRegion.RegionType;
import org.eclipse.dawnsci.plotting.api.region.IRegionListener;
import org.eclipse.dawnsci.plotting.api.region.ROIEvent;
import org.eclipse.dawnsci.plotting.api.region.RegionEvent;
import org.eclipse.dawnsci.plotting.api.trace.ILineTrace;
import org.eclipse.dawnsci.plotting.api.trace.ILineTrace.PointStyle;
import org.eclipse.january.DatasetException;
import org.eclipse.january.dataset.Comparisons;
import org.eclipse.january.dataset.Comparisons.Monotonicity;
import org.eclipse.january.dataset.Dataset;
import org.eclipse.january.dataset.DatasetFactory;
import org.eclipse.january.dataset.DatasetUtils;
import org.eclipse.january.dataset.IDataset;
import org.eclipse.january.dataset.ILazyDataset;
import org.eclipse.january.dataset.IntegerDataset;
import org.eclipse.january.dataset.Maths;
import org.eclipse.january.dataset.Slice;
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
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.fitting.functions.DiffGaussian;
import uk.ac.diamond.scisoft.analysis.optimize.ApacheOptimizer;
import uk.ac.diamond.scisoft.analysis.optimize.ApacheOptimizer.Optimizer;
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

	private Map<String, Dataset> originalX = new HashMap<>();

	private Button resetButton;
	private IRectangularROI currentROI = null;

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
				plotSelected(false);
			}
		};
	
		fileController.addStateListener(fileStateListener);
		plottingSystem = plottingService.getPlottingSystem(PostRIXSPerspective.PLOT_NAME, true);
		plottingSystem.addRegionListener(new IRegionListener() {
			
			@Override
			public void regionsRemoved(RegionEvent evt) {
				clearOriginalX();
			}

			@Override
			public void regionRemoved(RegionEvent evt) {
				IRegion r = evt.getRegion();
				if (r != null && ALIGN_REGION.equals(r.getName())) {
					clearOriginalX();
				}
			}

			@Override
			public void regionNameChanged(RegionEvent evt, String oldName) {
			}
			
			@Override
			public void regionCreated(RegionEvent evt) {
			}
			
			@Override
			public void regionCancelled(RegionEvent evt) {
			}
			
			@Override
			public void regionAdded(RegionEvent evt) {
			}
		});

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

		// align
		Button b = new Button(plotComp, SWT.PUSH);
		b.setText("Align");
		b.setToolTipText("Align spectra using leftmost leading slope in selected region");
		b.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				selectRegion();
			}
		});
		b.setLayoutData(new GridData());

		resetButton = b = new Button(plotComp, SWT.PUSH);
		b.setText("Reset");
		b.setToolTipText("Use original spectra");
		b.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				plotOriginal();
			}
		});
		b.setLayoutData(new GridData());
		b.setEnabled(false);
	}

	private void clearOriginalX() {
		originalX.clear();
		updateResetButton();
	}

	private void updateResetButton() {
		if (resetButton.getEnabled() == originalX.isEmpty()) {
			Display.getDefault().asyncExec(() -> resetButton.setEnabled(!originalX.isEmpty()));
		}
	}

	private static final String ALIGN_REGION = "Align region";
	
	private void selectRegion() {
		IRegion r = plottingSystem.getRegion(ALIGN_REGION);
		if (r != null && r.getRegionType() != RegionType.XAXIS) {
			plottingSystem.renameRegion(r, "Not " + ALIGN_REGION);
			r = null;
		}
		if (r == null) {
			r = createRegion();
		} else {
			if (!r.isActive()) {
				r.setActive(true);
			}
			if (!r.isVisible()) {
				r.setVisible(true);
			}

			double[] xs = getLimits();
			if (xs != null) {
				if (!ensureRegionOK(r, xs)) {
					return;
				}

				alignPlots(xs[0], xs[1]);
			}
		}
	}

	private IRegion createRegion() {
		IRegion r = null;
		try {
			r = plottingSystem.createRegion(ALIGN_REGION, RegionType.XAXIS);
			r.addROIListener(new IROIListener() {

				@Override
				public void roiSelected(ROIEvent evt) {
				}

				@Override
				public void roiDragged(ROIEvent evt) {
				}

				@Override
				public void roiChanged(ROIEvent evt) {
					currentROI = (IRectangularROI) evt.getROI();
					double[] xs = getLimits();
					if (xs != null) {
						alignPlots(xs[0], xs[1]);
					}
				}
			});
		} catch (Exception e) {
			logger.error("Could not create alignment region", e);
		}
		return r;
	}

	private boolean ensureRegionOK(IRegion r, double[] xs) {
		IAxis axis = plottingSystem.getSelectedXAxis();
		double l = axis.getLower();
		double u = axis.getUpper();
		if (xs[0] > u || xs[1] < l) {
			currentROI.setPoint(0.5 * (l + u), currentROI.getPointY());
			r.setROI(currentROI);
			return false;
		}
		return true;
	}
	
	private double[] getLimits() {
		if (currentROI == null) {
			return null;
		}
		double lx = currentROI.getPointX();
		double dx = currentROI.getLength(0);
		double hx;
		if (dx < 0) {
			hx = lx;
			lx -= dx;
		} else if (dx > 0) {
			hx = lx + dx;
		} else {
			return null;
		}
		return new double[] {lx, hx};
	}

	private void alignPlots(double lx, double hx) {
		List<Double> shifts = new ArrayList<>();

		logger.debug("Region bounds are {}, {}", lx, hx);
		List<ILineTrace> traces = new ArrayList<>(plottingSystem.getTracesByClass(ILineTrace.class));

		for (ILineTrace t : traces) {
			String n = t.getName();
			Dataset x = originalX.get(n);
			if (x == null) {
				x = DatasetUtils.convertToDataset(t.getXData());
				originalX.put(n, x);
				updateResetButton();
			}
			Dataset y = DatasetUtils.convertToDataset(t.getYData());

			Monotonicity m = Comparisons.findMonotonicity(x);
			Slice cs = findCroppingSlice(m, x, y, lx, hx);
			if (cs == null) {
				logger.warn("Trace {} ignored as it is not strictly monotonic", t.getName());
				shifts.add(null);
				continue;
			}

			logger.debug("Cropping to {}", cs);
			Dataset cy = y.getSliceView(cs);

			// look for 1st peak in derivative
			Dataset dy = Maths.difference(cy, 1, 0);
			int pos = dy.argMax(true);
			
			// fit to derivative of Gaussian???
			double c = fitDiffGaussian(dy, cy.max(true).doubleValue()/5., pos);
			shifts.add(cs.getStart() + c);
		}

		logger.debug("Shifts are {}", shifts);

		int i = 0;
		int imax = traces.size();
		double firstShift = Double.NaN;
		for (; i < imax; i++) {
			Double s = shifts.get(i);
			if (s != null && Double.isFinite(s)) {
				firstShift = s;
				break;
			}
		}
		if (Double.isFinite(firstShift)) {
			ILineTrace t = traces.get(i);
			Dataset x = originalX.get(t.getName());
			firstShift = Maths.interpolate(x, firstShift);
			logger.debug("First shift is {}", firstShift);

			for (i++; i < imax; i++) {
				Double s = shifts.get(i);
				if (s != null && Double.isFinite(s)) {
					t = traces.get(i);
					x = originalX.get(t.getName());
					s = Maths.interpolate(x, s);
					double delta = s - firstShift;
					logger.debug("Shifting {} by {}", t.getName(), delta);
					Dataset nx = Maths.subtract(x, delta);

					t.setData(nx, t.getYData());
				}
			}
			plottingSystem.repaint(false);
		}
	}

	private static Slice findCroppingSlice(Monotonicity m, Dataset x, Dataset y, double lx, double hx) {
		int l = Math.min(x.getSize(), y.getSize());
		if (m == Monotonicity.STRICTLY_DECREASING) {
			Slice s = new Slice(l - 1, null, -1);
			x = x.getSliceView(s);
		} else if (m != Monotonicity.STRICTLY_INCREASING) {
			return null;
		}

		// slice plot according to x interval
		// it may happen that trace starts (or ends in chosen range)
		int li = 0;
		if (lx > x.getDouble(li)) {
			List<Double> c = DatasetUtils.crossings(x, lx);
			if (c.size() > 0) {
				li = (int) Math.ceil(c.get(0));
			}
		}

		int hi = l - 1;
		if (hx < x.getDouble(hi)) {
			List<Double> c = DatasetUtils.crossings(x, hx);
			if (c.size() > 0) {
				hi = (int) Math.floor(c.get(0));
			}
		}
		assert hi > li;

		return m == Monotonicity.STRICTLY_INCREASING ? new Slice(li, hi) : new Slice(l - 1 - hi, l - 1 - li);
	}

	private static DiffGaussian dg = new DiffGaussian();

	private static double fitDiffGaussian(Dataset dy, double peak, int pos) {
		double hpy = 0.5 * dy.getDouble(pos);
		boolean neg = hpy < 0;
		int max = dy.getSize();

		int hwhm;
		int beg, end;
		if (neg) {
			int pw = pos + 1;
			// find range to fit from peak to half-peak distance
			while (dy.getDouble(pw) < hpy && ++pw < max) {
			}
			if (pw >= max) {
				logger.warn("Could not find closest mid-height point");
				return Double.NaN;
			}
			hwhm = Math.max(2, pw - pos);
			// work out where zero crossing ends
			beg = pos - 1;
			while (dy.getDouble(beg) <= 0 && --beg >= 0) {
			}
			beg++;
			end = Math.min(max, pos + 4*hwhm);
		} else {
			int pw = pos - 1;
			while (dy.getDouble(pw) > hpy && --pw >= 0) {
			}
			if (pw < 0) {
				logger.warn("Could not find closest mid-height point");
				return Double.NaN;
			}
			hwhm = Math.max(2, pos - pw);
			beg = Math.max(0, pos - 4*hwhm);
			end = pos + 1;
			while (dy.getDouble(end) >= 0 && ++end < max) {
			}
		}
		Dataset cdy = dy.getSliceView(new Slice(beg, end));
		logger.info("Using hw of {} in {}:{}", hwhm, beg, end);
		logger.info(cdy.toString(true));
		Dataset cdx = DatasetFactory.createRange(beg, end, 1.0);

		IParameter p = dg.getParameter(0);
		double cen = neg ? beg : end;
		dg.setParameterValues(cen - 0.5, peak, 1e-1/hwhm);
		p.setLimits(cen - 1, cen);
		ApacheOptimizer opt = new ApacheOptimizer(Optimizer.SIMPLEX_NM);
		double residual = Double.POSITIVE_INFINITY;
		double[] errors = null;
		try {
			opt.optimize(new IDataset[] {cdx}, cdy, dg);
			residual = opt.calculateResidual();
			logger.info("Residual is {}", residual);
			errors = opt.guessParametersErrors();
		} catch (Exception e) {
			return Double.NaN;
		}

		return dg.getParameterValue(0);
	}

	private void plotOriginal() {
		for (ILineTrace t : plottingSystem.getTracesByClass(ILineTrace.class)) {
			Dataset x = originalX.get(t.getName());
			if (x != null) {
				t.setData(x, t.getYData());
			}
		}
		plottingSystem.repaint(false);
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
		clearOriginalX();

		String xName = null;
		for (String n : plots.keySet()) {
			Dataset r = plots.get(n);
			if (r.getSize() == 1) {
				logger.debug("Not plotting single value: {}", n);
				continue; // TODO SCI-7345 add single point plots...
			}
			Dataset[] axes = MetadataUtils.getAxesAndMakeMissing(r);
			Dataset x = axes.length > 0 ? axes[0] : null;

			if (x == null || x.peakToPeak(true).doubleValue() <= Double.MIN_NORMAL) {
				x = DatasetFactory.createRange(IntegerDataset.class, r.getSize());
			}
			if (xName == null) {
				xName = x.getName();
			}
			ILineTrace l = plottingSystem.createLineTrace(n);
			l.setData(x, r);
			if (r.getSize() == 1) {
				l.setPointStyle(PointStyle.XCROSS);
			}
			plottingSystem.addTrace(l);
		}
		plottingSystem.getSelectedXAxis().setTitle(xName);
		plottingSystem.repaint(reset);
		if (currentROI != null) {
			IRegion r = plottingSystem.getRegion(ALIGN_REGION);
			if (r == null) {
				r = createRegion();
				plottingSystem.addRegion(r);
			}
			if (r != null) {
				if (ensureRegionOK(r, getLimits()) && r.getROI() != currentROI) {
					r.setROI(currentROI);
				}
			}
		}
	}

	private Map<String, Dataset> createPlotData(List<LoadedFile> files) {
		Map<String, Dataset> plots = new LinkedHashMap<>();

		List<NameSelect> selection = currentSelection.stream()
				.filter(n -> n.isSelected()).collect(Collectors.toList());

		if (selection.size() > 0 && currentProcess != null) {
			List<String> selected;
			int b;
			if (RESULT.equals(currentProcess)) {
				b = -1;
				selected = new ArrayList<>();
				selected.add(RESULT_NAME);
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
							l.setName(fn == null ? suffix : fn + ":" + suffix);
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
				}
			} else {
				pd.add(DatasetUtils.sliceAndConvertLazyDataset(lz));
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
	static final String RESULT_NAME = "/entry/" + RESULT + DATA;

	private void updateGUI() {
		List<LoadedFile> files = fileController.getSelectedFiles();
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
		} else if (n.equals(RESULT_NAME)) {
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
