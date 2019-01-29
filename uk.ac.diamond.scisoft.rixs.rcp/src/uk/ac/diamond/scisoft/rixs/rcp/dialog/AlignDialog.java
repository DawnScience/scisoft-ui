package uk.ac.diamond.scisoft.rixs.rcp.dialog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.dawnsci.analysis.api.roi.IRectangularROI;
import org.eclipse.dawnsci.plotting.api.IPlottingSystem;
import org.eclipse.dawnsci.plotting.api.axis.IAxis;
import org.eclipse.dawnsci.plotting.api.region.IROIListener;
import org.eclipse.dawnsci.plotting.api.region.IRegion;
import org.eclipse.dawnsci.plotting.api.region.IRegion.RegionType;
import org.eclipse.dawnsci.plotting.api.region.IRegionListener;
import org.eclipse.dawnsci.plotting.api.region.ROIEvent;
import org.eclipse.dawnsci.plotting.api.region.RegionEvent;
import org.eclipse.dawnsci.plotting.api.trace.ILineTrace;
import org.eclipse.dawnsci.plotting.api.trace.ITrace;
import org.eclipse.january.dataset.Dataset;
import org.eclipse.january.dataset.DatasetFactory;
import org.eclipse.january.dataset.DatasetUtils;
import org.eclipse.january.dataset.IDataset;
import org.eclipse.january.dataset.Maths;
import org.eclipse.january.dataset.Slice;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ColumnViewerEditor;
import org.eclipse.jface.viewers.ColumnViewerEditorActivationEvent;
import org.eclipse.jface.viewers.ColumnViewerEditorActivationStrategy;
import org.eclipse.jface.viewers.EditingSupport;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.TableViewerEditor;
import org.eclipse.jface.viewers.TextCellEditor;
import org.eclipse.jface.viewers.ViewerCell;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.dataset.function.AlignToHalfGaussianPeak;

public class AlignDialog extends Dialog implements IRegionListener {

	protected static final Logger logger = LoggerFactory.getLogger(AlignDialog.class);

	private IPlottingSystem<?> plottingSystem;

	private Map<String, PlotItem> plotItems = new LinkedHashMap<>();

	private Button resetButton;
	private IRectangularROI currentROI = null;
	private boolean forceToZero;
	private boolean resampleX;
	private boolean plotAverage;
	private AlignToHalfGaussianPeak align = new AlignToHalfGaussianPeak(false);

	private Button plotAverageButton;

	private TableViewer resultTable;

	public AlignDialog(Shell parentShell, IPlottingSystem<?> plottingSystem) {
		super(parentShell);

		setShellStyle(SWT.MODELESS | SWT.CLOSE | SWT.BORDER | SWT.TITLE | SWT.RESIZE);
		setBlockOnOpen(false);

		this.plottingSystem = plottingSystem;
		plottingSystem.addRegionListener(this);
	}

	@Override
	public void regionsRemoved(RegionEvent evt) {
		resetPlotItems();
	}

	@Override
	public void regionRemoved(RegionEvent evt) {
		IRegion r = evt.getRegion();
		if (r != null && ALIGN_REGION.equals(r.getName())) {
			resetPlotItems();
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

	@Override
	protected Control createDialogArea(Composite parent) {
		Composite comp = (Composite) super.createDialogArea(parent); // implementation returns a composite with grid layout

		Composite alignComp = new Composite(comp, SWT.NONE);
		alignComp.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		alignComp.setLayout(new RowLayout());

		Button b = new Button(alignComp, SWT.PUSH);
		b.setText("Align");
		b.setToolTipText("Click and drag to select region on plot.\n"
				+ "Align spectra using leftmost leading slope in selected region.\n"
				+ "It aligns to first line or to zero if the selected region encloses zero.");
		b.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				selectRegion(false);
			}
		});

