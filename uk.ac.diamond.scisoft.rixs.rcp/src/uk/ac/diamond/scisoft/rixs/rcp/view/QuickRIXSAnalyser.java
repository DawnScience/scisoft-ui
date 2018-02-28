/*
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
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

import org.dawnsci.datavis.model.DataOptions;
import org.dawnsci.datavis.model.FileControllerStateEvent;
import org.dawnsci.datavis.model.FileControllerStateEventListener;
import org.dawnsci.datavis.model.IFileController;
import org.dawnsci.datavis.model.LoadedFile;
import org.dawnsci.processing.ui.model.ModelViewer;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.JobGroup;
import org.eclipse.dawnsci.analysis.api.processing.IOperationService;
import org.eclipse.dawnsci.analysis.api.processing.OperationData;
import org.eclipse.dawnsci.analysis.api.processing.model.AbstractOperationModel;
import org.eclipse.dawnsci.analysis.api.processing.model.ModelField;
import org.eclipse.dawnsci.analysis.api.processing.model.OperationModelField;
import org.eclipse.dawnsci.analysis.dataset.slicer.SliceFromSeriesMetadata;
import org.eclipse.dawnsci.analysis.dataset.slicer.SliceInformation;
import org.eclipse.dawnsci.analysis.dataset.slicer.SourceInformation;
import org.eclipse.dawnsci.plotting.api.IPlottingService;
import org.eclipse.dawnsci.plotting.api.IPlottingSystem;
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
import org.eclipse.january.dataset.ShapeUtils;
import org.eclipse.january.dataset.SliceND;
import org.eclipse.january.dataset.SliceNDIterator;
import org.eclipse.january.metadata.AxesMetadata;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;

import uk.ac.diamond.scisoft.analysis.processing.operations.MetadataUtils;
import uk.ac.diamond.scisoft.analysis.processing.operations.backgroundsubtraction.SubtractFittedBackgroundModel;
import uk.ac.diamond.scisoft.analysis.processing.operations.backgroundsubtraction.SubtractFittedBackgroundOperation;
import uk.ac.diamond.scisoft.analysis.processing.operations.rixs.ElasticLineReduction;
import uk.ac.diamond.scisoft.analysis.processing.operations.rixs.ElasticLineReductionModel;
import uk.ac.diamond.scisoft.analysis.processing.operations.rixs.RixsBaseOperation;
import uk.ac.diamond.scisoft.rixs.rcp.QuickRIXSPerspective;

/**
 * Part to configure reduction and plot result
 */
public class QuickRIXSAnalyser implements PropertyChangeListener {

	private static final int MAX_THREADS = 3; // limited to reduce memory usage

	@Inject IPlottingService plottingService;

	@Inject IFileController fileController;

	private FileControllerStateEventListener fileStateListener;

	private IPlottingSystem<?> plottingSystem;

	@Inject IOperationService opService;

	private SubtractFittedBackgroundOperation bgOp;
	private SubtractFittedBackgroundModel bgModel;

	private ElasticLineReduction elOp;
	private ElasticLineReductionModel elModel;

	private List<ProcessFileJob> jobs;
	private Map<String, ProcessFileJob> cachedJobs;

	private static final String ZERO = "0";
	private enum PlotOption {
		Spectrum(ElasticLineReduction.ES_PREFIX + ZERO, "%s"),
		SpectrumWithFit(ElasticLineReduction.ESF_PREFIX + ZERO, "%s-fit"),
		FWHM(ElasticLineReduction.ESFWHM_PREFIX + ZERO, "FWHM"),
		Slope("line_0_m", "slope"),
		Intercept("line_0_c", "intercept");

		private final String dName;
		private String plotFormat;
		PlotOption(String dataName, String plotFormat) {
			dName = dataName;
			this.plotFormat = plotFormat;
		}

		public String getDataName() {
			return dName;
		}

		public String getPlotFormat() {
			return plotFormat;
		}
	}

	class SubtractBGModel extends AbstractOperationModel {
		@OperationModelField(label = "Subtract background", hint = "Uncheck to leave background in image")
		private boolean subtractBackground = true;

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

