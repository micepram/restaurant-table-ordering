package com.restaurant.ordering.kitchen.config;

import java.util.List;
import java.util.Set;

import com.restaurant.ordering.security.Claims;
import com.restaurant.ordering.security.Roles;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP over WebSocket for the kitchen display.
 *
 * <p>Uses the simple in-memory broker. That is the right fit precisely because each
 * instance only ever pushes to its own sessions — the Kafka consumer group is what fans
 * messages out across instances, so no external broker relay is needed.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebSocketConfig.class);

    /** Only back-of-house may watch the kitchen board. */
    private static final Set<String> ALLOWED_ROLES = Set.of(Roles.KITCHEN, Roles.STAFF, Roles.MANAGER);

    private final JwtDecoder jwtDecoder;
    private final List<String> allowedOrigins;

    public WebSocketConfig(JwtDecoder jwtDecoder,
                           @Value("${rto.cors.allowed-origins}") List<String> allowedOrigins) {
        this.jwtDecoder = jwtDecoder;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Native WebSocket, no SockJS: the display is a modern browser app and @stomp/stompjs
        // speaks WebSocket directly, so the SockJS fallback would only add a client dependency.
        registry.addEndpoint("/ws/kitchen")
                .setAllowedOriginPatterns(allowedOrigins.toArray(String[]::new));
    }

    /**
     * Authenticates the STOMP CONNECT frame.
     *
     * <p>The HTTP handshake cannot carry an Authorization header from browser WebSocket
     * APIs, so the token travels in the CONNECT frame instead and is verified here. Without
     * this, the endpoint would be open to anyone who can reach the port — the HTTP security
     * chain only guards the handshake, not the messages that follow it.
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor =
                        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
                    return message;
                }

                String header = accessor.getFirstNativeHeader("Authorization");
                if (header == null || !header.startsWith("Bearer ")) {
                    throw new IllegalArgumentException("STOMP CONNECT requires a Bearer token");
                }

                Jwt jwt;
                try {
                    jwt = jwtDecoder.decode(header.substring("Bearer ".length()));
                } catch (JwtException ex) {
                    log.warn("Rejected STOMP CONNECT with an invalid token");
                    throw new IllegalArgumentException("Invalid token", ex);
                }

                String role = jwt.getClaimAsString(Claims.ROLE);
                if (role == null || !ALLOWED_ROLES.contains(role)) {
                    log.warn("Rejected STOMP CONNECT for role {}", role);
                    throw new IllegalArgumentException("Role " + role + " may not watch the kitchen board");
                }

                accessor.setUser(new UsernamePasswordAuthenticationToken(
                        jwt.getSubject(), null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
                log.debug("STOMP CONNECT accepted for {} ({})", jwt.getSubject(), role);
                return message;
            }
        });
    }
}
