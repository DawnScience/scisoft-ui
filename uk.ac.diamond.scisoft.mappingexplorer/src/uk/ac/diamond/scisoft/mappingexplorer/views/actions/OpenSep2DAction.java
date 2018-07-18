/*-
 * Copyright © 2011 Diamond Light Source Ltd.
 *
 * This file is part of GDA.
 *
 * GDA is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License version 3 as published by the Free
 * Software Foundation.
 *
 * GDA is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along
 * with GDA. If not, see <http://www.gnu.org/licenses/>.
 */
package uk.ac.diamond.scisoft.mappingexplorer.views.actions;

import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.NotEnabledException;
import org.eclipse.core.commands.NotHandledException;
import org.eclipse.core.commands.common.NotDefinedException;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.swt.widgets.Event;
import org.eclipse.ui.IActionDelegate2;
import org.eclipse.ui.IViewActionDelegate;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.handlers.IHandlerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author rsr31645
 * 
 */
public class OpenSep2DAction implements IViewActionDelegate, IActionDelegate2 {

	private static final Logger logger = LoggerFactory
			.getLogger(OpenSep2DAction.class);
	private IViewPart view;

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ui.IActionDelegate#run(org.eclipse.jface.action.IAction)
	 */
	@Override
	public void run(IAction action) {
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.eclipse.ui.IActionDelegate#selectionChanged(org.eclipse.jface.action
	 * .IAction, org.eclipse.jface.viewers.ISelection)
	 */
	@Override
	public void selectionChanged(IAction action, ISelection selection) {
		if (view.getViewSite().getSecondaryId() != null) {
			action.setEnabled(false);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ui.IViewActionDelegate#init(org.eclipse.ui.IViewPart)
	 */
	@Override
	public void init(IViewPart view) {
		this.view = view;
	}

	@Override
	public void init(IAction action) {
	}

	@Override
	public void dispose() {
		// TODO Auto-generated method stub

	}

	@Override
	public void runWithEvent(IAction action, Event event) {
		logger.info("runWithEvent(IAction action, Event event)");
		IHandlerService hs = view.getSite().getService(
				IHandlerService.class);
		if (hs != null) {
			try {
				hs.executeCommand(
						"uk.ac.diamond.scisoft.mappingexplorer.popout2D", event);
			} catch (ExecutionException e) {
				logger.error("Exceution exception", e);
			} catch (NotDefinedException e) {
				logger.error("NotDefinedException", e);
			} catch (NotEnabledException e) {
				logger.error("NotEnabledException", e);
			} catch (NotHandledException e) {
				logger.error("NotHandledException", e);
			}
		}
	}

}