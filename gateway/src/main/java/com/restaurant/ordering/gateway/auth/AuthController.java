package com.restaurant.ordering.gateway.auth;

import java.time.Duration;
import java.util.Optional;

import com.restaurant.ordering.gateway.auth.StaffProperties.StaffUser;
import com.restaurant.ordering.security.JwtProperties;
import com.restaurant.ordering.security.TokenIssuer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Staff login.
 *
 * <p>Lives on the gateway because it is the one endpoint that must exist before any token
 * does. Customers never come through here — a diner's credential comes from scanning the
 * QR code at their table, not from a password.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final StaffProperties staff;
    private final PasswordEncoder passwordEncoder;
    private final TokenIssuer tokenIssuer;
    private final JwtProperties jwtProperties;

    public AuthController(StaffProperties staff,
                          PasswordEncoder passwordEncoder,
                          TokenIssuer tokenIssuer,
                          JwtProperties jwtProperties) {
        this.staff = staff;
        this.passwordEncoder = passwordEncoder;
        this.tokenIssuer = tokenIssuer;
        this.jwtProperties = jwtProperties;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        Optional<StaffUser> found = staff.users().stream()
                .filter(user -> user.username().equalsIgnoreCase(request.username()))
                .findFirst();

        // The password is checked even when the username is unknown, against a dummy hash.
        // Returning early on an unknown user makes the response measurably faster and turns
        // login timing into a way to enumerate valid usernames.
        String hash = found.map(StaffUser::passwordHash).orElse(DUMMY_HASH);
        boolean matches = passwordEncoder.matches(request.password(), hash);

        if (found.isEmpty() || !matches) {
            log.warn("Failed login for '{}'", request.username());
            // One message for both cases, so a failed attempt never reveals which half
            // was wrong.
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }

        StaffUser user = found.get();
        log.info("Staff login: {} ({})", user.username(), user.role());
        return new LoginResponse(
                tokenIssuer.issueStaffToken(user.username(), user.role()),
                user.username(),
                user.role(),
                jwtProperties.staffTtl().getSeconds());
    }

    /** A valid BCrypt hash of a value nobody knows, used purely to equalise timing. */
    private static final String DUMMY_HASH =
            "$2a$10$AJE3cxrUOJxTTSPo3y9e3uQ1fGV.SJgKC9DVrjKSUfVqn4r6Q.PrW";

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record LoginResponse(String token, String username, String role, long expiresInSeconds) {

        public Duration expiresIn() {
            return Duration.ofSeconds(expiresInSeconds);
        }
    }
}
