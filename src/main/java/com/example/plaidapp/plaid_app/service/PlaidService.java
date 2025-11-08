package com.example.plaidapp.plaid_app.service;

import com.plaid.client.ApiClient;
import com.plaid.client.request.PlaidApi;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

import java.util.HashMap;

@Service
public class PlaidService {

    private PlaidApi client;

    @Bean
    public void getClient() {
        if (client == null) {
            HashMap<String,String> apiKeys = new HashMap<String,String>();
            apiKeys.put("clientId", )
            ApiClient apiClient = new ApiClient()
        }
    }
}
