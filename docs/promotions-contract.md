# Promotions: POS and analytics contract

Implementation is code and manual DDL only. No SQL, tests, seeds or application startup were performed.

## Storage / handoff to Drill and operational analytics owner

Exact new columns in **sales_order_items**:

| Column | SQL type | Meaning |
| --- | --- | --- |
| `net_unit_price` | `DECIMAL(18,2) NULL` | Actual charged unit price, including promotions. New sales always populate it, including no-promotion sales and free (100% discounted) items. NULL identifies historical invoices. Never backfill with catalog prices. |
| `promotion_snapshot` | `JSON NULL` | Immutable sale-time object `{promotionId, name, percent, startsAt, endsAt}`; NULL if no promotion. Datetimes are ISO local Vietnam time. No foreign key to a live promotion. |

Existing `unit_price` remains the original **gross** unit price. Existing line `subtotal` is **charged net line amount** for new sales: `net_unit_price * quantity`. Invoice `sales_orders.subtotal` is original gross `SUM(unit_price * quantity)`; `discount_amount` is gross minus charged total; `total_amount` is charged total and the payment amount. Returns leave original invoice totals unchanged; return/refund receipts record separate amounts.

Reconciliation must compare invoice gross `subtotal` with `SUM(i.unit_price * i.quantity)`, and new invoice `total_amount` with `SUM(i.subtotal)`. Comparing invoice gross subtotal directly against net line subtotals falsely flags every discounted invoice. Preserve the historical invoice-discount rules when reconciling NULL-net legacy rows.

For new sales, reporting remaining sales/revenue must use `(quantity - COALESCE(returned_quantity,0)) * COALESCE(net_unit_price,unit_price)`. Costs keep their existing calculation; margins subtract costs from net revenue. Never multiply `unit_price` by an invoice discount ratio again for new sales: that would double-discount them. Do not use truthiness/positive-only fallback: zero net prices are valid.

For historical `net_unit_price IS NULL` rows, preserve any existing invoice-level discount allocation. The return service retains its original cumulative boundary formula: for a quantity range beginning at gross offset B with gross length G, refund is `floor2((B+G)*paidTotal/grossTotal) - floor2(B*paidTotal/grossTotal)`. Gross line offsets are ordered by `order_item_id`. Simple `COALESCE(net_unit_price, unit_price)` preserves legacy unit price semantics but does not reconstruct legacy invoice-wide discounts. Reports requiring exact historical refund attribution must use recorded return receipts or the same cumulative allocation.

Parent owns `DrillAnalyticsService`, Drill scripts and `OperationalAnalyticsService`; promotion worker does not edit them. Update product/category/hourly revenue and margin projections to read net prices. Existing payment/outbox amounts already use the net order total; no event schema change is required.

## API

- `GET /api/promotions`, `POST /api/promotions`, `PUT /api/promotions/{id}`: ADMIN/MANAGER. Write body `{productId,name,percent,startsAt,endsAt,active}`. Percentage has at most two decimals, `0 < percent <= 100`. Datetimes use Vietnam local wall time without a UTC offset.
- `PUT /api/promotions/{id}/active`: ADMIN/MANAGER, body `{active:boolean}`. Disable rather than delete.
- `POST /api/orders/quote`: STAFF, body `{items:[{productId,quantity}]}`. Read-only pricing; it does not reserve/check batch inventory. Returns `{quoteToken,quotedAt,timezone,subtotal,discountAmount,totalAmount,items}`. Each item contains `{productId,quantity,originalUnitPrice,netUnitPrice,grossSubtotal,discountAmount,subTotal,promotionSnapshot}`.
- `POST /api/orders`: existing payload plus required 64-character `quoteToken`; bank transfers also require `paymentReference`. Checkout uses READ_COMMITTED isolation, locks products in ascending ID order and recomputes prices once at a single Vietnam timestamp before any inventory/payment writes. READ_COMMITTED prevents an earlier staff lookup from hiding promotion changes committed during a lock wait. Stale/missing tokens at the service layer return 409 `{code:"PRICE_CHANGED",message,quote}` (missing/wrong-length tokens at request validation return 400). Quote token is a comparison fingerprint, not a reservation, security credential, or checkout idempotency key. Client cannot choose prices: server always recomputes. Promotion edits committed after the pricing read apply to subsequent sales; the current sale retains its computed snapshot.

Order item response keeps `unitPrice` gross and `subTotal` charged, adding `originalUnitPrice`, `netUnitPrice`, `grossSubtotal`, `discountAmount` and `promotionSnapshot`. For legacy NULL net prices, API `netUnitPrice` falls back to stored `unitPrice`, never the product's current catalog price. Frontend receipt and details consume these server fields rather than recomputing discounts.