	class SlopeOverrideModel extends AbstractOperationModel {
		@OperationModelField(label = "Slope override", hint = "Any non-zero value is used to manually set slope")
		private double slopeOverride = 0;
	
		/**
		 * @return slope of elastic line. Non-zero values are used to override values from elastic fit files
		 */
		public double getSlopeOverride() {
			return slopeOverride;
		}

		public void setSlopeOverride(double slopeOverride) {
			firePropertyChange("setSlopeOverride", this.slopeOverride, this.slopeOverride = slopeOverride);
		}
	}

	class PlotModel extends AbstractOperationModel {
		@OperationModelField(label = "Plot option", hint = "What to plot: Spectrum, Spectrum w/ fit, FWHM, Slope, Intercept")
		private PlotOption plotOption = PlotOption.Spectrum;

		/**
		 * @return what to plot
		 */
		public PlotOption getPlotOption() {
			return plotOption;
		}

		public void setPlotOption(PlotOption plotOption) {
			firePropertyChange("setPlotOption", this.plotOption, this.plotOption = plotOption);
		}

		void internalSetPlotOption(PlotOption plotOption) {
			this.plotOption = plotOption;
		}
	}

	private SubtractBGModel subtractModel;
	private SlopeOverrideModel slopeModel;
	private PlotModel plotModel;

	private int maxThreads;

	public QuickRIXSAnalyser() {
		jobs = new ArrayList<>();
		subtractModel = new SubtractBGModel();
		slopeModel = new SlopeOverrideModel();
		plotModel = new PlotModel();

		maxThreads = Math.min(Math.max(1, Runtime.getRuntime().availableProcessors() - 1), MAX_THREADS);
		System.err.println("Number of threads: " + maxThreads);
		cachedJobs = new HashMap<>();
	}

	@PostConstruct
	public void createComposite(Composite parent) {
		fileStateListener  = new FileControllerStateEventListener() {

			@Override
			public void stateChanged(FileControllerStateEvent event) {
				if (!QuickRIXSPerspective.LOADED_FILE_ID.equals(fileController.getID())) {
					return; // ignore other sources of state changes
				}
				if (!event.isSelectedDataChanged() && !event.isSelectedFileChanged()) return;
				runProcessing(event.isSelectedDataChanged(), true);
			}

		};
	
		fileController.addStateListener(fileStateListener);
		plottingSystem = plottingService.getPlottingSystem(QuickRIXSPerspective.PLOT_NAME, true);

		try {
			subtractModel.addPropertyChangeListener(this);
			slopeModel.addPropertyChangeListener(this);
			bgOp = (SubtractFittedBackgroundOperation) opService.create("uk.ac.diamond.scisoft.analysis.processing.operations.backgroundsubtraction.SubtractFittedBackgroundOperation");
			bgModel = bgOp.getModel();
			bgModel.addPropertyChangeListener(this);
			elOp = (ElasticLineReduction) opService.create("uk.ac.diamond.scisoft.analysis.processing.operations.rixs.ElasticLineReduction");
			elModel = elOp.getModel();
			elModel.setRoiA(null);
			elModel.setRoiB(null);
			elModel.addPropertyChangeListener(this);
		} catch (Exception e) {
			e.printStackTrace();
		}

		plotModel.addPropertyChangeListener(new PropertyChangeListener() {
			
			@Override
			public void propertyChange(PropertyChangeEvent evt) {
				plotResults(true);
			}
		});

		// Create custom set of ModelFields from models
		parent.setLayout(new FillLayout());
		ModelViewer modelViewer = new ModelViewer();
		modelViewer.createPartControl(parent);
		modelViewer.setModelFields(
				new ModelField(subtractModel, "subtractBackground"),
				new ModelField(bgModel, "darkImageFile"),
				new ModelField(bgModel, "gaussianSmoothingLength"),
				new ModelField(bgModel, "ratio"),
				new ModelField(slopeModel, "slopeOverride"),
				new ModelField(elModel, "minPhotons"),
				new ModelField(elModel, "delta"),
				new ModelField(elModel, "cutoff"),
				new ModelField(elModel, "peakFittingFactor"),
				new ModelField(plotModel, "plotOption")
		);
	}

