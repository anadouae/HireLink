# Recherche Web internationale HireLink — rapport honnête

## Faisabilité (vérifiée 2026-11-15)

| Source | Clé | Coût | Couverture réelle | URL renvoyée | Statut HireLink |
|--------|-----|------|-------------------|--------------|-----------------|
| **Remotive** | Non | Gratuit (API publique) | Remote mondial | Fiche Remotive deep-link | Actif si `web-search.enabled` |
| **Arbeitnow** | Non | Gratuit + attribution | Europe (DE/FR/NL/CH/UK…) | Fiche Arbeitnow / ATS | Actif |
| **Remote OK** | Non | Gratuit (API publique) | Remote mondial | Fiche Remote OK | Actif |
| **Jooble** | Oui (1 clé = 1 pays) | Gratuit ~100 req/j | Régional (ex. MA) | Deep-link Jooble | Optionnel si clé |
| **Adzuna** | Oui (app_id+key) | Essai / licence commerciale | 19 pays, **pas MA** | Annonces Adzuna | Non branché (clé + ToS commercial) |

**Ce n’est pas** un crawl de « tout le Web ». Aucune source gratuite sans clé ne donne un index mondial exhaustif d’offres on-site locales (Indeed/LinkedIn interdits sans partenariat).

## Comportement produit

- Pays par défaut API : `ALL` (plus de forçage silencieux vers MA).
- Filtre pays précis : seules les offres **géo-vérifiées** sont gardées ; « Worldwide » exclu d’un filtre pays.
- Offres Web **éphémères** (non persistées).
- Clic → URL https deep-link de l’annonce (pas la home du site).
- Message de couverture + statut par source exposés à l’API/FE.

## Activation (sans toucher 8081 tant que non autorisé)

```properties
hirelink.web-search.enabled=true
# optionnel Jooble MA :
HIRELINK_JOOBLE_API_KEY=...
```

## Preuves

- `InternationalWebSearchLiveTest` : HTTP **réel** Remotive / Arbeitnow / Remote OK (pas mock).
- Géo DE / FR / MA / ALL couverts dans les assertions.

## Attribution (ToS)

| Source | Exigence | HireLink |
|--------|----------|----------|
| Remotive | Link-back URL Remotive + mention source ; ≤~4 req/j ; pas de re-syndication Google/LinkedIn/Jooble | Label + lien followed ; cache 6 h |
| Remote OK | Lien **follow** (sans nofollow) + nom « Remote OK » ; logo interdit | `rel=noopener` sans nofollow |
| Arbeitnow | Link-back Arbeitnow.com (API §11) | `sourceHome` + label |

## Finalisation ops (2026-11-15)

- JAR candidat + SHA-256 + smoke `:18089` : voir `docs/HIRELINK_WEB_INTL_CUTOVER_PLAN.md`
- Preuves : `docs/evidence/final-delivery/WEB_INTL_FINAL_OPS.txt`

## Limites assumées

- Remotive / Remote OK / Arbeitnow renvoient souvent l’URL de **leur** fiche offre (redirection possible vers l’employeur), pas toujours l’ATS employeur en clair.
- Maroc on-site riche nécessite une clé Jooble MA (ou autre partenariat).
- Arbeitnow : usage commercial produit à clarifier si mise en production hors démo académique.
- Sans cutover + `web-search.enabled=true` sur le JAR live, la démo 8081 reste en mode indexé uniquement.
