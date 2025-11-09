package com.example.plaidapp.plaid_app.service;

import com.example.plaidapp.plaid_app.model.PlaidItem;
import com.example.plaidapp.plaid_app.repository.PlaidItemRepository;
import com.plaid.client.model.*;
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

    public List<StatementsAccount> getTransactions(String itemId) {
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

}
