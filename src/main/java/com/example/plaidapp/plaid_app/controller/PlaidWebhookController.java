package com.example.plaidapp.plaid_app.controller;


import com.example.plaidapp.plaid_app.model.PlaidItem;
import com.example.plaidapp.plaid_app.repository.PlaidItemRepository;
import com.example.plaidapp.plaid_app.service.PlaidExchangeToken;
import com.example.plaidapp.plaid_app.service.PlaidStatementService;
import com.example.plaidapp.plaid_app.service.PlaidWebhookVerifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plaid.client.model.StatementsAccount;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@RestController
@RequestMapping("/plaid/webhook")
public class PlaidWebhookController {

    private final PlaidExchangeToken plaidExchangeToken;
    private final PlaidItemRepository plaidItemRepository;
    private final PlaidStatementService plaidStatementService;

    private final PlaidWebhookVerifier verifier;
    private final ObjectMapper mapper;

    //private final WebhookJobPublisher publisher; // your async queue/worker


    public PlaidWebhookController(PlaidExchangeToken plaidExchangeToken, PlaidItemRepository plaidItemRepository,
                                  PlaidStatementService plaidStatementService, PlaidWebhookVerifier verifier, ObjectMapper mapper ) {
        this.plaidExchangeToken = plaidExchangeToken;
        this.plaidItemRepository = plaidItemRepository;
        this.plaidStatementService = plaidStatementService;
        this.verifier = verifier;
        this.mapper = mapper;
        //this.publisher = publisher;
    }

    @PostMapping
    public ResponseEntity<Void> handleWebhook(HttpServletRequest req,
                                              @RequestHeader(value = "plaid-verification", required = false) String plaidVerification) {
        try{
            byte[] bodyBytes = req.getInputStream().readAllBytes();

            if(plaidVerification == null || verifier.verify(plaidVerification, bodyBytes)) {
                Logger.getLogger(PlaidWebhookController.class.getName()).log(Level.SEVERE, "Plaid verification FAIL");
                return ResponseEntity.status(401).build();
            }
            Logger.getLogger(PlaidWebhookController.class.getName()).log(Level.INFO, "Plaid verification verified");
            // Safe to parse now

            return ResponseEntity.status(200).build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }
}
