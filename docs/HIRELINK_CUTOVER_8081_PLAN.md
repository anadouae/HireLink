# Cutover 8081 — plan sécurisé (NE PAS EXÉCUTER sans autorisation explicite)

Date de préparation : 2026-11-15. JAR candidat isolé déjà construit ; ce document ne lance **aucun** stop/start sur 8081.

## Candidat

| Champ | Valeur |
|-------|--------|
| JAR | `docs/deploy-prep/b0-candidate-runtime/hirelink-candidate-20261115-095739-E65802C4A567.jar` (ou `hirelink-b0-backend-latest.jar`) |
| SHA-256 (build) | `E65802C4A567CA159DB6E378C782A3B5E679D23C8E38C9A37EC2051CC779E644` |
| Smoke isolé | port **18086** (pay-iso) + tests billing ; marqueur `b0-fatjar-smoke` |
| Démo actuelle | 8081 PID **17008** sur JAR verrouillé `C63FC7EE…` (classloader dégradé → paiement 500) — **ne pas tuer globalement** |
| Note | Ne jamais écraser un JAR utilisé par une JVM active |

## Prérequis GO/NO-GO

1. `GET http://127.0.0.1:8081/api/v1/health` → UP
2. Offres hirelink = 123 (pas de wipe)
3. `scripts/hirelink-process-guard.ps1` → `Assert-DemoStillUp`
4. Backup SQL frais : `mysqldump` hirelink → `backup-db/` (hors git)
5. SHA candidat relu ; contenu fat (`JarLauncher`, `ExternalOfferListability`)
6. Smoke 18084 PASS (places MA sans Paris ; external sans MAROC_EMPLOI)

## Séquence proposée (autorisation requise)

1. Copier JAR candidat vers nom distinct runtime (ex. `hirelink-b0-backend-next.jar`) **sans** écraser le JAR démo archivé.
2. Noter PID 8081 + CommandLine exacte.
3. Stop **uniquement** ce PID (jamais `taskkill /F /IM java.exe`).
4. Démarrer le nouveau JAR sur 8081 avec la **même** config runtime démo (`application-cutover-runtime.properties` / secrets locaux).
5. Attendre health UP (timeout 3–5 min si DDL).
6. Smoke : auth candidat/recruteur, `catalog/offers` B0, `catalog/places?q=Ta&country=MA` (pas de France), pipeline, timeline.
7. Si NO-GO → rollback (étape suivante).

## Rollback

1. Stop PID du nouveau process uniquement.
2. Relancer l’ancien JAR B0 (SHA précédent F0197F1A… / copie `demo-runtime`) sur 8081.
3. Health + smoke auth + compteurs offres 123.

## Interdits

- Pas de `DROP` / wipe hirelink
- Pas d’activation connecteurs externes
- Pas de kill Java global
- Pas de cutover sans message d’autorisation explicite de l’utilisateur
