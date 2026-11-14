# HireLink — Rapport Vague 0 (Hygiène démo)

**Date :** 2026-11-14  
**Autorisation :** Vague 0 uniquement  
**Hors périmètre respecté :** pas de redémarrage `8081`/`5500`, pas de commit/push, pas d'activation Jooble/ATS/RemoteOK, pas de suppression métier.

---

## Verdict

**HYGIÈNE OK** — catalogue cohérent ; config disque alignée pour le prochain redémarrage ; Git préparé (sans commit).

**Blocage pour prise d'effet runtime :** les changements `application.properties` / cutover-runtime **nécessitent un redémarrage de `8081`** (non effectué — en attente de votre autorisation séparée).

---

## 1. Sauvegarde avant écriture

| Attribut | Valeur |
|---|---|
| Fichier | `docs/deploy-prep/hirelink-vague0-20261114-015822.sql` |
| SHA-256 | `9B1DF5D68A92A0BCF74F2A4D50F9A73599B6547B0B32E237004AEA8289819639` |
| Octets | 2 197 014 |
| Restore isolé | `hirelink_vague0_restore` — offres/apps/catalogue identiques |

---

## 2. État avant / après

| Métrique | Avant | Après |
|---:|---:|---:|
| `job_offers` | 123 | **123** |
| `applications` | 221 | **221** |
| `users` | 126 | **126** |
| `external_job_offers` | 106 | **106** |
| `catalog_listings` | 107 | **107** |
| Offres ouvertes listables | 107 | **107** |
| Boards ATS enabled | 0 | **0** |

Aucune ligne métier créée/supprimée. Aucune donnée de `LiveSync D` altérée (titre, lieu, contrat, skills, description).

---

## 3. Audit des offres de test bascule

| id | Titre | published | closed_at | Dans catalogue |
|---:|---|---:|---|---:|
| 159 | Bascule Phase3 Test CDI Spring Modifie | 0 | oui | non |
| 160 | SyncUpdate Beta Rabat | 0 | oui | non |
| 161 | SyncProof Two | 0 | oui | non |
| 162 | LiveSync B | 0 | oui | non |
| **163** | **LiveSync D** | **1** | **oui** | **non** |

### Diagnostic `LiveSync D`

- L'offre a été **clôturée** (`closed_at` renseigné) tout en gardant `published=1`.  
- C'est le comportement de `JobOfferService.close()` : pose `closedAt`, **ne force pas** `published=false`.  
- `CatalogListingIndexer.isInternalListable` exige `closedAt == null` → exclusion **correcte**.  
- Reindex admin → `indexed=107` ; `LiveSync D` toujours hors catalogue.  
- Compteur ouvertures listables = catalogue = **107** → **pas de dérive catalogue**.

**Correction appliquée :** aucune modification métier ; confirmation par reindex que la projection est juste.  
**Non fait (volontaire) :** rouvrir l'offre ou forcer `published=0` (changerait l'état métier / sémantique close).

---

## 4. Alignement configuration (disque)

Fichier `backend/src/main/resources/application.properties` :

| Clé | Avant | Après |
|---|---|---|
| `hirelink.external-jobs.startup-sync` | `true` | **`false`** |
| `hirelink.external-jobs.maroc.enabled` | `true` | **`false`** |

`backend/application-cutover-runtime.properties` : ajout `maroc.enabled=false`, `remoteok.enabled=false` (Jooble/ATS déjà false, `startup-sync` déjà false).

**Effet actuel sur la JVM 8081 :** inchangé jusqu'au redémarrage (le processus tourne encore avec l'ancien classpath/JAR chargé).  
**Effet au prochain redémarrage :** plus d'import auto au boot ; fixture MAROC_EMPLOI non synchronisée.

---

## 5. Tests (preuves)

Fichier : `docs/deploy-prep/vague0-smoke.txt`

| Test | Résultat |
|---|---|
| Cohérence catalogue 107 = offres ouvertes | OK |
| LiveSync D fermée / hors catalogue | OK |
| Empreinte métier LiveSync D | OK (inchangée) |
| Login candidat + candidatures + Pour toi | OK |
| Login recruteur + `GET /api/v1/applications/pipeline` | OK (200) |
| Pages FE candidate / pipeline | OK (200) |
| Connecteurs / catalogue externe | OK (0) |

Note : `GET /api/v1/pipeline` renvoie 404 (route réelle = `/api/v1/applications/pipeline`) — comportement préexistant, hors périmètre Vague 0.

---

## 6. Préparation Git (sans commit)

`.gitignore` enrichi pour exclure :

- `docs/deploy-prep/**/*.sql` et `*.meta.txt`
- `docs/deploy-prep/rollback-*/`
- `backend/application-cutover-runtime.properties`
- `backend/application-audit-iso.properties`
- `backend/application-deploy-prep.properties`
- `backend/data/jwt.key` (déjà couvert par `backend/data/`)

Vérifié : `git check-ignore` marque bien le dump Vague 0, cutover-runtime et jwt.key.

**Aucun commit / push effectué.**

Fichiers à versionner plus tard (quand vous l'autoriserez) : code Phases 1–3, `application.properties` (startup-sync/maroc false), docs d'audit, `.gitignore`.  
À ne jamais committer : dumps SQL, secrets, props d'iso/cutover.

---

## 7. Blocages / prochaines étapes

| Item | Statut |
|---|---|
| Redémarrage `8081` pour appliquer config hygiène | **En attente de votre autorisation** |
| Activation Jooble / ATS | Non demandé — toujours OFF |
| Vraies offres marocaines | Après hygiène runtime : clé Jooble MA + ToS, ou partenariat portail (voir plan d'activation Vague 1–2) |

### Prochaine étape recommandée (Maroc)

1. Autoriser le **redémarrage contrôlé de 8081** avec la config hygiène.  
2. Smoke post-restart (catalogue 107, pas d'import fixture).  
3. Puis seulement : Jooble MA (clé) **ou** canary ATS international selon `HIRELINK_EXTERNAL_JOBS_ACTIVATION_PLAN.md`.
