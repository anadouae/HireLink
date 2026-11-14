# HireLink — Complete Functional and Business Logic Audit

**Scope:** Read-only inspection of the HireLink codebase at `C:\Users\daurinia\Desktop\HireLink`  
**Date:** 2026-11-12  
**Method:** Source review of backend services/controllers/entities, frontend journeys, configuration, and unit/integration tests. No application code changes, no DB writes, no demo restart, no browser mutation tests.  
**Follow-up:** Consolidated with parallel deep-dives on candidate/AI, recruiter/billing, and external aggregation (same codebase pass).

**Capability labels used throughout**

| Label | Meaning |
|---|---|
| **IMPLEMENTED AND VERIFIED** | Code path exists + supporting tests and/or documented deterministic rules |
| **IMPLEMENTED BUT NOT TESTED** | Code path exists; no dedicated automated test found in this pass |
| **PARTIALLY IMPLEMENTED** | Real path with material gaps, placeholders, or mixed real/local behavior |
| **UI ONLY** | Interface exists without a complete server journey |
| **SIMULATED** | Explicit mock / fictitious provider / sample feed presented as product flow |
| **NOT IMPLEMENTED** | No evidence in code |
| **UNKNOWN** | Insufficient evidence without runtime keys or live third-party calls |

---

## 1. Executive summary

HireLink is a **bilateral Moroccan-first recruitment SaaS**: candidates search and apply; recruiters run an ATS-style pipeline; admins moderate and configure plans. The strongest *real* differentiators already in code are:

1. **Deterministic, explainable matching** (local skill catalog + weighted criteria, no embeddings, no AI credits).
2. **A working ATS loop** (applications, status history, SLA deadlines, interviews, notifications; manual scorecards are retired).
3. **Organization governance** (ICE-oriented company verification, seats, roles, invitations).
4. **Honest AI metering** (reserve ? call ? settle/release; local fallback does not bill as paid AI).
5. **Simulated but structurally complete billing** (quotes, invoices, PDF, idempotent payments) — **not** a real PSP.

Critical honesty points:

- **Payment is simulated** (`MockPaymentProvider`). Card PAN/CVV must not reach the server.
- **External Maroc jobs** currently come from a **classpath partner sample feed**, with **homepage-level URLs** (`rekrute.com/`, `emploi.ma/`), not live authorized scrapes of exact offer pages.
- **Remote OK** is integrated as a real public JSON API client but **disabled by default** and filtered out of the public list when disabled.
- **Matching is not AI.** Optional `/ai/match-explain` narrates skill gaps via LLM and *does* consume credits.
- **CV “OCR”** extracts PDF text layers / TXT; images and scanned PDFs have **no OCR engine**.

**Bottom line for positioning:** HireLink can win as a **transparent Morocco-focused matching + ATS companion**, not as a LinkedIn/Indeed replacement or a payment-grade marketplace.

---

## 2. Current HireLink architecture

### Stack (verified)

- **Backend:** Spring Boot (`backend/`), package `ma.ensat.hirelink`, REST under `/api/v1/*`
- **Frontend:** Static HTML/JS/CSS (`frontend/`), role shells via `frontend/js/app-shell.js`
- **Persistence:** JPA entities under `backend/src/main/java/ma/ensat/hirelink/model/` + Flyway-style SQL under `database/migrations/`
- **Auth:** JWT sessions, OTP email flows, Google, WebAuthn/passkeys, optional admin MFA (`AuthController`, `AuthService`, `WebAuthn*`)

### Major API surfaces

| Area | Controller | Base path |
|---|---|---|
| Auth | `AuthController` | `/api/v1/auth` |
| Profile / me | `MeController` | `/api/v1/me` |
| Offers | `JobOfferController` | `/api/v1/offers` |
| Applications | `ApplicationController` | `/api/v1/applications` |
| AI assists | `AiAssistController` | `/api/v1/ai` |
| Credits | same | `GET /api/v1/ai/credits` |
| Billing | `BillingController` | `/api/v1/billing` |
| Organizations | `OrganizationController` | `/api/v1/organizations` |
| Follows | `CompanyFollowController` | `/api/v1/follows` |
| External jobs | `ExternalJobController` | `/api/v1/external-offers` |
| Admin externals | `ExternalJobAdminController` | `/api/v1/admin/external-offers` |
| Catalog / places | `CatalogSuggestController` | `/api/v1/catalog` |
| OCR / CV text | `OcrController` | `/api/v1/ocr` |
| Notifications | `NotificationController` | `/api/v1/notifications` |
| Admin | `AdminController` | `/api/v1/admin` |
| Scorecards (retired writes) | `ScorecardController` | `/api/v1` (409 on mutate) |

### Core domain entities

