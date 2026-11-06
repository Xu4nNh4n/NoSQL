# Promotions implementation

User authorized autonomous implementation on the current dirty branch, without tests, seeds, startup or database mutations.

1. Add single-product percentage promotions with Vietnam local datetime windows `[startsAt, endsAt)`, enable/disable, admin/manager management, greatest percentage then lowest ID selection.
2. Share a server quote calculation with checkout. Require the quote fingerprint at checkout and reject stale quotes before stock/payment writes. Round net unit price HALF_UP to two decimals before multiplying quantity.
3. Keep invoice unit_price as original gross; add nullable net_unit_price and immutable JSON promotion_snapshot. Line subtotal is charged net; invoice subtotal is gross, discount_amount is gross minus charged total. Preserve legacy return allocation when net_unit_price is null.
4. Integrate quote-driven cart, checkout price confirmation, bank reference, receipt, order details and promotion management/navigation using existing UI patterns.
5. Supply manual SQL migration plus fresh schema and analytics contract. Parent owns Drill and OperationalAnalyticsService changes; do not edit either.
6. Verify through compile/type checks and diff review only, without running tests, application or SQL.
