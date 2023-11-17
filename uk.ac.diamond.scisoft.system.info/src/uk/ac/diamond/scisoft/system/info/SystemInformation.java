/*
 * Copyright (c) 2012 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.system.info;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.HashMap;

import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;

import uk.ac.diamond.scisoft.analysis.crystallography.VersionUtils;

/**
 * SystemInformation is a static class that holds all the current System
 * information of the system running the SDA Workbench. At least all the
 * information it can gather easily enough
 */
public class SystemInformation {

	private static HashMap<String, String> systemInfo = null;

	public static final String NUMCPUS = "NUMCPUS";
	public static final String JAVAVERSION = "JAVAVERSION";
	public static final String JAVAVMNAME = "JAVAVMNAME";
	public static final String TOTALMEMORY = "TOTALMEMORY";
	public static final String OSNAME = "OSNAME";
	public static final String OSVERSION = "OSVERSION";
	public static final String OSARCH = "OSARCH";
	public static final String SUPPORTOPENGL = "SUPPORTOPENGL";
	public static final String SUPPORTGLSL = "SUPPORTGLSL";
	public static final String GPUVENDOR = "GPUVENDOR";
	public static final String MAXTEXDIM = "MAXTEXDIM";

	private static final String GREEN = "green";
	private static final String YELLOW = "yellow";
	private static final String RED = "red";

	private static final String PASSED = "PASSED";

	private static final String MIN_JAVA_VERSION= "1.6";
	private static final int KIBI = 1024;

	public static void initialize() {
		systemInfo = new HashMap<>();
		Runtime runtime = Runtime.getRuntime();
		systemInfo.put(OSNAME, System.getProperty("os.name"));
		systemInfo.put(OSVERSION, System.getProperty("os.version"));
		systemInfo.put(JAVAVERSION, System.getProperty("java.version"));
		systemInfo.put(JAVAVMNAME, System.getProperty("java.vm.name"));
		systemInfo.put(OSARCH, System.getProperty("os.arch"));
		systemInfo.put(TOTALMEMORY, "" + runtime.maxMemory());
		systemInfo.put(NUMCPUS, "" + runtime.availableProcessors());
	}

	public static void writeInfo(GC gc, int xpos, Color red, Color green, Color yellow) {

		if (systemInfo.get(SUPPORTOPENGL) != null) {
			boolean hasJOGL = Boolean.parseBoolean(systemInfo.get(SUPPORTOPENGL));
			if (hasJOGL) {
				gc.setForeground(green);
				gc.drawText(PASSED, xpos, 30);
			} else {
				gc.setForeground(red);
				gc.drawText("FAILED: Please upgrade your system to an OpenGL1.4 compatible graphics card", xpos, 30);
			}
		}
		if (systemInfo.get(SUPPORTGLSL) != null) {
			boolean hasJOGLshaders = Boolean.parseBoolean(systemInfo.get(SUPPORTGLSL));
			if (hasJOGLshaders) {
				gc.setForeground(green);
				gc.drawText(PASSED, xpos, 50);
			} else {
				gc.setForeground(red);
				gc.drawText("FAILED: GLSL isn't mandatory but recommended some features need it!", xpos, 50);
			}
		}
		float mBytes = Runtime.getRuntime().maxMemory() / (KIBI * KIBI);
		if (mBytes < 300) {
			gc.setForeground(red);
		} else if (mBytes < KIBI) {
			gc.setForeground(yellow);
		} else
			gc.setForeground(green);
		String outputStr = "" + mBytes + "MB";
		if (mBytes < KIBI) {
			outputStr += " We recommend at least " + KIBI + "MB of Java heapspace";
		}
		gc.drawText(outputStr, xpos, 70);
		if (Runtime.getRuntime().availableProcessors() < 2) {
			gc.setForeground(yellow);
		} else
			gc.setForeground(green);
		gc.drawText("" + Runtime.getRuntime().availableProcessors(), xpos, 90);

		String javaVersionStr = systemInfo.get(JAVAVERSION);
		if (javaVersionStr.indexOf('.') >= 0) {
			if (VersionUtils.isOldVersion(MIN_JAVA_VERSION, javaVersionStr)) {
				gc.setForeground(red);
				outputStr += " You need Java " + MIN_JAVA_VERSION;
			} else {
				gc.setForeground(green);
			}
		} else {
			gc.setForeground(red);
		}

		gc.drawText(outputStr, xpos, 110);

	}

