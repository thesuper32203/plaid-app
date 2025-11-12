package com.example.plaidapp.plaid_app.service;

import com.example.plaidapp.plaid_app.model.PlaidItem;
import com.example.plaidapp.plaid_app.repository.PlaidItemRepository;
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
    private final PlaidItemRepository plaidItemRepository;

    public PlaidLinkToken(PlaidService plaidService, PlaidItemRepository PlaidItemRepository ) {
        this.plaidApi = plaidService.getPlaidApi();
        this.plaidItemRepository = PlaidItemRepository;
    }

    public LinkTokenCreateResponse createLinkToken(String repId) throws IOException {

        PlaidItem existing = plaidItemRepository.findByRepIdAndAccessTokenIsNull(repId);
        if (existing != null) {
            // reuse existing link token instead of creating a new one
            LinkTokenCreateResponse response = new LinkTokenCreateResponse();
            response.setLinkToken(existing.getLinkToken());
            return response;
        }
        // Generate unique userId for this session
        String userId = UUID.randomUUID().toString();
        String clientUserId = repId + ":" + userId;

        LinkTokenCreateRequestUser user = new LinkTokenCreateRequestUser()
                .clientUserId(clientUserId);

        LinkTokenCreateRequestStatements statements =  new LinkTokenCreateRequestStatements()
                .startDate(LocalDate.now().minusMonths(4))
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
                .webhook("https://transmaterial-frederic-nonbeatific.ngrok-free.dev/plaid/webhook")
                .redirectUri("https://www.wiseadvances.com");

        Response<LinkTokenCreateResponse> response = plaidApi.linkTokenCreate(request).execute();

        if (!response.isSuccessful()) {
            String error = response.errorBody() != null ? response.errorBody().string() : "Unknown error";
            throw new IOException("Plaid API error: " + error);
        }

        LinkTokenCreateResponse linkResponse = response.body();

        PlaidItem plaidItem = new PlaidItem().builder()
                .linkToken(linkResponse.getLinkToken())
                .repId(repId)
                .userId(userId)
                .build();

        plaidItemRepository.save(plaidItem);
        System.out.printf("Created link session for rep %s with userId %s%n", repId, userId);

        return linkResponse;
    }
}
