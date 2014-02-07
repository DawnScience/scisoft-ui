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

package uk.ac.diamond.scisoft;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
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
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.ui.IStartup;
import org.eclipse.ui.preferences.ScopedPreferenceStore;
import org.osgi.framework.Bundle;
import org.python.copiedfromeclipsesrc.JavaVmLocationFinder;
import org.python.pydev.core.IInterpreterInfo;
import org.python.pydev.core.IInterpreterManager;
import org.python.pydev.core.IPythonNature;
import org.python.pydev.core.MisconfigurationException;
import org.python.pydev.debug.newconsole.PydevConsoleConstants;
import org.python.pydev.editor.codecompletion.revisited.ModulesManagerWithBuild;
import org.python.pydev.plugin.PydevPlugin;
import org.python.pydev.plugin.preferences.PydevPrefs;
import org.python.pydev.runners.SimpleRunner;
import org.python.pydev.shared_core.io.FileUtils;
import org.python.pydev.shared_core.structure.Tuple;
import org.python.pydev.ui.interpreters.JythonInterpreterManager;
import org.python.pydev.ui.pythonpathconf.InterpreterInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JythonCreator implements IStartup {

	private static Logger logger = LoggerFactory.getLogger(JythonCreator.class);

	@Override
	public void earlyStartup() {
		try {
			initialiseConsole();
			initialiseInterpreter(new NullProgressMonitor());
		} catch (Throwable e) {
			logger.error("Cannot create interpreter!", e);
		}
	}

	private void initialiseConsole() {
		// need to set some preferences to get the Pydev features working.
		IPreferenceStore pydevDebugPreferenceStore =  new ScopedPreferenceStore(InstanceScope.INSTANCE,"org.python.pydev.debug");

		pydevDebugPreferenceStore.setDefault(PydevConsoleConstants.INITIAL_INTERPRETER_CMDS, "#Configuring Environment, please wait\nimport scisoftpy as dnp;import sys;sys.executable=''\n");
		pydevDebugPreferenceStore.setDefault(PydevConsoleConstants.INTERACTIVE_CONSOLE_VM_ARGS, "-Xmx512m");
		pydevDebugPreferenceStore.setDefault(PydevConsoleConstants.INTERACTIVE_CONSOLE_MAXIMUM_CONNECTION_ATTEMPTS, 4000);
	}


	private static final String JYTHON_BUNDLE = "uk.ac.diamond.jython";
	private static final String JYTHON_VERSION = "2.5";
	public static final String  INTERPRETER_NAME = "Jython" + JYTHON_VERSION;
	private static String JYTHON_DIR = "jython" + JYTHON_VERSION;
	private static final String GIT_REPO_ENDING = ".git";
	private static final String GIT_SUFFIX = "_git";
	private static final String RUN_IN_ECLIPSE = "run.in.eclipse";
	private static final String[] blackListedJarDirs = {
		"uk.ac.gda.libs",
		"ch.qos.logback.eclipse",
		"ch.qos.logback.beagle",
		"org.dawb.workbench.jmx",
		GIT_REPO_ENDING,
		JYTHON_DIR
		};
	private static final String[] requiredJars = {
		"org.python.pydev",
		"cbflib-0.9",
		"org.apache.commons.codec",
		"org.apache.commons.math", // includes math3
		"uk.ac.diamond.CBFlib",
		"uk.ac.diamond.jama",
		"uk.ac.diamond.scisoft.analysis",
		"uk.ac.diamond.scisoft.python",
		"uk.ac.diamond.scisoft.spectroscopy",
		"uk.ac.gda.common",
		"org.dawb.hdf5", // fix to http://jira.diamond.ac.uk/browse/SCI-1467
		"slf4j.api",
		"jcl.over.slf4j",
		"log4j.over.slf4j",
		"ch.qos.logback.core",
		"ch.qos.logback.classic",
		"com.springsource.org.apache.commons",
		"com.springsource.javax.media.jai.core",
		"com.springsource.javax.media.jai.codec",
		"jtransforms",
		"jai_imageio",
		"it.tidalwave.imageio.raw",
		"javax.vecmath",
		"uk.ac.diamond.org.apache.ws.commons.util",
		"uk.ac.diamond.org.apache.xmlrpc.client",
		"uk.ac.diamond.org.apache.xmlrpc.common",
		"uk.ac.diamond.org.apache.xmlrpc.server"
	};
	private static final String[] removedLibEndings = {
		"pysrc",
		"classpath__" // includes __classpath__ and __pyclasspath__
	};
	private final static String[] pluginKeys = {
		"org.dawb.hdf5", // required for loading to work in client started from IDE
		"uk.ac.diamond.scisoft.analysis",
		"uk.ac.diamond.scisoft.python",
		"uk.ac.diamond.CBFlib",
		"uk.ac.gda.common",
		"ncsa.hdf"
	};

	private void initialiseInterpreter(IProgressMonitor monitor) throws CoreException {
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

		boolean isRunningInEclipse = "true".equalsIgnoreCase(System.getProperty(RUN_IN_ECLIPSE));

		// Horrible Hack warning: This code is copied from parts of Pydev to set up the interpreter and save it.
		{

			File pluginsDir = getPluginsDirectory(isRunningInEclipse); // plugins or git workspace directory
			if (pluginsDir == null) {
				logger.error("Failed to find plugins directory!");
				return;
			}
			logger.debug("Plugins directory is {}", pluginsDir);

			// Set cache directory to something not in the installation directory
			IPreferenceStore pyStore = PydevPrefs.getPreferenceStore();
			String cachePath = pyStore.getString(IInterpreterManager.JYTHON_CACHE_DIR);
			if (cachePath == null || cachePath.length() == 0) {
				final String workspace = ResourcesPlugin.getWorkspace().getRoot().getLocation().toOSString();
				final File cacheDir = new File(workspace, ".jython_cachedir");
				if (!cacheDir.exists())
					cacheDir.mkdirs();
				cachePath = cacheDir.getAbsolutePath();
				pyStore.setValue(IInterpreterManager.JYTHON_CACHE_DIR, cacheDir.getAbsolutePath());
			}
			System.setProperty("python.cachedir", cachePath);

			// check for the existence of this standard pydev script
			final File script = PydevPlugin.getScriptWithinPySrc("interpreterInfo.py");
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

			String executable = new File(getInterpreterDirectory(pluginsDir, isRunningInEclipse), "jython.jar").getAbsolutePath();
			if (!(new File(executable)).exists()) { 
				logger.error("Failed to find jython jar at all");
				return;
			}
			logger.debug("executable path = {}", executable);

			String[] cmdarray = {javaPath, "-Xmx64m",
//					"-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=localhost:8000",
					"-Dpython.cachedir.skip=true", // this works in Windows
					"-jar", executable,
					FileUtils.getFileAbsolutePath(script)};
			File workingDir = new File(System.getProperty("java.io.tmpdir"));
//			logger.debug("Cache and working dirs are {} and {}", cachePath, workingDir);
			IPythonNature nature = null;

			String outputString = "";
			try {
				Tuple<Process, String> outTuple = new SimpleRunner().run(cmdarray, workingDir, nature, monitor);
				outputString = IOUtils.toString(outTuple.o1.getInputStream());
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
				logger.error("InterpreterInfo.fromString(outTup.o1) has failed in pydev setup with exception");
				logger.error("{}", e);

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
			final boolean isMacOSX = osName.contains("Mac OS X");
			final String pathEnv = isMacOSX ? "DYLD_LIBRARY_PATH" : (osName.contains("Windows") ? "PATH"
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

			// Defines all third party libs that can be used in scripts.
			logger.debug("Adding files to python path");
			final List<File> allJars = findJars(pluginsDir);
			for (File file : allJars) {
				if (pyPaths.add(file.getAbsolutePath())) {
					logger.debug("Adding jar file to python path : {} ", file.getAbsolutePath());
				}
			}

			final List<File> allPluginDirs = findDirs(pluginsDir, isRunningInEclipse);

			logger.debug("All Jars prepared");

			if (isRunningInEclipse) {
				// ok checking for items inside the tp directory
				File wsDir = pluginsDir;
				if (!new File(wsDir, "tp").isDirectory()) {
					String ws = wsDir.getName();
					int i = ws.indexOf(GIT_SUFFIX);
					if (i >= 0) {
						wsDir = new File(wsDir.getParentFile(), ws.substring(0, i));
					}
				}
				final File wsPluginsDir = new File(wsDir, "plugins");
				if (wsPluginsDir.isDirectory()) {
					allPluginDirs.addAll(findDirs(wsPluginsDir, isRunningInEclipse));
				}
				wsDir = new File(wsDir, "tp");
				if (wsDir.isDirectory()) {
					wsDir = new File(wsDir, "plugins");
					final List<File> tJars = findJars(wsDir);
					for (File file : tJars) {
						if (pyPaths.add(file.getAbsolutePath())) {
							logger.debug("Adding jar file to python path : {} ", file.getAbsolutePath());
						}
					}
				}

				// add plugins and ScisoftPy package
				for (File file: allPluginDirs) {
					File b = new File(file, "bin");
					if (b.isDirectory()) {
						if (pyPaths.add(b.getAbsolutePath())) {
							logger.debug("Adding dir to python path: {} ", b.getAbsolutePath());
						}
					} 
					// also check for internal jars
					final List<File> tJars = findJars(file);
					for (File j : tJars) {
						if (pyPaths.add(j.getAbsolutePath())) {
							logger.debug("Adding jar file to python path : {} ", j.getAbsolutePath());
						}
					}
				}
			} else {
				// and add all unjarred folders
				for (File file: allPluginDirs) {
					if (pyPaths.add(file.getAbsolutePath())) {
						logger.debug("Adding dir to python path: {} ", file.getAbsolutePath());
					}
				}
			}

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
			File libraryDir = new File(pluginsDir.getParent(), "lib");
			Set<String> paths = new LinkedHashSet<String>();
			if (!isRunningInEclipse && libraryDir.exists()) {
				paths.add(libraryDir.getAbsolutePath());
			} else {
				// check each plugin directory's for dynamic libraries
				String osarch = Platform.getOS() + "-" + Platform.getOSArch();
				logger.debug("Using OS and ARCH: {}", osarch);
				for (File dir : allPluginDirs) {
					File d = new File(dir, "lib");
					if (d.isDirectory()) {
						d = new File(d, osarch);
						if (d.isDirectory()) {
							if (paths.add(d.getAbsolutePath()))
								logger.debug("Adding library path: {}", d);
						}
					}
				}

			}

			// add from environment variables
			String ldPath = System.getenv(pathEnv);
			if (ldPath != null) {
				for (String p : ldPath.split(File.pathSeparator)) {
					paths.add(p);
				}
			}
			StringBuilder allPaths = new StringBuilder();
			for (String p : paths) {
				allPaths.append(p);
				allPaths.append(File.pathSeparatorChar);
			}
			String libraryPath = allPaths.length() > 0 ? allPaths.substring(0, allPaths.length()-1) : null;

			PyDevAdditionalInterpreterSettings settings = new PyDevAdditionalInterpreterSettings();
			Collection<String> envVariables = settings.getAdditionalEnvVariables();
			if (libraryPath == null) {
				logger.warn("{} not defined as no library paths were found!" + pathEnv);
			} else {
				logPaths("Setting " + pathEnv + " for dynamic libraries", libraryPath);
				envVariables.add(pathEnv + "=" + libraryPath);
			}

			if (isMacOSX) {
				// do we also add DYLD_VERSIONED_LIBRARY_PATH and DYLD_ROOT_PATH?
				String fbPathEnv = "DYLD_FALLBACK_LIBRARY_PATH";
				String fbPath = System.getenv(fbPathEnv);
				if (fbPath == null) {
					logger.debug("{} not defined" + fbPathEnv);
				} else {
					logPaths("For Mac OS X, setting " + fbPathEnv + " for dynamic libraries", fbPath);
					envVariables.add(fbPathEnv + "=" + fbPath);
				}
			}

			String[] envVarsAlreadyIn = info.getEnvVariables();
			if (envVarsAlreadyIn != null) {
				envVariables.addAll(Arrays.asList(envVarsAlreadyIn));
			}
			
			info.setEnvVariables(envVariables.toArray(new String[envVariables.size()]));

			// java, java.lang, etc should be found now
			info.restoreCompiledLibs(monitor);
			info.setName(INTERPRETER_NAME);

			logger.debug("Finalising the Jython interpreter manager");

			final JythonInterpreterManager man = (JythonInterpreterManager) PydevPlugin.getJythonInterpreterManager();
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
					man.setInfos(infos, set, monitor);
				} catch (RuntimeException e) {
					logger.warn("Problem with restoring info");
				}
			}

			logger.debug("Finished the Jython interpreter setup");
		}
	}
	
	/**
	 * @return directory where plugins live (defined as parent of current bundle)
	 */
	private File getPluginsDirectory(boolean isRunningInEclipse) {
		Bundle b = Platform.getBundle(Activator.PLUGIN_ID);
		logger.debug("Bundle: {}", b);
		try {
			File f = FileLocator.getBundleFile(b).getParentFile();
			logger.debug("Bundle location: {}", f.getAbsolutePath());
	
			if (isRunningInEclipse) {
				File gitws = f.getParentFile();
				return gitws;
			}
	
			return f;
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	private File getInterpreterDirectory(File pluginsDir, boolean isRunningInEclipse) {
		if (isRunningInEclipse) {
			for (File g : pluginsDir.listFiles()) { // git repositories
				if (g.isDirectory() && g.getName().endsWith(GIT_REPO_ENDING)) {
					for (File p : g.listFiles()) { // projects
						if (p.getName().startsWith(JYTHON_BUNDLE)) {
							File d = new File(p, JYTHON_DIR);
							return d;
						}
					}
				}
			}
		} else {
			for (File p : pluginsDir.listFiles()) { // plugins
				if (p.getName().startsWith(JYTHON_BUNDLE)) {
					File d = new File(p, JYTHON_DIR);
					return d;
				}
			}

		}
		logger.error("Could not find a directory for {}:", JYTHON_BUNDLE);
		logger.error("\tEither you are running in Eclipse and need to add -D{}=true in the run configuration VM arguments", RUN_IN_ECLIPSE);
		logger.error("\tor there was a problem with the product build.");
		return null;
	}

	/**
	 * Method returns recursively all the jars found in a directory (apart from Jython directory)
	 * 
	 * @return list of jar Files
	 */
	public static final List<File> findJars(File directory) {
		final List<File> libs = new ArrayList<File>();
	
		if (directory.isDirectory()) {
			for (File f : directory.listFiles()) {
				final String name = f.getName();
				// if the file is a jar, then add it
				if (name.endsWith(".jar")) {
					if (isRequired(f, requiredJars)) {
						libs.add(f);
					}
				} else if (f.isDirectory() && !isRequired(f, blackListedJarDirs)) {
					libs.addAll(findJars(f));
				}
			}
		}
	
		return libs;
	}

	private static void logPaths(String pathname, String paths) {
		if (paths == null)
			return;
		logger.debug(pathname);
		for (String p : paths.split(File.pathSeparator))
			logger.debug("\t{}", p);
	}

	/**
	 * Method returns path to directories
	 * 
	 * @return list of directories
	 */
	private List<File> findDirs(File directory, boolean isRunningInEclipse) {

		// ok we get the plugins directory here, so we need to explore a bit further for git
		final List<File> plugins = new ArrayList<File>();

		if (isRunningInEclipse) {
			// get down to the git checkouts
			// only do this if we are running inside Eclipse
			List<File> dirs = new ArrayList<File>();

			for (File d : directory.listFiles()) {
				if (d.isDirectory()) {
					String n = d.getName();
					if (n.endsWith(GIT_REPO_ENDING)) {
						dirs.add(d);
					} else if (n.equals("scisoft")) { // old source layout
						for (File f : d.listFiles()) {
							if (f.isDirectory()) {
								if (f.getName().endsWith(GIT_REPO_ENDING)) {
									logger.debug("Adding scisoft directory {}", f);
									dirs.add(f);
								}
							}
						}
					}
				}
			}

			for (File f : dirs) {
				for (File p : f.listFiles()) {
					if (p.isDirectory()) {
						if (isRequired(p, pluginKeys)) {
							logger.debug("Adding plugin directory {}", p);
							plugins.add(p);
						}
					}
				}
			}
		} else {
			// get the basic plugins directory
			if (directory.isDirectory()) {
				for (File f : directory.listFiles()) {
					if (f.isDirectory()) {
						if (isRequired(f, pluginKeys)) {
							logger.debug("Adding plugin directory {}", f);
							plugins.add(f);
						}
					}
				}
			}
		}

		return plugins;
	}

	private static boolean isRequired(File file, String[] keys) {
		String filename = file.getName();
//		logger.debug("Jar/dir found: {}", filename);
		for (String key : keys) {
			if (filename.startsWith(key)) return true;
		}
		return false;
	}
}
