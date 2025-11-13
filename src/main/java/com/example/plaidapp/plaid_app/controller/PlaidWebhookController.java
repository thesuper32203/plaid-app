package com.example.plaidapp.plaid_app.controller;

import com.example.plaidapp.plaid_app.model.PlaidWebhookDTO;
import com.example.plaidapp.plaid_app.service.PlaidWebhookVerifier;
import com.example.plaidapp.plaid_app.service.WebhookJobPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.logging.Level;
import java.util.logging.Logger;

@RestController
@RequestMapping("/plaid/webhook")
public class PlaidWebhookController {

    private final PlaidWebhookVerifier verifier;
    private final ObjectMapper objectMapper;
    private final WebhookJobPublisher publisher;

    public PlaidWebhookController(PlaidWebhookVerifier verifier,
                                  ObjectMapper objectMapper,
                                  WebhookJobPublisher publisher) {
        this.verifier = verifier;
        this.publisher = publisher;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<Void> handleWebhook(
            HttpServletRequest request,
            @RequestHeader(value = "plaid-verification", required = false) String plaidVerification) {

        try {
            // Log the verification attempt
            Logger.getLogger(PlaidWebhookController.class.getName())
                    .log(Level.INFO, "Received webhook with verification header: " +
                            (plaidVerification != null ? "present" : "missing"));

            // Verify the webhook signature
            if (plaidVerification == null) {
                Logger.getLogger(PlaidWebhookController.class.getName())
                        .log(Level.SEVERE, "Missing plaid-verification header");
                return ResponseEntity.status(401).build();
            }

            // Get the raw body bytes from our custom cached wrapper
            byte[] bodyBytes = (byte[]) request.getAttribute(
                    com.example.plaidapp.plaid_app.service.CachedBodyFilter.CACHED_BODY_ATTRIBUTE
            );

            if (bodyBytes == null) {
                Logger.getLogger(PlaidWebhookController.class.getName())
                        .log(Level.SEVERE, "Cached body not found in request attributes");
                return ResponseEntity.status(500).build();
            }

            Logger.getLogger(PlaidWebhookController.class.getName())
                    .log(Level.INFO, "Body bytes length: " + bodyBytes.length);

            boolean verified = verifier.verify(plaidVerification, bodyBytes);

            if (!verified) {
                Logger.getLogger(PlaidWebhookController.class.getName())
                        .log(Level.SEVERE, "Plaid verification FAILED");
                return ResponseEntity.status(401).build();
            }

            Logger.getLogger(PlaidWebhookController.class.getName())
                    .log(Level.INFO, "Plaid verification successful");

            // Parse the webhook payload
            PlaidWebhookDTO dto = objectMapper.readValue(bodyBytes, PlaidWebhookDTO.class);

            // Publish to async queue for processing
            publisher.publish(dto);

            return ResponseEntity.ok().build();

        } catch (Exception e) {
            Logger.getLogger(PlaidWebhookController.class.getName())
                    .log(Level.SEVERE, "Error processing webhook", e);
            return ResponseEntity.status(500).build();
        }
    }
}