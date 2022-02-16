/*
 * Copyright (c) 2012 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.feedback.utils;

import org.eclipse.swt.graphics.Image;

import uk.ac.diamond.scisoft.feedback.Activator;

public class FeedbackConstants {

	public static final int MAX_SIZE = 10000 * 1024; // bytes
	public static final int MAX_TOTAL_SIZE = 10000 * 2048;
	public static final String DAWN_FEEDBACK = "[DAWN-FEEDBACK]";
	public static final String FROM_PREF = FeedbackConstants.class.getName() + ".emailAddress";
	public static final String SUBJ_PREF = FeedbackConstants.class.getName() + ".subject";

	public static final String MAIL_TO = "scisoftjira@diamond.ac.uk";
	public static final String DAWN_MAILING_LIST = "DAWN@JISCMAIL.AC.UK";

	public static final String RECIPIENT_PROPERTY = "uk.ac.diamond.scisoft.feedback.recipient";

}
