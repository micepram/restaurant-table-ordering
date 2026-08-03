/**
 * Money is integer cents everywhere, converted to a float only for display.
 *
 * Keeping cents as the working unit is what lets a three-way split add back up exactly;
 * see BillSplitter on the backend. Nothing in the UI should ever do arithmetic on the
 * output of these functions.
 */

const formatter = new Intl.NumberFormat('en-GB', {
  style: 'currency',
  currency: 'GBP',
});

export function formatCents(cents: number): string {
  return formatter.format(cents / 100);
}

/** Sum without leaving integer arithmetic. */
export function sumCents(values: number[]): number {
  return values.reduce((total, value) => total + value, 0);
}

/**
 * Line total for a cart entry: (item + chosen modifiers) x quantity.
 *
 * Mirrors `OrderLineEntity.of` on the backend. The client shows this so the cart matches
 * what the server will charge, but the server recomputes it from its own prices — this is
 * a preview, never the source of truth.
 */
export function lineTotalCents(
  unitPriceCents: number,
  modifierDeltas: number[],
  quantity: number,
): number {
  return (unitPriceCents + sumCents(modifierDeltas)) * quantity;
}
