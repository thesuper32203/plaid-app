package com.example.plaidapp.plaid_app.service;

import com.example.plaidapp.plaid_app.model.PlaidItem;
import com.example.plaidapp.plaid_app.repository.PlaidItemRepository;
import com.example.plaidapp.plaid_app.util.StatementFile;
import com.plaid.client.model.*;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Service;
import retrofit2.Response;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
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
    private final S3Client s3Client;

    public PlaidStatementService(PlaidService plaidService,
                                 PlaidItemRepository plaidItemRepository,
                                 S3Client s3Client) {
        this.plaidService = plaidService;
        this.plaidItemRepository = plaidItemRepository;
        this.s3Client = s3Client;
    }

    public List<StatementsAccount> getAccounts(String itemId) {
        PlaidItem item = plaidItemRepository.findByItemId(itemId);
        if (item == null) {
            LOGGER.log(Level.SEVERE, "PlaidItem not found for itemId: " + itemId);
            throw new RuntimeException("PlaidItem not found");
        }

        String accessToken = item.getAccessToken();

        try {
            StatementsListRequest statementsRequest = new StatementsListRequest()
                    .accessToken(accessToken);

            Response<StatementsListResponse> response = plaidService.getPlaidApi()
                    .statementsList(statementsRequest)
                    .execute();

            if (!response.isSuccessful()) {
                String errorBody = response.errorBody() != null ? response.errorBody().string() : "Unknown error";
                LOGGER.log(Level.SEVERE, "Failed to fetch accounts: " + errorBody);
                throw new RuntimeException("Failed to fetch accounts");
            }

            List<StatementsAccount> accounts = response.body().getAccounts();
            LOGGER.log(Level.INFO, "Retrieved " + accounts.size() + " accounts for item " + itemId);
            return accounts;

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "IOException while fetching accounts", e);
            throw new RuntimeException("Failed to fetch accounts", e);
        }
    }

    public List<StatementFile> fetchStatements(List<StatementsAccount> accounts,
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

    public void uploadStatements(PlaidItem plaidItem) {
        String accessToken = plaidItem.getAccessToken();
        String repId = plaidItem.getRepId();
        String itemId = plaidItem.getItemId();

        LOGGER.log(Level.INFO, "Starting statement upload for item: " + itemId);

        try {
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

            // Upload files in parallel
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

            LOGGER.log(Level.INFO, "Statement upload completed for item: " + itemId);

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "IOException during statement upload", e);
            throw new RuntimeException("Failed to upload statements", e);
        }
    }
}
