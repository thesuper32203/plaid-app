package com.example.plaidapp.plaid_app.service;

import com.example.plaidapp.plaid_app.model.PlaidItem;
import com.example.plaidapp.plaid_app.repository.PlaidItemRepository;
import com.plaid.client.model.*;
import org.springframework.stereotype.Service;
import retrofit2.Response;

import java.io.IOException;
import java.util.UUID;

@Service
public class PlaidExchangeToken {

    private final PlaidService plaidService;
    private final PlaidItemRepository plaidItemRepository;

    public PlaidExchangeToken(PlaidService plaidService, PlaidItemRepository plaidItemRepository) {
        this.plaidService = plaidService;
        this.plaidItemRepository = plaidItemRepository;
    }

    public PlaidItem exchangeToken(String publicToken, String linkToken) {
        try{
            PlaidItem item = plaidItemRepository.findByLinkToken(linkToken);

            if(item == null) {
                throw new RuntimeException("No PlaidItem found for linkToken: " + linkToken);
            }
            if(item.getAccessToken() != null){
                System.out.println("⚠️ Access token already stored, skipping duplicate exchange.");
                return item;
            }

            //Exchange public access token for  access token
            ItemPublicTokenExchangeRequest request = new ItemPublicTokenExchangeRequest()
                    .publicToken(publicToken);
            Response<ItemPublicTokenExchangeResponse> response = plaidService.getPlaidApi()
                    .itemPublicTokenExchange(request)
                    .execute();

            if (!response.isSuccessful()) {
                throw new RuntimeException("Failed to exchange token: " +
                        (response.errorBody() != null ? response.errorBody().string() : "Unknown error"));
            }

            String accessToken = response.body().getAccessToken();
            String itemId = response.body().getItemId();

            // Update the item
            item.setAccessToken(accessToken);
            item.setItemId(itemId);
            // Save the updated item (repId and userId are already there!)
            item = plaidItemRepository.save(item);
            System.out.printf("✅ Exchanged public token for item %s (user %s, rep %s)%n",
                    itemId, item.getUserId(), item.getRepId());
            return item;
        } catch (Exception e) {
            throw new RuntimeException("Failed to exchange public token", e);
        }
    }


}
