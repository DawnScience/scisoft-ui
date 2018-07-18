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

import gda.observable.IObservable;
import gda.observable.IObserver;

import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.part.IPage;
import org.eclipse.ui.part.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.rcp.editors.HDF5TreeEditor;
import uk.ac.diamond.scisoft.analysis.rcp.histogram.HistogramDataUpdate;
import uk.ac.diamond.scisoft.analysis.rcp.histogram.HistogramUpdate;
import uk.ac.diamond.scisoft.mappingexplorer.views.IContributingPart;
import uk.ac.diamond.scisoft.mappingexplorer.views.IDatasetPlotterContainingView;
import uk.ac.diamond.scisoft.mappingexplorer.views.IMappingView2dData;
import uk.ac.diamond.scisoft.mappingexplorer.views.IMappingView3dData;
import uk.ac.diamond.scisoft.mappingexplorer.views.IMappingViewDataContainingPage;
import uk.ac.diamond.scisoft.mappingexplorer.views.MappingPageBookView;
import uk.ac.diamond.scisoft.mappingexplorer.views.oned.OneDMappingView;

/**
 * @author rsr31645
 */
public class TwoDMappingView extends MappingPageBookView implements IDatasetPlotterContainingView, IObserver,
		IObservable, IHistogramDataSelect {
	private static final String VIEW_PART_NAME = "TwoD - %1$s";
	public static final String ID = "uk.ac.diamond.scisoft.mappingexplorer.twodview";
	private static final Logger logger = LoggerFactory.getLogger(TwoDMappingView.class);

	public TwoDMappingView() {
		logger.info("TwoD mapping View created");
	}

	protected String getOneDViewId() {
		String secondaryId = getViewSite().getSecondaryId();
		if (secondaryId != null) {
			return OneDMappingView.ID + ":" + secondaryId;
		}
		return OneDMappingView.ID;
	}

	private ISelectionListener oneDViewSelectionListener = new ISelectionListener() {

		@Override
		public void selectionChanged(IWorkbenchPart part, ISelection selection) {
			if (getCurrentPage() instanceof TwoDViewPage) {
				TwoDViewPage twoDViewPage = (TwoDViewPage) getCurrentPage();
				twoDViewPage.doSelectionChangedOnOneDView(part, selection);
			}
		}

	};

	@Override
	public void createPartControl(Composite parent) {
		super.createPartControl(parent);
		String viewId = getOneDViewId();
		getSite().getWorkbenchWindow().getSelectionService().addSelectionListener(viewId, oneDViewSelectionListener);
	}

	@Override
	protected Page createPage(IWorkbenchPart part) {
		Object adapter = part.getAdapter(IMappingView2dData.class);
		TwoDViewPage page = null;

		if (part.getAdapter(IMappingView3dData.class) instanceof IMappingView3dData) {
			IMappingView3dData iMappingView3dData = part.getAdapter(IMappingView3dData.class);
			page = new TwoDViewPage(part, getViewSite().getSecondaryId());
			initPage(page);
			page.createControl(getPageBook());
			page.setMappingViewData(iMappingView3dData);
		} else if (adapter instanceof IMappingView2dData) {
			page = new TwoDViewPage(part, getViewSite().getSecondaryId());
			initPage(page);
			page.createControl(getPageBook());
			page.setMappingViewData((IMappingView2dData) adapter);
		} else if (part instanceof HDF5TreeEditor) {
			page = new TwoDViewPage(part, getViewSite().getSecondaryId());
			initPage(page);
			page.createControl(getPageBook());
			page.setSelection(((HDF5TreeEditor) part).getHDF5TreeExplorer().getSelection());
		}
		return page;
	}

	@SuppressWarnings("rawtypes")
	@Override
	public Object getAdapter(Class key) {
		if (key == IMappingView3dData.class || key == IMappingView2dData.class) {
			if (getCurrentPage() instanceof IMappingViewDataContainingPage) {
				IMappingViewDataContainingPage mappingViewDataContainingPage = (IMappingViewDataContainingPage) getCurrentPage();
				return mappingViewDataContainingPage.getMappingViewData();

			}
		} else if (key == IContributingPart.class) {
			return getCurrentContributingPart();
		}
		return super.getAdapter(key);
	}

	@Override
	public String getPartName() {
		if (getViewSite() != null && getViewSite().getSecondaryId() != null) {
			return String.format(VIEW_PART_NAME, getViewSite().getSecondaryId());
		}
		return super.getPartName();
	}

	@Override
	protected boolean isImportant(IWorkbenchPart part) {
		return part instanceof IEditorPart;
	}

	@Override
	public void update(Object source, Object arg) {
		if (getCurrentPage() instanceof IHistogramDataUpdateProvider) {
			IHistogramDataUpdateProvider histDataUpdater = (IHistogramDataUpdateProvider) getCurrentPage();
			histDataUpdater.setHistogramUpdate((HistogramUpdate) arg);
		}
	}

	@Override
	public void addIObserver(IObserver observer) {
		logger.info("add an observer {} called", observer);
	}

	@Override
	public void deleteIObserver(IObserver observer) {
		logger.info("delete an observer {} called", observer);
	}

	@Override
	public void deleteIObservers() {
		logger.info("delete observers called");
	}

	public HistogramDataUpdate getHistogramDataUpdate() {
		if (getCurrentPage() instanceof IHistogramDataUpdateProvider) {
			IHistogramDataUpdateProvider histDataUpdater = (IHistogramDataUpdateProvider) getCurrentPage();
			return histDataUpdater.getHistogramDataUpdate();
		}
		return null;
	}

	@Override
	protected void showPageRec(PageRec pageRec) {
		IPage currentPage = getCurrentPage();
		super.showPageRec(pageRec);
		IPage newPage = getCurrentPage();
		if (currentPage != newPage) {
			SelectionProvider selectionProvider = getSelectionProvider();
			selectionProvider.selectionChanged(new SelectionChangedEvent(selectionProvider, selectionProvider
					.getSelection()));

		}
	}

	@Override
	public void selectAllDataForHistogram() {
		if (getCurrentPage() instanceof IHistogramDataUpdateProvider) {
			IHistogramDataUpdateProvider histDataUpdater = (IHistogramDataUpdateProvider) getCurrentPage();
			histDataUpdater.selectAllForHistogram();
		}
	}

	@Override
	public void dispose() {
		getSite().getWorkbenchWindow().getSelectionService()
				.removeSelectionListener(getOneDViewId(), oneDViewSelectionListener);
		super.dispose();
	}

}
