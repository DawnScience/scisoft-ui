/*-
 * Copyright (c) 2018 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.rixs.rcp;

import org.eclipse.ui.IFolderLayout;
import org.eclipse.ui.IPageLayout;
import org.eclipse.ui.IPerspectiveFactory;
import org.eclipse.ui.IViewLayout;

public class PostRIXSPerspective implements IPerspectiveFactory {

	public final static String ID = "uk.ac.diamond.scisoft.rixs.PRPerspective";
	public final static String PLOT_NAME = "RIXS Plot";

	private final static String PLOT_ID = "uk.ac.diamond.scisoft.rixs.PRPlot";
	private final static String LOADED_FILE_ID = "uk.ac.diamond.scisoft.rixs.PRLoadedFilePart";

	@Override
	public void createInitialLayout(IPageLayout layout) {
		layout.setEditorAreaVisible(false);
		IFolderLayout folderLayout = layout.createFolder("folder", IPageLayout.LEFT, 0.2f, IPageLayout.ID_EDITOR_AREA);
		folderLayout.addView(LOADED_FILE_ID);
		IViewLayout vLayout = layout.getViewLayout(LOADED_FILE_ID);
		vLayout.setCloseable(false);

		folderLayout = layout.createFolder("folder_1", IPageLayout.LEFT, 0.7f, IPageLayout.ID_EDITOR_AREA);
		folderLayout.addView(PLOT_ID);
		vLayout = layout.getViewLayout(PLOT_ID);
		vLayout.setCloseable(false);

		folderLayout = layout.createFolder("folder_2", IPageLayout.RIGHT, 0.4f, IPageLayout.ID_EDITOR_AREA);
		String analyser = "uk.ac.diamond.scisoft.rixs.PRAggregator";
		folderLayout.addView(analyser);
		vLayout = layout.getViewLayout(analyser);
		vLayout.setCloseable(false);
	}
}
