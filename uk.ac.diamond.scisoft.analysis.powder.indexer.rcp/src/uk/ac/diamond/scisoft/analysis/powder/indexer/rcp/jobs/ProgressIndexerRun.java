package uk.ac.diamond.scisoft.analysis.powder.indexer.rcp.jobs;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.january.dataset.IDataset;
import org.eclipse.january.dataset.ILazyDataset;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.swt.widgets.Display;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.powder.indexer.IPowderIndexerParam;
import uk.ac.diamond.scisoft.analysis.powder.indexer.indexers.CellParameter;
import uk.ac.diamond.scisoft.analysis.powder.indexer.indexers.IPowderIndexer;
import uk.ac.diamond.scisoft.analysis.powder.indexer.indexers.PowderIndexerFactory;
import uk.ac.diamond.scisoft.analysis.powder.indexer.indexers.StandardConstantParameters;
import uk.ac.diamond.scisoft.analysis.powder.indexer.rcp.Activator;
import uk.ac.diamond.scisoft.analysis.powder.indexer.rcp.PowderIndexerManager;


/**
 * Process that delegates the run indexing procedure and gathers status from the
 * indexing.
 * 
 * The model is then updated on completion of the process.
 * 
 * TODO: should have a abstract class?
 * 
 * @author Dean P. Ottewell
 */
public class ProgressIndexerRun implements IRunnableWithProgress {

	protected final Logger logger = LoggerFactory.getLogger(ProgressIndexerRun.class);

	PowderIndexerManager manager;
	List<CellParameter> currentData;

	IPowderIndexer indexer;

	ILazyDataset peakData;

	public ProgressIndexerRun(String ID_indexer, String title, PowderIndexerManager manager, IDataset peakPositions) {
		this.manager = manager;
		//TODO: Shouldn't really do this as I need the parameters
		this.indexer = PowderIndexerFactory.createIndexer(ID_indexer);
		//TODO: then load standard parameters or check here?...
		//this.indexer.setOutFileTitle(title);
		this.peakData = peakPositions;
		
	}

	
	//TODO: Adjust to better method without manager
	private void updateOnFinish(List<CellParameter> cellData) {
		if (cellData != null) {
			Display.getDefault().syncExec(new Runnable() {
				@Override
				public void run() {
					
					
					manager.setCellData(cellData);
					
					//TODO: only use the crysta version
				}
			});
		}
	}

