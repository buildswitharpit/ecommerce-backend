package com.ecommerce.security;

import com.ecommerce.entity.Role;
import com.ecommerce.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Signs and validates the short-lived JWT access token (HS256, via jjwt). Refresh
 * tokens are a separate, opaque, database-backed concept handled by
 * {@link TokenHasher} + {@code RefreshTokenRepository} -- they are never JWTs
 * themselves, which is what allows them to be individually revoked (a signed JWT can't
 * be "un-signed" before its own expiry without a server-side blocklist).
 */
@Component
public class JwtService {

    private final SecretKey key;
    private final long accessTokenExpirationMs;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-expiration-ms:900000}") long accessTokenExpirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpirationMs = accessTokenExpirationMs;
    }

    public long getAccessTokenExpirationMs() {
        return accessTokenExpirationMs;
    }

    public String generateAccessToken(User user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessTokenExpirationMs);
        return Jwts.builder()
                .subject(user.getEmail())
                .claim("uid", user.getId())
                .claim("role", user.getRole().name())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    /**
     * Validates signature and expiration and returns the decoded principal, or
     * {@code null} if the token is missing, malformed, expired, or has a bad
     * signature. Deliberately does not throw: an invalid bearer token on a request
     * should fall through to "unauthenticated", not blow up the filter chain.
     */
    public UserPrincipal parseAndValidate(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Long userId = claims.get("uid", Integer.class) != null
                    ? claims.get("uid", Integer.class).longValue()
                    : claims.get("uid", Long.class);
            String email = claims.getSubject();
            Role role = Role.valueOf(claims.get("role", String.class));
            return new UserPrincipal(userId, email, role);
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }
}
