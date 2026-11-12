# Matrice UX isolée

Base : `hirelink_ux_check` · API : `8093` · Front : `5513` · Proxy CSP `8081→8093`  
Démo principale (`8081` / `hirelink`) non utilisée pour ces tests UI.

| ID | Statut | Note |
|---|---|---|
| X00-api-login | PASS | 3 comptes isolés OK |
| R00-recruiter-home | PASS | `/app/recruiter.html` |
| R01-pipeline-kanban | PASS | contrôle Kanban |
| R02-pipeline-liste | PASS | vue Liste activée |
| R03-candidates-merged | PASS | redirect → `recruiter-pipeline.html` |
| R04-assistant-cv | PASS | page « Synthèse de CV » |
| R05-assistant-no-kit-ui | PASS | pas de grille kit UI |
| R06-interviews | PASS | `/app/recruiter-interviews.html` |
| R07-agenda | PASS | `/app/recruiter-agenda.html` |
| C00-candidate-home | PASS | `/app/candidate.html` |
| C01-profil-cv-nav | PASS | navigation profil/CV |
| C02-cv-panel | PASS | onglet Mon CV |
| C03-companies | PASS | entreprises suivies |
| C04-notifications | PASS | `/app/notifications.html` |
| A01-admin-offers | PASS | `#offers` |
| A02-admin-reports | PASS | `#reports` |
| A03-admin-sources | PASS | sources externes |
| S01-cgu-checkbox | PASS | `#accept-terms` visible |
| S02-privacy-checkbox | PASS | `#accept-privacy` visible |
| S03-cgu-link | PASS | lien `legal/cgu.html` |
| S04-privacy-link | PASS | lien `legal/confidentialite.html` |
| S05-server-reject-without-consent | PASS | `verify-register-otp` → **HTTP 400** « Acceptez les Conditions générales… » (OTP + captcha OK, versions absentes) |
| S05b-server-accept-with-consent | PASS | `verify-register-otp` → **HTTP 200** ; 2 lignes `TERMS`/`PRIVACY` dans `hirelink_ux_check.legal_acceptances` uniquement |
| S06-cgu-page | PASS | page CGU |
| S07-privacy-page | PASS | page confidentialité |
| K01-checkout-display | PASS | UI checkout + badge simulation |
| K02-no-real-debit-ui | PASS | pas de Stripe/PayPal/carte réelle |
| K03-simulated-copy | PASS | « Simulation sécurisée » / paiement simulé |
| U01-LegalConsentServiceTest | PASS | Maven 3/3 (`refusesMissingConsents`, `refusesStaleTermsVersion`, `recordsBothPurposes`) |

**Totaux** : PASS **29** · FAIL **0** · BLOCKED **0**

Captures : `docs/security-control-20261112/shots/` (`01`…`18`).

### Observation (non bloquante)
- Checkout : totaux HT/TVA/TTC affichés en tirets sur la capture (`18-checkout.png`) — libellés de simulation présents ; à revoir si le plan sélectionné doit toujours peupler les montants côté serveur.
