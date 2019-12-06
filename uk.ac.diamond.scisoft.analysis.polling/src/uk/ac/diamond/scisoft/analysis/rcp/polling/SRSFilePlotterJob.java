/*
 * Copyright (c) 2012 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.analysis.rcp.polling;

import java.util.ArrayList;

import org.eclipse.january.dataset.CompoundDataset;
import org.eclipse.january.dataset.Dataset;
import org.eclipse.january.dataset.DatasetUtils;

import uk.ac.diamond.scisoft.analysis.SDAPlotter;
import uk.ac.diamond.scisoft.analysis.io.DataHolder;
import uk.ac.diamond.scisoft.analysis.io.SRSLoader;
import uk.ac.diamond.sda.polling.jobs.FilenameReaderJob;

public class SRSFilePlotterJob extends FilenameReaderJob {

	public static final String PLOT_VIEW_NAME = "PlotViewName";
	
	@Override
	protected void processFile(ArrayList<String> filenames) {
		// only process the last given file
		SRSLoader srsLoader = new SRSLoader(filenames.get(filenames.size()-1));
		try {
			DataHolder holder = srsLoader.loadFile();
			
			String[] dataPlotNames = getJobParameters().get("YAxis").split(",");
						
			ArrayList<CompoundDataset> list = new ArrayList<CompoundDataset>();
		
			for (String name : dataPlotNames) {				
				
				Dataset[] acd = new Dataset[] { holder.getDataset(getJobParameters().get("XAxis")), holder.getDataset(name.trim()) };
				CompoundDataset cdd = DatasetUtils.cast(acd[0].getClass(), acd);
				list.add(cdd);			
			}
			
			int[] sizes = new int[list.size()];
			for (int i = 0 ; i < sizes.length; i++) {
				sizes[i] = 5;
			}
			
			SDAPlotter.scatter2DPlot(getJobParameters().get(PLOT_VIEW_NAME),
					list.toArray(new CompoundDataset[0]),
					sizes);
			
			
			
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		

	}

}
