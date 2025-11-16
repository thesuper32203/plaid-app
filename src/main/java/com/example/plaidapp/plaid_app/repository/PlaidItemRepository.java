package com.example.plaidapp.plaid_app.repository;

import com.example.plaidapp.plaid_app.model.PlaidItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlaidItemRepository extends JpaRepository<PlaidItem, String> {
    boolean existsByItemIdAndUserId(String itemId, String userId);

    PlaidItem findByItemId(String itemId);

    PlaidItem findByLinkToken(String linkToken);

    PlaidItem findByRepIdAndAccessTokenIsNull(String repId);
}
