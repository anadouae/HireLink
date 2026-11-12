
/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
DROP TABLE IF EXISTS `ai_usage_logs`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `ai_usage_logs` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `chars_in` int(11) DEFAULT NULL,
  `chars_out` int(11) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `credits_spent` int(11) DEFAULT NULL,
  `feature` varchar(64) NOT NULL,
  `model` varchar(64) DEFAULT NULL,
  `organization_id` bigint(20) DEFAULT NULL,
  `provider` varchar(64) NOT NULL,
  `success` bit(1) NOT NULL,
  `user_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_ai_user` (`user_id`),
  KEY `idx_ai_org` (`organization_id`),
  KEY `idx_ai_created` (`created_at`)
) ENGINE=InnoDB AUTO_INCREMENT=46 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `application_status_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `application_status_history` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `actor_user_id` bigint(20) DEFAULT NULL,
  `application_id` bigint(20) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `from_status` varchar(32) DEFAULT NULL,
  `payload_json` text DEFAULT NULL,
  `to_status` varchar(32) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_ash_application` (`application_id`),
  KEY `idx_ash_created` (`created_at`)
) ENGINE=InnoDB AUTO_INCREMENT=602 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `applications`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `applications` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `match_score` double DEFAULT NULL,
  `rejection_reason` varchar(500) DEFAULT NULL,
  `status` enum('HIRED','INTERVIEW','OFFER','REJECTED','SCREENING','SHORTLISTED','SUBMITTED','WITHDRAWN') NOT NULL,
  `submitted_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `candidate_id` bigint(20) NOT NULL,
  `job_offer_id` bigint(20) NOT NULL,
  `cover_letter` text DEFAULT NULL,
  `cv_file_name` varchar(255) DEFAULT NULL,
  `cv_source` varchar(24) DEFAULT NULL,
  `advance_note` varchar(4000) DEFAULT NULL,
  `interview_at` datetime(6) DEFAULT NULL,
  `interview_location` varchar(255) DEFAULT NULL,
  `interview_notes` varchar(1000) DEFAULT NULL,
  `interview_type` varchar(32) DEFAULT NULL,
  `assigned_hm_user_id` bigint(20) DEFAULT NULL,
  `refusal_reason_code` varchar(64) DEFAULT NULL,
  `first_decision_at` datetime(6) DEFAULT NULL,
  `response_deadline` datetime(6) DEFAULT NULL,
  `sla_breach_notified` bit(1) NOT NULL,
  `cv_sections` text DEFAULT NULL,
  `cv_storage_name` varchar(255) DEFAULT NULL,
  `interview_cancel_reason` varchar(300) DEFAULT NULL,
  `interview_candidate_note` varchar(300) DEFAULT NULL,
  `interview_confirmation` varchar(16) DEFAULT NULL,
  `interview_duration_minutes` int(11) DEFAULT NULL,
  `interview_kind` varchar(16) DEFAULT NULL,
  `interview_map_url` varchar(300) DEFAULT NULL,
  `interview_participants` varchar(300) DEFAULT NULL,
  `interview_reminder_sent` bit(1) DEFAULT NULL,
  `interview_status` varchar(16) DEFAULT NULL,
  `interview_time_zone` varchar(40) DEFAULT NULL,
  `interview_video_url` varchar(300) DEFAULT NULL,
  `application_details` text DEFAULT NULL,
  `interview_reminder_claimed_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKbigbiiy8iifquorgjvxockq5s` (`candidate_id`,`job_offer_id`),
  KEY `FKawuu01ou28l0903avu0nwx058` (`job_offer_id`),
  KEY `idx_app_status` (`status`),
  KEY `idx_app_submitted` (`submitted_at`),
  KEY `idx_app_candidate` (`candidate_id`),
  KEY `idx_app_hm` (`assigned_hm_user_id`),
  KEY `idx_app_deadline` (`response_deadline`),
  CONSTRAINT `FK32iahkg1fqci0pt76uql80nj7` FOREIGN KEY (`candidate_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKawuu01ou28l0903avu0nwx058` FOREIGN KEY (`job_offer_id`) REFERENCES `job_offers` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=262 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `audit_logs`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `audit_logs` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `action` varchar(48) NOT NULL,
  `actor_email` varchar(160) DEFAULT NULL,
  `actor_user_id` bigint(20) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `detail` varchar(500) DEFAULT NULL,
  `ip` varchar(64) DEFAULT NULL,
  `success` bit(1) NOT NULL,
  `target_id` varchar(64) DEFAULT NULL,
  `target_type` varchar(32) DEFAULT NULL,
  `actor_role` varchar(32) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_audit_at` (`created_at`),
  KEY `idx_audit_actor` (`actor_user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=1581 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `billing_invoices`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `billing_invoices` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `amount_ht` double NOT NULL,
  `amount_ttc` double NOT NULL,
  `cycle` varchar(12) NOT NULL,
  `issued_at` datetime(6) NOT NULL,
  `number` varchar(40) NOT NULL,
  `plan_code` varchar(32) NOT NULL,
  `plan_label` varchar(80) NOT NULL,
  `status` varchar(16) NOT NULL,
  `tva` double NOT NULL,
  `organization_id` bigint(20) NOT NULL,
  `cancels_invoice_id` bigint(20) DEFAULT NULL,
  `note` varchar(255) DEFAULT NULL,
  `bill_to_email` varchar(160) DEFAULT NULL,
  `bill_to_extra` varchar(255) DEFAULT NULL,
  `bill_to_name` varchar(200) DEFAULT NULL,
  `paid_at` datetime(6) DEFAULT NULL,
  `period_end` datetime(6) DEFAULT NULL,
  `rights_applied` bit(1) DEFAULT NULL,
  `transaction_ref` varchar(64) DEFAULT NULL,
  `vat_configured` bit(1) DEFAULT NULL,
  `debtor_user_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKm1wpmtpvc7upb5llj2iyrdk4l` (`number`),
  KEY `FK6midusfb0en3e1p67nwjca6da` (`organization_id`),
  KEY `FKolqve2lbva8c2vgusswscr8w6` (`debtor_user_id`),
  CONSTRAINT `FK6midusfb0en3e1p67nwjca6da` FOREIGN KEY (`organization_id`) REFERENCES `organizations` (`id`),
  CONSTRAINT `FKolqve2lbva8c2vgusswscr8w6` FOREIGN KEY (`debtor_user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `commercial_plans`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `commercial_plans` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `audience` varchar(24) NOT NULL,
  `code` varchar(32) NOT NULL,
  `credits` int(11) NOT NULL,
  `features` text DEFAULT NULL,
  `label` varchar(80) NOT NULL,
  `monthly_mad` int(11) NOT NULL,
  `yearly_mad` int(11) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKkx84itmod428b8rkl1d71l3lo` (`code`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `company_follows`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `company_follows` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `candidate_user_id` bigint(20) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `organization_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKbcu8nsw1hdnq7qnpbfdrn5dih` (`candidate_user_id`,`organization_id`),
  KEY `idx_follow_org` (`organization_id`),
  KEY `idx_follow_candidate` (`candidate_user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=19 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `email_otps`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `email_otps` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `code` varchar(16) NOT NULL,
  `consumed` bit(1) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `email` varchar(160) NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `purpose` varchar(32) NOT NULL,
  `attempts` int(11) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_otp_email_purpose` (`email`,`purpose`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `external_job_offers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `external_job_offers` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `source` varchar(40) NOT NULL,
  `external_id` varchar(80) NOT NULL,
  `origin_url` varchar(500) NOT NULL,
  `apply_url` varchar(500) DEFAULT NULL,
  `company` varchar(200) NOT NULL,
  `title` varchar(200) NOT NULL,
  `location` varchar(160) DEFAULT NULL,
  `contract_label` varchar(60) DEFAULT NULL,
  `description` text DEFAULT NULL,
  `skills` varchar(500) DEFAULT NULL,
  `salary_min` int(11) DEFAULT NULL,
  `salary_max` int(11) DEFAULT NULL,
  `published_at` datetime(6) DEFAULT NULL,
  `last_synced_at` datetime(6) NOT NULL,
  `last_seen_at` datetime(6) DEFAULT NULL,
  `available` bit(1) NOT NULL,
  `hidden_by_admin` bit(1) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_external_offer_source` (`source`,`external_id`),
  UNIQUE KEY `UKk2m4y460t9dr9n5u7aaftgc8u` (`source`,`external_id`),
  KEY `idx_external_offer_available` (`source`,`available`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `external_sync_runs`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `external_sync_runs` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `source` varchar(40) NOT NULL,
  `created_count` int(11) NOT NULL,
  `updated_count` int(11) NOT NULL,
  `withdrawn_count` int(11) NOT NULL,
  `rejected_count` int(11) NOT NULL,
  `duplicate_count` int(11) NOT NULL,
  `kept_previous` bit(1) NOT NULL,
  `message` varchar(300) NOT NULL,
  `finished_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `job_offer_tags`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `job_offer_tags` (
  `job_offer_id` bigint(20) NOT NULL,
  `tag_id` bigint(20) NOT NULL,
  PRIMARY KEY (`job_offer_id`,`tag_id`),
  KEY `FK6j07qh0x0p82712tv8tp8b7s` (`tag_id`),
  CONSTRAINT `FK6j07qh0x0p82712tv8tp8b7s` FOREIGN KEY (`tag_id`) REFERENCES `tags` (`id`),
  CONSTRAINT `FK7fk7fhdstb4wwfjgccutg4e77` FOREIGN KEY (`job_offer_id`) REFERENCES `job_offers` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `job_offers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `job_offers` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `contract_type` varchar(60) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `description` text NOT NULL,
  `location` varchar(120) DEFAULT NULL,
  `published` bit(1) NOT NULL,
  `required_skills` varchar(500) DEFAULT NULL,
  `response_sla_days` int(11) NOT NULL,
  `title` varchar(200) NOT NULL,
  `recruiter_id` bigint(20) NOT NULL,
  `quarantine_reason` varchar(500) DEFAULT NULL,
  `report_count` int(11) NOT NULL,
  `suspended` bit(1) NOT NULL,
  `agreement_required` bit(1) DEFAULT NULL,
  `benefits` varchar(400) DEFAULT NULL,
  `compensation_type` enum('NEGOTIABLE','PAID','UNPAID','UNSPECIFIED') DEFAULT NULL,
  `currency` varchar(3) DEFAULT NULL,
  `deadline` date DEFAULT NULL,
  `duration_months` int(11) DEFAULT NULL,
  `education_level` enum('BAC','BAC_2','BAC_3','BAC_4','BAC_5','DOCTORATE') DEFAULT NULL,
  `experience_level` enum('EXPERT','INTERMEDIATE','JUNIOR','NONE','SENIOR') DEFAULT NULL,
  `hiring_possible` bit(1) DEFAULT NULL,
  `positions` int(11) DEFAULT NULL,
  `probation_months` int(11) DEFAULT NULL,
  `salary_basis` enum('GROSS','NET') DEFAULT NULL,
  `salary_max` int(11) DEFAULT NULL,
  `salary_min` int(11) DEFAULT NULL,
  `salary_period` enum('DAY','HOUR','MONTH','YEAR') DEFAULT NULL,
  `schedule` varchar(120) DEFAULT NULL,
  `start_asap` bit(1) DEFAULT NULL,
  `start_date` date DEFAULT NULL,
  `work_mode` enum('HYBRID','ONSITE','REMOTE') DEFAULT NULL,
  `nice_to_have_skills` varchar(500) DEFAULT NULL,
  `archived_at` datetime(6) DEFAULT NULL,
  `closed_at` datetime(6) DEFAULT NULL,
  `organization_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_offer_published` (`published`),
  KEY `idx_offer_recruiter` (`recruiter_id`),
  KEY `idx_offer_created` (`created_at`),
  KEY `idx_offer_org` (`organization_id`),
  CONSTRAINT `FK758c03cg7urxxvno8objwv0xv` FOREIGN KEY (`organization_id`) REFERENCES `organizations` (`id`),
  CONSTRAINT `FKnqarts4oog62ueq8q11iu24u3` FOREIGN KEY (`recruiter_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=159 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `legal_acceptances`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `legal_acceptances` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `accepted_at` datetime(6) NOT NULL,
  `document_version` varchar(64) NOT NULL,
  `granted` bit(1) NOT NULL,
  `purpose` varchar(32) NOT NULL,
  `source` varchar(32) NOT NULL,
  `user_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_legal_user` (`user_id`),
  KEY `idx_legal_purpose` (`purpose`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `memberships`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `memberships` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `accepted_at` datetime(6) DEFAULT NULL,
  `active` bit(1) NOT NULL,
  `invited_at` datetime(6) DEFAULT NULL,
  `org_role` enum('HIRING_MANAGER','OWNER','RECRUTEUR') NOT NULL,
  `organization_id` bigint(20) NOT NULL,
  `user_id` bigint(20) NOT NULL,
  `suspended_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKa40gitejf8o2j6ofjm2qntn5g` (`organization_id`,`user_id`),
  KEY `FKdjormybfoo7f4i4d4r803qohb` (`user_id`),
  CONSTRAINT `FKdjormybfoo7f4i4d4r803qohb` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKhl8m9f3nlulw6lo6elrbf9gsd` FOREIGN KEY (`organization_id`) REFERENCES `organizations` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=107 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `notifications`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `notifications` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `body` varchar(500) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `kind` varchar(48) NOT NULL,
  `read_flag` bit(1) NOT NULL,
  `title` varchar(160) NOT NULL,
  `user_id` bigint(20) NOT NULL,
  `link` varchar(120) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_notif_user` (`user_id`),
  KEY `idx_notif_user_read` (`user_id`,`read_flag`),
  KEY `idx_notif_created` (`created_at`)
) ENGINE=InnoDB AUTO_INCREMENT=1525 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `offer_reports`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `offer_reports` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `job_offer_id` bigint(20) NOT NULL,
  `reason` varchar(500) DEFAULT NULL,
  `reporter_user_id` bigint(20) NOT NULL,
  `handled_by_email` varchar(160) DEFAULT NULL,
  `resolved_at` datetime(6) DEFAULT NULL,
  `status` varchar(16) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKhw4kunbh2k812611mruih8d56` (`job_offer_id`,`reporter_user_id`),
  KEY `idx_report_offer` (`job_offer_id`),
  KEY `idx_report_reporter` (`reporter_user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=90013 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `organization_events`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `organization_events` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `category` varchar(32) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `description` varchar(2000) DEFAULT NULL,
  `duration_minutes` int(11) NOT NULL,
  `location` varchar(120) DEFAULT NULL,
  `starts_at` datetime(6) NOT NULL,
  `title` varchar(180) NOT NULL,
  `video_url` varchar(500) DEFAULT NULL,
  `created_by_user_id` bigint(20) NOT NULL,
  `organization_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_org_events_org_start` (`organization_id`,`starts_at`),
  KEY `FKaqa0p9yo4u5s74g0f3fce2775` (`created_by_user_id`),
  CONSTRAINT `FKaqa0p9yo4u5s74g0f3fce2775` FOREIGN KEY (`created_by_user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKqe3yi1jw3hidqdnk066xrmd5i` FOREIGN KEY (`organization_id`) REFERENCES `organizations` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `organization_invitations`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `organization_invitations` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `accepted_at` datetime(6) DEFAULT NULL,
  `email` varchar(160) NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `org_role` enum('HIRING_MANAGER','OWNER','RECRUTEUR') NOT NULL,
  `revoked_at` datetime(6) DEFAULT NULL,
  `token_hash` varchar(64) NOT NULL,
  `version` bigint(20) NOT NULL,
  `organization_id` bigint(20) NOT NULL,
  `user_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_org_invitation_token` (`token_hash`),
  KEY `idx_org_invitation_email` (`email`),
  KEY `FKpt2hiwb0x73kxxm65yxabtvc` (`organization_id`),
  KEY `FK2vuf4opkmop31o75dbhw73jmt` (`user_id`),
  CONSTRAINT `FK2vuf4opkmop31o75dbhw73jmt` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKpt2hiwb0x73kxxm65yxabtvc` FOREIGN KEY (`organization_id`) REFERENCES `organizations` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `organization_notes`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `organization_notes` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `content` varchar(8000) DEFAULT NULL,
  `drawing_data` text DEFAULT NULL,
  `ink_color` varchar(20) NOT NULL,
  `note_color` varchar(20) NOT NULL,
  `position_x` int(11) NOT NULL,
  `position_y` int(11) NOT NULL,
  `title` varchar(120) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `organization_id` bigint(20) NOT NULL,
  `owner_user_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_org_notes_owner_updated` (`organization_id`,`owner_user_id`,`updated_at`),
  KEY `FKj3v1tq3r5lw5554bbblpjjmt1` (`owner_user_id`),
  CONSTRAINT `FKb1y4gm73nutqhfs2j8e4h7ust` FOREIGN KEY (`organization_id`) REFERENCES `organizations` (`id`),
  CONSTRAINT `FKj3v1tq3r5lw5554bbblpjjmt1` FOREIGN KEY (`owner_user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `organizations`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `organizations` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `ice` varchar(32) DEFAULT NULL,
  `name` varchar(200) NOT NULL,
  `plan_tier` enum('ACCES','ESSENTIEL','PERFORMANCE','PREMIUM') NOT NULL,
  `verified` bit(1) NOT NULL,
  `owner_user_id` bigint(20) DEFAULT NULL,
  `verification_status` varchar(48) DEFAULT NULL,
  `city` varchar(80) DEFAULT NULL,
  `employee_range` varchar(40) DEFAULT NULL,
  `logo_url` varchar(400) DEFAULT NULL,
  `presentation` varchar(1200) DEFAULT NULL,
  `sector` varchar(80) DEFAULT NULL,
  `verification_method` varchar(24) DEFAULT NULL,
  `website` varchar(180) DEFAULT NULL,
  `ice_address` varchar(400) DEFAULT NULL,
  `ice_checked_at` datetime(6) DEFAULT NULL,
  `ice_format_valid` bit(1) DEFAULT NULL,
  `ice_legal_name` varchar(200) DEFAULT NULL,
  `ice_source` varchar(180) DEFAULT NULL,
  `cancel_at_period_end` bit(1) DEFAULT NULL,
  `subscription_ends_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK37dv86ymr1mh8lhcosssu5rc6` (`owner_user_id`),
  CONSTRAINT `FK37dv86ymr1mh8lhcosssu5rc6` FOREIGN KEY (`owner_user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=46 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `password_reset_tokens`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `password_reset_tokens` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `token_hash` varchar(64) NOT NULL,
  `used_at` datetime(6) DEFAULT NULL,
  `user_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKajre85ybxavf1tt4omkrs5p6g` (`token_hash`),
  KEY `idx_reset_hash` (`token_hash`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `payment_transactions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `payment_transactions` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `actor_user_id` bigint(20) NOT NULL,
  `amount_ttc` double NOT NULL,
  `completed_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `currency` varchar(8) NOT NULL,
  `failure_reason` varchar(255) DEFAULT NULL,
  `idempotency_key` varchar(80) NOT NULL,
  `provider` varchar(16) NOT NULL,
  `reference_code` varchar(64) NOT NULL,
  `scenario` varchar(16) NOT NULL,
  `status` enum('FAILED','PENDING','SUCCEEDED') NOT NULL,
  `invoice_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_idempotency` (`idempotency_key`),
  UNIQUE KEY `uk_payment_reference` (`reference_code`),
  KEY `FKe3j6ngpdeef8pjn1qg4p6uaan` (`invoice_id`),
  CONSTRAINT `FKe3j6ngpdeef8pjn1qg4p6uaan` FOREIGN KEY (`invoice_id`) REFERENCES `billing_invoices` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `refusal_reason_catalog`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `refusal_reason_catalog` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `active` bit(1) NOT NULL,
  `code` varchar(64) NOT NULL,
  `label` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKie0ria696e0c5m3evw5iu3jfw` (`code`)
) ENGINE=InnoDB AUTO_INCREMENT=19 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `scorecard_criteria`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `scorecard_criteria` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `job_offer_id` bigint(20) NOT NULL,
  `label` varchar(120) NOT NULL,
  `veto` bit(1) NOT NULL,
  `weight` int(11) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_sc_offer` (`job_offer_id`)
) ENGINE=InnoDB AUTO_INCREMENT=127 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `scorecard_entries`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `scorecard_entries` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `application_id` bigint(20) NOT NULL,
  `comment` varchar(500) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `criterion_id` bigint(20) NOT NULL,
  `evaluator_user_id` bigint(20) NOT NULL,
  `score` int(11) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_se_application` (`application_id`),
  KEY `idx_se_criterion` (`criterion_id`),
  KEY `idx_se_evaluator` (`evaluator_user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=325 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `sent_emails`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `sent_emails` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `body_html` mediumtext DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `error` varchar(200) DEFAULT NULL,
  `kind` varchar(24) NOT NULL,
  `recipient` varchar(160) NOT NULL,
  `status` enum('FAILED','LOGGED','SENT') NOT NULL,
  `subject` varchar(200) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_sent_emails_created` (`created_at`)
) ENGINE=InnoDB AUTO_INCREMENT=328 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `tags`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `tags` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `name` varchar(64) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKt48xdq560gs3gap9g7jg36kgc` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=135 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `user_avatars`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `user_avatars` (
  `user_id` bigint(20) NOT NULL,
  `content_type` varchar(40) NOT NULL,
  `data` tinyblob NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `user_sessions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `user_sessions` (
  `id` varchar(36) NOT NULL,
  `absolute_expires_at` datetime(6) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `device_hash` varchar(64) DEFAULT NULL,
  `device_label` varchar(120) DEFAULT NULL,
  `idle_expires_at` datetime(6) NOT NULL,
  `ip` varchar(64) DEFAULT NULL,
  `last_used_at` datetime(6) NOT NULL,
  `previous_hash` varchar(64) DEFAULT NULL,
  `revoked_at` datetime(6) DEFAULT NULL,
  `revoked_reason` varchar(40) DEFAULT NULL,
  `token_hash` varchar(64) NOT NULL,
  `user_agent` varchar(300) DEFAULT NULL,
  `user_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_session_user` (`user_id`),
  KEY `idx_session_hash` (`token_hash`),
  KEY `idx_session_prev` (`previous_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `users`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `users` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `active` bit(1) NOT NULL,
  `company` varchar(120) DEFAULT NULL,
  `company_ice` varchar(32) DEFAULT NULL,
  `company_verified` bit(1) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `email` varchar(160) NOT NULL,
  `email_verified` bit(1) NOT NULL,
  `experience_level` varchar(40) DEFAULT NULL,
  `full_name` varchar(160) NOT NULL,
  `google_sub` varchar(128) DEFAULT NULL,
  `must_set_password` bit(1) NOT NULL,
  `password_hash` varchar(255) DEFAULT NULL,
  `preferred_contracts` varchar(120) DEFAULT NULL,
  `preferred_locations` varchar(300) DEFAULT NULL,
  `profile_complete` bit(1) NOT NULL,
  `role` enum('ADMINISTRATEUR','CANDIDAT','RECRUTEUR') NOT NULL,
  `skills` varchar(500) DEFAULT NULL,
  `sought_roles` varchar(400) DEFAULT NULL,
  `assigned_org_id` bigint(20) DEFAULT NULL,
  `plan_tier` enum('ACCES','ESSENTIEL','PERFORMANCE','PREMIUM') DEFAULT NULL,
  `verification_status` varchar(48) DEFAULT NULL,
  `cv_file_name` varchar(255) DEFAULT NULL,
  `cv_sections` text DEFAULT NULL,
  `cv_storage_name` varchar(255) DEFAULT NULL,
  `company_presentation` varchar(800) DEFAULT NULL,
  `job_title` varchar(120) DEFAULT NULL,
  `phone` varchar(40) DEFAULT NULL,
  `school` varchar(160) DEFAULT NULL,
  `work_mode` varchar(40) DEFAULT NULL,
  `birth_date` date DEFAULT NULL,
  `city` varchar(80) DEFAULT NULL,
  `first_name` varchar(80) DEFAULT NULL,
  `last_name` varchar(80) DEFAULT NULL,
  `nationality` varchar(80) DEFAULT NULL,
  `phone_country` varchar(80) DEFAULT NULL,
  `availability` varchar(40) DEFAULT NULL,
  `gender` varchar(24) DEFAULT NULL,
  `mfa_backup_hashes` varchar(1000) DEFAULT NULL,
  `mfa_enabled` bit(1) NOT NULL,
  `mfa_secret_enc` varchar(255) DEFAULT NULL,
  `password_changed_at` datetime(6) DEFAULT NULL,
  `email_notifications` bit(1) DEFAULT NULL,
  `education_domain` varchar(120) DEFAULT NULL,
  `graduation_year` int(11) DEFAULT NULL,
  `cancel_at_period_end` bit(1) DEFAULT NULL,
  `subscription_ends_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK6dotkott2kjsp8vw4d0m25fb7` (`email`),
  UNIQUE KEY `UKfla1a4b51hmn2n4kuw14u210i` (`google_sub`),
  KEY `idx_users_role` (`role`)
) ENGINE=InnoDB AUTO_INCREMENT=142 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `webauthn_credentials`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `webauthn_credentials` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `credential_id` varchar(512) NOT NULL,
  `last_used_at` datetime(6) DEFAULT NULL,
  `name` varchar(80) NOT NULL,
  `public_key_cose` text NOT NULL,
  `signature_count` bigint(20) NOT NULL,
  `user_handle` varchar(96) NOT NULL,
  `user_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKglrl82tpoldymtubv6p3nx26c` (`credential_id`),
  KEY `idx_wa_user` (`user_id`),
  KEY `idx_wa_handle` (`user_handle`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