	/**
	 * Method to generate content for a system information check (used a HTML
	 * content provider)
	 * 
	 * @param systemInfoCheck SystemInformation static fields
	 * @return outputStr[] a String array with 2 columns ([0]= style, [1]=
	 * content)
	 */
	public static String[] writeHTMLInfo(String systemInfoCheck) {
		String[] outputStr = { "", "" };
		if (systemInfoCheck.equals(SUPPORTOPENGL)) {
			if (systemInfo.get(SUPPORTOPENGL) != null) {
				boolean hasJOGL = Boolean.parseBoolean(systemInfo.get(SUPPORTOPENGL));
				if (hasJOGL) {
					outputStr[0] = GREEN;
					outputStr[1] = PASSED;
				} else {
					outputStr[0] = RED;
					outputStr[1] = "FAILED: Please upgrade your system to a OpenGL1.4 compatible graphics card";
				}
			}
		} else if (systemInfoCheck.equals(SUPPORTGLSL)) {
			if (systemInfo.get(SUPPORTGLSL) != null) {
				boolean hasJOGLshaders = Boolean.parseBoolean(systemInfo.get(SUPPORTGLSL));
				if (hasJOGLshaders) {
					outputStr[0] = GREEN;
					outputStr[1] = PASSED;
				} else {
					outputStr[0] = YELLOW;
					outputStr[1] = "FAILED: GLSL isn't mandatory but recommended some features need it!";
				}
			}
		} else if (systemInfoCheck.equals(NUMCPUS)) {
			if (Runtime.getRuntime().availableProcessors() < 2) {
				outputStr[0] = YELLOW;
			} else
				outputStr[0] = GREEN;
			outputStr[1] = "" + Runtime.getRuntime().availableProcessors();

		} else if (systemInfoCheck.equals(JAVAVERSION)) {
			String javaVersionStr = systemInfo.get(JAVAVERSION);
			
			if (javaVersionStr.indexOf('.') >= 0) {
				if (VersionUtils.isOldVersion(MIN_JAVA_VERSION, javaVersionStr)) {
					outputStr[0] = RED;
					outputStr[1] += " You need Java " + MIN_JAVA_VERSION;
				} else {
					outputStr[0] = GREEN;
				}
			} else {
				outputStr[0] = RED;
			}
		} else if (systemInfoCheck.equals(TOTALMEMORY)) {
			float mBytes = Runtime.getRuntime().maxMemory() / (KIBI * KIBI);
			if (mBytes < 300) {
				outputStr[0] = RED;
			} else if (mBytes < KIBI) {
				outputStr[0] = YELLOW;
			} else
				outputStr[0] = GREEN;
			outputStr[1] = "" + mBytes + "MB";
			if (mBytes < 1024) {
				outputStr[1] += " We recommend at least " + KIBI + "MB of Java heapspace";
			}
		}

		return outputStr;
	}

	public static void setOpenGLVendor(String vendor) {
		systemInfo.put(GPUVENDOR, vendor);
	}

	public static String getOpenGLVendor() {
		return systemInfo.get(GPUVENDOR);
	}

	public static void setOpenGLMaxTex(int maxTexdim) {
		systemInfo.put(MAXTEXDIM, "" + maxTexdim);
	}

	public static void setOpenGLSupport(boolean support) {
		systemInfo.put(SUPPORTOPENGL, "" + support);
	}

	public static void setGLSLSupport(boolean support) {
		systemInfo.put(SUPPORTGLSL, "" + support);
	}

	public static String getGPUVendor() {
		return systemInfo.get(GPUVENDOR);
	}

	public static boolean supportsOpenGL() {
		return Boolean.parseBoolean(systemInfo.get(SUPPORTOPENGL));
	}

	public static boolean supportsGLSL() {
		return Boolean.parseBoolean(systemInfo.get(SUPPORTGLSL));
	}

	public static int getNumCPUs() {
		return Integer.parseInt(systemInfo.get(NUMCPUS));
	}

	public static long getTotalMemory() {
		return Long.parseLong(systemInfo.get(TOTALMEMORY));
	}

	public static String getJAVAVMVersion() {
		return systemInfo.get(JAVAVERSION);
	}

	public static String getJAVAVMName() {
		return systemInfo.get(JAVAVMNAME);
	}

	public static String getOSVersion() {
		return systemInfo.get(OSVERSION);
	}

	public static String getOSArchitecture() {
		return systemInfo.get(OSARCH);
	}

	public static String getOSName() {
		return systemInfo.get(OSNAME);
	}

	private static String createSystemItemLine(String itemKey) {
		return itemKey + ": " + systemInfo.get(itemKey) + "\n";
	}

	public static String getSystemString() {
		if (systemInfo == null) {
			initialize();
		}

		StringBuilder result = new StringBuilder();

		for (String k : new String[] { OSNAME, OSVERSION, OSARCH, JAVAVMNAME, JAVAVERSION }) {
			result.append(createSystemItemLine(k));
		}

		DecimalFormatSymbols dfs = new DecimalFormatSymbols();
		dfs.setGroupingSeparator('_');
		DecimalFormat df = new DecimalFormat("###,###,###,###", dfs);
		result.append(TOTALMEMORY + ": " + df.format(getTotalMemory()) + "\n");

		for (String k : new String[] { SUPPORTOPENGL, SUPPORTGLSL, GPUVENDOR, MAXTEXDIM }) {
			if (systemInfo.containsKey(k)) {
				result.append(createSystemItemLine(k));
			}
		}
		return result.toString();
	}
}
