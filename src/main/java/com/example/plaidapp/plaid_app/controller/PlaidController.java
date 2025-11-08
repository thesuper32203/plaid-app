package com.example.plaidapp.plaid_app.controller;


import com.example.plaidapp.plaid_app.service.PlaidLinkToken;
import com.example.plaidapp.plaid_app.service.PlaidService;
import com.plaid.client.model.LinkTokenCreateResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
public class PlaidController {

    private final PlaidService plaidService;
    private final PlaidLinkToken plaidLinkToken;

    public PlaidController(PlaidService plaidService, PlaidLinkToken plaidLinkToken) {
        this.plaidService = plaidService;
        this.plaidLinkToken = plaidLinkToken;
    }

    @GetMapping("/")
    public ResponseEntity<?> home() {
        try {
            LinkTokenCreateResponse response = plaidLinkToken.createLinkToken();
            String hostedLinkUrl = response.getHostedLinkUrl();

            // Redirect user to the Plaid-hosted link page
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header("Location", hostedLinkUrl)
                    .build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to create Plaid link token: " + e.getMessage());
        }
    }


}

