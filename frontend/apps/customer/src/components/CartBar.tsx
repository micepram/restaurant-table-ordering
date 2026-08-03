import { formatCents } from '@rto/shared';
import { useState } from 'react';

import { lineTotal, type CartLine } from '../cart';

interface Props {
  lines: CartLine[];
  total: string;
  onChangeQuantity: (key: string, delta: number) => void;
  onPlace: () => void;
}

/** Sticky cart, collapsed to a summary bar until tapped. */
export function CartBar({ lines, total, onChangeQuantity, onPlace }: Props) {
  const [expanded, setExpanded] = useState(false);
  const count = lines.reduce((sum, line) => sum + line.quantity, 0);

  return (
    <div className={`cart${expanded ? ' expanded' : ''}`}>
      {expanded && (
        <ul className="cart-lines">
          {lines.map((line) => (
            <li key={line.key}>
              <div className="cart-line-text">
                <strong>{line.item.name}</strong>
                {line.modifierNames.length > 0 && (
                  <span className="muted">{line.modifierNames.join(', ')}</span>
                )}
                {line.note && <span className="muted">“{line.note}”</span>}
              </div>
              <div className="stepper">
                <button onClick={() => onChangeQuantity(line.key, -1)} aria-label="One fewer">−</button>
                <span>{line.quantity}</span>
                <button onClick={() => onChangeQuantity(line.key, 1)} aria-label="One more">+</button>
              </div>
              <span className="price">{formatCents(lineTotal(line))}</span>
            </li>
          ))}
        </ul>
      )}
      <div className="cart-bar">
        <button className="link" onClick={() => setExpanded((value) => !value)}>
          {count} item{count === 1 ? '' : 's'} · {total} {expanded ? '▾' : '▴'}
        </button>
        <button className="primary" onClick={onPlace}>Place order</button>
      </div>
    </div>
  );
}
