import { formatCents, lineTotalCents, type MenuItem } from '@rto/shared';
import { useMemo, useState } from 'react';

import { validateModifiers } from '../cart';

interface Props {
  item: MenuItem;
  onAdd: (item: MenuItem, modifierIds: number[], note?: string) => void;
  onClose: () => void;
}

/** Bottom sheet for choosing modifiers. Mobile-first: thumb-reachable, one column. */
export function ItemSheet({ item, onAdd, onClose }: Props) {
  const [selected, setSelected] = useState<number[]>([]);
  const [note, setNote] = useState('');

  const validation = useMemo(() => validateModifiers(item, selected), [item, selected]);

  const deltas = item.modifierGroups
    .flatMap((group) => group.modifiers)
    .filter((modifier) => selected.includes(modifier.id))
    .map((modifier) => modifier.priceDeltaCents);

  const toggle = (groupId: number, modifierId: number, maxSelect: number) => {
    const group = item.modifierGroups.find((candidate) => candidate.id === groupId);
    if (!group) return;
    const groupIds = group.modifiers.map((modifier) => modifier.id);

    setSelected((current) => {
      if (current.includes(modifierId)) {
        return current.filter((id) => id !== modifierId);
      }
      const inGroup = current.filter((id) => groupIds.includes(id));
      // A single-select group replaces rather than refusing — tapping a second option in
      // "Cooked to" should switch the choice, not silently do nothing.
      if (maxSelect === 1) {
        return [...current.filter((id) => !groupIds.includes(id)), modifierId];
      }
      if (inGroup.length >= maxSelect) return current;
      return [...current, modifierId];
    });
  };

  return (
    <div className="sheet-backdrop" onClick={onClose}>
      <div className="sheet" onClick={(event) => event.stopPropagation()} role="dialog" aria-label={item.name}>
        <header>
          <h2>{item.name}</h2>
          <button className="link" onClick={onClose} aria-label="Close">✕</button>
        </header>
        {item.description && <p className="muted">{item.description}</p>}

        {item.modifierGroups.map((group) => (
          <fieldset key={group.id} className="group">
            <legend>
              {group.name}
              <span className="hint">
                {group.minSelect > 0 ? ' required' : ' optional'}
                {group.maxSelect > 1 ? ` · up to ${group.maxSelect}` : ''}
              </span>
            </legend>
            {group.modifiers.map((modifier) => (
              <label key={modifier.id} className={modifier.available ? '' : 'unavailable'}>
                <input
                  type={group.maxSelect === 1 ? 'radio' : 'checkbox'}
                  name={`group-${group.id}`}
                  checked={selected.includes(modifier.id)}
                  disabled={!modifier.available}
                  onChange={() => toggle(group.id, modifier.id, group.maxSelect)}
                />
                <span>{modifier.name}</span>
                {modifier.priceDeltaCents > 0 && (
                  <span className="price">+{formatCents(modifier.priceDeltaCents)}</span>
                )}
              </label>
            ))}
          </fieldset>
        ))}

        <label className="note">
          Anything we should know?
          <input
            type="text"
            value={note}
            maxLength={255}
            placeholder="e.g. no capers"
            onChange={(event) => setNote(event.target.value)}
          />
        </label>

        {!validation.valid && <p className="hint warn">{validation.message}</p>}

        <button
          className="primary"
          disabled={!validation.valid}
          onClick={() => onAdd(item, selected, note.trim() || undefined)}
        >
          Add · {formatCents(lineTotalCents(item.priceCents, deltas, 1))}
        </button>
      </div>
    </div>
  );
}
