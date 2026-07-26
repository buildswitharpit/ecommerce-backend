package com.ecommerce.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Helpers for opaque refresh tokens: {@link #newOpaqueToken()} generates the raw value
 * handed to the client, {@link #sha256} produces the value actually persisted in
 * {@code RefreshToken.tokenHash} -- the raw token is never stored, so a database leak
 * alone can't be used to forge a session, mirroring how {@code User.passwordHash}
 * never stores the plaintext password.
 */
public final class TokenHasher {

    private TokenHasher() {
    }

    public static String newOpaqueToken() {
        return UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
    }

    public static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
