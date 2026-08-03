import type { MenuItem } from '@rto/shared';
import { describe, expect, it } from 'vitest';

import { cartLineKey, cartTotal, toOrderRequest, validateModifiers, type CartLine } from './cart';

const steak: MenuItem = {
  id: 5,
  name: 'Ribeye Steak',
  description: null,
  priceCents: 2650,
  available: true,
  modifierGroups: [
    {
      id: 1,
      name: 'Cooked to',
      minSelect: 1,
      maxSelect: 1,
      modifiers: [
        { id: 7, name: 'Medium rare', priceDeltaCents: 0, available: true },
        { id: 8, name: 'Rare', priceDeltaCents: 0, available: true },
      ],
    },
    {
      id: 2,
      name: 'Sauce',
      minSelect: 0,
      maxSelect: 1,
      modifiers: [{ id: 4, name: 'Peppercorn', priceDeltaCents: 150, available: true }],
    },
  ],
};

describe('cartLineKey', () => {
  it('separates the same item ordered with different modifiers', () => {
    expect(cartLineKey(5, [7])).not.toBe(cartLineKey(5, [8]));
  });

  it('treats the same modifiers chosen in a different order as one line', () => {
    // Otherwise picking sauce-then-doneness would stack as a second line beside
    // doneness-then-sauce, and the cart would show the same steak twice.
    expect(cartLineKey(5, [7, 4])).toBe(cartLineKey(5, [4, 7]));
  });
});

describe('validateModifiers', () => {
  it('rejects a steak with no doneness chosen', () => {
    const result = validateModifiers(steak, []);
    expect(result.valid).toBe(false);
    expect(result.message).toContain('Cooked to');
  });

  it('accepts a steak once a mandatory choice is made', () => {
    expect(validateModifiers(steak, [7]).valid).toBe(true);
  });

  it('rejects two choices from a single-select group', () => {
    const result = validateModifiers(steak, [7, 8]);
    expect(result.valid).toBe(false);
    expect(result.message).toContain('at most 1');
  });

  it('allows an optional group to be skipped', () => {
    expect(validateModifiers(steak, [7]).valid).toBe(true);
  });
});

describe('cartTotal', () => {
  it('charges modifiers on every unit', () => {
    const line: CartLine = {
      key: cartLineKey(5, [7, 4]),
      item: steak,
      quantity: 2,
      modifierIds: [7, 4],
      modifierNames: ['Medium rare', 'Peppercorn'],
      modifierDeltas: [0, 150],
    };
    expect(cartTotal([line])).toBe(5600);
  });
});

describe('toOrderRequest', () => {
  it('sends no prices, so the server sets them', () => {
    const line: CartLine = {
      key: cartLineKey(5, [7]),
      item: steak,
      quantity: 1,
      modifierIds: [7],
      modifierNames: ['Medium rare'],
      modifierDeltas: [0],
    };
    const request = toOrderRequest([line]);
    expect(request.lines[0]).toEqual({
      menuItemId: 5,
      quantity: 1,
      modifierIds: [7],
      note: undefined,
    });
    expect(JSON.stringify(request)).not.toContain('2650');
  });
});
