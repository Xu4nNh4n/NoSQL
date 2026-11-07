import { useEffect, useMemo, useRef, useState, type KeyboardEvent } from 'react';
import { usePrintReceipt } from '../hooks/usePrintReceipt';
import { ReceiptView } from '../components/sales/Receipt';
import { Minus, Pause, Plus, Search, ShoppingCart, Sparkles, Trash2, TrendingUp } from 'lucide-react';
import { api, ApiError, errorMessage, newRequestKey } from '../api/client';
import { useLoad } from '../hooks/useLoad';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import { Modal } from '../components/ui/Modal';
import { useToast } from '../components/ui/Toast';
import { inputClass } from '../components/ui/Table';
import { formatDateTime, formatVND } from '../utils/formatters';
import type { HeldCart, PaymentMethod, PosProduct, PricedCart, SalesOrder, Suggestions, TopSales } from '../types';

type CartItem = { productId: string; quantity: number };

export function PosPage() {
  const toast = useToast();
  const products = useLoad(() => api.get<PosProduct[]>('/products/active'), []);
  const top = useLoad(() => api.get<TopSales>('/stats/top-sales/today'), []);
  const [keyword, setKeyword] = useState('');
  const [cart, setCart] = useState<CartItem[]>([]);
  const [quote, setQuote] = useState<PricedCart | null>(null);
  const [quoting, setQuoting] = useState(false);
  const [note, setNote] = useState('');
  const [method, setMethod] = useState<PaymentMethod>('CASH');
  const [txRef, setTxRef] = useState('');
  const [paying, setPaying] = useState(false);
  const [receipt, setReceipt] = useState<SalesOrder | null>(null);
  const printer = usePrintReceipt();
  const [suggest, setSuggest] = useState<Suggestions | null>(null);
  const [heldOpen, setHeldOpen] = useState(false);
  const [held, setHeld] = useState<HeldCart[]>([]);
  const requestKey = useRef(newRequestKey());

  const shown = useMemo(() => {
    const k = keyword.trim().toLowerCase();
    const list = products.data ?? [];
    return (k ? list.filter(p => p.name.toLowerCase().includes(k) || p.sku.toLowerCase().includes(k)) : list).slice(0, 60);
  }, [products.data, keyword]);

  // Tính giá lại mỗi khi giỏ đổi (khuyến mãi tự áp ở server).
  useEffect(() => {
    requestKey.current = newRequestKey();
    if (cart.length === 0) { setQuote(null); return; }
    const t = setTimeout(async () => {
      setQuoting(true);
      try { setQuote(await api.post<PricedCart>('/orders/quote', { items: cart })); }
      catch (e) { toast.error(errorMessage(e)); }
      finally { setQuoting(false); }
    }, 250);
    return () => clearTimeout(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [cart]);

  const add = async (productId: string, qty = 1) => {
    setCart(c => c.some(i => i.productId === productId)
      ? c.map(i => i.productId === productId ? { ...i, quantity: i.quantity + qty } : i)
      : [...c, { productId, quantity: qty }]);
    api.get<Suggestions>(`/products/${productId}/also-bought`).then(setSuggest).catch(() => setSuggest(null));
  };
  const setQty = (productId: string, quantity: number) =>
    setCart(c => quantity <= 0 ? c.filter(i => i.productId !== productId) : c.map(i => i.productId === productId ? { ...i, quantity } : i));

  const onScan = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key !== 'Enter') return;
    const exact = (products.data ?? []).find(p => p.sku.toLowerCase() === keyword.trim().toLowerCase());
    if (exact) { void add(exact.id); setKeyword(''); }
    else if (shown.length === 1) { void add(shown[0].id); setKeyword(''); }
  };


  const reset = () => { setCart([]); setQuote(null); setNote(''); setTxRef(''); setMethod('CASH'); setSuggest(null); };

  const pay = async () => {
    if (!quote) return;
    if (method === 'BANK_TRANSFER' && !txRef.trim()) { toast.warning('Nhập mã giao dịch chuyển khoản'); return; }
    setPaying(true);
    try {
      const order = await api.post<SalesOrder>('/orders', {
        requestKey: requestKey.current, items: cart,
        paymentMethod: method, transactionRef: method === 'BANK_TRANSFER' ? txRef.trim() : null, quoteHash: quote.quoteHash,
      });
      setReceipt(order);
      reset();
      void products.reload();
      void top.reload();
    } catch (e) {
      if (e instanceof ApiError && e.status === 409) {
        toast.warning('Giá hoặc khuyến mãi vừa thay đổi, đã tính lại giỏ hàng');
        setQuote(await api.post<PricedCart>('/orders/quote', { items: cart }));
      } else toast.error(errorMessage(e));
    } finally { setPaying(false); }
  };

  const hold = async () => {
    try {
      await api.post('/pos/held', { note: note.trim() || null, items: cart });
      toast.success('Đã treo đơn (tự hết hạn sau 30 phút)');
      reset();
    } catch (e) { toast.error(errorMessage(e)); }
  };
  const openHeld = async () => {
    try { setHeld(await api.get<HeldCart[]>('/pos/held')); setHeldOpen(true); }
    catch (e) { toast.error(errorMessage(e)); }
  };
  const resume = async (h: HeldCart) => {
    try {
      const c = await api.get<HeldCart>(`/pos/held/${h.holdId}`);
      setCart(c.items);
      setHeldOpen(false);
      toast.info('Đã mở lại đơn treo, giá được tính lại');
    } catch (e) { toast.error(errorMessage(e)); }
  };

  return (
    <div className="grid gap-4 xl:grid-cols-[1fr_440px]">
      <div className="space-y-4">
        <Card>
          <div className="relative">
            <Search className="pointer-events-none absolute left-3 top-3 h-4 w-4 text-gray-400" />
            <input autoFocus className={`${inputClass} pl-9`} placeholder="Quét mã / gõ tên hoặc SKU rồi Enter"
              value={keyword} onChange={e => setKeyword(e.target.value)} onKeyDown={onScan} />
          </div>
          <div className="mt-4 grid grid-cols-2 gap-2 sm:grid-cols-3 lg:grid-cols-4">
            {products.loading && <p className="text-sm text-gray-500">Đang tải sản phẩm…</p>}
            {shown.map(p => (
              <button key={p.id} type="button" onClick={() => add(p.id)} disabled={p.stockQuantity <= 0}
                className="rounded-lg border border-gray-200 bg-white p-3 text-left hover:border-blue-400 hover:bg-blue-50 disabled:opacity-50">
                <p className="line-clamp-2 text-sm font-medium text-gray-900">{p.name}</p>
                <p className="mt-1 text-xs text-gray-500">{p.sku} · còn {p.stockQuantity}</p>
                <p className="mt-1 text-sm font-semibold text-blue-700">{formatVND(p.price)}</p>
              </button>
            ))}
          </div>
        </Card>
        <div className="grid gap-4 md:grid-cols-2">
          <Card title="Gợi ý mua kèm (Neo4j)" action={<Sparkles className="h-4 w-4 text-amber-500" />}>
            {!suggest ? <p className="text-sm text-gray-500">Thêm một sản phẩm để xem gợi ý.</p>
              : !suggest.available ? <p className="text-sm text-gray-500">Gợi ý tạm thời không khả dụng.</p>
              : suggest.items.length === 0 ? <p className="text-sm text-gray-500">Chưa đủ dữ liệu bán kèm.</p>
              : <div className="flex flex-wrap gap-2">{suggest.items.map(s => (
                  <Button key={s.productId} size="sm" variant="outline" onClick={() => add(s.productId)}>
                    + {s.name} <span className="text-xs text-gray-400">({s.times} đơn)</span>
                  </Button>))}</div>}
          </Card>
          <Card title="Top bán chạy hôm nay (Redis)" action={<TrendingUp className="h-4 w-4 text-blue-600" />}>
            {!top.data?.available ? <p className="text-sm text-gray-500">Tạm thời không khả dụng.</p>
              : top.data.items.length === 0 ? <p className="text-sm text-gray-500">Chưa có đơn hôm nay.</p>
              : <ol className="space-y-1 text-sm">{top.data.items.map((t, i) => (
                  <li key={t.productId} className="flex justify-between"><span>{i + 1}. {t.name}</span><b>{t.quantity}</b></li>))}</ol>}
          </Card>
        </div>
      </div>

      <Card title="Giỏ hàng" action={<Button size="sm" variant="ghost" leftIcon={<Pause className="h-4 w-4" />} onClick={openHeld}>Đơn treo</Button>}>
        {cart.length === 0 ? (
          <div className="py-10 text-center text-gray-400"><ShoppingCart className="mx-auto mb-2 h-8 w-8" />Giỏ trống</div>
        ) : (
          <div className="space-y-2">
            {(quote?.lines ?? []).map(l => (
              <div key={l.lineNo} className={`rounded-md border p-2 ${l.gift ? 'border-amber-200 bg-amber-50' : 'border-gray-200'}`}>
                <div className="flex items-start justify-between gap-2">
                  <div>
                    <p className="text-sm font-medium">{l.gift && '🎁 '}{l.name}</p>
                    <p className="text-xs text-gray-500">{formatVND(l.unitPrice)} × {l.quantity}</p>
                    {l.promotion && <p className="text-xs text-emerald-700">{l.promotion.name}: −{formatVND(l.promotion.discount)}</p>}
                  </div>
                  <div className="text-right">
                    {l.grossAmount !== l.netAmount && <p className="text-xs text-gray-400 line-through">{formatVND(l.grossAmount)}</p>}
                    <p className="text-sm font-semibold">{formatVND(l.netAmount)}</p>
                  </div>
                </div>
                {!l.gift && (
                  <div className="mt-2 flex items-center gap-1">
                    <Button size="sm" variant="outline" aria-label="Giảm" onClick={() => setQty(l.productId, (cart.find(c => c.productId === l.productId)?.quantity ?? 1) - 1)}><Minus className="h-3 w-3" /></Button>
                    <input className="h-8 w-14 rounded border border-gray-300 text-center text-sm" type="number" min={1}
                      value={cart.find(c => c.productId === l.productId)?.quantity ?? l.quantity}
                      onChange={e => setQty(l.productId, Number(e.target.value))} />
                    <Button size="sm" variant="outline" aria-label="Tăng" onClick={() => add(l.productId)}><Plus className="h-3 w-3" /></Button>
                    <Button size="sm" variant="ghost" aria-label="Xóa" onClick={() => setQty(l.productId, 0)}><Trash2 className="h-4 w-4 text-rose-500" /></Button>
                  </div>
                )}
              </div>
            ))}
            {quoting && <p className="text-xs text-gray-400">Đang tính giá…</p>}
            {quote?.warnings.map(w => <p key={w} className="text-xs text-amber-700">⚠ {w}</p>)}
          </div>
        )}

        <div className="mt-4 space-y-3 border-t border-gray-100 pt-4">
          <div className="flex gap-2">
            <input className={inputClass} placeholder="Ghi chú khi treo đơn (vd: chị áo xanh)" maxLength={100} value={note} onChange={e => setNote(e.target.value)} />
          </div>
          {quote && (
            <dl className="space-y-1 text-sm">
              <div className="flex justify-between"><dt>Tạm tính</dt><dd>{formatVND(quote.grossTotal)}</dd></div>
              {quote.orderPromotion && <div className="flex justify-between text-emerald-700"><dt>{quote.orderPromotion.name}</dt><dd>−{formatVND(quote.orderPromotion.discount)}</dd></div>}
              <div className="flex justify-between text-emerald-700"><dt>Tổng giảm</dt><dd>−{formatVND(quote.discountTotal)}</dd></div>
              <div className="flex justify-between text-lg font-bold"><dt>Khách trả</dt><dd>{formatVND(quote.total)}</dd></div>
            </dl>
          )}
          <div className="grid grid-cols-2 gap-2">
            {(['CASH', 'BANK_TRANSFER'] as PaymentMethod[]).map(m => (
              <Button key={m} variant={method === m ? 'primary' : 'outline'} onClick={() => setMethod(m)}>{m === 'CASH' ? 'Tiền mặt' : 'Chuyển khoản'}</Button>
            ))}
          </div>
          {method === 'BANK_TRANSFER' && <input className={inputClass} placeholder="Mã giao dịch ngân hàng" value={txRef} onChange={e => setTxRef(e.target.value)} />}
          <div className="grid grid-cols-[1fr_2fr] gap-2">
            <Button variant="outline" disabled={cart.length === 0} onClick={hold}>Treo đơn</Button>
            <Button size="lg" disabled={!quote || quoting} isLoading={paying} onClick={pay}>Thu tiền {quote ? formatVND(quote.total) : ''}</Button>
          </div>
        </div>
      </Card>

      <Modal isOpen={heldOpen} onClose={() => setHeldOpen(false)} title="Đơn đang treo (Redis, 30 phút)">
        {held.length === 0 ? <p className="text-sm text-gray-500">Không có đơn treo.</p> : (
          <ul className="space-y-2">{held.map(h => (
            <li key={h.holdId} className="flex items-center justify-between rounded border border-gray-200 p-2 text-sm">
              <div><p className="font-medium">{h.note || 'Đơn treo'} · {h.items.length} món</p>
                <p className="text-xs text-gray-500">Treo lúc {formatDateTime(h.heldAt)} · hết hạn {formatDateTime(h.expiresAt)}</p></div>
              <div className="flex gap-1">
                <Button size="sm" onClick={() => resume(h)}>Mở lại</Button>
                <Button size="sm" variant="ghost" onClick={async () => { await api.delete(`/pos/held/${h.holdId}`); setHeld(x => x.filter(y => y.holdId !== h.holdId)); }}>Xóa</Button>
              </div>
            </li>))}</ul>)}
      </Modal>

      <Modal isOpen={!!receipt} onClose={() => setReceipt(null)} title={`Hóa đơn ${receipt?.orderCode ?? ''}`}
        footer={<><Button variant="outline" onClick={() => receipt && printer.print(receipt)}>In hóa đơn</Button><Button onClick={() => setReceipt(null)}>Đơn mới</Button></>}>
        {receipt && <ReceiptView order={receipt} />}
      </Modal>
      {printer.portal}
    </div>
  );
}
