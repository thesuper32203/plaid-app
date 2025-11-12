package com.example.plaidapp.plaid_app.repository;

import lombok.Data;

import java.util.Map;

    @Data
    public class PlaidWebhookDTO {

        private String webhook_type;   // e.g. "LINK", "ITEM", "STATEMENTS"
        private String webhook_code;   // e.g. "ITEM_ADD_RESULT", "STATEMENTS_AVAILABLE"
        private String item_id;        // Plaid item ID (may be null for some webhook types)
        private String link_token;     // Only present for LINK webhooks
        private String environment;    // "sandbox", "development", or "production"
        private Map<String, Object> error; // nested error object

    }

