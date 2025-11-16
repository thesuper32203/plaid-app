package com.example.plaidapp.plaid_app.service;

import com.example.plaidapp.plaid_app.model.PlaidItem;
import com.example.plaidapp.plaid_app.repository.PlaidItemRepository;
import com.plaid.client.model.*;
import com.plaid.client.request.PlaidApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import retrofit2.Response;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class PlaidLinkToken {
    @Value("${plaid.webhook-url}")
    private String webhookUrl;
    private static final Logger LOGGER = Logger.getLogger(PlaidLinkToken.class.getName());
    private final PlaidApi plaidApi;
    private final PlaidItemRepository plaidItemRepository;

    public PlaidLinkToken(PlaidService plaidService, PlaidItemRepository plaidItemRepository) {
        this.plaidApi = plaidService.getPlaidApi();
        this.plaidItemRepository = plaidItemRepository;
    }

    public LinkTokenCreateResponse createLinkToken(String repId) throws IOException {
        // Generate unique userId for this session
        String userId = UUID.randomUUID().toString();
        String clientUserId = repId + ":" + userId;

        LinkTokenCreateRequestUser user = new LinkTokenCreateRequestUser()
                .clientUserId(clientUserId);

        LinkTokenCreateRequestStatements statements = new LinkTokenCreateRequestStatements()
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
                .webhook(webhookUrl)
                .redirectUri("https://www.wiseadvances.com");

        Response<LinkTokenCreateResponse> response = plaidApi.linkTokenCreate(request).execute();

        if (!response.isSuccessful()) {
            String error = response.errorBody() != null ? response.errorBody().string() : "Unknown error";
            LOGGER.log(Level.SEVERE, "Plaid API error: " + error);
            throw new IOException("Plaid API error: " + error);
        }

        LinkTokenCreateResponse linkResponse = response.body();

        // Create PlaidItem with auto-generated ID (not using linkToken as PK)
        PlaidItem plaidItem = PlaidItem.builder()
                .linkToken(linkResponse.getLinkToken())
                .repId(repId)
                .userId(userId)
                .build();

        plaidItem = plaidItemRepository.save(plaidItem);

        LOGGER.log(Level.INFO, String.format(
                "Created link session for rep %s with userId %s (PlaidItem ID: %s)",
                repId, userId, plaidItem.getId()
        ));

        return linkResponse;
    }
}