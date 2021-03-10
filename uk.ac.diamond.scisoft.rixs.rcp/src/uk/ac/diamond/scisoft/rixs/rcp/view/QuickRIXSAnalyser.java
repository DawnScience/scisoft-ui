/*-
 * Copyright (c) 2018 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.rixs.rcp.view;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.Serializable;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;

import org.dawnsci.datavis.model.DataOptions;
import org.dawnsci.datavis.model.FileControllerStateEvent;
import org.dawnsci.datavis.model.FileControllerStateEventListener;
import org.dawnsci.datavis.model.FileControllerUtils;
import org.dawnsci.datavis.model.LoadedFile;
import org.dawnsci.processing.ui.model.ModelViewer;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.JobGroup;
import org.eclipse.dawnsci.analysis.api.processing.OperationData;
import org.eclipse.dawnsci.analysis.api.processing.model.AbstractOperationModel;
import org.eclipse.dawnsci.analysis.api.processing.model.ModelField;
import org.eclipse.dawnsci.analysis.api.processing.model.OperationModelField;
import org.eclipse.dawnsci.analysis.api.roi.IROI;
import org.eclipse.dawnsci.analysis.dataset.roi.RectangularROI;
import org.eclipse.dawnsci.analysis.dataset.roi.XAxisBoxROI;
import org.eclipse.dawnsci.analysis.dataset.slicer.SliceFromSeriesMetadata;
import org.eclipse.dawnsci.analysis.dataset.slicer.SliceInformation;
import org.eclipse.dawnsci.analysis.dataset.slicer.SourceInformation;
import org.eclipse.dawnsci.plotting.api.IPlottingService;
import org.eclipse.dawnsci.plotting.api.IPlottingSystem;
import org.eclipse.dawnsci.plotting.api.axis.IAxis;
import org.eclipse.dawnsci.plotting.api.region.IROIListener;
import org.eclipse.dawnsci.plotting.api.region.IRegion;
import org.eclipse.dawnsci.plotting.api.region.IRegion.RegionType;
import org.eclipse.dawnsci.plotting.api.region.ROIEvent;
import org.eclipse.dawnsci.plotting.api.trace.ILineTrace;
import org.eclipse.dawnsci.plotting.api.trace.ILineTrace.PointStyle;
import org.eclipse.dawnsci.plotting.api.trace.MetadataPlotUtils;
import org.eclipse.january.DatasetException;
import org.eclipse.january.dataset.BroadcastIterator;
import org.eclipse.january.dataset.BroadcastPairIterator;
import org.eclipse.january.dataset.Dataset;
import org.eclipse.january.dataset.DatasetFactory;
import org.eclipse.january.dataset.DatasetUtils;
import org.eclipse.january.dataset.ILazyDataset;
import org.eclipse.january.dataset.IndexIterator;
import org.eclipse.january.dataset.IntegerDataset;
import org.eclipse.january.dataset.Maths;
import org.eclipse.january.dataset.ShapeUtils;
import org.eclipse.january.dataset.SliceND;
import org.eclipse.january.dataset.SliceNDIterator;
import org.eclipse.january.metadata.AxesMetadata;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ComboViewer;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.processing.operations.MetadataUtils;
import uk.ac.diamond.scisoft.analysis.processing.operations.backgroundsubtraction.SubtractFittedBackgroundModel;
import uk.ac.diamond.scisoft.analysis.processing.operations.backgroundsubtraction.SubtractFittedBackgroundOperation;
import uk.ac.diamond.scisoft.analysis.processing.operations.rixs.ElasticLineReduction;
import uk.ac.diamond.scisoft.analysis.processing.operations.rixs.ElasticLineReductionModel;
import uk.ac.diamond.scisoft.analysis.processing.operations.rixs.RixsBaseModel.ENERGY_DIRECTION;
import uk.ac.diamond.scisoft.analysis.processing.operations.utils.KnownDetector;
import uk.ac.diamond.scisoft.rixs.rcp.QuickRIXSPerspective;

/**
 * Part to configure reduction and plot result
 */
public class QuickRIXSAnalyser implements PropertyChangeListener {
	protected static final Logger logger = LoggerFactory.getLogger(QuickRIXSAnalyser.class);