`User`, `Organization`, `Membership`, `JobOffer`, `Application`, `ApplicationStatusHistory`, `CompanyFollow`, `CommercialPlan`, `BillingInvoice`, `PaymentTransaction`, `AiUsageLog`, `ExternalJobOffer`, `ExternalSyncRun`, `InAppNotification`, `ScorecardEntry` / `ScorecardCriterion`, `OfferReport`, `LegalAcceptance`, `WebAuthnCredential`, …

### Role model

- Platform roles (`Role`): `CANDIDAT`, `RECRUTEUR`, `ADMINISTRATEUR`
- Org roles (`OrgRole`): `OWNER`, `RECRUTEUR`, `HIRING_MANAGER`

---

## 3. Candidate features

### 3.1 Registration, authentication, profile

| Capability | Status | Evidence |
|---|---|---|
| Email OTP register/login | **IMPLEMENTED AND VERIFIED** | `AuthController` `/request-otp`, `/verify-register-otp`, `/verify-login-otp`; `EmailOtp` entity |
| Password login + refresh/logout | **IMPLEMENTED AND VERIFIED** | `/login`, `/refresh`, `/logout` |
| Google sign-in | **PARTIALLY IMPLEMENTED** | `/auth/google`; `hirelink.google.client-id` / `dev-bypass` in properties — behavior depends on config |
| Passkeys (WebAuthn) | **IMPLEMENTED BUT NOT TESTED** (this pass) | `/auth/passkey/*` endpoints present |
| MFA for admin | **IMPLEMENTED BUT NOT TESTED** | `hirelink.security.admin-mfa-required`; enroll/verify endpoints |
| Profile CRUD (skills, city, contracts, CV metadata…) | **IMPLEMENTED AND VERIFIED** | `MeController`, candidate settings JS |
| Legal consent versions | **IMPLEMENTED AND VERIFIED** | `LegalAcceptance`, legal version properties |

**Real behavior:** Profile skills used for matching are those **confirmed on the user record** (`users.skills`). Uploading a CV alone does **not** auto-confirm skills into matching (`SkillMatchingService.hasConfirmedSkills` + docs).

### 3.2 CV upload, extraction, analysis, AI synthesis

| Capability | Status | Evidence |
|---|---|---|
| Upload CV (PDF/TXT/image) | **IMPLEMENTED AND VERIFIED** | `OcrService.extract`, `FileSignature` |
| PDF text-layer extraction | **IMPLEMENTED AND VERIFIED** | PDFBox `PDFTextStripper` |
| True OCR (scanned PDF / images) | **NOT IMPLEMENTED** | Explicit note: “Aucun moteur OCR n'est installé” |
| Section / experience heuristic parse | **IMPLEMENTED AND VERIFIED** | `CvSectionParser` via `OcrService.analyze` |
| Suggested skills from text | **IMPLEMENTED AND VERIFIED** | `OcrResult.suggestedSkills` |
| AI CV summary | **PARTIALLY IMPLEMENTED** | `POST /api/v1/ai/cv-summary` — LLM with local text fallback; credits reserved; local fallback releases/zeros paid credit semantics via settle rules |
| AI coach on CV | **IMPLEMENTED BUT NOT TESTED** | `POST /api/v1/ai/coach` (candidate-only) |

### 3.3 Job search, filters, internal vs external

| Capability | Status | Evidence |
|---|---|---|
| Public / authenticated offer listing | **IMPLEMENTED AND VERIFIED** | `/offers/public`, authenticated offer APIs; `landing-offers.js`, `candidate-home.js` |
| Filters (city, skill, contract, origin, date…) | **IMPLEMENTED AND VERIFIED** | Frontend filters + API fields |
| Internal HireLink offers | **IMPLEMENTED AND VERIFIED** | `JobOffer` + apply ? `Application` |
| External partner offers | **PARTIALLY IMPLEMENTED** | `ExternalJobOffer` list; apply opens external URL; **no HireLink application** |
| Origin filter hirelink/external | **IMPLEMENTED AND VERIFIED** | `candidate-home.js`, `landing-offers.js` |

**Apply button reality**

- Internal: `candidate.html?offer=…` ? `POST /applications`
- External: `applyUrl || originUrl` in new tab; UI states HireLink does not track the candidature (`candidate-home.js`, `offer-card.js`)

### 3.4 Matching scores (candidate view)

See §5. Candidate UI shows score / “Pourquoi ce score ?” from server compatibility payload when skills are confirmed.

Filter “offers matching my profile” uses **>= 50%** threshold (`JobOfferService.PROFILE_MATCH_THRESHOLD = 50.0`, also in `docs/SCORE_COMPATIBILITE.md`). List DTOs expose a **live** current score/breakdown recomputed on read; `applications.match_score` remains the **submission snapshot**.

### 3.5 Applications, tracking, notifications

