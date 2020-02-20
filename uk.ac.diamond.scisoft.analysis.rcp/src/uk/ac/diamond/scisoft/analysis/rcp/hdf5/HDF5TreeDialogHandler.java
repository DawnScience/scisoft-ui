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

import java.util.ArrayList;
import java.util.List;

import org.dawnsci.datavis.api.IDataFilePackage;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.dawnsci.analysis.api.tree.Tree;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.ui.ISelectionService;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.HandlerUtil;

public class HDF5TreeDialogHandler extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		List<IDataFilePackage> list = getSelectedFiles();
		if (!list.isEmpty()) {
			IDataFilePackage p = list.get(0);
			Tree tree = p.getTree();
			
			if (tree == null) return null;
			
			HDF5TreeDialog d = new HDF5TreeDialog(HandlerUtil.getActiveShell(event),tree, p.getFilePath());
			d.open();
		}
		
		
		return null;
	}

	@Override
	public void setEnabled(Object evaluationContext) {
		List<IDataFilePackage> list = getSelectedFiles();
		setBaseEnabled(!list.isEmpty() && list.get(0).getTree() != null);
	}

	private List<IDataFilePackage> getSelectedFiles() {
		
		ISelectionService  selectionService= PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
		ISelection selection = selectionService.getSelection("org.dawnsci.datavis.view.parts.LoadedFilePart");

		List<IDataFilePackage> list = new ArrayList<>();
		if (selection instanceof StructuredSelection) {
			Object[] array = ((StructuredSelection) selection).toArray();
			for (Object o : array) {
				if (o instanceof IDataFilePackage) {
					list.add((IDataFilePackage) o);
				}
			}
		}

		return list;
	}
}
