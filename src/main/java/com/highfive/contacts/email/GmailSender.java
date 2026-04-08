package com.highfive.contacts.email;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import com.google.api.services.gmail.model.Message;

import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import java.io.*;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Collections;
import java.util.Properties;

/**
 * Sends email via Google Gmail API using OAuth2 installed-app credentials.
 *
 * First run: opens a browser (or prints a URL) for Google OAuth consent.
 * Subsequent runs: uses the stored refresh token automatically.
 *
 * Setup:
 *   1. Download credentials.json (OAuth2 Client ID, Desktop app type) from Google Cloud Console
 *   2. Set gmail.credentials.json.path in config.properties
 *   3. Run the program — authorize in the browser when prompted
 */
public class GmailSender {

    private static final String APPLICATION_NAME = "HighFiveRetreats Contact Uploader";
    private static final JacksonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

    private final String credentialsJsonPath;
    private final String tokensDir;
    private final String from;
    private final String to;

    public GmailSender(String credentialsJsonPath, String tokensDir, String from, String to) {
        this.credentialsJsonPath = credentialsJsonPath;
        this.tokensDir = tokensDir;
        this.from = from;
        this.to = to;
    }

    public void sendReport(String subject, String htmlBody) throws Exception {
        Gmail service = buildGmailService();
        MimeMessage mimeMessage = buildMimeMessage(subject, htmlBody);
        sendMessage(service, mimeMessage);
        System.out.println("[Gmail] Report sent to " + to);
    }

    // -------------------------------------------------------------------------
    // Gmail service
    // -------------------------------------------------------------------------

    private Gmail buildGmailService() throws Exception {
        var httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        Credential credential = authorize(httpTransport);
        return new Gmail.Builder(httpTransport, JSON_FACTORY, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    private Credential authorize(com.google.api.client.http.HttpTransport httpTransport) throws Exception {
        File credFile = Paths.get(credentialsJsonPath).toFile();
        if (!credFile.exists()) {
            throw new FileNotFoundException(
                    "Gmail credentials file not found: " + credentialsJsonPath
                    + "\nDownload it from Google Cloud Console → APIs & Services → Credentials → OAuth 2.0 Client IDs");
        }

        GoogleClientSecrets clientSecrets;
        try (FileReader reader = new FileReader(credFile)) {
            clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, reader);
        }

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                httpTransport, JSON_FACTORY, clientSecrets,
                Collections.singletonList(GmailScopes.GMAIL_SEND))
                .setDataStoreFactory(new FileDataStoreFactory(new File(tokensDir)))
                .setAccessType("offline")
                .build();

        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
        return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
    }

    // -------------------------------------------------------------------------
    // Message construction
    // -------------------------------------------------------------------------

    private MimeMessage buildMimeMessage(String subject, String htmlBody) throws Exception {
        Session session = Session.getDefaultInstance(new Properties());
        MimeMessage msg = new MimeMessage(session);
        msg.setFrom(new InternetAddress(from));
        msg.addRecipient(jakarta.mail.Message.RecipientType.TO, new InternetAddress(to));
        msg.setSubject(subject, "UTF-8");
        msg.setContent(htmlBody, "text/html; charset=utf-8");
        return msg;
    }

    private void sendMessage(Gmail service, MimeMessage mimeMessage) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        mimeMessage.writeTo(buffer);
        String encodedEmail = Base64.getUrlEncoder().encodeToString(buffer.toByteArray());
        Message message = new Message();
        message.setRaw(encodedEmail);
        service.users().messages().send("me", message).execute();
    }
}