	private static final int MAX_THREADS = 3; // limited to reduce memory usage

	@Inject IQRFileController fileController;

	private FileControllerStateEventListener fileStateListener;

	private IPlottingSystem<?> plottingSystem;

	private SubtractFittedBackgroundModel bgModel;

	private ElasticLineReductionModel elModel;
	private int roiMax = 1;

	private List<ProcessFileJob> jobs;
	private Map<String, ProcessFileJob> cachedJobs;

	private PlotOption poSpectrum;
	private PlotOption poSpectrumWithFit;
	private PlotOption poPosn;
	private PlotOption poFWHM;
	private PlotOption poHeight;
	private PlotOption poArea;
	private PlotOption poElasticLineSlope;
	private PlotOption poElasticLineIntercept;
	private Map<String, PlotOption> poAll;
	private PlotOption currentPlotOption;

	static class PlotOption {
		private final String oName;
		private final String dName;
		private String plotFormat;
		private double m = 1;
		private double c = 0;
		PlotOption(String optionName, String dataName, String plotFormat) {
			oName = optionName;
			dName = dataName;
			this.plotFormat = plotFormat;
		}

		public String getOptionName() {
			return oName;
		}

		public String getDataName(int r) {
			return String.format(dName, r);
		}

		public String getPlotFormat() {
			return plotFormat;
		}

		public double getXScale() {
			return m;
		}

		public void setXScale(double scale) {
			m = scale;
		}

		public double getXOffset() {
			return c;
		}

		public void setXOffset(double offset) {
			c = offset;
		}
	}

	private void createPlotOptions() {
		// these are summary data
		poAll = new LinkedHashMap<>();
		poSpectrum = new PlotOption("Spectrum", ElasticLineReduction.ES_PREFIX + "%d", "%s");
		poAll.put(poSpectrum.getOptionName(), poSpectrum);
		poSpectrumWithFit = new PlotOption("SpectrumWithFit", ElasticLineReduction.ESF_PREFIX + "%d", "%s-fit");
		poAll.put(poSpectrumWithFit.getOptionName(), poSpectrumWithFit);
		poPosn = new PlotOption("Posn", ElasticLineReduction.ESPOSN_PREFIX + "%d", "Posn");
		poAll.put(poPosn.getOptionName(), poPosn);
		poFWHM = new PlotOption("FWHM", ElasticLineReduction.ESFWHM_PREFIX + "%d", "FWHM");
		poAll.put(poFWHM.getOptionName(), poFWHM);
		poArea = new PlotOption("Area", ElasticLineReduction.ESAREA_PREFIX + "%d", "Area");
		poAll.put(poArea.getOptionName(), poArea);
		poHeight = new PlotOption("Height", ElasticLineReduction.ESHEIGHT_PREFIX + "%d", "Height");
		poAll.put(poHeight.getOptionName(), poHeight);

		poElasticLineSlope = new PlotOption("ElasticLineSlope", "line_%d_m", "elastic line slope");
		poAll.put(poElasticLineSlope.getOptionName(), poElasticLineSlope);
		poElasticLineIntercept = new PlotOption("ElasticLineIntercept", "line_%d_c", "elastic line intercept");
		poAll.put(poElasticLineIntercept.getOptionName(), poElasticLineIntercept);
	}

	class SubtractBGModel extends AbstractOperationModel {
		@OperationModelField(label = "Subtract background", hint = "Uncheck to leave background in image")
		private boolean subtractBackground = false;

		/**
		 * @return true if want to subtract background
		 */
		public boolean isSubtractBackground() {
			return subtractBackground;
		}

		public void setSubtractBackground(boolean subtractBackground) {
			firePropertyChange("setSubtractBackground", this.subtractBackground, this.subtractBackground = subtractBackground);
		}
	}

	private SubtractBGModel subtractModel;
	private ComboViewer plotCombo;

	private int maxThreads;

	private Text scaleText;

	private Text offsetText;

	private Button resetButton;

	public QuickRIXSAnalyser() {
		jobs = new ArrayList<>();
		subtractModel = new SubtractBGModel();

		maxThreads = Math.min(Math.max(1, Runtime.getRuntime().availableProcessors() - 1), MAX_THREADS);
		logger.debug("Number of threads: {}", maxThreads);
		cachedJobs = new HashMap<>();

		createPlotOptions();
	}

