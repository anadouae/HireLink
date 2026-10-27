# Comment on a réalisé les séances 3 et 4 — réponse orale

## Phrase d’ouverture (30 s)

« En séance 3 on a modélisé le métier HireLink en Java avec JPA : chaque table MySQL vient d’une classe `@Entity`, pas d’un script SQL écrit à la main. En séance 4 on a exposé ce modèle en API REST (Controller → Service → Repository) et on a branché le frontend + Postman pour prouver le CRUD. »

## Séance 3 — Modélisation (ce qu’on a fait)

1. **Identifier les entités** à partir du CDC : User, Organization, Membership, JobOffer, Application, Tag, RefusalReason, Notification, Scorecard, Matching…
2. **Écrire les classes** dans `backend/.../model/` avec `@Entity`, `@Table`, colonnes, enums (`Role`, `OrgRole`, `ApplicationStatus`, `PlanTier`).
3. **Poser les relations** : `@ManyToOne` / `@OneToMany` (offre ↔ candidatures), N–N User↔Organization via **Membership** (avec rôle), `@ManyToMany` JobOffer↔Tag.
4. **Configurer Hibernate** : `spring.jpa.hibernate.ddl-auto=update` + JDBC `127.0.0.1:3306/hirelink`. Au démarrage, Hibernate crée/met à jour les tables.
5. **Contrôler** dans phpMyAdmin la base `hirelink`, et livrer le **diagramme de classes** (`docs/diagrammes/diagramme_classes_hirelink_FINAL.*`).

**Point fort à dire :** « La source de vérité, c’est le modèle Java. MySQL reflète le code. »

**Évolution récente (alignée S3) :** Organization porte aussi `logoUrl`, `website`, `sector`, `city`, `verificationMethod` — toujours via JPA `ddl-auto=update`.

## Séance 4 — CRUD + API REST (ce qu’on a fait)

Architecture systématique pour chaque feature :

`Frontend → Controller (@RestController) → Service (règles) → Repository (JpaRepository) → Table`

### Trois CRUD complets présentés

| Entité | Endpoints | Qui |
|--------|-----------|-----|
| **JobOffer** | POST/GET/PUT/DELETE `/api/v1/offers` (+ `/public`, `/mine`) | Recruteur (+ gate entreprise vérifiée) |
| **RefusalReason** | CRUD `/api/v1/refusal-reasons` | Admin |
| **InAppNotification** | CRUD + `/read` `/api/v1/notifications` | Tous rôles |

### Preuves

- Frontend : `recruiter-offers.html`, `admin-refusal-reasons.html`, cloche notifications.
- Postman : `docs/postman/HireLink_CRUD_Seance4.postman_collection.json` (login → token → CRUD).
- Autres APIs livrées dans le même esprit : candidatures/pipeline, organisations (vérif + public logos), admin entreprises, CV `/me/cv`, synthèse `/ai/cv-summary`.

## Lien avec les espaces (demo)

| Rôle | Ce qu’on montre |
|------|-----------------|
| Candidat | Dashboard, offres, entreprises (logos), candidatures, CV + score, Performance |
| Recruteur | Offres CRUD, pipeline, fiche entreprise + relancer vérification, Premium |
| Admin | Stats, file « à vérifier », Vérifier/Retirer, plans, comptes |

## Si on pose « comment le score CV ? »

Le score **25–100** lit les **sections du CV** (pas le PDF brut) :
- Identité /15, Expériences /25, Formation /20, Compétences /25, Langues /15.
- ≥ 80 = A, ≥ 55 = B, sinon C.
- La **synthèse** (`/ai/cv-summary`) relit ces textes ; Essentiel = 5 synthèses/mois, Performance = sans plafond.

## Si on pose « vérification entreprise ? »

Comme LinkedIn : ICE + nom + e-mail pro → badge **auto** si registre OK ; sinon file **admin** (`PATCH /admin/organizations/{id}`). Sans badge → pas de publication.

## Comptes démo

- `candidat@hirelink.ma` / `Candidat123!`
- `n.bennani@atlasdigital.ma` / `Recruteur123!`
- `admin@hirelink.ma` / `Admin123!`
