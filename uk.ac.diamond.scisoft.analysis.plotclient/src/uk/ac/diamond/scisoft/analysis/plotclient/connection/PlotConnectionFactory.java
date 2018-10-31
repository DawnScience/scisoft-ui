package uk.ac.diamond.scisoft.analysis.plotclient.connection;

import org.eclipse.dawnsci.plotting.api.IPlottingSystem;
import org.eclipse.dawnsci.plotting.api.PlotType;

import uk.ac.diamond.scisoft.analysis.plotserver.GuiPlotMode;

/**
 * Gets the plot connection.
 * @author Matthew Gerring
 *
 */
public class PlotConnectionFactory {

	/**
	 * Get the connection whilst shielding the concrete implementation
	 * @param plotMode
	 * @param plottingSystem
	 * @return connection
	 */
	public static AbstractPlotConnection getConnection(GuiPlotMode plotMode, IPlottingSystem<?> plottingSystem) {
		
		AbstractPlotConnection plotUI=null;
		if (plotMode.equals(GuiPlotMode.ONED)) {
			plottingSystem.setPlotType(PlotType.XY);
			plotUI = new Plotting1DUI(plottingSystem);
		} else if (plotMode.equals(GuiPlotMode.ONED_THREED)) {
			plottingSystem.setPlotType(PlotType.XY_STACKED_3D);
			plotUI = new Plotting1DStackUI(plottingSystem);
		} else if (plotMode.equals(GuiPlotMode.TWOD)) {
			plottingSystem.setPlotType(PlotType.IMAGE);
			plotUI = new Plotting2DUI(plottingSystem);
		} else if (plotMode.equals(GuiPlotMode.SURF2D)) {
			plottingSystem.setPlotType(PlotType.SURFACE);
			plotUI = new Plotting2DUI(plottingSystem);
		} else if (plotMode.equals(GuiPlotMode.SCATTER2D)) {
//			plottingSystem.setPlotType(PlotType.SCATTER2D);
			plottingSystem.setPlotType(PlotType.XY);
			plotUI = new PlottingScatter2DUI(plottingSystem);
		} else if (plotMode.equals(GuiPlotMode.SCATTER3D)) {
			plottingSystem.setPlotType(PlotType.XY_SCATTER_3D);
			plotUI = new PlottingScatter3DUI(plottingSystem);
		} else if (plotMode.equals(GuiPlotMode.MULTI2D)) {
			plottingSystem.setPlotType(PlotType.MULTI_IMAGE);
			plotUI = new Plotting2DMultiUI(plottingSystem);
		} else if (plotMode.equals(GuiPlotMode.VOLUME)) {
			plotUI = new PlottingVolumeUI(plottingSystem);
		}
		return plotUI;
	}

	public static GuiPlotMode getPlotMode(IPlottingSystem<?> plottingSystem) {
		
		if (plottingSystem==null) return GuiPlotMode.ONED;
		
		final PlotType type = plottingSystem.getPlotType();
		if (type == PlotType.XY) {
			return GuiPlotMode.ONED;
			
		} else if (type==PlotType.XY_STACKED_3D) {
			return GuiPlotMode.ONED_THREED;
			
		} else if (type == PlotType.IMAGE) {
			return GuiPlotMode.TWOD;
			
		} else if (type == PlotType.SURFACE) {
			return GuiPlotMode.SURF2D;
			
		} else if (type == PlotType.XY_SCATTER_3D) {
			return GuiPlotMode.SCATTER3D;
			
		} else if (type == PlotType.MULTI_IMAGE) {
			return GuiPlotMode.MULTI2D;
		}
		
		return GuiPlotMode.ONED;
	}
}
