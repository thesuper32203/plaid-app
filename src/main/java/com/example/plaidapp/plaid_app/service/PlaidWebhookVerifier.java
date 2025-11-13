package com.example.plaidapp.plaid_app.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.plaid.client.model.WebhookVerificationKeyGetRequest;
import com.plaid.client.model.WebhookVerificationKeyGetResponse;
import com.plaid.client.request.PlaidApi;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class PlaidWebhookVerifier {

    private static final Logger LOGGER = Logger.getLogger(PlaidWebhookVerifier.class.getName());
    private final PlaidApi plaidApi;
    private final Cache<String, JWK> keyCache = Caffeine.newBuilder()
            .maximumSize(10)
            .expireAfterWrite(Duration.ofHours(12))
            .build();

    public PlaidWebhookVerifier(PlaidService plaidService) {
        this.plaidApi = plaidService.getPlaidApi();
    }

    public boolean verify(String jwtFromHeader, byte[] rawBodyBytes) {
        try {
            LOGGER.log(Level.INFO, "Verifying plaid webhook");
            LOGGER.log(Level.INFO, "Body length: " + rawBodyBytes.length + " bytes");

            // 1) Parse JWT header (no verification yet)
            SignedJWT signed = SignedJWT.parse(jwtFromHeader);
            JWSHeader header = signed.getHeader();
            LOGGER.log(Level.INFO, "Signed and got header: " + header);

            // 2) Verify algorithm
            if (!"ES256".equals(header.getAlgorithm().getName())) {
                LOGGER.log(Level.SEVERE, "Invalid JWS algorithm: " + header.getAlgorithm().getName());
                return false;
            }

            String kid = header.getKeyID();
            LOGGER.log(Level.INFO, "Key ID: " + kid);

            // 3) Get JWK (cache by kid)
            JWK jwk = keyCache.get(kid, this::fetchJwk);
            if (jwk == null) {
                LOGGER.log(Level.SEVERE, "Failed to fetch JWK for kid: " + kid);
                return false;
            }

            if (!(jwk instanceof ECKey ecKey)) {
                LOGGER.log(Level.SEVERE, "JWK is not an ECKey");
                return false;
            }
            LOGGER.log(Level.INFO, "Successfully retrieved JWK");

            // 4) Verify signature
            JWSVerifier verifier = new ECDSAVerifier(ecKey);
            if (!signed.verify(verifier)) {
                LOGGER.log(Level.SEVERE, "JWT signature verification failed");
                return false;
            }
            LOGGER.log(Level.INFO, "JWT signature verified");

            // 5) Verify iat (<= 5 minutes old)
            JWTClaimsSet claims = signed.getJWTClaimsSet();
            Date issuedAt = claims.getIssueTime();
            if (issuedAt == null) {
                LOGGER.log(Level.SEVERE, "Missing iat (issued at) claim");
                return false;
            }

            Instant now = Instant.now();
            Instant issuedInstant = issuedAt.toInstant();
            Instant expiryTime = issuedInstant.plus(Duration.ofMinutes(5));

            LOGGER.log(Level.INFO, String.format("Time check - Now: %s, Issued: %s, Expires: %s",
                    now, issuedInstant, expiryTime));

            if (now.isAfter(expiryTime)) {
                LOGGER.log(Level.SEVERE, "JWT is too old (> 5 minutes)");
                return false;
            }
            LOGGER.log(Level.INFO, "JWT timestamp is valid");

            // 6) Compare body hash
            String claimedHash = claims.getStringClaim("request_body_sha256");
            if (claimedHash == null) {
                LOGGER.log(Level.SEVERE, "Missing request_body_sha256 claim");
                return false;
            }

            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = sha256.digest(rawBodyBytes);

            // Convert to hex string (Plaid uses hex, not base64url)
            StringBuilder actualHash = new StringBuilder();
            for (byte b : hashBytes) {
                actualHash.append(String.format("%02x", b));
            }
            String actualHashStr = actualHash.toString();

            LOGGER.log(Level.INFO, "Claimed hash: " + claimedHash);
            LOGGER.log(Level.INFO, "Actual hash:  " + actualHashStr);

            // Constant-time compare
            boolean hashesMatch = MessageDigest.isEqual(
                    actualHashStr.getBytes(StandardCharsets.US_ASCII),
                    claimedHash.getBytes(StandardCharsets.US_ASCII)
            );

            if (!hashesMatch) {
                LOGGER.log(Level.SEVERE, "Body hash mismatch!");
                // Log a snippet of the body for debugging (first 100 chars)
                String bodyPreview = new String(rawBodyBytes, StandardCharsets.UTF_8);
                if (bodyPreview.length() > 100) {
                    bodyPreview = bodyPreview.substring(0, 100) + "...";
                }
                LOGGER.log(Level.INFO, "Body preview: " + bodyPreview);
                return false;
            }

            LOGGER.log(Level.INFO, "Body hash verified successfully");
            LOGGER.log(Level.INFO, "Done verifying plaid webhook - SUCCESS");
            return true;

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Exception during webhook verification", e);
            return false;
        }
    }

    private JWK fetchJwk(String kid) {
        try {
            LOGGER.log(Level.INFO, "Fetching JWK from Plaid for kid: " + kid);
            WebhookVerificationKeyGetRequest req = new WebhookVerificationKeyGetRequest().keyId(kid);
            retrofit2.Response<WebhookVerificationKeyGetResponse> resp =
                    plaidApi.webhookVerificationKeyGet(req).execute();

            if (!resp.isSuccessful() || resp.body() == null) {
                String errorBody = resp.errorBody() != null ? resp.errorBody().string() : "null";
                LOGGER.log(Level.SEVERE, "Failed to retrieve JWK. Status: " +
                        resp.code() + ", Error: " + errorBody);
                return null;
            }

            var key = resp.body().getKey();
            LOGGER.log(Level.INFO, "Successfully fetched JWK from Plaid");

            return new ECKey.Builder(Curve.P_256,
                    new Base64URL(key.getX()), new Base64URL(key.getY()))
                    .keyUse(KeyUse.SIGNATURE)
                    .keyID(key.getKid())
                    .algorithm(JWSAlgorithm.ES256)
                    .build();
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Exception fetching JWK", ex);
            return null;
        }
    }
}