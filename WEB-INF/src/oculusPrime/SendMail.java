package oculusPrime;

import java.util.*;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.*;
import javax.mail.internet.*;

import oculusPrime.Application;
import oculusPrime.ManualSettings;
import oculusPrime.Settings;

public class SendMail {

	private Settings settings = Settings.getReference();
	private final String username = settings.readSetting(GUISettings.email_username.toString());
	private final String password = settings.readSetting(GUISettings.email_password.toString());
	private final String host = settings.readSetting(GUISettings.email_smtp_server.toString());
	private final int port = Integer.parseInt(settings.readSetting(GUISettings.email_smtp_port.toString()));
	private final String from = settings.readSetting(GUISettings.email_from_address.toString());
	
	private String subject = null;
	private String body = null;
	private String fileName = null;
	private String recipient = null;
	
	private Application application = null;

	/** */
	public SendMail(final String sub, final String text, final String file) {
		
		subject = sub;
		body = text;
		fileName = file;

		new Thread(new Runnable() {
			public void run() {
				sendAttachment();
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


	/** send messages to user */
	public SendMail(final String sub, final String text, final String file, Application app) {
		
		subject = sub;
		body = text;
		fileName = file;
		application = app;
		
		new Thread(new Runnable() {
			public void run() {
				sendAttachment();
			}
		}).start();
	}

	/** send messages to user */
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
	
	/** send messages to user */
	public SendMail(final String str, Application app) {
		// valid email = /^([^@\s]+)@((?:[-a-z0-9]+\.)+[a-z]{2,})$/i
		recipient = str.substring(0, str.indexOf(" "));
		if (!recipient.matches("(?i)^([^@\\s]+)@((?:[-a-z0-9]+\\.)+[a-z]{2,})$")) {
			app.message("error - invalid recipient email", null, null);
			return;
		}

		subject = str.substring(str.indexOf("[")+1, str.indexOf("]"));
		body = str.substring(str.indexOf("]")+2);
		application = app;
		
		new Thread(new Runnable() {
			public void run() {
				sendMessage();
			}
		}).start();
	}
	
//	/** gmail */
//	private void sendMessage() {
//	
//	int SMTP_HOST_PORT = 587;
//	String SMTP_HOST_NAME = "smtp.gmail.com";
//
//
//		if (user == null || pass == null) {
//			System.out.println("no email and password found in settings");
//			return;
//		}
//		
//		try {
//
//			Properties props = new Properties();
//			props.put("mail.smtps.host", SMTP_HOST_NAME);
//			props.put("mail.smtps.auth", "true");
//			props.put("mail.smtp.starttls.enable", "true");
//
//			Session mailSession = Session.getDefaultInstance(props);
//			Transport transport = mailSession.getTransport("smtp");
//
//			// if (debug) mailSession.setDebug(true);
//
//			MimeMessage message = new MimeMessage(mailSession);
//			message.setSubject(subject);
//			message.setContent(body, "text/plain");
//			message.addRecipient(Message.RecipientType.TO, new InternetAddress(user));
//
//			transport.connect(SMTP_HOST_NAME, SMTP_HOST_PORT, user, pass);
//			transport.sendMessage(message, message.getRecipients(Message.RecipientType.TO));
//			transport.close();
//
//			// if (debug) System.out.println("... email sent");
//			
//			if(application!=null) application.message("email has been sent", null, null);
//
//		} catch (Exception e) {
//			//log.error(e.getMessage() + "error sending email, check settings");
//			if(application!=null) application.message("error sending email", null, null);
//		}
//	}

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
//			Transport.send(message);

			if(application!=null) application.message("email has been sent", null, null);
			Util.log("email has been sent", this);

		} catch (MessagingException e) {
			//log.error(e.getMessage() + "error sending email, check settings");
			if(application!=null) application.message("error sending email", null, null);
			e.printStackTrace();
		}
	}
	
	/** */
	private void sendAttachment() {

//		if (user == null || pass == null) {
//			// log.error("no email and password found in settings");
//			System.out.println("OCULUS: no email and password found in settings");
//			return;
//		}
		
		try {

			// if (debug) System.out.println("sending email..");
			
			Properties props = new Properties();
			props.put("mail.smtps.host", host);
			props.put("mail.smtps.auth", "true");
			props.put("mail.smtp.starttls.enable", "true");

			Session mailSession = Session.getDefaultInstance(props);
			Transport transport = mailSession.getTransport("smtp");

			// if (debug) mailSession.setDebug(true);

			MimeMessage message = new MimeMessage(mailSession);
			message.setSubject(subject);
			message.addRecipient(Message.RecipientType.TO, new InternetAddress(recipient));

			BodyPart messageBodyPart = new MimeBodyPart();
			messageBodyPart.setText(body);
			Multipart multipart = new MimeMultipart();
			multipart.addBodyPart(messageBodyPart);

			messageBodyPart = new MimeBodyPart();
			DataSource source = new FileDataSource(fileName);
			messageBodyPart.setDataHandler(new DataHandler(source));
			messageBodyPart.setFileName(fileName);
			multipart.addBodyPart(messageBodyPart);
			message.setContent(multipart);

			transport.connect(host, port, username, password);
			transport.sendMessage(message, message.getRecipients(Message.RecipientType.TO));
			transport.close();

			//if (debug) System.out.println("... email sent");
			
			if(application!=null) application.message("email has been sent", null, null);

		} catch (Exception e) {
			System.out.println("error sending email, check settings");
			if(application!=null) application.message("error sending email", null, null);
		}
	}
}