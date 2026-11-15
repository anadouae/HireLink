# Cutover final 8081 — rapport

**Résultat : CUTOVER PASS**

Date : 2026-11-15. Aucun commit / push. Connecteurs non activés. Seed OFF.

## Processus

| | Avant | Après |
|--|-------|-------|
| PID | **17008** | **35024** |
| JAR | `hirelink-b0-backend.jar` | `hirelink-candidate-20261115-095739-E65802C4A567.jar` |
| SHA-256 | `C63FC7EE…` (classloader dégradé) | `E65802C4A567CA159DB6E378C782A3B5E679D23C8E38C9A37EC2051CC779E644` |

Config : `application-cutover-runtime.properties` + flags seed/sync/connectors **false**, `ddl-auto=update`.
Backup : `backup-db/hirelink-pre-cutover-final-20261115-100951.sql` (~2,2 Mo).
JAR démo archivé / legacy précédents : **préservés** (non écrasés).

## Tests post-cutover

| Test | Résultat |
|------|----------|
| Health UP | PASS |
| NoClassDefFoundError (log) | PASS (absent) |
| Offres DB 123 | PASS |
| Catalogue 107 | PASS |
| MAROC_EMPLOI = 0 | PASS |
| Places `Ta&MA` (pas de France) | PASS (Taza, Tanger, …) |
| `minMatch=60` | PASS (total=9) |
| Login candidat / recruteur / OWNER / admin | PASS |
| Pipeline (22 dossiers) | PASS |
| Timeline app#1 | PASS (API OK) |
| OWNER quote PREMIUM + factures | PASS |
| Admin stats + `/admin/reports` 200 | PASS |
| Crédits IA plan org **PREMIUM** 192/200 | PASS (F5 live) |
| Entretiens (3 INTERVIEW → 0 à venir / 3 à replanifier — créneaux passés) | PASS cohérent |
| FE 5500 | PASS |

Paiement / refus / annulation **non rejoués sur `hirelink`** (isolé déjà validé avant cutover).

## Données

- `hirelink.job_offers` = **123**
- Catalogue = **107**
- Connecteurs OFF

## Anomalies restantes

- **3** dossiers historiques `REJECTED` + `interviewStatus=SCHEDULED` (données antérieures au fix D2 ; nouveaux refus OK en units/iso)
- Recette **navigateur** complète non rejouée dans cette session (smoke API + FE 5500 UP)
- Git local sale (mods non commit) — HEAD `6233927` — **pas de commit/push**

## Rollback (si besoin)

1. Stop PID **35024** uniquement
2. Relancer l’ancien `hirelink-b0-backend.jar` (SHA `C63FC7EE…`) ou `demo-runtime` `C5722732…` avec les mêmes flags runtime

Evidence : `docs/evidence/cutover-final/`
