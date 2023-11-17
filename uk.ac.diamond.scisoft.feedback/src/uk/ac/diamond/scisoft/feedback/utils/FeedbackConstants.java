/*
 * Copyright (c) 2012 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.feedback.utils;

import java.nio.charset.StandardCharsets;

public class FeedbackConstants {
	public static final long KIBI_MULTIPLIER = 1024;

	public static final int MAX_SIZE = 10000 * 1024; // bytes
	public static final int MAX_TOTAL_SIZE = 10000 * 2048;
	public static final String DAWN_FEEDBACK = "[DAWN-FEEDBACK]";
	public static final String SUBJ_PREF = FeedbackConstants.class.getName() + ".subject";

	public static final String RECIPIENT_PROPERTY = "uk.ac.diamond.scisoft.feedback.recipient";
	public static final String DAWN_LOG_PROPERTY = "uk.ac.diamond.dawn.log";

	public static final String[] MAIL_TO_PAIR = { "1c710e993c840eab1b09bcc51aba88a287ecddd534bc7f81a56c7948", "78186ff453ea6acf7a7ed2a17fccc8c6ee8db0ba5ad851e0c6420c23" };

	public static String getMailTo() {
		return decode(MAIL_TO_PAIR[0], MAIL_TO_PAIR[1]);
	}

	private static String decode(String a, String b) {
		int l = a.length();
		assert l == b.length();
		assert l % 2 == 0;
		byte[] out = new byte[l/2];
		for (int i = 0; i < l; i += 2) {
			short na = Short.parseShort(a.substring(i, i+2), 16);
			short nb = Short.parseShort(b.substring(i, i+2), 16);
			out[i/2] = (byte) (na ^ nb);
		}
		return new String(out, StandardCharsets.UTF_8);
	}

	public static void main(String args[]) {
		if ("hello world".equals(decode("47d2bab56a2868e5ce65fa", "2fb7d6d905081f8abc099e"))) {
			System.out.println("Decoder: OK");
		} else {
			System.err.println("Decoder: Failed");
		}
		if (args.length >= 2) {
			System.out.println(decode(args[0], args[1]));
		}
	}
}
