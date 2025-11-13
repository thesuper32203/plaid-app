package com.example.plaidapp.plaid_app.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "plaid_items")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaidItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private String id; // Auto-generated UUID primary key

    @Column(name = "link_token", unique = true, nullable = false)
    private String linkToken;

    @Column(name = "access_token", unique = true)
    private String accessToken;

    @Column(name = "item_id", unique = true)
    private String itemId;

    @Column(name = "rep_id", nullable = false)
    private String repId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}