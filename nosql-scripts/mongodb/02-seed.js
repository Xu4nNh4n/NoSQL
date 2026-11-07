// 02-seed.js – dữ liệu mẫu tối thiểu để chạy thử các truy vấn 03/04 bằng mongosh.
// Dữ liệu demo đầy đủ (phiếu nhập, đơn, trả hàng, đồng bộ sang Cassandra/Neo4j) do backend tạo khi APP_SEED_DEMO=true.
// Chạy sau 01: mongosh "$MONGODB_URI" --file nosql-scripts/mongodb/02-seed.js

const dbName = (typeof process !== 'undefined' && process.env.MONGODB_DATABASE) || 'supermarket';
const s = db.getSiblingDB(dbName);
const now = new Date();
const day = (n) => new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate() + n));

const milkCat = ObjectId();
s.categories.insertOne({ _id: milkCat, name: 'Sữa & chế phẩm', nameLower: 'sữa & chế phẩm', active: true, createdAt: now });
const vinamilk = ObjectId();
s.suppliers.insertOne({ _id: vinamilk, name: 'Vinamilk', phone: '0281111001', active: true, createdAt: now });

const milk = ObjectId();
s.products.insertMany([
  { _id: milk, sku: 'SUA-VNM-1L', name: 'Sữa tươi Vinamilk 1L', price: NumberDecimal('32000'), categoryId: milkCat.toHexString(),
    categoryName: 'Sữa & chế phẩm', supplierId: vinamilk.toHexString(), reorderPoint: 20, active: true,
    attributes: { dungTich: '1L', loai: 'Không đường' }, stockQuantity: 80, damagedQuantity: 0, createdAt: now, version: NumberLong(0) },
  { sku: 'DEN-LED-9W', name: 'Bóng đèn LED 9W', price: NumberDecimal('35000'), categoryId: milkCat.toHexString(),
    categoryName: 'Sữa & chế phẩm', reorderPoint: 5, active: true,
    attributes: { congSuat: '9W', baoHanhThang: 12 }, stockQuantity: 0, damagedQuantity: 0, createdAt: now, version: NumberLong(0) },
]);

// Hai lô của cùng sản phẩm, hạn khác nhau (FEFO lấy lô hạn gần trước). Lô không có hạn dùng expirySortKey = 9999-12-31.
s.batches.insertMany([
  { batchCode: 'L-DEMO-0001', productId: milk.toHexString(), productSku: 'SUA-VNM-1L', productName: 'Sữa tươi Vinamilk 1L',
    supplierId: vinamilk.toHexString(), supplierName: 'Vinamilk', receivedQty: 30, remainingQty: 30, damagedQty: 0,
    unitCost: NumberDecimal('24000'), expiryDate: day(6), expirySortKey: day(6), receivedAt: now, adjustments: [] },
  { batchCode: 'L-DEMO-0002', productId: milk.toHexString(), productSku: 'SUA-VNM-1L', productName: 'Sữa tươi Vinamilk 1L',
    supplierId: vinamilk.toHexString(), supplierName: 'Vinamilk', receivedQty: 50, remainingQty: 50, damagedQty: 0,
    unitCost: NumberDecimal('24000'), expiryDate: day(16), expirySortKey: day(16), receivedAt: now, adjustments: [] },
]);

s.orders.insertOne({
  orderCode: 'HD-DEMO-0001', requestKey: UUID().toString(), cashier: { id: 'demo', name: 'thungan01' },
  orderedAt: now, orderDay: day(0), status: 'PAID', paymentMethod: 'CASH',
  grossTotal: NumberDecimal('64000'), discountTotal: NumberDecimal('6400'), total: NumberDecimal('57600'),
  refundedTotal: NumberDecimal('0'),
  items: [{ lineNo: 1, productId: milk.toHexString(), sku: 'SUA-VNM-1L', name: 'Sữa tươi Vinamilk 1L', categoryId: milkCat.toHexString(),
    quantity: 2, unitPrice: NumberDecimal('32000'), grossAmount: NumberDecimal('64000'), netAmount: NumberDecimal('57600'),
    gift: false, returnedQuantity: 0,
    promotion: { name: 'Sữa giảm 10%', type: 'PERCENT', discount: NumberDecimal('6400') },
    allocations: [{ batchCode: 'L-DEMO-0001', quantity: 2, returnedQuantity: 0 }] }],
  payments: [{ paymentId: 'PAY-HD-DEMO-0001', type: 'PAYMENT', method: 'CASH', amount: NumberDecimal('57600'),
    reference: 'POS-HD-DEMO-0001', at: now, by: 'thungan01' }],
});
print('Đã thêm dữ liệu mẫu vào ' + dbName);
