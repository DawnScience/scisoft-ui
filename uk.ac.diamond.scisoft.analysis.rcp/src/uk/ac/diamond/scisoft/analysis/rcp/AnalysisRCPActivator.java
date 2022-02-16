/*-
 * Copyright (c) 2012 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.analysis.rcp;

import org.eclipse.core.runtime.preferences.ConfigurationScope;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.dawnsci.analysis.api.ServerPortEvent;
import org.eclipse.dawnsci.analysis.api.ServerPortListener;
import org.eclipse.dawnsci.plotting.api.IPlottingSystem;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Widget;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.eclipse.ui.preferences.ScopedPreferenceStore;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.AnalysisRpcServerProvider;
import uk.ac.diamond.scisoft.analysis.PlotServer;
import uk.ac.diamond.scisoft.analysis.PlotServerProvider;
import uk.ac.diamond.scisoft.analysis.rcp.preference.PreferenceConstants;

/**
 * The activator class controls the plug-in life cycle
 */
public class AnalysisRCPActivator extends AbstractUIPlugin implements ServerPortListener {

	private static final Logger logger = LoggerFactory.getLogger(AnalysisRCPActivator.class);

	private ServiceTracker<?, ?> plotServerTracker;

	/**
	 * The plug-in ID
	 */
	public static final String PLUGIN_ID = "uk.ac.diamond.scisoft.analysis.rcp";

	// The shared instance
	private static AnalysisRCPActivator plugin;

	private static IPreferenceStore plottingPreferenceStore;

	private BundleContext context;

	/**
	 * The constructor
	 */
	public AnalysisRCPActivator() {
	}

	@Override
	public void start(BundleContext context) throws Exception {
		super.start(context);
		this.context = context;
		plugin = this;

		if (isGDA()) {
			AnalysisRpcServerProvider.getInstance().addPortListener(this);

			plotServerTracker = new ServiceTracker<>(context, PlotServer.class.getName(), null);
			plotServerTracker.open();
			PlotServer plotServer = (PlotServer)plotServerTracker.getService();
			if( plotServer != null) PlotServerProvider.setPlotServer(plotServer);
		}
	}

	@Override
	public void portAssigned(ServerPortEvent evt) {
		logger.info("Setting " + PreferenceConstants.ANALYSIS_RPC_SERVER_PORT_AUTO + " to: " + evt.getPort());
		IEclipsePreferences node = ConfigurationScope.INSTANCE.getNode("uk.ac.diamond.scisoft.analysis.rpc");
		node.putInt(PreferenceConstants.ANALYSIS_RPC_SERVER_PORT_AUTO, evt.getPort());
		try {
			node.flush();
		} catch (Exception e) {
			logger.error("Error saving preference", e);
		}
	}

	@Override
	public void stop(BundleContext context) throws Exception {
		plugin = null;

		if (isGDA()) {
			PlotServer plotServer = (PlotServer)plotServerTracker.getService();
			if( plotServer != null)
				PlotServerProvider.setPlotServer(null);
			plotServerTracker.close();
		}

		this.context = null;
		super.stop(context);
	}

	/**
	 * Returns the shared instance
	 *
	 * @return the shared instance
	 */
	public static AnalysisRCPActivator getDefault() {
		return plugin;
	}

	/**
	 * Returns an image descriptor for the image file at the given
	 * plug-in relative path
	 *
	 * @param path the path
	 * @return the image descriptor
	 */
	public static ImageDescriptor getImageDescriptor(String path) {
		return imageDescriptorFromPlugin(PLUGIN_ID, path);
	}

	/**
	 * Creates the image, this should be disposed later.
	 * @param path
	 * @return Image
	 */
	public static Image getImage(String path) {
		ImageDescriptor des = imageDescriptorFromPlugin(PLUGIN_ID, path);
		return des.createImage();
	}

	/**
	 * Get image from given path and add dispose listener so caller does not need to dispose
	 * @param w widget
	 * @param path plugin relative path of image file
	 * @return image
	 */
	public static Image getImageAndAddDisposeListener(Widget w, String path) {
		Image i = getImageDescriptor(path).createImage();
		w.addDisposeListener(e -> i.dispose());
		return i;
	}

	public BundleContext getBundleContext() {
		return context;
	}

	public static boolean isGDA() {
		return System.getProperty("gda.config")!=null;
	}

	public static IPreferenceStore getPlottingPreferenceStore() {
		if (plottingPreferenceStore == null)
			plottingPreferenceStore = new ScopedPreferenceStore(InstanceScope.INSTANCE, IPlottingSystem.PREFERENCE_STORE);
		return plottingPreferenceStore;
	}
	
	public static <T> T getService(final Class<T> serviceClass) {
		if (plugin == null) return null;
		BundleContext bc = plugin.getBundleContext();
		ServiceReference<T> ref = bc.getServiceReference(serviceClass);
		if (ref==null) return null;
		return bc.getService(ref);
	}
}
