package com.example.plaidapp.plaid_app.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlaidItem {

    @Id
    private String linkToken;

    private String itemId;
    private String accessToken;
    private String userId;
    private String repId;
}