| Capability | Status | Evidence |
|---|---|---|
| Apply with profile CV or upload | **IMPLEMENTED AND VERIFIED** | `ApplicationService` CV source `PROFILE`/`UPLOAD` |
| Freeze match score at apply time | **IMPLEMENTED AND VERIFIED** | `app.setMatchScore(compat.score())` |
| Status pipeline for candidate view | **IMPLEMENTED AND VERIFIED** | `ApplicationStatus` + history |
| Withdraw | **IMPLEMENTED AND VERIFIED** | `WITHDRAWN` terminal |
| In-app notifications + SSE stream | **IMPLEMENTED AND VERIFIED** | `NotificationController`, `NotificationStreamService` |
| Email notifications | **PARTIALLY IMPLEMENTED** | `MailService` can log/send depending on SMTP config; tests often `LOGGED` |

### 3.6 Company following

| Capability | Status | Evidence |
|---|---|---|
| Follow / unfollow organizations | **IMPLEMENTED AND VERIFIED** | `/api/v1/follows`, `CompanyFollow` entity, `candidate-companies.html` |

### 3.7 AI tools, credits, subscriptions (candidate)

| Feature | Endpoint | Credits | Status |
|---|---|---|---|
| Credits status | `GET /ai/credits` | — | **IMPLEMENTED AND VERIFIED** |
| Profile rewrite | `POST /ai/profile-rewrite` | 1 if external LLM success | **IMPLEMENTED AND VERIFIED** |
| Interview prep | `POST /ai/interview-prep` | 1 if external success | **IMPLEMENTED AND VERIFIED** |
| Match explain (LLM narrative) | `POST /ai/match-explain` | 1 if external success | **IMPLEMENTED AND VERIFIED** |
| CV summary | `POST /ai/cv-summary` | 1 if external success; local fallback path | **PARTIALLY IMPLEMENTED** |
| Coach | `POST /ai/coach` | 1 if external success | **IMPLEMENTED BUT NOT TESTED** |
| Deterministic matching | local service | **0** | **IMPLEMENTED AND VERIFIED** |

**Plan gates (seed catalog):** Essentiel 5 credits / month free; Performance 39 MAD / 40 credits (`BillingCatalogService.seed`).

**UI consistency:** Crown / credits links now route unpaid users to `checkout.html` (`app-shell.js`, `credits.js`). Help text still sometimes says “Paramètres ? Facturation” while checkout is the payment path — minor copy inconsistency.

---

## 4. Recruiter features

### 4.1 Company registration and verification

| Capability | Status | Evidence |
|---|---|---|
| Organization + membership | **IMPLEMENTED AND VERIFIED** | `Organization`, `Membership`, `OrganizationService` |
| ICE format + internal registry match | **IMPLEMENTED AND VERIFIED** | `CompanyVerificationService` + `IceIdentityLookup` / `CompanyDirectory` |
| Live OMPIC / official registry API | **NOT IMPLEMENTED** | Response field `officialApiAvailable: false`; OMPIC appears as documentation link only |
| Auto-verify heuristics | **IMPLEMENTED AND VERIFIED** | Corporate email + legal-name similarity ? **0.72** ? `REGISTRY_MATCH` / `AUTO_REGISTRY`; else `NEED_CORPORATE_EMAIL`, `WAITING_REGISTRY`, `NAME_MISMATCH` |
| Admin manual verification | **IMPLEMENTED AND VERIFIED** | Admin org verification flows |
| Publication blocked until alignment | **IMPLEMENTED AND VERIFIED** | `JobOfferService.assertCanPublish` (org/user verified required) |

**Classification note:** Badge “verified” is **HireLink-controlled** (seed/internal registry + corporate-email heuristics), not a live government API check. Status: **PARTIALLY IMPLEMENTED / SIMULATED registry** relative to “official OMPIC verification”.

### 4.2 Job creation, publication, management

| Capability | Status | Evidence |
|---|---|---|
| Create / edit / publish / close offers | **IMPLEMENTED AND VERIFIED** | `JobOfferService`, recruiter offers UI |
| Required + nice-to-have skills, location, contract, experience, work mode | **IMPLEMENTED AND VERIFIED** | `JobOffer` fields used by matching |
| Offer reporting / moderation | **IMPLEMENTED AND VERIFIED** | `OfferReport`, admin moderation |
| Language inclusivity check on offer text | **IMPLEMENTED AND VERIFIED** | `LanguageCheckService` via `/ai/language-check` (**no AI credit**; rule-based markers) |

### 4.3 Candidate discovery and application management

