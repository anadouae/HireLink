-- HireLink — logos des entreprises de la base de démonstration.
-- Met à jour UNIQUEMENT organizations.logo_url ; n'insère ni ne supprime aucune ligne. Rejouable sans effet de bord.
-- Toutes les adresses sont locales (/api/v1/media/org-logos/...), servies par l'application elle-même :
-- la démonstration fonctionne donc sans connexion Internet. Les fichiers sont dans backend/data/org-logos/.
-- Si un fichier manque (installation neuve sans backend/data), le front affiche les initiales de l'entreprise.

SET NAMES utf8mb4;
START TRANSACTION;

UPDATE organizations SET logo_url = '/api/v1/media/org-logos/org-1/263d7481-5d73-4948-8bdf-a298114c115a.jpg'
 WHERE TRIM(name) = 'Atlas Digital';

UPDATE organizations SET logo_url = '/api/v1/media/org-logos/org-3/cb46ead4-5fac-4688-972f-482c37e266ef.png'
 WHERE TRIM(name) = 'OCP Group';

UPDATE organizations SET logo_url = '/api/v1/media/org-logos/org-4/980bca35-eca1-447e-b791-9176ef482200.png'
 WHERE TRIM(name) = 'Maroc Telecom';

UPDATE organizations SET logo_url = '/api/v1/media/org-logos/org-5/764dad06-22a9-4f74-a2c6-a6c7f0109c23.png'
 WHERE TRIM(name) = 'Attijariwafa bank';

UPDATE organizations SET logo_url = '/api/v1/media/org-logos/org-6/377b7c8c-738b-4b1b-8ec0-ded4fa4693a7.png'
 WHERE TRIM(name) = 'Royal Air Maroc';

UPDATE organizations SET logo_url = '/api/v1/media/org-logos/org-7/2b99ba6c-61d8-4793-baf5-0932924dff54.png'
 WHERE TRIM(name) = 'Renault Group Maroc';

UPDATE organizations SET logo_url = '/api/v1/media/org-logos/org-8/67cf8eda-d0b3-415f-ba39-571271f2c010.png'
 WHERE TRIM(name) = 'Stellantis';

UPDATE organizations SET logo_url = '/api/v1/media/org-logos/org-9/5ef9e20e-1e12-4509-a703-3fcbcf977059.png'
 WHERE TRIM(name) = 'inwi';

UPDATE organizations SET logo_url = '/api/v1/media/org-logos/org-10/758994a3-57f6-4aa2-b41e-c44ff4992dda.png'
 WHERE TRIM(name) = 'Intelcia';

UPDATE organizations SET logo_url = '/api/v1/media/org-logos/org-11/b07ba908-53aa-4ba2-828c-ec5ede819b53.png'
 WHERE TRIM(name) = 'Capgemini Maroc';

UPDATE organizations SET logo_url = '/api/v1/media/org-logos/org-12/c4030cc9-b089-4fcb-983a-028acb5c4f91.png'
 WHERE TRIM(name) = 'CGI Maroc';

UPDATE organizations SET logo_url = '/api/v1/media/org-logos/org-13/3ef08db8-714c-4897-affc-80d8989ce9b9.png'
 WHERE TRIM(name) = 'HPS';

UPDATE organizations SET logo_url = '/api/v1/media/org-logos/org-14/3730638e-22d0-457b-ac73-ae8f8c6cdeb7.png'
 WHERE TRIM(name) = 'Bank of Africa';

UPDATE organizations SET logo_url = '/api/v1/media/org-logos/org-15/f50003fb-c13f-4a30-94a1-c2f70d9955e1.png'
 WHERE TRIM(name) = 'Marjane';

UPDATE organizations SET logo_url = '/api/v1/media/org-logos/org-16/15c53b44-cc05-4021-9ad8-e2d324eb1af3.png'
 WHERE TRIM(name) = 'Decathlon Maroc';

UPDATE organizations SET logo_url = '/api/v1/media/org-logos/org-17/f314efa3-bab6-43a9-942f-c32e3b9e4c81.png'
 WHERE TRIM(name) = 'Cosumar';

UPDATE organizations SET logo_url = '/api/v1/media/org-logos/org-18/034cb20e-1696-4e8f-b1c3-09bd5ffb2877.png'
 WHERE TRIM(name) = 'Managem';

UPDATE organizations SET logo_url = '/api/v1/media/org-logos/org-19/0582dfd1-acbf-4b3d-8e41-93751852f239.png'
 WHERE TRIM(name) = 'ONCF';

UPDATE organizations SET logo_url = '/api/v1/media/org-logos/org-20/d8452541-98b6-4326-826e-6acb8f9ebc23.png'
 WHERE TRIM(name) = 'Tanger Med';

