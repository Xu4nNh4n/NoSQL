import React, { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Search,
  Barcode,
  ShoppingCart,
  Trash2,
  Plus,
  Minus,
  Banknote,
  QrCode,
  Printer,
  X,
  RotateCcw,
} from 'lucide-react';
import { api, ApiError } from '../api/client';
import { useCart } from '../contexts/CartContext';
import type { Product, SalesOrder, CreateOrderRequest, PosQuote } from '../types';
import { normalizeProduct, normalizeOrder } from '../types';
import { formatVND } from '../utils/formatters';
import { OrderReceipt } from '../components/OrderReceipt';
import { Button } from '../components/ui/Button';
import { Input } from '../components/ui/Input';
import { Modal } from '../components/ui/Modal';
import { useToast } from '../components/ui/Toast';

export const PosPage: React.FC = () => {
  const navigate = useNavigate();
  const [products, setProducts] = useState<Product[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [searchKeyword, setSearchKeyword] = useState('');
  const [barcodeInput, setBarcodeInput] = useState('');
  const [selectedCategory, setSelectedCategory] = useState<string>('ALL');
  const [categories, setCategories] = useState<string[]>([]);

  // Checkout and Receipt
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [lastOrder, setLastOrder] = useState<SalesOrder | null>(null);
  const [isReceiptOpen, setIsReceiptOpen] = useState(false);
  const [paymentReference, setPaymentReference] = useState('');
  const checkoutLock = useRef(false);

  const barcodeInputRef = useRef<HTMLInputElement>(null);

  const {
    items,
    customerName,
    setCustomerName,
    customerPhone,
    setCustomerPhone,
    paymentMethod,
    setPaymentMethod,
    note,
    addItem,
    updateQuantity,
    removeItem,
    clearCart,
    subtotal,
    total,
    discountAmount,
    quote,
    quoteLoading,
    quoteError,
    acceptQuote,
    retryQuote,
    itemCount,
  } = useCart();

  const { success, error, warning } = useToast();

  const loadData = async () => {
    setIsLoading(true);
    try {
      const rawProds = await api.get<unknown[]>('/products/active');
      const prodList = rawProds.map(normalizeProduct);
      setProducts(prodList);
      setCategories([...new Set(prodList.map((p: Product) => p.categoryName)
        .filter((name): name is string => Boolean(name)))].sort());
    } catch {
      error('Lỗi khi tải danh mục sản phẩm cho thu ngân');
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    loadData();
    barcodeInputRef.current?.focus();
  }, []);

  // Handle barcode scanner Enter press
  const handleBarcodeSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!barcodeInput.trim()) return;

    const term = barcodeInput.trim().toLowerCase();
    const matched = products.find(
      (p: Product) =>
        (p.barcode && p.barcode.toLowerCase() === term) ||
        (p.skuCode && p.skuCode.toLowerCase() === term)
    );

    if (matched) {
      const added = addItem(matched, 1);
      if (added) {
        success(`Đã thêm: ${matched.name || matched.productName}`);
        setBarcodeInput('');
      } else {
        warning(`Sản phẩm [${matched.name || matched.productName}] đã hết tồn khả dụng!`);
      }
    } else {
      error(`Không tìm thấy mã vạch / SKU: "${barcodeInput}"`);
    }
  };

  // Perform real checkout via Spring Boot /api/orders
  const handleCheckout = async () => {
    if (checkoutLock.current || !quote || quoteLoading) return;
    if (items.length === 0) {
      warning('Giỏ hàng đang trống!');
      return;
    }

    if (paymentMethod === 'BANK_TRANSFER' && !paymentReference.trim()) {
      warning('Nhập mã giao dịch chuyển khoản đã xác nhận thành công.');
      return;
    }
    checkoutLock.current = true;
    setIsSubmitting(true);
    try {
      const orderItems = items.map(i => ({ productId: i.product.id, quantity: i.quantity }));
      const latest = await api.post<PosQuote>('/orders/quote', { items: orderItems });
      if (latest.quoteToken !== quote.quoteToken) {
        if (!window.confirm(`Giá hoặc khuyến mãi đã thay đổi. Tổng cũ: ${formatVND(quote.totalAmount)}. Tổng mới: ${formatVND(latest.totalAmount)}. Xác nhận giá mới và thanh toán?`)) {
          acceptQuote(latest);
          return;
        }
      }
      acceptQuote(latest);
      const payload: CreateOrderRequest = {
        quoteToken: latest.quoteToken,
        paymentReference: paymentMethod === 'BANK_TRANSFER' ? paymentReference.trim() : undefined,
        customerName: customerName.trim() || undefined,
        customerPhone: customerPhone.trim() || undefined,
        orderType: 'IN_STORE',
        paymentMethod: paymentMethod,
        note: note || 'Bán tại quầy thu ngân',
        items: items.map((i) => ({
          productId: i.product.id || i.product.productId || 0,
          quantity: i.quantity,
        })),
      };

      const createdOrder = await api.post<any>('/orders', payload);
      setLastOrder(normalizeOrder(createdOrder));
      setIsReceiptOpen(true);
      clearCart();
      setPaymentReference('');
      success('Thanh toán đơn hàng thành công! Đã tự động xuất kho theo FEFO.');
      loadData(); // Refresh product stock
    } catch (err: unknown) {
      if (err instanceof ApiError && err.status === 409) {
        const data = err.data as { code?: string; quote?: PosQuote };
        if (data?.code === 'PRICE_CHANGED' && data.quote) {
          // Keep the displayed quote until the cashier explicitly accepts the new one.
          if (window.confirm(`${err.message} Tổng mới: ${formatVND(data.quote.totalAmount)}. Chấp nhận giá mới? (Bấm Thanh toán lần nữa để hoàn tất.)`)) {
            acceptQuote(data.quote);
          }
        }
      }
      if (err instanceof Error) error(err.message);
    } finally {
      checkoutLock.current = false;
      setIsSubmitting(false);
    }
  };

  const handlePrintReceipt = () => {
    window.print();
  };

  // Filter products by search and category
  const filteredProducts = products.filter((p: Product) => {
    const pName = p.name || p.productName || '';
    const pSku = p.skuCode || '';
    const pBarcode = p.barcode || '';
    const kw = searchKeyword.toLowerCase();

    const matchesSearch =
      pName.toLowerCase().includes(kw) ||
      pSku.toLowerCase().includes(kw) ||
      pBarcode.toLowerCase().includes(kw);

    const matchesCat =
      selectedCategory === 'ALL' ||
      p.categoryName === selectedCategory;

    return matchesSearch && matchesCat;
  });

  return (
    <div inert={isSubmitting} aria-busy={isSubmitting} className={`grid grid-cols-1 lg:grid-cols-12 gap-4 lg:gap-5 min-h-[calc(100vh-120px)] lg:h-[calc(100vh-100px)] lg:min-h-0 ${isSubmitting ? 'opacity-75' : ''}`}>
      {/* LEFT COLUMN: Catalog & Fast Barcode Entry (7 cols) */}
      <div className="lg:col-span-7 xl:col-span-8 flex flex-col min-h-[60vh] lg:h-full bg-white rounded-lg border border-gray-200 overflow-hidden">
        {/* Search & Barcode Scan Bar */}
        <div className="p-3.5 border-b border-gray-100 space-y-3 bg-white">
          <div className="flex items-center justify-between gap-2">
            <span className="text-xs font-semibold text-gray-700">Bán hàng tại quầy</span>
            <Button size="sm" variant="outline" leftIcon={<RotateCcw className="w-3.5 h-3.5" />}
              onClick={() => navigate('/orders')}>
              Tra hóa đơn / Trả hàng
            </Button>
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-12 gap-2.5">
            {/* Barcode scanner input */}
            <form onSubmit={handleBarcodeSubmit} className="sm:col-span-5">
              <div className="relative">
                <Barcode className="w-4 h-4 text-blue-600 absolute left-3 top-1/2 -translate-y-1/2" />
                <input
                  ref={barcodeInputRef}
                  type="text"
                  placeholder="Quét mã vạch / SKU (Enter)..."
                  value={barcodeInput}
                  onChange={(e) => setBarcodeInput(e.target.value)}
                  className="w-full pl-9 pr-3 py-2 text-xs font-mono rounded-lg border border-blue-300 focus:outline-none focus:ring-2 focus:ring-blue-500 bg-blue-50/20"
                />
              </div>
            </form>

            {/* Keyword search input */}
            <div className="sm:col-span-7">
              <Input
                placeholder="Tìm tên sản phẩm..."
                leftIcon={<Search className="w-4 h-4 text-gray-400" />}
                value={searchKeyword}
                onChange={(e) => setSearchKeyword(e.target.value)}
              />
            </div>
          </div>

          {/* Category Tabs */}
          <div className="flex items-center gap-1.5 overflow-x-auto pb-1 scrollbar-none text-xs">
            <button
              type="button"
              onClick={() => setSelectedCategory('ALL')}
              className={`px-3 py-1.5 rounded-lg font-medium whitespace-nowrap transition-colors cursor-pointer ${
                selectedCategory === 'ALL'
                  ? 'bg-blue-600 text-white shadow-2xs'
                  : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
              }`}
            >
              Tất cả ngành hàng
            </button>
            {categories.map((category: string) => (
              <button
                key={category}
                type="button"
                onClick={() => setSelectedCategory(category)}
                className={`px-3 py-1.5 rounded-lg font-medium whitespace-nowrap transition-colors cursor-pointer ${
                  selectedCategory === category
                    ? 'bg-blue-600 text-white shadow-2xs'
                    : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
                }`}
              >
                {category}
              </button>
            ))}
          </div>
        </div>

        {/* Product Grid */}
        <div className="flex-1 overflow-y-auto p-4 bg-gray-50/40">
          {isLoading ? (
            <div className="h-full flex items-center justify-center text-xs text-gray-400">
              Đang tải danh mục hàng...
            </div>
          ) : filteredProducts.length === 0 ? (
            <div className="h-full flex items-center justify-center text-xs text-gray-400">
              Không tìm thấy sản phẩm phù hợp
            </div>
          ) : (
        <div className="grid grid-cols-2 sm:grid-cols-3 xl:grid-cols-4 gap-3">
              {filteredProducts.map((p: Product) => {
                const pId = p.id || p.productId || 0;
                const pName = p.name || p.productName || 'Sản phẩm';
                const pPrice = p.sellingPrice ?? p.price ?? 0;
                const available = p.availableQuantity ?? p.totalQuantity ?? p.stockQuantity ?? 0;
                const isOutOfStock = available <= 0;

                return (
                  <button
                    key={pId}
                    type="button"
                    disabled={isOutOfStock}
                    onClick={() => addItem(p, 1)}
                    className={`p-3 rounded-xl border text-left flex flex-col justify-between transition-all cursor-pointer ${
                      isOutOfStock
                        ? 'bg-gray-100 border-gray-200 opacity-60 cursor-not-allowed'
                        : 'bg-white border-gray-200 hover:border-blue-500 hover:shadow-xs active:scale-[0.98]'
                    }`}
                  >
                    <div>
                      <div className="flex items-center justify-between gap-1 mb-1">
                        <span className="font-mono text-[10px] text-gray-400 truncate">
                          {p.skuCode}
                        </span>
                        <span
                          className={`text-[10px] font-semibold px-1.5 py-0.2 rounded ${
                            isOutOfStock
                              ? 'bg-rose-100 text-rose-700'
                              : 'bg-blue-50 text-blue-700'
                          }`}
                        >
                          {isOutOfStock ? 'Hết hàng' : `Còn ${available}`}
                        </span>
                      </div>
                      <h4 className="text-xs font-semibold text-gray-900 line-clamp-2 leading-tight">
                        {pName}
                      </h4>
                    </div>

                    <div className="mt-3 flex items-center justify-between">
                      <span className="text-xs font-bold text-blue-700">
                        <span className="font-normal text-gray-500">Giá gốc </span>
                        {formatVND(pPrice)}
                      </span>
                      <span className="text-[10px] text-gray-400">/{p.unit || 'Cái'}</span>
                    </div>
                  </button>
                );
              })}
            </div>
          )}
        </div>
      </div>

      {/* RIGHT COLUMN: Active Cart & Checkout Panel (5 cols) */}
      <div className="lg:col-span-5 xl:col-span-4 flex flex-col min-h-[420px] lg:min-h-0 lg:h-full bg-white rounded-lg border border-gray-200 overflow-hidden">
        {/* Cart Header */}
        <div className="px-4 py-3 border-b border-gray-100 flex items-center justify-between bg-white">
          <div className="flex items-center gap-2">
            <ShoppingCart className="w-4 h-4 text-blue-600" />
            <h3 className="text-sm font-bold text-gray-900">Giỏ hàng thanh toán</h3>
            <span className="px-2 py-0.5 rounded-full text-xs font-bold bg-blue-100 text-blue-800">
              {itemCount}
            </span>
          </div>
          {items.length > 0 && (
            <button
              type="button"
              onClick={clearCart}
              className="text-xs text-rose-600 hover:underline flex items-center gap-1 cursor-pointer"
            >
              <Trash2 className="w-3.5 h-3.5" />
              Xóa sạch
            </button>
          )}
        </div>

        {/* Optional customer snapshot */}
        <div className="p-3 border-b border-gray-100 bg-gray-50/50 space-y-2">
          <Input value={customerName} onChange={(e) => setCustomerName(e.target.value)} placeholder="Tên khách (không bắt buộc)" />
          <Input value={customerPhone} onChange={(e) => setCustomerPhone(e.target.value)} placeholder="Số điện thoại (không bắt buộc)" />
        </div>

        {/* Cart Item List */}
        <div className="flex-1 overflow-y-auto p-3 space-y-2 divide-y divide-gray-100">
          {items.length === 0 ? (
            <div className="h-full flex flex-col items-center justify-center text-gray-400 py-12 text-xs">
              <ShoppingCart className="w-10 h-10 stroke-1 text-gray-300 mb-2" />
              <p>Giỏ hàng đang trống</p>
              <p className="text-[11px] text-gray-400 mt-0.5">
                Quét mã vạch hoặc chọn sản phẩm bên trái
              </p>
            </div>
          ) : (
            items.map((item) => {
              const pId = item.product.id || item.product.productId || 0;
              const price = quote?.items.find(line => line.productId === pId);
              const pPrice = price?.netUnitPrice;
              const pName = item.product.name || item.product.productName || 'Sản phẩm';
              return (
                <div key={pId} className="pt-2 first:pt-0 flex items-center justify-between gap-2">
                  <div className="flex-1 min-w-0">
                    <h5 className="text-xs font-semibold text-gray-900 truncate">
                      {pName}
                    </h5>
                    {price?.promotionSnapshot && <p className="text-[11px] text-emerald-700">
                      {price.promotionSnapshot.name} · −{price.promotionSnapshot.percent}%
                      <span className="ml-1 text-gray-500 line-through">{formatVND(price.originalUnitPrice)}</span>
                    </p>}
                    <div className="flex items-center gap-2 mt-0.5 text-[11px] text-gray-500">
                      <span>{pPrice == null ? 'Đang lấy giá…' : formatVND(pPrice)}</span>
                      <span>×</span>
                      <span className="font-semibold text-gray-800">{item.quantity}</span>
                      <span>=</span>
                      <span className="font-bold text-blue-700">
                        {price ? formatVND(price.subTotal) : '—'}
                      </span>
                    </div>
                  </div>

                  {/* Quantity Controls */}
                  <div className="flex items-center gap-1">
                    <button
                      type="button"
                      onClick={() => updateQuantity(pId, item.quantity - 1)}
                      className="w-6 h-6 rounded border border-gray-200 flex items-center justify-center hover:bg-gray-100 text-gray-600 cursor-pointer"
                    >
                      <Minus className="w-3 h-3" />
                    </button>
                    <span className="w-7 text-center font-bold text-xs text-gray-900">
                      {item.quantity}
                    </span>
                    <button
                      type="button"
                      onClick={() => updateQuantity(pId, item.quantity + 1)}
                      className="w-6 h-6 rounded border border-gray-200 flex items-center justify-center hover:bg-gray-100 text-gray-600 cursor-pointer"
                    >
                      <Plus className="w-3 h-3" />
                    </button>
                    <button
                      type="button"
                      onClick={() => removeItem(pId)}
                      className="p-1 text-gray-300 hover:text-rose-600 cursor-pointer ml-1"
                    >
                      <X className="w-3.5 h-3.5" />
                    </button>
                  </div>
                </div>
              );
            })
          )}
        </div>

        {/* Payment Methods & Totals */}
        <div className="p-4 border-t border-gray-200 bg-gray-50/70 space-y-3">
          {/* Payment Method Selector */}
          <div>
            <span className="block text-[11px] font-semibold text-gray-500 uppercase tracking-wide mb-1.5">
              Phương thức thanh toán
            </span>
            <div className="grid grid-cols-2 gap-1.5">
              <button
                type="button"
                onClick={() => setPaymentMethod('CASH')}
                className={`p-2 rounded-lg border text-xs font-medium flex flex-col items-center gap-1 transition-colors cursor-pointer ${
                  paymentMethod === 'CASH'
                    ? 'border-blue-600 bg-blue-50 text-blue-800 font-bold'
                    : 'border-gray-200 bg-white text-gray-600 hover:bg-gray-50'
                }`}
              >
                <Banknote className="w-4 h-4 text-blue-600" />
                <span>Tiền mặt</span>
              </button>
              <button
                type="button"
                onClick={() => setPaymentMethod('BANK_TRANSFER')}
                className={`p-2 rounded-lg border text-xs font-medium flex flex-col items-center gap-1 transition-colors cursor-pointer ${
                  paymentMethod === 'BANK_TRANSFER'
                    ? 'border-blue-600 bg-blue-50 text-blue-800 font-bold'
                    : 'border-gray-200 bg-white text-gray-600 hover:bg-gray-50'
                }`}
              >
                <QrCode className="w-4 h-4 text-blue-600" />
                <span>Chuyển khoản</span>
              </button>
            </div>
          </div>

          {/* Financial summary */}
          {paymentMethod === 'BANK_TRANSFER' && <Input label="Mã giao dịch đã xác nhận" maxLength={100}
            value={paymentReference} onChange={e => setPaymentReference(e.target.value)} />}
          {quoteLoading && <p role="status" className="text-xs text-gray-500">Đang lấy giá chính xác từ máy chủ…</p>}
          {quoteError && <div role="alert" className="text-xs text-red-700">{quoteError}
            <button type="button" className="ml-2 underline" onClick={retryQuote}>Thử lại</button></div>}
          <div className="pt-2 border-t border-gray-200 space-y-1 text-xs">
            <div className="flex justify-between text-gray-500">
              <span>Tạm tính ({itemCount} món):</span>
              <span className="font-medium text-gray-900">{quote ? formatVND(subtotal) : '—'}</span>
            </div>
            <div className="flex justify-between text-gray-500"><span>Giảm khuyến mãi:</span><span>{quote ? formatVND(discountAmount) : '—'}</span></div>
            <div className="flex justify-between items-baseline pt-1">
              <span className="text-sm font-bold text-gray-900">TỔNG THANH TOÁN:</span>
              <span className="text-lg font-bold text-blue-700">{quote ? formatVND(total) : '—'}</span>
            </div>
          </div>

          {/* Submit Checkout Button */}
          <Button
            size="lg"
            variant="primary"
            className="w-full text-base font-bold shadow-md"
            disabled={items.length === 0 || isSubmitting || quoteLoading || !quote || (paymentMethod === 'BANK_TRANSFER' && !paymentReference.trim())}
            isLoading={isSubmitting}
            onClick={handleCheckout}
          >
            {isSubmitting ? 'Đang thanh toán...' : quote ? `THANH TOÁN (${formatVND(total)})` : 'Chờ báo giá'}
          </Button>
        </div>
      </div>

      {/* POS Receipt Modal (Printable) */}
      <Modal
        isOpen={isReceiptOpen}
        onClose={() => setIsReceiptOpen(false)}
        title="Hóa đơn thanh toán bán lẻ"
        maxWidth="md"
        footer={
          <>
            <Button variant="outline" size="sm" onClick={() => setIsReceiptOpen(false)}>
              Đóng
            </Button>
            <Button
              variant="primary"
              size="sm"
              leftIcon={<Printer className="w-4 h-4" />}
              onClick={handlePrintReceipt}
            >
              In hóa đơn (K80)
            </Button>
          </>
        }
      >
        {lastOrder && (
          <OrderReceipt order={lastOrder} />
        )}
      </Modal>
    </div>
  );
};
