package com.restaurant.ordering.table.config;

import com.restaurant.ordering.security.JwtSupport;

import jakarta.servlet.DispatcherType;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // Stateless bearer-token API: there is no session cookie for CSRF to protect,
                // and no browser form posts to this service.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Spring Security also guards the internal forward to /error. Without
                        // this, any handler exception is re-dispatched, hits the default rule,
                        // and comes back as a 401 — which hides the actual 404 or 500 and sends
                        // you looking for an authentication bug that isn't there.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        // The QR scan is how a customer obtains their first token, so it
                        // cannot itself require one.
                        .requestMatchers(HttpMethod.POST, "/api/tables/sessions").permitAll()
                        .requestMatchers("/actuator/health/**").permitAll()
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
