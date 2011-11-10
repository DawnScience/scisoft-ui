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

package uk.ac.diamond.scisoft.analysis.rcp.editors;

import gda.analysis.io.ScanFileHolderException;

import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.PartInitException;

import uk.ac.diamond.scisoft.analysis.dataset.DoubleDataset;
import uk.ac.diamond.scisoft.analysis.io.DataHolder;
import uk.ac.diamond.scisoft.analysis.io.XasAsciiLoader;
import uk.ac.diamond.scisoft.analysis.rcp.inspector.DatasetSelection;

/**
 * Should act identically to the SRS Editor, but for tab separated data files with the Xas format header and footer.
 */
public class XasAsciiEditor extends SRSEditor {

	@Override
	public void init(IEditorSite site, IEditorInput input) throws PartInitException {
        setSite(site);
        setInput(input);
        
        getSite().setSelectionProvider(this);
        
        // now load the data
	    try {
	    	XasAsciiLoader dataLoader = new XasAsciiLoader(fileName);
	    	data = dataLoader.loadFile();
		} catch (ScanFileHolderException e) {
			data = new DataHolder();
			data.addDataset("Failed to load File", new DoubleDataset(1));
		}

		setSelection(new DatasetSelection()); // set up null selection to clear plot
	}
	
}
