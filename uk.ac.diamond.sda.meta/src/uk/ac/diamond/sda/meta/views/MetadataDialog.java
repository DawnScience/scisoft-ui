package uk.ac.diamond.sda.meta.views;

import org.eclipse.january.metadata.IMetadata;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;

public class MetadataDialog extends Dialog {
	
	private IMetadata metadata;

	public MetadataDialog(Shell parentShell, IMetadata metadata) {
		super(parentShell);
		this.metadata = metadata;
	}
	
	@Override
	public Control createDialogArea(Composite parent)  {
		Composite container = (Composite) super.createDialogArea(parent);
		container.setLayout(new FillLayout());
		
		MetadataComposite metacomp = new MetadataComposite(container, SWT.None);
		metacomp.setMeta(metadata);
		
		return container;
	}
	
	@Override
	protected Point getInitialSize() {
		Rectangle bounds = PlatformUI.getWorkbench().getWorkbenchWindows()[0].getShell().getBounds();
		return new Point((int)(bounds.width*0.5),(int)(bounds.height*0.8));
	}

	@Override
	protected boolean isResizable() {
		return true;
	}

	@Override
	protected void configureShell(Shell shell) {
		super.configureShell(shell);
		shell.setText("File Metadata");
	}

}
