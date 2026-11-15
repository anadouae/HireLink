# Plan de cutover — Recherche Web internationale

## Candidat

| Champ | Valeur |
|--------|--------|
| JAR | `docs/deploy-prep/b0-candidate-runtime/hirelink-candidate-20261115-123926-C60F85B39C86.jar` |
| SHA-256 | `C60F85B39C86B04C585767889BB2E888B36D944BC7935DA3B152617AD2924629` |
| Smoke isolé | `http://127.0.0.1:18089` — PASS (éphémères HTTPS, géo DE, pagination, listings=0) |
| Démo actuelle | PID 35024 · JAR E65802C4… · **123 offres** — **ne pas toucher sans autorisation** |

## Attribution / ToS (obligations)

| Source | Obligation | Implémentation HireLink |
|--------|------------|-------------------------|
| **Remotive** | Lien vers l’URL Remotive + mention « Remotive » ; ≤~4 req/j conseillé ; pas de re-soumission vers Jooble/Google Jobs/LinkedIn | `sourceLabel` + lien followed (`rel=noopener` sans nofollow) ; cache TTL 6 h |
| **Remote OK** | Lien **follow** (sans nofollow) vers l’URL Remote OK + mention du nom ; logo interdit sans accord | Idem ; pas de logo Remote OK |
| **Arbeitnow** | Lien retour vers Arbeitnow.com ; API « as is », révocable | `sourceHome` + label Arbeitnow |
| **Jooble** | Clé régionale ; 100 req/j | SKIPPED sans clé |

## Avant cutover (checklist)

1. Autorisation explicite de l’utilisateur.
2. Sauvegarde MySQL `hirelink` (dump timestampé) + copie du JAR live actuel.
3. Confirmer SHA-256 du candidat.
4. Activer uniquement si voulu : `hirelink.web-search.enabled=true` (props / env). Jooble optionnel via `HIRELINK_JOOBLE_API_KEY`.
5. Couper **uniquement** le process 8081 ciblé (pas `Stop-Process -Name java`).
6. Démarrer le candidat sur 8081 avec les mêmes props démo + web-search.
7. Smoke post-cutover : health, catalog ALL/DE, offers=123, web ephemeral > 0 si enabled.
8. Rollback = ré-attacher l’ancien JAR + dump si besoin.

## Rollback

1. Arrêter le process 8081 du candidat.
2. Relancer l’ancien JAR (backup pré-cutover).
3. Vérifier `job_offers=123` et health UP.

## Limites restantes

- Couverture **partielle** (remote + Europe ATS) — pas un index mondial exhaustif.
- URLs souvent deep-links agrégateur (Remotive/Arbeitnow/Remote OK), pas toujours l’ATS employeur brut.
- Remotive : données retardées ~24 h ; quota strict → cache obligatoire.
- Arbeitnow ToS site : licence « personal non-commercial viewing » + API exige link-back — usage académique/démo OK ; usage commercial produit à clarifier avec Arbeitnow si mise en production payante.
- Sans cutover, **8081 n’expose pas** encore web-search (enabled=false sur le live).
