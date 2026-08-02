package com.restaurant.ordering.security;

/** Custom JWT claim names shared by the issuer and every verifier. */
public final class Claims {

    /** Single role string, e.g. {@code CUSTOMER}. */
    public static final String ROLE = "role";

    /**
     * Table this token is scoped to. Present on customer tokens only.
     *
     * <p>Authorisation for customer WebSocket subscriptions is derived from this claim, so
     * a diner at table 3 cannot subscribe to table 4's order stream.
     */
    public static final String TABLE_ID = "tableId";

    /** Opaque id for one seating, so a re-scan after the table is cleared gets a new session. */
    public static final String SESSION_ID = "sessionId";

    /** Human-readable table code such as {@code T-01}, for display only. */
    public static final String TABLE_CODE = "tableCode";

    private Claims() {
    }
}
