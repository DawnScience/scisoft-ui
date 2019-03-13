package uk.ac.diamond.sda.meta.views;

import java.util.ArrayList;
import java.util.List;

import org.dawnsci.datavis.api.IDataFilePackage;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.dawnsci.analysis.api.io.ILoaderService;
import org.eclipse.january.metadata.IMetadata;
import org.eclipse.january.metadata.Metadata;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.ui.ISelectionService;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.HandlerUtil;

import uk.ac.diamond.sda.meta.Activator;

public class MetadataDialogHandler extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		List<IDataFilePackage> list = getSelectedFiles();
		if (!list.isEmpty()) {
			String path = list.get(0).getFilePath();
			
			ILoaderService service = Activator.getService(ILoaderService.class);
			
			IMetadata metadata = new Metadata();
			
			try {
				metadata = service.getMetadata(path, null);
			} catch (Exception e) {
				MessageDialog.openError(HandlerUtil.getActiveShell(event), "Error", "Could not read metadata!");
				return null;
			}
			
			MetadataDialog d = new MetadataDialog(HandlerUtil.getActiveShell(event),metadata);
			d.open();

		}
		
		return null;
	}

	@Override
	public void setEnabled(Object evaluationContext) {
		List<IDataFilePackage> list = getSelectedFiles();
		setBaseEnabled(!list.isEmpty());
	}

	private List<IDataFilePackage> getSelectedFiles() {
		ISelectionService  selectionService= PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
	    ISelection selection = selectionService.getSelection("org.dawnsci.datavis.view.parts.LoadedFilePart");    

		List<IDataFilePackage> list = new ArrayList<>();
		if (selection instanceof StructuredSelection) {
			Object[] array = ((StructuredSelection) selection).toArray();
			for (Object o : array) {
				if (o instanceof IDataFilePackage) {
					list.add((IDataFilePackage) o);
				}
			}
		}

		return list;
	}

}
