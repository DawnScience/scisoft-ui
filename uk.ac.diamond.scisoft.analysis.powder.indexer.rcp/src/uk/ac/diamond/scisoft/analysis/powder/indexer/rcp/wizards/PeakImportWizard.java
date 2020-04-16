package uk.ac.diamond.scisoft.analysis.powder.indexer.rcp.wizards;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.dawb.common.ui.wizard.ResourceChoosePage;
import org.eclipse.january.dataset.DatasetFactory;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.ui.IImportWizard;
import org.eclipse.ui.IWorkbench;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.powder.indexer.rcp.PowderIndexerManager;

/**
 * @author Dean P. Ottewell
 *
 */
public class PeakImportWizard extends Wizard implements IImportWizard {
	
	public static final String ID = "uk.ac.diamond.scisoft.analysis.powder.diffraction.indexer.rcp.wizards.PeakImportWizard";

	private static final Logger logger = LoggerFactory.getLogger(PeakImportWizard.class);
	
	PowderIndexerManager controller; 
	
	ImportPeaksPage peaksImport;
	
	public PeakImportWizard(PowderIndexerManager controller) {
		this.controller = controller;
		setWindowTitle("Import Peaks");
		this.peaksImport = new  ImportPeaksPage();
		peaksImport.setTitle("Peaks Imports");
		peaksImport.setDescription("Import peaks for indexing routine");
		addPage(peaksImport);
	}

	@Override
	public void init(IWorkbench workbench, IStructuredSelection selection) {
	}
	
	
	@Override
	public boolean performFinish() {
		
		final ImportPeaksPage ep = (ImportPeaksPage)getPages()[0];
		try {
//			String path = "";
//			
//			path = ep.loadPeaks();
//
//			if (ep.isOpen()) {
//				EclipseUtils.openExternalEditor(path);
//			}
//			
			
			
			String path = peaksImport.getAbsoluteFilePath();

			//TODO: catch failure
			loadPeaks(path);
			
			
		} catch (Exception e) {
			logger.error("Cannot import peaks", e);
		}

		//Configure to allow indexing
		controller.finishedPowderSearching();
		
		return true;
	}
	
//	private final class ImportPeaksPage extends WizardPage {
//
//		private boolean open      = true;
//		private String  path;
//		private SelectorWidget select;
//
//		protected ImportPeaksPage(String pageName) {
//			super(pageName);
//			
//		}
//
//		@Override
//		public void createControl(Composite parent) {
//			Composite container = 	new Composite(parent, SWT.NULL);
//			GridLayout layout = new GridLayout();
//			container.setLayout(layout);
//			layout.numColumns      = 3;
//			layout.verticalSpacing = 9;
//
//			select = new SelectorWidget(container, new String[] {"File"}, new String[] {"*"} ) {
//				@Override
//				public void pathChanged(String path, TypedEvent event) {
//					ImportPeaksPage.this.pathChanged();
//				}
//			};
//			
//			FileSelectionDialog fileSelection = new FileSelectionDialog(this.getShell());
////			
////			final Button open = new Button(container, SWT.CHECK);
////			open.setText("Open file after import.");
////			open.setSelection(true);
////			open.addSelectionListener(new SelectionAdapter() {
////				@Override
////				public void widgetSelected(SelectionEvent e) {
////					ExportPage.this.open = open.getSelection();
////					pathChanged();
////				}
////			});
////			open.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 3, 1));
//
//			pathChanged();		
//			setControl(container);
//		}
//		
//		/**
//		 * Ensures that both text fields are set.
//		 */
//		private void pathChanged() {
//			final String p = select.getText();
//			this.path = p;
//			if(!path.equals(""))
//				loadPeaks();
//		}
//
//		public boolean isOpen() {
//			return open;
//		}
//
//		@SuppressWarnings("unused")
//		public void setOpen(boolean open) {
//			this.open = open;
//		}
//		
//	
//	}
	
	private String loadPeaks(String path){
		File file = new File(path);			
		BufferedReader reader = null;
		
		List<Double> xData = new ArrayList<Double>();
		List<Double> yData = new ArrayList<Double>();
		
		try {
			reader = new BufferedReader(new FileReader(file));
			String text = null;
			
			if(path.toLowerCase().endsWith(".xy")){
				while((text = reader.readLine()) != null){
					String[] xy = text.split(" ");
					
					xData.add(Double.parseDouble(xy[0]));
					yData.add(Double.parseDouble(xy[1]));
				}
			} else {
				//Assume data files
				while((text = reader.readLine()) != null){
					yData.add(Double.parseDouble(text));
					xData.add((double) yData.size());
				}
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		controller.setPeakPosData(DatasetFactory.createFromList(xData),DatasetFactory.createFromList(yData));
		//controller.setyData(DatasetFactory.createFromList(yData)); //TODO: should really do elsewhere
		
		return path;
	}
	
	public class ImportPeaksPage extends ResourceChoosePage {

		public ImportPeaksPage () {
			super("wizardPage", "Page importing peak data", null);
			setTitle("import peak data from file");
			setDirectory(true);
			
			this.setDirectory(false);
			this.setNewFile(false);
			this.setPathEditable(true);
		}
	}
	
}
