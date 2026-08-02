package com.pramika.rto.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Shared JWT settings. Every service binds the same {@code rto.jwt.*} block so tokens
 * issued by table-service and the gateway verify everywhere.
 *
 * @param secret        HS256 signing key. Must be at least 32 bytes — HMAC-SHA256 rejects
 *                      anything shorter, and the failure surfaces as an opaque key-length
 *                      error at first use rather than at startup.
 * @param issuer        {@code iss} claim, verified on decode.
 * @param customerTtl   lifetime of a table session token; long enough to cover a meal.
 * @param staffTtl      lifetime of a staff login token.
 */
@ConfigurationProperties(prefix = "rto.jwt")
public record JwtProperties(
        String secret,
        String issuer,
        Duration customerTtl,
        Duration staffTtl) {

    public JwtProperties {
        issuer = issuer == null ? "rto" : issuer;
        customerTtl = customerTtl == null ? Duration.ofHours(4) : customerTtl;
        staffTtl = staffTtl == null ? Duration.ofHours(12) : staffTtl;
    }
}