UPDATE organizations SET logo_url = '/api/v1/media/org-logos/org-21/4baf4f33-88e0-471a-9e86-01f5ae80c35a.png'
 WHERE TRIM(name) = 'Accor';

UPDATE organizations SET logo_url = '/api/v1/media/org-logos/org-22/4d6f8df0-6548-452f-9f7c-e70bc90143f6.png'
 WHERE TRIM(name) = 'Centrale Danone';

UPDATE organizations SET logo_url = '/api/v1/media/org-logos/org-23/07690343-1e88-4bbc-a9ef-43718ff23842.png'
 WHERE TRIM(name) = 'LabelVie';

UPDATE organizations SET logo_url = '/api/v1/media/org-logos/org-24/5ee8e8fe-8d00-4908-867e-7b77167a64e5.png'
 WHERE TRIM(name) = 'Wafa Assurance';

UPDATE organizations SET logo_url = '/api/v1/media/org-logos/org-25/ee7c4ca7-78d2-4e86-aef8-ea8b8512dffb.png'
 WHERE TRIM(name) = 'Alstom Maroc';

UPDATE organizations SET logo_url = '/api/v1/media/org-logos/org-28/7d0f2171-1d8f-4daa-83a9-0f7bd0ada984.png'
 WHERE TRIM(name) = 'Medina Soft';

UPDATE organizations SET logo_url = '/api/v1/media/org-logos/org-29/f8ea5be9-bf3d-426e-84a1-3d84121a0ab1.png'
 WHERE TRIM(name) = 'Rif Logistics';

UPDATE organizations SET logo_url = '/api/v1/media/org-logos/org-30/e49d58a2-c17e-4d1f-a4fd-f81c1dd1e69d.png'
 WHERE TRIM(name) = 'Atlantic Cloud';

UPDATE organizations SET logo_url = '/api/v1/media/org-logos/org-31/9868a89a-7cd2-422e-83a6-fe6a12d11040.png'
 WHERE TRIM(name) = 'Kasbah Analytics';

UPDATE organizations SET logo_url = '/api/v1/media/org-logos/org-32/logo.png'
 WHERE TRIM(name) = 'Bouregreg Pay';

UPDATE organizations SET logo_url = '/api/v1/media/org-logos/org-33/logo.png'
 WHERE TRIM(name) = 'Solaris Nord Énergies';

UPDATE organizations SET logo_url = '/api/v1/media/org-logos/org-34/f41f1a59-d637-47d2-b1e4-14f18bf074f6.png'
 WHERE TRIM(name) = 'Zellige Studio';

UPDATE organizations SET logo_url = '/api/v1/media/org-logos/org-35/logo.png'
 WHERE TRIM(name) = 'Nova RH';

UPDATE organizations SET logo_url = '/api/v1/media/org-logos/org-36/logo.png'
 WHERE TRIM(name) = 'Tanja MedTech';

UPDATE organizations SET logo_url = '/api/v1/media/org-logos/org-37/logo.png'
 WHERE TRIM(name) = 'Oued Martil Automation';

UPDATE organizations SET logo_url = '/api/v1/media/org-logos/org-38/logo.png'
 WHERE TRIM(name) = 'Orange Maroc';

UPDATE organizations SET logo_url = '/api/v1/media/org-logos/org-39/logo.png'
 WHERE TRIM(name) = 'CIH Bank';

UPDATE organizations SET logo_url = '/api/v1/media/org-logos/org-40/ce17e9f3-4e67-4093-acb2-d1a65d0696a4.png'
 WHERE TRIM(name) = 'Deloitte Maroc';

UPDATE organizations SET logo_url = '/api/v1/media/org-logos/org-41/5cbc301a-9634-48fa-8e04-716181ffb125.png'
 WHERE TRIM(name) = 'Yazaki Maroc';

UPDATE organizations SET logo_url = '/api/v1/media/org-logos/org-42/06d80ea3-63e3-4da5-9c80-5b115dc33a46.png'
 WHERE TRIM(name) = 'Lear Corporation Maroc';

UPDATE organizations SET logo_url = '/api/v1/media/org-logos/org-43/bf675356-1653-4fa2-97c1-ae221e04bb7b.png'
 WHERE TRIM(name) = 'Valeo Maroc';

UPDATE organizations SET logo_url = '/api/v1/media/org-logos/org-44/45ea8489-4bbb-4bb1-8f7f-1ad75d5c27be.png'
 WHERE TRIM(name) = 'Sopra Steria Maroc';

UPDATE organizations SET logo_url = '/api/v1/media/org-logos/org-45/b4641da7-5147-401a-b024-b96b047cfbaf.png'
 WHERE TRIM(name) = 'Schneider Electric Maroc';

COMMIT;

-- Contrôle : 0 ligne attendue (aucun logo ne doit pointer hors de localhost)
SELECT id, name, logo_url FROM organizations WHERE logo_url LIKE 'http%';
