-- Régularisation IDEMPOTENTE des dossiers REJECTED encore « actifs » côté entretien.
-- Ne PAS exécuter sur hirelink sans autorisation explicite.
-- Effet : passe interview_status à CANCELLED + motif technique ; conserve interview_at et l'historique timeline.
-- N'envoie AUCUNE notification (SQL pur).

-- Cible actuelle (Atlas Digital démo) :
--   id=1  Sara El Amrani / Développeur Full-Stack Java — produit ATS
--   id=4  Youssef Idrissi / Data Analyst
--   id=94 Lina Mernissi / Product Designer UX

START TRANSACTION;

UPDATE applications
SET interview_status = 'CANCELLED',
    interview_cancel_reason = COALESCE(
        NULLIF(TRIM(interview_cancel_reason), ''),
        'Régularisation : candidature déjà refusée — entretien clôturé sans notification.'
    )
WHERE status = 'REJECTED'
  AND interview_at IS NOT NULL
  AND (interview_status IS NULL OR interview_status = 'SCHEDULED')
  AND id IN (1, 4, 94);

-- Vérification attendue : 3 lignes, status CANCELLED
SELECT id, status, interview_status, interview_at, LEFT(interview_cancel_reason, 80) AS motif
FROM applications
WHERE id IN (1, 4, 94);

-- COMMIT;   -- décommenter uniquement après validation humaine
-- ROLLBACK; -- sinon
