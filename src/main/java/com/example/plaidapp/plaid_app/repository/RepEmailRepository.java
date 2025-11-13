package com.example.plaidapp.plaid_app.repository;

import com.example.plaidapp.plaid_app.model.RepEmail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RepEmailRepository extends JpaRepository<RepEmail, String> {
    RepEmail findByRepId(String repId);
}