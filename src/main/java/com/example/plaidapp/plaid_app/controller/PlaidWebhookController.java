package com.example.plaidapp.plaid_app.controller;


import com.example.plaidapp.plaid_app.service.PlaidExchangeToken;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/plaid/webhook")
public class PlaidWebhookController {

    private final PlaidExchangeToken plaidExchangeToken;

    public PlaidWebhookController(PlaidExchangeToken plaidExchangeToken) {
        this.plaidExchangeToken = plaidExchangeToken;
    }

    @PostMapping
    public ResponseEntity<?> handleWebhook(@RequestBody Map<String, Object> payload) {
        try{


            String webhookType = (String) payload.get("webhook_type");
            String webhookCode = (String) payload.get("webhook_code");

            if ("LINK".equalsIgnoreCase(webhookType) &&
                    ("ITEM_ADD_RESULT".equalsIgnoreCase(webhookCode) || "SESSION_FINISHED".equalsIgnoreCase(webhookCode))) {
                String publicToken = (String) payload.get("public_token");

                //TODO: get the userId from plaid link
                String userId = (String) payload.get("link_session_id");
                plaidExchangeToken.exchangeToken(publicToken, userId);

            }
            return ResponseEntity.ok("Webhook processed successfully");

        }catch (Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error handling webhook: " + e.getMessage());
        }
    }
}
