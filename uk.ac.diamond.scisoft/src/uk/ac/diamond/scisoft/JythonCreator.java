/*-
 * Copyright (c) 2012 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.io.IOUtils;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.ui.IStartup;
import org.eclipse.ui.preferences.ScopedPreferenceStore;
import org.python.pydev.ast.codecompletion.revisited.ModulesManagerWithBuild;
import org.python.pydev.ast.interpreter_managers.InterpreterInfo;
import org.python.pydev.ast.interpreter_managers.InterpreterManagersAPI;
import org.python.pydev.ast.interpreter_managers.JythonInterpreterManager;
import org.python.pydev.ast.listing_utils.JavaVmLocationFinder;
import org.python.pydev.ast.runners.SimpleRunner;
import org.python.pydev.core.CorePlugin;
import org.python.pydev.core.IInterpreterInfo;
import org.python.pydev.core.IInterpreterManager;
import org.python.pydev.core.IPythonNature;
import org.python.pydev.core.MisconfigurationException;
import org.python.pydev.debug.newconsole.PydevConsoleConstants;
import org.python.pydev.plugin.PyDevUiPrefs;
import org.python.pydev.shared_core.io.FileUtils;
import org.python.pydev.shared_core.structure.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.osgi.FunctionFactoryStartup;
import uk.ac.diamond.scisoft.analysis.osgi.LoaderFactoryStartup;
import uk.ac.diamond.scisoft.jython.JythonPath;

public class JythonCreator implements IStartup {

	private static Logger logger = LoggerFactory.getLogger(JythonCreator.class);

	@Override
	public void earlyStartup() {

		// initialiseInterpreter only when 
		// loader factory and function factory plugins 
		// are known.
		final Runnable runner = new Runnable() {
			@Override
			public void run() {
				
				try {
					Thread.sleep(500); // 1/2 second
				} catch (InterruptedException e) {
					logger.error("Cannot wait on worker thread", e);
				}

				while(!LoaderFactoryStartup.isStarted() || 
					  !FunctionFactoryStartup.isStarted()) {
					
					try {
						Thread.sleep(500); // 1/2 second
					} catch (InterruptedException e) {
						logger.error("Cannot sleep on worker thread", e);
					}
				}
				try {
					initialiseConsole();
					initialiseInterpreter(new NullProgressMonitor());
				} catch (Exception e) {
					logger.error("Cannot initialize the Jython interpreter.", e);
				}
			}
		};

		final Thread daemon = new Thread(runner);
		daemon.setPriority(Thread.MIN_PRIORITY);
		daemon.setDaemon(true);
		daemon.start();
	}

	private void initialiseConsole() {
		// need to set some preferences to get the Pydev features working.
		IPreferenceStore pydevDebugPreferenceStore =  new ScopedPreferenceStore(InstanceScope.INSTANCE,"org.python.pydev.debug");

		pydevDebugPreferenceStore.setDefault(PydevConsoleConstants.INITIAL_INTERPRETER_CMDS, "#Configuring Environment, please wait\nimport scisoftpy as dnp;import sys;sys.executable=''\n");
		pydevDebugPreferenceStore.setDefault(PydevConsoleConstants.INTERACTIVE_CONSOLE_VM_ARGS, "-Xmx512m");
		pydevDebugPreferenceStore.setDefault(PydevConsoleConstants.INTERACTIVE_CONSOLE_MAXIMUM_CONNECTION_ATTEMPTS, 4000);
	}

	/**
	 * Name of interpreter that is set in the PyDev Jython Interpreter settings
	 */
	public static final String INTERPRETER_NAME = "Jython" + JythonPath.getJythonMajorVersion();

	/**
	 * Variables containing paths have been moved to u.a.d.jython.util.JythonPath
	 */
	private static final String[] removedLibEndings = {
		"pysrc",
		"classpath__" // includes __classpath__ and __pyclasspath__
	};

	private void initialiseInterpreter(IProgressMonitor monitor) throws Exception {
		/*
		 * The layout of plugins can vary between where a built product and
		 * a product run from Ellipse:
		 * 
		 *  1) Built product
		 *     . this class in plugins/a.b.c
		 *     . flat hierarchy with jars and expanded bundles (with jars in a.b.c and a.b.c/jars)
		 *  2) Ellipse run
		 *     . flagged by RUN_IN_ECLIPSE property
		 *     . source code can be in workspace/plugins or workspace_git (this class is in workspace_git/blah.git/a.b.c)
		 * 
		 * Jython lives in diamond-jython.git in uk.ac.diamond.jython (after being moved from uk.ac.gda.libs)
		 */

		logger.debug("Initialising the Jython interpreter setup");


		// Horrible Hack warning: This code is copied from parts of Pydev to set up the interpreter and save it.
		{

			File pluginsDir = JythonPath.getPluginsDirectory(); // plugins or git workspace directory
			if (pluginsDir == null) {
				logger.error("Failed to find plugins directory!");
				return;
			}
			logger.debug("Plugins directory is {}", pluginsDir);
			boolean isRunningInEclipse = JythonPath.isRunningInEclipse(pluginsDir);
			if (isRunningInEclipse) {
				logger.debug("Running in Eclipse");
			}

			// Set cache directory to something not in the installation directory
			IPreferenceStore pyStore = PyDevUiPrefs.getPreferenceStore();
			String cachePath = pyStore.getString(IInterpreterManager.JYTHON_CACHE_DIR);
			if (System.getProperty("python.cachedir.skip") != null) {
				System.clearProperty("python.cachedir");
				pyStore.setValue(IInterpreterManager.JYTHON_CACHE_DIR, "");
				logger.debug("Do not cache jars as python.cachedir.skip is set - clearing python.cachedir property");
			} else {
				final File cacheDir;
				if (cachePath == null || cachePath.isEmpty()) {
					final String workspace = ResourcesPlugin.getWorkspace().getRoot().getLocation().toOSString();
					cacheDir = new File(workspace, ".jython_cachedir");
					cachePath = cacheDir.getAbsolutePath();
					pyStore.setValue(IInterpreterManager.JYTHON_CACHE_DIR, cachePath);
				} else {
					cacheDir = new File(cachePath);
				}
				if (!cacheDir.exists() && !cacheDir.mkdirs()) {
					logger.error("Could not create Jython cache directory: {}", cachePath);
				}
				System.setProperty("python.cachedir", cachePath);
			}

			// check for the existence of this standard pydev script
			final File script = CorePlugin.getScriptWithinPySrc("interpreterInfo.py").getCanonicalFile();
			if (!script.exists()) {
				logger.error("The file specified does not exist: {} ", script);
				throw new RuntimeException("The file specified does not exist: " + script);
			}
			logger.debug("Script path = {}", script.getAbsolutePath());

			File java = JavaVmLocationFinder.findDefaultJavaExecutable();
			logger.debug("Using java: {}", java);
			String javaPath;
			try {
				javaPath = java.getCanonicalPath();
			} catch (IOException e) {
				logger.warn("Could not resolve default Java path so resorting to PATH", e);
				javaPath = "java";
			}
			
			//If the interpreter directory comes back unset, we don't want to go any further.
			File interpreterDirectory = JythonPath.getInterpreterDirectory();
			if (interpreterDirectory == null) {
				logger.error("Interpreter directory not set. Cannot find interpreter.");
				return;
			}

			String executable = new File(interpreterDirectory, JythonPath.getJythonExecutableName()).getAbsolutePath();
			if (!(new File(executable)).exists()) { 
				logger.error("Failed to find jython jar at all");
				return;
			}
			logger.debug("executable path = {}", executable);

			String[] cmdarray = {javaPath, "-Xmx64m",
//					"-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=localhost:8000",
					"-Dpython.cachedir.skip=true", // this works in Windows
					"-jar", executable,
//					"-B", 
					FileUtils.getFileAbsolutePath(script)};
			File workingDir = new File(System.getProperty("java.io.tmpdir"));
			logger.debug("Cache and tmp working dirs are {} and {}", cachePath, workingDir);
			IPythonNature nature = null;

			String outputString = "";
			try {
				Tuple<Process, String> outTuple = new SimpleRunner().run(cmdarray, workingDir, nature, monitor);
				outputString = IOUtils.toString(outTuple.o1.getInputStream(), StandardCharsets.UTF_8);
			} catch (IOException e1) {
				logger.error("Could not parse output from running interpreterInfo.py in Jython", e1);
			} catch (Exception e2) {
				logger.error("Something went wrong in running interpreterInfo.py in Jython", e2);
			}

			logger.debug("Output String is {}", outputString);

			// this is the main info object which contains the environment data
			InterpreterInfo info = null;

			try {
				// HACK Otherwise Pydev shows a dialog to the user.
				ModulesManagerWithBuild.IN_TESTS = true;
				info = InterpreterInfo.fromString(outputString, false);
			} catch (Exception e) {
				logger.error("InterpreterInfo.fromString(outTup.o1) has failed in pydev setup with exception", e);

			} finally {
				ModulesManagerWithBuild.IN_TESTS = false;
			}

			if (info == null) {
				logger.error("pydev info is set to null");
				return;
			}

			// the executable is the jar itself
			info.executableOrJar = executable;

			final String osName = System.getProperty("os.name");
			final String pathEnv = osName.contains("Mac OS X") ? "DYLD_LIBRARY_PATH" : (osName.contains("Windows") ? "PATH"
					: "LD_LIBRARY_PATH");
			logPaths("Library paths:", System.getenv(pathEnv));

			logPaths("Class paths:", System.getProperty("java.library.path"));

			// set of python paths
			Set<String> pyPaths = new TreeSet<String>();

			// we have to find the jars before we restore the compiled libs
			final List<File> jars = JavaVmLocationFinder.findDefaultJavaJars();
			for (File jar : jars) {
				if (!pyPaths.add(jar.getAbsolutePath())) {
					logger.warn("File {} already there!", jar.getName());
				}
			}

			Set<String> extraPlugins = new HashSet<String>(7);
			// Find all packages that contribute to loader factory
			Set<String> loaderPlugins = LoaderFactoryStartup.getPlugins();
			if (loaderPlugins != null) {
				logger.debug("Extra plugins: {}", loaderPlugins);
				extraPlugins.addAll(loaderPlugins);
			}
			
			// We add the SWT plugins so that the plotting system works in Jython mode.
			// The class IRemotePlottingSystem ends up referencing color so SWT plugins are
			// required to expose IRemotePlottingSystem to the scripting layer.
			createSwtEntries(extraPlugins);

			// Also need allPluginsDirs for later parts
			final List<File> allPluginDirs = JythonPath.findDirs(pluginsDir, extraPlugins, isRunningInEclipse);
			// Get Jython paths for DAWN libs
			pyPaths.addAll(JythonPath.assembleJyPaths(pluginsDir, allPluginDirs, extraPlugins));

			Set<String> removals = new HashSet<String>();
			for (String s : info.libs) {
				String ls = s.toLowerCase();
				for (String r : removedLibEndings) {
					if (ls.endsWith(r)) {
						removals.add(s);
						break;
					}
				}
			}
			info.libs.removeAll(removals);
			info.libs.addAll(pyPaths);

			// now set up the dynamic library environment
			Set<String> paths = new LinkedHashSet<String>();
			// add from environment variables
			String ldPath = System.getenv(pathEnv);
			if (ldPath != null) {
				for (String p : ldPath.split(File.pathSeparator)) {
					paths.add(p);
				}
			}
			PyDevAdditionalInterpreterSettings settings = new PyDevAdditionalInterpreterSettings();
			Collection<String> envVariables = settings.getAdditionalEnvVariables();

			addLibraryPathsAndHDF5PluginPath(envVariables, paths, allPluginDirs);

			StringBuilder allPaths = new StringBuilder();
			for (String p : paths) {
				allPaths.append(p);
				allPaths.append(File.pathSeparatorChar);
			}
			String libraryPath = allPaths.length() > 0 ? allPaths.substring(0, allPaths.length()-1) : null;


			if (libraryPath == null) {
				logger.warn("{} not defined as no library paths were found!", pathEnv);
			} else {
				logPaths("Setting " + pathEnv + " for dynamic libraries", libraryPath);
				envVariables.add(pathEnv + "=" + libraryPath);
			}

			String[] envVarsAlreadyIn = info.getEnvVariables();
			if (envVarsAlreadyIn != null) {
				envVariables.addAll(Arrays.asList(envVarsAlreadyIn));
			}

			// add custom loader extensions to work around Jython not being OSGI
			Set<String> loaderExts = LoaderFactoryStartup.getExtensions();
			if (loaderExts != null) {
				String ev = "LOADER_FACTORY_EXTENSIONS=";
				for (String e : loaderExts) {
					ev += e + "|";
				}
				envVariables.add(ev);
			}

			info.setEnvVariables(envVariables.toArray(new String[envVariables.size()]));

			// java, java.lang, etc should be found now
			info.restoreCompiledLibs(monitor);
			info.setName(INTERPRETER_NAME);

			logger.debug("Finalising the Jython interpreter manager");

			final JythonInterpreterManager man = (JythonInterpreterManager) InterpreterManagersAPI.getJythonInterpreterManager();
			HashSet<String> set = new HashSet<String>();
			// Note, despite argument in PyDev being called interpreterNamesToRestore
			// in this context that name is the exe. 
			// Pydev doesn't allow two different interpreters to be configured for the same
			// executable path so in some contexts the executable is the unique identifier (as it is here)
			set.add(executable);
			
			// Attempt to update existing Jython configuration
			IInterpreterInfo[] interpreterInfos = man.getInterpreterInfos();
			IInterpreterInfo existingInfo = null;
			try {
				existingInfo = man.getInterpreterInfo(executable, monitor);
			} catch (MisconfigurationException e) {
				// MisconfigurationException thrown if executable not found
			}

			if (existingInfo != null && existingInfo.toString().equals(info.toString())) {
				logger.debug("Jython interpreter already exists with exact settings");
			} else {
				// prune existing interpreters with same name
				Map<String, IInterpreterInfo> infoMap = new LinkedHashMap<String, IInterpreterInfo>();
				for (IInterpreterInfo i : interpreterInfos) {
					infoMap.put(i.getName(), i);
				}
				if (existingInfo == null) {
					if (infoMap.containsKey(INTERPRETER_NAME)) {
						existingInfo = infoMap.get(INTERPRETER_NAME);
						logger.debug("Found interpreter of same name");
					}
				}
				if (existingInfo == null) {
					logger.debug("Adding interpreter as an additional interpreter");
				} else {
					logger.debug("Updating interpreter which was previously created");
				}
				infoMap.put(INTERPRETER_NAME, info);
				try {
					IInterpreterInfo[] infos = new IInterpreterInfo[infoMap.size()];
					int j = 0;
					for (String i : infoMap.keySet()) {
						infos[j++] = infoMap.get(i);
					}
					try {
						man.setInfos(infos, set, monitor);
					} catch (Throwable swallowed) {
						// Occurs with a clean workspace.
					}
				} catch (RuntimeException e) {
					logger.warn("Problem with restoring info");
				}
			}

			logger.debug("Finished the Jython interpreter setup");
		}
	}

	static final String HDF5_PLUGIN_PATH = "HDF5_PLUGIN_PATH";

	/**
	 * Add HDF5 OS and architecture-specific native plugin libraries location to environment variables
	 * @param envVariables
	 * @param allPluginDirs
	 */
	public static void addHDF5PluginPath(Collection<String> envVariables, List<File> allPluginDirs) {
		addLibraryPathsAndHDF5PluginPath(envVariables, null, allPluginDirs);
	}

	/**
	 * Add all OS and architecture-specific native plugin libraries location to environment variables and paths
	 * @param envVariables
	 * @param paths (can be null when only HDF5 libraries needed)
	 * @param allPluginDirs
	 */
	private static void addLibraryPathsAndHDF5PluginPath(Collection<String> envVariables, Set<String> paths, List<File> allPluginDirs) {
		String hdf5Lib = addAllNativeLibraries(paths, allPluginDirs);

		String hdf5PluginPath = System.getenv(HDF5_PLUGIN_PATH);
		if (hdf5PluginPath == null) {
			hdf5PluginPath = hdf5Lib;
		}

		if (hdf5PluginPath != null) {
			if (paths != null) { // only log when called by initialiseInterpreter
				logger.debug("Found {}: {}", HDF5_PLUGIN_PATH, hdf5PluginPath);
			}
			envVariables.add(HDF5_PLUGIN_PATH + "=" + hdf5PluginPath);
		}
	}

	private static String addAllNativeLibraries(Set<String> paths, List<File> allPluginDirs) {
		String hdf5Lib = null;
		String osarch = Platform.getOS() + "-" + Platform.getOSArch();
		if (paths != null) { // only log when called by initialiseInterpreter
			logger.debug("Using OS and ARCH: {}", osarch);
		}
		for (File dir : allPluginDirs) {
			File d = new File(dir, "lib");
			if (d.isDirectory()) {
				d = new File(d, osarch);
				if (d.isDirectory()) {
					String p = d.getAbsolutePath();
					boolean isHDF5 = p.contains("hdf.hdf5lib");
					if (paths == null) {
						if (isHDF5) {
							return p;
						}
					} else if (paths.add(p)) {
						logger.debug("Adding library path: {}", d);
						if (isHDF5 && hdf5Lib == null) {
							hdf5Lib = p;
						}
					}
				}
			}
		}
		return hdf5Lib;
	}

	private void createSwtEntries(Set<String> extraPlugins) {
		final String ws   = Platform.getWS();
		if (ws == null) return;
		final String os   = Platform.getOS();
		if (os == null) return;
		final String arch = Platform.getOSArch();
		if (arch == null) return;
		
		extraPlugins.add("org.eclipse.swt_"); // Core SWT
		extraPlugins.add("org.eclipse.swt."+ws+"."+os+"."+arch); // OS SWT
	}

	private static void logPaths(String pathname, String paths) {
		if (paths == null)
			return;
		logger.debug(pathname);
		for (String p : paths.split(File.pathSeparator))
			logger.debug("\t{}", p);
	}
}
