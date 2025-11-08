package com.example.plaidapp.plaid_app.controller;


import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PlaidController {

    @GetMapping("/home")
    public String hello() {return "Testing";}
}

