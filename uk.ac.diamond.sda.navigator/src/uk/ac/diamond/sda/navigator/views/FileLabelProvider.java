/*
 * Copyright (c) 2012 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.sda.navigator.views;

import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

import org.dawb.common.util.io.FileUtils;
import org.eclipse.dawnsci.analysis.api.io.IDataHolder;
import org.eclipse.dawnsci.analysis.api.io.ILoaderService;
import org.eclipse.dawnsci.analysis.api.tree.GroupNode;
import org.eclipse.dawnsci.analysis.api.tree.Tree;
import org.eclipse.dawnsci.hdf5.HDF5Utils;
import org.eclipse.dawnsci.plotting.api.image.IFileIconService;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.StructuredViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;

import uk.ac.diamond.osgi.services.ServiceProvider;
import uk.ac.diamond.scisoft.analysis.utils.OSUtils;
import uk.ac.diamond.sda.intro.navigator.NavigatorRCPActivator;
import uk.ac.diamond.sda.navigator.preference.FileNavigatorPreferenceConstants;
import uk.ac.diamond.sda.navigator.util.NIOUtils;
import uk.ac.diamond.sda.navigator.util.NavigatorUtils;

class FileLabelProvider extends ColumnLabelProvider {

	private int columnIndex;
	private SimpleDateFormat dateFormat;
	private IFileIconService service;
	private IPreferenceStore store;
	private StructuredViewer viewer;
	private boolean          showCollapsedFiles;
	private IPropertyChangeListener propertyListenner;

	public FileLabelProvider(StructuredViewer viewer, final int column) {
		
		this.viewer      = viewer;
		this.columnIndex = column;
		
		this.dateFormat  = new SimpleDateFormat("dd/MM/yyyy HH:mm");
		service = NavigatorRCPActivator.getService(IFileIconService.class);
		this.store       =  NavigatorRCPActivator.getDefault().getPreferenceStore();
		
        this.showCollapsedFiles = store.getBoolean(FileNavigatorPreferenceConstants.SHOW_COLLAPSED_FILES);
        
        this.propertyListenner = new IPropertyChangeListener() {
			@Override
			public void propertyChange(PropertyChangeEvent event) {
				if (FileNavigatorPreferenceConstants.SHOW_COLLAPSED_FILES.equals(event.getProperty())) {
			        showCollapsedFiles = store.getBoolean(FileNavigatorPreferenceConstants.SHOW_COLLAPSED_FILES);
				}
			}
        };
        
        store.addPropertyChangeListener(propertyListenner);

	}

	@Override
	public Color getForeground(Object element) {
		if (columnIndex==0) return null;
		return Display.getCurrent().getSystemColor(SWT.COLOR_DARK_GRAY);
	}
	
	@Override
	public Image getImage(Object element) {
		
		if (element instanceof String) return null;
		final Path node   = (Path)element;
	
		switch(columnIndex) {
		case 0:
			try {
  			    return service.getIconForFile(node.toAbsolutePath().toString());
			} catch (Throwable ne) {
				return null;
			}

        default:
        	return null;
		}
	}

	/**
	 * { "Name", "Class", "Dims", "Type", "Size" };
	 */
	@Override
	public String getText(Object element) {
		

		if (element instanceof String) return (String)element;
		final Path node   = (Path)element;


		try {
			// Reading preferences = fast!
			// They can be changed by the user so 
			boolean showComment = store.getBoolean(FileNavigatorPreferenceConstants.SHOW_COMMENT_COLUMN);
			boolean showScanCmd = store.getBoolean(FileNavigatorPreferenceConstants.SHOW_SCANCMD_COLUMN);
			
			// Only read attributes if we need them!
			Map<Integer, String> attr = showComment||showScanCmd ? getH5Attributes(node) : null;
	
			String ret;
			switch(columnIndex) {
			case 0:
				String name = NIOUtils.getRoots().contains(node)
				            ? getRootLabel(node)
				            : getName(node);
				ret = name;
				break;
			case 1:
				ret = dateFormat.format(Files.getLastModifiedTime(node).toMillis());
				break;
			case 2:
				ret = Files.isDirectory(node) ? "Directory" : FileUtils.getFileExtension(node.getFileName().toString());
			    break;
			case 3:
				ret = formatSize(Files.size(node));
				break;
			case 4:
				ret = attr!=null&&showComment ? attr.get(4) : null;
				break;
			case 5:
				ret = attr!=null&&showScanCmd ? attr.get(5) : null;
				break;
			default:
				ret = null;
			
			}
			
			if (ret!=null && ret.startsWith("(\\\\Data.diamond.ac.uk\\")) {
				ret = ret.substring("(\\\\Data.diamond.ac.uk\\".length(), ret.length()-1);
			}
			return ret;
			
		} catch (Exception ne) {
			return ne.getMessage();
		}
	}
	
	private ILoaderService lservice;
	/**
	 * Gets a name, allowing for those folders that have been compressed.
	 * @param node
	 * @return name
	 */
	private String getName(Path node) {
		
		final String name = node.getFileName().toString();
	    if (!showCollapsedFiles)     return name;
		if (Files.isDirectory(node)) return name;
			
 
        IFileContentProvider prov = viewer.getContentProvider() instanceof IFileContentProvider
					             ? (IFileContentProvider)viewer.getContentProvider()
					             : null;	
		if (prov == null) return name;
		        	
        final Set<String> stubs = prov.getStubs(node.getParent()); // TODO Allow for collapsed data collections.
        if (stubs==null || stubs.isEmpty()) return name;

        if (lservice==null) lservice = NavigatorRCPActivator.getService(ILoaderService.class);
        final Matcher matcher = lservice.getStackMatcher(name);

        if (matcher!=null && matcher.matches()) {
        	final String stub = matcher.group(1);
        	final String ext  = matcher.group(3);
        	if (stubs.contains(stub)) return stub+"_*."+ext;
        }
	    
		return name;

	}

	private Map<Path, Map<Integer, String>> attributes;

	private Map<Integer, String> getH5Attributes(Path node) throws Exception {
		
		if (Files.isDirectory(node))          return null;
		if (!HDF5Utils.isHDF5(node.toAbsolutePath().toString())) return null;
		
		if (attributes==null) attributes = new HashMap<Path, Map<Integer, String>>(89);
		if (attributes.containsKey(node)) return attributes.get(node);

		IDataHolder dh = ServiceProvider.getService(ILoaderService.class).getData(node.toAbsolutePath().toString(), null);
		Tree tree = dh.getTree();
		GroupNode rootnode = tree.getGroupNode();
		final Map<Integer, String> attr = new HashMap<Integer, String>(3);
		attributes.put(node, attr);

		String comment;
		try {
			comment = NavigatorUtils.getHDF5Title(node.toAbsolutePath().toString(), rootnode);
		} catch (Exception e) {
			comment = "N/A";
		}
		attr.put(4, comment);

		String scanCmd;
		try {
			scanCmd = NavigatorUtils.getHDF5ScanCommand(node.toAbsolutePath().toString(), rootnode);
		} catch (Exception e) {
			e.printStackTrace();
			scanCmd = "N/A";
		}
		attr.put(5, scanCmd);

		return attr;
	}

	@Override
	public void dispose() {
		super.dispose();
	    if (store!=null)      store.removePropertyChangeListener(propertyListenner);
		if (attributes!=null) attributes.clear();
	}


	private String getRootLabel(Path node) {
    	if (OSUtils.isWindowsOS()) {
    		return	"("+node.toAbsolutePath().toString().substring(0, node.toAbsolutePath().toString().length()-1)+")";
    	}
		return "/";
    }
 
	private static final double BASE = 1024, KB = BASE, MB = KB*BASE, GB = MB*BASE;
    private static final DecimalFormat df = new DecimalFormat("#.##");

    public static String formatSize(long size) {
        if(size >= GB) {
            return df.format(size/GB) + " GB";
        }
        if(size >= MB) {
            return df.format(size/MB) + " MB";
        }
        if(size >= KB) {
            return df.format(size/KB) + " KB";
        }
        return "" + (int)size + " bytes";
    }
}