	@Override
	public void propertyChange(PropertyChangeEvent event) {
		if (slopeModel.getSlopeOverride() != 0) {
			plotModel.internalSetPlotOption(PlotOption.Spectrum);
		}
		runProcessing(false, false);
	}

	private void runProcessing(boolean resetPlot, boolean useCachedResults) {
		List<LoadedFile> files = fileController.getSelectedFiles();
		if (files.isEmpty()) {
			plottingSystem.clear();
			return;
		}

		do {
			dispatchJobs(files, resetPlot, useCachedResults);
		} while (retry);

	}

	boolean retry = false;
	private void dispatchJobs(List<LoadedFile> files, final boolean resetPlot, final boolean useCachedResults) {
		final JobGroup jg = new JobGroup("QR jobs", maxThreads, files.size());
		jobs.clear();
		if (!useCachedResults) {
			cachedJobs.clear();
		}
		for (LoadedFile f : files) {
			String path = f.getFilePath();
			ProcessFileJob j = cachedJobs.get(path);
			if (j == null || j.getData() == null) {
				j = new ProcessFileJob("QR per file: " + f.getName(), f);
				j.setJobGroup(jg);
				j.setPriority(Job.LONG);
				j.schedule();
				cachedJobs.put(path, j);
			}
			jobs.add(j);
		}
		retry = false;
		Thread t = new Thread(() -> {
			try {
				jg.join(0, null);
				
				for (ProcessFileJob j : jobs) {
					if (j.getResult().getCode() == IStatus.WARNING || j.getData() == null) {
						retry = true;
						return;
					}
				}
				plotResults(resetPlot);
			} catch (OperationCanceledException | InterruptedException e) {
			}
		});
		t.start();
		if (retry) {
			Runtime.getRuntime().gc();
		}
	}

