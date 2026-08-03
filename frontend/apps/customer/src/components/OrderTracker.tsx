import { formatCents, type Order, type OrderStatus } from '@rto/shared';

interface Props {
  orders: Order[];
  onPay: (orderId: number) => void;
}

/** The stages a customer sees. PAID is shown as a completed state, not a step to reach. */
const STAGES: OrderStatus[] = ['PLACED', 'ACKNOWLEDGED', 'PREPARING', 'READY'];

const LABELS: Record<OrderStatus, string> = {
  PLACED: 'Sent to kitchen',
  ACKNOWLEDGED: 'Kitchen has it',
  PREPARING: 'Being cooked',
  READY: 'Ready',
  PAID: 'Paid',
  CANCELLED: 'Cancelled',
};

/**
 * Live order status.
 *
 * Driven entirely by pushes from notification-service — there is no polling here. The
 * progress bar is derived from the order's own status rather than from elapsed time, so it
 * never advances on its own and then has to jump back.
 */
export function OrderTracker({ orders, onPay }: Props) {
  if (orders.length === 0) {
    return <p className="muted centred">No orders yet. Add something from the menu.</p>;
  }

  return (
    <div className="orders">
      {orders.map((order) => {
        const reached = STAGES.indexOf(order.status);
        const settled = order.status === 'PAID';
        return (
          <article key={order.id} className="order">
            <header>
              <h3>Order #{order.id}</h3>
              <span className={`status status-${order.status.toLowerCase()}`}>
                {LABELS[order.status]}
              </span>
            </header>

            {!settled && order.status !== 'CANCELLED' && (
              <ol className="progress">
                {STAGES.map((stage, index) => (
                  <li key={stage} className={index <= reached ? 'done' : ''}>
                    <span className="dot" aria-hidden="true" />
                    <span className="label">{LABELS[stage]}</span>
                  </li>
                ))}
              </ol>
            )}

            <ul className="order-lines">
              {order.lines.map((line) => (
                <li key={line.id}>
                  <span>
                    {line.quantity}× {line.name}
                    {line.modifiers.length > 0 && (
                      <span className="muted"> · {line.modifiers.join(', ')}</span>
                    )}
                  </span>
                  <span className="price">{formatCents(line.lineTotalCents)}</span>
                </li>
              ))}
            </ul>

            <footer>
              <span className="total">{formatCents(order.subtotalCents)}</span>
              {order.status === 'READY' && (
                <button className="primary" onClick={() => onPay(order.id)}>
                  Pay this order
                </button>
              )}
              {settled && <span className="muted">Settled — thank you</span>}
            </footer>
          </article>
        );
      })}
    </div>
  );
}