## Rules and rounding

Promotion eligibility is `active && starts_at <= pricedAt && pricedAt < ends_at`. Greatest percent wins; equal percentages choose the lowest promotion ID. No stacking. `net = roundHalfUp(original * (100-percent)/100, 2)`; round per unit, then multiply quantity. Consequently every unit's refund is exactly its charged unit price and all complete returns sum to the original paid total. A 100% discount creates a zero line, zero refund and, if the entire invoice is free, a valid zero payment.

Cashier sees exact server quote in the cart. Before posting checkout the client fetches a fresh quote and asks explicit confirmation if its fingerprint changed. A subsequent server 409 never auto-retries a sale; the cashier accepts the replacement quote and clicks checkout again. Controls are disabled while checkout is in flight.

## Deployment / remaining checks

Use **either** `SQL/schema.sql` for a new database **or** `SQL/migrations/2026-10-30-promotions.sql` once for an existing schema. No seed/backfill. The migration is deliberately not wired to automatic startup. Backend `ddl-auto=none` remains unchanged. Apply the schema before deploying code or reporting queries that reference the columns. All old clients posting orders must adopt quoteToken.

Runtime/database/UI checks were intentionally excluded by the user. Follow-up manual checks when authorized: overlapping and tied promotions, exact start/end boundaries, disabled promotions, 100% discounts, fractional-cent unit rounding, split returns in arbitrary order, legacy discounted returns, stale quotes between display and checkout, server 409 recovery, bank references, and authorization.

## Changed files and static verification

Backend paths below are relative to `backend/src/main/java/com/api/supermarket/`:

- New: `entity/Promotion.java`, `repository/PromotionRepository.java`, `service/PromotionService.java`, `controller/PromotionController.java`, `domain/PromotionSnapshot.java`, `dto/request/PromotionRequest.java`, `dto/request/PosQuoteRequest.java`, `dto/response/PromotionResponse.java`, `dto/response/PosQuoteResponse.java`, `exception/PriceChangedException.java`.
- Updated: `entity/SalesOrderItem.java`, `dto/request/SalesOrderRequest.java`, `dto/response/OrderItemResponse.java`, `service/SalesOrderService.java`, `service/Impl/SalesOrderServiceImpl.java`, `controller/SalesOrderController.java`, `exception/GlobalExceptionHandler.java`.
- Frontend: `frontend/src/pages/PromotionsPage.tsx` (new), `pages/PosPage.tsx`, `pages/SalesOrdersPage.tsx`, `contexts/CartContext.tsx`, `components/OrderReceipt.tsx`, `components/layout/Sidebar.tsx`, `App.tsx`, `types/index.ts`, `api/client.ts` (remaining frontend paths relative to `frontend/src/`).
- Manual DDL: `SQL/schema.sql`, `SQL/migrations/2026-10-30-promotions.sql`.
- Documentation: this file and `docs/superpowers/plans/2026-10-30-promotions.md`.

Also connected `/supplier-prices` to the CSV worker's `SupplierPricesPage`, with ADMIN/MANAGER/WAREHOUSE route and menu permissions; did not edit that worker's page.

Static verification: frontend `tsc --project tsconfig.app.json --noEmit --incremental false` passed. Scoped oxlint completed with zero errors and seven warnings (effect state updates, existing effect dependencies and context fast-refresh export). Isolated Java 21 `javac` compilation of all 17 changed backend files plus their transitive source dependencies passed, with annotation processing disabled and output outside the repository. Full offline Maven compile is currently blocked by the concurrent CSV worker's missing `org.apache.commons.csv` dependency in `SupplierPriceFileService`. No test sources were created, compiled or run. No Drill/operational analytics files were edited by this worker.

Follow-up static review (no financial execution): checkout now explicitly uses READ_COMMITTED to avoid stale promotion snapshots after product-lock waits; quoteToken length is exactly 64; both DDL paths constrain promotion `is_active` to 0/1; quote rejects gross totals beyond DECIMAL(18,2) capacity before any sale writes; frontend tolerates unrelated 409 responses with no JSON body. Parent is handling the subsequent compile/build with tests skipped. Refund policy remains stored net price times returned units for new lines, original cumulative allocation for NULL-net legacy lines. Quote tokens are reusable pricing fingerprints: they do not deduplicate repeated checkout requests, and an ambiguous network failure still requires invoice lookup before a cashier manually retries.
