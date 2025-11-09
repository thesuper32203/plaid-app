package com.example.plaidapp.plaid_app.service;


import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.ListBucketsRequest;
import software.amazon.awssdk.services.s3.model.ListBucketsResponse;

@Service
public class S3TestService {

    private final S3Client s3Client;

    public S3TestService(S3Client s3Client) {
        System.out.println("✅ S3TestService bean created");
        this.s3Client = s3Client;
    }

    @PostConstruct
    public void testConnection(){
        try{

            ListBucketsResponse  response = s3Client.listBuckets();
            response.buckets().forEach(bucket ->
                    System.out.println("🪣 Bucket: " + bucket.name())
            );
        }catch (Exception e) {
            System.err.println("❌ Failed to connect to AWS S3: " + e.getMessage());
            e.printStackTrace();
        }
    }

}