| Capability | Status | Evidence |
|---|---|---|
| Pipeline kanban + list | **IMPLEMENTED AND VERIFIED** | `recruiter-pipeline.html` + JS; status chips aligned to real statuses |
| Status transitions with history | **IMPLEMENTED AND VERIFIED** | `ApplicationStatus`, `ApplicationStatusHistory` |
| Active path | SUBMITTED ? SCREENING ? SHORTLISTED ? INTERVIEW | `ApplicationService` allowed transitions |
| OFFER / HIRED | **NOT IMPLEMENTED** for new advances | Kept for legacy/seed; recruiters cannot advance into them |
| Reject with catalog reason | **IMPLEMENTED AND VERIFIED** | `RefusalReason`, mandatory reason on `REJECTED` |
| Match detail in dossier | **IMPLEMENTED AND VERIFIED** | `evaluateIfAvailable` criteria payload |
| Scorecards | **NOT IMPLEMENTED** (retired) | `ScorecardService.setCriteria` / `rate` ? **409 CONFLICT** |
| Assign hiring manager on application | **NOT IMPLEMENTED** (retired) | `ApplicationService.assignHm` ? **409 CONFLICT** |
| Talent sourcing (non-applicants) | **NOT IMPLEMENTED** | Inbound applications only |
| Offer fraud / multi-report suspend | **IMPLEMENTED AND VERIFIED** | `FraudOfferScanService`; ?3 reports can suspend |

**Time-saving value (genuine):** structured pipeline + SLA response deadline + explainable score + refusal drafts + interview scheduling reduce spreadsheet chaos. Not a sourcing powerhouse.

### 4.4 Interviews and agenda

| Capability | Status | Evidence |
|---|---|---|
| Interview scheduling fields | **IMPLEMENTED AND VERIFIED** | `Application.interviewAt`, `interview_status` |
| Agenda / interviews pages | **IMPLEMENTED AND VERIFIED** | `recruiter-agenda.html`, `recruiter-interviews.html` |
| Notifications on changes | **IMPLEMENTED AND VERIFIED** | notification services |

### 4.5 AI-assisted recruiter tools

| Feature | Status | Notes |
|---|---|---|
| Interview kit | **PARTIALLY IMPLEMENTED / SIMULATED** | API still returns **local template** questions (`AiCreditService.interviewKit`) while calling LLM for audit/debit; **no call site in `recruiter-assistant.js`** (UI removed) |
| Refusal draft | **IMPLEMENTED AND VERIFIED** | LLM draft; human must send |
| Language check | **IMPLEMENTED AND VERIFIED** | Deterministic; **not** billed as AI credit |
| CV summary (recruiter tone) | **PARTIALLY IMPLEMENTED** | Same endpoint, recruiter prompt branch |

### 4.6 Organization membership, seats, permissions

| Rule | Evidence | Status |
|---|---|---|
| Seat limit Accès = 5, Premium = 25 | `PlanTier.seatLimit()` | **IMPLEMENTED AND VERIFIED** |
| Enforced on invite/accept | `OrganizationService` capacity check | **IMPLEMENTED AND VERIFIED** |
| Only OWNER pays / changes plan | `PaymentService.targetPaidTier` / `prepare` | **IMPLEMENTED AND VERIFIED** |
| HIRING_MANAGER limited manage flags | shell `manage: true` nav + server checks | **IMPLEMENTED AND VERIFIED** (pattern present) |

### 4.7 Dashboards and analytics

| Capability | Status | Evidence |
|---|---|---|
| Recruiter home KPIs | **PARTIALLY IMPLEMENTED** | Client aggregations over real APIs; funnel charts may still mention OFFER/HIRED ? legacy KPI drift |
| Admin charts/stats | **IMPLEMENTED AND VERIFIED** | `/admin/stats`, `/admin/charts` |
| Responsiveness / SLA monitor | **IMPLEMENTED BUT NOT TESTED** | `ResponsivenessService`, `hirelink.sla.poll-ms` |
| Dedicated `/analytics` backend | **NOT IMPLEMENTED** | No analytics controller |

---

## 5. AI and matching logic

### 5.1 Matching score — how it works

**Engine:** `SkillMatchingService`  
**Docs:** `docs/SCORE_COMPATIBILITE.md`  
**Type:** **Deterministic rules + skill catalog normalization**.  
**Not used:** embeddings, vector DB, cosine similarity, ranking LLMs for the numeric score.

#### Skill overlap (`explain` / `computeMatchScore`)

\[
score = 100 \times \frac{\lvert covered\rvert}{\lvert covered\rvert + \lvert missing\rvert}
\]

Skills normalized via `SkillCatalogService` (`skills-catalog.json` synonyms, accents, light typos). Languages can be separated from hard skills in `evaluate`.

#### Weighted compatibility (`evaluate`)

| Criterion | Default weight |
|---|---|
| Required skills | 45 |
| Nice-to-have skills | 10 |
| Languages (CEFR-aware) | 10 |
| Experience level | 15 |
| Contract preference | 10 |
| Location / work mode | 10 |

Rules of note:

- Inapplicable criteria (not set on offer) are dropped; weights renormalize.
- Missing profile preference ? neutral ratio `UNKNOWN = 0.5` (not punished as 0).
- If required-skill coverage &lt; 50%, total capped to `min(60, coverage% + 20)`.
- `evaluateIfAvailable` returns **null** (UI: “Correspondance non disponible”) if candidate has no confirmed skills or offer has no exploitable criterion — **never fakes 0%**.

#### Recalculation

