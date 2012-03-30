/*-
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
package uk.ac.diamond.sda.exporter;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.CheckboxTableViewer;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.Text;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.io.DataHolder;
import uk.ac.diamond.scisoft.analysis.io.IMetaData;
import uk.ac.diamond.scisoft.analysis.io.LoaderFactory;
import uk.ac.gda.common.rcp.util.EclipseUtils;
import uk.ac.gda.monitor.ProgressMonitorWrapper;

/**
 * Adapted from org.dawb.workbench.convert
 *
 */
public class ExportWizardPage1 extends WizardPage {

	private static final org.slf4j.Logger logger = LoggerFactory
			.getLogger(ExportWizardPage1.class);

	private CheckboxTableViewer checkboxTableViewer;
	private String[] dataSetNames;

	private ISelection selection;
	
	private String delimiter="";
	public Text delimiterText;
	public Combo combo;

	/**
	 * Create the wizard.
	 */
	public ExportWizardPage1() {
		super("wizardPage");
		setTitle("Export Data");
		setDescription("Convert data from synchrotron formats and compressed files to common simple data formats.");
		dataSetNames = new String[] { "Loading..." };
	}

	/**
	 * Create contents of the wizard.
	 * 
	 * @param parent
	 */
	@Override
	public void createControl(Composite parent) {

		Composite container = new Composite(parent, SWT.NULL);

		setControl(container);
		container.setLayout(new GridLayout(1, false));

		Composite top = new Composite(container, SWT.NONE);
		top.setLayout(new GridLayout(4, false));
		top.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 1, 1));

		Label convertLabel = new Label(top, SWT.NONE);
		convertLabel.setBounds(0, 0, 68, 17);
		convertLabel.setText("Convert to");

		combo = new Combo(top, SWT.READ_ONLY);
		combo.setItems(new String[] { "Simple ASCII output", "ASCII output with subfolders" });
		combo.setToolTipText("Convert to file type by file extension");
		combo.setBounds(0, 0, 189, 29);
		combo.select(1);

		Label delimeterLabel = new Label(top, SWT.NONE);
		delimeterLabel.setBounds(0, 0, 68, 17);
		delimeterLabel.setText("Delimeter:");

		delimiterText = new Text(top, SWT.BORDER | SWT.FULL_SELECTION);
		delimiterText.setBounds(0, 0, 189, 29);
		delimiterText.setToolTipText("Type a delimeter character or set of characters");

		Composite main = new Composite(container, SWT.NONE);
		main.setLayout(new GridLayout(1, false));
		main.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));

		this.checkboxTableViewer = CheckboxTableViewer.newCheckList(main,
				SWT.BORDER | SWT.FULL_SELECTION);
		Table table = checkboxTableViewer.getTable();
		table.setToolTipText("Select data to export to output file.");
		table.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));

		this.selection = EclipseUtils.getActivePage().getSelection();

		checkboxTableViewer.setContentProvider(new IStructuredContentProvider() {
			@Override
			public void dispose() {}

			@Override
			public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {}

			@Override
			public Object[] getElements(Object inputElement) {
				return dataSetNames;
			}
		});
		checkboxTableViewer.setInput(new Object());
		checkboxTableViewer.setAllGrayed(true);

		// We populate the names later using a wizard task.
		parent.getDisplay().asyncExec(new Runnable() {
			@Override
			public void run() {
				try {
					getDataSetNames();
				} catch (Exception e) {
					logger.error("Cannot extract data sets!", e);
				}
			}
		});
	}

	protected void getDataSetNames() throws Exception {

		getContainer().run(true, true, new IRunnableWithProgress() {
			@Override
			public void run(IProgressMonitor monitor)
					throws InvocationTargetException, InterruptedException {

				if (selection instanceof StructuredSelection) {
					StructuredSelection s = (StructuredSelection) selection;
					final Object o = s.getFirstElement();
					if (o instanceof IFile) {
						try {

							// Attempt to use meta data, save memory
							final IFile file = (IFile) o;
							final IMetaData meta = LoaderFactory.getMetaData(
									file.getLocation().toOSString(),
									new ProgressMonitorWrapper(monitor));
							if (meta != null) {
								final Collection<String> names = meta
										.getDataNames();
								if (names != null) {
									setDataNames(names.toArray(new String[names
											.size()]));
									return;
								}
							}

							// Clobber the memory!
							final DataHolder holder = LoaderFactory.getData(
									file.getLocation().toOSString(),
									new ProgressMonitorWrapper(monitor));
							final List<String> names = new ArrayList<String>(
									holder.getMap().keySet());
							Collections.sort(names);
							setDataNames(names.toArray(new String[names.size()]));
							return;

						} catch (Exception ne) {
							throw new InvocationTargetException(ne);
						}
					}
				}

			}
		});
	}

	protected void setDataNames(String[] array) {
		dataSetNames = array;
		getContainer().getShell().getDisplay().asyncExec(new Runnable() {
			@Override
			public void run() {
				checkboxTableViewer.getTable().setEnabled(true);
				checkboxTableViewer.refresh();
				checkboxTableViewer.setAllChecked(true);
				checkboxTableViewer.setAllGrayed(false);
			}
		});
	}

	protected Object[] getSelected() {
		return checkboxTableViewer.getCheckedElements();
	}

	public IFile getFile() {
		if (selection instanceof StructuredSelection) {
			StructuredSelection s = (StructuredSelection) selection;
			final Object o = s.getFirstElement();
			if (o instanceof IFile)
				return (IFile) o;
		}
		return null;
	}

	public String getDelimeter(){
		return delimiter;
	}
}
