-- Roll back V013. This drops promotion data stored in these tables.
DROP TABLE IF EXISTS `billing_promotion_events`;
DROP TABLE IF EXISTS `billing_promotions`;