- **At application time:** score **frozen** into `applications.match_score` (`ApplicationService`).
- **On later reads:** current compatibility can be recomputed for display; frozen score remains historical.
- **Detail lists** (covered/missing) are **re-derived**, not stored.
- CV file changes alone do **not** change score until confirmed skills / preferences on `User` change.

#### Explainability

1. **Primary (free):** structured criteria + covered/missing skills from `Compatibility`.
2. **Secondary (paid AI):** `POST /ai/match-explain` LLM prose about skill gaps — **not** the score engine.

### 5.2 AI models and providers

`HttpLlmProvider`:

- Providers: **Gemini**, **Groq**, **Mistral** (configurable models in `application.properties`)
- Fallback chain: `hirelink.llm.fallback-chain` (default `gemini,groq,mistral`)
- If no keys / all fail ? `LocalLlmProvider` (`local-fallback`)
- Defaults observed: `gemini-3.5-flash-lite`, Groq `qwen/qwen3.8-27b`, `mistral-small-latest`

**UNKNOWN without runtime env:** which keys are present on a given deployment. Code path is clear; live model quality is environment-dependent.

### 5.3 Credit consumption

`AiCreditService`:

- `COST_PER_ACTION = 1`
- **Reserve** (row lock) before call ? **settle** after ? **release** on hard failure
- Settle rule: credit kept only if `success && provider` does **not** start with `local`
- Monthly ledger: `ai_usage_logs`
- Limits from commercial plan credits (admin-editable catalog)
- Matching **never** consumes credits (explicit R08 comment)

### 5.4 When AI is unavailable

- Chain falls back to local provider text
- Credits for local success are **not** charged as external (`settle`)
- CV summary has an extra catch ? `localSummary(...)` heuristic trim of CV text

### 5.5 Feature matrix (AI vs matching)

| Concern | Mechanism | AI? | Credits? |
|---|---|---|---|
| Candidate–job numeric match | `SkillMatchingService.evaluate` | No | No |
| Missing skills list | set difference after catalog keys | No | No |
| LLM match narrative | `/ai/match-explain` | Yes | Yes (if external) |
| CV summarization | `/ai/cv-summary` | Yes / local | Yes only if external |
| Recruiter interview kit cards | local templates | No (display) | Yes still reserved because LLM called |
| Offer language check | marker rules | No | No |

---

## 6. External job aggregation

### 6.1 Sources

| Code | Label | Default | Kind | Acquisition |
|---|---|---|---|---|
| `MAROC_EMPLOI` | Emploi Maroc (partenaires) | **enabled=true** | maroc | Classpath JSON feed `feeds/maroc-emploi-partenaires.v1.json` (configurable `hirelink.external-jobs.maroc.feed`) |
| `REMOTE_OK` | Remote OK | **enabled=false** | remote | HTTP GET `https://remoteok.com/api` via `RemoteOkClient` |

Registry: `ExternalSourceCatalog`  
Orchestration: `ExternalJobSyncFacade`  
Startup fill if empty: `ExternalJobsStartupSync`  
Cron: `MarocPartnerSyncJob`, `RemoteOkSyncJob` (conditional on flags)

**Do not claim official partnership with Rekrute/Emploi.ma** based on this codebase alone: the sample feed **attributes URLs to those domains’ homepages** but contains HireLink-authored sample job payloads. Status: **SIMULATED / sample partner feed**, with a parser contract ready for a real HTTPS JSON feed.

Remote OK: **real public aggregator API** when enabled — not a company career site, not an ATS webhook.

### 6.2 Extraction

**Maroc parser** (`MarocPartnerFeedParser`): expects `{ "jobs": [ { id, title, company, url, applyUrl?, location?, contract?, description?, skills?, salaryMin/Max?, publishedAt? } ] }`. Requires `https://` origin URL.

**Remote OK parser** (`RemoteOkParser`): public array JSON; skips legal banner object; maps `position`/`title`, `company`, `url`, `apply_url`, tags?contract/skills, HTML description stripped, salaries, epoch dates.

### 6.3 Why links open homepages

In `maroc-emploi-partenaires.v1.json`, `url` / `applyUrl` are set to site roots (`https://www.rekrute.com/`, `https://www.emploi.ma/`), **not** deep offer permalinks. That is a **feed content choice**, not a frontend bug. Apply button correctly opens stored URL.

### 6.4 Persistence and dedupe

- Entity `ExternalJobOffer`, unique `(source, external_id)`
- Import upsert by `externalId` within source (`ExternalJobImportService.importLocked`)
- Duplicates in same snapshot counted; last copy wins
- Admin hide: `hiddenByAdmin=true`, `available=false` (sync does not unhide)
- Absences: if ID not seen for **7 days** (`ABSENCE_BEFORE_HIDE`), row set unavailable
- Empty/unacceptable snapshot ? **keep previous** offers

### 6.5 API ? UI flow

