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
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import javax.swing.plaf.nimbus.State;
import java.io.IOException;
import java.net.URL;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class PlaidStatementService {

    private final PlaidService plaidService;
    private final PlaidItemRepository plaidItemRepository;
    private final S3Client s3Client;
    private final S3TestService s3Presigner;

    public PlaidStatementService(PlaidService plaidService, PlaidItemRepository plaidItemRepository, S3Client s3Client, S3TestService s3Presigner ) {
        this.plaidService = plaidService;
        this.plaidItemRepository = plaidItemRepository;
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
    }

    public List<StatementsAccount> getAccounts(String itemId) {
        PlaidItem item = plaidItemRepository.findByItemId(itemId);
        String accessToken = item.getAccessToken();

        try{
            StatementsListRequest statementsRequest = new StatementsListRequest().accessToken(accessToken);
            Response<StatementsListResponse> statementsListResponseResponse = plaidService.getPlaidApi()
                    .statementsList(statementsRequest).execute();
            List<StatementsAccount> accounts = statementsListResponseResponse.body().getAccounts();
            return accounts;

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public List<StatementFile> fetchStatements(List<StatementsAccount> accounts,String accessToken, String repId) throws IOException {
        List<StatementFile> statementFiles = new ArrayList<>();

        for (StatementsAccount account : accounts) {
            for(StatementsStatement statement : account.getStatements()) {
                StatementsDownloadRequest request = new StatementsDownloadRequest()
                        .accessToken(accessToken)
                        .statementId(statement.getStatementId());

                Response<ResponseBody> downloadResposne = plaidService.getPlaidApi()
                        .statementsDownload(request)
                        .execute();

                if(downloadResposne.isSuccessful()) {
                    byte[] bytes = downloadResposne.body().bytes();
                    String key = String.format(
                            "reps/%s/accounts/%s/statements/%s_%s.pdf",
                            repId,
                            account.getAccountId(),
                            LocalDate.now(),
                            UUID.randomUUID()
                    );
                    statementFiles.add(new StatementFile(key, bytes));
                    //String url = s3Presigner.createPresignedGetUrl("plaid-bank-statements",key);

                }
            }
        }
        return statementFiles;
    }

    public void uploadStatements(PlaidItem plaidItem) {

        String accessToken = plaidItem.getAccessToken();
        String repId = plaidItem.getRepId();
        try {
            StatementsListRequest statementsRequest = new StatementsListRequest().accessToken(accessToken);
            Response<StatementsListResponse> response = plaidService.getPlaidApi()
                    .statementsList(statementsRequest)
                    .execute();
            List<StatementsAccount> accounts = response.body().getAccounts();
            List<StatementFile> statementFiles = fetchStatements(accounts, accessToken, repId);

            statementFiles.parallelStream().forEach(statementFile -> {
                try{
                s3Client.putObject(
                        PutObjectRequest.builder()
                                .bucket("plaid-bank-statements")
                                .key(statementFile.getKey())
                                .contentType("application/pdf")
                                .build(),
                        RequestBody.fromBytes(statementFile.getData())

                );
                System.out.println("Plaid statement file saved to " + statementFile.getKey());
                } catch (Exception e) {
                    System.err.println("Failed to upload: " + statementFile.getKey());
                }
            });

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    private String generatePresignedUrl(String key) {
        try (S3Presigner presigner = S3Presigner.create()) {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket("plaid-bank-statements")
                    .key(key)
                    .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(30))
                    .getObjectRequest(getObjectRequest)
                    .build();

            URL url = presigner.presignGetObject(presignRequest).url();
            return url.toString();
        }
    }

}
