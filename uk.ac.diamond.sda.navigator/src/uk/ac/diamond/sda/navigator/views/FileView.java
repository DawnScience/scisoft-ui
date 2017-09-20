/*
 * Copyright (c) 2012 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.sda.navigator.views;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.dawb.common.ui.menu.CheckableActionGroup;
import org.dawb.common.ui.util.EclipseUtils;
import org.dawb.common.util.io.IOpenFileAction;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.dawnsci.plotting.api.image.IFileIconService;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.fieldassist.ContentProposalAdapter;
import org.eclipse.jface.fieldassist.IContentProposal;
import org.eclipse.jface.fieldassist.IContentProposalListener;
import org.eclipse.jface.fieldassist.TextContentAdapter;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferenceDialog;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TreePath;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.TreeViewerColumn;
import org.eclipse.richbeans.widgets.content.FileContentProposalProvider;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DragSource;
import org.eclipse.swt.dnd.DragSourceAdapter;
import org.eclipse.swt.dnd.DragSourceEvent;
import org.eclipse.swt.dnd.FileTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.TreeColumn;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.IViewSite;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.dialogs.PreferencesUtil;
import org.eclipse.ui.part.ViewPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.utils.OSUtils;
import uk.ac.diamond.sda.intro.navigator.NavigatorRCPActivator;
import uk.ac.diamond.sda.navigator.preference.FileNavigatorPreferenceConstants;
import uk.ac.diamond.sda.navigator.util.NIOUtils;

/**
 * This class navigates a file system and remembers where you last left it. 
 * 
 * It is lazy in loading the file tree.
 *
 */
public final class FileView extends ViewPart implements IFileView {

	public static final String ID = "uk.ac.diamond.sda.navigator.views.FileView";
	
    private static final Logger logger = LoggerFactory.getLogger(FileView.class);
	
	private TreeViewer tree;

	private Path savedSelection;
	private Text filePath;
	
	public FileView() {
		super();
	}
	
	@Override
	public void init(IViewSite site, IMemento memento) throws PartInitException {
		
		super.init(site, memento);
		
		String path = null;
		if (memento!=null) path = memento.getString("DIR");
		if (path==null) path = System.getProperty("uk.ac.diamond.sda.navigator.default.file.view.location");
		if (path==null) path = System.getProperty("user.home");
		
		if (path!=null){
			savedSelection = Paths.get(path);
		}
		
	}

	@Override
	public void saveState(IMemento memento) {
		
		if (memento==null) return;
		if ( getSelectedPath() != null ) {
		    final String path = getSelectedPath().toAbsolutePath().toString();
		    memento.putString("DIR", path);
		}
	}

	/**
	 * Get the file path selected
	 * 
	 * @return String
	 */
	@Override
	public Path getSelectedPath() {
		Path sel = (Path)((IStructuredSelection)tree.getSelection()).getFirstElement();
		if (sel==null) sel = savedSelection;
		return sel;
	}
	
	/**
	 * Get the file paths selected
	 * 
	 * @return String[]
	 */
	public String[] getSelectedPaths() {
		
		Object[] objects = ((IStructuredSelection)tree.getSelection()).toArray();
		if (tree.getSelection()==null || tree.getSelection().isEmpty()) 
			objects = new Object[]{savedSelection};
		
		String absolutePaths [] = new String[objects.length];
		for (int i=0; i < objects.length; i++) {
			absolutePaths[i] = ((Path) (objects[i])).toAbsolutePath().toString();
		}
		return absolutePaths;
	}
	
	private boolean updatingTextFromTreeSelections=true;
	
