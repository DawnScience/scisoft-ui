/*
 * Copyright (c) 2012 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.feedback;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.ActionContributionItem;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ColumnViewerToolTipSupport;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.ControlListener;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IPartListener;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.ui.progress.UIJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.feedback.attachment.AttachedFileContentProvider;
import uk.ac.diamond.scisoft.feedback.attachment.AttachedFileEditingSupport;
import uk.ac.diamond.scisoft.feedback.attachment.AttachedFileLabelProvider;
import uk.ac.diamond.scisoft.feedback.jobs.FeedbackJob;
import uk.ac.diamond.scisoft.feedback.utils.FeedbackConstants;

public class FeedbackView extends ViewPart implements IPartListener {

	private static Logger logger = LoggerFactory.getLogger(FeedbackView.class);

	// this is the default to the java property "org.dawnsci.feedbackmail"
	private String destinationEmail = System.getProperty(FeedbackConstants.RECIPIENT_PROPERTY, FeedbackConstants.getMailTo());
	private String destinationName = "DAWN developers";

	/**
	 * The ID of the view as specified by the extension.
	 */
	public static final String ID = "uk.ac.diamond.scisoft.feedback.FeedbackView";
	private Action feedbackAction;
	private Action attachAction;
	private Text messageText;
	private Text subjectText;
	private Label attachLabel;

	private List<File> attachedFiles = new ArrayList<>();
	private Button btnSendFeedback;
	private TableViewer tableViewer;

	private FeedbackJob feedbackJob;

	private static final String ATTACH_LABEL = "Attached Files";

	/**
	 * The constructor.
	 */
	public FeedbackView() {
	}

	/**
	 * This is a callback that will allow us to create the viewer and initialise it.
	 */
	@Override
	public void createPartControl(Composite parent) {
		final ScrolledComposite scrollComposite = new ScrolledComposite(parent, SWT.H_SCROLL | SWT.V_SCROLL);
		scrollComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		scrollComposite.setExpandVertical(true);
		scrollComposite.setExpandHorizontal(true);
		scrollComposite.addControlListener(ControlListener.controlResizedAdapter(e -> {
			Rectangle r = scrollComposite.getClientArea();
			scrollComposite.setMinSize(parent.computeSize(r.width, SWT.DEFAULT));
		}));

		final Composite content = new Composite(scrollComposite, SWT.NONE);
		content.setLayout(new GridLayout(1, false));
		content.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		scrollComposite.setContent(content);

		makeActions();

		Label lblSubject = new Label(content, SWT.NONE);
		lblSubject.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, false, false, 1, 1));
		lblSubject.setText("Summary");

		subjectText = new Text(content, SWT.BORDER | SWT.SINGLE);
		subjectText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));

		final String subject = Activator.getDefault() != null ? Activator.getDefault().getPreferenceStore()
				.getString(FeedbackConstants.SUBJ_PREF) : null;
		if (subject != null && !"".equals(subject))
			subjectText.setText(subject);

		Label lblComment = new Label(content, SWT.NONE);
		lblComment.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, false, false, 1, 1));
		lblComment.setText("Comment");

		messageText = new Text(content, SWT.BORDER | SWT.MULTI | SWT.WRAP | SWT.V_SCROLL);
		GridData gd = new GridData(SWT.FILL, SWT.CENTER, true, false);
		gd.heightHint = 200;
		gd.minimumHeight = 200;
		messageText.setLayoutData(gd);

		attachLabel = new Label(content, SWT.NONE);
		attachLabel.setText("Attached Files");

		tableViewer = new TableViewer(content, SWT.FULL_SELECTION | SWT.SINGLE | SWT.BORDER);
		ColumnViewerToolTipSupport.enableFor(tableViewer);
		createColumns(tableViewer);
