// 04-queries-advanced.js – truy vấn nâng cao: aggregation, $facet, $lookup, text search, cập nhật có điều kiện,
// arrayFilters, transaction nhiều document, explain(). Backend dùng đúng các mẫu này (xem class được ghi chú).
// Chạy: mongosh "$MONGODB_URI/supermarket" --file nosql-scripts/mongodb/04-queries-advanced.js

const today = new Date();
const startOfDay = new Date(today.getFullYear(), today.getMonth(), today.getDate());
const weekAgo = new Date(startOfDay.getTime() - 6 * 86400000);

// [NC-M1] AP-04: tìm kiếm tiếng Việt không dấu bằng text index (default_language none, không phân biệt dấu) – ProductRepository.search
printjson(db.products.find({ $text: { $search: 'sua tuoi' } }, { name: 1, score: { $meta: 'textScore' } })
  .sort({ score: { $meta: 'textScore' } }).toArray());

// [NC-M2] AP-23: thống kê 7 ngày trong MỘT lệnh: $match → $facet (theo ngày, theo nhóm hàng có $lookup, top sản phẩm, tổng) – StatsService
printjson(db.orders.aggregate([
  { $match: { orderedAt: { $gte: weekAgo, $lt: new Date(startOfDay.getTime() + 86400000) } } },
  { $facet: {
    daily: [
      { $group: { _id: '$orderDay', orders: { $sum: 1 }, revenue: { $sum: '$total' }, refunds: { $sum: '$refundedTotal' } } },
      { $sort: { _id: 1 } },
    ],
    byCategory: [
      { $unwind: '$items' },
      { $group: { _id: '$items.categoryId', quantity: { $sum: '$items.quantity' }, revenue: { $sum: '$items.netAmount' } } },
      { $lookup: { from: 'categories', let: { cid: '$_id' },
        pipeline: [{ $match: { $expr: { $eq: ['$_id', { $convert: { input: '$$cid', to: 'objectId', onError: null, onNull: null } }] } } }],
        as: 'category' } },
      { $project: { quantity: 1, revenue: 1, name: { $first: '$category.name' } } },
      { $sort: { revenue: -1 } },
    ],
    topProducts: [
      { $unwind: '$items' }, { $match: { 'items.gift': false } },
      { $group: { _id: '$items.productId', name: { $first: '$items.name' }, quantity: { $sum: '$items.quantity' } } },
      { $sort: { quantity: -1 } }, { $limit: 10 },
    ],
    totals: [{ $group: { _id: null, orders: { $sum: 1 }, revenue: { $sum: '$total' }, discount: { $sum: '$discountTotal' } } }],
  } },
]).toArray());

// [NC-M3] AP-06: tồn khả dụng = tổng tồn còn lại của các lô CÒN HẠN, so với ngưỡng đặt lại → sắp hết hàng – LowStockService
printjson(db.batches.aggregate([
  { $match: { expirySortKey: { $gte: startOfDay }, remainingQty: { $gt: 0 } } },
  { $group: { _id: '$productId', available: { $sum: '$remainingQty' } } },
  { $lookup: { from: 'products', let: { pid: { $toObjectId: '$_id' } },
    pipeline: [{ $match: { $expr: { $eq: ['$_id', '$$pid'] } } }, { $project: { sku: 1, name: 1, reorderPoint: 1 } }], as: 'p' } },
  { $unwind: '$p' },
  { $match: { $expr: { $lte: ['$available', '$p.reorderPoint'] } } },
]).toArray());

// [NC-M4] AP-07 + chống bán quá tồn: lấy lô FEFO rồi trừ CÓ ĐIỀU KIỆN remainingQty >= số lấy – FefoAllocator
const lot = db.batches.find({ productSku: 'SUA-VNM-1L', expirySortKey: { $gte: startOfDay }, remainingQty: { $gt: 0 } })
  .sort({ expirySortKey: 1, receivedAt: 1, batchCode: 1 }).limit(1).next();
const r = db.batches.updateOne({ _id: lot._id, remainingQty: { $gte: 2 } }, { $inc: { remainingQty: -2 } });
print('Trừ lô ' + lot.batchCode + ': matched=' + r.matchedCount + ' (0 nghĩa là đơn khác đã lấy trước → thử lại)');

// [NC-M5] Transaction nhiều document: trừ lô + trừ tồn sản phẩm + tạo đơn cùng commit hoặc cùng hủy – MongoTx + OrderService
const session = db.getMongo().startSession();
const sdb = session.getDatabase(db.getName());
session.startTransaction({ readConcern: { level: 'majority' }, writeConcern: { w: 'majority' } });
try {
  const ok = sdb.batches.updateOne({ _id: lot._id, remainingQty: { $gte: 1 } }, { $inc: { remainingQty: -1 } });
  if (ok.matchedCount !== 1) throw new Error('Không đủ tồn lô');
  sdb.products.updateOne({ sku: 'SUA-VNM-1L' }, { $inc: { stockQuantity: -1 } });
  sdb.orders.insertOne({ orderCode: 'HD-TX-' + Date.now(), requestKey: UUID().toString(), status: 'PAID', paymentMethod: 'CASH',
    orderedAt: new Date(), total: NumberDecimal('32000'), items: [{ sku: 'SUA-VNM-1L', quantity: 1 }],
    payments: [{ type: 'PAYMENT', method: 'CASH', amount: NumberDecimal('32000') }] });
  session.commitTransaction();
  print('Transaction commit');
} catch (e) {
  session.abortTransaction();
  print('Transaction hủy: ' + e.message);
} finally {
  session.endSession();
}

// [NC-M6] Sinh mã chứng từ nguyên tử bằng findOneAndUpdate + $inc + upsert (AP-24) – CodeGenerator
printjson(db.counters.findOneAndUpdate({ _id: 'HD:' + today.toISOString().slice(2, 10).replace(/-/g, '') },
  { $inc: { seq: NumberLong(1) } }, { upsert: true, returnDocument: 'after' }));

// [NC-M7] Trả hàng: cập nhật phần tử lồng nhau trong mảng bằng arrayFilters (dòng 1, lô L-DEMO-0001) – ReturnService
db.orders.updateOne({ orderCode: 'HD-DEMO-0001' },
  { $inc: { 'items.$[line].returnedQuantity': 1, 'items.$[line].allocations.$[a].returnedQuantity': 1 } },
  { arrayFilters: [{ 'line.lineNo': 1 }, { 'a.batchCode': 'L-DEMO-0001' }] });

// [NC-M8] Schema linh hoạt: tìm sản phẩm có bảo hành ≥ 12 tháng (thuộc tính chỉ có ở hàng gia dụng)
printjson(db.products.find({ 'attributes.baoHanhThang': { $gte: 12 } }, { name: 1, attributes: 1 }).toArray());

// [NC-M9] Chứng minh truy vấn dùng index (NFR-12): explain() phải có IXSCAN trên categoryId_price
const plan = db.products.find({ categoryId: 'x', price: { $gte: NumberDecimal('10000') } }).explain('executionStats');
print('Winning plan: ' + JSON.stringify(plan.queryPlanner.winningPlan));
