package uk.ac.diamond.scisoft.analysis.powder.indexer.rcp.widget;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import uk.ac.diamond.scisoft.analysis.powder.indexer.rcp.Activator;

class IndexerProgressDialog extends ProgressMonitorDialog {

	private Text outputArea;
	private Image powderImage;

	public IndexerProgressDialog(Shell parent) {
		super(parent);
	}

	// @Override
	// protected Image getImage() {
	// Image icon = Activator.getImage("icons/powderIndexing.png");
	// //Maginfiy
	// icon.getImageData().scaledTo(1000, 1000);
	// return icon;
	// }

	@Override
	protected void finishedRun() {
		// TODO Auto-generated method stub
		// Do not close but change the button
		Button cancel = getCancelButton();
		cancel.setText("Finish");
		// super.finishedRun();
	}

	@Override
	protected void okPressed() {
		// TODO Auto-generated method stub
		super.okPressed();
	}

	@Override
	protected void cancelPressed() {
		decrementNestingDepth(); // TODO: this will break things
		super.cancelPressed();
	}

	@Override
	protected Control createContents(Composite parent) {
		this.getShell().setText("Determing Structure");
		powderImage = Activator.getImage("icons/powderIndexing.png");
		this.getShell().setImage(powderImage);

		super.createContents(parent);

		return parent;
	}

	@Override
	protected Control createDialogArea(Composite parent) {

		super.createDialogArea(parent);

//		Composite composite = new Composite(parent, SWT.BORDER);
//		composite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		this.outputArea = new Text(parent, SWT.V_SCROLL);

		 GridData gd = new GridData(100,100);
		 gd.horizontalAlignment = GridData.FILL;
		 gd.grabExcessHorizontalSpace = true;
		 gd.grabExcessVerticalSpace = true;
		 gd.horizontalSpan = 2;
		
		 outputArea.setLayoutData(gd);
		outputArea.setBackground(parent.getDisplay().getSystemColor(SWT.COLOR_GRAY));
		outputArea.setSize(100, 200);

		OutputStream out = new OutputStream() {
			@Override
			public void write(int b) throws IOException {
				if (outputArea.isDisposed())
					return;
				outputArea.append(String.valueOf((char) b));
			}
		};
		final PrintStream oldOut = System.out;
		System.setOut(new PrintStream(out));

		outputArea.addDisposeListener(new DisposeListener() {
			public void widgetDisposed(DisposeEvent e) {
				System.setOut(oldOut);
			}
		});

		return parent;
	}

	@Override
	public boolean close() {
		boolean ok = super.close();
		if (ok) {
			powderImage.dispose();
		}
		return ok;
	}

	private void streamLog(String message) {
		outputArea.setText(message);
		outputArea.update();
	}
}