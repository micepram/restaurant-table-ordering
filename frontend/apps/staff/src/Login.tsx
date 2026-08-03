import { api, staffSession, type Role } from '@rto/shared';
import { useState } from 'react';

interface Props {
  onSignedIn: () => void;
  /** Roles permitted to use this app, so a waiter cannot sign into the kitchen board. */
  allowedRoles: Role[];
  title: string;
}

/**
 * Staff sign-in. Shared shape between the kitchen and staff apps.
 *
 * The role check here is a courtesy, not a control: it stops someone signing into the wrong
 * app and seeing an empty screen. Every endpoint and WebSocket subscription enforces the
 * role independently on the server.
 */
export function Login({ onSignedIn, allowedRoles, title }: Props) {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const response = await api.login(username, password);
      if (!allowedRoles.includes(response.role)) {
        setError(`${response.role} accounts cannot use this screen.`);
        return;
      }
      staffSession.set({
        token: response.token,
        username: response.username,
        role: response.role,
      });
      onSignedIn();
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="login">
      <form onSubmit={submit}>
        <h1>{title}</h1>
        <label>
          Username
          <input
            value={username}
            onChange={(event) => setUsername(event.target.value)}
            autoComplete="username"
            autoFocus
          />
        </label>
        <label>
          Password
          <input
            type="password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            autoComplete="current-password"
          />
        </label>
        {error && <p className="error" role="alert">{error}</p>}
        <button className="primary" disabled={busy || !username || !password}>
          {busy ? 'Signing in…' : 'Sign in'}
        </button>
        <p className="hint">
          Demo accounts: marco (kitchen), ana or sam (staff), rita (manager). Password is the
          username followed by <code>-pw</code>.
        </p>
      </form>
    </div>
  );
}
