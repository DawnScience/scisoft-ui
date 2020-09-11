/*
 * Copyright (c) 2012 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.analysis.rcp.views;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.commons.io.FileUtils;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IContributionItem;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.ui.menus.CommandContributionItem;
import org.eclipse.ui.part.ViewPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.rcp.AnalysisRCPActivator;
import uk.ac.diamond.scisoft.analysis.rcp.handlers.AsciiMonitorAction;

/**
 *
 */
public class AsciiTextView extends ViewPart {

	private static final Logger logger = LoggerFactory.getLogger(AsciiTextView.class);
	
	/**
	 * 
	 */
	public static final String ID = "uk.ac.diamond.scisoft.analysis.rcp.views.AsciiTextView"; //$NON-NLS-1$
	
	private StyledText text;
	private boolean monitoringFile = false;
	private File    file;
	private Timer   timer;

	private Action saveAction;
	private Action wrapAction;

	/**
	 * 
	 */
	public AsciiTextView() {
	}

	/**
	 * Create contents of the view part.
	 * @param parent
	 */
	@Override
	public void createPartControl(Composite parent) {
		Composite container = new Composite(parent, SWT.NONE);
		container.setLayout(new FillLayout(SWT.HORIZONTAL));
		{
			text = new StyledText(container, SWT.BORDER | SWT.READ_ONLY | SWT.H_SCROLL | SWT.V_SCROLL | SWT.CANCEL | SWT.MULTI | SWT.DOUBLE_BUFFERED);
		}

		createActions();
		initializeToolBar();
		initializeMenu();
	}

	/**
	 * Create the actions.
	 */
	private void createActions() {
		// Create the actions
		saveAction = new Action() {
			@Override
			public void run() {
				saveText();
			}
		};
		saveAction.setToolTipText("Save text");
		saveAction.setImageDescriptor(AnalysisRCPActivator.getImageDescriptor("icons/script_save.png"));

		wrapAction = new Action("Wrap text", IAction.AS_CHECK_BOX) {
			@Override
			public void run() {
				wrapText();
			}
		};
		wrapAction.setToolTipText("Toggle wrap text");
		wrapAction.setImageDescriptor(AnalysisRCPActivator.getImageDescriptor("icons/wordwrap.png"));
	}

	/**
	 * Initialize the toolbar.
	 */
	private void initializeToolBar() {
		IToolBarManager toolbarManager = getViewSite().getActionBars()
				.getToolBarManager();
		toolbarManager.add(saveAction);
		toolbarManager.add(wrapAction);
	}

	/**
	 * Initialize the menu.
	 */
	private void initializeMenu() {
		getViewSite().getActionBars().getMenuManager();
	}

	@Override
	public void setFocus() {
		text.setFocus();
	}

	/**
	 * DO NOT load large files with this method. It is all read to memory.
	 * @param file
	 */
	public void load(File file) {
		
		monitoringFile = false;
		updateMonitoring();
		setPartName(file.getName());
		this.file = file;
		refreshFile();
	}

	/**
	 * Set text data to display
	 */
	public void setData(String textData) {
		text.setText(textData);
	}

	public void wrapText() {
		text.setWordWrap(wrapAction.isChecked());
	}

	/**
	 * Save text to file
	 */
	public void saveText() {
		FileDialog dialog = new FileDialog(getSite().getShell(), SWT.SAVE);
		dialog.setOverwrite(true);
		dialog.setFileName("text.txt");
		dialog.setFilterExtensions(new String[] { ".txt" });
		dialog.setFilterNames(new String[] { "Ascii text" });

		String fileName = dialog.open();
		if (fileName == null) {
			return;
		}

		try {
			final PrintStream stream = new PrintStream(fileName);

			getSite().getShell().getDisplay().asyncExec(new Runnable() {

				@Override
				public void run() {
					stream.append(text.getText());
					stream.close();
				}
			});
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}

	private String refreshFile() {
		try {
			final String allText = FileUtils.readFileToString(file, Charset.defaultCharset());
			text.setText(allText);
			return allText;
		} catch (IOException e) {
			logger.error("Cannot read file "+file, e);
			return "";
		}
	}

	/**
	 * Can switch on / off monitoring here
	 */
	public void toggleMonitor() {
		this.monitoringFile = !monitoringFile;
		updateMonitoring();
	}

	private void updateMonitoring() {
		final IToolBarManager toolbarManager = getViewSite().getActionBars().getToolBarManager();
		final IContributionItem[] items = toolbarManager.getItems();
		
		// TODO Find out how to do this properly. Looked many times but never seem to find out
		// something good. Paul may have not on XYPlot.
		for (int i = 0; i < items.length; i++) {
			if (items[i] instanceof CommandContributionItem) {
				final CommandContributionItem c = (CommandContributionItem)items[i];
				if (c.getId().equals(AsciiMonitorAction.ID)) {
					try {
						final Method method = CommandContributionItem.class.getDeclaredMethod("setChecked", boolean.class);
						method.setAccessible(true);
						method.invoke(c, monitoringFile);
					} catch (Exception ignored) {
						// Not critical to do
					}
				}
			}
		}
		
		if (monitoringFile) {
			this.timer = new Timer("Update text timer", false);		
			this.timer.schedule(new TimerTask(){
				@Override
				public void run() {
					getSite().getShell().getDisplay().asyncExec(new Runnable() {
						@Override
						public void run() {
							final String allText = refreshFile();
							text.setSelection(allText.lastIndexOf('\n')+1);
						}
					});
					// TODO System preference needed one day.
				}}, 0, 5000);
		} else {
			if (timer!=null) this.timer.cancel();
		}
	}

	@Override
	public void dispose() {
		if (timer!=null) this.timer.cancel();
		super.dispose();
	}
	
}
