package uk.ac.diamond.sda.meta.views;

import java.io.Serializable;
import java.util.Collection;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.january.metadata.IMetadata;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.progress.UIJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MetadataComposite extends Composite {

	private static final Logger logger = LoggerFactory.getLogger(MetadataComposite.class);

	private static String filterText = "";
	
	private IMetadata meta;
	private TableViewer table;
	
	UIJob updateTable = new UIJob("Updating Metadata Table") {

		@Override
		public IStatus runInUIThread(IProgressMonitor monitor) {

			if (table.getControl().isDisposed()) {
				logger.warn("The header table is disposed, cannot update table");
				return Status.CANCEL_STATUS;
			}
			table.setContentProvider(new IStructuredContentProvider() {
				@Override
				public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
				}

				@Override
				public void dispose() {
				}

				@Override
				public Object[] getElements(Object inputElement) {
					try {
						Collection<String> names = meta == null ? null : meta.getMetaNames();
						return names == null ? new Object[] { "" } : names.toArray(new Object[names.size()]);
					} catch (Exception e) {
						return new Object[] { "" };
					}
				}
			});

			if (table.getControl().isDisposed())
				return Status.CANCEL_STATUS;
			table.setInput(new String());
			table.refresh();
			return Status.OK_STATUS;
		}
	};
	
	
	public MetadataComposite(Composite parent, int style) {
		super(parent, style);
		setLayout(new GridLayout(1,true));
		final Composite container = new Composite(this, SWT.NONE);
		container.setLayout(new GridLayout(1, false));
		container.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		// GridUtils.removeMargins(container);

		final Text searchText = new Text(container, SWT.BORDER | SWT.SEARCH | SWT.ICON_SEARCH | SWT.ICON_CANCEL);
		searchText.setLayoutData(new GridData(GridData.GRAB_HORIZONTAL | GridData.HORIZONTAL_ALIGN_FILL));
		searchText.setToolTipText("Search on data set name or expression value.");
		searchText.setText(filterText);
		
		this.table = new TableViewer(container, SWT.FULL_SELECTION | SWT.SINGLE | SWT.H_SCROLL | SWT.V_SCROLL
				| SWT.BORDER);

		table.getTable().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		table.getTable().setLinesVisible(true);
		table.getTable().setHeaderVisible(true);
		final MetadataTableComparator comparator = new MetadataTableComparator();
		table.setComparator(comparator);

		final TableViewerColumn key = new TableViewerColumn(table, SWT.NONE, 0);
		key.getColumn().setText("Key");
		key.getColumn().setWidth(200);
		key.setLabelProvider(new HeaderColumnLabelProvider(0));
		key.getColumn().addSelectionListener(getSelectionAdapter(comparator, key.getColumn(), 0));

		final TableViewerColumn value = new TableViewerColumn(table, SWT.NONE, 1);
		value.getColumn().setText("Value");
		value.getColumn().setWidth(500);
		value.setLabelProvider(new HeaderColumnLabelProvider(1));
		value.getColumn().addSelectionListener(getSelectionAdapter(comparator, value.getColumn(), 1));

		table.setColumnProperties(new String[] { "Key", "Value" });
		table.setUseHashlookup(true);

		final HeaderFilter filter = new HeaderFilter();
		final NXEntryFilter nxFilter = new NXEntryFilter();
		table.addFilter(filter);
		table.addFilter(nxFilter);
		searchText.addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent e) {
				if (parent.isDisposed())
					return;
				filterText = searchText.getText();
				filter.setSearchText(searchText.getText());
				table.refresh();
			}
		});
		
		// refresh the table to start with, as there may already be input.
		filter.setSearchText(searchText.getText());
		table.refresh();
		
	}
	
	private class HeaderColumnLabelProvider extends ColumnLabelProvider {
		private int column;

		public HeaderColumnLabelProvider(int col) {
			this.column = col;
		}

		@Override
		public String getText(final Object element) {
			if (column == 0)
				return element.toString();
			if (column == 1)
				try {
					Serializable value = meta == null ? null : meta.getMetaValue(element.toString());
					return value == null ? "" : value.toString();
				} catch (Exception ignored) {
					// Null allowed
				}
			return "";
		}
	}

	class HeaderFilter extends ViewerFilter {

		private String searchString;

		public void setSearchText(String s) {
			if (s == null)
				s = "";
			this.searchString = ".*" + s.toLowerCase() + ".*";
		}

		@Override
		public boolean select(Viewer viewer, Object parentElement, Object element) {
			if (searchString == null || searchString.length() == 0) {
				return true;
			}

			final String name = (String) element;

			if (name == null || name.isEmpty())
				return true;

			if (name.toLowerCase().matches(searchString)) {
				return true;
			}

			return false;
		}
	}

	public void setMeta(IMetadata meta) {
		this.meta = meta;
		updateTable.schedule();
	}

	/**
	 * Filter NX class entries
	 * @author wqk87977
	 *
	 */
	class NXEntryFilter extends ViewerFilter {

		private String nxEntryString = ".*@nx.*";

		@Override
		public boolean select(Viewer viewer, Object parentElement, Object element) {
			final String name = (String) element;
			if (name == null || name.isEmpty())
				return true;
			if (name.toLowerCase().matches(nxEntryString)) {
				return false;
			}
			return true;
		}
	}

	private SelectionAdapter getSelectionAdapter(final MetadataTableComparator comparator, final TableColumn column,
			final int index) {
		SelectionAdapter selectionAdapter = new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				comparator.setColumn(index);
				int dir = comparator.getDirection();
				table.getTable().setSortDirection(dir);
				table.getTable().setSortColumn(column);
				table.refresh();
			}
		};
		return selectionAdapter;
	}

	/**
	 * Sorter for the table viewer
	 * @author wqk87977
	 *
	 */
	class MetadataTableComparator extends ViewerComparator {
		private int column;
		private static final int ASCENDING = 0;
		private static final int DESCENDING = 1;
		private int direction = DESCENDING;

		public MetadataTableComparator() {
			direction = ASCENDING;
		}

		public int getDirection() {
			return direction == 1 ? SWT.DOWN : SWT.UP;
		}

		public void setColumn(int column) {
			if (column == this.column) {
				direction = 1 - direction;
			} else {
				this.column = column;
				direction = ASCENDING;
			}
		}

		@Override
		public int compare(Viewer viewer, Object e1, Object e2) {
			int rc = 0;

			switch (column) {
			case 0:
				rc = e1.toString().compareTo(e2.toString());
				break;
			case 1:
				try {
					Serializable value1 = meta == null ? null : meta.getMetaValue(e1.toString());
					if (value1 == null)
						value1 = "";
					Serializable value2 = meta == null ? null : meta.getMetaValue(e2.toString());
					if (value2 == null)
						value2 = "";
					rc = value1.toString().compareTo(value2.toString());
				} catch (Exception ignored) {
					// Null allowed
				}
				break;
			}
			if (direction == DESCENDING)
				rc = -rc;
			return rc;
		}
	}
	
	


}