1. Sync (admin `POST /external-offers/sync` or startup/cron) ? parsers ? DB  
2. `GET /external-offers` returns only offers whose **source is currently enabled**  
3. Frontend merges with HireLink offers; marks `external: true`  
4. Card/CTA uses `applyUrl || originUrl`; source credit uses `sourceLabel` / `sourceHome`  
5. Matching on externals: **skills-only** `SkillMatchingService.explain(...)` (not full weighted `evaluate`) if candidate has confirmed skills (`ExternalJobController.toPublic`); label warns skills are source-claimed, not company-validated
6. Doc drift: `docs/sources-offres-externes.md` / some landing copy remain Remote-OK-centric while defaults favor the Maroc sample feed; Maroc `sourceHome` is `https://hirelink.ma`, not Rekrute/Emploi.ma

### 6.6 Extensibility

Adding a source today requires code: catalog entry + parser branch in `ExternalJobSyncFacade` (+ optional job). Not a fully pluggable plugin system, but **structured** for multi-source (better than a single Remote OK hardcode). **PARTIALLY IMPLEMENTED** extensibility.

### 6.7 Permissions / reliability

- Public list/sources: permitAll GET  
- Sync: `ADMINISTRATEUR` only  
- Remote OK: depends on third-party availability/ToS; disabled by default after product choice against international remote noise  
- Maroc feed: local file reliability high; **freshness/authenticity low** until replaced by a real partner API

**Tests:** `RemoteOkParserTest`, `MarocPartnerFeedParserTest`, `ExternalJobAndCvIntegrationTest` (sync disabled when no sources enabled in test profile).

---

## 7. Subscription and business model

### 7.1 Catalog (seeded defaults)

From `BillingCatalogService.seed`:

| Code | Audience | Monthly | Yearly | AI credits | Stated features |
|---|---|---|---|---|---|
| ESSENTIEL | Candidat | 0 | 0 | 5 | Apply + matching + 5 AI |
| PERFORMANCE | Candidat | 39 | 390 | 40 | Higher AI + prep/rewrite |
| ACCES | Recruteur | 0 | 0 | 20 | Publish + pipeline + **5 seats** |
| PREMIUM | Recruteur | 299 | 2990 | 200 | **25 seats** + crown |

Admin can edit credits/prices (`/admin/plans`). Seat limits are **hardcoded in `PlanTier.seatLimit()`**, not read from editable plan row — **inconsistency risk** if admin changes copy but seats stay 5/25.

### 7.2 What paid actually unlocks (code reality)

- **More AI credits** (enforced)
- **More seats** for recruiters (enforced)
- **Premium crown UI state** (`isPremium`)
- Matching, applying, pipeline, publishing core ATS features are **available on free tiers** (subject to verification for publish)

Help/UI correctly state paid plans do **not** boost ranking — aligned with code.

**Naming trap:** API field `premiumUnlimited` means “paid tier” (PERFORMANCE/PREMIUM), **not** unlimited credits — `allowed` remains `used < limit` (`AiCreditService.status`).

### 7.3 Checkout / payment

| Step | Reality | Status |
|---|---|---|
| Quote HT/TVA/TTC | Server-side catalog math | **IMPLEMENTED AND VERIFIED** |
| Create pending invoice | `PaymentService.prepare` | **IMPLEMENTED AND VERIFIED** |
| Pay | `MockPaymentProvider.charge(scenario)` SUCCESS/FAILURE | **SIMULATED** |
| Reject PAN/CVV keys server-side | `PaymentService.rejectCardData` | **IMPLEMENTED AND VERIFIED** |
| Idempotency key | required, replay-safe | **IMPLEMENTED AND VERIFIED** |
| Activate plan after success | `grant(...)` | **IMPLEMENTED AND VERIFIED** |
| Subscription expiry / downgrade cron | opportunistic `expireIfDue` on subscribe/cancel only | **PARTIALLY IMPLEMENTED** |
| Invoice PDF | `InvoicePdfService` | **IMPLEMENTED AND VERIFIED** |
| Card rejection + mock pay tests | `BillingPaymentIntegrationTest` | **IMPLEMENTED AND VERIFIED** |
| Real CMI/Stripe/PayPal | — | **NOT IMPLEMENTED** |

Frontend checkout collects card-like fields for UX but must send `scenario: SUCCESS` without PAN (`checkout.js` product intent). Server ignores/rejects card fields.

**Classification of checkout:** **SIMULATED payment** inside an **IMPLEMENTED** billing state machine.

### 7.4 Value for paying customers today

- Candidates: mostly **AI volume** (prep, rewrite, coach, summaries)
- Recruiters: **team scale (seats)** + **AI volume** (kits, refusal drafts, summaries)
- Not: better search ranking, exclusive inventory, or verified payment compliance

---

## 8. Current strengths and weaknesses

### A. Existing strengths (implemented and useful)

