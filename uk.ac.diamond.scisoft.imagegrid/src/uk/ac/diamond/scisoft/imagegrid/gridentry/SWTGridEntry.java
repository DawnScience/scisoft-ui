/*
 * Copyright (c) 2012 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.imagegrid.gridentry;

import java.awt.Dimension;
import java.io.IOException;

import org.dawnsci.plotting.services.util.SWTImageUtils;
import org.eclipse.dawnsci.plotting.api.histogram.IPaletteService;
import org.eclipse.january.dataset.CompoundDataset;
import org.eclipse.january.dataset.Dataset;
import org.eclipse.january.dataset.DatasetUtils;
import org.eclipse.january.dataset.IDataset;
import org.eclipse.january.dataset.Stats;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageLoader;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Canvas;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.plotserver.DatasetWithAxisInformation;
import uk.ac.diamond.scisoft.imagegrid.ServiceHolder;

/**
 * SWT Image implementation of a ImageGridEntry
 */
public class SWTGridEntry extends AbstractGridEntry {

	// Adding in some logging to help with getting this running
	private static final Logger logger = LoggerFactory.getLogger(SWTGridEntry.class);
	private double loThreshold = 0.0;
	private double hiThreshold = 0.98;
	private static Color green = null;
	private static Color red = null;
	private static Color blue = null;
	private static long lastREDRAWinMillis = 0; 
	
	private Image gridImage;
	private Dimension imageDim;
	private Canvas canvas;
	private String toolTipText = null;
	@SuppressWarnings("unused")
	private int colourMapChoice = 0;
	private PaletteData paletteData;
	/**
	 * palette service used to retrieved the colour scheme
	 */
	private IPaletteService pservice;

	public SWTGridEntry(String filename) {
		super(filename);
		gridImage = null;

	}

	public SWTGridEntry(String filename, Object additional) {
		super(filename,additional);
		gridImage = null;
	}

	public SWTGridEntry(String filename, Object additional, Canvas canvas,
			            int colourMapChoice, double loThreshold, double hiThreshold) {
		this(filename,additional);
		this.canvas = canvas;
		this.colourMapChoice = colourMapChoice;
		this.loThreshold = loThreshold;
		this.hiThreshold = hiThreshold;
	}

	public SWTGridEntry(String filename, Object additional, Canvas canvas, String colorScheme, double loThreshold,
			double hiThreshold) {
		this(filename, additional);
		this.canvas = canvas;
		if (pservice == null)
			pservice = ServiceHolder.getPaletteService();
		this.paletteData = pservice.getDirectPaletteData(colorScheme);
		this.loThreshold = loThreshold;
		this.hiThreshold = hiThreshold;
	}

	@Override
	public void setNewfilename(String newFilename) {
		this.filename = newFilename;
		this.additionalInfo = null;
		if (gridImage != null)
			gridImage.dispose();
		if (thumbnailFilename != null) {
			java.io.File imageFile = new java.io.File(thumbnailFilename);
			imageFile.delete();
			thumbnailFilename = null;
		}
	}

	@Override
	public void setStatus(int newStatus) {
		status = newStatus;

	}

	@Override
	public void deActivate() {
		if (gridImage != null) {
			try {
				if (thumbnailFilename == null) {
					java.io.File  file = java.io.File.createTempFile("tmp_thumb", ".png");
					file.deleteOnExit(); // We try to ensure that the thing does get removed.
					
					thumbnailFilename = file.getAbsolutePath();
					ImageLoader loader = new ImageLoader();
					loader.data = new ImageData[]{gridImage.getImageData()};
					loader.save(thumbnailFilename, SWT.IMAGE_PNG);
				}
			} catch (IOException e) {
				logger.error("Cannot cache thubnail in image explorer", e);
			} 
			
			gridImage.dispose();
			gridImage = null;
		}
	}

