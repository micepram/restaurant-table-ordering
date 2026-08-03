import { api, formatCents, type Bill, type SplitView } from '@rto/shared';
import { useEffect, useState } from 'react';

interface Props {
  bill: Bill;
  token: string;
  onChange: (bill: Bill) => void;
  onClose: () => void;
  onError: (message: string) => void;
}

const TIP_OPTIONS = [0, 10, 12, 15, 18, 20];

/**
 * Tip, split, and pay.
 *
 * Splitting is a preview: the server computes the shares so the client never has to divide
 * money, which is what keeps three shares adding back up to the bill exactly. Each payer
 * then charges their own share, and the order only moves to PAID when the outstanding
 * balance reaches zero.
 */
export function BillPanel({ bill, token, onChange, onClose, onError }: Props) {
  const [ways, setWays] = useState(1);
  const [split, setSplit] = useState<SplitView | null>(null);
  const [card, setCard] = useState('4111 1111 1111 1111');
  const [paying, setPaying] = useState(false);

  useEffect(() => {
    api
      .split(token, bill.orderId, ways)
      .then(setSplit)
      .catch((err: Error) => onError(err.message));
  }, [token, bill.orderId, bill.tipCents, ways, onError]);

  const applyTip = async (percent: number) => {
    try {
      onChange(await api.setTip(token, bill.orderId, percent));
    } catch (err) {
      onError((err as Error).message);
    }
  };

  const pay = async (amountCents: number, tipCents: number) => {
    setPaying(true);
    try {
      onChange(await api.pay(token, bill.orderId, amountCents, tipCents, card));
    } catch (err) {
      // A 402 means the card was refused, not that the request was malformed. The
      // distinction is why the message is shown as-is: "try another card", not "fix input".
      onError((err as Error).message);
    } finally {
      setPaying(false);
    }
  };

  const tipPercent = bill.subtotalCents > 0
    ? Math.round((bill.tipCents / bill.subtotalCents) * 100)
    : 0;

  return (
    <section className="bill">
      <header>
        <h3>Bill for order #{bill.orderId}</h3>
        <button className="link" onClick={onClose} aria-label="Close bill">✕</button>
      </header>

      <dl className="totals">
        <div><dt>Subtotal</dt><dd>{formatCents(bill.subtotalCents)}</dd></div>
        <div><dt>Tip</dt><dd>{formatCents(bill.tipCents)}</dd></div>
        <div className="grand"><dt>Total</dt><dd>{formatCents(bill.totalCents)}</dd></div>
        <div><dt>Outstanding</dt><dd>{formatCents(bill.outstandingCents)}</dd></div>
      </dl>

      {!bill.settled && (
        <>
          <div className="field">
            <span className="label">Add a tip</span>
            <div className="chips">
              {TIP_OPTIONS.map((percent) => (
                <button
                  key={percent}
                  className={tipPercent === percent ? 'chip active' : 'chip'}
                  onClick={() => applyTip(percent)}
                  // The tip is fixed once part of the bill is paid; changing it would
                  // silently re-price shares that have already settled.
                  disabled={bill.outstandingCents !== bill.totalCents}
                >
                  {percent === 0 ? 'None' : `${percent}%`}
                </button>
              ))}
            </div>
            {bill.outstandingCents !== bill.totalCents && (
              <p className="hint">The tip is locked once someone has paid.</p>
            )}
          </div>

          <div className="field">
            <span className="label">Split between</span>
            <div className="chips">
              {[1, 2, 3, 4, 5, 6].map((count) => (
                <button
                  key={count}
                  className={ways === count ? 'chip active' : 'chip'}
                  onClick={() => setWays(count)}
                >
                  {count}
                </button>
              ))}
            </div>
          </div>

          {split && (
            <ul className="shares">
              {split.shares.map((share) => (
                <li key={share.position}>
                  <span>
                    Share {share.position}
                    <span className="muted">
                      {' '}
                      {formatCents(share.amountCents)} + {formatCents(share.tipCents)} tip
                    </span>
                  </span>
                  <button
                    onClick={() => pay(share.amountCents, share.tipCents)}
                    disabled={paying || bill.settled}
                  >
                    Pay {formatCents(share.amountCents + share.tipCents)}
                  </button>
                </li>
              ))}
            </ul>
          )}

          <label className="note">
            Card number
            <input
              type="text"
              inputMode="numeric"
              value={card}
              onChange={(event) => setCard(event.target.value)}
            />
            <span className="hint">
              Mock processor: any card ending 0000 is declined, 0001 fails.
            </span>
          </label>
        </>
      )}

      {bill.payments.length > 0 && (
        <ul className="payments">
          {bill.payments.map((payment) => (
            <li key={payment.id} className={payment.status.toLowerCase()}>
              <span>
                {payment.status} · {formatCents(payment.amountCents + payment.tipCents)}
                {payment.cardLast4 && <span className="muted"> ····{payment.cardLast4}</span>}
              </span>
              {payment.failureReason && <span className="muted">{payment.failureReason}</span>}
            </li>
          ))}
        </ul>
      )}

      {bill.settled && <p className="settled">Settled in full — thank you</p>}
    </section>
  );
}
