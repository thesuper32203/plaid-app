package com.example.plaidapp.plaid_app.controller;


import com.example.plaidapp.plaid_app.model.PlaidItem;
import com.example.plaidapp.plaid_app.repository.PlaidItemRepository;
import com.example.plaidapp.plaid_app.service.PlaidExchangeToken;
import com.example.plaidapp.plaid_app.service.PlaidStatementService;
import com.plaid.client.model.StatementsAccount;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/plaid/webhook")
public class PlaidWebhookController {

    private final PlaidExchangeToken plaidExchangeToken;
    private final PlaidItemRepository plaidItemRepository;
    private final PlaidStatementService plaidStatementService;

    public PlaidWebhookController(PlaidExchangeToken plaidExchangeToken, PlaidItemRepository plaidItemRepository, PlaidStatementService plaidStatementService) {
        this.plaidExchangeToken = plaidExchangeToken;
        this.plaidItemRepository = plaidItemRepository;
        this.plaidStatementService = plaidStatementService;
    }

    @PostMapping
    public ResponseEntity<?> handleWebhook(@RequestBody Map<String, Object> payload) {
        try{

            String webhookType = (String) payload.get("webhook_type");
            String webhookCode = (String) payload.get("webhook_code");

            if ("LINK".equalsIgnoreCase(webhookType) &&
                    ("ITEM_ADD_RESULT".equalsIgnoreCase(webhookCode) ||
                            "SESSION_FINISHED".equalsIgnoreCase(webhookCode))) {


                String publicToken = (String) payload.get("public_token");
                String linkToken = (String) payload.get("link_token");

                System.out.println("linktoken: " + linkToken);

                if (publicToken == null) {
                    return ResponseEntity.badRequest().body("Missing public_token in webhook");
                }

                PlaidItem item = plaidExchangeToken.exchangeToken(publicToken, linkToken);
                plaidStatementService.uploadStatements(item);

            }
            return ResponseEntity.ok("Webhook processed successfully");

        }catch (Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error handling webhook: " + e.getMessage());
        }
    }
}
