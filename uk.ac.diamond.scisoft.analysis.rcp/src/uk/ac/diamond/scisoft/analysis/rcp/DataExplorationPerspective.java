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

package uk.ac.diamond.scisoft.analysis.rcp;

import org.eclipse.ui.IFolderLayout;
import org.eclipse.ui.IPageLayout;
import org.eclipse.ui.IPerspectiveFactory;

import uk.ac.diamond.scisoft.analysis.rcp.views.DatasetInspectorView;
import uk.ac.diamond.scisoft.analysis.rcp.views.PlotView;
import uk.ac.diamond.scisoft.analysis.rcp.views.SidePlotView;

public class DataExplorationPerspective implements IPerspectiveFactory {

	public static final String ID = "uk.ac.diamond.scisoft.dataexplorationperspective";
	@Override
	public void createInitialLayout(IPageLayout layout) {
		String editorArea = layout.getEditorArea();
		
		IFolderLayout navigatorLayout = layout.createFolder("navigators", IPageLayout.LEFT, 0.15f, editorArea);
		String explorer = "org.eclipse.ui.navigator.ProjectExplorer";
		navigatorLayout.addView(explorer);
		navigatorLayout.addView("uk.ac.diamond.sda.navigator.views.FileView");
		
		if (layout.getViewLayout(explorer) != null)
			layout.getViewLayout(explorer).setCloseable(false);

		IFolderLayout dataLayout = layout.createFolder("data", IPageLayout.RIGHT, 0.25f, editorArea);
		String plot = PlotView.ID + "DP";
		dataLayout.addView(plot);
		
		layout.addView(plot, IPageLayout.RIGHT, 0.25f, editorArea);
		if (layout.getViewLayout(plot) != null)
			layout.getViewLayout(plot).setCloseable(false);

		String sidePlot = SidePlotView.ID + ":Dataset Plot";
		layout.addView(sidePlot, IPageLayout.RIGHT, 0.60f, plot);
		if (layout.getViewLayout(sidePlot) != null)
			layout.getViewLayout(sidePlot).setCloseable(false);
		
		layout.addView("fable.imageviewer.views.HeaderView", IPageLayout.BOTTOM, 0.60f, sidePlot);

		String inspector = DatasetInspectorView.ID;
		layout.addStandaloneView(inspector, false, IPageLayout.BOTTOM, 0.60f, editorArea);
		if (layout.getViewLayout(inspector) != null)
			layout.getViewLayout(inspector).setCloseable(false);
		
	}

}
