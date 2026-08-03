/**
 * Types mirroring the backend DTOs.
 *
 * Hand-written rather than generated from OpenAPI. The contract is small and stable, and a
 * codegen step would have to run before every build for a handful of interfaces. The
 * trade-off is that these must be updated alongside backend DTO changes — the record types
 * in `common-events` and each service's `*Dtos` class are the source of truth.
 *
 * All money is integer cents, matching the backend. Nothing here converts to a float except
 * at the moment of display; see `formatCents`.
 */

export type OrderStatus =
  | 'PLACED'
  | 'ACKNOWLEDGED'
  | 'PREPARING'
  | 'READY'
  | 'PAID'
  | 'CANCELLED';

export type TableState = 'FREE' | 'SEATED' | 'ORDERING' | 'AWAITING_PAYMENT' | 'SETTLED';

export type Urgency = 'NORMAL' | 'WARNING' | 'LATE';

export type Role = 'CUSTOMER' | 'STAFF' | 'KITCHEN' | 'MANAGER';

/* ---------- menu ---------- */

export interface Modifier {
  id: number;
  name: string;
  priceDeltaCents: number;
  available: boolean;
}

export interface ModifierGroup {
  id: number;
  name: string;
  /** `minSelect >= 1` makes the group mandatory — the order is rejected without a choice. */
  minSelect: number;
  maxSelect: number;
  modifiers: Modifier[];
}

export interface MenuItem {
  id: number;
  name: string;
  description: string | null;
  priceCents: number;
  available: boolean;
  modifierGroups: ModifierGroup[];
}

export interface Category {
  id: number;
  name: string;
  sortOrder: number;
  items: MenuItem[];
}

export interface Menu {
  generatedAt: string;
  categories: Category[];
}

/* ---------- orders ---------- */

export interface OrderLine {
  id: number;
  menuItemId: number;
  name: string;
  quantity: number;
  unitPriceCents: number;
  modifiersTotalCents: number;
  lineTotalCents: number;
  note: string | null;
  modifiers: string[];
}

export interface Order {
  id: number;
  tableId: number;
  tableCode: string;
  status: OrderStatus;
  /** Which statuses this order may legally move to next, straight from the state machine. */
  nextStates: OrderStatus[];
  placedAt: string;
  updatedAt: string;
  subtotalCents: number;
  lines: OrderLine[];
}

/** What the customer sends. Deliberately carries no prices — the server sets those. */
export interface PlaceOrderRequest {
  lines: Array<{
    menuItemId: number;
    quantity: number;
    modifierIds: number[];
    note?: string;
  }>;
}

/* ---------- kitchen ---------- */

export interface TicketLine {
  name: string;
  quantity: number;
  modifiers: string | null;
  note: string | null;
}

export interface Ticket {
  orderId: number;
  tableId: number;
  tableCode: string;
  status: OrderStatus;
  nextStates: OrderStatus[];
  placedAt: string;
  /** Age when the server built this view; the board re-derives it locally each second. */
  waitSeconds: number;
  urgency: Urgency;
  subtotalCents: number;
  lines: TicketLine[];
}

export interface BoardUpdate {
  generatedAt: string;
  warnAfterSeconds: number;
  lateAfterSeconds: number;
  tickets: Ticket[];
}

/* ---------- tables ---------- */

export interface TableView {
  id: number;
  code: string;
  seats: number;
  state: TableState;
  attentionFlagged: boolean;
  attentionNote: string | null;
  updatedAt: string;
}

export interface TableSession {
  token: string;
  tableId: number;
  tableCode: string;
  sessionId: string;
  expiresAt: string;
}

/* ---------- payments ---------- */

export interface PaymentRecord {
  id: number;
  amountCents: number;
  tipCents: number;
  cardLast4: string | null;
  status: 'APPROVED' | 'DECLINED' | 'FAILED';
  failureReason: string | null;
  reference: string;
  createdAt: string;
}

export interface Bill {
  orderId: number;
  tableId: number;
  tableCode: string;
  subtotalCents: number;
  tipCents: number;
  totalCents: number;
  outstandingCents: number;
  settled: boolean;
  updatedAt: string;
  payments: PaymentRecord[];
}

export interface Share {
  position: number;
  amountCents: number;
  tipCents: number;
}

export interface SplitView {
  ways: number;
  subtotalCents: number;
  tipCents: number;
  totalCents: number;
  shares: Share[];
}

/* ---------- auth & notifications ---------- */

export interface LoginResponse {
  token: string;
  username: string;
  role: Role;
  expiresInSeconds: number;
}

export type NotificationType =
  | 'ORDER_PLACED'
  | 'ORDER_STATUS'
  | 'MENU_INVALIDATED'
  | 'PAYMENT'
  | 'TABLE';

/**
 * A push is a signal, not a payload to trust. Handlers refetch over HTTP, so a missed or
 * duplicated message costs a redundant fetch rather than leaving the UI wrong.
 */
export interface Notification<T = Record<string, unknown>> {
  type: NotificationType;
  at: string;
  data: T;
}
