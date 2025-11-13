package com.example.plaidapp.plaid_app.service;

import com.example.plaidapp.plaid_app.util.StatementFile;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class EmailService {

    private static final Logger LOGGER = Logger.getLogger(EmailService.class.getName());
    private final JavaMailSender mailSender;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * Send bank statements to a rep via email
     * @param recipientEmail The rep's email address
     * @param repId The rep ID
     * @param statementFiles List of statement files to attach
     */
    public void sendBankStatements(String recipientEmail, String repId, List<StatementFile> statementFiles) {
        try {
            LOGGER.log(Level.INFO, "Preparing to send " + statementFiles.size() + " statements to " + recipientEmail);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            // Set email details
            helper.setTo(recipientEmail);
            helper.setSubject("Bank Statements Available - " + LocalDate.now());
            helper.setText(buildEmailBody(repId, statementFiles.size()), true); // true = HTML

            // Attach each statement PDF
            for (StatementFile statementFile : statementFiles) {
                String filename = extractFilename(statementFile.getKey());
                ByteArrayResource resource = new ByteArrayResource(statementFile.getData());
                helper.addAttachment(filename, resource);
            }

            // Send email
            mailSender.send(message);
            LOGGER.log(Level.INFO, "Successfully sent " + statementFiles.size() +
                    " statements to " + recipientEmail);

        } catch (MessagingException e) {
            LOGGER.log(Level.SEVERE, "Failed to send email to " + recipientEmail, e);
            throw new RuntimeException("Failed to send bank statements email", e);
        }
    }

    /**
     * Send bank statements with S3 presigned URLs instead of attachments
     * (Better for large files or many statements)
     */
    public void sendBankStatementsWithLinks(String recipientEmail, String repId, List<String> presignedUrls) {
        try {
            LOGGER.log(Level.INFO, "Sending email with " + presignedUrls.size() +
                    " statement links to " + recipientEmail);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(recipientEmail);
            helper.setSubject("Bank Statements Available - " + LocalDate.now());
            helper.setText(buildEmailBodyWithLinks(repId, presignedUrls), true);

            mailSender.send(message);
            LOGGER.log(Level.INFO, "Successfully sent statement links to " + recipientEmail);

        } catch (MessagingException e) {
            LOGGER.log(Level.SEVERE, "Failed to send email to " + recipientEmail, e);
            throw new RuntimeException("Failed to send bank statements email", e);
        }
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
        // Extract just the filename from the S3 key path
        String[] parts = s3Key.split("/");
        return parts[parts.length - 1];
    }
}