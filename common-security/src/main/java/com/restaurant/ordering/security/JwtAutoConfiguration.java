package com.restaurant.ordering.security;

import javax.crypto.SecretKey;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Wires the shared symmetric-key JWT plumbing into every service that puts
 * {@code common-security} on its classpath.
 *
 * <p>Registered as an auto-configuration rather than relying on component scanning,
 * because the services' {@code @SpringBootApplication} classes live in sibling packages
 * ({@code com.restaurant.ordering.menu}, {@code ...order}, …) and would not otherwise scan
 * {@code com.restaurant.ordering.security}.
 *
 * <p>A symmetric HS256 key shared by all services is the right trade-off for a
 * single-operator venue: no key distribution, no JWKS endpoint, and any service can
 * verify any token. It does mean every service can also <em>mint</em> tokens, which a
 * multi-tenant deployment would not accept — that would want asymmetric keys with only
 * the issuer holding the private half.
 */
@AutoConfiguration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SecretKey rtoJwtSecretKey(JwtProperties properties) {
        return JwtSupport.secretKey(properties);
    }

    /**
     * Blocking decoder used by the six servlet services. The gateway builds its own
     * {@code ReactiveJwtDecoder} from the same {@link SecretKey} bean.
     */
    @Bean
    @ConditionalOnMissingBean(JwtDecoder.class)
    public JwtDecoder jwtDecoder(SecretKey secretKey, JwtProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer()));
        return decoder;
    }

    @Bean
    @ConditionalOnMissingBean(JwtEncoder.class)
    public JwtEncoder jwtEncoder(SecretKey secretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(secretKey));
    }

    @Bean
    @ConditionalOnMissingBean
    public TokenIssuer tokenIssuer(JwtEncoder encoder, JwtProperties properties) {
        return new TokenIssuer(encoder, properties);
    }
}
