package com.highfive.contacts.email;

import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import java.util.Properties;

/**
 * Sends email via Gmail SMTP using a Google App Password.
 * No OAuth setup required — just the App Password from Google Account settings.
 */
public class GmailSender {

    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final String from;
    private final String to;

    public GmailSender(String host, int port, String username, String password,
                       String from, String to) {
        this.host     = host;
        this.port     = port;
        this.username = username;
        this.password = password;
        this.from     = from;
        this.to       = to;
    }

    public void sendReport(String subject, String htmlBody) throws MessagingException {
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", String.valueOf(port));
        props.put("mail.smtp.ssl.protocols", "TLSv1.2");

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });

        MimeMessage msg = new MimeMessage(session);
        msg.setFrom(new InternetAddress(from));
        msg.addRecipient(Message.RecipientType.TO, new InternetAddress(to));
        msg.setSubject(subject, "UTF-8");
        msg.setContent(htmlBody, "text/html; charset=utf-8");

        Transport.send(msg);
        System.out.println("[SMTP] Report sent to " + to);
    }
}
