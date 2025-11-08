package com.example.plaidapp.plaid_app.service;

import com.example.plaidapp.plaid_app.config.PlaidConfig;
import com.plaid.client.ApiClient;
import com.plaid.client.request.PlaidApi;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

import java.util.HashMap;

@Service
public class PlaidService {

    private final PlaidApi plaidApi;

    public PlaidService(PlaidConfig config){

        HashMap<String,String> apiKeys = new HashMap<String,String>();
        apiKeys.put("clientId", config.getClientId());
        apiKeys.put("secret", config.getSecret());
        ApiClient apiClient = new ApiClient(apiKeys);
        apiClient.setPlaidAdapter(ApiClient.Sandbox);

        plaidApi = apiClient.createService(PlaidApi.class);

    }

    public PlaidApi getPlaidApi(){return plaidApi;}
}
