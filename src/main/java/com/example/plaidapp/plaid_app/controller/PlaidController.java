package com.example.plaidapp.plaid_app.controller;


import com.example.plaidapp.plaid_app.service.PlaidService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PlaidController {

    private final PlaidService plaidService;

    public PlaidController(PlaidService plaidService) {
        this.plaidService = plaidService;
    }

    @GetMapping("/home")
    public String hello() {
        var client = plaidService.getPlaidApi();
        return "Testing";
    }

}

