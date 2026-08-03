import type { Role } from './types';

/**
 * Token storage.
 *
 * `sessionStorage`, not `localStorage`: a table session should not outlive the browser tab.
 * A diner who closes the page and hands the phone to someone else — or a shared tablet at
 * the pass — should not carry the previous session's credential forward.
 *
 * Storing a bearer token in the browser at all is XSS-exposed. It is the right trade-off
 * here (the alternative, an httpOnly cookie, brings CSRF and cross-origin complications for
 * three separate apps), but it is a trade-off, not a non-issue.
 */

const CUSTOMER_KEY = 'rto.customer.session';
const STAFF_KEY = 'rto.staff.session';

export interface CustomerSession {
  token: string;
  tableId: number;
  tableCode: string;
  sessionId: string;
  expiresAt: string;
}

export interface StaffSession {
  token: string;
  username: string;
  role: Role;
}

function read<T>(key: string): T | null {
  try {
    const raw = sessionStorage.getItem(key);
    return raw ? (JSON.parse(raw) as T) : null;
  } catch {
    return null;
  }
}

function write(key: string, value: unknown): void {
  sessionStorage.setItem(key, JSON.stringify(value));
}

export const customerSession = {
  get: () => {
    const session = read<CustomerSession>(CUSTOMER_KEY);
    // Treat an expired token as absent so the app re-scans rather than firing doomed
    // requests and rendering a wall of 401s.
    if (session && new Date(session.expiresAt).getTime() < Date.now()) {
      sessionStorage.removeItem(CUSTOMER_KEY);
      return null;
    }
    return session;
  },
  set: (session: CustomerSession) => write(CUSTOMER_KEY, session),
  clear: () => sessionStorage.removeItem(CUSTOMER_KEY),
};

export const staffSession = {
  get: () => read<StaffSession>(STAFF_KEY),
  set: (session: StaffSession) => write(STAFF_KEY, session),
  clear: () => sessionStorage.removeItem(STAFF_KEY),
};
