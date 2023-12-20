/*
 * Copyright (c) 2023 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.feedback;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class that send an email
 */
public class FeedbackMailer {

	private static Logger logger = LoggerFactory.getLogger(FeedbackMailer.class);

	/**
	 * Send email with given arguments
	 * 
	 * @param to destination email address
	 * @param subject
	 * @param messageBody
	 * @param attachmentFiles
	 * @param monitor
	 */
	public static IStatus send(String to, String subject, String messageBody,
			List<File> attachmentFiles, IProgressMonitor monitor) throws Exception {
		Status status = null;

		if (monitor.isCanceled()) return Status.CANCEL_STATUS;

		String mailtoURL = createMailtoURL(to, subject, messageBody, attachmentFiles);
		String osName = System.getProperty("os.name");
		String[] arguments;
		if (osName.startsWith("Windows")) {
			arguments = new String[] { "cmd", "/c", "start \"\" \"" + mailtoURL + "\""};
		} else if (osName.startsWith("Mac")) {
			arguments = new String[] {"open", mailtoURL};
		} else {
			arguments = new String[] {"xdg-open", mailtoURL};
		}

		if (monitor.isCanceled()) return Status.CANCEL_STATUS;

		if (logger.isDebugEnabled()) {
			String command = String.join(", ", arguments);
			logger.debug("Feedback OS command {}", command);
		}

		Process process = new ProcessBuilder(arguments).redirectErrorStream(true).start();
		if (process.waitFor() == 0) {
			status = new Status(IStatus.OK, "Feedback successfully sent", "Thank you for your contribution");
		} else {
			try (BufferedReader rdr = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
				String reasonPhrase = rdr.lines().collect(Collectors.joining("\n"));
				logger.debug("Feedback email not sent - shell response: {}", reasonPhrase);
				status = new Status(IStatus.WARNING, "Feedback not sent",
						"The response from the shell is the following:\n" + reasonPhrase);
				
			}
		}
		return status;
	}

	/**
	 * Percent encode
	 * @param atom
	 * @param replacePlus if true, replace '+' with '%20'
	 * @return encoded atom
	 */
	private static String encode(String atom, boolean replacePlus) {
		String out = URLEncoder.encode(atom, StandardCharsets.UTF_8);
		if (replacePlus) {
			return out.replace("+", "%20");
		}
		return out;
	}

	private static String createMailtoURL(String to, String subject, String messageBody, List<File> attachmentFiles) {
		StringBuilder mailtoURI = new StringBuilder("mailto:");
		mailtoURI.append(encode(to, false));
		mailtoURI.append("?subject=");
		mailtoURI.append(encode(subject, true));
		mailtoURI.append("&body=");
		mailtoURI.append(encode(messageBody, true));
		for (File attachment : attachmentFiles) {
			mailtoURI.append("&attach=");
			mailtoURI.append(encode(attachment.getAbsolutePath(), true));
		}

		return mailtoURI.toString();
	}

	public static void main(String args[]) {
		for (String a : args) {
			String ea = encode(a, true);
			String da = URLDecoder.decode(ea, StandardCharsets.UTF_8);
			System.out.printf("'%s' => '%s'", a, ea);
			if (da.equals(a)) {
				System.out.println(" OK");
			} else {
				System.out.printf(" != '%s'\n", da);
			}
		}
	}

}
