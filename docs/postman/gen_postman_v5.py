#!/usr/bin/env python3
# Genere la collection Postman de HireLink (v5) a partir d'un tableau compact.
# Deux fichiers produits :
#   HireLink_v5_api.postman_collection.json     — tout le playbook, ecritures comprises (a jouer avec precaution)
#   HireLink_v5_lecture_seule.postman_collection.json — seulement GET + connexions : safe pour la base de demo
import json, pathlib

BASE = "http://localhost:8081/api/v1"

# (dossier, nom, methode, chemin, corps, token, attendu, lecture_seule)
# token: None | "candidate" | "recruiter" | "admin" | "var:<nom>"
R = []
def add(folder, name, method, path, body=None, token=None, expect=200, ro=True):
    R.append((folder, name, method, path, body, token, expect, ro))

# 00 — Sante et decouverte
add("00 Sante", "GET /health — app + MySQL", "GET", "/health", None, None, 200)
add("00 Sante", "GET /auth/config — methodes d'auth disponibles", "GET", "/auth/config", None, None, 200)
add("00 Sante", "GET /auth/captcha — epreuve anti-robot", "GET", None, None, None, 200)  # placeholder, corrige plus bas
R.pop()
add("00 Sante", "POST /auth/captcha — epreuve anti-robot", "POST", "/auth/captcha", None, None, 200)

# 01 — Connexion des trois roles (capture des jetons)
add("01 Connexion", "POST /auth/login — candidat", "POST", "/auth/login",
    {"email": "candidat@hirelink.ma", "password": "Candidat123!"}, None, 200)
R[-1] = R[-1] + ("candidate",)
add("01 Connexion", "POST /auth/login — recruteur", "POST", "/auth/login",
    {"email": "recruteur@hirelink.ma", "password": "Recruteur123!"}, None, 200)
R[-1] = R[-1] + ("recruiter",)
add("01 Connexion", "POST /auth/login — administrateur", "POST", "/auth/login",
    {"email": "admin@hirelink.ma", "password": "Admin123!"}, None, 200)
R[-1] = R[-1] + ("admin",)
add("01 Connexion", "POST /auth/login — mot de passe errone (401)", "POST", "/auth/login",
    {"email": "candidat@hirelink.ma", "password": "MauvaisMdp!"}, None, 401, False)
R[-1] = R[-1] + (None,)

# 02 — Profil et session
add("02 Profil", "GET /me — profil complet du candidat", "GET", "/me", None, "candidate", 200)
add("02 Profil", "PATCH /me — niveau d'experience valide (Junior)", "PATCH", "/me",
    {"experienceLevel": "Junior"}, "candidate", 200, False)
add("02 Profil", "PATCH /me — niveau hors liste refuse (400)", "PATCH", "/me",
    {"experienceLevel": "Bac+3"}, "candidate", 400, False)
add("02 Profil", "GET /me/export — export RGPD", "GET", "/me/export", None, "candidate", 200)
add("02 Profil", "POST /auth/refresh — renouveler le jeton", "POST", "/auth/refresh", None, None, 200)
add("02 Profil", "GET /auth/me — route supprimee (404 attendu)", "GET", "/auth/me", None, "candidate", 404)
add("02 Profil", "GET /ai/status — route supprimee (404 attendu)", "GET", "/ai/status", None, "candidate", 404)
add("02 Profil", "GET /me sans jeton (401 attendu)", "GET", "/me", None, None, 401)

# 03 — Annuaire public et catalogues
add("03 Public", "GET /offers/public — offres publiees", "GET", "/offers/public", None, None, 200)
add("03 Public", "GET /organizations/public — annuaire entreprises", "GET", "/organizations/public", None, None, 200)
add("03 Public", "GET /organizations/public/company — vitrine Atlas Digital", "GET",
    "/organizations/public/company?name=Atlas%20Digital", None, None, 200)
add("03 Public", "GET /catalog/suggest?kind=contract — types de contrat", "GET",
    "/catalog/suggest?kind=contract&q=CD", None, None, 200)
add("03 Public", "GET /catalog/suggest?kind=city — villes", "GET",
    "/catalog/suggest?kind=city&q=Tet", None, None, 200)
add("03 Public", "GET /catalog/suggest?kind=level — type retire (repli metier)", "GET",
    "/catalog/suggest?kind=level&q=Bac", None, None, 200)
add("03 Public", "GET /skills/suggest sans jeton — route authentifiee (401)", "GET",
    "/skills/suggest?q=Spring", None, None, 401)
add("03 Public", "GET /catalog/places — tous les lieux", "GET", "/catalog/places", None, None, 200)
add("03 Public", "GET /skills/suggest — competences proches (authentifie)", "GET",
    "/skills/suggest?q=Spring", None, "candidate", 200)

