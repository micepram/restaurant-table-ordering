import type {
  Bill,
  BoardUpdate,
  LoginResponse,
  Menu,
  Order,
  OrderStatus,
  PlaceOrderRequest,
  SplitView,
  TableSession,
  TableView,
} from './types';

/** Everything goes through the gateway. One origin, one CORS policy, one place to change. */
export const API_BASE = import.meta.env?.VITE_API_BASE ?? 'http://localhost:8080';

/**
 * An error carrying the server's RFC 9457 problem detail.
 *
 * The backend puts user-facing text in `detail` ("Ribeye Steak requires at least 1 choice
 * from Cooked to"), so surfacing that rather than a generic message is the whole reason
 * these responses exist.
 */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly detail: string,
    readonly title?: string,
    readonly body?: unknown,
  ) {
    super(detail);
    this.name = 'ApiError';
  }
}

async function request<T>(path: string, token: string | null, init: RequestInit = {}): Promise<T> {
  const headers: Record<string, string> = {
    Accept: 'application/json',
    ...((init.headers as Record<string, string>) ?? {}),
  };
  if (init.body) headers['Content-Type'] = 'application/json';
  if (token) headers.Authorization = `Bearer ${token}`;

  const response = await fetch(`${API_BASE}${path}`, { ...init, headers });

  if (response.status === 204) return undefined as T;

  const text = await response.text();
  const body = text ? safeParse(text) : undefined;

  if (!response.ok) {
    const problem = body as { detail?: string; title?: string; message?: string } | undefined;
    throw new ApiError(
      response.status,
      problem?.detail ?? problem?.message ?? `Request failed (${response.status})`,
      problem?.title,
      body,
    );
  }
  return body as T;
}

function safeParse(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

/** Typed calls, grouped by the service that owns each. */
export const api = {
  /** Staff login. Customers never use this — their credential comes from the QR code. */
  login: (username: string, password: string) =>
    request<LoginResponse>('/api/auth/login', null, {
      method: 'POST',
      body: JSON.stringify({ username, password }),
    }),

  /** Exchanges a scanned QR code for a table-scoped session token. Unauthenticated. */
  scan: (qrCode: string) =>
    request<TableSession>('/api/tables/sessions', null, {
      method: 'POST',
      body: JSON.stringify({ qrCode }),
    }),

  menu: (token: string) => request<Menu>('/api/menu', token),

  placeOrder: (token: string, order: PlaceOrderRequest) =>
    request<Order>('/api/orders', token, { method: 'POST', body: JSON.stringify(order) }),

  order: (token: string, orderId: number) => request<Order>(`/api/orders/${orderId}`, token),

  ordersForTable: (token: string, tableId: number) =>
    request<Order[]>(`/api/orders/table/${tableId}`, token),

  openOrders: (token: string) => request<Order[]>('/api/orders/open', token),

  tables: (token: string) => request<TableView[]>('/api/tables', token),

  flagTable: (token: string, tableId: number, flagged: boolean, note?: string) =>
    request<TableView>(`/api/tables/${tableId}/attention`, token, {
      method: 'POST',
      body: JSON.stringify({ flagged, note }),
    }),

  board: (token: string) => request<BoardUpdate>('/api/kitchen/board', token),

  /**
   * Both kitchen actions return 202, not 200: they publish an intent to Kafka. The board
   * updates when the resulting change arrives over the WebSocket, not when this resolves.
   */
  advanceTicket: (token: string, orderId: number, status: OrderStatus) =>
    request<void>(`/api/kitchen/tickets/${orderId}/advance`, token, {
      method: 'POST',
      body: JSON.stringify({ status }),
    }),

  setAvailability: (token: string, menuItemId: number, available: boolean, reason?: string) =>
    request<void>(`/api/kitchen/items/${menuItemId}/availability`, token, {
      method: 'POST',
      body: JSON.stringify({ available, reason }),
    }),

  openBill: (token: string, orderId: number) =>
    request<Bill>(`/api/payments/bills/${orderId}`, token, { method: 'POST' }),

  bill: (token: string, orderId: number) => request<Bill>(`/api/payments/bills/${orderId}`, token),

  setTip: (token: string, orderId: number, percent: number) =>
    request<Bill>(`/api/payments/bills/${orderId}/tip`, token, {
      method: 'POST',
      body: JSON.stringify({ percent }),
    }),

  split: (token: string, orderId: number, ways: number) =>
    request<SplitView>(`/api/payments/bills/${orderId}/split?ways=${ways}`, token),

  pay: (token: string, orderId: number, amountCents: number, tipCents: number, cardNumber: string) =>
    request<Bill>(`/api/payments/bills/${orderId}/pay`, token, {
      method: 'POST',
      body: JSON.stringify({ amountCents, tipCents, cardNumber }),
    }),
};
