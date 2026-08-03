package com.restaurant.ordering.gateway.auth;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Seeded staff accounts.
 *
 * <p>Configuration rather than a database because staff accounts for a single venue are
 * a handful of rows that change when someone is hired, not per-request data. A real
 * deployment would move these to a user store; the shape of {@link StaffUser} is what that
 * store would return, so the login flow would not change.
 */
@ConfigurationProperties(prefix = "rto.staff")
public record StaffProperties(List<StaffUser> users) {

    public StaffProperties {
        users = users == null ? List.of() : List.copyOf(users);
    }

    /**
     * @param passwordHash BCrypt hash. Plaintext passwords never appear in configuration,
     *                     even in a demo — a seed file is the most common way real
     *                     credentials end up in version control.
     */
    public record StaffUser(String username, String role, String passwordHash) {
    }
}
