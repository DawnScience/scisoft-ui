package uk.ac.diamond.optid;

import org.eclipse.ui.IFolderLayout;
import org.eclipse.ui.IPageLayout;
import org.eclipse.ui.IPerspectiveFactory;
import org.eclipse.ui.IViewLayout;

public class IdOptimisationPerspective implements IPerspectiveFactory {
	
	static final String ID = "uk.ac.diamond.optid.idOptimisationPerspective";

	@Override
	public void createInitialLayout(IPageLayout layout) {
		layout.setEditorAreaVisible(true);
		
		// ID Optimisation 'Main' view
		layout.addStandaloneView("uk.ac.diamond.optid.mainView", true, IPageLayout.LEFT, 0.25f, IPageLayout.ID_EDITOR_AREA);
		IViewLayout mainViewLayout = layout.getViewLayout("uk.ac.diamond.optid.mainView");
		mainViewLayout.setCloseable(false);
		mainViewLayout.setMoveable(false);
		
		// Data manipulation tools
		IFolderLayout rightFolder = layout.createFolder("rightFolder", IPageLayout.RIGHT, 0.6f, IPageLayout.ID_EDITOR_AREA);
		rightFolder.addView("org.dawb.workbench.views.dataSetView");
		rightFolder.addView("org.dawb.workbench.plotting.views.toolPageView.2D");
		rightFolder.addView("org.dawb.common.ui.views.e4.headerTableView");
		
		// Progress & Console views
		IFolderLayout bottomFolder = layout.createFolder("bottomFolder", IPageLayout.BOTTOM, 0.7f, IPageLayout.ID_EDITOR_AREA);
		bottomFolder.addView("org.eclipse.ui.views.ProgressView");
		bottomFolder.addView("org.eclipse.ui.console.ConsoleView");
		
		// Optimisation file generation forms
		IFolderLayout leftFolder = layout.createFolder("leftFolder", IPageLayout.LEFT, 0.4f, IPageLayout.ID_EDITOR_AREA);
		leftFolder.addPlaceholder("uk.ac.diamond.optid.idDescForm");
		leftFolder.addPlaceholder("uk.ac.diamond.optid.magStrForm");
		leftFolder.addPlaceholder("uk.ac.diamond.optid.lookupGenForm");
		
		// Genome view
		layout.addStandaloneView("uk.ac.diamond.optid.genomeView", true, IPageLayout.BOTTOM, 0.5f, "uk.ac.diamond.optid.mainView");

		IViewLayout idDescFormLayout = layout.getViewLayout("uk.ac.diamond.optid.idDescForm");
		idDescFormLayout.setCloseable(false);
		idDescFormLayout.setMoveable(false);

		IViewLayout magStrFormLayout = layout.getViewLayout("uk.ac.diamond.optid.magStrForm");
		magStrFormLayout.setCloseable(false);
		magStrFormLayout.setMoveable(false);
		
		IViewLayout lookupGenFormLayout = layout.getViewLayout("uk.ac.diamond.optid.lookupGenForm");
		lookupGenFormLayout.setCloseable(false);
		lookupGenFormLayout.setMoveable(false);
	}

}
