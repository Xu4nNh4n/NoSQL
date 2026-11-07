// 01-create-collections.js – tạo collection, $jsonSchema validation và index cho database siêu thị.
// Chạy: mongosh "$MONGODB_URI" --file nosql-scripts/mongodb/01-create-collections.js
// SINH TỰ ĐỘNG từ backend/src/main/resources/mongodb/validators.json (backend áp cùng schema lúc khởi động).

const dbName = (typeof process !== 'undefined' && process.env.MONGODB_DATABASE) || 'supermarket';
const target = db.getSiblingDB(dbName);

function ensureCollection(name, schema) {
  const exists = target.getCollectionNames().includes(name);
  if (!schema) { if (!exists) target.createCollection(name); return; }
  const options = { validator: { $jsonSchema: schema }, validationLevel: 'moderate', validationAction: 'error' };
  if (exists) target.runCommand(Object.assign({ collMod: name }, options));
  else target.createCollection(name, options);
}

const schemas = {
  "users": {
    "bsonType": "object",
    "required": [
      "username",
      "passwordHash",
      "role",
      "active"
    ],
    "properties": {
      "username": {
        "bsonType": "string",
        "minLength": 3,
        "maxLength": 30
      },
      "passwordHash": {
        "bsonType": "string"
      },
      "fullName": {
        "bsonType": "string"
      },
      "email": {
        "bsonType": "string"
      },
      "phone": {
        "bsonType": "string"
      },
      "role": {
        "enum": [
          "ADMIN",
          "WAREHOUSE",
          "STAFF"
        ]
      },
      "active": {
        "bsonType": "bool"
      }
    }
  },
  "products": {
    "bsonType": "object",
    "required": [
      "sku",
      "name",
      "price",
      "categoryId",
      "active",
      "stockQuantity",
      "damagedQuantity"
    ],
    "properties": {
      "sku": {
        "bsonType": "string"
      },
      "name": {
        "bsonType": "string"
      },
      "price": {
        "bsonType": "decimal",
        "minimum": 0
      },
      "categoryId": {
        "bsonType": "string"
      },
      "reorderPoint": {
        "bsonType": [
          "int",
          "long"
        ],
        "minimum": 0
      },
      "active": {
        "bsonType": "bool"
      },
      "attributes": {
        "bsonType": "object"
      },
      "stockQuantity": {
        "bsonType": [
          "int",
          "long"
        ],
        "minimum": 0
      },
      "damagedQuantity": {
        "bsonType": [
          "int",
          "long"
        ],
        "minimum": 0
      }
    }
  },
  "batches": {
    "bsonType": "object",
    "required": [
      "batchCode",
      "productId",
      "receivedQty",
      "remainingQty",
      "damagedQty",
      "expirySortKey",
      "receivedAt"
    ],
    "properties": {
      "batchCode": {
        "bsonType": "string"
      },
      "productId": {
        "bsonType": "string"
      },
      "receivedQty": {
        "bsonType": [
          "int",
          "long"
        ],
        "minimum": 1
      },
      "remainingQty": {
        "bsonType": [
          "int",
          "long"
        ],
        "minimum": 0
      },
      "damagedQty": {
        "bsonType": [
          "int",
          "long"
        ],
        "minimum": 0
      },
      "unitCost": {
        "bsonType": "decimal",
        "minimum": 0
      },
      "expirySortKey": {
        "bsonType": "date"
      },
      "receivedAt": {
        "bsonType": "date"
      }
    }
  },
  "orders": {
    "bsonType": "object",
    "required": [
      "orderCode",
      "requestKey",
      "status",
      "paymentMethod",
      "total",
      "items",
      "payments",
      "orderedAt"
    ],
    "properties": {
      "orderCode": {
        "bsonType": "string"
      },
      "requestKey": {
        "bsonType": "string"
      },
      "status": {
        "enum": [
          "PAID",
          "PARTIALLY_RETURNED",
          "RETURNED"
        ]
      },
      "paymentMethod": {
        "enum": [
          "CASH",
          "BANK_TRANSFER"
        ]
      },
      "total": {
        "bsonType": "decimal",
        "minimum": 0
      },
      "items": {
        "bsonType": "array",
        "minItems": 1
      },
      "payments": {
        "bsonType": "array",
        "minItems": 1
      },
      "orderedAt": {
        "bsonType": "date"
      }
    }
  },
  "promotions": {
    "bsonType": "object",
    "required": [
      "name",
      "type",
      "startsAt",
      "endsAt",
      "active"
    ],
    "properties": {
      "name": {
        "bsonType": "string"
      },
      "type": {
        "enum": [
          "PERCENT",
          "FIXED",
          "BUY_X_GET_Y",
          "ORDER_THRESHOLD"
        ]
      },
      "startsAt": {
        "bsonType": "date"
      },
      "endsAt": {
        "bsonType": "date"
      },
      "active": {
        "bsonType": "bool"
      },
      "rule": {
        "bsonType": "object"
      }
    }
  }
};

