package uk.ac.diamond.scisoft.rixs.rcp.view;

import org.dawnsci.datavis.view.parts.LoadedFilePart;

import uk.ac.diamond.scisoft.rixs.rcp.PostRIXSPerspective;

public class PRLoadedFilePart extends LoadedFilePart {

	@Override
	protected String getPerspectiveID() {
		return PostRIXSPerspective.ID;
	}
}
