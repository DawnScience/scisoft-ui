/*
 * Copyright (c) 2012 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.sda.polling.views;


import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerSorter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.part.ViewPart;

import uk.ac.diamond.sda.polling.Activator;
import uk.ac.diamond.sda.polling.jobs.AbstractPollJob;
import uk.ac.diamond.sda.polling.monitor.IPollMonitor;
import uk.ac.diamond.sda.polling.server.PollJobContribution;
import uk.ac.diamond.sda.polling.server.PollServer;


/**
 * This sample class demonstrates how to plug-in a new
 * workbench view. The view shows data obtained from the
 * model. The sample creates a dummy model on the fly,
 * but a real implementation would connect to the model
 * available either in this or another plug-in (e.g. the workspace).
 * The view is connected to the model using a content provider.
 * <p>
 * The view uses a label provider to define how model
 * objects should be presented in the view. Each
 * view can present the same model objects using
 * different labels and icons, if needed. Alternatively,
 * a single label provider can be shared between views
 * in order to ensure that objects of the same type are
 * presented in the same way everywhere.
 * <p>
 */

public class PollingView extends ViewPart implements IPollMonitor {

	/**
	 * The ID of the view as specified by the extension.
	 */
	public static final String ID = "uk.ac.diamond.sda.polling.views.SampleView";

	private TableViewer viewer;
	private Action deleteJobAction;
	private Action deleteAllJobAction;
	private Action reloadAllJobAction;
	private Action doubleClickAction;
	private Action pauseAction;
	private Action resumeAction;
	
	private List<Action> jobActions; 

	/*
	 * The content provider class is responsible for
	 * providing objects to the view. It can wrap
	 * existing objects in adapters or simply return
	 * objects as-is. These objects may be sensitive
	 * to the current input of the view, or ignore
	 * it and always show the same content 
	 * (like Task List, for example).
	 */

	class ViewContentProvider implements IStructuredContentProvider {
		@Override
		public void inputChanged(Viewer v, Object oldInput, Object newInput) {
		}
		
		@Override
		public void dispose() {
		}
		
		@Override
		public Object[] getElements(Object parent) {
			Collection<AbstractPollJob> jobs = PollServer.getInstance().getPollJobs();
			
			return jobs.toArray();
		}
		
	}
	
	class ViewLabelProvider extends LabelProvider implements ITableLabelProvider {
		@Override
		public String getColumnText(Object obj, int index) {
			AbstractPollJob job = (AbstractPollJob) obj;
			if (index == 0) return job.getClass().getSimpleName();
			if (index == 1) return job.getPollTime();
			if (index == 2) return job.getJobParametersFilename();
			if (index == 3) return job.getStatus();
			return null;
		}
		
		@Override
		public Image getColumnImage(Object obj, int index) {
			if (index == 0 ) return getImage(obj);
			return null;
		}
		
		@Override
		public Image getImage(Object obj) {
			return PlatformUI.getWorkbench().
			getSharedImages().getImage(ISharedImages.IMG_OBJ_ELEMENT);
		}
	}

	/**
	 * The constructor.
	 */
	public PollingView() {
	}

	/**
	 * This is a callback that will allow us
	 * to create the viewer and initialize it.
	 */
	@Override
	public void createPartControl(Composite parent) {
		
		viewer = new TableViewer(parent, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL);
		viewer.getTable().setHeaderVisible (true);
		
		TableColumn tc = new TableColumn(viewer.getTable(), SWT.LEFT);
		tc.setText("Job");
		tc.setWidth(200);
		tc = new TableColumn(viewer.getTable(), SWT.LEFT);
		tc.setText("Poll Time");
		tc.setWidth(100);
		tc = new TableColumn(viewer.getTable(), SWT.LEFT);
		tc.setText("Filename");
		tc.setWidth(200);
		tc = new TableColumn(viewer.getTable(), SWT.LEFT);
		tc.setText("Status");
		tc.setWidth(100);
		
		
		viewer.setContentProvider(new ViewContentProvider());
		viewer.setLabelProvider(new ViewLabelProvider());
		viewer.setSorter(new ViewerSorter());
		viewer.setInput(getViewSite());

		// Create the help context id for the viewer's control
		PlatformUI.getWorkbench().getHelpSystem().setHelp(viewer.getControl(), "uk.ac.diamond.sda.polling.viewer");
		makeActions();
		hookContextMenu();
		hookDoubleClickAction();
		contributeToActionBars();
		
		PollServer.getInstance().setPollMonitor(this);
		
	}

