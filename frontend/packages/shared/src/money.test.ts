import { describe, expect, it } from 'vitest';

import { formatCents, lineTotalCents, sumCents } from './money';

describe('formatCents', () => {
  it('renders whole and fractional amounts', () => {
    expect(formatCents(8050)).toBe('£80.50');
    expect(formatCents(500)).toBe('£5.00');
    expect(formatCents(0)).toBe('£0.00');
  });

  it('keeps two decimals for amounts under a pound', () => {
    expect(formatCents(5)).toBe('£0.05');
  });
});

describe('lineTotalCents', () => {
  it('applies modifiers per unit, not per line', () => {
    // A steak at 26.50 with a 1.50 sauce, ordered twice, is 56.00 -- the sauce is charged
    // on each steak. Adding modifiers once and then multiplying would undercharge by 1.50.
    expect(lineTotalCents(2650, [150], 2)).toBe(5600);
  });

  it('handles an item with no modifiers', () => {
    expect(lineTotalCents(500, [], 1)).toBe(500);
  });

  it('sums several modifiers on one line', () => {
    expect(lineTotalCents(1450, [200, 150, 100], 1)).toBe(1900);
  });

  it('matches the backend subtotal for the demo order', () => {
    // Salmon + salsa verde, two ribeyes with medium-rare and peppercorn, and chips.
    const salmon = lineTotalCents(1850, [100], 1);
    const steak = lineTotalCents(2650, [0, 150], 2);
    const chips = lineTotalCents(500, [], 1);
    expect(sumCents([salmon, steak, chips])).toBe(8050);
  });
});
