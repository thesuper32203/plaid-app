package com.example.plaidapp.plaid_app.service;
import com.example.plaidapp.plaid_app.model.PlaidItem;
import com.example.plaidapp.plaid_app.model.RepEmail;
import com.example.plaidapp.plaid_app.repository.PlaidItemRepository;
import com.example.plaidapp.plaid_app.repository.RepEmailRepository;
import com.example.plaidapp.plaid_app.service.EmailService;
import com.example.plaidapp.plaid_app.service.PlaidService;
import com.example.plaidapp.plaid_app.util.StatementFile;
import com.plaid.client.model.*;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Service;
import retrofit2.Response;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.net.URL;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class PlaidStatementService {

    private static final Logger LOGGER = Logger.getLogger(PlaidStatementService.class.getName());
    private static final String S3_BUCKET = "plaid-bank-statements";

    private final PlaidService plaidService;
    private final PlaidItemRepository plaidItemRepository;
    private final RepEmailRepository repEmailRepository;
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final EmailService emailService;

    public PlaidStatementService(PlaidService plaidService,
                                 PlaidItemRepository plaidItemRepository,
                                 RepEmailRepository repEmailRepository,
                                 S3Client s3Client,
                                 S3Presigner s3Presigner,
                                 EmailService emailService) {
        this.plaidService = plaidService;
        this.plaidItemRepository = plaidItemRepository;
        this.repEmailRepository = repEmailRepository;
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.emailService = emailService;
    }

    public void uploadStatementsAndNotify(PlaidItem plaidItem) {
        String accessToken = plaidItem.getAccessToken();
        String repId = plaidItem.getRepId();
        String itemId = plaidItem.getItemId();

        LOGGER.log(Level.INFO, "Starting statement upload and notification for item: " + itemId);

        try {
            // 1. Fetch statements from Plaid
            StatementsListRequest statementsRequest = new StatementsListRequest()
                    .accessToken(accessToken);

            Response<StatementsListResponse> response = plaidService.getPlaidApi()
                    .statementsList(statementsRequest)
                    .execute();

            if (!response.isSuccessful()) {
                String errorBody = response.errorBody() != null ? response.errorBody().string() : "Unknown error";
                LOGGER.log(Level.SEVERE, "Failed to list statements: " + errorBody);
                throw new RuntimeException("Failed to list statements");
            }

            List<StatementsAccount> accounts = response.body().getAccounts();
            List<StatementFile> statementFiles = fetchStatements(accounts, accessToken, repId);

            if (statementFiles.isEmpty()) {
                LOGGER.log(Level.INFO, "No statements to upload for item " + itemId);
                return;
            }

            // 2. Upload to S3
            uploadToS3(statementFiles);

            // 3. Send email notification
            sendEmailNotification(repId, statementFiles);

            LOGGER.log(Level.INFO, "Statement upload and notification completed for item: " + itemId);

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "IOException during statement upload", e);
            throw new RuntimeException("Failed to upload statements", e);
        }
    }

    private void uploadToS3(List<StatementFile> statementFiles) {
        LOGGER.log(Level.INFO, "Uploading " + statementFiles.size() + " statements to S3");

        statementFiles.parallelStream().forEach(statementFile -> {
            try {
                s3Client.putObject(
                        PutObjectRequest.builder()
                                .bucket(S3_BUCKET)
                                .key(statementFile.getKey())
                                .contentType("application/pdf")
                                .build(),
                        RequestBody.fromBytes(statementFile.getData())
                );
                LOGGER.log(Level.INFO, "Uploaded statement to: " + statementFile.getKey());
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Failed to upload: " + statementFile.getKey(), e);
            }
        });
    }

    private void sendEmailNotification(String repId, List<StatementFile> statementFiles) {
        try {
            // Get rep email from database
            RepEmail repEmail = repEmailRepository.findByRepId(repId);

            if (repEmail == null) {
                LOGGER.log(Level.WARNING, "No email found for rep: " + repId);
                return;
            }

            // Option 1: Send with attachments (good for small number of statements)
            if (statementFiles.size() <= 5) {
                emailService.sendBankStatements(repEmail.getEmail(), repId, statementFiles);
            }
            // Option 2: Send with presigned URLs (better for many statements or large files)
            else {
                List<String> presignedUrls = generatePresignedUrls(statementFiles);
                emailService.sendBankStatementsWithLinks(repEmail.getEmail(), repId, presignedUrls);
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to send email notification for rep: " + repId, e);
            // Don't throw - we don't want email failure to fail the entire process
        }
    }

    private List<String> generatePresignedUrls(List<StatementFile> statementFiles) {
        List<String> urls = new ArrayList<>();

        for (StatementFile file : statementFiles) {
            try {
                GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                        .bucket(S3_BUCKET)
                        .key(file.getKey())
                        .build();

                GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofHours(24))
                        .getObjectRequest(getObjectRequest)
                        .build();

                URL url = s3Presigner.presignGetObject(presignRequest).url();
                urls.add(url.toString());
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Failed to generate presigned URL for: " + file.getKey(), e);
            }
        }

        return urls;
    }

    private List<StatementFile> fetchStatements(List<StatementsAccount> accounts,
                                                String accessToken,
                                                String repId) throws IOException {
        List<StatementFile> statementFiles = new ArrayList<>();
        int totalStatements = accounts.stream()
                .mapToInt(acc -> acc.getStatements().size())
                .sum();

        LOGGER.log(Level.INFO, "Fetching " + totalStatements + " statements across " + accounts.size() + " accounts");

        for (StatementsAccount account : accounts) {
            for (StatementsStatement statement : account.getStatements()) {
                try {
                    StatementsDownloadRequest request = new StatementsDownloadRequest()
                            .accessToken(accessToken)
                            .statementId(statement.getStatementId());

                    Response<ResponseBody> downloadResponse = plaidService.getPlaidApi()
                            .statementsDownload(request)
                            .execute();

                    if (downloadResponse.isSuccessful() && downloadResponse.body() != null) {
                        byte[] bytes = downloadResponse.body().bytes();
                        String key = String.format(
                                "reps/%s/accounts/%s/statements/%s_%s.pdf",
                                repId,
                                account.getAccountId(),
                                LocalDate.now(),
                                UUID.randomUUID()
                        );
                        statementFiles.add(new StatementFile(key, bytes));
                        LOGGER.log(Level.INFO, "Downloaded statement: " + statement.getStatementId());
                    } else {
                        String errorBody = downloadResponse.errorBody() != null ?
                                downloadResponse.errorBody().string() : "Unknown error";
                        LOGGER.log(Level.WARNING, "Failed to download statement " +
                                statement.getStatementId() + ": " + errorBody);
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Exception downloading statement " +
                            statement.getStatementId(), e);
                }
            }
        }

        LOGGER.log(Level.INFO, "Successfully fetched " + statementFiles.size() + " statement files");
        return statementFiles;
    }
}