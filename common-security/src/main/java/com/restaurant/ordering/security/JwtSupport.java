package com.restaurant.ordering.security;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Stack-neutral JWT helpers.
 *
 * <p>Nothing here touches servlet or reactive types on purpose: the gateway is a WebFlux
 * application and the six services are servlet applications, and both need to derive a
 * decoder from the same key. Each side wraps these pieces in its own configuration.
 */
public final class JwtSupport {

    private static final int MIN_SECRET_BYTES = 32;

    private JwtSupport() {
    }

    /**
     * Builds the HMAC signing key.
     *
     * @throws IllegalStateException if the configured secret is too short for HS256.
     *         Checked eagerly so a weak key fails at startup rather than on the first
     *         login attempt.
     */
    public static SecretKey secretKey(JwtProperties properties) {
        byte[] bytes = properties.secret() == null
                ? new byte[0]
                : properties.secret().getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "rto.jwt.secret must be at least %d bytes for HS256, got %d. Set the RTO_JWT_SECRET environment variable."
                            .formatted(MIN_SECRET_BYTES, bytes.length));
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    /**
     * Maps the single {@link Claims#ROLE} claim onto one {@code ROLE_}-prefixed authority.
     *
     * <p>A one-role-per-token model is enough here and keeps the customer token minimal.
     * Both the servlet and reactive resource-server configs wrap this same converter.
     */
    public static Converter<Jwt, Collection<GrantedAuthority>> authoritiesConverter() {
        return jwt -> {
            String role = jwt.getClaimAsString(Claims.ROLE);
            if (role == null || role.isBlank()) {
                return List.of();
            }
            return List.of(new SimpleGrantedAuthority("ROLE_" + role));
        };
    }
}
