import {
  api,
  formatCents,
  formatWait,
  secondsSince,
  staffSession,
  useStomp,
  type Bill,
  type Order,
  type TableView,
} from '@rto/shared';
import { useCallback, useEffect, useMemo, useState } from 'react';

import { Login } from './Login';
import { useTicker } from './useTicker';

/** Order statuses the front of house cares about, in the sequence they happen. */
const OPEN_STATUSES = new Set(['PLACED', 'ACKNOWLEDGED', 'PREPARING', 'READY']);

export function App() {
  const [session, setSession] = useState(() => staffSession.get());
  const [tables, setTables] = useState<TableView[]>([]);
  const [orders, setOrders] = useState<Order[]>([]);
  const [bills, setBills] = useState<Record<number, Bill>>({});
  const [error, setError] = useState<string | null>(null);
  const [selected, setSelected] = useState<number | null>(null);

  const token = session?.token ?? null;
  const now = useTicker(5000);

  const load = useCallback(() => {
    if (!token) return;
    Promise.all([api.tables(token), api.openOrders(token)])
      .then(([nextTables, nextOrders]) => {
        setTables(nextTables);
        setOrders(nextOrders);
      })
      .catch((err: Error) => setError(err.message));
  }, [token]);

  useEffect(load, [load]);

  /**
   * Staff watch every table, so the dashboard subscribes to one destination per table.
   * A customer token would be refused on any table but its own; a STAFF token is not
   * table-scoped, so the server permits all of them.
   */
  const destinations = useMemo(
    () => (tables.length > 0 ? tables.map((table) => `/topic/tables/${table.id}`) : []),
    [tables],
  );

  const status = useStomp({
    path: '/ws/customer',
    token,
    destinations,
    enabled: Boolean(token) && destinations.length > 0,
    onMessage: () => load(),
  });

  const flag = async (table: TableView) => {
    if (!token) return;
    try {
      const updated = await api.flagTable(
        token,
        table.id,
        !table.attentionFlagged,
        table.attentionFlagged ? undefined : 'Flagged from the dashboard',
      );
      setTables((current) => current.map((row) => (row.id === updated.id ? updated : row)));
    } catch (err) {
      setError((err as Error).message);
    }
  };

  const openBill = async (orderId: number) => {
    if (!token) return;
    try {
      const bill = await api.openBill(token, orderId);
      setBills((current) => ({ ...current, [orderId]: bill }));
    } catch (err) {
      setError((err as Error).message);
    }
  };

  if (!session) {
    return (
      <Login
        title="Staff dashboard"
        allowedRoles={['STAFF', 'MANAGER']}
        onSignedIn={() => setSession(staffSession.get())}
      />
    );
  }

  const ordersByTable = orders.reduce<Record<number, Order[]>>((map, order) => {
    (map[order.tableId] ??= []).push(order);
    return map;
  }, {});

  const flagged = tables.filter((table) => table.attentionFlagged);

  return (
    <div className="dash">
      <header className="dash-header">
        <h1>Front of house</h1>
        <div className="dash-meta">
          <span className={`ws ws-${status}`}>
            {status === 'connected' ? 'Live' : status === 'connecting' ? 'Connecting' : 'Offline'}
          </span>
          <button className="link" onClick={load}>Refresh</button>
          <button
            className="link"
            onClick={() => {
              staffSession.clear();
              setSession(null);
            }}
          >
            Sign out ({session.username})
          </button>
        </div>
      </header>

      {error && (
        <div className="error" role="alert">
          {error}
          <button className="link" onClick={() => setError(null)}>dismiss</button>
        </div>
      )}

      {flagged.length > 0 && (
        <div className="attention" role="status">
          Needs attention: {flagged.map((table) => table.code).join(', ')}
        </div>
      )}

      <ul className="tables">
        {tables.map((table) => {
          const tableOrders = ordersByTable[table.id] ?? [];
          const open = tableOrders.filter((order) => OPEN_STATUSES.has(order.status));
          const ready = open.filter((order) => order.status === 'READY');
          const oldest = open.reduce<number>(
            (max, order) => Math.max(max, secondsSince(order.placedAt, now)),
            0,
          );

          return (
            <li
              key={table.id}
              className={[
                'table-card',
                table.attentionFlagged ? 'flagged' : '',
                ready.length > 0 ? 'ready' : '',
                selected === table.id ? 'selected' : '',
              ].join(' ').trim()}
            >
              <header onClick={() => setSelected(selected === table.id ? null : table.id)}>
                <span className="code">{table.code}</span>
                <span className={`state state-${table.state.toLowerCase()}`}>{table.state}</span>
              </header>

              <dl className="table-stats">
                <div><dt>Seats</dt><dd>{table.seats}</dd></div>
                <div><dt>Open orders</dt><dd>{open.length}</dd></div>
                <div><dt>Ready</dt><dd>{ready.length}</dd></div>
                {open.length > 0 && (
                  <div><dt>Oldest</dt><dd>{formatWait(oldest)}</dd></div>
                )}
              </dl>

              {table.attentionFlagged && table.attentionNote && (
                <p className="note">“{table.attentionNote}”</p>
              )}

              {selected === table.id && (
                <div className="table-detail">
                  {tableOrders.length === 0 && <p className="muted">No open orders.</p>}
                  {tableOrders.map((order) => {
                    const bill = bills[order.id];
                    return (
                      <div key={order.id} className="order-row">
                        <div>
                          <strong>#{order.id}</strong>
                          <span className={`status status-${order.status.toLowerCase()}`}>
                            {order.status}
                          </span>
                          <span className="muted">{formatCents(order.subtotalCents)}</span>
                        </div>
                        {bill ? (
                          <span className={bill.settled ? 'paid' : 'unpaid'}>
                            {bill.settled
                              ? 'Paid in full'
                              : `${formatCents(bill.outstandingCents)} outstanding`}
                          </span>
                        ) : (
                          <button className="link" onClick={() => openBill(order.id)}>
                            Check payment
                          </button>
                        )}
                      </div>
                    );
                  })}
                </div>
              )}

              <footer>
                <button
                  className={table.attentionFlagged ? 'primary' : 'ghost'}
                  onClick={() => flag(table)}
                >
                  {table.attentionFlagged ? 'Resolve' : 'Flag for attention'}
                </button>
              </footer>
            </li>
          );
        })}
      </ul>
    </div>
  );
}
