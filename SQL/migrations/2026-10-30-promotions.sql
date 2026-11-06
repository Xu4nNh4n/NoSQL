-- MANUAL DDL ONLY. Not run by the application; execute once on an existing POS schema.
-- Take a backup first. MySQL DDL commits implicitly. Do not run after loading schema.sql.
-- Deliberately leave old invoice net prices NULL: legacy refund allocation must be preserved.
CREATE TABLE `promotions` (
  `promotion_id` bigint NOT NULL AUTO_INCREMENT,
  `product_id` bigint NOT NULL,
  `name` varchar(150) COLLATE utf8mb4_unicode_ci NOT NULL,
  `percent` decimal(5,2) NOT NULL,
  `starts_at` datetime NOT NULL COMMENT 'Vietnam wall time Asia/Ho_Chi_Minh; inclusive',
  `ends_at` datetime NOT NULL COMMENT 'Vietnam wall time Asia/Ho_Chi_Minh; exclusive',
  `is_active` tinyint NOT NULL DEFAULT '1',
  PRIMARY KEY (`promotion_id`),
  KEY `idx_promotions_effective` (`product_id`,`is_active`,`starts_at`,`ends_at`),
  CONSTRAINT `fk_promotions_products` FOREIGN KEY (`product_id`) REFERENCES `products` (`product_id`) ON DELETE RESTRICT,
  CONSTRAINT `chk_promotions_percent` CHECK (`percent` > 0 AND `percent` <= 100),
  CONSTRAINT `chk_promotions_active` CHECK (`is_active` IN (0,1)),
  CONSTRAINT `chk_promotions_window` CHECK (`ends_at` > `starts_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE `sales_order_items`
  ADD COLUMN `net_unit_price` decimal(18,2) DEFAULT NULL COMMENT 'Charged unit price rounded HALF_UP before quantity multiplication' AFTER `unit_price`,
  ADD COLUMN `promotion_snapshot` json DEFAULT NULL AFTER `net_unit_price`,
  ADD CONSTRAINT `chk_order_items_net_price` CHECK (`net_unit_price` IS NULL OR (`net_unit_price` >= 0 AND `net_unit_price` <= `unit_price`));
