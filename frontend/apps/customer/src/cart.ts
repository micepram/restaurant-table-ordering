import { lineTotalCents, type MenuItem, type PlaceOrderRequest } from '@rto/shared';

export interface CartLine {
  /** Stable key: the same item with different modifiers is a separate line. */
  key: string;
  item: MenuItem;
  quantity: number;
  modifierIds: number[];
  modifierNames: string[];
  modifierDeltas: number[];
  note?: string;
}

export function cartLineKey(itemId: number, modifierIds: number[]): string {
  return `${itemId}:${[...modifierIds].sort((a, b) => a - b).join('-')}`;
}

export function lineTotal(line: CartLine): number {
  return lineTotalCents(line.item.priceCents, line.modifierDeltas, line.quantity);
}

export function cartTotal(lines: CartLine[]): number {
  return lines.reduce((total, line) => total + lineTotal(line), 0);
}

/**
 * Converts the cart into the request body.
 *
 * Prices are deliberately absent: the server re-reads every price from menu-service, so
 * what the cart displays is a preview, never the authority. That is also what stops a
 * tampered request from setting its own total.
 */
export function toOrderRequest(lines: CartLine[]): PlaceOrderRequest {
  return {
    lines: lines.map((line) => ({
      menuItemId: line.item.id,
      quantity: line.quantity,
      modifierIds: line.modifierIds,
      note: line.note,
    })),
  };
}

/**
 * Whether the chosen modifiers satisfy every group's min/max on this item.
 *
 * The same rule is enforced server-side; checking here keeps the customer from submitting
 * an order that is certain to be rejected, rather than replacing the server's check.
 */
export function validateModifiers(
  item: MenuItem,
  modifierIds: number[],
): { valid: boolean; message?: string } {
  for (const group of item.modifierGroups) {
    const chosen = group.modifiers.filter((modifier) => modifierIds.includes(modifier.id)).length;
    if (chosen < group.minSelect) {
      return {
        valid: false,
        message: `Choose ${group.minSelect === 1 ? 'an option' : `at least ${group.minSelect}`} from ${group.name}`,
      };
    }
    if (chosen > group.maxSelect) {
      return { valid: false, message: `Choose at most ${group.maxSelect} from ${group.name}` };
    }
  }
  return { valid: true };
}
