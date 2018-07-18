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
package uk.ac.diamond.scisoft.mappingexplorer.views.oned;

import org.eclipse.jface.viewers.ISelection;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.INullSelectionListener;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IViewReference;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.IPageBookViewPage;

import uk.ac.diamond.scisoft.analysis.rcp.editors.HDF5TreeEditor;
import uk.ac.diamond.scisoft.mappingexplorer.views.IDatasetPlotterContainingView;
import uk.ac.diamond.scisoft.mappingexplorer.views.IMappingView2dData;
import uk.ac.diamond.scisoft.mappingexplorer.views.IMappingView3dData;
import uk.ac.diamond.scisoft.mappingexplorer.views.MappingPageBookView;
import uk.ac.diamond.scisoft.mappingexplorer.views.twod.IMappingDataControllingView;
import uk.ac.diamond.scisoft.mappingexplorer.views.twod.TwoDMappingView;

/**
 * PageBook view for One D Mapping viewer. One D display of a dataset that contains at least 3 dimensions.
 * 
 * @author rsr31645
 */
public class OneDMappingView extends MappingPageBookView implements IDatasetPlotterContainingView {
	private static final String PART_NAME = "OneD View - %1$s";
	public static final String ID = "uk.ac.diamond.scisoft.mappingexplorer.onedimension";

	protected String getTwoDViewId() {
		String secondaryId = getViewSite().getSecondaryId();
		if (secondaryId != null) {
			return TwoDMappingView.ID + ":" + secondaryId;
		}
		return TwoDMappingView.ID;
	}

	private ISelectionListener twoDViewSelectionListener = new INullSelectionListener() {
		@Override
		public void selectionChanged(IWorkbenchPart part, ISelection selection) {
			if (getCurrentPage() instanceof OneDViewPage) {
				OneDViewPage oneDViewPage = (OneDViewPage) getCurrentPage();
				oneDViewPage.doSelectionChangedOnTwoDView(part, selection);
			}
		}

	};

	@Override
	public void createPartControl(org.eclipse.swt.widgets.Composite parent) {
		super.createPartControl(parent);
		String viewId = getTwoDViewId();

		getSite().getWorkbenchWindow().getSelectionService().addSelectionListener(viewId, twoDViewSelectionListener);

	}

	@Override
	protected OneDViewPage createPage(IWorkbenchPart part) {

		Object adapter = part.getAdapter(IMappingView2dData.class);
		OneDViewPage page = null;

		if (part.getAdapter(IMappingView3dData.class) instanceof IMappingView3dData) {
			IMappingView3dData iMappingView3dData = part.getAdapter(IMappingView3dData.class);
			page = new OneDViewPage(part, getViewSite().getSecondaryId());
			initPage(page);
			page.createControl(getPageBook());
			page.setMappingViewData(iMappingView3dData);
		} else if (adapter instanceof IMappingView2dData) {
			page = new OneDViewPage(part, getViewSite().getSecondaryId());
			initPage(page);
			page.createControl(getPageBook());
			page.setMappingViewData((IMappingView2dData) adapter);
		} else if (part instanceof HDF5TreeEditor) {
			page = new OneDViewPage(part, getViewSite().getSecondaryId());
			initPage(page);
			page.createControl(getPageBook());
			page.setSelection(((HDF5TreeEditor) part).getHDF5TreeExplorer().getSelection());
		}

		// Find the view references -if there is a mapping data controlling
		// view, then get the selection from it and pass it to the view
		// page.
		// Also disable the control composite.
		IWorkbenchPage activePage = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
		if (page != null && activePage != null) {
			IViewReference[] viewReferences = activePage.getViewReferences();

			for (IViewReference viewRef : viewReferences) {
				IViewPart viewpart = (IViewPart) viewRef.getPart(false);
				if (viewpart instanceof IMappingDataControllingView) {
					String mdcvSecId = viewpart.getViewSite().getSecondaryId();
					// if secondaryid do match
					if ((mdcvSecId == null && getSecondaryId() == null)
							|| (mdcvSecId != null && mdcvSecId.equals(getSecondaryId()))) {
						page.setSelection(viewpart.getViewSite().getSelectionProvider().getSelection());
						break;
					}
				}
			}
		}

		return page;
	}

	@Override
	public String getPartName() {
		if (getViewSite() != null && getViewSite().getSecondaryId() != null) {
			return String.format(PART_NAME, getViewSite().getSecondaryId());
		}
		return super.getPartName();
	}

	@Override
	protected boolean isImportant(IWorkbenchPart part) {
		if (part instanceof IEditorPart) {
			return true;
		}
		return isInterestingMappingDataControllingView(part);
	}

	private boolean isInterestingMappingDataControllingView(IWorkbenchPart part) {
		if (part instanceof IMappingDataControllingView) {
			IMappingDataControllingView iTwoDMappingView = (IMappingDataControllingView) part;
			String secondaryId = iTwoDMappingView.getViewSite().getSecondaryId();

			if (secondaryId != null && secondaryId.equals(getSecondaryId())) {
				return true;
			}
		}
		return false;
	}

	protected String getSecondaryId() {
		return getViewSite().getSecondaryId();
	}

	@Override
	protected PageRec doCreatePage(IWorkbenchPart part) {
		if (getViewSite().getSecondaryId() != null) {
			return new PageRec(part, getDefaultPage());
		}
		// If the view belongs to a specific 2D view - this is identified with a secondary id and and page is created
		// that does not respond to input changes on the editor.
		PageRec pageRec = getPageRec(part);
		if (pageRec != null) {
			return pageRec;
		}
		IPageBookViewPage pageView = createPage(part);
		if (pageView != null) {
			return new PageRec(part, pageView);
		}
		return null;
	}

	@Override
	public void dispose() {
		getSite().getWorkbenchWindow().getSelectionService()
				.removeSelectionListener(getTwoDViewId(), twoDViewSelectionListener);
	}

}
