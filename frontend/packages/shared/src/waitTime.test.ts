import { describe, expect, it } from 'vitest';

import { formatWait, secondsSince, urgencyFor } from './waitTime';

/**
 * The board's colour coding. This is the logic a cook reads at a glance across the pass,
 * so an off-by-one at a threshold is a ticket that looks fine while it is already late.
 */
describe('urgencyFor', () => {
  const warn = 300; // 5 minutes
  const late = 600; // 10 minutes

  it('is normal below the warning threshold', () => {
    expect(urgencyFor(0, warn, late)).toBe('NORMAL');
    expect(urgencyFor(299, warn, late)).toBe('NORMAL');
  });

  it('turns amber exactly at the warning threshold, not a second later', () => {
    expect(urgencyFor(300, warn, late)).toBe('WARNING');
    expect(urgencyFor(599, warn, late)).toBe('WARNING');
  });

  it('turns red exactly at the late threshold', () => {
    expect(urgencyFor(600, warn, late)).toBe('LATE');
    expect(urgencyFor(9999, warn, late)).toBe('LATE');
  });

  it('honours thresholds supplied by the server rather than hard-coding them', () => {
    // The server owns these values; the board must follow whatever it sends.
    expect(urgencyFor(90, 60, 120)).toBe('WARNING');
    expect(urgencyFor(150, 60, 120)).toBe('LATE');
  });
});

describe('secondsSince', () => {
  it('measures elapsed time from an ISO timestamp', () => {
    const now = Date.parse('2026-08-03T12:05:30Z');
    expect(secondsSince('2026-08-03T12:00:00Z', now)).toBe(330);
  });

  it('floors at zero when the clock is skewed into the future', () => {
    const now = Date.parse('2026-08-03T12:00:00Z');
    // A ticket timestamped ahead of this browser's clock must read 0, not a negative age
    // that would render as a nonsense wait time.
    expect(secondsSince('2026-08-03T12:00:10Z', now)).toBe(0);
  });
});

describe('formatWait', () => {
  it('pads seconds so the display does not jitter in width', () => {
    expect(formatWait(65)).toBe('1:05');
    expect(formatWait(9)).toBe('0:09');
  });

  it('shows hours only once a ticket has been waiting that long', () => {
    expect(formatWait(3599)).toBe('59:59');
    expect(formatWait(3661)).toBe('1:01:01');
  });
});
