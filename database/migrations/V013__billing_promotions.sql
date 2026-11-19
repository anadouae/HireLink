-- Billing promotion tables copied from the read-only SHOW CREATE TABLE output
-- on hirelink. Keep the definitions synchronized with that schema.

CREATE TABLE IF NOT EXISTS `billing_promotions` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `active` bit(1) NOT NULL,
  `code` varchar(40) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `created_by_user_id` bigint(20) DEFAULT NULL,
  `discount_type` enum('FIXED','PERCENT') NOT NULL,
  `discount_value` int(11) NOT NULL,
  `label` varchar(120) NOT NULL,
  `note` varchar(500) DEFAULT NULL,
  `plan_code` varchar(32) NOT NULL,
  `renewal_policy` enum('EVERY_RENEWAL','FIRST_PERIOD_ONLY') NOT NULL,
  `stacking_policy` enum('NONE','WITH_YEARLY_ONLY') NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `valid_from` datetime(6) DEFAULT NULL,
  `valid_to` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKa1apbn6as97xhj14s5habuxvs` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `billing_promotion_events` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `action` varchar(40) NOT NULL,
  `actor_user_id` bigint(20) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `detail` text DEFAULT NULL,
  `invoice_id` bigint(20) DEFAULT NULL,
  `promotion_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
