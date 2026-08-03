import {
  api,
  formatCents,
  useStomp,
  type Bill,
  type Menu,
  type MenuItem,
  type Notification,
  type Order,
} from '@rto/shared';
import { useCallback, useEffect, useMemo, useState } from 'react';

import { BillPanel } from './components/BillPanel';
import { CartBar } from './components/CartBar';
import { ItemSheet } from './components/ItemSheet';
import { MenuList } from './components/MenuList';
import { OrderTracker } from './components/OrderTracker';
import { cartLineKey, cartTotal, toOrderRequest, type CartLine } from './cart';
import { useSession } from './useSession';

type Tab = 'menu' | 'orders';

export function App() {
  const { session, qrCode, error: sessionError, loading, reset } = useSession();
  const [menu, setMenu] = useState<Menu | null>(null);
  const [cart, setCart] = useState<CartLine[]>([]);
  const [openItem, setOpenItem] = useState<MenuItem | null>(null);
  const [orders, setOrders] = useState<Order[]>([]);
  const [bill, setBill] = useState<Bill | null>(null);
  const [tab, setTab] = useState<Tab>('menu');
  const [error, setError] = useState<string | null>(null);
  const [flash, setFlash] = useState<string | null>(null);

  const token = session?.token ?? null;

  const loadMenu = useCallback(() => {
    if (!token) return;
    api.menu(token).then(setMenu).catch((err: Error) => setError(err.message));
  }, [token]);

  const loadOrders = useCallback(() => {
    if (!token || !session) return;
    api
      .ordersForTable(token, session.tableId)
      .then(setOrders)
      .catch((err: Error) => setError(err.message));
  }, [token, session]);

  useEffect(loadMenu, [loadMenu]);
  useEffect(loadOrders, [loadOrders]);

  const destinations = useMemo(
    () => (session ? [`/topic/tables/${session.tableId}`, '/topic/menu'] : []),
    [session],
  );

  /**
   * Live updates. Every handler refetches rather than trusting the pushed payload, so a
   * duplicated or out-of-order message costs a redundant request instead of corrupting
   * what is on screen.
   */
  const status = useStomp({
    path: '/ws/customer',
    token,
    destinations,
    enabled: Boolean(session),
    onMessage: (_destination, body) => {
      const notification = body as Notification<Record<string, unknown>>;
      switch (notification.type) {
        case 'MENU_INVALIDATED': {
          // The salmon flow's last hop. menu-service publishes this only after the row is
          // written and Redis is evicted, so refetching now is guaranteed to see the change.
          loadMenu();
          const name = notification.data.itemName as string;
          const available = notification.data.available as boolean;
          setFlash(available ? `${name} is back on` : `${name} is no longer available`);
          break;
        }
        case 'ORDER_PLACED':
        case 'ORDER_STATUS':
          loadOrders();
          break;
        case 'PAYMENT':
          loadOrders();
          if (bill) api.bill(token!, bill.orderId).then(setBill).catch(() => undefined);
          break;
        default:
          break;
      }
    },
  });

  useEffect(() => {
    if (!flash) return;
    const timer = setTimeout(() => setFlash(null), 5000);
    return () => clearTimeout(timer);
  }, [flash]);

  const addToCart = (item: MenuItem, modifierIds: number[], note?: string) => {
    const chosen = item.modifierGroups
      .flatMap((group) => group.modifiers)
      .filter((modifier) => modifierIds.includes(modifier.id));
    const key = cartLineKey(item.id, modifierIds);

    setCart((current) => {
      const existing = current.find((line) => line.key === key);
      if (existing) {
        return current.map((line) =>
          line.key === key ? { ...line, quantity: line.quantity + 1 } : line,
        );
      }
      return [
        ...current,
        {
          key,
          item,
          quantity: 1,
          modifierIds,
          modifierNames: chosen.map((modifier) => modifier.name),
          modifierDeltas: chosen.map((modifier) => modifier.priceDeltaCents),
          note,
        },
      ];
    });
    setOpenItem(null);
  };

  const changeQuantity = (key: string, delta: number) => {
    setCart((current) =>
      current
        .map((line) => (line.key === key ? { ...line, quantity: line.quantity + delta } : line))
        .filter((line) => line.quantity > 0),
    );
  };

  const placeOrder = async () => {
    if (!token || cart.length === 0) return;
    setError(null);
    try {
      const order = await api.placeOrder(token, toOrderRequest(cart));
      setCart([]);
      setOrders((current) => [order, ...current]);
      setTab('orders');
    } catch (err) {
      // The server's message is user-facing ("Ribeye Steak requires at least 1 choice
      // from Cooked to"), so it is shown verbatim rather than replaced with a generic one.
      setError((err as Error).message);
    }
  };

  const openBill = async (orderId: number) => {
    if (!token) return;
    try {
      setBill(await api.openBill(token, orderId));
    } catch (err) {
      setError((err as Error).message);
    }
  };

  if (!qrCode && !session) {
    return (
      <div className="centred">
        <h1>Scan the code on your table</h1>
        <p className="muted">
          This page opens from the QR sticker at your table. For the demo, try{' '}
          <a href="/t/qr-t01-9f3a2b">table T-01</a>.
        </p>
      </div>
    );
  }

  if (loading) return <div className="centred"><p>Opening your table…</p></div>;

  if (sessionError) {
    return (
      <div className="centred">
        <h1>That code didn't work</h1>
        <p className="muted">{sessionError}</p>
        <button onClick={reset}>Try again</button>
      </div>
    );
  }

  if (!session || !menu) return <div className="centred"><p>Loading menu…</p></div>;

  return (
    <div className="app">
      <header className="app-header">
        <div>
          <span className="table-code">{session.tableCode}</span>
          <h1>Order at your table</h1>
        </div>
        <span className={`ws ws-${status}`} title={`Live updates: ${status}`} aria-live="polite">
          {status === 'connected' ? 'Live' : status === 'connecting' ? 'Connecting' : 'Offline'}
        </span>
      </header>

      {flash && <div className="flash" role="status">{flash}</div>}
      {error && (
        <div className="error" role="alert">
          {error}
          <button className="link" onClick={() => setError(null)}>dismiss</button>
        </div>
      )}

      <nav className="tabs">
        <button className={tab === 'menu' ? 'active' : ''} onClick={() => setTab('menu')}>
          Menu
        </button>
        <button className={tab === 'orders' ? 'active' : ''} onClick={() => setTab('orders')}>
          Your orders{orders.length > 0 && <span className="badge">{orders.length}</span>}
        </button>
      </nav>

      <main>
        {tab === 'menu' ? (
          <MenuList menu={menu} onSelect={setOpenItem} />
        ) : (
          <>
            <OrderTracker orders={orders} onPay={openBill} />
            {bill && (
              <BillPanel
                bill={bill}
                token={session.token}
                onChange={setBill}
                onClose={() => setBill(null)}
                onError={setError}
              />
            )}
          </>
        )}
      </main>

      {openItem && (
        <ItemSheet item={openItem} onAdd={addToCart} onClose={() => setOpenItem(null)} />
      )}

      {cart.length > 0 && tab === 'menu' && (
        <CartBar
          lines={cart}
          total={formatCents(cartTotal(cart))}
          onChangeQuantity={changeQuantity}
          onPlace={placeOrder}
        />
      )}
    </div>
  );
}
