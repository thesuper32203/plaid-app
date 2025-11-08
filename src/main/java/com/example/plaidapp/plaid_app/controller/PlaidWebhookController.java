package com.example.plaidapp.plaid_app.controller;


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

    @PostMapping
    public ResponseEntity<?> handleWebhook(@RequestBody Map<String, Object> payload) {
        try{
            System.out.println("Received Plaid Webhook:");
            System.out.println(payload);

            String webhookType = (String) payload.get("webhook_type");
            String webhookCode = (String) payload.get("webhook_code");

            if ("STATEMENTS".equalsIgnoreCase(webhookType) && "AUTHORIZATION_COMPLETED".equalsIgnoreCase(webhookCode)) {
                System.out.println("✅ User has completed STATEMENT link flow");
            }
            return ResponseEntity.ok().build();

        }catch (Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error handling webhook: " + e.getMessage());
        }
    }
}
