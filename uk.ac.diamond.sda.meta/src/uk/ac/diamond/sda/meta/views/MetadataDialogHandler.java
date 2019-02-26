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
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.ui.ISources;
import org.eclipse.ui.handlers.HandlerUtil;

import uk.ac.diamond.sda.meta.Activator;

public class MetadataDialogHandler extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		List<IDataFilePackage> list = getSelectedFiles(event);
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
		List<IDataFilePackage> list = getSelectedFiles(evaluationContext);
		setBaseEnabled(!list.isEmpty());
	}

	private List<IDataFilePackage> getSelectedFiles(Object evaluationContext) {
		Object variable = evaluationContext instanceof ExecutionEvent ? HandlerUtil.getVariable((ExecutionEvent) evaluationContext, ISources.ACTIVE_CURRENT_SELECTION_NAME):
				HandlerUtil.getVariable(evaluationContext, ISources.ACTIVE_CURRENT_SELECTION_NAME);

		List<IDataFilePackage> list = new ArrayList<>();
		if (variable instanceof StructuredSelection) {
			Object[] array = ((StructuredSelection) variable).toArray();
			for (Object o : array) {
				if (o instanceof IDataFilePackage) {
					list.add((IDataFilePackage) o);
				}
			}
		}

		return list;
	}

}
