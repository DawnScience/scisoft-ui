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
import org.eclipse.jface.action.ActionContributionItem;
import org.eclipse.jface.action.IContributionItem;
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
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.ui.wizards.datatransfer.ImportOperation;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import uk.ac.diamond.scisoft.analysis.rcp.editors.HDF5TreeEditor;
import uk.ac.diamond.scisoft.analysis.rcp.hdf5.HDF5TableTree;
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
public class TwoDSecondaryOpenOneDPluginTest {

	private static final String ONED_COMMAND_ID = "uk.ac.diamond.scisoft.mappingexplorer.openonedview";
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
			boolean exectued = false;
			for (IViewReference vr : viewReferences) {
				if (vr.getId().equals(TwoDMappingView.ID)) {
					IViewPart view = vr.getView(false);

					PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().activate(view);
					IContributionItem[] items = view.getViewSite().getActionBars().getToolBarManager().getItems();
					for (IContributionItem iContributionItem : items) {
						if (iContributionItem instanceof ActionContributionItem) {
							ActionContributionItem act = (ActionContributionItem) iContributionItem;
							Event ev = new Event();
							ev.widget = act.getWidget();
							ev.type = SWT.Selection;
							ev.time = (int) System.currentTimeMillis();
							// Open the TwoD Independent view using the toolbar widget.
							act.getAction().runWithEvent(ev);
							exectued = true;
							break;
						}
					}
					if (exectued) {
						break;
					}
				}
			}
			PluginTestHelpers.delay(1000);
			// Open the 2D Dataset in the two d view using the mouse double click simulation
			PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().activate(editor);
			NodeLink link1 = getElement05Duma(hdf5treeed);
			tableTree.setSelection(new StructuredSelection(link1));
			((TreeViewer) tableTree.getViewer()).getTree().notifyListeners(SWT.MouseDoubleClick, event);
			PluginTestHelpers.delay(1000);

			IViewPart vf2 = null;
			for (IViewReference vf : PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage()
					.getViewReferences()) {
				if (vf.getId().equals(TwoDMappingView.ID) && vf.getSecondaryId() != null) {
					vf2 = vf.getView(true);
					break;
				}
			}
			// Open the One D view from the 2D independent view.
			if (vf2 != null) {
				PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().activate(vf2);

				IContributionItem[] items = vf2.getViewSite().getActionBars().getToolBarManager().getItems();

				for (IContributionItem iContributionItem : items) {
					if (iContributionItem.getId().equals(ONED_COMMAND_ID)) {
						IHandlerService service = vf2.getSite().getService(IHandlerService.class);
						service.executeCommand(ONED_COMMAND_ID, null);
					}
				}
			}
		}

		PluginTestHelpers.delay(3000000);
	}

	/**
	 * @param hdf5treeed
	 * @return NodeLink
	 * @throws InterruptedException
	 */
	protected NodeLink getElement05(HDF5TreeEditor hdf5treeed) throws InterruptedException {
		Thread.sleep(500);
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
