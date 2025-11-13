package com.example.plaidapp.plaid_app.model;

import lombok.Data;

import java.util.Map;

    @Data
    public class PlaidWebhookDTO {

        private String webhook_type;
        private String webhook_code;
        private String link_session_id;
        private String link_token;
        private String public_token;


    }

