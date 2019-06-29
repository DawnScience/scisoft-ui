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

import org.eclipse.dawnsci.analysis.api.io.IDataHolder;
import org.eclipse.january.dataset.Dataset;
import org.eclipse.january.dataset.DatasetFactory;
import org.eclipse.january.dataset.IDataset;

import uk.ac.diamond.scisoft.analysis.SDAPlotter;
import uk.ac.diamond.scisoft.analysis.io.LoaderFactory;
import uk.ac.diamond.sda.polling.jobs.FilenameReaderUpdateOnlyJob;

public class ImagePlotAccumulatorJob extends FilenameReaderUpdateOnlyJob {

	private static final Object PLOT_VIEW_NAME = "PlotViewName";
	private static final Object MAX_IMAGES_TO_ACCUMULATE = "MaxImagesToAccumulate";
	
	@Override
	protected void processFile(ArrayList<String> filenames) {
		try {	
			// get the end of the list
			int listEnd = filenames.size()-1;

			ArrayList<IDataset> images = new ArrayList<IDataset>();
			
			int position = 0;
			while (listEnd-position >= 0 && position < Integer.parseInt(getJobParameters().get(MAX_IMAGES_TO_ACCUMULATE))) {
				IDataHolder data = LoaderFactory.getData(filenames.get(listEnd-position));
				images.add(data.getDataset(0));
				position++;
			}			
				
			Dataset accumulator =  DatasetFactory.zeros(images.get(0).getShape());
			for(int i = 0; i < images.size(); i++) {
				accumulator.iadd(images.get(i));
			}
			
			SDAPlotter.imagePlot(getJobParameters().get(PLOT_VIEW_NAME), accumulator);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}

}
