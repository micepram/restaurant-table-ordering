package com.restaurant.ordering.notification.config;

import com.restaurant.ordering.security.JwtSupport;

import jakarta.servlet.DispatcherType;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    // No CORS configuration here on purpose — see the note in kitchen-service's
    // SecurityConfig. The gateway owns the CORS headers; setting them again here produced a
    // duplicated Access-Control-Allow-Origin that browsers refuse.
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/actuator/health/**").permitAll()
                        // The handshake carries no Authorization header; the real check is
                        // on the STOMP CONNECT and SUBSCRIBE frames in WebSocketConfig.
                        .requestMatchers("/ws/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt
                        .jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(JwtSupport.authoritiesConverter());
        return converter;
    }
}
