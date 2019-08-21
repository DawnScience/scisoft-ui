package uk.ac.diamond.scisoft.rixs.rcp.view;

import javax.inject.Inject;

import org.dawnsci.datavis.view.parts.LoadedFilePart;

import uk.ac.diamond.scisoft.rixs.rcp.PostRIXSPerspective;

public class PRLoadedFilePart extends LoadedFilePart {

	@Inject
	protected void setPRFileController(IPRFileController fileController) {
		setFileController(fileController);
	}

	@Override
	protected String getPerspectiveID() {
		return PostRIXSPerspective.ID;
	}
}