		resetButton = b = new Button(alignComp, SWT.PUSH);
		b.setText("Reset");
		b.setToolTipText("Use original spectra");
		b.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				plotOriginal();
			}
		});
		b.setEnabled(false);

		b = new Button(alignComp, SWT.CHECK);
		b.setText("Force to zero");
		b.setToolTipText("Make align to zero unconditionally; reselect region");
		b.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				plotOriginal();
				Button button = (Button) e.getSource();
				forceToZero = button.getSelection();
				selectRegion(true);
			}
		});

		b = new Button(alignComp, SWT.CHECK);
		b.setText("Resample");
		b.setToolTipText("Interpolate to common x points");
		b.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				Button button = (Button) e.getSource();
				resampleX = button.getSelection();
				plotAverageButton.setEnabled(resampleX);
				if (!resampleX) {
					plotAverage = false;
					plotAverageButton.setSelection(plotAverage);
				}
				selectRegion(false);
			}
		});

		plotAverageButton = new Button(alignComp, SWT.CHECK);
		plotAverageButton.setText("Show average");
		plotAverageButton.setToolTipText("Average data and plot it");
		plotAverageButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				Button button = (Button) e.getSource();
				plotAverage = button.getSelection();
				selectRegion(false);
			}
		});
		plotAverageButton.setEnabled(resampleX);

		Composite resultComp = new Composite(comp, SWT.NONE);
		resultComp.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		resultComp.setLayout(new FillLayout());

		resultTable = new TableViewer(resultComp, SWT.NONE);
		resultTable.setContentProvider(new IStructuredContentProvider() {
			
			@Override
			public Object[] getElements(Object inputElement) {
				@SuppressWarnings("unchecked")
				Map<String, PlotItem> input = (Map<String, PlotItem>) inputElement;
				return input.values().toArray(new PlotItem[input.size()]);
			}
		});

		resultTable.getTable().setHeaderVisible(true);

		TableViewerColumn name = new TableViewerColumn(resultTable, SWT.LEFT);
		name.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				return ((PlotItem) element).getName();
			}
		});
		TableColumn column = name.getColumn();
		column.setText("Dataset Name");
		column.setWidth(250);

		TableViewerColumn auto = new TableViewerColumn(resultTable, SWT.LEFT);
		auto.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				return Double.toString(((PlotItem) element).getAuto());
			}
		});
		column = auto.getColumn();
		column.setText("Auto-align");
		column.setToolTipText("Values found by auto-aligner");
		column.setWidth(80);

		TableViewerColumn manual = new TableViewerColumn(resultTable, SWT.LEFT);
		manual.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				return String.valueOf(((PlotItem) element).getManual());
			}
		});
		column = manual.getColumn();
		column.setText("Manual");
		column.setToolTipText("Edit to manually adjust plot after auto-align");
		column.setWidth(50);
		manual.setEditingSupport(new ManualAdjustEditingSupport(resultTable));

		// add tab navigation
		ColumnViewerEditorActivationStrategy actStrategy = new ColumnViewerEditorActivationStrategy(resultTable) {
			@Override
			protected boolean isEditorActivationEvent(ColumnViewerEditorActivationEvent event) {
				return ((ViewerCell) event.getSource()).getColumnIndex() == 2;
			}
		};
		TableViewerEditor.create(resultTable, actStrategy, ColumnViewerEditor.TABBING_HORIZONTAL 
			| ColumnViewerEditor.TABBING_VERTICAL | ColumnViewerEditor.TABBING_MOVE_TO_ROW_NEIGHBOR);

		resultTable.setInput(plotItems);
		updatePlotItems();
		resultTable.refresh();
		resultTable.getControl().pack(); // update to display as many rows in table as possible
		updateResetButton();

		return comp;
	}

	@Override
	public int open() { // can do this as dialog is non-modal
		int code = super.open();
		getButton(IDialogConstants.CANCEL_ID).setToolTipText("Reset plots");
		getButton(IDialogConstants.OK_ID).setToolTipText("Keep alignment");
		return code;
	}

	private void updatePlotItems() {
		List<ILineTrace> traces = new ArrayList<>(plottingSystem.getTracesByClass(ILineTrace.class));
		plotItems.clear();
		for (ILineTrace t : traces) {
			String n = t.getName();
			if (RESAMPLE_AVERAGE.equals(n)) {
				continue;
			}
			PlotItem pi = new PlotItem(n);
			plotItems.put(n, pi);
			pi.setX(DatasetUtils.convertToDataset(t.getXData()));
			pi.setY(DatasetUtils.convertToDataset(t.getYData()));
		}
	}

	/**
	 * Clear old state
	 */
	public void resetPlotItems() {
		for (PlotItem pi : plotItems.values()) {
			pi.setAuto(0);
			pi.setManual(0);
		}
		if (!resultTable.getControl().isDisposed()) {
			resultTable.refresh();
			updateResetButton();
			setRegionVisible(false);
		}
	}

	private void updateResetButton() {
		if (resetButton.getEnabled() == plotItems.isEmpty()) {
			Display.getDefault().asyncExec(() -> resetButton.setEnabled(!plotItems.isEmpty()));
		}
	}

	private static final String ALIGN_REGION = "Align region";

	private void selectRegion(boolean removeOld) {
		IRegion r = plottingSystem.getRegion(ALIGN_REGION);
		if (r != null) {
			if (r.getRegionType() != RegionType.XAXIS) {
				plottingSystem.renameRegion(r, "Not " + ALIGN_REGION);
				r = null;
			} else if (removeOld) {
				plottingSystem.removeRegion(r);
				r = null;
			}
		}
		if (r == null) {
			r = createRegion();
		} else {
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

	/**
	 * Set selection region's visibility
	 * @param visible
	 */
	public void setRegionVisible(boolean visible) {
		IRegion r = plottingSystem.getRegion(ALIGN_REGION);
		if (r != null) {
			r.setVisible(visible);
		}
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

	private static final String RESAMPLE_AVERAGE = "Average";

	private void alignPlots(double lx, double hx) {
		logger.debug("Region bounds are {}, {}", lx, hx);
		removeAveragePlot();
		List<ILineTrace> traces = new ArrayList<>(plottingSystem.getTracesByClass(ILineTrace.class));

		IDataset[] input = new IDataset[2 * traces.size()];
		int i = 0;
		for (PlotItem pi : plotItems.values()) { // gather inputs to shifter
			input[i++] = pi.getX();
			input[i++] = pi.getY();
		}

		align.setPeakZone(lx, hx);
		List<Double> posn = align.value(input);
		List<Double> shifts = AlignToHalfGaussianPeak.calculateShifts(resampleX, forceToZero || (lx <= 0 && hx >= 0), 0, posn, input);

		i = 0;
		for (PlotItem pi : plotItems.values()) {
			double delta = pi.getManual();
			Dataset x = pi.getX();
			Double s = shifts.get(i);
			if (s == null) {
				s = shifts.get(i + 1);
				if (delta != 0) { // translate to index-space
					double xd = x.getDouble(1) - x.getDouble(0);
					delta /= xd; // TODO remove when shifts are all in x-space
					shifts.set(i + 1, s - delta);
					s *= xd; // so displays are in x-space
				}
			} else {
				if (delta != 0) {
					shifts.set(i, s + delta);
				}
			}
			pi.setAuto(s);
			i += 2;
		}
		List<Dataset> data = AlignToHalfGaussianPeak.shiftData(shifts, input);

		i = 0;
		int minSize = Integer.MAX_VALUE;
		for (ILineTrace t : traces) {
			String n = t.getName();
			if (RESAMPLE_AVERAGE.equals(n)) {
				continue;
			}
			Dataset x = data.get(i);
			Dataset d = data.get(i + 1);
			PlotItem pi = plotItems.get(n);
			Dataset ox = pi.getX();
			if (resampleX || x != ox) {
				x.setName(ox.getName());
				minSize = Math.min(minSize, x.getSize());
			}
			t.setData(x, d);
			i += 2;
		}

		if (plotAverage) {
			plotAverage(data, minSize);
		}

		plottingSystem.repaint(false);

		resultTable.refresh();

		new Thread(new Runnable() {
			
			@Override
			public void run() {
				try {
					Thread.sleep(1500);
				} catch (InterruptedException e) {
				}
				Display.getDefault().syncExec(new Runnable() {
					@Override
					public void run() {
						setRegionVisible(false);
					}
				});
			}
		}).start();
	}

	private void plotAverage(List<? extends IDataset> data, int minSize) {
		Slice s = new Slice(minSize);
		Dataset sum = DatasetFactory.zeros(DatasetUtils.convertToDataset(data.get(1)).getClass(), minSize);
		int max = data.size();
		for (int i = 0; i < max; i += 2) {
			Dataset d = DatasetUtils.convertToDataset(data.get(i + 1));
			sum.iadd(d.getSliceView(s));
		}
		sum.idivide(max/2);
		ILineTrace t = plottingSystem.createLineTrace(RESAMPLE_AVERAGE);
		IDataset ox = data.get(0);
		Dataset x = DatasetUtils.convertToDataset(ox).getSliceView(s);
		x.setName(ox.getName());
		t.setData(x, sum);
		plottingSystem.addTrace(t);
	}

	private void updateTrace(double delta, PlotItem pi) {
		ITrace t = plottingSystem.getTrace(pi.getName());
		if (t instanceof ILineTrace) {
			ILineTrace lt = (ILineTrace) t;
			Dataset nx;
			IDataset ny;
			if (resampleX) {
				
				nx = Maths.add(pi.getX(), delta);
				ny = Maths.interpolate(nx, pi.getY(), pi.getX(), null, null);
				nx = pi.getX();
			} else {
				delta += pi.getAuto();
				nx = Maths.add(pi.getX(), delta);
				ny = lt.getYData();
			}
			lt.setData(nx, ny);
			lt.repaint();

			if (plotAverage) {
				removeAveragePlot();

				List<ILineTrace> traces = new ArrayList<>(plottingSystem.getTracesByClass(ILineTrace.class));
				List<IDataset> data = new ArrayList<>();
				int minSize = Integer.MAX_VALUE;
				for (ILineTrace nt : traces) {
					if (RESAMPLE_AVERAGE.equals(nt.getName())) {
						continue;
					}
					IDataset x = nt.getXData();
					minSize = Math.min(minSize, x.getSize());
					data.add(x);
					data.add(nt.getYData());
				}

				plotAverage(data, minSize);
			}
		}
	}

	private void plotOriginal() {
		removeAveragePlot();
		for (ILineTrace t : plottingSystem.getTracesByClass(ILineTrace.class)) {
			PlotItem pi = plotItems.get(t.getName());
			Dataset x = pi.getX();
			Dataset y = pi.getY();
			if (x != null) {
				t.setData(x, y);
			}
			pi.setAuto(0);
			pi.setManual(0);
		}
		setRegionVisible(true);
		resultTable.refresh();
		plottingSystem.repaint(false);
	}

	private void removeAveragePlot() {
		ITrace at = plottingSystem.getTrace(RESAMPLE_AVERAGE);
		if (at != null) {
			plottingSystem.removeTrace(at);
		}
	}

	@Override
	protected void configureShell(Shell newShell) {
		super.configureShell(newShell);
		newShell.setText("Plot aligner");
	}

	@Override
	public boolean close() {
		plottingSystem.removeRegionListener(this);
		if (getReturnCode() == Window.CANCEL) {
			plotOriginal();
			removeRegion();
		}
		setRegionVisible(false);
		return super.close();
	}

	private void removeRegion() {
		IRegion r = plottingSystem.getRegion(ALIGN_REGION);
		if (r != null) {
			plottingSystem.removeRegion(r);
		}
	}

	/**
	 * Refresh region
	 */
	public void refreshRegion() {
		if (currentROI != null) {
			IRegion r = plottingSystem.getRegion(ALIGN_REGION);
			if (r == null) {
				r = createRegion();
				plottingSystem.addRegion(r);
			}
			if (r != null) {
				if (!r.isVisible()) {
					r.setVisible(true);
				}
				if (ensureRegionOK(r, getLimits()) && r.getROI() != currentROI) {
					r.setROI(currentROI);
				}
			}
		}
	}

	private static class PlotItem {
		private String name;
		private double auto;
		private double manual;
		private Dataset x, y;

		public PlotItem(String name) {
			this.name = name;
		}

		public String getName() {
			return name;
		}

		public Dataset getX() {
			return x;
		}

		public void setX(Dataset x) {
			this.x = x;
		}

		public Dataset getY() {
			return y;
		}

		public void setY(Dataset y) {
			this.y = y;
		}

		public double getAuto() {
			return auto;
		}

		public void setAuto(double auto) {
			this.auto = auto;
		}

		public double getManual() {
			return manual;
		}

		public void setManual(double manual) {
			this.manual = manual;
		}
	}

	private class ManualAdjustEditingSupport extends EditingSupport {
		private Table table;

		public ManualAdjustEditingSupport(TableViewer viewer) {
			super(viewer);
			table = viewer.getTable();
		}

		@Override
		protected CellEditor getCellEditor(Object element) {
			return new TextCellEditor(table);
		}

		@Override
		protected boolean canEdit(Object element) {
			return true;
		}

		@Override
		protected Object getValue(Object element) {
			if (element instanceof PlotItem) {
				return String.valueOf(((PlotItem) element).getManual());
			}
			return null;
		}

		@Override
		protected void setValue(Object element, Object value) {
			try {
				double delta = Double.parseDouble((String) value);
				PlotItem pi = ((PlotItem) element);
				pi.setManual(delta);
				getViewer().update(element, null);

				updateTrace(delta, pi);
			} catch (Exception e) {
				// do nothing
			}
		}
	}
}
