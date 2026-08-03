package com.restaurant.ordering.notification.config;

import java.util.List;

import com.restaurant.ordering.notification.Destinations;
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
 * The customer-facing WebSocket.
 *
 * <p>Two distinct checks happen here, and conflating them is the usual mistake:
 *
 * <ul>
 *   <li><strong>CONNECT</strong> authenticates — is this a valid token?</li>
 *   <li><strong>SUBSCRIBE</strong> authorises — may <em>this</em> token read <em>that</em>
 *       destination?</li>
 * </ul>
 *
 * <p>Authenticating alone would let any diner with a valid table session subscribe to
 * {@code /topic/tables/4} and watch another table's orders and payments. Because the table
 * id is part of the destination string, the check is a comparison against the token's own
 * {@code tableId} claim.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebSocketConfig.class);

    private static final String TABLE_PREFIX = "/topic/tables/";

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
        registry.addEndpoint("/ws/customer")
                .setAllowedOriginPatterns(allowedOrigins.toArray(String[]::new));
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor =
                        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null || accessor.getCommand() == null) {
                    return message;
                }

                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    authenticate(accessor);
                } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                    authorise(accessor);
                }
                return message;
            }
        });
    }

    /** Verifies the token carried on the CONNECT frame and attaches it to the session. */
    private void authenticate(StompHeaderAccessor accessor) {
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
        accessor.setUser(new UsernamePasswordAuthenticationToken(
                jwt.getSubject(), jwt, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
        // Kept on the session so SUBSCRIBE can consult the claims without re-decoding.
        accessor.getSessionAttributes().put("jwt", jwt);
        log.debug("STOMP CONNECT accepted for {} ({})", jwt.getSubject(), role);
    }

    /**
     * Decides whether this session may read the requested destination.
     *
     * <p>Staff see everything. A customer is confined to their own table's stream; the menu
     * topic is open to any authenticated session because it carries no table-specific data.
     * Order destinations are left to the HTTP layer — the id alone is not a capability, and
     * order-service already refuses to serve another table's order.
     */
    private void authorise(StompHeaderAccessor accessor) {
        Object stored = accessor.getSessionAttributes() == null
                ? null
                : accessor.getSessionAttributes().get("jwt");
        if (!(stored instanceof Jwt jwt)) {
            throw new IllegalArgumentException("SUBSCRIBE before CONNECT");
        }

        String destination = accessor.getDestination();
        if (destination == null) {
            throw new IllegalArgumentException("SUBSCRIBE requires a destination");
        }

        String role = jwt.getClaimAsString(Claims.ROLE);
        if (!Roles.CUSTOMER.equals(role)) {
            return;
        }
        if (Destinations.MENU.equals(destination) || !destination.startsWith(TABLE_PREFIX)) {
            return;
        }

        Object claim = jwt.getClaim(Claims.TABLE_ID);
        Long ownTableId = claim instanceof Number number ? number.longValue() : null;
        String requested = destination.substring(TABLE_PREFIX.length());

        if (ownTableId == null || !requested.equals(String.valueOf(ownTableId))) {
            log.warn("Rejected SUBSCRIBE to {} from a session scoped to table {}", destination, ownTableId);
            throw new IllegalArgumentException("Not permitted to subscribe to " + destination);
        }
    }
}
