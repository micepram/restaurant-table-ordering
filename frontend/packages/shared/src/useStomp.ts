import { Client, type IMessage } from '@stomp/stompjs';
import { useEffect, useRef, useState } from 'react';

import { API_BASE } from './api';

export type StompStatus = 'connecting' | 'connected' | 'disconnected';

export interface UseStompOptions {
  /** Endpoint path on the gateway, e.g. `/ws/customer`. */
  path: string;
  token: string | null;
  /** Destinations to subscribe to. Re-subscribed automatically after a reconnect. */
  destinations: string[];
  onMessage: (destination: string, body: unknown) => void;
  enabled?: boolean;
}

/**
 * Subscribes to STOMP destinations over a WebSocket, with reconnection.
 *
 * Two details matter here:
 *
 * - The token goes in the CONNECT frame's headers, not the URL. Browser WebSocket APIs
 *   cannot set an Authorization header on the handshake, and putting a credential in a
 *   query string leaks it into access logs and browser history.
 *
 * - `onMessage` is held in a ref rather than being a dependency of the effect. Callers
 *   almost always pass an inline arrow function, which is a new identity on every render;
 *   as a dependency it would tear down and rebuild the socket on each one.
 */
export function useStomp({
  path,
  token,
  destinations,
  onMessage,
  enabled = true,
}: UseStompOptions): StompStatus {
  const [status, setStatus] = useState<StompStatus>('disconnected');
  const handlerRef = useRef(onMessage);
  handlerRef.current = onMessage;

  // Joined so the effect re-runs when the set of destinations actually changes, not when
  // the caller happens to build a new array with the same contents.
  const destinationKey = destinations.join(',');

  useEffect(() => {
    if (!enabled || !token || destinations.length === 0) {
      setStatus('disconnected');
      return;
    }

    const url = `${API_BASE.replace(/^http/, 'ws')}${path}`;
    const client = new Client({
      brokerURL: url,
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 3000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        setStatus('connected');
        for (const destination of destinationKey.split(',')) {
          client.subscribe(destination, (message: IMessage) => {
            try {
              handlerRef.current(destination, JSON.parse(message.body));
            } catch {
              handlerRef.current(destination, message.body);
            }
          });
        }
      },
      onWebSocketClose: () => setStatus('disconnected'),
      // A STOMP ERROR frame means the server refused the CONNECT or a SUBSCRIBE — a bad
      // token, or a destination this session may not read. Retrying will not help, so the
      // client is deactivated rather than left reconnecting in a loop.
      onStompError: (frame) => {
        console.error('STOMP error:', frame.headers.message, frame.body);
        setStatus('disconnected');
        void client.deactivate();
      },
    });

    setStatus('connecting');
    client.activate();

    return () => {
      void client.deactivate();
    };
  }, [path, token, destinationKey, enabled, destinations.length]);

  return status;
}
