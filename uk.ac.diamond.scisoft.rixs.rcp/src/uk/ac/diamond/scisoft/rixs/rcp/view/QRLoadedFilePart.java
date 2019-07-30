package uk.ac.diamond.scisoft.rixs.rcp.view;

import org.dawnsci.datavis.view.parts.LoadedFilePart;

import uk.ac.diamond.scisoft.rixs.rcp.QuickRIXSPerspective;

public class QRLoadedFilePart extends LoadedFilePart {

	@Override
	protected String getPerspectiveID() {
		return QuickRIXSPerspective.ID;
	}
}