//		tableViewer.getTable().setHeaderVisible(true);
		tableViewer.setContentProvider(new AttachedFileContentProvider());
		tableViewer.getControl().setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		attachedFiles = new ArrayList<>();
		tableViewer.setInput(attachedFiles);
		tableViewer.refresh();

		Composite actionComp = new Composite(content, SWT.NONE);
		actionComp.setLayout(new GridLayout(3, false));
		actionComp.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		ActionContributionItem attachAci = new ActionContributionItem(attachAction);
		attachAci = new ActionContributionItem(attachAci.getAction());
		attachAci.fill(actionComp);
		Button btnBrowseFile = (Button) attachAci.getWidget();
		btnBrowseFile.setText("Attach Files");
		btnBrowseFile.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1));

		Label emptyLabel = new Label(actionComp, SWT.SINGLE);
		emptyLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		ActionContributionItem sendAci = new ActionContributionItem(feedbackAction);
		sendAci = new ActionContributionItem(sendAci.getAction());
		sendAci.fill(actionComp);
		btnSendFeedback = (Button) sendAci.getWidget();
		btnSendFeedback.setText("Send Feedback");
		btnSendFeedback.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));


		hookContextMenu();
		contributeToActionBars();
		getSite().getPage().addPartListener(this);
	}

	private void createColumns(TableViewer tv) {
		int c =0;
		TableViewerColumn tvc = new TableViewerColumn(tv, SWT.NONE);
		tvc.setEditingSupport(new AttachedFileEditingSupport(tv, c));
		tvc.setLabelProvider(new AttachedFileLabelProvider(c));
		TableColumn tc = tvc.getColumn();
		tc.setText("File Name");
		tc.setWidth(400);
		c++;

		tvc = new TableViewerColumn(tv, SWT.NONE);
		tvc.setEditingSupport(new AttachedFileEditingSupport(tv, c));
		tvc.setLabelProvider(new AttachedFileLabelProvider(c));
		tc = tvc.getColumn();
		tc.setText("Size");
		tc.setWidth(80);
		c++;

		tvc = new TableViewerColumn(tv, SWT.NONE);
		tvc.setEditingSupport(new AttachedFileEditingSupport(tv, c));
		tvc.setLabelProvider(new AttachedFileLabelProvider(c));
		tc = tvc.getColumn();
		tc.setText("Delete");
		tc.setWidth(60);
		c++;

		tvc = new TableViewerColumn(tv, SWT.NONE);
		tvc.setEditingSupport(new AttachedFileEditingSupport(tv, c));
		tvc.setLabelProvider(new AttachedFileLabelProvider(c));
		tc = tvc.getColumn();
		tc.setText("Copy");
		tc.setToolTipText("Copy to clipboard");
		tc.setWidth(20);
	}

	private void hookContextMenu() {
		MenuManager menuMgr = new MenuManager("#PopupMenu");
		menuMgr.setRemoveAllWhenShown(true);
		menuMgr.addMenuListener(manager -> FeedbackView.this.fillContextMenu(manager));
	}

	private void contributeToActionBars() {
		IActionBars bars = getViewSite().getActionBars();
//		fillLocalPullDown(bars.getMenuManager());
		fillLocalToolBar(bars.getToolBarManager());
	}