	@Override
	public void createPartControl(final Composite parent) {
		
		parent.setLayout(new GridLayout(1, false));
		
		final Composite top = new Composite(parent, SWT.NONE);
		top.setLayout(new GridLayout(2, false));
		top.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
		
		final Label fileLabel = new Label(top, SWT.NONE);
		fileLabel.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));
		try {
			IFileIconService service = FileIconServiceHolder.getFileIconService();
			final Image       icon    = service.getIconForFile(OSUtils.isWindowsOS() ? "C:/Windows/" : "/");
			fileLabel.setImage(icon);
		} catch (Exception e) {
			logger.error("Cannot get icon for system root!", e);
		}
		
		this.filePath = new Text(top, SWT.BORDER);
		if (savedSelection!=null) filePath.setText(savedSelection.toAbsolutePath().toString());
		filePath.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		final FileContentProposalProvider   prov = new FileContentProposalProvider();
		final TextContentAdapter         adapter = new TextContentAdapter();
		final ContentProposalAdapter ad = new ContentProposalAdapter(filePath, adapter, prov, null, null);
		ad.setProposalAcceptanceStyle(ContentProposalAdapter.PROPOSAL_REPLACE);
		ad.addContentProposalListener(new IContentProposalListener() {		
			@Override
			public void proposalAccepted(IContentProposal proposal) {
				final String path = proposal.getContent();
				try {
					updatingTextFromTreeSelections=false;
					setSelectedFile(path);
				} finally {
					updatingTextFromTreeSelections=true;
				}
			}
		});
	
		filePath.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				if (e.character=='\t') {
					if (ad.isProposalPopupOpen()) {
						if (prov.getFirstPath()!=null) {
							final String path = prov.getFirstPath();
							
							filePath.getDisplay().asyncExec(new Runnable() {
								@Override
								public void run() {
									try {
										updatingTextFromTreeSelections=false;
										filePath.setFocus();
										filePath.setText(path);
										setSelectedFile(path);
										filePath.setFocus();
										filePath.setSelection(path.length(), path.length());
									} finally {
										updatingTextFromTreeSelections=true;
									}
								}
							});
						}
					}
				} else if (e.character=='\t' || e.character=='\n'|| e.character=='\r') {
					final String path = filePath.getText();
					try {
						updatingTextFromTreeSelections=false;
						setSelectedFile(path);
					} finally {
						updatingTextFromTreeSelections=true;
					}
				}
			}
		});
		
		tree = new TreeViewer(parent, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION | SWT.BORDER |SWT.VIRTUAL);
		tree.getTree().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		tree.getTree().setHeaderVisible(true);
		tree.setUseHashlookup(true);
		
		tree.addSelectionChangedListener(new ISelectionChangedListener() {			
			@Override
			public void selectionChanged(SelectionChangedEvent event) {
				if (!updatingTextFromTreeSelections) return;
				final Path file = getSelectedPath();
				if (file!=null &&  Files.isDirectory(file)) {
					try {
						ad.setEnabled(false);
					    filePath.setText(file.toAbsolutePath().toString());
					    filePath.setSelection(filePath.getText().length());
					} finally {
						ad.setEnabled(true);
					}
				}
			}
		});

		final String[] titles = { "Name", "Date", "Type", "Size", "Comment", "Scan Command" };

		IPreferenceStore store = NavigatorRCPActivator.getDefault().getPreferenceStore();
		boolean showDate = store.getBoolean(FileNavigatorPreferenceConstants.SHOW_DATE_COLUMN);
		boolean showType = store.getBoolean(FileNavigatorPreferenceConstants.SHOW_TYPE_COLUMN);
		boolean showSize = store.getBoolean(FileNavigatorPreferenceConstants.SHOW_SIZE_COLUMN);
		boolean showComment = store.getBoolean(FileNavigatorPreferenceConstants.SHOW_COMMENT_COLUMN);
		boolean showScanCmd = store.getBoolean(FileNavigatorPreferenceConstants.SHOW_SCANCMD_COLUMN);

		// we listen to the preference store property changes
		store.addPropertyChangeListener(new IPropertyChangeListener() {
			@Override
			public void propertyChange(PropertyChangeEvent event) {
				if (event.getProperty().equals(FileNavigatorPreferenceConstants.SHOW_DATE_COLUMN)) {
					setColumnVisible(1, 120, (Boolean) event.getNewValue());
				} else if (event.getProperty().equals(FileNavigatorPreferenceConstants.SHOW_TYPE_COLUMN)) {
					setColumnVisible(2, 80, (Boolean) event.getNewValue());
				} else if (event.getProperty().equals(FileNavigatorPreferenceConstants.SHOW_SIZE_COLUMN)) {
					setColumnVisible(3, 150, (Boolean) event.getNewValue());
				} else if (event.getProperty().equals(FileNavigatorPreferenceConstants.SHOW_COMMENT_COLUMN)) {
					setColumnVisible(4, 250, (Boolean) event.getNewValue());
				} else if (event.getProperty().equals(FileNavigatorPreferenceConstants.SHOW_SCANCMD_COLUMN)) {
					setColumnVisible(5, 300, (Boolean) event.getNewValue());
				}
			}
		});

		int dateWidth = showDate ? 120 : 0;
		int typeWidth = showType ? 80 : 0;
		int sizeWidth = showSize ? 150 : 0;
		int commentWidth = showComment ? 250 : 0;
		int scanCmdWidth = showScanCmd ? 300 : 0;

		final int[]    widths = { 350, dateWidth, typeWidth, sizeWidth, commentWidth, scanCmdWidth };
		TreeViewerColumn tVCol;
		for (int i = 0; i < titles.length; i++) {
			tVCol = new TreeViewerColumn(tree, SWT.NONE);
			TreeColumn tCol = tVCol.getColumn();
			tCol.setText(titles[i]);
			tCol.setWidth(widths[i]);
			try {
				tVCol.setLabelProvider(new FileLabelProvider(tree, i));
			} catch (Exception e1) {
				logger.error("Cannot create label provider "+i, e1);
			}
		}
		getSite().setSelectionProvider(tree);
		
		createContent(true);

		// Make drag source, it can then drag into projects
		final DragSource dragSource = new DragSource(tree.getControl(), DND.DROP_MOVE| DND.DROP_DEFAULT| DND.DROP_COPY);
		dragSource.setTransfer(new Transfer[] { FileTransfer.getInstance () });
		dragSource.addDragListener(new DragSourceAdapter() {
			@Override
			public void dragSetData(DragSourceEvent event){
				if (getSelectedPaths()==null) return;
				event.data = getSelectedPaths();
			}
		});
		
		// Add ability to open any file double clicked on (folders open in Image Monitor View)
		tree.getTree().addListener(SWT.MouseDoubleClick, new Listener() {

             @Override
			public void handleEvent(Event event) {
                 openSelectedFile();
             }
         });
		
		tree.getTree().addKeyListener(new KeyListener() {		
			@Override
			public void keyReleased(KeyEvent e) {
				
			}
			
			@Override
			public void keyPressed(KeyEvent e) {
				if (e.character == '\n' || e.character == '\r') {
					openSelectedFile();
				}
			}
		});

		createRightClickMenu();
		createActions();

		if (savedSelection!=null) {
			if (savedSelection.toFile().exists()) {
				tree.setSelection(new StructuredSelection(savedSelection));
			} else if (savedSelection.getParent()!=null && savedSelection.getParent().toFile().exists()) {
				// If file deleted, select parent.
				tree.setSelection(new StructuredSelection(savedSelection.getParent()));
			}
		}
		
		Display.getDefault().asyncExec(new Runnable() {
			@Override
			public void run() {
				final TreePath path = new TreePath(new Object[]{NIOUtils.getRoots().get(0)});
				tree.setExpandedState(path, true);
			}
		});

	}
	
	@Override
	public void collapseAll() {
		this.tree.collapseAll();
	}

	@Override
	public void showPreferences() {
		PreferenceDialog pref = PreferencesUtil.createPreferenceDialogOn(
				getViewSite().getShell(), "uk.ac.diamond.sda.navigator.fileNavigatorPreferencePage", null, null);
		if (pref != null) {
			pref.open();
		}
	}

	private void setColumnVisible(final int col, final int width, boolean isVis) {
		if (this.tree==null || this.tree.getControl().isDisposed()) return;
		tree.getTree().getColumn(col).setWidth(isVis?width:0);
		tree.getTree().getColumn(col).setResizable(isVis);
		tree.getTree().getColumn(col).setMoveable(isVis);
	}

	@Override
	public void refresh() {
		final Path     file     = getSelectedPath();
		refresh(file);
	}
		
	protected void refresh(Path file) {

		try {
			updatingTextFromTreeSelections=false;

			final Object[] elements = file==null?this.tree.getExpandedElements():null;
			
			if (file!=null) {
				final IFileContentProvider fileCont = (IFileContentProvider)tree.getContentProvider();
				fileCont.clear(file);
			}
	
			if (file!=null) {
				if (!Files.exists(file)) {
					tree.remove(file);
				} else {
				    tree.refresh(file);
				    if (file.getParent() != null) tree.refresh(file.getParent());
				}
			} else {
				tree.refresh();
			}
			
			if (elements!=null) this.tree.setExpandedElements(elements);
			
		} finally {
			updatingTextFromTreeSelections=true;
		}
	}

	public void refreshAll() {
		NIOUtils.getRoots(true);
		tree.refresh();
	}

	private void createContent(boolean setItemCount) {
		
		final List<Path> roots = NIOUtils.getRoots();
		if (setItemCount) tree.getTree().setItemCount(roots.size());
		if (Boolean.getBoolean("uk.ac.diamond.sda.navigator.threadingFileNavigator")) {
		    tree.setContentProvider(new ThreadingFileContentProvider());
		} else {
			tree.setContentProvider(new NioFileContentProvider());
		}
		if (roots.size()==1) {
			tree.setInput(roots.get(0));
		} else {
		    tree.setInput("Root");
		}
		tree.expandToLevel(1);
	}

	public void setSelectedFile(String path) {
		final Path file = Paths.get(path);
		setSelectedFile(file);
	}
	
	public void setSelectedFile(final Path file) {
		if (Files.exists(file, NOFOLLOW_LINKS)) {	
			
			
			final Job expandJob = new Job("Update tree expanded state") {
				// Job needed because lazy tree - do not copy for all trees!	
				// Required this funny way because tree is lazy
				@Override
				protected IStatus run(IProgressMonitor monitor) {
					for (Path f : file) {
						
								
						if (monitor.isCanceled()) break;
						
						int count = 0;
						while (!getExpandedState(f) && count<1000) {
							expand(f);
							try {
								if (monitor.isCanceled()) break;
								Thread.sleep(20);
								count++;
							} catch (InterruptedException e) {
								break;
							}
						}
					}
					
					Display.getDefault().syncExec(new Runnable() {
						@Override
						public void run() {
					        tree.setSelection(new StructuredSelection(file));
						}
					});
					
					return Status.OK_STATUS;
				}
				
				private void expand(final Path f) {
					Display.getDefault().syncExec(new Runnable() {
						@Override
						public void run() {
							tree.setExpandedState(f, true);
						}
					});
				}
				private boolean getExpandedState(final Path f) {
					final List<Boolean> res = new ArrayList<Boolean>(1);
					Display.getDefault().syncExec(new Runnable() {
						@Override
						public void run() {
							res.add(tree.getExpandedState(f));
						}
					});
					return res.get(0);
				}

			};
			expandJob.setUser(false);
			expandJob.setSystem(true);
			expandJob.setPriority(Job.INTERACTIVE);
			expandJob.schedule();
		}	
	}

	private void createRightClickMenu() {
		final MenuManager menuManager = new MenuManager();
		tree.getControl().setMenu(menuManager.createContextMenu(tree.getControl()));
		getSite().registerContextMenu(menuManager, tree);
	}
	
    /**
     * Never really figured out how to made toggle buttons work properly with
     * contributions. Use hard coded actions
     * 
     * TODO Move this to contributions
     */
	private void createActions() {
		
        // TODO Save preference as property
		final IPreferenceStore store = NavigatorRCPActivator.getDefault().getPreferenceStore();
		final IToolBarManager toolMan = getViewSite().getActionBars().getToolBarManager();
		
		final Action filterCollections = new Action("Compress data collections with the same name", IAction.AS_CHECK_BOX) {
			@Override
			public void run() {
				IFileContentProvider prov = (IFileContentProvider)tree.getContentProvider();
				prov.setCollapseDatacollections(isChecked());
				store.setValue(FileNavigatorPreferenceConstants.SHOW_COLLAPSED_FILES, isChecked());
				prov.clearAll();
				refreshAll();
			}
		};
		filterCollections.setImageDescriptor(NavigatorRCPActivator.getImageDescriptor("icons/zip.png"));
		filterCollections.setChecked(store.getBoolean(FileNavigatorPreferenceConstants.SHOW_COLLAPSED_FILES));
		toolMan.add(filterCollections);
		toolMan.add(new Separator("sorting"));
		
		final CheckableActionGroup grp = new CheckableActionGroup();
		
		final Action dirsTop = new Action("Sort alphanumeric, directories at top.", IAction.AS_CHECK_BOX) {
			@Override
			public void run() {
				final Path selection = getSelectedPath();
				((IFileContentProvider)tree.getContentProvider()).setSort(FileSortType.ALPHA_NUMERIC_DIRS_FIRST);
				tree.refresh();
				if (selection!=null)tree.setSelection(new StructuredSelection(selection));
			}
		};
		dirsTop.setImageDescriptor(NavigatorRCPActivator.getImageDescriptor("icons/alpha_mode_folder.png"));
		dirsTop.setChecked(true);
		grp.add(dirsTop);
		toolMan.add(dirsTop);
		
		
		final Action alpha = new Action("Alphanumeric sort for everything.", IAction.AS_CHECK_BOX) {
			@Override
			public void run() {
				final Path selection = getSelectedPath();
				((IFileContentProvider)tree.getContentProvider()).setSort(FileSortType.ALPHA_NUMERIC);
				tree.refresh();
				if (selection!=null)tree.setSelection(new StructuredSelection(selection));
			}
		};
		alpha.setImageDescriptor(NavigatorRCPActivator.getImageDescriptor("icons/alpha_mode.gif"));
		grp.add(alpha);
		toolMan.add(alpha);

		toolMan.add(new Separator("uk.ac.diamond.sda.navigator.views.monitorSep"));
		
        // NO MONITORING! There are some issues with monitoring, the Images Monitor part should
		// be used for this.

	}


	/**
	 * Opens the file selected in the tree. If a openFile extension is active for this perspective
	 * this is called instead to delegate opening the file.
	 * 
	 */
	@Override
	public void openSelectedFile() {
		final Path file = getSelectedPath();
		if (file==null) return;
		
		final IOpenFileAction action = getFirstPertinentAction();
		if (action!=null) {
			action.openFile(file);
			return;
		}
		
		if (Files.isDirectory(file)) {
			// Disabled, this causes a UI blocking thread to execute.
			
		} else { // Open file
			
			try {
				EclipseUtils.openExternalEditor(file.toAbsolutePath().toString());
			} catch (PartInitException e) {
				logger.error("Cannot open file "+file, e);
			}
		}
	}

	private IOpenFileAction getFirstPertinentAction() {
		
		try {
			IConfigurationElement[] eles = Platform.getExtensionRegistry().getConfigurationElementsFor("org.dawnsci.fileviewer.openFile");
			final String perspectiveId   = EclipseUtils.getPage().getPerspective().getId();
			
			for (IConfigurationElement e : eles) {
				final String perspective = e.getAttribute("perspective");
				if (perspectiveId.equals(perspective) || perspective==null) {
					return (IOpenFileAction)e.createExecutableExtension("class");
				}
			}
			return null;
		} catch (CoreException coreEx) {
			coreEx.printStackTrace();
			return null;
		}
	}

	@Override
	public void dispose() {
		super.dispose();
		
		// TODO Any other disposals?
	}

	@Override
	public void setFocus() {
		tree.getControl().setFocus();
	}

	

    /**
     * The adapter IContentProvider gives the value of the H5Dataset
     */
	@SuppressWarnings("rawtypes")
	@Override
	public Object getAdapter(final Class clazz) {

		return super.getAdapter(clazz);
		
		// TODO returns an adapter part for 'IPage' which is a page summary for the file or folder?
	}


}
