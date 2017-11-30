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

import org.eclipse.january.dataset.DatasetFactory;
import org.eclipse.january.dataset.IDataset;
import org.eclipse.nebula.widgets.nattable.NatTable;
import org.eclipse.nebula.widgets.nattable.config.DefaultNatTableStyleConfiguration;
import org.eclipse.nebula.widgets.nattable.selection.SelectionLayer;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;

public class DatasetTableComposite extends Composite {

	private final NatTable table;
	private final DatasetGridLayerStack dStack;
	
	public DatasetTableComposite(Composite parent, int style) {
		super(parent, style);
		this.setLayout(new FillLayout());
		dStack = new DatasetGridLayerStack(DatasetFactory.zeros(new int[]{1,1}), null, null);
		SelectionLayer sLayer = dStack.getSelectionLayer();
		sLayer.registerCommandHandler(new ExportSelectionCommandHandler(dStack, sLayer));
		table = new NatTable(this, dStack, false);
		table.addConfiguration(new DefaultNatTableStyleConfiguration());
		table.configure();
		this.layout();
	}
	
	public void setData(IDataset dataset, IDataset rows, IDataset cols){
		dStack.setData(dataset, rows, cols);
	}
	
	public NatTable getTable() {
		return table;
	}
}