1. Explainable local matching with anti-gaming floor — rare honesty vs black-box “AI match %”.
2. Full bilateral ATS spine (apply ? statuses ? interviews ? notifications).
3. Org seats/roles + ICE-oriented trust workflow suited to Morocco.
4. AI credit ledger that does not charge failed/local calls.
5. Catalog places with flags (MA-first + world cities) for coherent location UX.
6. Admin moderation, audit logs, email journal — operational maturity for an academic SaaS.
7. External offer architecture that **redirects to origin** instead of faking in-platform apply.

### B. Existing weaknesses

1. Maroc external inventory is a **sample feed** with **homepage links**.
2. Payment is mock — cannot claim production monetization.
3. “OCR” naming oversells PDF text extraction.
4. Interview kit API is template-driven and **UI-detached**; still can debit if LLM path succeeds.
5. Company verification is **internal registry** (`officialApiAvailable: false`), easy to confuse with OMPIC live verification.
6. Seat limits not driven by editable `CommercialPlan` fields.
7. External matching is skills-only (`explain`), weaker than internal weighted `evaluate`.
8. OFFER/HIRED and retired scorecard/HM-assign endpoints create legacy surface area / dashboard drift.
9. No embeddings / semantic match — synonyms limited to catalog curation.
10. Remote OK disabled + stale docs: product story can disagree with runtime defaults.
11. `premiumUnlimited` naming oversells finite paid credit pools.
12. No scheduled subscription downgrade job — expiry is opportunistic.

### C. Missing capabilities (high-level)

- Real payment PSP + invoices for tax compliance at scale
- Authorized live job connectors (company ATS, partner APIs with deep links)
- Semantic/skills inference from CV into confirmed profile (with user approval UX)
- Multi-country compliance packs, employer branding pages at LinkedIn depth
- Candidate messaging / chat with recruiters
- Advanced analytics (time-to-hire cohorts, source ROI)
- Mobile apps
- True OCR engine
- Recommendation system beyond filter ?50% and sort by score

### D. Potential differentiation (realistic)

See §9–§11.

---

## 9. Competitive differentiation opportunities

### vs LinkedIn

LinkedIn wins network effects, content, sourcing, and brand. HireLink cannot out-LinkedIn LinkedIn.  
**HireLink edge if doubled down:** Morocco trust (ICE), bilingual FR workflow, **transparent match**, ATS for SMEs who will never leave their careers page but need a shared candidate workspace.

### vs Indeed / job boards

Indeed wins distribution. HireLink’s board is small; external sample feed is not competitive inventory.  
**Edge:** quality of **application handling after the click**, not the click itself — unless real deep-linked Maroc feeds arrive.

### vs modern ATS (Greenhouse, Lever, local ATS)

ATS giants win integrations and enterprise workflows.  
**HireLink edge:** lighter bilingual product + candidate-facing match transparency + academic-demo completeness (billing shell, credits, verification story).

### Candidate “why HireLink?”

1. See **why** an offer fits (criteria, missing skills) without paying.
2. Apply + track in one FR-first pipeline.
3. Optional AI prep when credits remain.
4. Discover some partner/external roles without fake in-app apply.

### Company “why HireLink alongside careers site?”

1. Keep official site / ATS as system of record for brand apply if desired, **or** use HireLink as operational ATS for campus/SME hiring.
2. Shared team seats, SLA reminders, refusal drafts, interview kits.
3. Verified org badge for candidate trust on the Moroccan market.
4. External listing can deep-link back to **their** site when feed URLs are real permalinks — HireLink as discovery companion, not jail.

### Ranked opportunities (value × feasibility × cost × academic fit)

| Rank | Opportunity | User value | Feasibility | Cost | Academic fit |
|---|---|---|---|---|---|
| 1 | **Transparent Match Report** as product brand (exportable PDF “fit sheet”) | High | High (reuse `Compatibility`) | Low | Excellent |
| 2 | **Real Maroc partner feed with deep links** (contract + JSON) | High | Medium | Medium | Excellent |
| 3 | **CV ? suggested skills confirmation UX** (not silent auto-match) | High | High | Low–Med | Excellent |
| 4 | **Companion mode**: “Apply on company site” + optional HireLink tracking link | High for cos | Medium | Medium | Strong |
| 5 | **Replace mock pay with one real PSP sandbox** | Medium (credibility) | Medium | Medium | Good |
| 6 | Semantic skills (embeddings) | Medium–High | Medium–Hard | High | Mixed (complexity) |
| 7 | LinkedIn-like social feed | Low differentiation | Hard | Very high | Poor |

---

## 10. Five prioritized product improvements

1. **Ship “Fiche de compatibilité” (candidate + recruiter)**  
   Package existing `Compatibility` criteria into a stable API + printable view. Zero new AI cost. Makes the unique logic *visible*.

2. **Replace sample Maroc feed with a documented partner contract + deep `applyUrl`s**  
   Keep parser; change data. Until then, UI must not imply live Rekrute/Emploi.ma synchronization.

3. **CV assist ? confirm skills**  
   After PDF text extract, one-click confirm suggested skills into `users.skills` so matching lights up without confusion.

