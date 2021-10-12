/*-
 * Copyright 2017 Diamond Light Source Ltd.
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

package uk.ac.diamond.scisoft.analysis.rcp.views;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.dawnsci.plotting.api.ActionType;
import org.eclipse.dawnsci.plotting.api.IPlotActionSystem;
import org.eclipse.dawnsci.plotting.api.IPlottingSystemViewer;
import org.eclipse.dawnsci.plotting.api.ManagerType;
import org.eclipse.dawnsci.plotting.api.trace.ITableDataTrace;
import org.eclipse.dawnsci.plotting.api.trace.ITrace;
import org.eclipse.january.DatasetException;
import org.eclipse.january.dataset.DatasetUtils;
import org.eclipse.january.dataset.IDataset;
import org.eclipse.january.dataset.ILazyDataset;
import org.eclipse.january.metadata.AxesMetadata;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.nebula.widgets.nattable.copy.command.CopyDataToClipboardCommand;
import org.eclipse.nebula.widgets.nattable.export.command.ExportCommand;
import org.eclipse.nebula.widgets.nattable.print.command.TurnViewportOffCommand;
import org.eclipse.nebula.widgets.nattable.print.command.TurnViewportOnCommand;
import org.eclipse.nebula.widgets.nattable.selection.command.ClearAllSelectionsCommand;
import org.eclipse.nebula.widgets.nattable.selection.command.SelectAllCommand;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;

import uk.ac.diamond.scisoft.analysis.rcp.AnalysisRCPActivator;

public class DatasetTablePlotViewer extends IPlottingSystemViewer.Stub<Composite> {

	private static final String DATASETTABLE_PLOTVIEWER_ACTIONS = "datasettable.plotviewer.actions";
	private DatasetTableComposite tableComposite;

	@Override
	public void createControl(final Composite parent) {
		tableComposite = new DatasetTableComposite(parent, SWT.None);
		createToolbar();
	}
	
	private void createToolbar() {
		IPlotActionSystem plotActionSystem = system.getPlotActionSystem();
		Action selAction = new Action() {
			@Override
			public void run() {
				tableComposite.getTable().doCommand(new SelectAllCommand());
				tableComposite.getTable().redraw();
			}
		};
		selAction.setToolTipText("Select all in table");
		selAction.setImageDescriptor(AnalysisRCPActivator.getImageDescriptor("icons/table_add.png"));

		Action deselAction = new Action() {
			@Override
			public void run() {
				tableComposite.getTable().doCommand(new ClearAllSelectionsCommand());
				tableComposite.getTable().redraw();
			}
		};
		deselAction.setToolTipText("Clear selection");
		deselAction.setImageDescriptor(AnalysisRCPActivator.getImageDescriptor("icons/table_delete.png"));

		Action clipboardAction = new Action() {
			@Override
			public void run() {
				tableComposite.getTable().doCommand(new CopyDataToClipboardCommand("\t", "\n", tableComposite.getTable().getConfigRegistry()));
			}
		};
		clipboardAction.setToolTipText("Copy selection to clipboard");
		clipboardAction.setImageDescriptor(AnalysisRCPActivator.getImageDescriptor("icons/table_go.png"));

		Action excelAction = new Action() {
			@Override
			public void run() {
				try {
					tableComposite.getTable().doCommand(new TurnViewportOffCommand());
					tableComposite.getTable().doCommand(new ExportCommand(tableComposite.getTable().getConfigRegistry(), tableComposite.getTable().getShell()));
					tableComposite.getTable().doCommand(new TurnViewportOnCommand());
				} catch (Exception e) {
					Status status = new Status(IStatus.ERROR, AnalysisRCPActivator.PLUGIN_ID, e.getCause().getMessage(), e); 
					ErrorDialog.openError(tableComposite.getTable().getShell(), "Excel export error", "Error exporting Excel table", status);
				}
			}
		};
		excelAction.setToolTipText("Export all as Excel");
		excelAction.setImageDescriptor(AnalysisRCPActivator.getImageDescriptor("icons/page_excel.png"));

		Action saveAction = new Action() {
			@Override
			public void run() {
				tableComposite.getTable().doCommand(new ExportSelectionCommand(tableComposite.getTable().getShell()));
			}
		};
		saveAction.setToolTipText("Export selection");
		saveAction.setImageDescriptor(AnalysisRCPActivator.getImageDescriptor("icons/table_save.png"));

		plotActionSystem.registerGroup(DATASETTABLE_PLOTVIEWER_ACTIONS, ManagerType.TOOLBAR);
		plotActionSystem.registerAction(DATASETTABLE_PLOTVIEWER_ACTIONS, selAction, ActionType.DATA, ManagerType.TOOLBAR);
		plotActionSystem.registerAction(DATASETTABLE_PLOTVIEWER_ACTIONS, deselAction, ActionType.DATA, ManagerType.TOOLBAR);
		plotActionSystem.registerAction(DATASETTABLE_PLOTVIEWER_ACTIONS, clipboardAction, ActionType.DATA, ManagerType.TOOLBAR);
		plotActionSystem.registerAction(DATASETTABLE_PLOTVIEWER_ACTIONS, excelAction, ActionType.DATA, ManagerType.TOOLBAR);
		plotActionSystem.registerAction(DATASETTABLE_PLOTVIEWER_ACTIONS, saveAction, ActionType.DATA, ManagerType.TOOLBAR);
	}

	@Override
	public boolean addTrace(ITrace trace){
		if (trace instanceof ITableDataTrace) {
			ITableDataTrace t = (ITableDataTrace)trace;
		
			IDataset data = t.getData();
			if (data.getRank() != 2)
				return false;
			
			IDataset rows = null;
			IDataset cols = null;
			if (data.getShape()[1] == 1) {
				// 1D mode
				AxesMetadata firstMetadata = data.getFirstMetadata(AxesMetadata.class);
				ILazyDataset[] axes = firstMetadata.getAxis(0);
				if (axes == null)
					axes = new ILazyDataset[0];
				ILazyDataset axis = null;
				for (ILazyDataset tempAxis : axes) {
					if (tempAxis != null && tempAxis.getShape()[0] == data.getShape()[0]) {
						axis = tempAxis;
						break;
					}
				}
				try {
					rows = DatasetUtils.sliceAndConvertLazyDataset(axis);
					if (rows != null)
						rows.squeeze();
				} catch (DatasetException e) {
					// do nothing, rows stays null
				}
			} else {
				// 2D mode
				AxesMetadata firstMetadata = data.getFirstMetadata(AxesMetadata.class);
				ILazyDataset[] axesX = firstMetadata.getAxis(0); // rows
				ILazyDataset[] axesY = firstMetadata.getAxis(1); // cols

				
				if (axesX == null)
					axesX = new ILazyDataset[0];
				ILazyDataset axisX = null;
				for (ILazyDataset tempAxisX : axesX) {
					if (tempAxisX != null && tempAxisX.getShape()[0] == data.getShape()[0]) {
						axisX = tempAxisX;
						break;
					}
				}
				
				if (axesY == null)
					axesY = new ILazyDataset[0];
				ILazyDataset axisY = null;
				for (ILazyDataset tempAxisY : axesY) {
					if (tempAxisY != null && tempAxisY.getShape()[1] == data.getShape()[1]) {
						axisY = tempAxisY;
						break;
					}
				}
				
				try {
					rows = DatasetUtils.sliceAndConvertLazyDataset(axisX);
					if (rows != null)
						rows.squeeze();
				} catch (DatasetException e) {
					// do nothing, rows stays null
				}
				try {
					cols = DatasetUtils.sliceAndConvertLazyDataset(axisY);
					if (cols != null)
						cols.squeeze();
				} catch (DatasetException e) {
					// do nothing, rows stays null
				}
			}
			
			tableComposite.setData(t.getData(), rows, cols);
			return true;
		}
		return false;
	}
	
	@Override
	public void removeTrace(ITrace trace) {
		
	}
	
	@Override
	public Composite getControl() {
		if (tableComposite == null)
			return null;
		return tableComposite;
	}
	
	@Override
	public  <U extends ITrace> U createTrace(String name, Class<U> clazz) {
		if (clazz == ITableDataTrace.class) {
			return null;
		}
		return null;
	}
	
	@Override
	public boolean isTraceTypeSupported(Class<? extends ITrace> trace) {
		return ITableDataTrace.class.isAssignableFrom(trace);
	}
	
	@Override
	public Collection<Class<? extends ITrace>> getSupportTraceTypes() {
		List<Class<? extends ITrace>> l = new ArrayList<>();
		l.add(ITableDataTrace.class);
		return l;
	}
	
}
