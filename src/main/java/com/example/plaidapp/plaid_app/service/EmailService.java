package com.example.plaidapp.plaid_app.service;

import com.example.plaidapp.plaid_app.util.StatementFile;
import jakarta.activation.DataHandler;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.RawMessage;
import software.amazon.awssdk.services.ses.model.SendRawEmailRequest;
import software.amazon.awssdk.services.ses.model.SesException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class EmailService {
    private static final Logger LOGGER = Logger.getLogger(EmailService.class.getName());

    private final SesClient sesClient;
    private final String fromEmail;

    public EmailService(
            @Value("${aws.access-key}") String accessKey,
            @Value("${aws.secret-key}") String secretKey,
            @Value("${aws.region}") String region,
            @Value("${ses.from-email}") String fromEmail) {

        this.fromEmail = fromEmail;

        AwsBasicCredentials awsCreds = AwsBasicCredentials.create(accessKey, secretKey);

        this.sesClient = SesClient.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(awsCreds))
                .build();
    }

    /**
     * Send bank statements to a rep via email (PDF attachments) using AWS SES
     * @param recipientEmail The rep's email address
     * @param repId The rep ID
     * @param statementFiles List of statement files to attach
     */
    @Async("webhookExecutor")
    public void sendBankStatements(String recipientEmail, String repId, List<StatementFile> statementFiles) {
        try {
            LOGGER.log(Level.INFO, "Preparing to send " + statementFiles.size() + " statements to " + recipientEmail);

            MimeMessage message = buildMimeMessageWithAttachments(recipientEmail, repId, statementFiles);
            sendViaSes(message);

            LOGGER.log(Level.INFO, "Successfully sent " + statementFiles.size() +
                    " statements to " + recipientEmail);

        } catch (MessagingException | IOException | SesException e) {
            LOGGER.log(Level.SEVERE, "Failed to send email to " + recipientEmail, e);
            throw new RuntimeException("Failed to send bank statements email via SES", e);
        }
    }

    /**
     * Send bank statements with S3 presigned URLs instead of attachments (using AWS SES)
     * (Better for large files or many statements)
     */
    @Async("webhookExecutor")
    public void sendBankStatementsWithLinks(String recipientEmail, String repId, List<String> presignedUrls) {
        try {
            LOGGER.log(Level.INFO, "Sending email with " + presignedUrls.size() +
                    " statement links to " + recipientEmail);

            MimeMessage message = buildMimeMessageWithLinks(recipientEmail, repId, presignedUrls);
            sendViaSes(message);

            LOGGER.log(Level.INFO, "Successfully sent statement links to " + recipientEmail);

        } catch (MessagingException | IOException | SesException e) {
            LOGGER.log(Level.SEVERE, "Failed to send email to " + recipientEmail, e);
            throw new RuntimeException("Failed to send bank statements email via SES", e);
        }
    }

    // ---------- Internal helpers ----------

    private MimeMessage buildMimeMessageWithAttachments(
            String recipientEmail,
            String repId,
            List<StatementFile> statementFiles
    ) throws MessagingException {

        Session session = Session.getInstance(new Properties());
        MimeMessage message = new MimeMessage(session);

        message.setFrom(new InternetAddress(fromEmail));
        message.setRecipient(Message.RecipientType.TO, new InternetAddress(recipientEmail));
        message.setSubject("Bank Statements Available - " + LocalDate.now());

        // multipart/mixed: HTML body + attachments
        MimeMultipart mixed = new MimeMultipart("mixed");

        // HTML body
        MimeBodyPart bodyPart = new MimeBodyPart();
        bodyPart.setContent(buildEmailBody(repId, statementFiles.size()), "text/html; charset=UTF-8");
        mixed.addBodyPart(bodyPart);

        // Attach each statement PDF
        for (StatementFile statementFile : statementFiles) {
            MimeBodyPart attachmentPart = new MimeBodyPart();

            String filename = extractFilename(statementFile.getKey());
            ByteArrayDataSource dataSource =
                    new ByteArrayDataSource(statementFile.getData(), "application/pdf");

            attachmentPart.setDataHandler(new DataHandler(dataSource));
            attachmentPart.setFileName(filename);

            mixed.addBodyPart(attachmentPart);
        }

        message.setContent(mixed);
        message.saveChanges();
        return message;
    }

    private MimeMessage buildMimeMessageWithLinks(
            String recipientEmail,
            String repId,
            List<String> presignedUrls
    ) throws MessagingException {

        Session session = Session.getInstance(new Properties());
        MimeMessage message = new MimeMessage(session);

        message.setFrom(new InternetAddress(fromEmail));
        message.setRecipient(Message.RecipientType.TO, new InternetAddress(recipientEmail));
        message.setSubject("Bank Statements Available - " + LocalDate.now());

        MimeMultipart alternative = new MimeMultipart("alternative");

        MimeBodyPart htmlPart = new MimeBodyPart();
        htmlPart.setContent(buildEmailBodyWithLinks(repId, presignedUrls), "text/html; charset=UTF-8");
        alternative.addBodyPart(htmlPart);

        message.setContent(alternative);
        message.saveChanges();
        return message;
    }

    private void sendViaSes(MimeMessage message) throws MessagingException, IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        message.writeTo(outputStream);
        byte[] bytes = outputStream.toByteArray();

        RawMessage rawMessage = RawMessage.builder()
                .data(SdkBytes.fromByteArray(bytes))
                .build();

        SendRawEmailRequest request = SendRawEmailRequest.builder()
                .rawMessage(rawMessage)
                .build();

        sesClient.sendRawEmail(request);
    }

    private String buildEmailBody(String repId, int statementCount) {
        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background-color: #4CAF50; color: white; padding: 20px; text-align: center; }
                    .content { background-color: #f9f9f9; padding: 20px; border-radius: 5px; margin-top: 20px; }
                    .footer { margin-top: 20px; font-size: 12px; color: #666; text-align: center; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>Bank Statements Ready</h1>
                    </div>
                    <div class="content">
                        <p>Hello,</p>
                        <p>Your bank statements are now available for review.</p>
                        <p><strong>Rep ID:</strong> %s</p>
                        <p><strong>Number of Statements:</strong> %d</p>
                        <p><strong>Date:</strong> %s</p>
                        <p>The statements are attached to this email as PDF files.</p>
                        <p>If you have any questions, please contact support.</p>
                    </div>
                    <div class="footer">
                        <p>This is an automated message. Please do not reply to this email.</p>
                    </div>
                </div>
            </body>
            </html>
            """, repId, statementCount, LocalDate.now());
    }

    private String buildEmailBodyWithLinks(String repId, List<String> presignedUrls) {
        StringBuilder linksHtml = new StringBuilder();
        for (int i = 0; i < presignedUrls.size(); i++) {
            linksHtml.append(String.format(
                    "<li><a href=\"%s\">Download Statement %d</a> (Link expires in 24 hours)</li>",
                    presignedUrls.get(i), i + 1
            ));
        }

        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background-color: #4CAF50; color: white; padding: 20px; text-align: center; }
                    .content { background-color: #f9f9f9; padding: 20px; border-radius: 5px; margin-top: 20px; }
                    .footer { margin-top: 20px; font-size: 12px; color: #666; text-align: center; }
                    ul { padding-left: 20px; }
                    a { color: #4CAF50; text-decoration: none; font-weight: bold; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>Bank Statements Ready</h1>
                    </div>
                    <div class="content">
                        <p>Hello,</p>
                        <p>Your bank statements are now available for download.</p>
                        <p><strong>Rep ID:</strong> %s</p>
                        <p><strong>Number of Statements:</strong> %d</p>
                        <p><strong>Date:</strong> %s</p>
                        <p><strong>Download Links:</strong></p>
                        <ul>
                            %s
                        </ul>
                        <p><strong>Note:</strong> These links will expire in 24 hours for security purposes.</p>
                    </div>
                    <div class="footer">
                        <p>This is an automated message. Please do not reply to this email.</p>
                    </div>
                </div>
            </body>
            </html>
            """, repId, presignedUrls.size(), LocalDate.now(), linksHtml.toString());
    }

    private String extractFilename(String s3Key) {
        String[] parts = s3Key.split("/");
        return parts[parts.length - 1];
    }
}