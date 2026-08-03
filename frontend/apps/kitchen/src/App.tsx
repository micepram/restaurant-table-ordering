import {
  api,
  formatWait,
  secondsSince,
  staffSession,
  urgencyFor,
  useStomp,
  type BoardUpdate,
  type MenuItem,
  type OrderStatus,
  type Ticket,
} from '@rto/shared';
import { useCallback, useEffect, useMemo, useState } from 'react';

import { Login } from './Login';
import { EightySixPanel } from './EightySixPanel';
import { useTicker } from './useTicker';

/** What the button says for the status a ticket is moving to. */
const ACTION_LABEL: Partial<Record<OrderStatus, string>> = {
  ACKNOWLEDGED: 'Accept',
  PREPARING: 'Start cooking',
  READY: 'Ready to serve',
  CANCELLED: 'Cancel',
};

export function App() {
  const [session, setSession] = useState(() => staffSession.get());
  const [board, setBoard] = useState<BoardUpdate | null>(null);
  const [items, setItems] = useState<MenuItem[]>([]);
  const [showEightySix, setShowEightySix] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const token = session?.token ?? null;
  const now = useTicker();

  const loadBoard = useCallback(() => {
    if (!token) return;
    api.board(token).then(setBoard).catch((err: Error) => setError(err.message));
  }, [token]);

  const loadItems = useCallback(() => {
    if (!token) return;
    api
      .menu(token)
      .then((menu) => setItems(menu.categories.flatMap((category) => category.items)))
      .catch((err: Error) => setError(err.message));
  }, [token]);

  useEffect(loadBoard, [loadBoard]);
  useEffect(loadItems, [loadItems]);

  const destinations = useMemo(() => ['/topic/kitchen'], []);

  // Every push carries the whole board, so a display that reconnects mid-service is
  // immediately correct rather than replaying deltas it may have gaps in.
  const status = useStomp({
    path: '/ws/kitchen',
    token,
    destinations,
    enabled: Boolean(token),
    onMessage: (_destination, body) => setBoard(body as BoardUpdate),
  });

  const advance = async (ticket: Ticket, next: OrderStatus) => {
    if (!token) return;
    setError(null);
    try {
      // 202: this publishes an intent. order-service validates it and the board updates
      // when the resulting status change arrives over the socket — not when this resolves.
      await api.advanceTicket(token, ticket.orderId, next);
    } catch (err) {
      setError((err as Error).message);
    }
  };

  const signOut = () => {
    staffSession.clear();
    setSession(null);
  };

  if (!session) {
    return (
      <Login
        title="Kitchen display"
        allowedRoles={['KITCHEN', 'MANAGER']}
        onSignedIn={() => setSession(staffSession.get())}
      />
    );
  }

  const warnAfter = board?.warnAfterSeconds ?? 300;
  const lateAfter = board?.lateAfterSeconds ?? 600;
  const tickets = board?.tickets ?? [];

  return (
    <div className="board">
      <header className="board-header">
        <h1>Kitchen</h1>
        <div className="board-meta">
          <span className="count">{tickets.length} on the pass</span>
          <span className={`ws ws-${status}`}>
            {status === 'connected' ? 'Live' : status === 'connecting' ? 'Connecting' : 'Offline'}
          </span>
          <button className="link" onClick={() => setShowEightySix(true)}>86 an item</button>
          <button className="link" onClick={signOut}>Sign out ({session.username})</button>
        </div>
      </header>

      {error && (
        <div className="error" role="alert">
          {error}
          <button className="link" onClick={() => setError(null)}>dismiss</button>
        </div>
      )}

      {tickets.length === 0 ? (
        <p className="empty">Nothing waiting.</p>
      ) : (
        <ul className="tickets">
          {tickets.map((ticket) => {
            // Recomputed locally each second from placedAt rather than trusting the
            // waitSeconds in the last frame, so the colour advances without a push and
            // keeps counting even if the socket drops.
            const waited = secondsSince(ticket.placedAt, now);
            const urgency = urgencyFor(waited, warnAfter, lateAfter);
            const next = ticket.nextStates.filter((state) => state !== 'PAID');

            return (
              <li key={ticket.orderId} className={`ticket ${urgency.toLowerCase()}`}>
                <header>
                  <span className="table">{ticket.tableCode}</span>
                  <span className="wait" title={`Placed ${new Date(ticket.placedAt).toLocaleTimeString()}`}>
                    {formatWait(waited)}
                  </span>
                </header>

                <span className="ticket-status">{ticket.status}</span>

                <ul className="lines">
                  {ticket.lines.map((line, index) => (
                    <li key={index}>
                      <span className="qty">{line.quantity}×</span>
                      <span className="what">
                        {line.name}
                        {line.modifiers && <em className="mods">{line.modifiers}</em>}
                        {line.note && <em className="note">“{line.note}”</em>}
                      </span>
                    </li>
                  ))}
                </ul>

                <footer>
                  {next.map((state) => (
                    <button
                      key={state}
                      className={state === 'CANCELLED' ? 'ghost' : 'primary'}
                      onClick={() => advance(ticket, state)}
                    >
                      {ACTION_LABEL[state] ?? state}
                    </button>
                  ))}
                </footer>
              </li>
            );
          })}
        </ul>
      )}

      {showEightySix && (
        <EightySixPanel
          items={items}
          token={session.token}
          onClose={() => setShowEightySix(false)}
          onChanged={loadItems}
          onError={setError}
        />
      )}
    </div>
  );
}
