import React, { createContext, useContext, useState, useMemo, useEffect, useRef, useCallback } from 'react';
import { useAuth } from './AuthContext';
import type { Product, PaymentMethod, PosQuote } from '../types';
import { api } from '../api/client';
import { normalizeProduct } from '../types';

export interface CartItem {
  product: Product;
  quantity: number;
}

interface CartContextType {
  items: CartItem[];
  customerName: string;
  setCustomerName: (name: string) => void;
  customerPhone: string;
  setCustomerPhone: (phone: string) => void;
  paymentMethod: PaymentMethod;
  setPaymentMethod: (method: PaymentMethod) => void;
  note: string;
  setNote: (note: string) => void;
  addItem: (product: Product, quantity?: number) => boolean;
  updateQuantity: (productId: number, quantity: number) => void;
  removeItem: (productId: number) => void;
  clearCart: () => void;
  refreshProducts: (products: Product[]) => void;
  subtotal: number;
  total: number;
  discountAmount: number;
  quote: PosQuote | null;
  quoteLoading: boolean;
  quoteError: string;
  acceptQuote: (quote: PosQuote) => void;
  retryQuote: () => void;
  itemCount: number;
}

const CartContext = createContext<CartContextType | undefined>(undefined);

export const CartProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { user } = useAuth();
  const previousOwner = useRef(user?.id);
  const [items, setItems] = useState<CartItem[]>([]);
  const [customerName, setCustomerName] = useState('');
  const [customerPhone, setCustomerPhone] = useState('');
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>('CASH');
  const [note, setNote] = useState<string>('Bán tại quầy');
  const [priced, setPriced] = useState<{ key: string; quote: PosQuote } | null>(null);
  const [quoteLoading, setQuoteLoading] = useState(false);
  const [quoteError, setQuoteError] = useState('');
  const [quoteAttempt, setQuoteAttempt] = useState(0);
  const quoteKey = JSON.stringify(items.map(item => ({ productId: item.product.id, quantity: item.quantity })));
  const quote = priced?.key === quoteKey && items.length > 0 ? priced.quote : null;
  const acceptQuote = (value: PosQuote) => setPriced({ key: quoteKey, quote: value });
  const retryQuote = () => setQuoteAttempt(value => value + 1);

  useEffect(() => {
    let cancelled = false;
    setQuoteError('');
    setPriced(null);
    if (quoteKey === '[]') { setQuoteLoading(false); return; }
    setQuoteLoading(true);
    api.post<PosQuote>('/orders/quote', { items: JSON.parse(quoteKey) }).then(value => {
      if (!cancelled) setPriced({ key: quoteKey, quote: value });
    }).catch((err: unknown) => {
      if (!cancelled) setQuoteError(err instanceof Error ? err.message : 'Không thể lấy giá POS');
    }).finally(() => { if (!cancelled) setQuoteLoading(false); });
    return () => { cancelled = true; };
  }, [quoteKey, quoteAttempt, user?.id]);

  useEffect(() => {
    // Clear the POS cart whenever the signed-in employee changes.
    if (previousOwner.current !== user?.id) {
      setItems([]);
      setCustomerName('');
      setCustomerPhone('');
      setNote('Bán tại quầy');
      setPaymentMethod('CASH');
    }
    previousOwner.current = user?.id;
  }, [user?.id]);

  const addItem = (product: Product, quantity: number = 1): boolean => {
    const norm = normalizeProduct(product);
    const prodId = norm.id || norm.productId || 0;
    const available = norm.availableQuantity ?? norm.totalQuantity ?? 0;
    if (!norm.active || available <= 0) {
      return false;
    }

    setItems((prev) => {
      const existingIndex = prev.findIndex(
        (item) => (item.product.id || item.product.productId) === prodId
      );
      if (existingIndex > -1) {
        const currentQty = prev[existingIndex].quantity;
        const newQty = Math.min(currentQty + quantity, available);
        const updated = [...prev];
        updated[existingIndex] = { ...updated[existingIndex], quantity: newQty };
        return updated;
      }
      return [...prev, { product: norm, quantity: Math.min(quantity, available) }];
    });
    return true;
  };

  const updateQuantity = (productId: number, quantity: number) => {
    if (quantity <= 0) {
      removeItem(productId);
      return;
    }
    setItems((prev) =>
      prev.map((item) => {
        const id = item.product.id || item.product.productId;
        if (id === productId) {
          const available = item.product.availableQuantity ?? item.product.totalQuantity ?? 0;
          if (!item.product.active || available <= 0) return item;
          return { ...item, quantity: Math.min(quantity, available) };
        }
        return item;
      })
    );
  };

  const removeItem = (productId: number) => {
    setItems((prev) =>
      prev.filter((item) => (item.product.id || item.product.productId) !== productId)
    );
  };

  const clearCart = () => {
    setItems([]);
    setNote('Bán tại quầy');
    setPaymentMethod('CASH');
    setCustomerName('');
    setCustomerPhone('');
  };

  const refreshProducts = useCallback((products: Product[]) => {
    const latest = new Map(products.map(product => [product.id, product]));
    // Preserve quantities so the cashier can resolve shortages explicitly.
    setItems(previous => previous.map(item => {
      const product = latest.get(item.product.id);
      return product ? { ...item, product } : item;
    }));
  }, []);

  const subtotal = quote?.subtotal ?? 0;
  const total = quote?.totalAmount ?? 0;
  const discountAmount = quote?.discountAmount ?? 0;

  const itemCount = useMemo(() => {
    return items.reduce((sum, item) => sum + item.quantity, 0);
  }, [items]);

  return (
    <CartContext.Provider
      value={{
        items,
        customerName,
        setCustomerName,
        customerPhone,
        setCustomerPhone,
        paymentMethod,
        setPaymentMethod,
        note,
        setNote,
        addItem,
        updateQuantity,
        removeItem,
        clearCart,
        refreshProducts,
        subtotal,
        total,
        discountAmount,
        quote,
        quoteLoading,
        quoteError,
        acceptQuote,
        retryQuote,
        itemCount,
      }}
    >
      {children}
    </CartContext.Provider>
  );
};

export function useCart() {
  const context = useContext(CartContext);
  if (!context) {
    throw new Error('useCart must be used within a CartProvider');
  }
  return context;
}