	//TODO: more a tmp load to just have things setup
	private void loadPowderIndexerPreferences(){
		
		
		
		//String indexerName = Activator.getDefault().getPreferenceStore().getString(PowderIndexerConstants.indexer);
		
		// Configure peak finder on preference store go through all the params that match
		Map<String, IPowderIndexerParam> params = indexer.getParameters();
		
		for (Entry<String, IPowderIndexerParam> powderParam : params.entrySet()){
			try {
				//TODO: if pref never initialised need to activate that...
				//String curVal = Activator.getDefault().getPreferenceStore().getString(powderParam.getKey());
				
				String curVal = Activator.getDefault().getPreferenceStore().getString(powderParam.getValue().getName());
				
				// Configure peak finder on preference store go through all the params that match
				Number val = Double.parseDouble(curVal);
				
				IPowderIndexerParam paramVal = powderParam.getValue();
				
				paramVal.setValue(val);
				
				//Set the value in the current indexer run
				indexer.setParameter(paramVal, powderParam.getKey());
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		
		}
		
		
		//TODO: Load in widget parameters? well they might already be set but overiding here
		String indexerDirectory = Activator.getLocalPreferenceStore().getString(indexer.getPowderRoutineID());
		indexer.setIndexerDirectory(indexerDirectory +"/"); //TODO: tmp add directory here

		indexer = loadStandardParameter(indexer, StandardConstantParameters.wavelength);
		indexer = loadStandardParameter(indexer, StandardConstantParameters.maxVolume);
		indexer = loadStandardParameter(indexer, StandardConstantParameters.minFigureMerit);
		
		//Crystal syste set TODO: when has this been loaded...

		Boolean widgetVal = Activator.getDefault().getPreferenceStore().getBoolean( StandardConstantParameters.cubicSearch);	
		indexer = loadStandardParameterBool(indexer, StandardConstantParameters.cubicSearch);
		
		indexer = loadStandardParameterBool(indexer, StandardConstantParameters.monoclinicSearch);
		indexer = loadStandardParameterBool(indexer, StandardConstantParameters.orthorhombicSearch);
		indexer = loadStandardParameterBool(indexer, StandardConstantParameters.hexagonalSearch);
		indexer = loadStandardParameterBool(indexer, StandardConstantParameters.tetragonalSearch);
		indexer = loadStandardParameterBool(indexer, StandardConstantParameters.trigonalSearch);
		indexer = loadStandardParameterBool(indexer, StandardConstantParameters.hexagonalSearch);
		indexer = loadStandardParameterBool(indexer, StandardConstantParameters.triclinicSearch);
	}
	
	/**
	 * TODO: assumes value is already set in the indexer
	 * 
	 * @param powderIndexer
	 * @param parameterName
	 * @return
	 */
	private IPowderIndexer loadStandardParameter(IPowderIndexer powderIndexer, String parameterName){
		
		
		try {
			IPowderIndexerParam param = powderIndexer.getParameter(parameterName);
			Double widgetVal = Activator.getDefault().getPreferenceStore().getDouble(parameterName);	
			param.setValue(widgetVal);
			powderIndexer.setParameter(param, parameterName);
			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			logger.warn("The parameter was not present to set inside powder indexer: " + parameterName );
			e.printStackTrace();
		}
		
		
		return powderIndexer;
	}
	
	private IPowderIndexer loadStandardParameterBool(IPowderIndexer powderIndexer, String parameterName){
		try {
			IPowderIndexerParam param = powderIndexer.getParameter(parameterName);
			Boolean widgetVal =  Activator.getDefault().getPreferenceStore().getBoolean(parameterName);	
			Integer isActive = widgetVal ? 1  : 0 ; 
			param.setValue(isActive);
			powderIndexer.setParameter(param, parameterName);
			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			logger.warn("The parameter was not present to set inside powder indexer: " + parameterName );
			e.printStackTrace();
		}
		
		return powderIndexer;
	}
	
	@Override
	public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {

		monitor.beginTask("Running indexing procedure...", IProgressMonitor.UNKNOWN);
		// TODO: need to get some status back to update the runnable progress
		// SO much variance could exist for every run though...
		// TODO: cancel button is overruled... how is inside the process
		// preventing this? what could possible to do get that desired feedback
		// currentData = indexer.runIndexing((IDataset) peakData);// too quick
		
		
		monitor.beginTask("Loading preference paramters...", IProgressMonitor.UNKNOWN);
		loadPowderIndexerPreferences();
		
		//TODO: attempt load of the standard parameters. Generic enough to be defined in the indexer plugin.
		
		// Set the indata
		indexer.setPeakData((IDataset) peakData);
		
		monitor.beginTask("Configuring indexing procedure...", IProgressMonitor.UNKNOWN);
		indexer.configureIndexer();
		monitor.beginTask("Running indexing procedure...", IProgressMonitor.UNKNOWN);
		
		//TODO: Spawn in job and catch a cancel
		
		indexer.runIndexer();
		
		
		
		
		
		// Check if indexer process is stil active
		monitor.beginTask("Gathering process data...", IProgressMonitor.UNKNOWN);
		
		String status = "";
		while ((status != null) && (!monitor.isCanceled())) {
			// TODO: call upon the indexer process call separately
			status = indexer.getStatus();
			monitor.subTask(status);
			
			logger.info(status);
			
		}

		currentData = indexer.getResultCells();

		Boolean dataSt = currentData.isEmpty();
		if (!currentData.isEmpty()){
		 	updateOnFinish(currentData);
		}
		
		monitor.beginTask("Terminating the indexing procedure...", IProgressMonitor.UNKNOWN);
		indexer.stopIndexer();
		
		if(currentData.isEmpty()){
			monitor.setTaskName("No solutions were found");
		} else {
			monitor.setTaskName("Solutions were found");
		}
		
		monitor.done();
	}

}
