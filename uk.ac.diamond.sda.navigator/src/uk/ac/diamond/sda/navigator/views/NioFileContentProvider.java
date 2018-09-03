/*-
 * Copyright 2015 Diamond Light Source Ltd.
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

package uk.ac.diamond.sda.navigator.views;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;

import org.eclipse.dawnsci.analysis.api.io.ILoaderService;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.sda.intro.navigator.NavigatorRCPActivator;
import uk.ac.diamond.sda.navigator.preference.FileNavigatorPreferenceConstants;
import uk.ac.diamond.sda.navigator.util.NIOUtils;

class NioFileContentProvider implements IFileContentProvider {
	
	private static final Logger logger = LoggerFactory.getLogger(NioFileContentProvider.class);

	private TreeViewer treeViewer;
	private FileSortType sort = FileSortType.ALPHA_NUMERIC_DIRS_FIRST;
	private boolean collapseDatacollections;
	
    private final Map<Path, List<Path>>      cachedFileList;
    private final Map<String, Path>          files;
    private final Map<String, Path>          dirs;
	private final Map<Path, Set<String>>     cachedStubs;
	
	public NioFileContentProvider() {
		
		this.cachedFileList = new HashMap<Path, List<Path>>(89);
		this.files = new TreeMap<String, Path>(new SortNatural<String>(false));
	    this.dirs  = new TreeMap<String, Path>(new SortNatural<String>(false));
		this.cachedStubs = new HashMap<Path, Set<String>>();
		
		final IPreferenceStore store = NavigatorRCPActivator.getDefault().getPreferenceStore();
		collapseDatacollections = store.getBoolean(FileNavigatorPreferenceConstants.SHOW_COLLAPSED_FILES);
	}

	@Override
	public void dispose() {
		// TODO Auto-generated method stub

	}

	@Override
	public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
		treeViewer = (TreeViewer) viewer;
		treeViewer.refresh();
	}


	@Override
	public void updateElement(Object parent, int index) {
		
		try {
			List<Path> paths = parent instanceof Path
					         ? getPaths((Path)parent)
					         : NIOUtils.getRoots();
			
			if (paths!=null) {
				Path path = paths.size()>index ? paths.get(index) : null;
				if (path!=null) {
					treeViewer.replace(parent, index, path);
					if (Files.isDirectory(path)) {
						updateChildCount(path, -1);
					}
				}
			}
		} catch (IOException e) {
			logger.error("Cannot get the files for "+parent, e);
		}			
	}


	@Override
	public void updateChildCount(Object element, int currentChildCount) {
	
	    int size = 0;
	    if (cachedFileList.containsKey(element)) {
	    	size = cachedFileList.get(element).size();
	    } else if (element instanceof Path) {
			Path path = (Path)element;
			if (Files.isDirectory(path)) {
				try {
					List<Path> paths = getPaths(path);
					size = paths!=null ? paths.size() : 0;
				} catch (IOException e) {
					logger.error("Cannot get paths for "+path, e);
				}
			}

		} else {
		    size = NIOUtils.getRoots().size();
		}
		treeViewer.setChildCount(element, size);
	}

	@Override
	public Object getParent(Object element) {
		if (element instanceof Path) {
			return ((Path)element).getParent();
		} 
		return null;

	}
	
	@Override
	public void clear(Path... paths) {
		if (paths==null) {
			if (cachedFileList!=null) cachedFileList.clear();
			if (cachedStubs!=null)    cachedStubs.clear();
		} else {
			for (int i = 0; i < paths.length; i++) {
				if (paths[i]==null) continue;
				cachedStubs.remove(paths[i]);
				if (Files.isDirectory(paths[i])) {
					removeCachedPath(paths[i].getParent());
				} else {
					cachedFileList.remove(paths[i].getParent());
				}
			}	
		}
	}

	private void removeCachedPath(Path delete) {
		cachedFileList.remove(delete);
		
		for (Path path : cachedFileList.keySet()) {
			if (path.startsWith(delete)) cachedFileList.remove(path);
		}
	}

	@Override
	public void clearAll() {
		clear();
	}

	public FileSortType getSort() {
		return sort;
	}

	@Override
	public void setSort(FileSortType sort) {
		clear();
		this.sort = sort;
	}

	public boolean isCollapseDatacollections() {
		return collapseDatacollections;
	}

	@Override
	public void setCollapseDatacollections(boolean collapseDatacollections) {
		this.collapseDatacollections = collapseDatacollections;
	}

	private List<Path> getPaths(Path parent) throws IOException {
		
		
		List<Path> paths = cachedFileList.get(parent);
		if (paths!=null) return paths;
		
		if (Files.isDirectory(parent)) {

			ILoaderService lservice=null;
			if (collapseDatacollections) {
				lservice = NavigatorRCPActivator.getService(ILoaderService.class);
			}

			// Faster way than File.list() in theory
			// see http://www.rgagnon.com/javadetails/java-get-directory-content-faster-with-many-files.html						
			try (DirectoryStream<Path> ds = Files.newDirectoryStream(parent)) {

				Set<String> tmp = null;

				if (collapseDatacollections) {
					tmp = new HashSet<String>(31);
					cachedStubs.put(parent, new HashSet<String>(31));
				}

				for (Path p : ds) {

					final boolean isDir = Files.isDirectory(p);
					final String  name  = p.getFileName().toString();

					if (!isDir) {
						if (lservice!=null) {
							Matcher matcher = lservice.getStackMatcher(name);
							if (matcher!=null && matcher.matches()) {
								String id = matcher.group(1);

								// If we already have an item for this scan:
								if (tmp!=null && tmp.contains(id)) {
									// We have more than one of them, so they get truncated
									Set<String> stubs = cachedStubs.get(parent);
									if (stubs!=null) {
										stubs.add(id);
										continue;
									}
								}

								// Otherwise allows its index to be added.
								if (tmp!=null) tmp.add(id);
							}
						}
						files.put(name, p);

					} else if (isDir && sort==FileSortType.ALPHA_NUMERIC_DIRS_FIRST) { // dirs separate
						dirs.put(name, p);
					} else {
						files.put(name, p);
					}
				}

				// We precache the directory contents now because we pared them down with the regexp
				paths = new ArrayList<Path>(files.size()+dirs.size());
				paths.addAll(dirs.values());
				paths.addAll(files.values());
				dirs.clear();
				files.clear();	
			} catch (java.nio.file.AccessDeniedException ne) {
				// We don't care about private dirs
				logger.debug("Private directory "+parent+" will be ignored.");
			}
		}

		cachedFileList.put(parent, paths);
		return paths;
	}

	@Override
	public Set<String> getStubs(Path parent) {
		return cachedStubs.get(parent);
	}

}
