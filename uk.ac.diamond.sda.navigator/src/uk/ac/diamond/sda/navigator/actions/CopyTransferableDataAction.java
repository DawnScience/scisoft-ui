/*
 * Copyright (c) 2012 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.sda.navigator.actions;

import org.eclipse.dawnsci.analysis.api.io.IDataHolder;
import org.eclipse.dawnsci.slicing.api.data.ITransferableDataObject;
import org.eclipse.dawnsci.slicing.api.data.ITransferableDataService;
import org.eclipse.january.IMonitor;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IWorkbenchPage;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.io.LoaderFactory;
import uk.ac.diamond.sda.intro.navigator.NavigatorRCPActivator;
import uk.ac.diamond.sda.navigator.srs.SRSTreeData;

public class CopyTransferableDataAction extends Action {

	private static final Logger logger = LoggerFactory.getLogger(CopyTransferableDataAction.class);
	@SuppressWarnings("unused")
	private IWorkbenchPage page;
	private SRSTreeData data;
	private ISelectionProvider provider;

	/**
	 * Construct the OpenSRSAction with the given page.
	 * 
	 * @param p
	 *            The page to use as context to open the editor.
	 * @param selectionProvider
	 *            The selection provider
	 */
	public CopyTransferableDataAction(IWorkbenchPage p, ISelectionProvider selectionProvider) {
		setText("Copy Data"); //$NON-NLS-1$
		page = p;
		provider = selectionProvider;
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.jface.action.Action#isEnabled()
	 */
	@Override
	public boolean isEnabled() {
		ISelection selection = provider.getSelection();
		if (!selection.isEmpty()) {
			IStructuredSelection sSelection = (IStructuredSelection) selection;
			if (sSelection.size() == 1 && sSelection.getFirstElement() instanceof SRSTreeData) {
				data = ((SRSTreeData) sSelection.getFirstElement());
				setText("Copy '"+data.getName()+"' it may then be paste in the 'Data' view");
				return true;
			}
		}
		return false;
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.jface.action.Action#run()
	 */
	@Override
	public void run() {
		try {
			if (isEnabled()) {
				final String    path = data.getFile().getRawLocation().toOSString();
				final IDataHolder hd = LoaderFactory.getData(path, true, false, new IMonitor.Stub());

				ITransferableDataService service = NavigatorRCPActivator.getService(ITransferableDataService.class);
				final ITransferableDataObject  object  = service.createData(hd, hd.getMetadata(), data.getName());
				service.setBuffer(object);
			}
		} catch (Exception e) {
			logger.error("Cannot copy data as transferable.", e);
		}
	}
}