ensureCollection('users', schemas['users']);
ensureCollection('categories', null);
ensureCollection('suppliers', null);
ensureCollection('products', schemas['products']);
ensureCollection('batches', schemas['batches']);
ensureCollection('purchase_orders', null);
ensureCollection('orders', schemas['orders']);
ensureCollection('returns', null);
ensureCollection('promotions', schemas['promotions']);
ensureCollection('shift_closings', null);
ensureCollection('sync_queue', null);

target.users.createIndex({ username: 1 }, { unique: true, name: 'username_unique' });
target.users.createIndex({ email: 1 }, { unique: true, name: 'email_unique_partial', partialFilterExpression: { email: { $exists: true } } });
target.users.createIndex({ phone: 1 }, { unique: true, name: 'phone_unique_partial', partialFilterExpression: { phone: { $exists: true } } });
target.categories.createIndex({ nameLower: 1 }, { unique: true, name: 'nameLower_unique' });
target.suppliers.createIndex({ phone: 1 }, { unique: true, name: 'phone_unique_partial', partialFilterExpression: { phone: { $exists: true } } });
target.suppliers.createIndex({ email: 1 }, { unique: true, name: 'email_unique_partial', partialFilterExpression: { email: { $exists: true } } });
target.products.createIndex({ sku: 1 }, { unique: true, name: 'sku_unique' });
target.products.createIndex({ name: 'text', sku: 'text' }, { name: 'name_sku_text', weights: { name: 2, sku: 3 }, default_language: 'none' });
target.products.createIndex({ categoryId: 1, price: 1 }, { name: 'categoryId_price' });
target.products.createIndex({ active: 1, name: 1 }, { name: 'active_name' });
target.batches.createIndex({ batchCode: 1 }, { unique: true, name: 'batchCode_unique' });
target.batches.createIndex({ productId: 1, expirySortKey: 1, receivedAt: 1, batchCode: 1 }, { name: 'product_fefo' });
target.batches.createIndex({ expirySortKey: 1, remainingQty: 1 }, { name: 'expiry_remaining' });
target.batches.createIndex({ supplierId: 1 }, { name: 'supplierId' });
target.purchase_orders.createIndex({ poCode: 1 }, { unique: true, name: 'poCode_unique' });
target.purchase_orders.createIndex({ status: 1, createdAt: -1 }, { name: 'status_createdAt' });
target.orders.createIndex({ orderCode: 1 }, { unique: true, name: 'orderCode_unique' });
target.orders.createIndex({ requestKey: 1 }, { unique: true, name: 'requestKey_unique' });
target.orders.createIndex({ orderedAt: -1 }, { name: 'orderedAt' });
target.orders.createIndex({ 'cashier.id': 1, orderedAt: -1 }, { name: 'cashier_orderedAt' });
target.orders.createIndex({ status: 1, orderedAt: -1 }, { name: 'status_orderedAt' });
target.returns.createIndex({ requestKey: 1 }, { unique: true, name: 'requestKey_unique' });
target.returns.createIndex({ orderId: 1 }, { name: 'orderId' });
target.promotions.createIndex({ active: 1, endsAt: 1 }, { name: 'active_endsAt' });
target.shift_closings.createIndex({ cashierId: 1, day: 1 }, { unique: true, name: 'cashier_day_unique' });
target.sync_queue.createIndex({ status: 1, createdAt: 1 }, { name: 'status_createdAt' });

print('Đã tạo collection, validation và index cho ' + dbName);