# 04 — Espace recruteur : offre et cycle de vie
add("04 Recruteur", "GET /offers/mine — mes offres", "GET", "/offers/mine", None, "recruiter", 200)
add("04 Recruteur", "POST /offers — creer une offre de test", "POST", "/offers",
    {"title": "Offre sonde Postman a supprimer", "description": "Offre creee par la collection Postman. "
     "Elle sert a verifier le cycle de vie complet puis est supprimee a la fin du run.",
     "location": "Tetouan", "contractType": "CDD", "durationMonths": 6, "experienceLevel": "JUNIOR",
     "educationLevel": "BAC+5", "workMode": "SUR_SITE", "salaryMin": 12000, "salaryMax": 16000,
     "salaryPeriod": "MONTH", "currency": "MAD", "positions": 1, "deadline": "2026-12-31"},
    "recruiter", 200, False)
R[-1] = R[-1] + ("newOffer",)
add("04 Recruteur", "PUT /offers/{id} — modifier l'offre creee", "PUT", "/offers/{{newOfferId}}",
    {"title": "Offre sonde Postman modifiee", "description": "Description modifiee par la collection, "
     "pour verifier que la mise a jour est bien persistee.", "location": "Tanger", "contractType": "CDD",
     "durationMonths": 9, "experienceLevel": "JUNIOR", "workMode": "HYBRIDE", "positions": 2},
    "recruiter", 200, False)
add("04 Recruteur", "POST /offers/{id}/publish — publier", "POST", "/offers/{{newOfferId}}/publish",
    None, "recruiter", 200, False)
add("04 Recruteur", "POST /offers/{id}/extend — prolonger de 7 jours", "POST", "/offers/{{newOfferId}}/extend",
    {"days": 7}, "recruiter", 200, False)
add("04 Recruteur", "POST /offers/{id}/extend — 999 jours refuse (400)", "POST", "/offers/{{newOfferId}}/extend",
    {"days": 999}, "recruiter", 400, False)
add("04 Recruteur", "POST /offers/{id}/unpublish — retirer de la vitrine", "POST",
    "/offers/{{newOfferId}}/unpublish", None, "recruiter", 200, False)
add("04 Recruteur", "POST /offers/{id}/close — cloturer la collecte", "POST", "/offers/{{newOfferId}}/close",
    None, "recruiter", 200, False)
add("04 Recruteur", "POST /offers/{id}/archive — archiver", "POST", "/offers/{{newOfferId}}/archive",
    None, "recruiter", 200, False)
add("04 Recruteur", "POST /offers/{id}/reopen — reouverture refusee si archivee (400)", "POST",
    "/offers/{{newOfferId}}/reopen", None, "recruiter", 400, False)
add("04 Recruteur", "POST /offers/{id}/duplicate — relancer la recherche", "POST",
    "/offers/{{newOfferId}}/duplicate", None, "recruiter", 200, False)
R[-1] = R[-1] + ("dupOffer",)
add("04 Recruteur", "DELETE /offers/{id} — supprimer la sonde", "DELETE", "/offers/{{newOfferId}}",
    None, "recruiter", 200, False)
add("04 Recruteur", "DELETE /offers/{id} — supprimer la copie", "DELETE", "/offers/{{dupOfferId}}",
    None, "recruiter", 200, False)
add("04 Recruteur", "POST /offers — titre trop court refuse (400)", "POST", "/offers",
    {"title": "AB", "description": "Titre de deux caracteres, sous la limite @Size(min=3).",
     "location": "Tetouan", "contractType": "CDI"}, "recruiter", 400, False)

# 05 — Pipeline et candidatures
add("05 Pipeline", "GET /applications/pipeline — dossiers du recruteur", "GET", "/applications/pipeline",
    None, "recruiter", 200)
add("05 Pipeline", "GET /applications/pace — signal de rythme de reponse", "GET", "/applications/pace",
    None, "recruiter", 200)
add("05 Pipeline", "GET /applications/mine — candidatures du candidat", "GET", "/applications/mine",
    None, "candidate", 200)
add("05 Pipeline", "GET /applications/{id}/timeline — frise du dossier 11", "GET",
    "/applications/11/timeline", None, "candidate", 200)
add("05 Pipeline", "GET /refusal-reasons — motifs actifs", "GET", "/refusal-reasons", None, "recruiter", 200)
add("05 Pipeline", "GET /offers/{id} — detail d'une offre publiee", "GET", "/offers/1", None, "candidate", 200)

# 06 — Entretiens (lecture seule : la planification est un ecriture)
add("06 Entretiens", "GET /applications/{id}/interview.ics — 404: aucun entretien planifie en base de demo", "GET",
    "/applications/11/interview.ics", None, "candidate", 404)