	public void loadThumbImage() {
		if (gridImage == null) {
			
			if (canvas.isDisposed() || isDisposed) return;
			canvas.getDisplay().asyncExec(new Runnable() {
				@Override
				public void run() {
					gridImage = new Image(canvas.getDisplay(),thumbnailFilename);
					// make sure system doesn't get flooded with redraw requests
					if (System.currentTimeMillis()-lastREDRAWinMillis > 20) {
						canvas.redraw();
						lastREDRAWinMillis = System.currentTimeMillis();
					}
				}
			});
		} else {
			logger.warn("Something is wrong");
		}
	}

	@Override
	public boolean isDeactivated() {
		return gridImage == null;
	}

	private double[] minMax(CompoundDataset d, double lo, double hi) {
		int e = d.getElementsPerItem();
		double[] mm;
		if (hi < 1) {
			if (lo > 0) {
				mm = Stats.quantile(d.getElementsView(0), lo, hi);
				for (int i = 1; i < e; i++) {
					double[] ti = Stats.quantile(d.getElementsView(i), lo, hi);
					mm[0] = Math.max(mm[0], ti[0]);
					mm[1] = Math.min(mm[1], ti[1]);
				}
			} else {
				Dataset v = d.getElementsView(0);
				mm = new double[] {v.min().doubleValue(), Stats.quantile(v, hi)};
				for (int i = 1; i < e; i++) {
					v = d.getElementsView(i);
					double tl = v.min().doubleValue();
					mm[0] = Math.max(mm[0], tl);
					double th = Stats.quantile(v, hi);
					mm[1] = Math.min(mm[1], th);
				}
			}
		} else if (lo > 0) {
			Dataset v = d.getElementsView(0);
			mm = new double[] {Stats.quantile(v, lo), v.max().doubleValue()};
			for (int i = 1; i < e; i++) {
				v = d.getElementsView(i);
				double tl = Stats.quantile(v, lo);
				mm[0] = Math.max(mm[0], tl);
				double th = v.max().doubleValue();
				mm[1] = Math.min(mm[1], th);
			}
		} else {
			Dataset v = d.getElementsView(0);
			mm = new double[] {v.min().doubleValue(), v.max().doubleValue()};
			for (int i = 1; i < e; i++) {
				v = d.getElementsView(i);
				double tl = v.min().doubleValue();
				mm[0] = Math.max(mm[0], tl);
				double th = v.max().doubleValue();
				mm[1] = Math.min(mm[1], th);
			}
		}
		return mm;
	}

	@Override
	public void createImage(final IDataset ids) {
		if (canvas.isDisposed() || isDisposed) return;

		canvas.getDisplay().asyncExec(new Runnable() {
			@Override
			public void run() {
				final Dataset ds = DatasetUtils.convertToDataset(ids);

				final int[] shape = ds.getShape();
				try {
					if (shape.length == 2) {
						double[] m = new double[2];
						if (ds instanceof CompoundDataset) {
							m = minMax((CompoundDataset) ds, loThreshold, hiThreshold);
						} else {
							if (hiThreshold < 1) {
								if (loThreshold > 0) {
									m = Stats.quantile(ds, loThreshold, hiThreshold);
								} else {
									m = new double[] {ds.min().doubleValue(), Stats.quantile(ds, hiThreshold)};
								}
							} else if (loThreshold > 0) {
								m = new double[] {Stats.quantile(ds, loThreshold), ds.max().doubleValue()};
							} else {
								m = new double[] {ds.min().doubleValue(), ds.max().doubleValue()};
							}
						}
						// Old mapping not necessary anymore 
//						int redSelect = GlobalColourMaps.colourSelectList.get(colourMapChoice * 4);
//						int greenSelect = GlobalColourMaps.colourSelectList.get(colourMapChoice * 4 + 1);
//						int blueSelect = GlobalColourMaps.colourSelectList.get(colourMapChoice * 4 + 2);
//						AbstractMapFunction redFunc = GlobalColourMaps.mappingFunctions.get(Math.abs(redSelect));
//						AbstractMapFunction greenFunc = GlobalColourMaps.mappingFunctions.get(Math.abs(greenSelect));
//						AbstractMapFunction blueFunc = GlobalColourMaps.mappingFunctions.get(Math.abs(blueSelect));
//						ImageData imgD = SWTImageUtils.createImageData(ds, m[0], m[1], redFunc, greenFunc, blueFunc,
//								(redSelect < 0), (greenSelect < 0), (blueSelect < 0));
						ImageData imgD = SWTImageUtils.createImageData(ds, m[0], m[1], paletteData);
						gridImage = new Image(canvas.getDisplay(), imgD);
						imageDim = new Dimension(shape[1], shape[0]);
						canvas.redraw();
					} else {
						setStatus(INVALIDSTATUS);
					}
				} catch (Exception e) {
					setStatus(INVALIDSTATUS);
					logger.debug(e.getMessage());
				}
			}
		});
	}

