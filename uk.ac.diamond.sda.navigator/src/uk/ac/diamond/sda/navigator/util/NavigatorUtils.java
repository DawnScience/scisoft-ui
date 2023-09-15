/*
 * Copyright (c) 2012 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.sda.navigator.util;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.dawb.common.util.io.FileUtils;
import org.eclipse.dawnsci.analysis.api.io.IDataHolder;
import org.eclipse.dawnsci.analysis.api.io.ILoaderService;
import org.eclipse.dawnsci.analysis.api.tree.DataNode;
import org.eclipse.dawnsci.analysis.api.tree.GroupNode;
import org.eclipse.dawnsci.analysis.api.tree.NodeLink;
import org.eclipse.dawnsci.analysis.api.tree.Tree;
import org.eclipse.january.metadata.IExtendedMetadata;
import org.eclipse.january.metadata.IMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.osgi.services.ServiceProvider;
import uk.ac.diamond.scisoft.analysis.io.LoaderFactory;

public class NavigatorUtils {

	private static final String scanCmdName = "scan_command";
	private static final String titleName = "title";

	private static final Logger logger = LoggerFactory.getLogger(NavigatorUtils.class);

	/**
	 * Method that returns a Scan Command given a .nxs, .hdf5, .srs, .dat file
	 * @param file
	 * @return a Scan Command as a String
	 */
	public static String getScanCommand(File file) {
		String extension = FileUtils.getFileExtension(file);
		if(extension.equals("dat") 
				|| extension.equals("srs")) 
			return getASCIIScanCommand(file);
		else if(extension.equals("hdf5") 
				|| extension.equals("hdf")
				|| extension.equals("h5")
				|| extension.equals("nxs")) 
			return getHDF5ScanCommand(file.getAbsolutePath());
		else return "";
	}

	/**
	 * Method that returns a Scan Command given a particular file<br>
	 * Files with .dat and .srs extensions are supported. So are .nxs files.<br>
	 * @param file
	 * @return a String value of the scan command
	 */
	public static String getASCIIScanCommand(File file) {
		// make it work just for srs files
		if(!FileUtils.getFileExtension(file).equals("dat") && !FileUtils.getFileExtension(file).equals("srs")) return "";
		String result = "N/A";
		IExtendedMetadata metaData = null;
		try {
			IMetadata metaDataTest=LoaderFactory.getMetadata(file.getAbsolutePath(), null);
			if(metaDataTest instanceof IExtendedMetadata){
				metaData = (IExtendedMetadata)LoaderFactory.getMetadata(file.getAbsolutePath(), null);
				if(metaData == null) return result;
				result = metaData.getScanCommand();
			}
		} catch (Exception ne) {
			ne.printStackTrace();
			return result;
		}
		return result;
	}

	/**
	 * Method that returns a matrix of String with scan commands and titles given the full path to a Nexus file.<br>
	 * Make sure that the file path is the one of a Nexus file.
	 * @param fullpath
	 * @return a String array of Scan commands and Titles
	 */
	public static String[][] getHDF5TitlesAndScanCmds(String fullpath) {

		String[][] results = {{""}, {""}};
		List<String> scans = new ArrayList<String>();
		List<String> titles = new ArrayList<String>();

		IDataHolder dh = null;
		try {
			dh = ServiceProvider.getService(ILoaderService.class).getData(fullpath, null);
		} catch (Exception e) {
			logger.debug("HDF5 file Reading Exception:", e);
			return results;
		}
		if (dh == null)
			return results;
		Tree tree = dh.getTree();
		GroupNode rootnode = tree.getGroupNode();
		Iterator<String> iterator = rootnode.getNodeNameIterator();
		while (iterator.hasNext()) {
			String entryName = iterator.next();
			entryName = !entryName.startsWith("/") ? "/" + entryName : entryName;
			try {
				//read scan command
				NodeLink scanCmdLink = rootnode.findNodeLink(entryName + "/" + scanCmdName);
				DataNode scanCmdData = scanCmdLink != null ? (DataNode)scanCmdLink.getDestination(): null;
				if (scanCmdData != null) {
					String scanstr = scanCmdData.getString();
					scans.add(scanstr);
					if (scans.size() > titles.size() + 1)
						titles.add(null); // bulk out list
				}

				// read title
				NodeLink titleLink = rootnode.findNodeLink(entryName + "/" + titleName);
				DataNode titleData = titleLink != null ? (DataNode) titleLink.getDestination() : null;
				if (titleData != null) {
					String titlestr = titleData.getString();
					titles.add(titlestr);
					if (titles.size() > scans.size() + 1)
						scans.add(null);
				}
			} catch (Exception e) {
				logger.debug("Error getting the data from the HDF5 tree:", e);
				return results;
			}
		}

		int s = scans.size();
		int t = titles.size();
		if (s != t) {
			// correct size of lists
//			logger.warn("Scans and titles not in sync!");
			while (s < t) {
				scans.add(null);
				s++;
			}
			while (t < s) {
				titles.add(null);
				t++;
			}
		}

		results = new String[2][s];
		for (int i = 0; i < s; i++) {
			String str = titles.get(i);
			if (str != null && str.length() > 100) { // restrict to 100 characters
				str = str.substring(0,  100) + "...";
			}
			if(i==0)
				results[0][i] = str == null ? "" : " * Title" + (i+1) + ": " + str;
			else
				results[0][i] = str == null ? "" : System.getProperty("line.separator")+"Title" + (i+1) + ": " + str;
			str = scans.get(i);
			if (str != null && str.length() > 100) { // restrict to 100 characters
				str = str.substring(0,  100) + "...";
			}
			results[1][i] = str == null ? "" : System.getProperty("line.separator")+"ScanCmd" + (i+1) + ": " + str;
		}
		return results;
	}

	/**
	 * Method that returns of the Scan Command of the nxs file being looked at.<br>
	 * If there are more than one scan command, it returns the first one<br>
	 * @param fullpath
	 * @return the Scan command as a String 
	 */
	public static String getHDF5ScanCommand(String fullpath) {
		return getHDF5ScanCommandOrTitle(fullpath, scanCmdName, null);
	}

	/**
	 * Method that returns of the Scan Command of the nxs file being looked at.<br>
	 * If there are more than one scan command, it returns the first one<br>
	 * @param fullpath
	 * @param rootnode 
	 * @return the Scan command as a String 
	 */
	public static String getHDF5ScanCommand(String fullpath, GroupNode rootnode) {
		return getHDF5ScanCommandOrTitle(fullpath, scanCmdName, rootnode);
	}

	public static String getHDF5Title(String fullpath) {
		return getHDF5ScanCommandOrTitle(fullpath, titleName, null);
	}
	/**
	 * Method that returns the title of the nxs file being looked at.<br>
	 * If there are more than one title, it returns the first one<br>
	 * @param fullpath
	 * @param rootnode 
	 * @return a String 
	 */
	public static String getHDF5Title(String fullpath, GroupNode rootnode) {
		return getHDF5ScanCommandOrTitle(fullpath, titleName, rootnode);
	}

	private static String getHDF5ScanCommandOrTitle(String fullpath, String type, GroupNode rootnode) {
		// make it work just for nxs and hdf5 files
		if(!fullpath.endsWith(".hdf5") 
				&& !fullpath.endsWith(".hdf")
				&& !fullpath.endsWith(".h5")
				&& !fullpath.endsWith(".nxs")) return "";

		String result = "N/A";
		List<String> comments = new ArrayList<String>();

		if (rootnode == null)
			return result;
		
		Iterator<String> iterator = rootnode.getNodeNameIterator();
		if (iterator.hasNext()) {
			String entryName = iterator.next();
			DataNode scanCmdData = rootnode.getDataNode(entryName + "/" + type);
			if (scanCmdData != null) {
				String scanstr = scanCmdData.getString();
				if (scanstr != null)
					comments.add(scanstr);
			}
		} else {
			return result;
		}
//		Enumeration<?> rootEntries = rootnode.getNode().children();
//		if (!rootEntries.hasMoreElements())
//			return result;
//		Object elem = rootEntries.nextElement();
//		TreeNode node = (TreeNode) elem;
//		String entryName = node.toString();
//
//		// read scan command /title
//		Dataset commentData;
//		try {
//			commentData = (Dataset) rootnode.getData(entryName + "/" + type);
//			if (commentData != null) {
//				Object val = commentData.read();
//				String[] comment = (String[]) val;
//				comments.add(comment[0]);
//			}
//		} catch (Exception e) {
//			logger.error("Error getting data from HDF5 tree:", e);
//			return "";
//		}


		int s = comments.size();
		if (s > 0) {
			String str = comments.get(0);
			if (str != null && str.length() > 100) { // restrict to 100 characters
				str = str.substring(0,  100) + "...";
			}
			result = str == null ? result : str;
		}
		return result;
	}

}
