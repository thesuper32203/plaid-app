package com.example.plaidapp.plaid_app.service;

import com.example.plaidapp.plaid_app.model.PlaidItem;
import com.example.plaidapp.plaid_app.model.PlaidWebhookDTO;
import com.example.plaidapp.plaid_app.repository.PlaidItemRepository;
import com.plaid.client.model.*;
import org.springframework.stereotype.Service;
import retrofit2.Response;

import java.io.IOException;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class PlaidExchangeToken {

    private static final Logger LOGGER = Logger.getLogger(PlaidExchangeToken.class.getName());
    private final PlaidService plaidService;
    private final PlaidItemRepository plaidItemRepository;

    public PlaidExchangeToken(PlaidService plaidService, PlaidItemRepository plaidItemRepository) {
        this.plaidService = plaidService;
        this.plaidItemRepository = plaidItemRepository;
    }

    public void exchangeToken(PlaidWebhookDTO dto) throws IOException {
        try {
            LOGGER.log(Level.INFO, "Starting token exchange process");
            String linkToken = dto.getLink_token();
            String publicToken = dto.getPublic_token();

            LOGGER.log(Level.INFO, "Link token: " + linkToken);
            LOGGER.log(Level.INFO, "Public token: " + (publicToken != null ? "present" : "missing"));

            PlaidItem item = plaidItemRepository.findByLinkToken(linkToken);

            if (item == null) {
                LOGGER.log(Level.WARNING, "Plaid item not found for link token: " + linkToken);
                return;
            }

            if (item.getAccessToken() != null) {
                LOGGER.log(Level.INFO, "Access token already exists, skipping duplicate exchange for link token: " + linkToken);
                return;
            }

            // Exchange public token for access token
            LOGGER.log(Level.INFO, "Exchanging public token for access token");
            ItemPublicTokenExchangeRequest request = new ItemPublicTokenExchangeRequest()
                    .publicToken(publicToken);

            Response<ItemPublicTokenExchangeResponse> response = plaidService.getPlaidApi()
                    .itemPublicTokenExchange(request)
                    .execute();

            if (!response.isSuccessful()) {
                String errorBody = response.errorBody() != null ? response.errorBody().string() : "Unknown error";
                LOGGER.log(Level.SEVERE, "Failed to exchange token: " + errorBody);
                return;
            }

            String accessToken = response.body().getAccessToken();
            String itemId = response.body().getItemId();

            // Update and save the item
            item.setAccessToken(accessToken);
            item.setItemId(itemId);
            item = plaidItemRepository.save(item);

            LOGGER.log(Level.INFO, String.format(
                    "Successfully exchanged token for item %s (user %s, rep %s)",
                    itemId, item.getUserId(), item.getRepId()
            ));

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Exception during token exchange", e);
            throw new RuntimeException("Failed to exchange public token", e);
        }
    }


}
