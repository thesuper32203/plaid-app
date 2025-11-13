package com.example.plaidapp.plaid_app.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "rep_emails")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepEmail {

    @Id
    @Column(name = "rep_id", nullable = false)
    private String repId;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "name")
    private String name;
}
