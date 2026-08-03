import { formatCents, type Menu, type MenuItem } from '@rto/shared';

interface Props {
  menu: Menu;
  onSelect: (item: MenuItem) => void;
}

/**
 * The menu, grouped by category.
 *
 * Unavailable items stay visible but disabled rather than disappearing. A diner who was
 * looking at the salmon when it sold out should see that it is gone, not watch the list
 * silently reflow and wonder whether they imagined it.
 */
export function MenuList({ menu, onSelect }: Props) {
  return (
    <div className="menu">
      {menu.categories.map((category) => (
        <section key={category.id} className="category">
          <h2>{category.name}</h2>
          <ul>
            {category.items.map((item) => (
              <li key={item.id}>
                <button
                  className={`menu-item${item.available ? '' : ' unavailable'}`}
                  onClick={() => onSelect(item)}
                  disabled={!item.available}
                  aria-disabled={!item.available}
                >
                  <span className="menu-item-text">
                    <span className="menu-item-name">
                      {item.name}
                      {!item.available && <span className="sold-out">sold out</span>}
                    </span>
                    {item.description && <span className="muted">{item.description}</span>}
                    {item.modifierGroups.some((group) => group.minSelect > 0) && (
                      <span className="hint">Choice required</span>
                    )}
                  </span>
                  <span className="price">{formatCents(item.priceCents)}</span>
                </button>
              </li>
            ))}
          </ul>
        </section>
      ))}
    </div>
  );
}
