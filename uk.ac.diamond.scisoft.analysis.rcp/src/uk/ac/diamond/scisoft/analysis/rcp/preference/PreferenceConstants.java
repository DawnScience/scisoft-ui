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

package uk.ac.diamond.scisoft.analysis.rcp.preference;


public class PreferenceConstants {

	public static final String IGNORE_DATASET_FILTERS = "ignore.data.set.filters";
	public static final String SHOW_XY_COLUMN         = "show.xy.column.in.nexus.editor";
	public static final String SHOW_DATA_SIZE         = "show.data.size.in.nexus.editor";
	public static final String SHOW_DIMS              = "show.dims.in.nexus.editor";
	public static final String SHOW_SHAPE             = "show.shape.in.nexus.editor";
	public static final String DATA_FORMAT            = "data.format.editor.view";
	public static final String PLAY_SPEED             = "data.format.slice.play.speed";

	// sideplotter 1D preferences
	public static final String SIDEPLOTTER1D_USE_LOG_Y = "sideplotter1d.useLog.y";

	// gridscan sideplot preferences
	public static final String GRIDSCAN_RESOLUTION_X = "gridscan.res.x";
	public static final String GRIDSCAN_RESOLUTION_Y = "gridscan.res.y";
	public static final String GRIDSCAN_BEAMLINE_POSX = "gridscan.beamline.posx";
	public static final String GRIDSCAN_BEAMLINE_POSY = "gridscan.beamline.posy";
    // diffraction viewer preferences
	public static final String DIFFRACTION_VIEWER_PEAK_TYPE = "diffviewer.peaktype";
	public static final String DIFFRACTION_VIEWER_MAX_PEAK_NUM = "diffviewer.maxNumPeaks";
	
	public static final String DIFFRACTION_VIEWER_AUTOSTOPPING = "diffviewer.autoStopping";
	public static final String DIFFRACTION_VIEWER_STOPPING_THRESHOLD = "diffviewer.stoppingThreshold";
	
	public static final String DIFFRACTION_VIEWER_STANDARD_NAME = "diffviewer.standardName";
	public static final String DIFFRACTION_VIEWER_STANDARD_NAME_LIST = "diffviewer.standardNameList";
	public static final String DIFFRACTION_VIEWER_STANDARD_DISTANCES = "diffviewer.standardDistances";
	public static final String DIFFRACTION_VIEWER_STANDARD_DISTANCES_LIST = "diffviewer.standardDistancesList";
	public static final String DIFFRACTION_VIEWER_PIXELOVERLOAD_THRESHOLD = "diffviewer.pixelOverLoadThreshold";

	// plot view preferences
	public static final String PLOT_VIEW_MULTI1D_CAMERA_PROJ = "plotView.multi1Dcamera";
	public static final String PLOT_VIEW_PLOT2D_COLOURMAP = "plotView.plot2DcolourMap";
	public static final String PLOT_VIEW_PLOT2D_SCALING = "plotView.plot2Dscaling";
	public static final String PLOT_VIEW_PLOT2D_CMAP_EXPERT = "plotView.plot2DcmapExpert";
	public static final String PLOT_VIEW_PLOT2D_AUTOCONTRAST = "plotView.plot2DautoContrast";
	public static final String PLOT_VIEW_PLOT2D_AUTOCONTRAST_LOTHRESHOLD = "plotView.autoContrastLoThreshold";
	public static final String PLOT_VIEW_PLOT2D_AUTOCONTRAST_HITHRESHOLD = "plotView.autoContrastHiThreshold";
	public static final String PLOT_VIEW_PLOT2D_SHOWSCROLLBAR = "plotView.plot2DshowScrollbar";
	public static final String PLOT_VIEW_PLOTTING_SYSTEM = "plotView.plottingsystem";

	/**
	 * Minimum difference in low and high contrast levels
	 */
	public static final int MINIMUM_CONTRAST_DELTA = 1;

	// image explorer preferences
	public static final String IMAGEEXPLORER_COLOURMAP = "imageExplorer.colourMap";
	public static final String IMAGEEXPLORER_AUTOCONTRAST_LOTHRESHOLD = "imageExplorer.autoContrastLoThreshold";
	public static final String IMAGEEXPLORER_AUTOCONTRAST_HITHRESHOLD = "imageExplorer.autoContrastHiThreshold";
	public static final String IMAGEEXPLORER_TIMEDELAYBETWEENIMAGES = "imageExplorer.timeDelay";
	public static final String IMAGEEXPLORER_PLAYBACKVIEW = "imageExplorer.playbackView";
	public static final String IMAGEEXPLORER_PLAYBACKRATE = "imageExplorer.playbackRate";

	// Fitting 1D preferences
	public static final String FITTING_1D_PEAKTYPE = "fitting1d.peak.type";
	public static final String FITTING_1D_PEAKLIST = "fitting1d.peak.list";
	public static final String FITTING_1D_PEAK_NUM = "fitting1d.peak.num";
	public static final String FITTING_1D_ALG_TYPE = "fitting1d.alg.type";
	public static final String FITTING_1D_ALG_LIST = "fitting1d.alg.list";
	public static final String FITTING_1D_SMOOTHING_VALUE = "fitting1d.alg.smoothing";
	public static final String FITTING_1D_ALG_ACCURACY = "fitting1d.alg.accuracy";
	public static final String FITTING_1D_AUTO_SMOOTHING = "fitting.alg.autosmoothing";
	public static final String FITTING_1D_AUTO_STOPPING = "fitting.alg.autostopping";
	public static final String FITTING_1D_THRESHOLD = "fitting.alg.stopping.threshold";
	public static final String FITTING_1D_THRESHOLD_MEASURE = "fitting.alg.threshold.measure";
	public static final String FITTING_1D_THRESHOLD_MEASURE_LIST = "fitting.alg.threshold.measure.list";
	public static final String FITTING_1D_DECIMAL_PLACES = "fitting1d.peak.dp";

	// Analysis RPC preferences
	public static final String ANALYSIS_RPC_SERVER_PORT = "analysisrpc.server.port";
	public static final String ANALYSIS_RPC_TEMP_FILE_LOCATION = "analysisrpc.tempfile";

	// RMI preferences
	public static final String RMI_SERVER_PORT = "rmi.server.port";
	
	// Analysis RPC and RMI shared preferences
	public static final String ANALYSIS_RPC_RMI_INJECT_VARIABLES = "analysisrpcrmi.injectvariables";

	// Print Settings preferences
	public static final String PRINTSETTINGS_PRINTER_NAME = "printsettings.printername";
	public static final String PRINTSETTINGS_ORIENTATION = "printsettings.orientation";
	public static final String PRINTSETTINGS_SCALE = "printsettings.scale";
	public static final String PRINTSETTINGS_RESOLUTION = "printsettings.resolution";

}
