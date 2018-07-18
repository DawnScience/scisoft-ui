/*
 * Copyright (c) 2012 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.sda.navigator.actions;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.State;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.commands.ICommandService;
import org.eclipse.ui.internal.IPreferenceConstants;
import org.eclipse.ui.preferences.ScopedPreferenceStore;

/**
 * Re-use Editor Command handler in the Project Explorer tool bar Enables the user to set on/off the re-use editor
 * functionality
 */
@SuppressWarnings("restriction")
public class ReUseEditorHandler extends AbstractHandler {

	public static String ID = "uk.ac.diamond.sda.navigator.MultipleEditor";
	private State state;

	@Override
	public final Object execute(ExecutionEvent event) throws ExecutionException {

		// update toggled state
		state = event.getCommand().getState("org.eclipse.ui.commands.toggleState");
		boolean currentState = (Boolean) state.getValue();
		boolean newState = !currentState;
		state.setValue(newState);

		ScopedPreferenceStore store = new ScopedPreferenceStore(InstanceScope.INSTANCE, "org.eclipse.ui.workbench");
		store.setValue(IPreferenceConstants.REUSE_EDITORS_BOOLEAN, newState);
		if (newState)
			store.setValue(IPreferenceConstants.REUSE_EDITORS, 1);
		else
			store.setValue(IPreferenceConstants.REUSE_EDITORS, 10);

		ICommandService commandService = PlatformUI.getWorkbench().getService(ICommandService.class);
		commandService.refreshElements(event.getCommand().getId(), null);

		return null;
	}
}
