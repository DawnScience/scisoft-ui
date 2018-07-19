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
import org.eclipse.dawnsci.analysis.api.tree.GroupNode;
import org.eclipse.dawnsci.analysis.api.tree.NodeLink;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IViewReference;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.IOverwriteQuery;
import org.eclipse.ui.handlers.IHandlerService;
import org.eclipse.ui.internal.ide.filesystem.FileSystemStructureProvider;
import org.eclipse.ui.menus.CommandContributionItem;
import org.eclipse.ui.menus.CommandContributionItemParameter;
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.ui.wizards.datatransfer.ImportOperation;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.rcp.editors.HDF5TreeEditor;
import uk.ac.diamond.scisoft.analysis.rcp.hdf5.HDF5TableTree;
import uk.ac.diamond.scisoft.mappingexplorer.views.twod.TwoDMappingView;

/**
 * Test to ensure that the Colour mapping view exists and works in tandem with the TwoD mapping view.
 * 
 * @author rsr31645
 */
public class ColourMappingViewPluginTest {

	private static final Logger logger = LoggerFactory.getLogger(ColourMappingViewPluginTest.class);

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
		IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject("Test Project");
		logger.debug("Project Test project created {}", project);
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
		IFile file = ResourcesPlugin.getWorkspace().getRoot().getProject("Test Project").getFolder("mappingexptest")
				.getFile("2495.nxs");
		IEditorPart editor = window.getActivePage().openEditor(new FileEditorInput(file), HDF5TreeEditor.ID);

		PluginTestHelpers.delay(2000);
		if (editor instanceof HDF5TreeEditor) {
			HDF5TreeEditor hdf5treeed = (HDF5TreeEditor) editor;
			NodeLink link = getElement05(hdf5treeed);

			HDF5TableTree tableTree = hdf5treeed.getHDF5TreeExplorer().getTableTree();
			tableTree.expandAll();
			Event event = new Event();
			event.button = 1;
			event.display = Display.getCurrent();
			event.count = 2;
			event.time = (int) System.currentTimeMillis();
			event.widget = ((TreeViewer) tableTree.getViewer()).getTree();
			event.x = 82;
			event.y = 165;
			event.type = SWT.MouseDoubleClick;
			link = ((GroupNode) link.getDestination()).iterator().next();
			tableTree.setSelection(new StructuredSelection(link));
			// Simulate the mouse double click on the tree viewer in the HDF5TreeEditor
			((TreeViewer) tableTree.getViewer()).getTree().notifyListeners(SWT.MouseDoubleClick, event);
			PluginTestHelpers.delay(1000);

			IViewReference[] viewReferences = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage()
					.getViewReferences();
			for (IViewReference vr : viewReferences) {
				if (vr.getId().equals(TwoDMappingView.ID)) {
					IViewPart view = vr.getView(false);

					PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().activate(view);
					CommandContributionItem item = (CommandContributionItem) view.getViewSite().getActionBars()
							.getToolBarManager().find("uk.ac.diamond.scisoft.mappingexplorer.colourmappingview.open");

					CommandContributionItemParameter data = item.getData();
					IHandlerService service = data.serviceLocator.getService(IHandlerService.class);
					service.executeCommand("uk.ac.diamond.scisoft.mappingexplorer.colourmappingview.open", null);
					break;

				}
			}
			PluginTestHelpers.delay(1000);

		}

		PluginTestHelpers.delay(3000000);
	}

	/**
	 * @param hdf5treeed
	 * @return NodeLink
	 */
	protected NodeLink getElement05(HDF5TreeEditor hdf5treeed) {
		return hdf5treeed.getHDF5Tree().findNodeLink("/entry1/EDXD_Element_05");
	}

	/**
	 * @param hdf5treeed
	 * @return NodeLink
	 */
	protected NodeLink getElement05Duma(HDF5TreeEditor hdf5treeed) {
		return hdf5treeed.getHDF5Tree().findNodeLink("/entry1/EDXD_Element_05/dum_a");
	}

	private File getSourceDirectory() {
		return new File(
				"/scratch/excalibur1/plugins/uk.ac.diamond.scisoft.mappingexplorer.test/testFiles/mappingexptest");
	}

}
