/*-
 * Copyright © 2010 Diamond Light Source Ltd.
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

package uk.ac.diamond.scisoft.mappingexplorer.test;

import java.io.File;
import java.util.Arrays;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.IOverwriteQuery;
import org.eclipse.ui.internal.ide.filesystem.FileSystemStructureProvider;
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.ui.wizards.datatransfer.ImportOperation;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import uk.ac.diamond.scisoft.analysis.IOTestUtils;
import uk.ac.diamond.scisoft.mappingexplorer.views.twod.TwoDMappingView;

/**
 * Test the 2D mapping view Test class to test
 * 
 * <pre>
 * <li> Open the TwoDView with the 3D dataset for a given Nxs Tree editor by simulating the double click on the tree</li>
 * <li> Use the toolbar widget to open an independent view </li>
 * <li> Open the TwoDView with 2D dataset using the Nxs tree editor.</li>
 * <li> Using the toolbar widget on the 2D independent view -open the OneD view</li>
 * </pre>
 */
public class DisplayAxisValuesPluginTest {

	static final long MAX_TIMEOUT_MS = 500;

	/**
	 */
	@BeforeClass
	public static void setUpBeforeClass() {
	}

	/**
	 */
	@AfterClass
	public static void tearDownAfterClass() {
	}

	/**
	 */
	@Before
	public void setUp() {
	}

	/**
	 */
	@After
	public void tearDown() {

	}

	/**
	 * @throws Exception
	 */
	@Test
	public final void testShowView() throws Exception {
		final IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		// Create a project called 'Test Project' in the workspace
		IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject("TestProject");
		if (!project.exists()) {
			project.create(null);
		}
		project.open(null);
		// Import the file system that contains the nexus files into 'Test Project'
		ImportOperation op = new ImportOperation(project.getFullPath(), new FileSystemStructureProvider(),
				new IOverwriteQuery() {

					@Override
					public String queryOverwrite(String pathString) {
						return pathString;
					}
				}, Arrays.asList(new File[] { getSourceDirectory() }));

		op.setCreateContainerStructure(false);
		op.run(null);

		window.getActivePage().showView(TwoDMappingView.ID);

		// Get a handle to the file '2495.nxs' within the 'Test Project'
		IFile file = ResourcesPlugin.getWorkspace().getRoot().getProject("TestProject").getFile("1.nxs");
		window.getActivePage().openEditor(new FileEditorInput(file), "uk.ac.gda.client.excalibur.mapping.editor");

		PluginTestHelpers.delay(3000000);
	}

	private File getSourceDirectory() {
		String GDALargeTestFilesLocation = IOTestUtils.getGDALargeTestFilesLocation();

		return new File(GDALargeTestFilesLocation + "Hdf5HelperTest/1.nxs");
	}

}
