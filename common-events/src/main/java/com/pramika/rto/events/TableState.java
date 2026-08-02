package com.pramika.rto.events;

/** Lifecycle of a physical table, independent of any single order. */
public enum TableState {

    /** No active session; QR scan will open one. */
    FREE,

    /** A customer session is open but nothing has been ordered yet. */
    SEATED,

    /** At least one order is open on this table. */
    ORDERING,

    /** All orders delivered, bill not yet settled. */
    AWAITING_PAYMENT,

    /** Settled; table will return to FREE once cleared. */
    SETTLED
}
