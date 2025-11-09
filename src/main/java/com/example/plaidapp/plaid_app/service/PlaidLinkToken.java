package com.example.plaidapp.plaid_app.service;

import com.plaid.client.model.*;
import com.plaid.client.request.PlaidApi;
import org.springframework.stereotype.Service;
import retrofit2.Response;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

@Service
public class PlaidLinkToken {

    private final PlaidApi plaidApi;

    public PlaidLinkToken(PlaidService plaidService) {
        this.plaidApi = plaidService.getPlaidApi();
    }

    public LinkTokenCreateResponse createLinkToken() throws IOException {
        LinkTokenCreateRequestUser user = new LinkTokenCreateRequestUser()
                .clientUserId(UUID.randomUUID().toString());

        LinkTokenCreateRequestStatements statements =  new LinkTokenCreateRequestStatements()
                .startDate(LocalDate.now().minusMonths(5))
                .endDate(LocalDate.now());


        DepositoryFilter depository = new DepositoryFilter()
                .accountSubtypes(Arrays.asList(
                        DepositoryAccountSubtype.CHECKING,
                        DepositoryAccountSubtype.SAVINGS
                ));

        LinkTokenAccountFilters accountFilters = new LinkTokenAccountFilters()
                .depository(depository);

        LinkTokenCreateRequest request = new LinkTokenCreateRequest()
                .user(user)
                .clientName("Personal Finance App")
                .products(Collections.singletonList(Products.STATEMENTS))
                .statements(statements)
                .countryCodes(Collections.singletonList(CountryCode.US))
                .language("en")
                .accountFilters(accountFilters)
                .hostedLink(new LinkTokenCreateHostedLink())
                .webhook("https://transmaterial-frederic-nonbeatific.ngrok-free.dev/plaid/webhook");

        Response<LinkTokenCreateResponse> response = plaidApi.linkTokenCreate(request).execute();

        if (!response.isSuccessful()) {
            String error = response.errorBody() != null ? response.errorBody().string() : "Unknown error";
            throw new IOException("Plaid API error: " + error);
        }

        return response.body();
    }
}
