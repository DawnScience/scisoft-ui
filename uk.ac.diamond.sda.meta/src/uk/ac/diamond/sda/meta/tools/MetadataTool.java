/*
 * Copyright (c) 2012 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package uk.ac.diamond.sda.meta.tools;

import org.eclipse.dawnsci.plotting.api.region.IRegion;
import org.eclipse.dawnsci.plotting.api.region.IRegionListener;
import org.eclipse.dawnsci.plotting.api.region.RegionEvent;
import org.eclipse.dawnsci.plotting.api.region.RegionUtils;
import org.eclipse.dawnsci.plotting.api.tool.AbstractToolPage;
import org.eclipse.dawnsci.plotting.api.trace.IImageTrace;
import org.eclipse.january.dataset.IDataset;
import org.eclipse.january.metadata.IMetadata;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.part.IPageSite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.sda.meta.Activator;
import uk.ac.diamond.sda.meta.page.DiffractionMetadataComposite;
import uk.ac.diamond.sda.meta.page.IDiffractionMetadataCompositeListener;

public class MetadataTool extends AbstractToolPage {

	protected DiffractionMetadataComposite diffMetadataComp;
	protected Composite composite;
	
	// Logger
	private final static Logger metadataToollogger = LoggerFactory.getLogger(MetadataTool.class);
	
	//Region and region listener added for 1-click beam centring
	private IRegion tmpRegion;
	private IRegionListener regionListener;
	
	public MetadataTool() {
		
		this.regionListener = new IRegionListener.Stub() {
			@Override
			public void regionAdded(RegionEvent evt) {
				//test if our region
				if (evt.getRegion() == tmpRegion) {
					//update beam position and remove region
					metadataToollogger.debug("1-Click region added");
					double[] point = evt.getRegion().getROI().getPoint();
					diffMetadataComp.updateBeamPositionPixels(point);
					getPlottingSystem().removeRegion(tmpRegion);
				}
			}
		};
	}

	@Override
	public void createControl(Composite parent) {
		diffMetadataComp = new DiffractionMetadataComposite();
		diffMetadataComp.setEditable(true);
		IImageTrace imageTrace = getImageTrace();
		if (imageTrace != null) {
			IDataset dataset = imageTrace.getData();
			if (dataset != null) {
				diffMetadataComp.setData(dataset);
				IMetadata meta = dataset.getFirstMetadata(IMetadata.class);
				if (meta != null)
					diffMetadataComp.setMetaData(meta);
			}
		}

		composite = diffMetadataComp.createComposite(parent);
		
		addMetadataCompositeListener();
		
		if (getPlottingSystem()!=null) {
			getPlottingSystem().addRegionListener(this.regionListener);
		}
		
		final IPageSite site = getSite();
		
		final Action reselect = new Action("Reset all fields", Activator.getImageDescriptor("icons/book_previous.png")) {
			@Override
			public void run() {
				diffMetadataComp.resetAllToOriginal();
			}
		};
		
		//Current implementation- pressing the button enters the cursor into create region mode
		//The first click on the image places the ROI to mark the centre
		//In the ROI added event the metadata composite is updates and the ROI is removed
		final Action centre = new Action("One-click beam centre",IAction.AS_PUSH_BUTTON) {
			@Override
			public void run() {
				metadataToollogger.debug("1-click clicked");
				
				try {
					if (tmpRegion != null) {
						getPlottingSystem().removeRegion(tmpRegion);
					}
					tmpRegion = getPlottingSystem().createRegion(RegionUtils.getUniqueName("BeamCentrePicker", getPlottingSystem()), IRegion.RegionType.POINT);
					tmpRegion.setUserRegion(false);
					tmpRegion.setVisible(false);
					metadataToollogger.debug("1-click ROI created");
					
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

				
			}
		};
		
		centre.setImageDescriptor(Activator.getImageDescriptor("icons/centre.png"));
		site.getActionBars().getToolBarManager().add(reselect);
		site.getActionBars().getToolBarManager().add(centre);
		
		activate();
		
		super.createControl(parent);
	}
	
	@Override
	public void activate() {
		super.activate();
		
		addMetadataCompositeListener();
		
		if (getPlottingSystem()!=null) {
			getPlottingSystem().addRegionListener(this.regionListener);
		}
	}
	
	@Override
	public void deactivate() {
		super.deactivate();
		// Remove the MXPlotImageEditor as a listener
		IWorkbenchPart viewPart = getPart();
		if (viewPart instanceof IDiffractionMetadataCompositeListener) {
			IDiffractionMetadataCompositeListener diffMetaCompListener = (IDiffractionMetadataCompositeListener)viewPart;
			diffMetadataComp.removeDiffractionMetadataCompositeListener(diffMetaCompListener);
		}

		//remove region listener
		if (getPlottingSystem()!=null) {
			getPlottingSystem().removeRegionListener(this.regionListener);
		}
	}
	

	@Override
	public void dispose() {
		super.dispose();
	}

	@Override
	public Control getControl() {
		return composite;
	}

	@Override
	public ToolPageRole getToolPageRole() {
		return ToolPageRole.ROLE_2D;
	}

	@Override
	public void setFocus() {
		composite.setFocus();
	}
	
	private void addMetadataCompositeListener() {
		// Add the MXPlotImageEditor as a listener
		IWorkbenchPart viewPart = getPart();
		if (viewPart instanceof IDiffractionMetadataCompositeListener  && diffMetadataComp != null) {
			IDiffractionMetadataCompositeListener diffMetaCompListener = (IDiffractionMetadataCompositeListener)viewPart;
			diffMetadataComp.addDiffractionMetadataCompositeListener(diffMetaCompListener);
		}
	}
}
