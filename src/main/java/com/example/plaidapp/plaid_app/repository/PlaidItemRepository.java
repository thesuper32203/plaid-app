package com.example.plaidapp.plaid_app.repository;

import com.example.plaidapp.plaid_app.model.PlaidItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaidItemRepository extends JpaRepository<PlaidItem, Long> {
}
