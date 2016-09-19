package oculusPrime;

import java.util.*;
import javax.mail.*;
import javax.mail.internet.*;

import oculusPrime.Application;
import oculusPrime.Settings;

public class SendMail {

	private static Settings settings = Settings.getReference();
	private final static String username = settings.readSetting(GUISettings.email_username.toString());
	private final static String password = settings.readSetting(GUISettings.email_password.toString());
	private final static String host = settings.readSetting(GUISettings.email_smtp_server.toString());
	private final static int port = Integer.parseInt(settings.readSetting(GUISettings.email_smtp_port.toString()));
	private final static String from = settings.readSetting(GUISettings.email_from_address.toString());
	private String recipient = settings.readSetting(GUISettings.email_from_address.toString());
	private Application application = null;
	private String subject = null;
	private String body = null;
	
	/** */
	public SendMail(final String sub, final String text, final String[] files) {

		subject = sub;
		body = text;

		new Thread(new Runnable() {
			public void run() {
				SendMail.sendEmailWithAttachments(sub, text, files);
			}
		}).start();
	}

	/**	*/
	public SendMail(final String sub, final String text) {

		subject = sub;
		body = text;

		new Thread(new Runnable() {
			public void run() {
				sendMessage();
			}
		}).start();
	}

	/** */
	public SendMail(final String sub, final String text, Application app) {

		subject = sub;
		body = text;
		application = app;

		new Thread(new Runnable() {
			public void run() {
				sendMessage();
			}
		}).start();
	}

	/** */
	public SendMail(final String str, Application app) {
		// valid email = /^([^@\s]+)@((?:[-a-z0-9]+\.)+[a-z]{2,})$/i
		recipient = str.substring(0, str.indexOf(" "));
		if (!recipient.matches("(?i)^([^@\\s]+)@((?:[-a-z0-9]+\\.)+[a-z]{2,})$")) {
			app.message("error - invalid recipient email", null, null);
			return;
		}

		subject = str.substring(str.indexOf("[") + 1, str.indexOf("]"));
		body = str.substring(str.indexOf("]") + 2);
		application = app;

		new Thread(new Runnable() {
			public void run() {
				sendMessage();
			}
		}).start();
	}

	/** */
	private void sendMessage() {
		Properties props = new Properties();
		props.put("mail.smtp.host", host);
		props.put("mail.user", username);
		props.put("mail.password", password);
		props.put("mail.smtp.port", port);
		props.put("mail.smtp.starttls.enable", "true");
		Session mailSession = Session.getDefaultInstance(props);
		
		try {

			MimeMessage message = new MimeMessage(mailSession);
			message.addRecipient(Message.RecipientType.TO, new InternetAddress(recipient));
			message.setFrom(new InternetAddress(from));
			message.setSubject(subject);
			message.setText(body);

			Transport transport = mailSession.getTransport("smtp");
			transport.connect(host, port, username, password);
			transport.sendMessage(message, message.getRecipients(Message.RecipientType.TO));
			transport.close();

			if (application != null) application.message("email has been sent", null, null);
			Util.log("email has been sent", this);

		} catch (MessagingException e) {
			Util.log(e.getMessage() + "error sending email, check settings", this);
			if (application != null)
				application.message("error sending email", null, null);
		}
	}

	/** */
	public static void sendEmailWithAttachments(String subject, String message, String[] attachFiles) {
		
		Properties props = new Properties();
		props.put("mail.smtp.host", host);
		props.put("mail.user", username);
		props.put("mail.password", password);
		props.put("mail.smtp.port", port);
		props.put("mail.smtp.starttls.enable", "true");
		props.put("mail.smtp.starttls.enable", "true");

		Authenticator auth = new Authenticator() {
			public PasswordAuthentication getPasswordAuthentication() {
				return new PasswordAuthentication(username, password);
			}
		};

		Session session = Session.getInstance(props, auth);
		Message msg = new MimeMessage(session);

		try {
			msg.setFrom(new InternetAddress(username));
			InternetAddress[] toAddresses = { new InternetAddress(from) };
			msg.setRecipients(Message.RecipientType.TO, toAddresses);
			msg.setSubject(subject);
			msg.setSentDate(new Date());

			MimeBodyPart messageBodyPart = new MimeBodyPart();
			messageBodyPart.setContent(message, "text/html");

			Multipart multipart = new MimeMultipart();
			multipart.addBodyPart(messageBodyPart);

			if (attachFiles != null && attachFiles.length > 0) {
				for (String filePath : attachFiles) {
					MimeBodyPart attachPart = new MimeBodyPart();
					try {
						attachPart.attachFile(filePath);
					} catch (Exception ex) {
						Util.log("sendEmailWithAttachments: " + ex.getMessage(), null);
					}
					multipart.addBodyPart(attachPart);
				}
			}
			msg.setContent(multipart);
			Transport.send(msg);

		} catch (Exception e) {
			Util.log("sendEmailWithAttachments: " + e.getMessage(), null);
		}
	}
}
