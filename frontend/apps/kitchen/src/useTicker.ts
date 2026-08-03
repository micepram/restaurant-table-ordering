import { useEffect, useState } from 'react';

/**
 * A one-second clock that drives the board's wait times and colours.
 *
 * Without it a ticket sitting at 4:59 would stay green until some unrelated event triggered
 * a WebSocket push — the colours have to advance on their own. Deriving them locally from
 * each ticket's `placedAt` also means the board keeps counting correctly while the socket
 * is down, instead of freezing at whatever the last frame said.
 */
export function useTicker(intervalMs = 1000): number {
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), intervalMs);
    return () => clearInterval(timer);
  }, [intervalMs]);

  return now;
}
