package oculusPrime;

import java.util.Properties;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

public class SendMail {

	private Settings settings;
	private String username;
	private String password;
	private String host;
	private int port;
	private String from;
	private String recipient;
	private Application application = null;
	private String subject = null;
	private String body = null;


	/** */
	public SendMail(final String str, Application app) {

		settings = Settings.getReference();
		username = settings.readSetting(GUISettings.email_username.toString());
		password = settings.readSetting(GUISettings.email_password.toString());
		host = settings.readSetting(GUISettings.email_smtp_server.toString());
		port = Integer.parseInt(settings.readSetting(GUISettings.email_smtp_port.toString()));
		from = settings.readSetting(GUISettings.email_from_address.toString());

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
				if (username.equals(Settings.DISABLED) && password.equals(Settings.DISABLED))
					sendMessage();
				else
					sendMessageAuth();
			}
		}).start();
	}

	private void sendMessage() {
		Properties props = new Properties();
		props.put("mail.smtp.host", host);
		props.put("mail.smtp.starttls.enable", true);
		props.put("mail.smtp.port", port);
		props.put("mail.debug", true);

		Util.debug("sendMessage(): "+host+" "+port,this);

		Session mailSession = Session.getDefaultInstance(props);

		try {

			MimeMessage message = new MimeMessage(mailSession);
			message.addRecipient(Message.RecipientType.TO, new InternetAddress(recipient));
			message.setFrom(new InternetAddress(from));
			message.setSubject(subject);
			message.setText(body);

			Transport.send(message);

			if (application != null) application.message("email has been sent", null, null);
			Util.log("email has been sent", this);

		} catch (MessagingException e) {
			Util.log(e.getMessage() + "error sending email, check settings", this);
			if (application != null)
				application.message("error sending email", null, null);
		}
	}

	/** largely taken from https://netcorecloud.com/tutorials/send-email-in-java-using-gmail-smtp  */
	private void sendMessageAuth() {
		Properties props = new Properties();
		props.put("mail.smtp.host", host);
		props.put("mail.smtp.ssl.enable", "true");
		props.put("mail.smtp.auth", "true");
		props.put("mail.smtp.port", port);

		Util.debug("sendMessageAuth(): "+host+" "+port+" "+username+" "+password,this);

		// Get the Session object.// and pass username and password
		Session mailSession = Session.getInstance(props, new javax.mail.Authenticator() {
			protected PasswordAuthentication getPasswordAuthentication() {
				return new PasswordAuthentication(username, password);
			}
		});

		mailSession.setDebug(true);

		try {

			MimeMessage message = new MimeMessage(mailSession);
			message.addRecipient(Message.RecipientType.TO, new InternetAddress(recipient));
			message.setFrom(new InternetAddress(from));
			message.setSubject(subject);
			message.setText(body);

			Transport.send(message);

			if (application != null) application.message("email has been sent", null, null);
			Util.log("email has been sent", this);

		} catch (MessagingException e) {
			Util.log(e.getMessage() + "error sending email, check settings", this);
			if (application != null)
				application.message("error sending email", null, null);
		}
	}

}
