import type { Urgency } from './types';

/**
 * Wait-time colour coding for the kitchen board.
 *
 * The server sends `urgency` with each ticket, but the board re-derives it locally on a
 * one-second tick. Otherwise a ticket sitting at 4:59 would stay green until some unrelated
 * event triggered a push — the colours have to advance on their own, without the server
 * being asked. The thresholds come from the server so both agree on what "late" means.
 */
export function urgencyFor(waitSeconds: number, warnAfter: number, lateAfter: number): Urgency {
  if (waitSeconds >= lateAfter) return 'LATE';
  if (waitSeconds >= warnAfter) return 'WARNING';
  return 'NORMAL';
}

/** Seconds elapsed since an ISO timestamp, floored at zero against clock skew. */
export function secondsSince(isoTimestamp: string, now: number = Date.now()): number {
  return Math.max(0, Math.floor((now - new Date(isoTimestamp).getTime()) / 1000));
}

/** Compact `m:ss` for a ticket that has been waiting; hours appear only when relevant. */
export function formatWait(totalSeconds: number): string {
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  const pad = (value: number) => String(value).padStart(2, '0');
  return hours > 0 ? `${hours}:${pad(minutes)}:${pad(seconds)}` : `${minutes}:${pad(seconds)}`;
}
