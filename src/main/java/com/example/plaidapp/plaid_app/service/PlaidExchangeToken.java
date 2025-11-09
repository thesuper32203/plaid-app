package com.example.plaidapp.plaid_app.service;

import com.example.plaidapp.plaid_app.model.PlaidItem;
import com.example.plaidapp.plaid_app.repository.PlaidItemRepository;
import com.plaid.client.model.ItemPublicTokenExchangeRequest;
import com.plaid.client.model.ItemPublicTokenExchangeResponse;
import org.springframework.stereotype.Service;
import retrofit2.Response;

@Service
public class PlaidExchangeToken {

    private final PlaidService plaidService;
    private final PlaidItemRepository plaidItemRepository;

    public PlaidExchangeToken(PlaidService plaidService, PlaidItemRepository plaidItemRepository) {
        this.plaidService = plaidService;
        this.plaidItemRepository = plaidItemRepository;
    }

    public PlaidItem exchangeToken(String publicToken, String userId) {
        try{
            ItemPublicTokenExchangeRequest request = new ItemPublicTokenExchangeRequest()
                    .publicToken(publicToken);
            Response<ItemPublicTokenExchangeResponse> response = plaidService.getPlaidApi()
                    .itemPublicTokenExchange(request)
                    .execute();

            String accessToken = response.body().getAccessToken();

            String itemId = response.body().getItemId();
            PlaidItem item = PlaidItem.builder()
                            .itemId(itemId)
                            .userId(userId)
                            .accessToken(accessToken)
                            .build();

            if (!plaidItemRepository.existsByItemIdAndUserId(itemId, userId)) {
                plaidItemRepository.save(item);
            }
            return item;
        } catch (Exception e) {
            throw new RuntimeException("Failed to exchange public token", e);
        }



    }
}
