/*
 * Copyright 2012 Diamond Light Source Ltd.
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

package uk.ac.diamond.scisoft.customprojects.rcp.projects;


import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IPageLayout;
import org.eclipse.ui.IViewReference;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.navigator.resources.ProjectExplorer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class HideDeepProjects extends ViewerFilter {

	private static Logger logger = LoggerFactory
			.getLogger(HideDeepProjects.class);
	
	static final String SINGLE_LEVEL_NATURE = uk.ac.diamond.scisoft.customprojects.rcp.natures.SingleLevelProjectNature.NATURE_ID;
	
	
	@Override
	public boolean select(Viewer viewer, Object parentElement, Object element) {

		if(element instanceof IResource){
						
			boolean isTopProjectSingleLevel = false;
			try {
				if(((IResource)element).getProject().isAccessible()){
				isTopProjectSingleLevel = ((IResource)element).getProject().hasNature(SINGLE_LEVEL_NATURE);
				}
			} catch (CoreException e1) {
				e1.printStackTrace();
			}
			

			if  (isTopProjectSingleLevel){

				if (!(((IResource)element) instanceof IFile) && ((IResource)element).getParent().isLinked()){

					return false;
				}

			}// is topProjectSingleLevel
		}
		

		return true;
	}
	

	private void refreshProjectExplorer(IResource itemToExpand) {
		
		ProjectExplorer projectExplorer = (ProjectExplorer)PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().findView(IPageLayout.ID_PROJECT_EXPLORER);
		if (projectExplorer!=null) {
			projectExplorer.getCommonViewer().expandToLevel(itemToExpand, IResource.DEPTH_ONE);//.expandAll();
			projectExplorer.getCommonViewer().refresh(true);
		}
	}

	
}