	@PostConstruct
	public void createComposite(Composite parent, IPlottingService plottingService) {
		fileStateListener = new FileControllerStateEventListener() {

			@Override
			public void stateChanged(FileControllerStateEvent event) {
				if (!event.isSelectedDataChanged() && !event.isSelectedFileChanged()) return;
				runProcessing(event.isSelectedDataChanged(), true);
			}

		};
	
		fileController.addStateListener(fileStateListener);
		plottingSystem = plottingService.getPlottingSystem(QuickRIXSPerspective.PLOT_NAME, true);

		subtractModel.addPropertyChangeListener(new PropertyChangeListener() {
			@Override
			public void propertyChange(PropertyChangeEvent evt) {
				runProcessing(true, false); // reset plot as y scale can be very different
			}
		});
		bgModel = new SubtractFittedBackgroundModel();
		bgModel.addPropertyChangeListener(this);
		elModel = new ElasticLineReductionModel();
		elModel.setRoiA(null);
		elModel.setRoiB(null);
		elModel.addPropertyChangeListener(this);

		// Create custom set of ModelFields from models
		parent.setLayout(new GridLayout());
		ModelViewer modelViewer = new ModelViewer();
		modelViewer.createPartControl(parent);
		modelViewer.setModelFields(
				new ModelField(subtractModel, "subtractBackground"),
				new ModelField(bgModel, "darkImageFile"),
				new ModelField(bgModel, "gaussianSmoothingLength"),
				new ModelField(bgModel, "ratio"),
				new ModelField(elModel, "slopeOverride"),
				new ModelField(elModel, "minPhotons"),
				new ModelField(elModel, "delta"),
				new ModelField(elModel, "cutoff"),
				new ModelField(elModel, "peakFittingFactor"),
				new ModelField(elModel, "roiA"),
				new ModelField(elModel, "roiB")
		);
		modelViewer.getControl().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));

		Composite plotComp = new Composite(parent, SWT.NONE);
		plotComp.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		plotComp.setLayout(new GridLayout(2, false));

		Label label;
		// spacer row
		label = new Label(plotComp, SWT.NONE);
		label = new Label(plotComp, SWT.NONE);

		label = new Label(plotComp, SWT.NONE);
		label.setText("Plot Options:");
		label.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
		plotCombo = new ComboViewer(plotComp, SWT.READ_ONLY | SWT.DROP_DOWN);
		plotCombo.addSelectionChangedListener(new ISelectionChangedListener() {
			
			@Override
			public void selectionChanged(SelectionChangedEvent event) {
				ISelection s = event.getSelection();
				if (s instanceof IStructuredSelection) {
					currentPlotOption = (PlotOption) ((IStructuredSelection) s).getFirstElement();
					scaleText.setText(Double.toString(currentPlotOption.getXScale()));
					offsetText.setText(Double.toString(currentPlotOption.getXOffset()));
					plotResults(true);
				}
			}
		});
		plotCombo.setContentProvider(ArrayContentProvider.getInstance());
		plotCombo.getControl().setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		plotCombo.setLabelProvider(new LabelProvider() {
			@Override
			public String getText(Object element) {
				if (element instanceof PlotOption) {
					return ((PlotOption) element).getOptionName();
				}
				return super.getText(element);
			}
		});

		// spacer row
		label = new Label(plotComp, SWT.NONE);
		label = new Label(plotComp, SWT.NONE);

		// x transformation
		label = new Label(plotComp, SWT.NONE);
		label.setText("X-axis");
		label.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));
		label = new Label(plotComp, SWT.NONE);

		label = new Label(plotComp, SWT.NONE);
		label.setText("Offset:");
		label.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
		offsetText = new Text(plotComp, SWT.LEAD);
		offsetText.setToolTipText("Value to add to x coordinates");
		offsetText.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
				if (currentPlotOption != null) {
					try {
						currentPlotOption.setXOffset(Double.parseDouble(offsetText.getText()));
						plotResults(true);
					} catch (NumberFormatException ex) {
						offsetText.setText(Double.toString(currentPlotOption.getXOffset()));
					}
				}
			}
		});

		label = new Label(plotComp, SWT.NONE);
		label.setText("Scale:");
		label.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
		scaleText = new Text(plotComp, SWT.LEAD);
		scaleText.setToolTipText("Multiplier of x coordinates (after adding offset)");
		scaleText.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
				if (currentPlotOption != null) {
					try {
						currentPlotOption.setXScale(Double.parseDouble(scaleText.getText()));
						plotResults(true);
					} catch (NumberFormatException ex) {
						scaleText.setText(Double.toString(currentPlotOption.getXScale()));
					}
				}
			}
		});

		// space filler row
		label = new Label(plotComp, SWT.NONE);
		label = new Label(plotComp, SWT.NONE);
		label.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, true));

		Button b = new Button(plotComp, SWT.PUSH);
		b.setText("Select range");
		b.setToolTipText("Click and drag to select region on plot\n");
		b.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				selectRegion(false);
				resetButton.setEnabled(true);
			}
		});

		resetButton = new Button(plotComp, SWT.PUSH);
		resetButton.setText("Reset range");
		resetButton.setToolTipText("Reset to original range");
		resetButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				selectRegion(true);
				resetButton.setEnabled(false);
			}
		});
		resetButton.setEnabled(false);
	}

	private static final String IMAGE_REGION = "Image region";
	private int[] rect = new int[2]; // start and length

	private void selectRegion(boolean remove) {
		IRegion r = plottingSystem.getRegion(IMAGE_REGION);
		if (remove) {
			if (r != null && r.getRegionType() == RegionType.XAXIS) {
				plottingSystem.removeRegion(r);
			}
			rect[0] = 0;
			rect[1] = 0;
			runProcessing(true, false);
			return;
		}
		if (r != null) {
			if (r.getRegionType() != RegionType.XAXIS) {
				plottingSystem.renameRegion(r, "Not " + IMAGE_REGION);
				r = null;
			}
		}
		if (r == null) {
			createRegion();
		}
	}

	private void removeRegion() {
		IRegion r = plottingSystem.getRegion(IMAGE_REGION);
		if (r != null && r.getRegionType() == RegionType.XAXIS) {
			plottingSystem.removeRegion(r);
		}
	}

	private void createRegion() {
		try {
			IRegion r = plottingSystem.createRegion(IMAGE_REGION, RegionType.XAXIS);
			r.addROIListener(new IROIListener() {

				@Override
				public void roiSelected(ROIEvent evt) {
				}

				@Override
				public void roiDragged(ROIEvent evt) {
				}

				@Override
				public void roiChanged(ROIEvent evt) {
					IROI roi = evt.getROI();
					if (roi instanceof XAxisBoxROI) {
						XAxisBoxROI ab = (XAxisBoxROI) roi;
						rect[0] = (int) Math.floor(ab.getPointX());
						rect[1] = (int) Math.ceil(ab.getLength(0));
						logger.debug("Start, length = {}", Arrays.toString(rect));
						runProcessing(true, false);
					}
				}
			});
		} catch (Exception e) {
			logger.error("Could not create alignment region", e);
		}
	}

	@PreDestroy
	public void preDestroy() {
		fileController.removeStateListener(fileStateListener);
	}

	@Override
	public void propertyChange(PropertyChangeEvent event) {
		roiMax = elModel.getRoiA() != null && elModel.getRoiB() != null ? 2 : 1;
		runProcessing(false, false);
	}

	private void runProcessing(boolean resetPlot, boolean useCachedResults) {
		List<LoadedFile> files = FileControllerUtils.getSelectedFiles(fileController);
		if (files.isEmpty()) {
			new Thread(() -> {
				synchronized (jobs) {
					jobs.clear();
					plottingSystem.clear();
				}
			}).start();
			return;
		}

		do {
			dispatchJobs(files, resetPlot, useCachedResults);
		} while (retry);

	}

	boolean retry = false;
	private void dispatchJobs(List<LoadedFile> files, final boolean resetPlot, final boolean useCachedResults) {
		final JobGroup jg = new JobGroup("QR jobs", maxThreads, files.size());
		synchronized (jobs) {
			jobs.clear();
			if (!useCachedResults) {
				cachedJobs.clear();
			}
			for (LoadedFile f : files) {
				String path = f.getFilePath();
				ProcessFileJob j = cachedJobs.get(path);
				if (j == null || j.getData() == null) {
					j = new ProcessFileJob("QR per file: " + f.getName(), f);
					j.setRegion(rect);
					j.setJobGroup(jg);
					j.setPriority(Job.LONG);
					j.schedule();
					cachedJobs.put(path, j);
				} else {
					j.setRegion(rect);
				}
				jobs.add(j);
			}
		}

		retry = false;
		Thread t = new Thread(() -> {
			synchronized (jobs) {
				try {
					while (!jg.getActiveJobs().isEmpty()) {
						// waiting for a time out and repeating avoids a race condition
						// where jobs finish before this join is executed
						jg.join(250, null);
					}
				} catch (OperationCanceledException | InterruptedException e) {
					logger.error("Problem running QuickRIXS jobs", e);
				}
				for (ProcessFileJob j : jobs) {
					IStatus s = j.getResult();
					if (s == null) {
						plottingSystem.autoscaleAxes();
						return;
					}
					if (s.getCode() == IStatus.WARNING || j.getData() == null) {
						retry = true;
						return;
					}
				}
			}
			populateCombo();
			plotResults(resetPlot);
		});
		t.start();
		if (retry) {
			Runtime.getRuntime().gc();
		}
	}

	private void populateCombo() {
		Set<String> options = new LinkedHashSet<>();
		synchronized (jobs) {
			for (ProcessFileJob j : jobs) {
				IStatus s = j.getResult();
				if (s != null && s.isOK()) {
					options.addAll(j.getData().keySet());
				}
			}
		}
		Set<PlotOption> ps = new LinkedHashSet<>();
		PlotOption po = null;
		for (PlotOption p : poAll.values()) {
			for (int r = 0; r < roiMax; r++) {
				String dn = p.getDataName(r);
				if (options.contains(dn)) {
					ps.add(p);
					if (po == null) {
						po = p;
					}
					options.remove(dn);
				}
			}
		}

		if (po == null) {
			return;
		}
		if (currentPlotOption == null) {
			currentPlotOption = po;
		}
		PlotOption fpo = po;
		Display.getDefault().asyncExec(() -> {
			scaleText.setText(Double.toString(fpo.getXScale()));
			offsetText.setText(Double.toString(fpo.getXOffset()));

			plotCombo.setInput(ps);
			Combo c = plotCombo.getCombo();
			c.select(c.indexOf(currentPlotOption.getOptionName()));
		});
	}

	private void plotResults(boolean reset) {
		removeRegion();
		synchronized (jobs) {
			if (jobs.isEmpty() || currentPlotOption == null) {
				plottingSystem.clear();
				return;
			}
		}
		Map<String, Dataset> plots = createPlotData();
		if (plots.isEmpty()) {
			plottingSystem.clear();
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
		double xo = currentPlotOption.getXOffset();
		double xs = currentPlotOption.getXScale();
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
			if (xo != 0 && xs != 1) {
				x = Maths.add(x, xo).imultiply(xs);
			}
			ILineTrace l = plottingSystem.createLineTrace(n);
			plottingSystem.addTrace(l);
			if (r.getSize() == 1) {
				l.setPointStyle(PointStyle.XCROSS);
			}
			l.setData(x, r);
		}
		plottingSystem.getSelectedXAxis().setTitle(xName);
		if (reset) {
			IAxis a = plottingSystem.getSelectedXAxis();
			if (rect[1] != 0 && (currentPlotOption == poSpectrum || currentPlotOption == poSpectrumWithFit)) {
				// set X axis when range selected set range so clipping is more obvious
				a.setRange(rect[0], rect[0] + rect[1]);
				a.setAutoscale(false);
			} else {
				a.setAutoscale(true);
			}
		}
		plottingSystem.repaint(reset);
	}

	private Map<String, Dataset> createPlotData() {
		Map<String, Dataset> plots = new LinkedHashMap<>();
		if (currentPlotOption == poElasticLineIntercept) {
			addPointData(plots);
		} else if (currentPlotOption == poElasticLineSlope) {
			addPointData(plots);
		} else if (currentPlotOption == poPosn) {
			addPointData(plots);
		} else if (currentPlotOption == poFWHM) {
			addPointData(plots);
		} else if (currentPlotOption == poArea) {
			addPointData(plots);
		} else if (currentPlotOption == poHeight) {
			addPointData(plots);
		} else if (currentPlotOption == poSpectrumWithFit) {
			addSpectrumData(plots, currentPlotOption);
			addSpectrumData(plots, poSpectrum);
		} else if (currentPlotOption == poSpectrum) {
			addSpectrumData(plots, currentPlotOption);
		}
		return plots;
	}

	private void addPointData(Map<String, Dataset> plots) {
		for (int r = 0; r < roiMax; r++) {
			addPointData(plots, r);
		}
	}

	private void addPointData(Map<String, Dataset> plots, int r) {
		List<Double> x = new ArrayList<>();
		List<Double> y = new ArrayList<>();
		String xName = null;
		
		synchronized (jobs) {
			for (ProcessFileJob j : jobs) {
				IStatus s = j.getResult();
				if (s == null || !s.isOK()) {
					continue;
				}
				LoadedFile f = j.getFile();
				Dataset v = f.getLabelValue();
				Map<String, Dataset> map = j.getData();
				Dataset pd = map == null ? null : map.get(currentPlotOption.getDataName(r));
	
				if (pd == null) {
					continue;
				}
				if (v == null) {
					Dataset[] axes = MetadataUtils.getAxesAndMakeMissing(pd);
					if (axes.length > 0) {
						v = axes[0];
					}
				}
				if (xName == null && v != null) {
					xName = v.getName();
				}
				logger.trace("Point label: {}", v);
				logger.trace("Point data: {}", pd);
	
				if (v == null) {
					IndexIterator it = pd.getIterator();
					while (it.hasNext()) {
						y.add(pd.getElementDoubleAbs(it.index));
					}
				} else {
					BroadcastIterator it = BroadcastPairIterator.createIterator(v, pd);
					it.setOutputDouble(true);
					while (it.hasNext()) {
						x.add(it.aDouble);
						y.add(it.bDouble);
					}
				}
			}
		}
		if (!y.isEmpty()) {
			Dataset yr = DatasetFactory.createFromList(y);
			if (!x.isEmpty()) {
				Dataset xd = DatasetFactory.createFromList(x);
				xd.setName(xName);
				MetadataUtils.setAxes(yr, xd);
			}
			plots.put(String.format(currentPlotOption.getPlotFormat() + "-%d", r), yr);
		}
	}

	private void addSpectrumData(Map<String, Dataset> plots, PlotOption option) {
		synchronized (jobs) {
			for (ProcessFileJob j : jobs) {
				IStatus s = j.getResult();
				if (s == null || !s.isOK()) {
					continue;
				}
				LoadedFile f = j.getFile();
				String n = String.format(option.getPlotFormat(), f.getName());
	
				for (int r = 0; r < roiMax; r++) {
					List<Dataset> pd = get1DPlotData(j.getData(), option.getDataName(r));
					Dataset v = f.getLabelValue();
					
					if (pd.size() > 1) {
						Dataset x = pd.remove(pd.size() - 1);
						if (v == null) {
							v = x;
						}
					}
					if (v != null) {
						v.squeeze();
						if (v.getRank() == 0) {
							v.setShape(1);
						}
					}
					int i = -1;
					for (Dataset yr : pd) {
						i++;
						if (yr == null) {
							continue;
						}
						yr.squeeze();
						String name;
						if (v == null) {
							name = String.format("%s-%d:%d", n, r, i);
						} else {
							name = String.format("%s-%d:%d (%s)", n, r, i, v.getObject(i));
						}
						plots.put(name, yr);
					}
				}
			}
		}
	}

	private List<Dataset> get1DPlotData(Map<String, Dataset> map, String name) {
		List<Dataset> pd = new ArrayList<>();
		if (map != null) {
			Dataset d = map.get(name);
			if (d != null) {
				int[] shape = d.getShapeRef();
				if (shape.length > 1) { // assumes only 1D scan
					int[] axes = new int[shape.length - 1];
					for (int i = 1; i < shape.length; i++) {
						axes[i-1] = i;
					}
					SliceNDIterator it = new SliceNDIterator(new SliceND(shape), axes);
					SliceND s = it.getCurrentSlice();
					while (it.hasNext()) {
						pd.add(d.getSliceView(s).squeeze());
					}
					Dataset[] ads = MetadataUtils.getAxes(d);
					pd.add(ads == null ? null : ads[0]);
				} else {
					pd.add(d);
				}
			}
		}
		
		return pd;
	}

	private class ProcessFileJob extends Job {
		private LoadedFile file;
		private List<Dataset> list;
		private SoftReference<Map<String, Dataset>> data;
		private int start;
		private int length;

		public ProcessFileJob(String name, LoadedFile file) {
			super(name);
			this.file = file;
			list = new ArrayList<>();
		}

		public void setRegion(int[] rect) {
			start = rect[0];
			length = rect[1];
		}

		public LoadedFile getFile() {
			return file;
		}

		/**
		 * @return may return null if pushed out by memory pressure
		 */
		public Map<String, Dataset> getData() {
			return data == null ? null : data.get();
		}

		@Override
		protected IStatus run(IProgressMonitor monitor) {
			file.setOnlySignals(true);
			List<DataOptions> opts = file.getDataOptions();

			if (opts.isEmpty()) { // try again with any dataset
				file.setOnlySignals(false);
				opts = file.getDataOptions();
				if (opts.isEmpty()) {
					return new Status(IStatus.WARNING, QuickRIXSPerspective.ID, "No data found");
				}
			}

			DataOptions opt = null;
			for (DataOptions o : opts) {
				if (o.getLazyDataset().getRank() >= 2) {
					opt = o;
					break;
				}
			}
			if (opt == null) {
				return new Status(IStatus.WARNING, QuickRIXSPerspective.ID, "Cannot find any data of rank >= 2");
			}

			// need per-file instances as operations are not thread-safe 
			SubtractFittedBackgroundOperation bop = null;
			if (subtractModel.isSubtractBackground()) {
				bop = new SubtractFittedBackgroundOperation();
				bop.setModel(bgModel);
			}
			ElasticLineReductionModel model = elModel.copy();
			ElasticLineReduction eop = new ElasticLineReduction();
			eop.setModel(model);
			eop.propertyChange(null); // trigger update from model

			ILazyDataset ld = opt.getLazyDataset();
			int[] shape = ld.getShape();
			int rank = shape.length;
			int[] dataDims = new int[] {rank - 2, rank - 1};

			RectangularROI ra = elModel.getRoiA() == null ? null : new RectangularROI(elModel.getRoiA());
			RectangularROI rb = elModel.getRoiB() == null ? null : new RectangularROI(elModel.getRoiB());
			if (roiMax == 1) {
				if (ra == null) {
					KnownDetector detector = KnownDetector.getDetector(file.getFilePath(), opt.getName(), ld);
					ra = KnownDetector.getDefaultROI(detector, shape, roiMax, 0, 10);
				}
			}
			model.setRoiA(ra);
			model.setRoiB(rb);

			if (length != 0) {
				boolean isFast = model.getEnergyDirection() == ENERGY_DIRECTION.FAST;
				if (ra != null) {
					cropROI(ra, isFast);
				}
				if (rb != null) {
					cropROI(rb, isFast);
				}
			}

			Dataset[] axes = new Dataset[rank - dataDims.length]; // assume axes are the same for all auxiliary data
			AxesMetadata amd = ld.getFirstMetadata(AxesMetadata.class);
			ILazyDataset[] lAxes = amd == null ? null : amd.getAxes();
			if (lAxes != null) {
				for (int r = 0; r < axes.length; r++) {
					ILazyDataset la = lAxes[r];
					if (la != null) {
						try {
							Dataset ad = DatasetUtils.sliceAndConvertLazyDataset(la).squeeze();
							ad.setName(MetadataPlotUtils.removeSquareBrackets(ad.getName()));
							axes[r] = ad;
						} catch (DatasetException e) {
						}
					}
				}
			}

			int total = ShapeUtils.calcSize(Arrays.copyOf(shape, rank - 2));
			SliceNDIterator iter = new SliceNDIterator(new SliceND(shape), dataDims);
			SliceND slice = iter.getCurrentSlice();
			SourceInformation sri = new SourceInformation(file.getFilePath(), opt.getName(), ld);
			SubMonitor sub = SubMonitor.convert(monitor, total);
			sub.setTaskName("Processing images in " + file.getName());
			int i = 0;
			while (iter.hasNext()) {
				sub.newChild(1);
				SliceInformation si = new SliceInformation(slice, slice, slice, dataDims, total, i++);
				try {
					Dataset image = DatasetUtils.convertToDataset(ld.getSlice(slice)).squeezeEnds();
					image.addMetadata(new SliceFromSeriesMetadata(sri, si));
					processImage(bop, eop, image, si, axes);
				} catch (DatasetException e) {
					break;
				} catch (OutOfMemoryError e) {
					data = null;
					return new Status(IStatus.WARNING, QuickRIXSPerspective.ID, "Out of memory");
				}
			}

			return Status.OK_STATUS;
		}

		private void cropROI(RectangularROI roi, boolean isFast) {
			if (isFast) {
				roi.setPoint(start, roi.getPointY());
				roi.setLengths(length, roi.getLength(1));
			} else {
				roi.setPoint(roi.getPointX(), start);
				roi.setLengths(roi.getLength(0), length);
			}
		}

		private void processImage(SubtractFittedBackgroundOperation bop, ElasticLineReduction eop, Dataset image, SliceInformation si, Dataset[] axes) {
			OperationData od = bop == null ? null : bop.process(image, null);
			Dataset i;
			if (od == null) {
				i = image.getView(true).squeeze();
			} else {
				i = DatasetUtils.convertToDataset(od.getData()).squeeze();
				addToList(list, od.getAuxData());

				if (si.isLastSlice()) {
					addToMap(od.getSummaryData());
				}
			}

			od = eop.execute(i, null);
			addToList(list, od.getAuxData());

			if (si.isLastSlice()) {
				if (od != null) {
					addToMap(od.getSummaryData());
				}

				combineListToMap(list, si.getTotalSlices(), axes);
			}
		}

		private Map<String, Dataset> getMap() {
			Map<String, Dataset> map;
			if (data == null) {
				map = new LinkedHashMap<>();
				data = new SoftReference<>(map);
			}
			map = data.get();
			return map;
		}

		private void addToList(List<Dataset> list, Serializable... array) {
			if (array == null) {
				return;
			}
			for (Serializable s : array) {
				if (s instanceof Dataset) {
					Dataset d = (Dataset) s;
					if (d.getRank() == 0) {
						list.add(d.reshape(1));
					} else {
						list.add(d);
					}
				}
			}
		}

		private void addToMap(Serializable... array) {
			if (array == null) {
				return;
			}
			Map<String, Dataset> map = getMap();
			for (Serializable s : array) {
				if (s instanceof Dataset) {
					Dataset d = (Dataset) s;
					map.put(d.getName(), d);
				}
			}
		}

		// combine datasets with common names in list and add to map
		private void combineListToMap(List<Dataset> list, int slices, Dataset[] axes) {
			Map<String, Dataset> map = getMap();
			Dataset[] ds = new Dataset[slices];

			for (int j = 0, jmax = list.size(); j < jmax; j++) {
				Arrays.fill(ds, null);
				Dataset d = list.get(j);
				if (d == null) {
					continue;
				}
				String dName = d.getName();
				int s = 0;
				ds[s++] = d;
				for (int k = j + 1; k < jmax && s < slices; k++) {
					Dataset e = list.get(k);
					if (e != null && dName.equals(e.getName())) {
						ds[s++] = e;
						list.set(k, null);
					}
				}
				if (s != slices) {
					logger.error("Error: missing data from processing job");
					continue; // skip when there are not specified number of datasets
				}
				d = DatasetUtils.concatenate(ds, 0);
				d.setName(dName);
				if (axes != null) {
					MetadataUtils.setAxes(d, axes);
				}
				map.put(dName, d);
			}
		}
	}
}
