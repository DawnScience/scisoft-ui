/*
 * Copyright (c) 2012 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.sda.meta.views;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

import org.dawb.common.ui.monitor.ProgressMonitorWrapper;
import org.dawb.common.ui.util.EclipseUtils;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.dawnsci.analysis.api.io.ILoaderService;
import org.eclipse.january.dataset.IMetadataProvider;
import org.eclipse.january.metadata.IMetadata;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IPartListener;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.IViewSite;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.ui.progress.UIJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.sda.meta.Activator;
import uk.ac.diamond.sda.meta.contribution.MetadataPageContribution;
import uk.ac.diamond.sda.meta.page.DiffractionMetadataComposite;
import uk.ac.diamond.sda.meta.page.IMetadataPage;
import uk.ac.diamond.sda.meta.preferences.PreferenceConstants;
import uk.ac.diamond.sda.meta.utils.MapUtils;

public class MetadataPageView extends ViewPart implements ISelectionListener, IPartListener {

	public final static String ID = "uk.ac.diamond.sda.meta.MetadataPageView"; // This ID is used ("hard-coded") elsewhere, don't change!

	private static final Logger logger = LoggerFactory.getLogger(MetadataPageView.class);

	private IMetadata meta;
	private ArrayList<MetadataPageContribution> pagesRegister = new ArrayList<MetadataPageContribution>();

	private HashMap<String, IMetadataPage> loadedPages = new HashMap<String, IMetadataPage>();
	private HashMap<String, Action> actionRegistary = new HashMap<String, Action>();

	private HashMap<String, String> metatdataPageAssociation = new HashMap<String, String>();
	private String defaultComposite;

	private IToolBarManager toolBarManager;

	private Composite parent;

	private static final String PAGE_EXTENTION_ID = "uk.ac.diamond.sda.meta.metadataPageRegister";

	public MetadataPageView() {
		super();
		getExtentionPoints();
	}

	@Override
	public void init(IViewSite site) throws PartInitException {
		super.init(site);
		getSite().getPage().getWorkbenchWindow().getSelectionService().addSelectionListener(this);
		getSite().getPage().addPartListener(this);
		initializePreferences();
	}

	@Override
	public void dispose() {
		super.dispose();
		getSite().getPage().getWorkbenchWindow().getSelectionService().removeSelectionListener(this);
		getSite().getPage().removePartListener(this);
	}

	private void initializePreferences() {
		IPreferenceStore store = Activator.getDefault().getPreferenceStore();
		defaultComposite = store.getString(PreferenceConstants.defaultPage);
		metatdataPageAssociation = (HashMap<String, String>) MapUtils.getMap(store
				.getString(PreferenceConstants.defaultMetadataAssociation));
		store.addPropertyChangeListener(new IPropertyChangeListener() {

			@Override
			public void propertyChange(PropertyChangeEvent event) {
				if (event.getProperty() == PreferenceConstants.defaultMetadataAssociation)
					metatdataPageAssociation = (HashMap<String, String>) MapUtils
							.getMap(event.getNewValue().toString());
				if (event.getProperty() == PreferenceConstants.defaultPage)
					defaultComposite = event.getProperty().toString();
			}
		});
	}

	private void getExtentionPoints() {
		IExtension[] extentionPoints = Platform.getExtensionRegistry().getExtensionPoint(PAGE_EXTENTION_ID)
				.getExtensions();
		for (int i = 0; i < extentionPoints.length; i++) {
			IExtension extension = extentionPoints[i];
			IConfigurationElement[] configElements = extension.getConfigurationElements();
			for (int j = 0; j < configElements.length; j++) {
				pagesRegister.add(new MetadataPageContribution(configElements[j]));
			}
		}
	}

	private void metadataChanged(final IMetadata meta) {
		// this method should react to the different types of metadata
		UIJob updateActionsForNewMetadata = new UIJob("Update for new metadata") {

			@Override
			public IStatus runInUIThread(IProgressMonitor monitor) {
				toolBarManager.removeAll();
				if (meta == null)
					return Status.CANCEL_STATUS;
				for (MetadataPageContribution mpc : pagesRegister) {
					if (mpc.isApplicableFor(meta)) {
						Action action = pageActionFactory(mpc);
						actionRegistary.put(mpc.getExtentionPointname(), action);
						toolBarManager.add(action);
					}
				}
				toolBarManager.update(false);
				// select the page that was last active for a given metadata

				doDefaultBehaviour();
				return Status.OK_STATUS;
			}
		};
		updateActionsForNewMetadata.schedule();
	}

	private void doDefaultBehaviour() {
		String currentAssociatedView;
		if (metatdataPageAssociation != null && meta!=null && metatdataPageAssociation.containsKey(meta.getClass().toString())) {
			currentAssociatedView = metatdataPageAssociation.get(meta.getClass().toString());
			if (actionRegistary.containsKey(currentAssociatedView))
				actionRegistary.get(currentAssociatedView).run();
		} else {
			if (actionRegistary.containsKey(defaultComposite))
				actionRegistary.get(defaultComposite).run();
		}
	}

	private Action pageActionFactory(final MetadataPageContribution mpc) {
		final Action metadatapage = new Action(mpc.getExtentionPointname()) {

			@Override
			public void run() {

				if (meta == null)
					return;

				try {
					if (!loadedPages.containsKey(mpc.getExtentionPointname())) {
						loadedPages.put(mpc.getExtentionPointname(), mpc.getPage());
					}
				} catch (CoreException e) {
					logger.warn("Could not create " + mpc.getExtentionPointname());
					return;
				}

				UIJob updateComposite = new UIJob("Update Composite") {
					@Override
					public IStatus runInUIThread(IProgressMonitor monitor) {
						if(parent == null || parent.isDisposed())
							return Status.CANCEL_STATUS;
						
						// clear the old composite
						for (Control iterable_element : parent.getChildren()) {
							iterable_element.dispose();
						}

						if (loadedPages.get(mpc.getExtentionPointname()) instanceof DiffractionMetadataComposite) {
							((DiffractionMetadataComposite)loadedPages.get(mpc.getExtentionPointname())).setEditable(false);
						}
						
						loadedPages.get(mpc.getExtentionPointname()).createComposite(parent);
						loadedPages.get(mpc.getExtentionPointname()).setMetaData(meta);
						parent.layout();
						return Status.OK_STATUS;
					}
				};
				updateComposite.schedule();
				metatdataPageAssociation.put(meta.getClass().toString(), mpc.getExtentionPointname());
				updateAssociationsMap();
			}

		};
		metadatapage.setImageDescriptor(mpc.getIcon());
		return metadatapage;
	}

	protected void updateAssociationsMap() {
		IPreferenceStore store = Activator.getDefault().getPreferenceStore();
		store.setValue(PreferenceConstants.defaultMetadataAssociation, MapUtils.getString(metatdataPageAssociation));
	}

	@Override
	public void selectionChanged(IWorkbenchPart part, ISelection selection) {
		if (part instanceof IMetadataProvider)
			try {
				meta = ((IMetadataProvider) part).getFirstMetadata(IMetadata.class);
				metadataChanged(meta);
			} catch (Exception e) {
				logger.error("There was an error reading the metadata from the selection", e);
			}
		else {
			if (selection != null)
				if (selection instanceof StructuredSelection) {
					// this.lastSelection = (StructuredSelection) selection;
					final Object sel = ((StructuredSelection) selection).getFirstElement();

					if (sel == null)
						return;

					if (sel instanceof IFile) {
						final IPath path = ((IFile) sel).getLocation();
						if (path == null)
							return;

						updatePath(path.toOSString());
					} else if (sel instanceof File) {
						final String filePath = ((File) sel).getAbsolutePath();
						updatePath(filePath);
					} else if (sel instanceof IMetadataProvider) {
						try {
							meta = ((IMetadataProvider) sel).getFirstMetadata(IMetadata.class);
							metadataChanged(meta);
						} catch (Exception e) {
							logger.error("Could not capture metadata from selection", e);
						}
					}
				}
		}
	}

	private void updatePath(final String filePath) {
		if (filePath == null)
			return;
		final Job metaJob = new Job("Extra Metadata " + filePath) {

			@Override
			protected IStatus run(IProgressMonitor monitor) {

				final ILoaderService service = PlatformUI.getWorkbench().getService(ILoaderService.class);

				if (service == null)
					return Status.CANCEL_STATUS;

				try {
					meta = service.getMetadata(filePath, new ProgressMonitorWrapper(monitor));
				} catch (Exception e1) {
					logger.debug("Cannot get meta data for " + filePath, e1);
					return Status.CANCEL_STATUS;
				}

				metadataChanged(meta);
				return Status.OK_STATUS;
			}

		};

		metaJob.schedule();
	}

	@Override
	public void partActivated(IWorkbenchPart part) {

		if (part instanceof IMetadataProvider) {
			try {
				meta = ((IMetadataProvider) part).getFirstMetadata(IMetadata.class);
				metadataChanged(meta);
			} catch (Exception e) {
				logger.warn("Could not get metadata from currently active window");
			}
		}
		if (part instanceof IEditorPart) {
			final IEditorPart ed = (IEditorPart) part;
			final IEditorInput in = ed.getEditorInput();
			final String path = EclipseUtils.getFilePath(in);
			updatePath(path);
		}
	}

	@Override
	public void partBroughtToTop(IWorkbenchPart part) {
		if (part instanceof IMetadataProvider) {
			try {
				meta = ((IMetadataProvider) part).getFirstMetadata(IMetadata.class);
				metadataChanged(meta);
			} catch (Exception e) {
				logger.warn("Could not get metadata from currently active window");
			}
		}
	}

	@Override
	public void partClosed(IWorkbenchPart part) {
	}

	@Override
	public void partDeactivated(IWorkbenchPart part) {

	}

	@Override
	public void partOpened(IWorkbenchPart part) {

	}

	@Override
	public void createPartControl(Composite parent) {
		// this is very light weight as most of the widgets come from the extension point
		this.parent = parent;
		toolBarManager = getViewSite().getActionBars().getToolBarManager();
	}

	@Override
	public void setFocus() {
		// do nothing
	}

}
