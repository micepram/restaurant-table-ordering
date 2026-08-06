package com.restaurant.ordering.kitchen.config;

import com.restaurant.ordering.security.JwtSupport;

import jakarta.servlet.DispatcherType;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    // No CORS configuration here on purpose. The browser only ever reaches this service
    // through the gateway, which sets the CORS headers itself; adding them again here made
    // every proxied response carry two Access-Control-Allow-Origin values, which browsers
    // reject outright ("contains multiple values, but only one is allowed"). The remaining
    // services do not configure CORS either. `rto.cors.allowed-origins` is still read by
    // WebSocketConfig, where it gates the STOMP handshake rather than the HTTP response.
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/actuator/health/**").permitAll()
                        // The WebSocket handshake carries no Authorization header — browser
                        // WebSocket APIs cannot set one. Authentication happens on the STOMP
                        // CONNECT frame instead, in WebSocketConfig's channel interceptor.
                        // Permitting the handshake here does not leave the socket open.
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
