import { api, customerSession, type CustomerSession } from '@rto/shared';
import { useCallback, useEffect, useState } from 'react';

/**
 * Resolves the QR code in the URL into a table session.
 *
 * The page is opened by scanning a sticker, so the code arrives as `/t/<qrCode>`. A stored
 * session for the same table is reused rather than re-scanned, which keeps a page refresh
 * mid-meal from opening a second session.
 */
export function useSession() {
  const [session, setSession] = useState<CustomerSession | null>(() => customerSession.get());
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const qrCode = window.location.pathname.startsWith('/t/')
    ? decodeURIComponent(window.location.pathname.slice(3))
    : null;

  useEffect(() => {
    if (!qrCode || session) return;
    setLoading(true);
    api
      .scan(qrCode)
      .then((scanned) => {
        customerSession.set(scanned);
        setSession(scanned);
        setError(null);
      })
      .catch((err: Error) => setError(err.message))
      .finally(() => setLoading(false));
  }, [qrCode, session]);

  const reset = useCallback(() => {
    customerSession.clear();
    setSession(null);
  }, []);

  return { session, qrCode, error, loading, reset };
}
