package com.example.plaidapp.plaid_app.util;

public class StatementFile {

    private final String key;
    private final byte[] data;

    public StatementFile(String key, byte[] data) {
        this.key = key;
        this.data = data;
    }

    public String getKey() { return key; }
    public byte[] getData() { return data; }

}