	private void hookContextMenu() {
		MenuManager menuMgr = new MenuManager("#PopupMenu");
		menuMgr.setRemoveAllWhenShown(true);
		menuMgr.addMenuListener(new IMenuListener() {
			@Override
			public void menuAboutToShow(IMenuManager manager) {
				PollingView.this.fillContextMenu(manager);
			}
		});
		Menu menu = menuMgr.createContextMenu(viewer.getControl());
		viewer.getControl().setMenu(menu);
		getSite().registerContextMenu(menuMgr, viewer);
	}

	private void contributeToActionBars() {
		IActionBars bars = getViewSite().getActionBars();
		fillLocalPullDown(bars.getMenuManager());
		fillLocalToolBar(bars.getToolBarManager());
	}

	private void fillLocalPullDown(IMenuManager manager) {
		manager.add(pauseAction);
		manager.add(resumeAction);
		manager.add(deleteJobAction);
		manager.add(deleteAllJobAction);
		manager.add(reloadAllJobAction);
		manager.add(new Separator());
		MenuManager jobmenu = new MenuManager("Add New Job");
		manager.add(jobmenu);

		for (Action jobAction : jobActions) {
			jobmenu.add(jobAction);
		}

	}

	private void fillContextMenu(IMenuManager manager) {
		manager.add(deleteJobAction);
		// Other plug-ins can contribute there actions here
		manager.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
	}

	private void fillLocalToolBar(IToolBarManager manager) {
		manager.add(pauseAction);
		manager.add(resumeAction);
		manager.add(deleteJobAction);
		manager.add(deleteAllJobAction);
		manager.add(reloadAllJobAction);
	}