	private void plotResults(boolean reset) {
		if (jobs.isEmpty()) {
			return;
		}
		Map<String, Dataset> plots = createPlotData(plotModel.getPlotOption());
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

	private Map<String, Dataset> createPlotData(PlotOption plotOption) {
		Map<String, Dataset> plots = new LinkedHashMap<>();
		switch (plotOption) {
		case Intercept:
			addPointData(plots, plotOption);
			break;
		case Slope:
			addPointData(plots, plotOption);
			break;
		case FWHM:
			addPointData(plots, plotOption);
			break;
		case SpectrumWithFit:
			addSpectrumData(plots, plotOption);
			addSpectrumData(plots, PlotOption.Spectrum);
			break;
		case Spectrum:
		default:
			addSpectrumData(plots, plotOption);
			break;
		}
		return plots;
	}

	private void addPointData(Map<String, Dataset> plots, PlotOption option) {
		List<Double> x = new ArrayList<>();
		List<Double> y = new ArrayList<>();
		String xName = null;
		for (ProcessFileJob j : jobs) {
			if (!j.getResult().isOK()) {
				continue;
			}
			LoadedFile f = j.getFile();
			Dataset v = f.getLabelValue();
			Map<String, Dataset> map = j.getData();
			Dataset pd = map == null ? null : map.get(option.getDataName());

			if (v == null) {
				Dataset[] axes = MetadataUtils.getAxesAndMakeMissing(pd);
				if (axes.length > 0) {
					v = axes[0];
				}
			}
			if (xName == null && v != null) {
				xName = v.getName();
			}
			System.err.println(v);
			System.err.println(pd);

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

		if (!y.isEmpty()) {
			Dataset r = DatasetFactory.createFromList(y);
			if (!x.isEmpty()) {
				Dataset xd = DatasetFactory.createFromList(x);
				xd.setName(xName);
				MetadataUtils.setAxes(r, xd);
			}
			plots.put(option.getPlotFormat(), r);
		}
	}

	private void addSpectrumData(Map<String, Dataset> plots, PlotOption option) {
		for (ProcessFileJob j : jobs) {
			if (!j.getResult().isOK()) {
				continue;
			}
			LoadedFile f = j.getFile();
			String n = String.format(option.getPlotFormat(), f.getName());
			List<Dataset> pd = get1DPlotData(j.getData(), option.getDataName());
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
			for (Dataset r : pd) {
				i++;
				if (r == null) {
					continue;
				}
				r.squeeze();
				String name;
				if (v == null) {
					name = String.format("%s:%d", n, i);
				} else {
					name = String.format("%s:%d (%s)", n, i, v.getObject(i));
				}
				plots.put(name, r);
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

		public ProcessFileJob(String name, LoadedFile file) {
			super(name);
			this.file = file;
			list = new ArrayList<>();
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

			if (!opts.isEmpty()) {
				// need per-file instances as operations are not thread-safe 
				SubtractFittedBackgroundOperation bop = null;
				if (subtractModel.isSubtractBackground()) {
					bop = new SubtractFittedBackgroundOperation();
					bop.setModel(bgModel);
				}
				ElasticLineReduction eop = new ElasticLineReduction();
				eop.setModel(elModel);
				eop.propertyChange(null); // trigger update from model

				ILazyDataset ld = opts.get(0).getLazyDataset();
				int[] shape = ld.getShape();
				int rank = shape.length;
				if (rank < 2) {
					return new Status(IStatus.WARNING, QuickRIXSPerspective.ID, "Image data must be in dataset of rank > 2");
				}
				int[] dataDims = new int[] {rank - 2, rank - 1};

				Dataset[] axes = new Dataset[rank - dataDims.length]; // assume axes are the same for all auxiliary data
				ILazyDataset[] lAxes = ld.getFirstMetadata(AxesMetadata.class).getAxes();
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

				int total = ShapeUtils.calcSize(Arrays.copyOf(shape, rank - 2));
				SliceNDIterator iter = new SliceNDIterator(new SliceND(shape), dataDims);
				SliceND slice = iter.getCurrentSlice();
				SourceInformation sri = new SourceInformation(file.getFilePath(), opts.get(0).getName(), ld);
				SubMonitor sub = SubMonitor.convert(monitor, total);
				sub.setTaskName("Processing images in " + file.getName());
				int i = 0;
				while (iter.hasNext()) {
					sub.newChild(1);
					SliceInformation si = new SliceInformation(slice, slice, slice, dataDims, total, i++);
					try {
						Dataset image = DatasetUtils.convertToDataset(ld.getSlice(slice));
						image.addMetadata(new SliceFromSeriesMetadata(sri, si));
						processImage(bop, eop, image, si, axes);
					} catch (DatasetException e) {
						break;
					} catch (OutOfMemoryError e) {
						data = null;
						return new Status(IStatus.WARNING, QuickRIXSPerspective.ID, "Out of memory");
					}
				}
			}
			return Status.OK_STATUS;
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

			double slope = slopeModel.getSlopeOverride();
			if (slope != 0) {
				Dataset sp = RixsBaseOperation.sumImageAlongSlope(i.transpose(), slope);
				sp.setName(PlotOption.Spectrum.getDataName());
				addToList(list, sp.reshape(1, sp.getSize()));
			} else {
				od = eop.execute(i, null);
				addToList(list, od.getAuxData());
			}

			if (si.isLastSlice()) {
				if (od != null) {
					addToMap(od.getSummaryData());
				}

				combineList(list, si.getTotalSlices(), axes);
			}
		}

		private Map<String, Dataset> getMap() {
			Map<String, Dataset> map;
			if (data == null) {
				map = new HashMap<>();
				data = new SoftReference<>(map);
			}
			map = data.get();
			if (map != null) {
				data = new SoftReference<>(map);
			}
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
		private void combineList(List<Dataset> list, int slices, Dataset[] axes) {
			Map<String, Dataset> map = getMap();
			Dataset[] ds = new Dataset[slices];

			for (int j = 0, jmax = list.size(); j < jmax; j++) {
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
					System.err.println("Error: missing data");
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

	// post-process:
	//  fitting to peaks in selected region and plot FWHM
	//  align peaks or turning points in selected region
}
