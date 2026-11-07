// 03-queries-basic.js – truy vấn cơ bản (CRUD, lọc, sắp xếp, phân trang). Mỗi truy vấn ghi rõ mẫu truy cập (AP) trong SRS.
// Chạy: mongosh "$MONGODB_URI/supermarket" --file nosql-scripts/mongodb/03-queries-basic.js

// [CB-M1] AP-01: tìm tài khoản để đăng nhập (index unique username)
printjson(db.users.findOne({ username: 'thungan01' }, { passwordHash: 0 }));

// [CB-M2] AP-05: lọc sản phẩm theo nhóm + khoảng giá, sắp theo giá, trang 1 (20/trang) – dùng index categoryId_price (ESR)
const cat = db.categories.findOne({ nameLower: 'sữa & chế phẩm' });
printjson(db.products.find(
  { categoryId: cat._id.toHexString(), price: { $gte: NumberDecimal('10000'), $lte: NumberDecimal('50000') }, active: true },
  { sku: 1, name: 1, price: 1, stockQuantity: 1 },
).sort({ price: 1 }).skip(0).limit(20).toArray());

// [CB-M3] Thêm khách hàng tại quầy (unique phone, email chữ thường)
db.customers.insertOne({ fullName: 'Trần Thị Bé', phone: '0912999888', email: 'be.tran@gmail.com', active: true, createdAt: new Date() });

// [CB-M4] Sửa thông tin khách (cập nhật có điều kiện theo phone) và xóa mềm (active = false)
db.customers.updateOne({ phone: '0912999888' }, { $set: { address: 'Quận 3, TP.HCM', updatedAt: new Date() } });
db.customers.updateOne({ phone: '0912999888' }, { $set: { active: false } });

// [CB-M5] AP-10: chi tiết đơn theo mã (index unique orderCode), chỉ lấy các trường cần (projection)
printjson(db.orders.findOne({ orderCode: 'HD-DEMO-0001' }, { orderCode: 1, total: 1, status: 1, 'items.name': 1, 'items.quantity': 1 }));

// [CB-M6] AP-08: lô đã hết hạn còn tồn (index expiry_remaining)
printjson(db.batches.find({ expirySortKey: { $lt: new Date() }, remainingQty: { $gt: 0 } }).sort({ expirySortKey: 1 }).toArray());
