import { api, type MenuItem } from '@rto/shared';
import { useState } from 'react';

interface Props {
  items: MenuItem[];
  token: string;
  onClose: () => void;
  onChanged: () => void;
  onError: (message: string) => void;
}

/**
 * The 86 board — mark an item off, or put it back on.
 *
 * Toggling here publishes to menu-service rather than writing anything locally. That single
 * path is what makes the database write, the Redis eviction and the push to every open
 * table session happen once, in one place: within a second of tapping, the item greys out
 * on every diner's phone.
 */
export function EightySixPanel({ items, token, onClose, onChanged, onError }: Props) {
  const [pending, setPending] = useState<number | null>(null);
  const [filter, setFilter] = useState('');

  const toggle = async (item: MenuItem) => {
    setPending(item.id);
    try {
      await api.setAvailability(
        token,
        item.id,
        !item.available,
        item.available ? 'Sold out' : 'Back on',
      );
      // The change travels through Kafka, so it is not readable the instant this resolves.
      // A short pause before refetching avoids showing the old value and then flipping it.
      setTimeout(onChanged, 600);
    } catch (err) {
      onError((err as Error).message);
    } finally {
      setPending(null);
    }
  };

  const visible = items.filter((item) =>
    item.name.toLowerCase().includes(filter.trim().toLowerCase()),
  );

  return (
    <div className="panel-backdrop" onClick={onClose}>
      <div className="panel" onClick={(event) => event.stopPropagation()} role="dialog" aria-label="86 an item">
        <header>
          <h2>86 an item</h2>
          <button className="link" onClick={onClose} aria-label="Close">✕</button>
        </header>

        <input
          className="filter"
          placeholder="Filter…"
          value={filter}
          onChange={(event) => setFilter(event.target.value)}
          autoFocus
        />

        <ul className="eighty-six">
          {visible.map((item) => (
            <li key={item.id} className={item.available ? '' : 'off'}>
              <span>{item.name}</span>
              <button
                className={item.available ? 'ghost' : 'primary'}
                disabled={pending === item.id}
                onClick={() => toggle(item)}
              >
                {pending === item.id ? '…' : item.available ? '86 it' : 'Back on'}
              </button>
            </li>
          ))}
        </ul>
      </div>
    </div>
  );
}
