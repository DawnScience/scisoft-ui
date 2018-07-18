/*-
 * Copyright © 2011 Diamond Light Source Ltd.
 *
 * This file is part of GDA.
 *
 * GDA is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License version 3 as published by the Free
 * Software Foundation.
 *
 * GDA is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along
 * with GDA. If not, see <http://www.gnu.org/licenses/>.
 */
package uk.ac.diamond.scisoft.mappingexplorer.views.twod;

import org.eclipse.january.DatasetException;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchPart2;
import org.eclipse.ui.part.PageBook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.rcp.editors.HDF5TreeEditor;
import uk.ac.diamond.scisoft.analysis.rcp.hdf5.HDF5Selection;
import uk.ac.diamond.scisoft.analysis.rcp.histogram.HistogramDataUpdate;
import uk.ac.diamond.scisoft.analysis.rcp.histogram.HistogramUpdate;
import uk.ac.diamond.scisoft.analysis.rcp.inspector.DatasetSelection;
import uk.ac.diamond.scisoft.mappingexplorer.views.BaseViewPageComposite;
import uk.ac.diamond.scisoft.mappingexplorer.views.BaseViewPageComposite.ViewPageCompositeListener;
import uk.ac.diamond.scisoft.mappingexplorer.views.HDF5MappingViewData;
import uk.ac.diamond.scisoft.mappingexplorer.views.IMappingView2dData;
import uk.ac.diamond.scisoft.mappingexplorer.views.IMappingViewData;
import uk.ac.diamond.scisoft.mappingexplorer.views.IMappingViewDataContainingPage;
import uk.ac.diamond.scisoft.mappingexplorer.views.MappingPageBookViewPage;

/**
 * @author rsr31645
 */
public class TwoDViewPage extends MappingPageBookViewPage implements IMappingViewDataContainingPage,
		IHistogramDataUpdateProvider {

	private BaseViewPageComposite activePage;

	private BlankPageComposite blankPageComposite;

	private TwoDDataSetPlotterContainingPage twoDDataSetPlotterComposite;

	private static final Logger logger = LoggerFactory.getLogger(TwoDViewPage.class);

	private PageBook pgBook;

	private Composite rootComposite;

	private String nodeName;

	public TwoDViewPage(IWorkbenchPart part, String secondaryViewId) {
		super(part, secondaryViewId);
	}

	@Override
	public void createControl(Composite parent) {
		super.createControl(parent);
		rootComposite = new Composite(parent, SWT.None);
		rootComposite.setLayout(new FillLayout());

		pgBook = new PageBook(rootComposite, SWT.None);

		blankPageComposite = new BlankPageComposite(pgBook, SWT.None);

		twoDDataSetPlotterComposite = new TwoDDataSetPlotterContainingPage(this, pgBook, SWT.None, getSecondaryViewId());

		activePage = blankPageComposite;

		pgBook.showPage(activePage);

	}

	public void setMappingViewData(IMappingViewData data) {

		try {
			if (data instanceof IMappingView2dData && ((IMappingView2dData) data).getDataSet() != null) {
				IMappingView2dData dataIn3D = (IMappingView2dData) data;
				if (!activePage.equals(twoDDataSetPlotterComposite)) {
					twoDDataSetPlotterComposite.setMappingViewData(dataIn3D);
					activePage.removeCompositeSelectionListener(pageSelectionListener);

					twoDDataSetPlotterComposite.addCompositeSelectionListener(pageSelectionListener);
					try {
						twoDDataSetPlotterComposite.initialPlot();
					} catch (Exception e) {
						logger.error("Initial plotting error in 3D view page {}", e);
					}
				} else {
					twoDDataSetPlotterComposite.setMappingViewData(dataIn3D);
					try {
						twoDDataSetPlotterComposite.initialPlot();
					} catch (Exception e) {
						logger.error("Initial plotting error in 3D view page {}", e);
					}
				}

				activePage = twoDDataSetPlotterComposite;
			} else {
				activePage.removeCompositeSelectionListener(pageSelectionListener);
				activePage = blankPageComposite;
			}
		} catch (DatasetException e) {
			throw new IllegalArgumentException("Could not get dataset", e);
		}
		pgBook.showPage(activePage);
		fireNotifySelectionChanged(activePage.getSelection());
	}

	@Override
	public void initialPlot() throws Exception {
		activePage.initialPlot();
	}

	@Override
	public IMappingViewData getMappingViewData() {
		return activePage.getMappingViewData();
	}

	@Override
	public ISelection getSelection() {
		return activePage.getSelection();
	}

	@Override
	public void setSelection(ISelection selection) {
		if (activePage != null) {
			activePage.selectionChanged(null, selection);
		}
	}

	@Override
	public Control getControl() {
		return rootComposite;
	}

	@Override
	public void setFocus() {
		activePage.setFocus();
	}

	private ViewPageCompositeListener pageSelectionListener = new ViewPageCompositeListener() {

		@Override
		public void selectionChanged(ISelection selection) {
			fireNotifySelectionChanged(selection);
		}

	};

	@Override
	protected void doSelectionForEditorSelectionChange(IWorkbenchPart part, ISelection selection) {
		if (this.part.equals(part)) {
			if (getSecondaryViewId() == null) {
				if (selection instanceof HDF5Selection) {
					// Should propagate the selection only if the secondary id is null
					// and the HDF5Treeviewer editor is the part that this page is
					// created for.
					HDF5Selection hdf5Selection = (HDF5Selection) selection;
					if (nodeName == null || (nodeName != null && !hdf5Selection.getNode().equals(nodeName))) {
						nodeName = hdf5Selection.getNode();
						IMappingViewData mappingViewData = HDF5MappingViewData
								.getMappingViewData((DatasetSelection) selection);
						setMappingViewData(mappingViewData);
						fireNotifySelectionChanged(getSelection());
					}
				} else {
					if (this.part.getAdapter(IMappingView2dData.class) != null) {
						IMappingView2dData mappingViewData = this.part.getAdapter(IMappingView2dData.class);
						setMappingViewData(mappingViewData);
						fireNotifySelectionChanged(getSelection());
					}
				}
			}
		}
	}

	@Override
	public String getOriginIdentifer() {
		if (!activePage.equals(blankPageComposite)) {
			if (this.part instanceof HDF5TreeEditor) {
				return nodeName;
			} else if (this.part instanceof IWorkbenchPart2) {
				IWorkbenchPart2 wp2 = (IWorkbenchPart2) this.part;
				return wp2.getPartName();
			}
		}
		return null;
	}

	@Override
	public void dispose() {
		fireNotifySelectionChanged(StructuredSelection.EMPTY);
		super.dispose();
	}

	protected void doSelectionChangedOnOneDView(IWorkbenchPart part, ISelection selection) {
		if (part == null && selection == null) {
			// this will be the effect of using a INullSelectionListener
			activePage.selectionChanged(null, null);
		} else if (part != null) {
			String secondaryId = ((IViewPart) part).getViewSite().getSecondaryId();
			if (getSecondaryViewId() != null && getSecondaryViewId().equals(secondaryId)) {
				activePage.selectionChanged(part, selection);
			} else {
				activePage.selectionChanged(part, selection);
			}
		}
	}

	@Override
	public HistogramDataUpdate getHistogramDataUpdate() {
		return activePage.getHistogramDataUpdate();
	}

	@Override
	public void setHistogramUpdate(HistogramUpdate histogramUpdate) {
		activePage.applyHistogramUpdate(histogramUpdate);
	}

	@Override
	public void selectAllForHistogram() {
		activePage.selectAllForHistogram();
	}

}