	public void paint(GC gc, int posX, int posY, int xSize, int ySize)
	{
		if (gridImage != null &&
			!gridImage.isDisposed()) {
			int w, h, x, y;
			if (imageDim.width > imageDim.height) {
				w = xSize;
				x = 0;
				h = ySize * imageDim.height / imageDim.width;
				y = (ySize - h)/2;
			} else {
				h = ySize;
				y = 0;
				w = xSize * imageDim.width / imageDim.height;
				x = (xSize - w)/2;
			}
			gc.drawImage(gridImage, 0, 0, imageDim.width, imageDim.height, posX+x, posY+y, w, h);
			switch(status) {
				case 1:
				{
					if (green == null) {
						green = new Color(canvas.getDisplay(),new RGB(0,255,0));						
					}
					gc.setForeground(green);
					gc.drawRectangle(posX, posY, xSize, ySize);
				}
				break;
				case 2:
				{
					if (red == null) {
						red = new Color(canvas.getDisplay(),new RGB(255,0,0));						
					}
					gc.setForeground(red);
					gc.drawRectangle(posX, posY, xSize, ySize);
				}
				break;
				case SELECTEDSTATUS:
				{
					if (blue == null) {
						blue = new Color(canvas.getDisplay(),new RGB(64,64,255));						
					}
					gc.setAlpha(128);
					gc.setBackground(blue);
					gc.fillRectangle(posX, posY, xSize, ySize);
					gc.setAlpha(255);
				}
				break;
			}
		} else {
			if (red == null) {
				red = new Color(canvas.getDisplay(),new RGB(255,0,0));						
			}			
			gc.setForeground(red);
			gc.drawLine(posX+8,posY+8, posX+xSize-8, posY+ySize-8);
			gc.drawLine(posX+xSize-8, posY+8, posX+8, posY+ySize-8);
		}
	}
	
	public Dimension getImageDimension() {
		return imageDim;
	}

	public boolean hasThumbnailImage() {
		return thumbnailFilename != null;
	}
	
	public boolean hasImage() {
		return (gridImage != null && !gridImage.isDisposed());
	}

	private boolean isDisposed = false;
	@Override
	public void dispose() {
		isDisposed = true;
		if (gridImage != null &&
			!gridImage.isDisposed())
			gridImage.dispose();
		
        if (thumbnailFilename != null)	
        {
        	java.io.File imageFile = new java.io.File(thumbnailFilename);
        	imageFile.delete();
        }
	}

	@Override
	public String getToolTipText() {
		if (additionalInfo == null) {
			if (toolTipText == null) {
				toolTipText = (new java.io.File(filename)).getName();
			}
		} else {
			if (additionalInfo instanceof DatasetWithAxisInformation) {
				DatasetWithAxisInformation datainfo = (DatasetWithAxisInformation) additionalInfo;
				toolTipText = datainfo.getData().getName();
			} else if (additionalInfo instanceof IDataset){
				toolTipText = ((IDataset) additionalInfo).getName();
			} else {
				logger.debug("tool tip not supported");
			}
		}
		return toolTipText;
	}
}
