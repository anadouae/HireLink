-- HireLink — logos officiels des entreprises marocaines déjà présentes en base.
-- Met à jour UNIQUEMENT organizations.logo_url ; n'insère ni ne supprime aucune ligne.
-- Identification par nom (insensible à la casse et aux espaces). Rejouable sans effet de bord.
-- Sources : fichiers de logo officiels hébergés par Wikimedia (upload.wikimedia.org) ;
-- Intelcia : logo du site officiel intelcia.com. Chaque URL a été vérifiée (HTTP 200, type image/*) le 01/11/2026.

SET NAMES utf8mb4;
START TRANSACTION;

-- OCP Group
UPDATE organizations SET logo_url = 'https://upload.wikimedia.org/wikipedia/commons/thumb/d/d9/Ocp-group.png/330px-Ocp-group.png'
 WHERE LOWER(TRIM(name)) IN ('ocp group', 'ocp', 'groupe ocp', 'ocp sa');
-- Maroc Telecom
UPDATE organizations SET logo_url = 'https://upload.wikimedia.org/wikipedia/commons/thumb/d/de/Maroc_Telecom_logo.png/330px-Maroc_Telecom_logo.png'
 WHERE LOWER(TRIM(name)) IN ('maroc telecom', 'itissalat al-maghrib', 'iam');
-- Attijariwafa bank
UPDATE organizations SET logo_url = 'https://upload.wikimedia.org/wikipedia/fr/thumb/1/1d/Logo_AWB.svg/330px-Logo_AWB.svg.png'
 WHERE LOWER(TRIM(name)) IN ('attijariwafa bank', 'attijariwafa');
-- Royal Air Maroc
UPDATE organizations SET logo_url = 'https://upload.wikimedia.org/wikipedia/fr/thumb/c/c7/Logo_Royal_Air_Maroc_2013_%28Horizontal%29.svg/330px-Logo_Royal_Air_Maroc_2013_%28Horizontal%29.svg.png'
 WHERE LOWER(TRIM(name)) IN ('royal air maroc', 'ram');
-- Renault Group Maroc
UPDATE organizations SET logo_url = 'https://upload.wikimedia.org/wikipedia/commons/thumb/0/09/2021_Renault_Group_logo.svg/330px-2021_Renault_Group_logo.svg.png'
 WHERE LOWER(TRIM(name)) IN ('renault group maroc', 'renault', 'renault maroc', 'renault tanger');
-- Stellantis
UPDATE organizations SET logo_url = 'https://upload.wikimedia.org/wikipedia/commons/thumb/d/dd/Stellantis.svg/330px-Stellantis.svg.png'
 WHERE LOWER(TRIM(name)) IN ('stellantis', 'stellantis kénitra', 'stellantis maroc');
-- inwi
UPDATE organizations SET logo_url = 'https://upload.wikimedia.org/wikipedia/commons/thumb/e/ef/Logo_inwi.svg/330px-Logo_inwi.svg.png'
 WHERE LOWER(TRIM(name)) IN ('inwi', 'wana corporate');
-- Orange Maroc
UPDATE organizations SET logo_url = 'https://upload.wikimedia.org/wikipedia/commons/thumb/c/c8/Orange_logo.svg/330px-Orange_logo.svg.png'
 WHERE LOWER(TRIM(name)) IN ('orange maroc', 'orange');
-- CIH Bank
UPDATE organizations SET logo_url = 'https://upload.wikimedia.org/wikipedia/fr/b/b2/Logo_CIH.png'
 WHERE LOWER(TRIM(name)) IN ('cih bank', 'cih');
-- Deloitte
UPDATE organizations SET logo_url = 'https://upload.wikimedia.org/wikipedia/commons/thumb/e/ed/Logo_of_Deloitte.svg/330px-Logo_of_Deloitte.svg.png'
 WHERE LOWER(TRIM(name)) IN ('deloitte', 'deloitte maroc');
-- Yazaki
UPDATE organizations SET logo_url = 'https://upload.wikimedia.org/wikipedia/commons/thumb/3/3a/Yazaki_company_logo.svg/330px-Yazaki_company_logo.svg.png'
 WHERE LOWER(TRIM(name)) IN ('yazaki', 'yazaki morocco', 'yazaki maroc');
-- Lear
UPDATE organizations SET logo_url = 'https://upload.wikimedia.org/wikipedia/commons/thumb/0/02/Lear_Corporation_logo.svg/330px-Lear_Corporation_logo.svg.png'
 WHERE LOWER(TRIM(name)) IN ('lear', 'lear corporation', 'lear maroc');
-- Valeo
UPDATE organizations SET logo_url = 'https://upload.wikimedia.org/wikipedia/commons/thumb/2/2b/Valeo_Logo.svg/330px-Valeo_Logo.svg.png'
 WHERE LOWER(TRIM(name)) IN ('valeo', 'valeo maroc');
-- Intelcia
UPDATE organizations SET logo_url = 'https://www.intelcia.com/themes/custom/intelcia/corpo/images/logo-gradient.svg'
 WHERE LOWER(TRIM(name)) IN ('intelcia');
-- Capgemini Maroc
UPDATE organizations SET logo_url = 'https://upload.wikimedia.org/wikipedia/fr/thumb/b/b5/Capgemini_Logo.svg/330px-Capgemini_Logo.svg.png'
 WHERE LOWER(TRIM(name)) IN ('capgemini maroc', 'capgemini');
-- CGI Maroc
UPDATE organizations SET logo_url = 'https://upload.wikimedia.org/wikipedia/commons/thumb/3/32/CGI_logo.svg/330px-CGI_logo.svg.png'
 WHERE LOWER(TRIM(name)) IN ('cgi maroc', 'cgi');
-- HPS
UPDATE organizations SET logo_url = 'https://upload.wikimedia.org/wikipedia/fr/thumb/6/6d/Logo_hps_0.png/330px-Logo_hps_0.png'
 WHERE LOWER(TRIM(name)) IN ('hps', 'hps worldwide', 'hightech payment systems');
-- Bank of Africa
UPDATE organizations SET logo_url = 'https://upload.wikimedia.org/wikipedia/commons/thumb/6/67/Bank_of_Africa_Logo.png/330px-Bank_of_Africa_Logo.png'
 WHERE LOWER(TRIM(name)) IN ('bank of africa', 'bmce', 'bmce bank', 'bmce bank of africa', 'bank of africa - bmce group');
-- Marjane
UPDATE organizations SET logo_url = 'https://upload.wikimedia.org/wikipedia/commons/thumb/7/71/Marjane_Holding_Logo.svg/330px-Marjane_Holding_Logo.svg.png'
 WHERE LOWER(TRIM(name)) IN ('marjane', 'marjane holding');
-- Decathlon Maroc
UPDATE organizations SET logo_url = 'https://upload.wikimedia.org/wikipedia/commons/thumb/b/bc/Decathlon_-_logo_%28France%2C_2024%29.svg/330px-Decathlon_-_logo_%28France%2C_2024%29.svg.png'
 WHERE LOWER(TRIM(name)) IN ('decathlon maroc', 'decathlon');
-- Cosumar
UPDATE organizations SET logo_url = 'https://upload.wikimedia.org/wikipedia/commons/thumb/5/5c/Logo_nouveau_cosumar.png/330px-Logo_nouveau_cosumar.png'
 WHERE LOWER(TRIM(name)) IN ('cosumar');
-- Managem
UPDATE organizations SET logo_url = 'https://upload.wikimedia.org/wikipedia/fr/thumb/1/11/Managem.png/330px-Managem.png'
 WHERE LOWER(TRIM(name)) IN ('managem', 'groupe managem');
-- ONCF
UPDATE organizations SET logo_url = 'https://upload.wikimedia.org/wikipedia/fr/thumb/e/e5/Logo_ONCF.svg/330px-Logo_ONCF.svg.png'
 WHERE LOWER(TRIM(name)) IN ('oncf', 'office national des chemins de fer');
-- Tanger Med
UPDATE organizations SET logo_url = 'https://upload.wikimedia.org/wikipedia/commons/thumb/9/95/Tanger_Med_logo.png/330px-Tanger_Med_logo.png'
 WHERE LOWER(TRIM(name)) IN ('tanger med', 'tanger med port authority', 'tmsa');
-- Accor
UPDATE organizations SET logo_url = 'https://upload.wikimedia.org/wikipedia/fr/thumb/c/c3/Accor_Logo.png/330px-Accor_Logo.png'
 WHERE LOWER(TRIM(name)) IN ('accor', 'accor maroc');
-- Centrale Danone
UPDATE organizations SET logo_url = 'https://upload.wikimedia.org/wikipedia/commons/thumb/c/c9/Logo-Centrale-Danone.png/330px-Logo-Centrale-Danone.png'
 WHERE LOWER(TRIM(name)) IN ('centrale danone');
-- LabelVie
UPDATE organizations SET logo_url = 'https://upload.wikimedia.org/wikipedia/commons/thumb/e/e7/Iabel%27vie.png/330px-Iabel%27vie.png'
 WHERE LOWER(TRIM(name)) IN ('labelvie', 'label''vie', 'label vie');
-- Wafa Assurance
UPDATE organizations SET logo_url = 'https://upload.wikimedia.org/wikipedia/fr/3/38/Waa.gif'
 WHERE LOWER(TRIM(name)) IN ('wafa assurance');
-- Alstom Maroc
UPDATE organizations SET logo_url = 'https://upload.wikimedia.org/wikipedia/commons/thumb/1/1c/Alstom_logo.svg/330px-Alstom_logo.svg.png'
 WHERE LOWER(TRIM(name)) IN ('alstom maroc', 'alstom');
-- Banque Populaire
UPDATE organizations SET logo_url = 'https://upload.wikimedia.org/wikipedia/commons/thumb/b/b8/Logo_BCP.svg/330px-Logo_BCP.svg.png'
 WHERE LOWER(TRIM(name)) IN ('banque populaire', 'banque centrale populaire', 'bcp', 'groupe banque populaire');
-- Akwa Group
UPDATE organizations SET logo_url = 'https://upload.wikimedia.org/wikipedia/commons/8/83/Akwa_Group.png'
 WHERE LOWER(TRIM(name)) IN ('akwa group', 'akwa');
-- Afriquia
UPDATE organizations SET logo_url = 'https://upload.wikimedia.org/wikipedia/fr/thumb/3/3f/Afriquialogo.png/330px-Afriquialogo.png'
 WHERE LOWER(TRIM(name)) IN ('afriquia', 'afriquia smdc');
-- ONEE
UPDATE organizations SET logo_url = 'https://upload.wikimedia.org/wikipedia/commons/thumb/2/25/ONEE_logo.png/330px-ONEE_logo.png'
 WHERE LOWER(TRIM(name)) IN ('onee', 'office national de l''electricité et de l''eau potable');

COMMIT;

-- Contrôle : entreprises encore sans logo ou avec l'ancien favicon Google.
SELECT id, name, logo_url FROM organizations
 WHERE logo_url IS NULL OR logo_url = '' OR logo_url LIKE 'https://www.google.com/s2/favicons%'
 ORDER BY name;