add("06 Entretiens", "GET /applications/999999/interview.ics — candidature inexistante (404)", "GET",
    "/applications/999999/interview.ics", None, "candidate", 404)
add("06 Entretiens", "PUT /applications/{id}/interview — fuseau inconnu refuse (400)", "PUT",
    "/applications/11/interview",
    {"type": "ONSITE", "kind": "TECHNIQUE", "startsAt": "2026-12-01T10:00:00", "timeZone": "Mars/Phobos",
     "location": "Immeuble test"}, "recruiter", 400, False)

# 07 — Organisation et equipe
add("07 Organisation", "GET /organizations/mine — espace entreprise", "GET", "/organizations/mine",
    None, "recruiter", 200)
add("07 Organisation", "GET /organizations/mine/members — equipe", "GET", "/organizations/mine/members",
    None, "recruiter", 200)
add("07 Organisation", "GET /organizations/mine/events — evenements", "GET", "/organizations/mine/events",
    None, "recruiter", 200)
add("07 Organisation", "GET /organizations/mine/notes — post-it partages", "GET", "/organizations/mine/notes",
    None, "recruiter", 200)
add("07 Organisation", "POST /organizations/mine/invite — inviter un membre", "POST",
    "/organizations/mine/invite", {"email": "sonde.postman@hirelink.ma", "orgRole": "RECRUTEUR"},
    "recruiter", 200, False)
add("07 Organisation", "GET /organizations/invitations/{token} — invitation sans compte", "GET",
    "/organizations/invitations/{{inviteToken}}", None, None, 410, False)

# 08 — Notifications
add("08 Notifications", "GET /notifications — mon fil", "GET", "/notifications", None, "candidate", 200)
add("08 Notifications", "POST /notifications/stream-ticket — ticket SSE", "POST",
    "/notifications/stream-ticket", None, "candidate", 200)

# 09 — IA interne (credit-consommante, donc un seul appel de controle)
add("09 IA", "GET /ai/credits — solde du mois", "GET", "/ai/credits", None, "candidate", 200)
add("09 IA", "POST /ai/match-explain — ecart de matching", "POST", "/ai/match-explain",
    {"skills": "Java, Spring Boot, MySQL", "requiredSkills": "Java, React, Docker"}, "candidate", 200, False)
add("09 IA", "POST /ai/interview-kit — reserve au recruteur (403 pour un candidat)", "POST",
    "/ai/interview-kit", {"jobTitle": "Developpeur Java", "skills": "Java"}, "candidate", 403, False)

# 10 — Facturation
add("10 Facturation", "GET /billing/plans — catalogue selon audience", "GET", "/billing/plans",
    None, "recruiter", 200)

# 11 — Administration
add("11 Admin", "GET /admin/stats — compteurs globaux", "GET", "/admin/stats", None, "admin", 200)
add("11 Admin", "GET /admin/charts — candidatures par statut", "GET", "/admin/charts", None, "admin", 200)
add("11 Admin", "GET /admin/organizations — entreprises", "GET", "/admin/organizations", None, "admin", 200)
add("11 Admin", "GET /admin/offers — toutes les offres", "GET", "/admin/offers", None, "admin", 200)
add("11 Admin", "GET /admin/reports — file de moderation", "GET", "/admin/reports", None, "admin", 200)
add("11 Admin", "GET /admin/users — comptes", "GET", "/admin/users", None, "admin", 200)
add("11 Admin", "GET /admin/audit — journal de securite", "GET", "/admin/audit", None, "admin", 200)
add("11 Admin", "GET /admin/search?q=Sara — recherche transversale", "GET", "/admin/search?q=Sara",
    None, "admin", 200)
add("11 Admin", "GET /admin/ai-usage — consommation IA du mois", "GET", "/admin/ai-usage", None, "admin", 200)
add("11 Admin", "GET /admin/stats avec un jeton candidat (403 attendu)", "GET", "/admin/stats",
    None, "candidate", 403)

# 12 — Securite du compte
add("12 Securite", "GET /account/security — sessions, MFA, passkeys", "GET", "/account/security",
    None, "candidate", 200)
add("12 Securite", "GET /account/sessions — liste des sessions", "GET", "/account/sessions",
    None, "candidate", 200)

# 13 — Isolation entre organisations
add("13 Isolation", "GET /applications/by-offer/{id} — offre d'une autre organisation (403)", "GET",
    "/applications/by-offer/15", None, "recruiter", 403)
add("13 Isolation", "GET /applications/by-offer/{id} — offre de sa propre organisation (200)", "GET",
    "/applications/by-offer/1", None, "recruiter", 200)