4. **Separate “AI kit” from “template kit”**  
   Either show local questions without LLM debit, or show LLM questions. Current hybrid is logically inconsistent.

5. **Billing honesty + seat source of truth**  
   Keep mock pay labeled for demos; drive `seatLimit` from `CommercialPlan` (or stop advertising admin-editable seats). Prepare one PSP adapter interface (already partially abstracted via `PaymentProvider`).

---

## 11. Recommended unique value proposition

> **HireLink is the bilingual Moroccan hiring workspace where every match score is explainable, every external offer sends candidates to the real source, and every SME team gets an ATS without abandoning its career site.**

Supporting pillars already grounded in code:

- Explainable local matching (`SkillMatchingService` + catalog)
- Origin-respecting external jobs (`originUrl` / `applyUrl`)
- Org trust + seats (`CompanyVerificationService`, `PlanTier.seatLimit`)
- Metered AI assistants that do not replace the score

Avoid UVPs that the code cannot defend: “official Rekrute integration”, “live OMPIC API”, “real card payments”, “AI matching engine”.

---

## 12. Technical evidence and remaining uncertainties

### High-confidence evidence map

| Claim | Primary evidence |
|---|---|
| Matching is deterministic & free | `SkillMatchingService.java`, `docs/SCORE_COMPATIBILITE.md`, `SkillMatchingServiceTest` |
| Score frozen at apply | `ApplicationService` (~lines setting `matchScore`) |
| AI credit reserve/settle | `AiCreditService.reserve/settle/release` |
| LLM providers | `HttpLlmProvider.java`, `application.properties` `hirelink.llm.*` |
| Payment mock | `MockPaymentProvider`, `BillingController` class Javadoc |
| Card data rejected | `PaymentService.CARD_KEYS` / `rejectCardData` |
| Plan prices/credits | `BillingCatalogService.seed` |
| Seat limits 5/25 | `PlanTier.seatLimit()` |
| External multi-source | `ExternalSourceCatalog`, `ExternalJobSyncFacade` |
| Maroc sample homepage URLs | `feeds/maroc-emploi-partenaires.v1.json` |
| Remote OK off by default | `application.properties` `remoteok.enabled=false` |
| No OCR engine | `OcrService` notes |
| Interview kit local questions | `AiAssistController.interviewKit` + `AiCreditService.interviewKit` |
| Language check free | `AiAssistController.languageCheck` (no reserve) |
| External absence hide 7d | `ExternalJobImportService.ABSENCE_BEFORE_HIDE` |
| Pipeline statuses | `ApplicationStatus.java` |

### Remaining uncertainties

| Topic | Why unknown |
|---|---|
| Which LLM keys are live in demo | Env/secrets not inspected (forbidden) |
| Production SMTP delivery rates | Depends on deployment mail config |
| Google OAuth in prod | client-id may be empty; `dev-bypass` exists for tests |
| Legal stance of Remote OK usage if enabled | Product/ToS question beyond code |
| Whether any real partner signed for MAROC_EMPLOI | No contract artifact in repo; feed is local sample |
| End-to-end browser journeys after latest nav changes | Read-only constraint; no mutating browser tests this pass |
| Full admin/recruiter permission matrix edge cases | Would need exhaustive security test matrix review |
| Google / passkey / TOTP happy paths in this environment | Code present; live E2E not re-run in this audit |
| Whether `docs/sources-offres-externes.md` will be updated | Currently drifts from Maroc-default runtime |

### Test coverage signals (non-exhaustive)

- Matching: `SkillMatchingServiceTest`
- Billing: `BillingQuoteTest`, `BillingPaymentIntegrationTest`
- Externals: `RemoteOkParserTest`, `MarocPartnerFeedParserTest`, `ExternalJobAndCvIntegrationTest`
- Many UI journeys covered historically by Playwright docs under `docs/` but not re-executed in this audit

---

## Final answer

**What can HireLink realistically offer that would make candidates and companies choose it, without requiring companies to abandon their existing recruitment websites?**

HireLink’s credible offer is to be the **trust and decision layer around hiring**, not the exclusive destination of every apply click:

- For **candidates**: a Morocco-first place to understand *fit* (explainable scores, missing skills), track applications when they apply on-platform, and still jump out to a company’s real offer URL when the job is external — without being trapped in a fake in-app apply.
- For **companies**: an operational ATS (pipeline, SLA, interviews, team seats, refusal/interview assists) and a discovery channel that can **deep-link back to the official career page**, while ICE-oriented verification and transparent matching improve candidate trust.

That combination — **explainable matching + origin-respecting aggregation + SME ATS** — is already substantially present in code. What must not be sold yet: live official board partnerships, true OCR, embedding-based AI matching, or real card acquiring. Strengthen the partner feed and the “fit sheet” productization, and HireLink has a coherent academic-to-market story that LinkedIn and Indeed do not emphasize.