	private void makeActions() {

		jobActions = new ArrayList<Action>();
		
		for (final PollJobContribution pollJobContribution : PollServer.getInstance().getPollJobClasses()) {

			Action jobAction = new Action() {
				@Override
				public void run() {

					try {
						String newFileName = PollServer.getInstance().getNewJobFileName(pollJobContribution);
						AbstractPollJob pollJob = pollJobContribution.getJob(newFileName);
						PollServer.getInstance().addJob(pollJob);

						openEditor(newFileName);

					} catch (final Exception e) {
						Display.getDefault().asyncExec(new Runnable() {
							@Override
							public void run() {
								MessageDialog.openError(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(),
										"Error creating new polling job", e.getMessage());
							}
						});
						e.printStackTrace();
					}

					// finally refresh the view to show the new content
					viewer.refresh();
				}
			};

			jobAction.setText(pollJobContribution.getName());

			jobActions.add(jobAction);

		}
	

		deleteJobAction = new Action() {
			@Override
			public void run() {
				AbstractPollJob job = getActivePollJob();
				MessageDialog dialog = new MessageDialog(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(),
						"Confirm Deletion of Job", 
						PlatformUI.getWorkbench().getSharedImages().getImage(ISharedImages.IMG_ETOOL_DELETE),
						"Are you sure that you want to delete this Job? This process cannot be undone", 0, new String[] {"OK", "Cancel"}, 1);
				int value = dialog.open();
				if (value == 0) {
					PollServer.getInstance().removeJob(job);
				}
				viewer.refresh();
			}
		};
		deleteJobAction.setText("Delete Job");
		deleteJobAction.setToolTipText("Delete the selected job from the list");
		deleteJobAction.setImageDescriptor(PlatformUI.getWorkbench().getSharedImages().
				getImageDescriptor(ISharedImages.IMG_ETOOL_DELETE));
		
		
		deleteAllJobAction = new Action() {
			@Override
			public void run() {
				MessageDialog dialog = new MessageDialog(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(),
						"Confirm Deletion of All Jobs", 
						PlatformUI.getWorkbench().getSharedImages().getImage(ISharedImages.IMG_ETOOL_DELETE),
						"Are you sure that you want to delete All Jobs! This process cannot be undone", 0, new String[] {"OK", "Cancel"}, 1);
				int value = dialog.open();
				if (value == 0) {
					PollServer.getInstance().removeAllJobs();
				}
				viewer.refresh();
			}
		};
		deleteAllJobAction.setText("Delete All Jobs");
		deleteAllJobAction.setToolTipText("Delete All jobs from the list");
		deleteAllJobAction.setImageDescriptor(PlatformUI.getWorkbench().getSharedImages().
				getImageDescriptor(ISharedImages.IMG_ELCL_REMOVEALL));
		
		
		reloadAllJobAction = new Action() {
			@Override
			public void run() {
				PollServer.getInstance().refresh();
				viewer.refresh();
			}
		};
		reloadAllJobAction.setText("Refresh All Jobs");
		reloadAllJobAction.setToolTipText("Refresh all the jobs");
		reloadAllJobAction.setImageDescriptor(PlatformUI.getWorkbench().getSharedImages().
				getImageDescriptor(ISharedImages.IMG_ELCL_SYNCED));
		
		
		pauseAction = new Action() {
			@Override
			public void run() {
				try {
					PollServer.getInstance().stopSheduler();
				} catch (InterruptedException e) {
					// TODO should sort this out.
				}
				viewer.refresh();
			}
		};
		pauseAction.setText("Pause All Jobs");
		pauseAction.setToolTipText("Pause all the jobs");
		pauseAction.setImageDescriptor(Activator.getImageDescriptor("icons/pause_16.png"));
		
		
		resumeAction = new Action() {
			@Override
			public void run() {
				PollServer.getInstance().runSheduler();
				viewer.refresh();
			}
		};
		resumeAction.setText("Restart All Jobs");
		resumeAction.setToolTipText("restart all the jobs");
		resumeAction.setImageDescriptor(Activator.getImageDescriptor("icons/play_16.png"));		
		
		doubleClickAction = new Action() {
			@Override
			public void run() {
				
				AbstractPollJob job = getActivePollJob();
				
				try {
					openEditor(job.getJobParametersFilename());
				} catch (PartInitException e) {
					// try to open the file but not a disaster if if dosent open
					e.printStackTrace();
				}

				viewer.refresh();

			}
		};
	}
	
	private AbstractPollJob getActivePollJob() {
		ISelection selection = viewer.getSelection();
		Object obj = ((IStructuredSelection)selection).getFirstElement();
		return (AbstractPollJob) obj;		
	}

	private void hookDoubleClickAction() {
		viewer.addDoubleClickListener(new IDoubleClickListener() {
			@Override
			public void doubleClick(DoubleClickEvent event) {
				doubleClickAction.run();
			}
		});
	}

	/**
	 * Passing the focus request to the viewer's control.
	 */
	@Override
	public void setFocus() {
		viewer.getControl().setFocus();
	}
	
	private void openEditor(String filename) throws PartInitException {
		File fileToOpen = new File(filename);
		IFileStore fileStore = EFS.getLocalFileSystem().getStore(fileToOpen.toURI());
		IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
		IDE.openEditorOnFileStore(page, fileStore);
	}

	@Override
	public void pollLoopStart() {
		// Not required
	}

	@Override
	public void processingJobs() {
		// Not required
	}

	@Override
	public void schedulingJob(AbstractPollJob pollJob) {
		// Not required
	}

	@Override
	public void processingJobsComplete(long timeTillNextJob) {		
		Display.getDefault().asyncExec (new Runnable () {
		      @Override
			public void run () {
		         viewer.refresh();
		      }
		});
		
	}

	@Override
	public void jobAdded(Job job) {
		// Not required
		
	}
	
}
