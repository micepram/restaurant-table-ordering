package com.pramika.rto.security;

/**
 * The four principals in the system.
 *
 * <p>Constants are the bare role name; Spring Security compares against the {@code ROLE_}
 * prefixed authority, which {@link JwtSupport#authoritiesConverter()} adds. Use
 * {@link #CUSTOMER} etc. with {@code hasRole(...)}, never with {@code hasAuthority(...)}.
 */
public final class Roles {

    /** Anonymous diner holding a table session token. Scoped to one table. */
    public static final String CUSTOMER = "CUSTOMER";

    /** Front of house: sees all tables, order and payment status. */
    public static final String STAFF = "STAFF";

    /** Back of house: kitchen display, advances tickets, toggles availability. */
    public static final String KITCHEN = "KITCHEN";

    /** Everything staff can do, plus menu edits. */
    public static final String MANAGER = "MANAGER";

    private Roles() {
    }
}
