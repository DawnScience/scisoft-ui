/*-
 * Copyright 2017 Diamond Light Source Ltd.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.ac.diamond.scisoft.analysis.rcp.hdf5;

import java.util.Arrays;

import org.eclipse.dawnsci.analysis.api.tree.DataNode;
import org.eclipse.dawnsci.analysis.api.tree.Node;
import org.eclipse.dawnsci.analysis.api.tree.NodeLink;
import org.eclipse.dawnsci.analysis.api.tree.Tree;
import org.eclipse.january.DatasetException;
import org.eclipse.january.dataset.DatasetUtils;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TreePath;
import org.eclipse.jface.viewers.TreeSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;

public class HDF5TreeDialog extends Dialog {

	private Tree tree;
	private String filename;

	protected HDF5TreeDialog(Shell parentShell, Tree tree, String filename) {
		super(parentShell);
		setShellStyle(SWT.MODELESS | SWT.CLOSE | SWT.BORDER | SWT.TITLE | SWT.RESIZE);

		this.tree = tree;
		this.filename = filename;
	}

	@Override
	public Control createDialogArea(Composite parent)  {
		getShell().setText(filename);
		Composite container = (Composite) super.createDialogArea(parent);
		container.setLayout(new FillLayout());
		SashForm sash = new SashForm(container, SWT.HORIZONTAL);
		HDF5TableTree tt = new HDF5TableTree(sash, null, null, null);
		tt.setFilename(filename);
		tt.setInput(tree.getNodeLink());

		Composite c = new Composite(sash, SWT.NONE);
		c.setLayout(new GridLayout());
		final StyledText l = new StyledText(c, SWT.WRAP | SWT.READ_ONLY | SWT.BORDER | SWT.V_SCROLL);
		l.setAlwaysShowScrollBars(false);
		l.setLayoutData(new GridData(GridData.FILL_BOTH));

		tt.getViewer().addSelectionChangedListener(new ISelectionChangedListener() {

			@Override
			public void selectionChanged(SelectionChangedEvent event) {
				ISelection selection = event.getSelection();

				StringBuilder b = new StringBuilder(event.getSelection().toString());

				if (selection instanceof TreeSelection && ((TreeSelection)selection).getFirstElement() instanceof NodeLink) {
					NodeLink n = (NodeLink)((TreeSelection)selection).getFirstElement();
					TreePath path = ((TreeSelection)selection).getPaths()[0];

					String fullPath = getStringFromPath(path);

					b.append(System.lineSeparator());
					b.append("Path: ");
					b.append(fullPath);

					Node d = n.getDestination();
					if (d instanceof DataNode) {
						DataNode dn = (DataNode)d;
						b.append(System.lineSeparator());
						b.append("Chunk Size: ");
						b.append(dn.getChunkShape() == null ? "Not chunked" : Arrays.toString(dn.getChunkShape()));
						b.append(System.lineSeparator());
						b.append("Max Size: ");
						b.append(dn.getMaxShape() == null ? "Not set" : Arrays.toString(dn.getMaxShape()));

						if (!dn.isString() && dn.getDataset().getSize() <= 128) {
							b.append(System.lineSeparator());
							b.append("Value: ");
							try {
								b.append(DatasetUtils.sliceAndConvertLazyDataset(dn.getDataset()).toString(true));
							} catch (DatasetException e) {
								b.append(e);
							}
						}
					}
				}

				l.setText(b.toString());
				l.getParent().layout();
			}
		});

		sash.setWeights(new int[] {70,30});

		return container;
	}

	private String getStringFromPath(TreePath path) {
		StringBuilder pathBuilder = new StringBuilder();
		for (int i = 0; i < path.getSegmentCount(); i++) {
			pathBuilder.append(Node.SEPARATOR);
			String segment = ((NodeLink)path.getSegment(i)).getName();
			pathBuilder.append(segment);
		}

		return pathBuilder.toString();
	}

	@Override
	protected Point getInitialSize() {
		Rectangle bounds = PlatformUI.getWorkbench().getWorkbenchWindows()[0].getShell().getBounds();
		return new Point((int)(bounds.width*0.8),(int)(bounds.height*0.8));
	}

	@Override
	protected boolean isResizable() {
		return true;
	}

	@Override
	protected void configureShell(Shell shell) {
		super.configureShell(shell);
		shell.setText("NeXus/HDF5 Tree");
	}

	@Override
	protected void createButtonsForButtonBar(Composite parent) {
		createButton(parent, IDialogConstants.CLOSE_ID, IDialogConstants.CLOSE_LABEL, true);
	}

	@Override
	protected void buttonPressed(int buttonId) {
		setReturnCode(buttonId);
		close();
	}
}
