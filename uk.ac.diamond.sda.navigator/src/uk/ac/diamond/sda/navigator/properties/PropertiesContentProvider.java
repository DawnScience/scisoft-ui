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

package uk.ac.diamond.sda.navigator.properties;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.StructuredViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.ui.progress.UIJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides the properties contained in a *.properties file as children of that file in a Common Navigator.
 * 
 */
public class PropertiesContentProvider implements ITreeContentProvider, IResourceChangeListener, IResourceDeltaVisitor {

	private static final Logger logger = LoggerFactory.getLogger(PropertiesContentProvider.class);
	private static final Object[] NO_CHILDREN = new Object[0];
	private static final Object PROPERTIES_EXT = "properties"; //$NON-NLS-1$
	private final Map<IFile, PropertiesTreeData[]> cachedModelMap = new HashMap<IFile, PropertiesTreeData[]>();
	private StructuredViewer viewer;

	/**
	 * Create the PropertiesContentProvider instance. Adds the content provider as a resource change listener to track
	 * changes on disk.
	 */
	public PropertiesContentProvider() {
		ResourcesPlugin.getWorkspace().addResourceChangeListener(this, IResourceChangeEvent.POST_CHANGE);
	}

	/**
	 * Return the model elements for a *.properties IFile or NO_CHILDREN for otherwise.
	 */
	@Override
	public Object[] getChildren(Object parentElement) {
		Object[] children = null;
		if (parentElement instanceof PropertiesTreeData) {
			children = NO_CHILDREN;
		} else if (parentElement instanceof IFile) {
			/* possible model file */
			IFile modelFile = (IFile) parentElement;
			if (PROPERTIES_EXT.equals(modelFile.getFileExtension())) {
				children = cachedModelMap.get(modelFile);
				if (children == null && updateModel(modelFile) != null) {
					children = cachedModelMap.get(modelFile);
				}
			}
		}
		return children != null ? children : NO_CHILDREN;
	}

	/**
	 * Load the model from the given file, if possible.
	 * 
	 * @param modelFile
	 *            The IFile which contains the persisted model
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private synchronized Properties updateModel(IFile modelFile) {

		if (PROPERTIES_EXT.equals(modelFile.getFileExtension())) {
			Properties model = new Properties();
			if (modelFile.exists()) {
				try {
					model.load(modelFile.getContents());

					String propertyName;
					List properties = new ArrayList();
					for (Enumeration names = model.propertyNames(); names.hasMoreElements();) {
						propertyName = (String) names.nextElement();
						properties.add(new PropertiesTreeData(propertyName, model.getProperty(propertyName), modelFile));
					}
					PropertiesTreeData[] propertiesTreeData = (PropertiesTreeData[]) properties
							.toArray(new PropertiesTreeData[properties.size()]);

					cachedModelMap.put(modelFile, propertiesTreeData);
					return model;
				} catch (IOException e) {
				} catch (CoreException e) {
				}
			} else {
				cachedModelMap.remove(modelFile);
			}
		}
		return null;
	}

	@Override
	public Object getParent(Object element) {
		if (element instanceof PropertiesTreeData) {
			PropertiesTreeData data = (PropertiesTreeData) element;
			logger.debug(data.getFile().toString());
			return data.getFile();
		}
		return null;
	}

	@Override
	public boolean hasChildren(Object element) {
		if (element instanceof PropertiesTreeData) {
			return false;
		} else if (element instanceof IFile) {
			return PROPERTIES_EXT.equals(((IFile) element).getFileExtension());
		}
		return false;
	}

	@Override
	public Object[] getElements(Object inputElement) {
		return getChildren(inputElement);
	}

	@Override
	public void dispose() {
		cachedModelMap.clear();
		ResourcesPlugin.getWorkspace().removeResourceChangeListener(this);
	}

	@Override
	public void inputChanged(Viewer aViewer, Object oldInput, Object newInput) {
		if (oldInput != null && !oldInput.equals(newInput))
			cachedModelMap.clear();
		viewer = (StructuredViewer) aViewer;
	}

	/*
	 * (non-Javadoc)
	 * @see
	 * org.eclipse.core.resources.IResourceChangeListener#resourceChanged(org.eclipse.core.resources.IResourceChangeEvent
	 * )
	 */
	@Override
	public void resourceChanged(IResourceChangeEvent event) {

		IResourceDelta delta = event.getDelta();
		try {
			delta.accept(this);
		} catch (CoreException e) {
			e.printStackTrace();
		}
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.core.resources.IResourceDeltaVisitor#visit(org.eclipse.core.resources.IResourceDelta)
	 */
	@Override
	public boolean visit(IResourceDelta delta) {

		IResource source = delta.getResource();
		switch (source.getType()) {
		case IResource.ROOT:
		case IResource.PROJECT:
		case IResource.FOLDER:
			return true;
		case IResource.FILE:
			final IFile file = (IFile) source;
			if (PROPERTIES_EXT.equals(file.getFileExtension())) {
				updateModel(file);
				new UIJob("Update Properties Model in CommonViewer") { //$NON-NLS-1$
					@Override
					public IStatus runInUIThread(IProgressMonitor monitor) {
						if (viewer != null && !viewer.getControl().isDisposed())
							viewer.refresh(file);
						return Status.OK_STATUS;
					}
				}.schedule();
			}
			return false;
		}
		return false;
	}
}
