package com.example.plaidapp.plaid_app.service;

import com.example.plaidapp.plaid_app.model.PlaidItem;
import com.example.plaidapp.plaid_app.repository.PlaidItemRepository;
import com.plaid.client.model.*;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Service;
import retrofit2.Response;

import javax.swing.plaf.nimbus.State;
import java.io.IOException;
import java.util.List;

@Service
public class PlaidStatementService {

    private final PlaidService plaidService;
    private final PlaidItemRepository plaidItemRepository;

    public PlaidStatementService(PlaidService plaidService, PlaidItemRepository plaidItemRepository ) {
        this.plaidService = plaidService;
        this.plaidItemRepository = plaidItemRepository;
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

    public void downloadAllStatements(String itemId) {
        PlaidItem item = plaidItemRepository.findByItemId(itemId);
        String accessToken = item.getAccessToken();

        try {
            StatementsListRequest statementsRequest = new StatementsListRequest().accessToken(accessToken);
            Response<StatementsListResponse> response = plaidService.getPlaidApi()
                    .statementsList(statementsRequest)
                    .execute();

            List<StatementsAccount> accounts = response.body().getAccounts();

            for (StatementsAccount account : accounts) {
                for (StatementsStatement statement : account.getStatements()) {
                    StatementsDownloadRequest downloadRequest = new StatementsDownloadRequest()
                            .accessToken(accessToken)
                            .statementId(statement.getStatementId());

                    Response<ResponseBody> downloadResponse = plaidService.getPlaidApi()
                            .statementsDownload(downloadRequest)
                            .execute();

                    if (downloadResponse.isSuccessful()) {
                        byte[] pdfData = downloadResponse.body().bytes();
                        System.out.println("Downloaded statement for " + account.getAccountName());
                        // TODO: Upload to S3 here
                    } else {
                        System.err.println("Error downloading statement for " + account.getAccountName());
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Error fetching statements", e);
        }
    }


}
