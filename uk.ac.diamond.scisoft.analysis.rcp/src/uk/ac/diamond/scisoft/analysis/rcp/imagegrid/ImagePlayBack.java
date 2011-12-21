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

package uk.ac.diamond.scisoft.analysis.rcp.imagegrid;

import java.util.ArrayList;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.swt.widgets.Slider;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.views.IViewDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.PlotServerProvider;
import uk.ac.diamond.scisoft.analysis.plotserver.FileOperationBean;
import uk.ac.diamond.scisoft.analysis.plotserver.GuiBean;
import uk.ac.diamond.scisoft.analysis.plotserver.GuiParameters;
import uk.ac.diamond.scisoft.analysis.rcp.queue.InteractiveJob;
import uk.ac.diamond.scisoft.analysis.rcp.queue.InteractiveJobAdapter;
import uk.ac.diamond.scisoft.analysis.rcp.queue.InteractiveQueue;

/**
 *
 */
public class ImagePlayBack implements Runnable {

	// Adding in some logging to help with getting this running
	transient private static final Logger logger = LoggerFactory.getLogger(ImagePlayBack.class);
	
	private ArrayList<String> jobFiles;
	private ArrayList<String> allFiles;
	private int playPos;
	private boolean terminate = false;
	private String viewName;
	private boolean hasOpenedView = false;
	private boolean waitTillfinished = false;
	private boolean paused = false;
	private boolean autoRewind = false;
	private IWorkbenchPage page;
	private int delay = 250;
	private int step = 1;
	private Slider sldProgress;

	class IJob extends InteractiveJobAdapter {
		private String file;

		public IJob(String filename) {
			file = filename;
		}

		@Override
		public void run(IProgressMonitor mon) {
			if (!isNull()) {
				sendOffLoadRequest(file);
			}
		}

		@Override
		public boolean isNull() {
			return file == null;
		}
	}

	private InteractiveQueue jobQueue;

	public ImagePlayBack(String viewName, IWorkbenchPage page, Slider slider, int delay, int step) {
		playPos = 0;
		jobFiles = new ArrayList<String>();
		allFiles = new ArrayList<String>();
		this.viewName = viewName;
		this.page = page;
		this.delay = delay;
		this.step = step;
		this.sldProgress = slider;
		jobQueue = new InteractiveQueue();
	}

	private void sendOffLoadRequest(String filename) {
		if (!hasOpenedView) {
			waitTillfinished = true;
			if (sldProgress.getDisplay().getThread() != Thread.currentThread()) {
				PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable() {
					@Override
					public void run() {
						try {
							IViewDescriptor[] views = PlatformUI.getWorkbench().getViewRegistry().getViews();
							for (int i = 0; i < views.length; i++)
								if (views[i].getLabel().equals(viewName))
									page.showView(views[i].getId());
						} catch (PartInitException e) {
							e.printStackTrace();
						}
						waitTillfinished = false;
					}
				});
			} else {
				try {
					IViewDescriptor[] views = PlatformUI.getWorkbench().getViewRegistry().getViews();
					for (int i = 0; i < views.length; i++)
						if (views[i].getLabel().equals(viewName))
							page.showView(views[i].getId());
				} catch (PartInitException e) {
					e.printStackTrace();
				}
				waitTillfinished = false;
			}
			hasOpenedView = true;
		}
		while (waitTillfinished) {
			try {
				Thread.sleep(2500);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		ArrayList<String> files = new ArrayList<String>();
		files.add(filename);
		GuiBean fileLoadBean = new GuiBean();
		FileOperationBean fopBean = new FileOperationBean(FileOperationBean.GETIMAGEFILE);
		fopBean.setFiles(files);
		fileLoadBean.put(GuiParameters.FILEOPERATION, fopBean);
		fileLoadBean.put(GuiParameters.DISPLAYFILEONVIEW, viewName);
		try {
			logger.debug("Sending request for "+filename);
			PlotServerProvider.getPlotServer().updateGui(viewName, fileLoadBean);
			logger.debug("Returned from sending request");
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	@Override
	public void run() {
		while(!terminate) {
			String fileEntry = null;
			synchronized(jobFiles) {
				if (playPos < jobFiles.size() && !paused) {
					fileEntry = jobFiles.get(playPos);
					final int tPos = playPos;
					playPos+=step;
					if (autoRewind && playPos > jobFiles.size()) {
						playPos = 0;
					}
					sldProgress.getDisplay().asyncExec(new Runnable() {
						@Override
						public void run() {
							sldProgress.setSelection(tPos);
						}
					});
				}
			}
			if (fileEntry != null) {
				sendOffLoadRequest(fileEntry);
				try {
					Thread.sleep(delay);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			} else {
				synchronized(this) {
					try {
						wait();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		}
	}

	public synchronized void stop() {
		playPos = 0;
		terminate = true;
		paused = false;
		notify();
	}
	
	public synchronized void rewind() {
		playPos = 0;
		notify();
	}
	
	public synchronized void forward() {
		synchronized(jobFiles) {
			playPos = jobFiles.size()-1;
		}
		notify();
	}

	public synchronized void start() {
		if (paused)
			notify();

		terminate = false;
		paused = false;
	}

	public synchronized void setDelay(int newDelay) {
		delay = newDelay;
	}
	
	public synchronized boolean isPaused() {
		return paused;
	}
	
	public synchronized void pause() {
		paused = true;
	}
	
	public synchronized void clearPlayback() {
		synchronized(jobFiles) {
			jobFiles.clear();
			allFiles.clear();
		}
	}
		
	public synchronized void addFile(String newFile) {
		synchronized(jobFiles) {
//			logger.debug("New file has been added "+newFile);
			jobFiles.add(newFile);
			allFiles.add(newFile);
			if (sldProgress.getDisplay().getThread() != Thread.currentThread()) {
				sldProgress.getDisplay().asyncExec(new Runnable() {
					@Override
					public void run() {
						sldProgress.setMaximum(jobFiles.size());
					}
				});
			} else
				sldProgress.setMaximum(jobFiles.size());
//			logger.debug("Add file, current playPos "+playPos+" total pos "+jobFiles.size());
			notify();
		}
	}
	
	public synchronized void setSelection(ArrayList<String> selectedFiles) {
		if (selectedFiles != null && selectedFiles.size() > 0) {
			jobFiles.clear();
			jobFiles.addAll(selectedFiles);
		} else {
			jobFiles.clear();
			jobFiles.addAll(allFiles);
		}
		sldProgress.setMaximum(jobFiles.size());
	}

	public synchronized void moveToLast() {
//		logger.debug("Move to last called");
		forward();
		if (paused || terminate) {
			String fileEntry = jobFiles.get(playPos);
			if (fileEntry != null) {
				sendOffLoadRequest(fileEntry);
			}
		}
//		logger.debug("Move to last new pos is "+playPos);
	}
	
	public synchronized void setPlayPos(int newPos) {
		playPos = newPos;
		notify();
		if (paused || terminate) {
			if (playPos < 0 || playPos >= jobFiles.size()) {
				playPos = 0;
				return;
			}
			String fileEntry = jobFiles.get(playPos);
			if (fileEntry != null) {
				try {
					final InteractiveJob obj = new IJob(fileEntry);
					jobQueue.addJob(obj);
				} catch (Exception e) {
					logger.error("Cannot generate new job", e);
				}
			}
			playPos+=step;
		}
	}
	
	public synchronized void setStepping(int newStep) {
		step = newStep;
	}
	
	public synchronized void setAutoRewind(boolean rewind) {
		autoRewind = rewind;
	}

	public void dispose() {
		jobQueue.dispose();
	}
}
