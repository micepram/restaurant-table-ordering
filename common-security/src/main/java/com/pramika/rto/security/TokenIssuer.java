package com.pramika.rto.security;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

/**
 * Mints the two kinds of token in the system.
 *
 * <p>Only table-service (customer sessions) and the gateway's staff login use this; the
 * other services verify but never issue.
 */
public class TokenIssuer {

    private final JwtEncoder encoder;
    private final JwtProperties properties;

    public TokenIssuer(JwtEncoder encoder, JwtProperties properties) {
        this.encoder = encoder;
        this.properties = properties;
    }

    /**
     * Token for a diner who has just scanned a QR code. Scoped to one table: the
     * {@link Claims#TABLE_ID} claim is what stops a customer reading another table's
     * orders or WebSocket stream.
     */
    public String issueCustomerToken(Long tableId, String tableCode, UUID sessionId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(Claims.ROLE, Roles.CUSTOMER);
        claims.put(Claims.TABLE_ID, tableId);
        claims.put(Claims.TABLE_CODE, tableCode);
        claims.put(Claims.SESSION_ID, sessionId.toString());
        return issue("table:" + tableCode, claims, properties.customerTtl().getSeconds());
    }

    /** Token for a logged-in staff member. Not scoped to any table. */
    public String issueStaffToken(String username, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(Claims.ROLE, role);
        return issue(username, claims, properties.staffTtl().getSeconds());
    }

    private String issue(String subject, Map<String, Object> claims, long ttlSeconds) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder builder = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(ttlSeconds))
                .subject(subject)
                .id(UUID.randomUUID().toString());
        claims.forEach(builder::claim);

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, builder.build())).getTokenValue();
    }
}
