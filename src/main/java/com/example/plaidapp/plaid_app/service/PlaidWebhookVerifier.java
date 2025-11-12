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
import java.util.Base64;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class PlaidWebhookVerifier {

    private final PlaidApi plaidApi;
    private final Cache<String, JWK> keyCache = Caffeine.newBuilder()
            .maximumSize(10)
            .expireAfterWrite(Duration.ofHours(12))
            .build();

    public PlaidWebhookVerifier(PlaidService plaidService) { // your PlaidService wrapper
        this.plaidApi = plaidService.getPlaidApi();
    }

    public boolean verify(String jwtFromHeader, byte[] rawBodyBytes) {
        try {
            // 1) Parse JWT header (no verification yet)
            Logger.getLogger(PlaidWebhookVerifier.class.getName()).log(Level.INFO, "Verifying plaid webhook");
            SignedJWT signed = SignedJWT.parse(jwtFromHeader);
            JWSHeader header = signed.getHeader();
            Logger.getLogger(PlaidWebhookVerifier.class.getName()).log(Level.INFO, "Signed and got header: " + header);

            if (!"ES256".equals(header.getAlgorithm().getName())) {
                Logger.getLogger(PlaidWebhookVerifier.class.getName()).log(Level.SEVERE, "Invalid JWS header");
                return false; // Plaid requires ES256
            }
            String kid = header.getKeyID();

            // 2) Get JWK (cache by kid)
            JWK jwk = keyCache.get(kid, this::fetchJwk);

            if (jwk == null || !(jwk instanceof ECKey ecKey)) {
                Logger.getLogger(PlaidWebhookVerifier.class.getName()).log(Level.SEVERE, "Invalid JWK");
                return false;
            }

            // 3) Verify signature and iat (<= 5 minutes old)
            JWSVerifier verifier = new ECDSAVerifier(ecKey);
            if (!signed.verify(verifier)) {
                Logger.getLogger(PlaidWebhookVerifier.class.getName()).log(Level.SEVERE, "Could not verify signature");
                return false;
            }

            JWTClaimsSet claims = signed.getJWTClaimsSet();
            Date issuedAt = claims.getIssueTime();
            if (issuedAt == null || Instant.now().isAfter(issuedAt.toInstant().plus(Duration.ofMinutes(5)))) {
                Logger.getLogger(PlaidWebhookVerifier.class.getName()).log(Level.SEVERE, "Too Old");
                return false; // too old
            }

            // 4) Compare body hash
            String claimedHash = claims.getStringClaim("request_body_sha256");
            if (claimedHash == null) {
                Logger.getLogger(PlaidWebhookVerifier.class.getName()).log(Level.SEVERE, "Claimed hash is null");
                return false;
            }

            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            String actualHash = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(sha256.digest(rawBodyBytes)); // base64url

            // Constant-time compare
            return MessageDigest.isEqual(actualHash.getBytes(StandardCharsets.US_ASCII),
                    claimedHash.getBytes(StandardCharsets.US_ASCII));

        } catch (Exception e) {
            return false;
        }
    }

    private JWK fetchJwk(String kid) {
        try {
            WebhookVerificationKeyGetRequest req = new WebhookVerificationKeyGetRequest().keyId(kid);
            retrofit2.Response<WebhookVerificationKeyGetResponse> resp =
                    plaidApi.webhookVerificationKeyGet(req).execute();

            if (!resp.isSuccessful() || resp.body() == null) {
                Logger.getLogger(PlaidWebhookVerifier.class.getName()).log(Level.SEVERE, "Failed to retrieve JWK");
                return null;
            }
            var key = resp.body().getKey(); // Plaid JWK fields
            // Build Nimbus JWK
            return new ECKey.Builder(Curve.P_256,
                    new Base64URL(key.getX()), new Base64URL(key.getY()))
                    .keyUse(KeyUse.SIGNATURE)
                    .keyID(key.getKid())
                    .algorithm(JWSAlgorithm.ES256)
                    .build();
        } catch (Exception ex) {
            return null;
        }
    }
}