//	private void fillLocalPullDown(IMenuManager manager) {
//		manager.add(attachAction);
//		manager.add(feedbackAction);
//	}
//
	private void fillContextMenu(IMenuManager manager) {
		manager.add(attachAction);
		manager.add(feedbackAction);
		// Other plug-ins can contribute there actions here
		manager.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
	}

	private void fillLocalToolBar(IToolBarManager manager) {
		manager.add(attachAction);
		manager.add(feedbackAction);
	}

	private void makeActions() {
		feedbackAction = new Action() {
			private String subjectvalue;
			private String messagevalue;

			@Override
			public void run() {
				UIJob formUIJob = new UIJob("Getting Form data") {
					@Override
					public IStatus runInUIThread(IProgressMonitor monitor) {
						subjectvalue = subjectText.getText();
						messagevalue = messageText.getText();
						return Status.OK_STATUS;
					}
				};
				formUIJob.addJobChangeListener(new JobChangeAdapter(){
					@Override
					public void done(IJobChangeEvent event) {
						feedbackJob = new FeedbackJob("Sending feedback to " + destinationName, 
								subjectvalue, messagevalue, destinationEmail,
								attachedFiles);
						feedbackJob.addJobChangeListener(getJobChangeListener());
						feedbackJob.setUser(true);
						feedbackJob.schedule();
					}
				});
				formUIJob.setUser(true);
				formUIJob.schedule();
			}
		};
		feedbackAction.setText("Send Feedback");
		feedbackAction.setToolTipText("Send Feedback");
		feedbackAction.setImageDescriptor(Activator.getImageDescriptor("icons/mailedit.gif"));

		attachAction = new Action() {
			@Override
			public void run() {
				FileDialog fd = new FileDialog(Display.getDefault().getActiveShell());
				fd.setFilterNames(new String [] {"Log Files (.log)", "Text Files (.txt)", "All Files (*.*)"});
				fd.setFilterExtensions(new String [] {"*?.log", "*?.txt", "*?.*"}); 
				fd.setText("Attach selected file to your feedback message");
				String fileName = fd.open();
				if (fileName != null) {
					File file = new File(fileName);
					long size = file.length();
					if (size > FeedbackConstants.MAX_SIZE) {
						MessageBox messageDialog = new MessageBox(PlatformUI.getWorkbench()
								.getActiveWorkbenchWindow().getShell(), SWT.ICON_WARNING | SWT.OK | SWT.CANCEL);
						messageDialog.setText("File not attached");
						messageDialog.setMessage("The file is too big (>10MB)");
						messageDialog.open();
					} else {
						if (!attachedFiles.contains(file))
							attachedFiles.add(file);
						tableViewer.refresh();
						tableViewer.getControl().requestLayout();
					}
				}
				attachLabel.setText(ATTACH_LABEL);
			}
		};
		attachAction.setText("Attach files");
		attachAction.setToolTipText("Attach file(s) to your feedback message");
		attachAction.setImageDescriptor(Activator.getImageDescriptor("icons/attach.png"));
	}

	private JobChangeAdapter getJobChangeListener(){
		return new JobChangeAdapter() {
			@Override
			public void running(IJobChangeEvent event) {
				Display.getDefault().asyncExec(() -> {
					btnSendFeedback.setText("Sending");
					feedbackAction.setEnabled(false);
				});
			}

			@Override
			public void done(final IJobChangeEvent event) {
				final String message = event.getResult().getMessage();
				Display.getDefault().asyncExec(() -> {
					if (event.getResult().isOK()) {
						messageText.setText("");
						Display.getDefault().asyncExec(() -> MessageDialog.openInformation(PlatformUI.getWorkbench()
								.getActiveWorkbenchWindow().getShell(),
								"Feedback successfully sent", message));
					} else {
						MessageBox messageDialog = new MessageBox(PlatformUI.getWorkbench()
								.getActiveWorkbenchWindow().getShell(), SWT.ICON_WARNING | SWT.OK
								| SWT.CANCEL);
						messageDialog.setText("Feedback not sent!");

						messageDialog.setMessage(message);
						int result = messageDialog.open();

						if (message.startsWith("Please set") && result == SWT.OK) {
							Display.getDefault().asyncExec(() -> {
								try {
									PlatformUI.getWorkbench().getBrowserSupport().getExternalBrowser()
											.openURL(new URL("mailto:"));
								} catch (PartInitException e) {
									logger.error("Error opening browser:", e);
								} catch (MalformedURLException e) {
									logger.error("Error - Malformed URL:", e);
								}
							});
						}
					}
					btnSendFeedback.setText("Send Feedback");
					feedbackAction.setEnabled(true);
					attachLabel.setText(ATTACH_LABEL);
				});
			}

			@Override
			public void aboutToRun(IJobChangeEvent event) {
				Display.getDefault().asyncExec(() -> {
					btnSendFeedback.setText("Sending");
					feedbackAction.setEnabled(false);
				});
			}
		};
	}

	private static final String LOG_DIR_PROP = "java.io.tmpdir";
	private static final String DAWNLOG_SUFFIX = "-dawn.log";

	private void autoAttachLogFile(List<File> attachedFiles) throws IllegalStateException {
		String logLocation = System.getProperty(FeedbackConstants.DAWN_LOG_PROPERTY);
		File log;
		if (logLocation == null) {
			String logDir = System.getProperty(LOG_DIR_PROP);
			log = new File(logDir, System.getProperty("user.name") + DAWNLOG_SUFFIX);
		} else {
			log = new File(logLocation);
		}
		logger.debug("Log file location: {}", log);
		long size = log.length();
		if (log.exists()) {
			if (size == 0) {
				logger.warn("Log file is empty: {}", log);
			} else if (size < FeedbackConstants.MAX_SIZE) {
				if (!attachedFiles.contains(log)) {
					attachedFiles.add(log);
				}
			} else {
				throw new IllegalStateException("Log file is too big: " + log.getAbsolutePath());
			}
		} else if(!log.exists()) {
			throw new IllegalStateException("Log file does not exist: " + log.getAbsolutePath());
		}
	}

	@Override
	public void dispose() {
		getSite().getPage().removePartListener(this);
	}

	/**
	 * Passing the focus request to the viewer's control.
	 */
	@Override
	public void setFocus() {
		messageText.setFocus();
	}

	@Override
	public void partActivated(IWorkbenchPart part) {
		attachLogFile();
	}

	@Override
	public void partBroughtToTop(IWorkbenchPart part) {
		// do nothing
	}

	@Override
	public void partClosed(IWorkbenchPart part) {
		// do nothing
	}

	@Override
	public void partDeactivated(IWorkbenchPart part) {
		// do nothing
	}

	@Override
	public void partOpened(IWorkbenchPart part) {
		// do nothing
	}

	private void attachLogFile() {
		if (attachLabel != null && tableViewer != null) {
			//add the log file to the input of the tableviewer
			try {
				autoAttachLogFile(attachedFiles);
			} catch (Exception e1) {
				attachLabel.setText(ATTACH_LABEL + ": Error attaching log file (" + e1.getMessage() + ")");
			}
			tableViewer.refresh();
		}
	}
}