def req(entry):
    folder, name, method, path, body, token, expect, ro = entry[:8]
    url = BASE + path if path else None
    # Postman reconstruit l'URL depuis host/path/query : sans ce tableau, le "?" et ses parametres sont perdus
    import urllib.parse as up
    tail = (path or "").strip("/")
    base_part, _, query_part = tail.partition("?")
    query = [{"key": up.unquote(kv[0]), "value": up.unquote(kv[1] if len(kv) > 1 else "")}
             for kv in [x.split("=", 1) for x in query_part.split("&") if x]]
    item = {
        "name": name,
        "request": {
            "method": method,
            "header": [{"key": "Content-Type", "value": "application/json"}] if body is not None else [],
            "url": {"raw": url, "host": ["{{baseUrl}}"], "path": ["api", "v1"] + [p for p in base_part.split("/") if p],
                    "query": query},
            "description": "Genere par gen_postman_v5.py — attendu: %d" % expect,
        },
        "event": [{"listen": "test", "script": {"type": "text/javascript", "exec": [
            "pm.test('statut %d', function () { pm.response.to.have.status(%d); });" % (expect, expect),
            "pm.test('reponse en moins de 4 s', function () { pm.expect(pm.response.responseTime).to.be.below(4000); });",
        ]}}],
    }
    if body is not None:
        item["request"]["body"] = {"mode": "raw", "raw": json.dumps(body, ensure_ascii=False)}
    # capture de jeton sur les trois connexions reussies
    if method == "POST" and path == "/auth/login" and body and body.get("email", "").startswith(("candidat", "recruteur", "admin")):
        var = {"candidat": "candidateToken", "recruteur": "recruiterToken", "admin": "adminToken"}[body["email"].split("@")[0]]
        item["event"][0]["script"]["exec"].extend([
            "var j = pm.response.json();",
            "if (j.accessToken) { pm.collectionVariables.set('%s', j.accessToken); }" % var,
            "pm.test('accessToken present', function () { pm.expect(j.accessToken).to.be.a('string'); });",
        ])
    if path == "/offers" and method == "POST" and expect == 200:
        item["event"][0]["script"]["exec"].extend([
            "var j = pm.response.json();",
            "if (j.id) { pm.collectionVariables.set('newOfferId', j.id); }",
        ])
    if "/duplicate" in (path or ""):
        item["event"][0]["script"]["exec"].extend([
            "var j = pm.response.json();",
            "if (j.id) { pm.collectionVariables.set('dupOfferId', j.id); }",
        ])
    if "/invite" in (path or ""):
        item["event"][0]["script"]["exec"].extend([
            "var j = pm.response.json();",
            "if (j.token) { pm.collectionVariables.set('inviteToken', j.token); }",
        ])
    auth = {"candidate": "{{candidateToken}}", "recruiter": "{{recruiterToken}}", "admin": "{{adminToken}}"}
    if token in auth:
        item["request"]["header"].append({"key": "Authorization", "value": "Bearer " + auth[token]})
    return item


def build(only_read_only):
    items, order = [], []
    for e in R:
        # en lecture seule on garde les GET et les trois connexions (seuls POST sans ecriture metier)
        is_login = e[2] == "POST" and e[3] == "/auth/login"
        if only_read_only and (not e[7] or (e[2] != "GET" and not is_login)):
            continue
        f = e[0]
        if f not in order:
            order.append(f)
            items.append({"name": f, "item": []})
        items[order.index(f)]["item"].append(req(e))
    items = [i for i in items if i["item"]]
    return {
        "info": {
            "name": "HireLink v5 — lecture seule" if only_read_only else "HireLink v5 — parcours complet",
            "description": ("Collection generees le 2026-11-06 par docs/postman/gen_postman_v5.py. "
                            "Lecture seule : aucune ecriture, jouable sur la base de demonstration sans risque. "
                            "Le parcours complet cree puis supprime ses propres offres de sonde."),
            "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json",
        },
        "item": items,
        "variable": [
            {"key": "baseUrl", "value": "http://localhost:8081"},
            {"key": "candidateToken", "value": ""},
            {"key": "recruiterToken", "value": ""},
            {"key": "adminToken", "value": ""},
            {"key": "newOfferId", "value": ""},
            {"key": "dupOfferId", "value": ""},
            {"key": "inviteToken", "value": ""},
        ],
    }


out = pathlib.Path(__file__).resolve().parent
(out / "HireLink_v5_api.postman_collection.json").write_text(
    json.dumps(build(False), ensure_ascii=False, indent=2), encoding="utf-8")
(out / "HireLink_v5_lecture_seule.postman_collection.json").write_text(
    json.dumps(build(True), ensure_ascii=False, indent=2), encoding="utf-8")
full, ro = build(False), build(True)
print("requetes completes:", sum(len(i["item"]) for i in full["item"]), "dossiers:", len(full["item"]))
print("requetes lecture seule:", sum(len(i["item"]) for i in ro["item"]))
