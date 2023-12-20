/*
 * Copyright (c) 2012 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.feedback.jobs;

import java.io.File;
import java.net.InetAddress;
import java.util.List;

import org.dawb.common.util.eclipse.BundleUtils;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.feedback.Activator;
import uk.ac.diamond.scisoft.feedback.FeedbackMailer;
import uk.ac.diamond.scisoft.feedback.utils.FeedbackConstants;
import uk.ac.diamond.scisoft.system.info.SystemInformation;

/**
 * 
 */
public class FeedbackJob extends Job {

	private static final Logger logger = LoggerFactory.getLogger(FeedbackJob.class);

	private String subjectvalue;
	private String messagevalue;
	private String destinationEmail;
	private List<File> attachedFiles;

	public FeedbackJob(String jobName, String subjectvalue, 
			String messagevalue, 
			String destinationEmail, List<File> attachedFiles) {
		super(jobName);
		this.subjectvalue = subjectvalue;
		this.messagevalue = messagevalue;
		this.destinationEmail = destinationEmail;
		this.attachedFiles = attachedFiles;
	}

	@Override
	protected IStatus run(IProgressMonitor monitor) {
		try {
			if (monitor.isCanceled()) return Status.CANCEL_STATUS;

			String subject = FeedbackConstants.DAWN_FEEDBACK + " - " + subjectvalue;
			if (subjectvalue != null && !"".equals(subjectvalue)) {
				if (Activator.getDefault() != null) {
					Activator.getDefault().getPreferenceStore().setValue(FeedbackConstants.SUBJ_PREF, subjectvalue);
				}
			}
			StringBuilder messageBody = new StringBuilder();
			String computerName = "Unknown";
			try {
				computerName = InetAddress.getLocalHost().getHostName();
			} finally {

			}
			messageBody.append("Machine: " + computerName + "\n");

			String versionNumber = "Unknown";
			try {
				versionNumber = BundleUtils.getDawnVersion();
			} catch (Exception e) {
				logger.debug("Could not retrieve product and system information", e);
			}

			if (monitor.isCanceled()) return Status.CANCEL_STATUS;

			messageBody.append("Version: " + versionNumber + "\n\n");
			messageBody.append(messagevalue);
			messageBody.append("\n\n\n");
			messageBody.append(SystemInformation.getSystemString());

			if (monitor.isCanceled()) return Status.CANCEL_STATUS;

			// fill the list of files to attach
			int totalSize = 0;
			for (int i = 0; i < attachedFiles.size(); i++) {
				// check that the size does not exceed the maximum one
				if (attachedFiles.get(i).length() > FeedbackConstants.MAX_SIZE) {
					logger.error("The attachment file size exceeds: {}", FeedbackConstants.MAX_SIZE);
					return new Status(IStatus.WARNING, "File Size Problem",
							"The attachment file size exceeds 10MB. Please choose a smaller file to attach.");
				}
				totalSize += attachedFiles.get(i).length();
			}

			if (totalSize > FeedbackConstants.MAX_TOTAL_SIZE) {
				logger.error("The total size of your attachement files exceeds: {}", FeedbackConstants.MAX_TOTAL_SIZE);
				return new Status(IStatus.WARNING, "File Size Problem",
						"The total size of your attachement files exceeds 20MB. Please choose smaller files to attach.");
			}

			if (monitor.isCanceled()) return Status.CANCEL_STATUS;

			// Check that the message is correctly formatted (not empty) and Test the email format
			if (!messagevalue.equals("")) {
				return FeedbackMailer.send(destinationEmail, subject, messageBody.toString(), attachedFiles, monitor);
			}
			return new Status(IStatus.WARNING, "Format Problem",
						"Please type your feedback in the message area before sending the feedback.");
		} catch (Exception e) {
			logger.error("Feedback email not sent", e);
			return new Status(
					IStatus.WARNING,
					"Feedback not sent!",
					"Please set up your computer's default mailer so it can handle mailto URLs");
		}
	}
}
