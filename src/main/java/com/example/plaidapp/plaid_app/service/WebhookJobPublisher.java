package com.example.plaidapp.plaid_app.service;

import com.example.plaidapp.plaid_app.model.PlaidItem;
import com.example.plaidapp.plaid_app.model.PlaidWebhookDTO;
import com.example.plaidapp.plaid_app.repository.PlaidItemRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class WebhookJobPublisher {

    private static final Logger LOGGER = Logger.getLogger(WebhookJobPublisher.class.getName());

    private final PlaidExchangeToken plaidExchangeToken;
    private final PlaidStatementService plaidStatementService;
    private final PlaidItemRepository plaidItemRepository;

    public WebhookJobPublisher(PlaidExchangeToken plaidExchangeToken,
                               PlaidStatementService plaidStatementService,
                               PlaidItemRepository plaidItemRepository) {
        this.plaidExchangeToken = plaidExchangeToken;
        this.plaidStatementService = plaidStatementService;
        this.plaidItemRepository = plaidItemRepository;
    }

    @Async
    public void publish(PlaidWebhookDTO dto) {
        try {
            LOGGER.log(Level.INFO, "Processing webhook: " + dto.getWebhook_type() + " / " + dto.getWebhook_code());

            String webhookType = dto.getWebhook_type();
            String webhookCode = dto.getWebhook_code();

            // Handle different webhook types
            if ("LINK".equals(webhookType)) {
                handleStatementsWebhook(dto, webhookCode);
            } else {
                LOGGER.log(Level.INFO, "Unhandled webhook type: " + webhookType);
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error processing webhook", e);
        }
    }

    private void handleStatementsWebhook(PlaidWebhookDTO dto, String webhookCode) {
        try {
            switch (webhookCode) {
                case "ITEM_ADD_RESULT":
                    LOGGER.log(Level.INFO, "Statements ready, exchanging token");
                    plaidExchangeToken.exchangeToken(dto);

                    // After token exchange, fetch the item and upload statements
                    String linkToken = dto.getLink_token();
                    PlaidItem item = plaidItemRepository.findByLinkToken(linkToken);

                    if (item != null && item.getAccessToken() != null) {
                        LOGGER.log(Level.INFO, "Starting statement upload for item: " + item.getItemId());
                        plaidStatementService.uploadStatements(item);
                    } else {
                        LOGGER.log(Level.WARNING, "Cannot upload statements - item or access token not found");
                    }
                    break;

                case "STATEMENTS_REFRESH_COMPLETE":
                    LOGGER.log(Level.INFO, "Statements refresh completed");
                    // Handle refresh if needed
                    break;

                default:
                    LOGGER.log(Level.INFO, "Unhandled statements webhook code: " + webhookCode);
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error handling statements webhook", e);
        }
    }
}