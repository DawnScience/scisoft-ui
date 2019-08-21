package uk.ac.diamond.scisoft.rixs.rcp.view;

import javax.inject.Inject;

import org.dawnsci.datavis.view.parts.LoadedFilePart;

import uk.ac.diamond.scisoft.rixs.rcp.QuickRIXSPerspective;

public class QRLoadedFilePart extends LoadedFilePart {

	@Inject
	protected void setQRFileController(IQRFileController fileController) {
		// this is called after the superclass gets its fields injected
		// so will override the standard one
		setFileController(fileController);
	}

	@Override
	protected String getPerspectiveID() {
		return QuickRIXSPerspective.ID;
	}
}
