# -*- coding: utf-8 -*-
"""Génère le Cahier des charges HireLink v5 — spécification vérifiée contre le code.

Règle directrice : aucune fonctionnalité inventée. Chaque exigence, chaque règle,
chaque message citedécoulent d'une lecture du dépôt (backend, frontend, docs).
Ce qui n'existe pas est explicitement listé en section 2.3 « hors périmètre ».
"""
from pathlib import Path
import re
from docx import Document
from docx.shared import Pt, Cm, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.oxml.ns import qn, nsdecls
from docx.oxml import parse_xml

OUT_DIRS = [
    Path(r"C:\Users\daurinia\Desktop\HireLink\docs\cahier-des-charges"),
    Path(r"C:\Users\daurinia\Desktop\HireLink-cahier-des-charges"),
    Path(r"C:\Users\daurinia\Desktop"),
]
FILENAME = "HireLink_Cahier_des_charges_v5.docx"
ASSETS = Path(r"C:\Users\daurinia\Desktop\HireLink\frontend\assets")

ROYAL = RGBColor(0x0B, 0x1A, 0x4D)
GREY = RGBColor(0x55, 0x5F, 0x71)
WHITE = RGBColor(0xFF, 0xFF, 0xFF)
HEADER_BG = "0B1A4D"
ALT_BG = "F3F5FA"
PAGE_W = 17.0  # largeur utile A4 avec marges de 2 cm


# --------------------------------------------------------------------- style
def set_run(run, *, bold=False, size=11, color=None, italic=False):
    run.bold = bold
    run.italic = italic
    run.font.size = Pt(size)
    run.font.name = "Calibri"
    run._element.rPr.rFonts.set(qn("w:eastAsia"), "Calibri")
    if color is not None:
        run.font.color.rgb = color


def add_heading_custom(doc, text, level=1):
    p = doc.add_paragraph()
    p.paragraph_format.space_before = Pt(16 if level == 1 else 10)
    p.paragraph_format.space_after = Pt(6)
    p.paragraph_format.keep_with_next = True
    run = p.add_run(text)
    sizes = {1: 16, 2: 13, 3: 11}
    set_run(run, bold=True, size=sizes.get(level, 11), color=ROYAL)
    return p


def add_para(doc, text, *, bold=False, size=11, space_after=6, italic=False, color=None):
    p = doc.add_paragraph()
    p.paragraph_format.space_after = Pt(space_after)
    p.paragraph_format.space_before = Pt(0)
    run = p.add_run(text)
    set_run(run, bold=bold, size=size, italic=italic, color=color)
    return p


def add_caption(doc, text):
    p = doc.add_paragraph()
    p.paragraph_format.space_before = Pt(6)
    p.paragraph_format.space_after = Pt(3)
    run = p.add_run(text)
    set_run(run, bold=True, size=9, color=GREY)
    return p


def add_bullets(doc, items, size=11):
    for it in items:
        p = doc.add_paragraph(style="List Bullet")
        p.clear()
        run = p.add_run(it)
        set_run(run, size=size)
        p.paragraph_format.space_after = Pt(2)


def shade_cell(cell, hex_color):
    tcPr = cell._tc.get_or_add_tcPr()
    shd = parse_xml(f'<w:shd {nsdecls("w")} w:fill="{hex_color}" w:val="clear"/>')
    tcPr.append(shd)


def set_cell_text(cell, text, *, bold=False, color=None, size=9):
    cell.text = ""
    p = cell.paragraphs[0]
    p.paragraph_format.space_before = Pt(1)
    p.paragraph_format.space_after = Pt(1)
    run = p.add_run(text)
    set_run(run, bold=bold, size=size, color=color)


def add_table(doc, headers, rows, col_widths=None, size=9):
    total = sum(col_widths) if col_widths else 0.0
    assert total <= PAGE_W + 0.001, f"table trop large : {total:.2f} cm > {PAGE_W} cm"
    table = doc.add_table(rows=1 + len(rows), cols=len(headers))
    table.style = "Table Grid"
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    table.autofit = False
    for i, h in enumerate(headers):
        cell = table.rows[0].cells[i]
        set_cell_text(cell, h, bold=True, color=WHITE, size=size)
        shade_cell(cell, HEADER_BG)
    for r_i, row in enumerate(rows):
        assert len(row) == len(headers), f"ligne {r_i} : {len(row)} cellules pour {len(headers)} en-têtes"
        for c_i, val in enumerate(row):
            cell = table.rows[r_i + 1].cells[c_i]
            set_cell_text(cell, str(val), size=size)
            if r_i % 2 == 1:
                shade_cell(cell, ALT_BG)
    if col_widths:
        for row in table.rows:
            for i, w in enumerate(col_widths):
                row.cells[i].width = Cm(w)
    tail = doc.add_paragraph()
    tail.paragraph_format.space_after = Pt(8)
    return table


def add_logo_row(doc, size_cm=(3.0, 3.0, 3.2)):
    names = ["logo-uae.png", "logo-ensa.png", "logo-hirelink-icon.png"]
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    missing = [n for n in names if not (ASSETS / n).exists()]
    if missing:
        set_run(p.add_run("[logos indisponibles : " + ", ".join(missing) + "]"), size=9, color=GREY)
        return
    for n, w in zip(names, size_cm):
        run = p.add_run()
        run.add_picture(str(ASSETS / n), width=Cm(w))
        if n != names[-1]:
            set_run(p.add_run("   "), size=12)


# --------------------------------------------------------------- page de garde
def add_cover(doc):
    for _ in range(2):
        doc.add_paragraph()
    add_logo_row(doc)
    t = doc.add_paragraph()
    t.alignment = WD_ALIGN_PARAGRAPH.CENTER
    set_run(t.add_run("HireLink"), bold=True, size=34, color=ROYAL)

    s = doc.add_paragraph()
    s.alignment = WD_ALIGN_PARAGRAPH.CENTER
    set_run(s.add_run("Cahier des charges — version 5"), bold=True, size=17, color=ROYAL)

    s2 = doc.add_paragraph()
    s2.alignment = WD_ALIGN_PARAGRAPH.CENTER
    set_run(s2.add_run("Plateforme bilatérale de gestion du recrutement (ATS)"), size=12, color=GREY)

    s3 = doc.add_paragraph()
    s3.alignment = WD_ALIGN_PARAGRAPH.CENTER
    s3.paragraph_format.space_before = Pt(4)
    set_run(s3.add_run("Spécification fonctionnelle et technique — document de référence\n"
                       "Toute exigence décrite ici est traçable dans le code livré (section 9)."),
            italic=True, size=10, color=GREY)

    doc.add_paragraph()
    meta = [
        ("Établissement", "ENSA Tétouan — Université Abdelmalek Essaâdi"),
        ("Filière", "Génie Informatique (GI) — Semestre 5"),
        ("Module", "Frameworks et Technologies Web"),
        ("Réalisé par", "EL ASSAL Douae"),
        ("Projet", "HireLink — ATS français pour PME marocaines"),
        ("Date", "6 novembre 2026"),
        ("Version du document", "5.0"),
        ("Backend", "Spring Boot 3.3 / Java 21 — http://localhost:8081/api/v1"),
        ("Frontend", "HTML, CSS et JavaScript statiques — http://localhost:5500"),
        ("Base de données", "MariaDB 10.4.32 — schéma hirelink, 25 tables"),
    ]
    add_table(doc, ["Rubrique", "Valeur"], meta, col_widths=[4.6, 12.4], size=10)


def add_sommaire(doc):
    doc.add_page_break()
    add_heading_custom(doc, "Sommaire", 1)
    add_para(doc, "Numérotation stable : EF = exigence fonctionnelle, EN = exigence non fonctionnelle, "
                  "R = règle de gestion, GO = objectif, RQ = risque. Repère de lecture : la section 9 "
                  "relie chaque EF à un fichier et à une ligne du dépôt.", size=10, italic=True, color=GREY)
    rows = [
        ("1", "Contexte et problématique", "1.1 terrain, 1.2 question, 1.3 réponse produit, 1.4 périmètre technique"),
        ("2", "Objectifs et périmètre", "GO-01 à GO-09, ce qui est dans le périmètre, ce qui n'y est pas"),
        ("3", "Acteurs et personas", "rôles, six personas, matrice des droits réels, comptes de démonstration"),
        ("4", "Exigences fonctionnelles", "EF-01 à EF-71 regroupées en huit modules"),
        ("5", "Règles de gestion", "R01 à R16 et RM01 tels qu'écrits dans le code, plus onze règles RG-nn"),
        ("6", "Exigences non fonctionnelles", "EN-01 à EN-34 : sécurité, performance, exploitation, ergonomie, RGPD"),
        ("7", "Modèle de données", "25 tables, relations, énumérations et vocabulaires contrôlés"),
        ("8", "Parcours fonctionnels", "six parcours pas à pas, avec le point d'observation"),
        ("9", "Matrice de traçabilité", "EF → fichier:line → preuve observable"),
        ("10", "Planning, risques, limites, glossaire", "séances, RQ-01 à RQ-10, annexes"),
    ]
    add_table(doc, ["§", "Titre", "Contenu"], rows, col_widths=[1.0, 5.4, 10.6], size=9)


# ------------------------------------------------------------------- section 1
def section_1(doc):
    doc.add_page_break()
    add_heading_custom(doc, "1. Contexte et problématique", 1)

    add_heading_custom(doc, "1.1 Le terrain observé", 2)
    add_para(doc, "Le recrutement dans les PME marocaines se pratique encore par boîtes aux lettres. "
                  "Une annonce est publiée sur un ou deux sites, les candidatures arrivent en pièces jointes, "
                  "la sélection se fait dans un classeur, et l'état d'une candidature n'est connu que de celui "
                  "qui a ouvert le fichier.")
    add_para(doc, "Les logiciels de suivi de candidature existants sont presque tous étrangers "
                  "(Workable, Greenhouse, SmartRecruiters, Teamtailor). Ils sont facturés par siège et dans une "
                  "devise non locale, leur contrat d'entrée de gamme se situe autour de 30 à 100 € par siège et "
                  "par mois, et ils supposent un processus de recrutement déjà formalisé. Une entreprise de "
                  "moins de cinquante salariés n'y a pas accès.")
    add_para(doc, "Le second constat concerne les candidats. Les petites annonces non contrôlées restent un "
                  "canal de fraude : demandes de frais d'inscription, fausses offres de télétravail, entreprises "
                  "sans existence légale vérifiable. Un candidat ne dispose d'aucun moyen, dans les outils "
                  "grand public, de distinguer une société immatriculée au registre marocain d'une coquille vide, "
                  "ni de savoir ce que sa candidature est devenue.")

    add_caption(doc, "Tableau 1.1 — Ce qui pose problème, et ce que le problème produit comme exigence")
    add_table(doc,
              ["Constat", "Effet mesurable", "Exigence qui en découle"],
              [
                  ("Candidatures dispersées en e-mail", "Aucun état partagé entre RH, manager et candidat.",
                   "Un dossier unique, un pipeline explicite, un historique append-only."),
                  ("Coût des ATS étrangers", "Licence en euros par siège ; engagement annuel.",
                   "Un produit utilisable sans licence, avec des formules tarifées en dirhams."),
                  ("Opacité pour le candidat", "Aucune réponse, ou une réponse après plusieurs semaines.",
                   "Suivi interne du délai pour le recruteur et rythme réellement mesuré pour le candidat."),
                  ("Offres frauduleuses", "Perte de temps, quelquefois perte d'argent.",
                   "Contrôle du contenu des annonces et identification de l'entreprise avant publication."),
                  ("Décisions non tracées", "Impossible d'expliquer un refus, donc d'améliorer le process.",
                   "Motif de refus obligatoire pris dans un catalogue, conservé dans l'historique."),
                  ("Comparaison de candidats subjective", "Le dossier est jugé sur le nom ou l'école.",
                   "Score de compatibilité calculé sur des critères déclarés, figé à la candidature, et "
                   "prompts d'aide sans données d'identité."),
              ],
              col_widths=[4.2, 6.0, 6.8], size=9)

    add_heading_custom(doc, "1.2 Problématique", 2)
    add_para(doc, "Comment une PME marocaine peut-elle conduire un recrutement complet — publication vérifiée, "
                  "réception des candidatures, pipeline, entretien, décision motivée et tracée — sans licence "
                  "étrangère, sans exposer le candidat à une annonce non contrôlée, et en donnant au candidat "
                  "une information honnête et vérifiable sur le traitement de sa candidature ?", bold=True)
    add_para(doc, "Le mot « honnête » est opérationnel ici : une information n'est affichée au candidat que si "
                  "elle est produite par une donnée mesurée dans la base, jamais par un texte promis. Cette "
                  "position conditionne plusieurs choix décrits en sections 4, 5 et 6.")

    add_heading_custom(doc, "1.3 Réponse réellement portée par l'application", 2)
    add_para(doc, "Six mécanismes portent la promesse « recrutement à performance équitable ». Chacun est décrit "
                  "avec son pendant absent, pour que la promesse ne dépasse pas le produit.")
    add_caption(doc, "Tableau 1.2 — Promesse, mécanisme codé, limite acceptée")
    add_table(doc,
              ["Promesse", "Mécanisme réellement en place", "Limite assumée"],
              [
                  ("Le recruteur est tenu par un délai interne",
                   "job_offers.response_sla_days ; deadline calculée à la candidature "
                   "(applications.sla_due_at) ; SlaMonitorService, exécuté toutes les 30 minutes, notifie "
                   "l'auteur de l'offre et le responsable du poste assigné.",
                   "Le délai n'est pas affiché au candidat : le champ reste un simple suivi interne, comme l'indique le "
                   "commentaire du DTO (ApiDtos, responseSlaDays)."),
                  ("Le candidat voit un rythme, pas une promesse",
                   "ResponsivenessService : médiane du premier recruteur-réponse sur 180 jours, minimum trois "
                   "dossiers répondus ; étiquettes « Réactif » (70 % des réponses en 3 jours ou moins) et "
                   "« Attentif » (50 %).",
                   "Jamais d'étiquette négative ; aucun badge embarquable sur un site tiers ; en dessous de trois "
                   "dossiers répondus, aucun signal n'est affiché."),
                  ("L'entreprise est identifiée avant de publier",
                   "CompanyVerificationService : format ICE 15 chiffres, rapprochement du nom, statuts de "
                   "vérification ; assertCanPublish bloque la publication tant que l'entreprise n'est pas vérifiée.",
                   "Aucun appel à une API OMPIC payante ; la vérification documentaire reste décidée par un "
                   "administrateur (PATCH /admin/organizations/{id})."),
                  ("La communauté peut faire retirer une annonce",
                   "POST /offers/{id}/report et OfferReport ; à trois signalements traités, "
                   "JobOfferService suspend l'offre (REPORT_SUSPEND_THRESHOLD = 3) ; l'administrateur rétablit ou "
                   "écarte.",
                   "Le comptage est une alerte, pas une enquête : aucun outil de preuve, aucune indemnisation."),
                  ("Une décision laisse une trace",
                   "application_status_history en append-only, timeline publique au dossier, motif de refus pris "
                   "dans refusal_reason_catalog, first_decision_at rempli au premier refus.",
                   "La timeline n'expose pas le détail interne aux candidats : le payload est réservé aux "
                   "recruteurs (R03)."),
                  ("L'aide automatique ne juge pas sur l'identité",
                   "R15 : les prompts ne reçoivent que compétences, intitulé et niveau ; R16 : aucun crédit IA "
                   "débité si l'appel échoue ; score de compatibilité déterministe, sans API tierce (R08).",
                   "Aucun modèle n'est entraîné par HireLink ; les aides IA produisent des brouillons relus par un "
                   "humain, jamais des décisions automatiques."),
              ],
              col_widths=[3.6, 8.0, 5.4], size=9)

    add_heading_custom(doc, "1.4 Périmètre technique observé", 2)
    add_caption(doc, "Tableau 1.3 — Couches livrées et volumétrie réelle du dépôt")
    add_table(doc,
              ["Couche", "Technologie", "Volumétrie vérifiée dans le dépôt"],
              [
                  ("API", "Spring Boot 3.3, Java 21, Maven",
                   "20 contrôleurs REST, 151 endpoints sous /api/v1, 51 services, 35 classes de modèle dont "
                   "24 entités @Table et 12 énumérations."),
                  ("Persistance", "Spring Data JPA / Hibernate, MariaDB 10.4.32",
                   "base hirelink, hibernate.ddl-auto=update : 24 tables d'entités + la jointure job_offer_tags, soit 25 tables."),
                  ("Interface", "HTML, CSS et JavaScript sans framework",
                   "9 pages publiques (frontend/*.html), 18 pages applicatives (frontend/app/*.html), "
                   "58 fichiers JS, 21 fichiers CSS."),
                  ("Sécurité", "Spring Security 6, JWT, WebAuthn, TOTP",
                   "filtre JWT, cookie de rafraîchissement HttpOnly, CAPTCHA auto-hébergé, journal d'audit."),
                  ("Exploitation", "Deux processus locaux",
                   "API sur le port 8081, interface sur le port 5500 ; aucun conteneur, aucune infrastructure "
                   "manquante pour la démonstration."),
              ],
              col_widths=[2.2, 4.6, 10.2], size=9)


# ------------------------------------------------------------------- section 2
def section_2(doc):
    doc.add_page_break()
    add_heading_custom(doc, "2. Objectifs et périmètre", 1)

    add_heading_custom(doc, "2.1 Objectifs mesurables", 2)
    add_para(doc, "Un objectif n'est retenu que s'il est vérifiable en exécutant l'application. La colonne "
                  "« vérification » donne le geste à faire, la colonne « observation » donne ce qui doit être "
                  "vu.")
    add_caption(doc, "Tableau 2.1 — Objectifs du projet et preuve attendue")
    add_table(doc,
              ["Réf", "Objectif", "Indicateur", "Vérification", "Observation de référence"],
              [
                  ("GO-01", "Publier une offre sans assistance",
                   "Temps de création d'une offre publiable", "Compte recruteur → Offre nouvelle → Assistant en "
                   "trois étapes → Publier.", "Moins de dix minutes ; aucune correction manuelle en base."),
                  ("GO-02", "Traiter une candidature de bout en bout",
                   "Nombre de clics entre réception et décision", "Pipeline, changement d'étape, motif de refus.",
                   "Une transition et une décision motivée par écran."),
                  ("GO-03", "Historiser toute décision",
                   "Une ligne par changement d'état", "GET /applications/{id}/timeline après trois transitions.",
                   "Trois lignes, aucune modification possible des lignes précédentes."),
                  ("GO-04", "Interdire la décision sans motif",
                   "Taux de refus motivés", "Passer un dossier en refus sans motif.",
                   "HTTP 400 « Choisissez un motif de refus. »."),
                  ("GO-05", "Isoler les organisations",
                   "Nombre d'accès inter-entreprises réussis", "Appeler le pipeline d'une offre d'une autre "
                   "organisation.", "HTTP 403 « Hors organisation (R02) », ligne dans audit_logs."),
                  ("GO-06", "Rendre le rythme de réponse visible",
                   "Médiane de la première réponse affichée", "Ouvrir la fiche d'une entreprise dont un recruteur a "
                   "répondu à au moins trois dossiers.", "Médiane en jours, étiquette Réactif ou Attentif le cas "
                   "échéant."),
                  ("GO-07", "Bloquer les annonces à risque",
                   "Publications refusées sur contenu", "Publier une offre reprenant une formule d'arnaque "
                   "connue.", "HTTP 403 « Publication refusée — contenu à risque (fake job)… »."),
                  ("GO-08", "Permettre l'exercice des droits RGPD",
                   "Export et effacement obtenus", "GET /me/export puis POST /account/erase.",
                   "Fichier JSON téléchargé ; profil anonymisé effacé après revalidation."),
                  ("GO-09", "Coût d'usage nul pour démarrer",
                   "Prix des formules de base", "GET /billing/plans, qui ne renvoie que les formules de l'audience "
                   "du compte.", "ESSENTIEL et ACCES à 0 dirham ; à 0 dirham de TTC la facture porte le statut "
                   "OFFERTE, sinon REGLEE sans encaissement réel (limite de démonstration, voir section 2.3)."),
              ],
              col_widths=[1.3, 3.3, 3.0, 4.9, 4.5], size=9)

    add_heading_custom(doc, "2.2 Ce qui est dans le périmètre", 2)
    add_caption(doc, "Tableau 2.2 — Modules spécifiés et références d'exigences")
    module_rows = []
    premier = 1
    for titre, exigences in MODULES_EF:
        dernier = premier + len(exigences) - 1
        nom = re.sub(r"^\d+\.\d+\s+", "", titre)
        bornes = (f"EF-{premier:02d}" if premier == dernier
                  else f"EF-{premier:02d} à EF-{dernier:02d}")
        module_rows.append((nom, bornes, str(len(exigences))))
        premier = dernier + 1
    add_table(doc, ["Module", "Références", "Nombre"], module_rows,
              col_widths=[8.0, 5.4, 3.6], size=9)
    total_ef = sum(len(e) for _, e in MODULES_EF)
    add_para(doc, f"Total : {total_ef} exigences fonctionnelles, {len(MODULES_EF)} modules.")
    add_para(doc, "Volumétrie de référence de la base locale, comptée le 6 novembre 2026 dans le schéma "
                  "hirelink et à recalculer avant la soutenance (les mêmes requêtes sont données ci-dessous) :")
    add_caption(doc, "Tableau 2.4 — Volumétrie de démonstration mesurée")
    add_table(doc,
              ["Grandeur", "Valeur mesurée", "Comment le revérifier"],
              [
                  ("Comptes", "130", "SELECT COUNT(*) FROM users;"),
                  ("Candidats", "32 dont 17 Junior et 9 Confirmé",
                   "SELECT experience_level, COUNT(*) FROM users WHERE role='CANDIDAT' GROUP BY 1;"),
                  ("Organisations", "42", "SELECT COUNT(*) FROM organizations;"),
                  ("Organisations avec au moins une offre publiée", "40",
                   "jointure organizations ↔ users.company ↔ job_offers.published"),
                  ("Offres", "117 dont 106 publiées et suspendues = 0",
                   "SELECT published, COUNT(*) FROM job_offers GROUP BY 1;"),
                  ("Candidatures", "217", "SELECT COUNT(*) FROM applications;"),
                  ("Tables", "25", "SHOW TABLES;"),
              ],
              col_widths=[5.4, 5.2, 6.4], size=9)

    add_heading_custom(doc, "2.3 Ce qui n'est pas dans le périmètre", 2)
    add_para(doc, "Cette section est volontairement longue. Elle écarte des fonctionnalités qui ont pu être "
                  "annoncées dans une version antérieure du document, ou qui sont attendues dans un ATS du "
                  "commerce. Rien de ce qui suit n'est implémenté, et rien de ce qui suit ne sera présenté "
                  "comme acquis dans les sections 4 à 9.", bold=True)
    add_caption(doc, "Tableau 2.3 — Hors périmètre, statut et motif de la vérification")
    add_table(doc,
              ["Élément", "Statut", "Vérification dans le dépôt"],
              [
                  ("Paiement en ligne (carte, CMI, Stripe)", "Non implémenté",
                   "Aucune dépendance de passerelle dans le POM ; POST /billing/subscribe émet seulement une "
                   "ligne de facture, marquée REGLEE dès l'émission même pour une formule payante : la facture "
                   "atteste une intention, pas un encaissement."),
                  ("Envoi réel d'e-mails en démonstration locale", "Non implémenté",
                   "hirelink.otp.log-to-console=true : les codes à usage unique sont écrits dans la console du "
                   "serveur ; la table sent_emails journalise une file interne, consultée par GET /admin/emails."),
                  ("Modèle d'IA entraîné ou affiné par HireLink", "Non implémenté",
                   "Le fournisseur est un appel externe avec chaîne de repli (groq, gemini, mistral) ; aucun "
                   "jeu de données client n'est exporté à cette fin."),
                  ("Parrainage et liens de recommandation", "Non implémenté",
                   "Ni table, ni route, ni vue ; la fonction F28 est barrée de docs/ACTEURS_FONCTIONNALITES_REGLES.md."),
                  ("Check-list d'intégration d'un nouveau recruteur", "Non implémenté",
                   "Aucune entité ni écran ; la mention figure uniquement dans la note de non-implémentation du "
                   "document CDC_V4_THEORIE_VERS_CODE.md."),
                  ("Badge de rythme embarquable sur un site d'entreprise", "Non implémenté",
                   "Aucun point d'entrée iframe ou embarquable ; les signaux de rythme ne s'affichent que dans "
                   "l'application."),
                  ("Score ou étiquette de fiabilité du candidat", "Non implémenté",
                   "Aucune table de type reliability_events ; l'application ne note pas les personnes."),
                  ("Enquête NPS ou satisfaction après une candidature", "Non implémenté",
                   "Fonction F20 retirée du document fonctionnel ; aucune route correspondante."),
                  ("Promesse salariale en ligne et acceptation électronique", "Non implémenté",
                   "Les étapes OFFER et HIRED de ApplicationStatus sont marquées dépréciées et ne figurent "
                   "aucune transition autorisée : une embauche se conclut hors de la plateforme."),
                  ("Accès jury externe à un dossier", "Non implémenté",
                   "Aucune table d'invitation de jury ; l'évaluation reste réservée aux membres de "
                   "l'organisation."),
                  ("Plusieurs entretiens pour une même candidature", "Non implémenté",
                   "Les données d'entretien sont des colonnes de la table applications : un seul créneau par "
                   "dossier, reprogrammable mais non cumulable."),
                  ("Entretien téléphonique programmable", "Non implémenté",
                   "InterviewService.SCHEDULABLE = {ONSITE, VISIO} ; le type PHONE ne subsiste qu'en lecture "
                   "pour les dossiers anciens."),
                  ("Effet bloquant d'un critère éliminatoire de la grille d'évaluation", "Non implémenté",
                   "Le booléen est stocké et affiché par ScorecardService, mais ApplicationService.advance ne "
                   "consulte pas les critères : un veto n'arrête donc aucune transition."),
                  ("Recherche, filtre et tri des offres côté serveur", "Non implémenté",
                   "GET /offers/public renvoie la liste ouverte sans paramètre de recherche ; le filtrage est "
                   "réalisé dans le navigateur."),
                  ("API publique pour tiers et webhooks sortants", "Non implémenté",
                   "Aucune route d'API publique, aucune signature sortante."),
                  ("Application mobile native", "Non implémenté",
                   "Interface responsive uniquement."),
                  ("Produit multilingue (arabe, anglais)", "Non implémenté",
                   "Les libellés sont en français, y compris les messages d'erreur."),
                  ("Envois par SMS, WhatsApp ou notification push", "Non implémenté",
                   "Canaux disponibles : notifications internes et flux SSE ; pas de passerelle externe."),
                  ("Permissions sur mesure par utilisateur", "Non implémenté",
                   "Trois rôles d'organisation fixes : OWNER, RECRUTEUR, HIRING_MANAGER."),
                  ("Migrations versionnées (Flyway ou Liquibase)", "Non implémenté",
                   "hibernate.ddl-auto=update ; database/schema.sql ne contient aucun CREATE TABLE."),
                  ("Référence OpenAPI maintenue", "Non implémenté",
                   "springdoc est dans les dépendances mais coupé par défaut (hirelink.swagger.enabled=${HI"
                   "RELINK_SWAGGER_ENABLED:false}) ; la référence reste docs/API.md, tenue à la main."),
                  ("Conteneurisation et déploiement automatisé", "Non implémenté",
                   "Aucun Dockerfile ni docker-compose dans le dépôt."),
                  ("Plan de reprise et sauvegarde chiffrée", "Non traité",
                   "Hors du cadre d'un travail d'établissement ; aucune procédure décrite ou testée."),
                  ("Compteur d'échecs de connexion persistant et multi-nœud", "Non implémenté",
                   "AuthThrottle est en mémoire : un redémarrage ou un second nœud réinitialise le comptage."),
                  ("Charge mesurée et test d'intrusion externe", "Non réalisé",
                   "Listé comme limite connue dans docs/SECURITE.md ; seuls les contrôles fonctionnels de "
                   "sécurité ont été exécutés."),
                  ("Contrôle anti-discrétionnaire du score de compatibilité", "Non implémenté",
                   "Le score est déterministe et explicable (section 5, R08), mais aucune étude statistique de "
                   "biais n'a été conduite."),
                  ("Garantie publique d'un délai de réponse au candidat", "Non implémenté",
                   "Le délai promis par le recruteur est interne ; l'aide en ligne précise qu'aucun délai n'est "
                   "promis ailleurs que par le rythme mesuré."),
                  ("Règles R06, R07, R09 et R10", "Sans correspondance dans le code",
                   "Aucune occurrence de ces références dans le backend ; elles sont retirées de la numérotation "
                   "et ne sont donc pas revendiquées en section 5."),
              ],
              col_widths=[4.6, 2.6, 9.8], size=9)


# ------------------------------------------------------------------- sections 3
def section_3(doc):
    doc.add_page_break()
    add_heading_custom(doc, "3. Acteurs et personas", 1)

    add_heading_custom(doc, "3.1 Rôles tels que définis dans le code", 2)
    add_para(doc, "Deux niveaux de rôle coexistent : le rôle de compte (Role) décide de l'espace accessible, le "
                  "rôle d'organisation (OrgRole) décide de ce qui est faisable dans l'espace d'une entreprise.")
    add_caption(doc, "Tableau 3.1 — Rôles système et rôles d'organisation")
    add_table(doc,
              ["Niveau", "Valeur", "Libellé affiché", "Effet réel dans l'application"],
              [
                  ("Role", "CANDIDAT", "Candidat",
                   "Espaces candidate-* ; POST /applications, GET /applications/mine, réponses d'entretien ; "
                   "aucun droit d'écriture sur une offre."),
                  ("Role", "RECRUTEUR", "Recruteur",
                   "Espaces recruiter-* ; crée des offres, une organisation, une équipe ; accès aux dossiers de "
                   "son organisation seulement."),
                  ("Role", "ADMINISTRATEUR", "Administrateur",
                   "Espace admin-* ; seul rôle à pouvoir écrire sur /api/v1/admin/**, vérification, offres et "
                   "factures. Non proposé à l'inscription."),
                  ("OrgRole", "OWNER", "Créateur",
                   "Gestion de l'équipe, invitations, formule d'abonnement, coordonnées ICE, transfert de "
                   "propriété."),
                  ("OrgRole", "RECRUTEUR", "Recruteur",
                   "Offres, pipeline, entretiens, grilles d'évaluation ; aucun droit sur l'équipe ni sur la "
                   "formule."),
                  ("OrgRole", "HIRING_MANAGER", "Responsable du poste",
                   "Accès limité aux dossiers auxquels il est assigné (applications.assigned_hm_user_id), lecture "
                   "du pipeline, évaluation et proposition d'entretien ; ne gère pas les offres."),
              ],
              col_widths=[1.6, 2.4, 2.4, 10.6], size=9)

    add_heading_custom(doc, "3.2 Personas", 2)
    add_caption(doc, "Tableau 3.2 — Six personas, besoins et fonctions concernées")
    add_table(doc,
              ["Persona", "Profil", "Besoin", "Fonctions mobilisées"],
              [
                  ("Salma Berrada", "Candidate, profil Junior, Tétouan, recherche un premier poste de "
                   "développeuse", "Postuler simplement, suivre l'état de chaque dossier, savoir si une "
                   "entreprise répond vraiment.", "EF-01, EF-02, EF-12, EF-13, EF-16, EF-21, EF-24, EF-28, "
                   "EF-29, EF-31, EF-45, EF-67, EF-68"),
                  ("Nadia Bennani", "Créatrice de l'espace Atlas Digital (OWNER), entreprise vérifiée",
                   "Structurer l'équipe, maîtriser la formule et le nombre de sièges, répondre aux "
                   "signalements.", "EF-02, EF-10, EF-48, EF-49, EF-50, EF-51, EF-52, EF-59"),
                  ("Karim Benali", "Recruteur membre de la même organisation",
                   "Publier et piloter ses propres offres sans se voir opposer le catalogue d'équipe.",
                   "EF-32, EF-33, EF-34, EF-35, EF-36, EF-38, EF-54, EF-55"),
                  ("Meriem Alaoui", "Responsable du poste (HIRING_MANAGER), côté métier",
                   "Ne voir que les dossiers qui la concernent, évaluer sur des critères, proposer une "
                   "rencontre.", "EF-36, EF-37, EF-39, EF-40, EF-41"),
                  ("Youssef Cherkaoui", "Administrateur de la plateforme",
                   "Contrôler la fraude, vérifier les entreprises, arbitrer les signalements, retrouver une "
                   "décision dans le journal.", "EF-27, EF-56 à EF-64"),
                  ("Visiteur sans compte", "Intéressé par une annonce depuis un lien",
                   "Lire l'offre et l'entreprise sans s'inscrire, décider ensuite s'il crée un compte.",
                   "EF-21, EF-23, EF-25, EF-26"),
              ],
              col_widths=[2.4, 3.6, 5.0, 6.0], size=9)

    add_heading_custom(doc, "3.3 Droits réels par rôle", 2)
    add_para(doc, "Tableau établi à partir des annotations de sécurité et des contrôles de service. « oui » "
                  "signifie autorisé, « non » signifie refusé par le serveur, avec le message indiqué le cas "
                  "échéant.")
    add_caption(doc, "Tableau 3.3 — Matrice des droits (V = visiteur, C = candidat, M = recruteur membre, "
                     "O = créateur, H = responsable du poste, A = administrateur)")
    add_table(doc,
              ["Action", "V", "C", "M", "O", "H", "A", "Refus serveur"],
              [
                  ("Consulter les offres publiées", "oui", "oui", "oui", "oui", "oui", "oui", "—"),
                  ("Consulter la fiche d'une organisation", "oui", "oui", "oui", "oui", "oui", "oui", "—"),
                  ("Postuler à une offre", "non", "oui", "non", "non", "non", "non", "403 « Réservé aux candidats »"),
                  ("Retirer sa candidature", "non", "oui", "non", "non", "non", "non", "403 « Seul le candidat peut retirer (R11) »"),
                  ("Créer ou modifier une offre", "non", "non", "oui", "oui", "non", "oui", "403 pour le responsable du poste (canManageOffer)"),
                  ("Publier une offre", "non", "non", "oui", "oui", "non", "oui", "403 tant que l'entreprise n'est pas vérifiée"),
                  ("Gérer l'équipe et inviter", "non", "non", "non", "oui", "non", "non", "403 « Seul le créateur de l'espace gère l'équipe (R13) »"),
                  ("Choisir la formule d'abonnement", "non", "oui (personnelle)", "non", "oui", "non", "non", "403 « Seul le créateur de l'espace choisit la formule »"),
                  ("Voir tous les dossiers de l'organisation", "non", "non", "oui", "oui", "non", "oui", "—"),
                  ("Voir un dossier de pipeline", "non", "non", "oui", "oui", "assigné", "oui", "403 « Dossier non assigné (R14) »"),
                  ("Noter sur la grille d'évaluation", "non", "non", "oui", "oui", "assigné", "non", "403 « HM : dossier non assigné (R14) »"),
                  ("Planifier un entretien", "non", "non", "oui", "oui", "assigné", "non", "403 « HM : dossier non assigné (R14) »"),
                  ("Répondre à une proposition d'entretien", "non", "oui (son dossier)", "non", "non", "non", "non", "403"),
                  ("Signaler une offre", "oui", "oui", "non", "non", "non", "non", "403 « Réservé aux candidats » (POST /offers/{id}/report)"),
                  ("Suspendre ou rétablir une offre", "non", "non", "non", "non", "non", "oui", "403 « Accès refusé. » hors /admin"),
                  ("Journal d'audit et export CSV", "non", "non", "non", "non", "non", "oui", "403 « Accès refusé. »"),
                  ("Gérer le catalogue de motifs de refus", "non", "non", "non", "non", "non", "oui", "403 « Réservé à l'administration »"),
                  ("Exporter ses données personnelles", "non", "oui", "oui", "oui", "oui", "oui", "—"),
              ],
              col_widths=[4.2, 0.8, 1.9, 0.8, 0.8, 1.3, 0.8, 6.4], size=8)

    add_heading_custom(doc, "3.4 Comptes de démonstration", 2)
    add_caption(doc, "Tableau 3.4 — Comptes semés par config/DataSeeder.java")
    add_table(doc,
              ["Rôle de démonstration", "Identifiant", "Mot de passe", "Particularité vérifiée"],
              [
                  ("Candidat", "candidat@hirelink.ma", "Candidat123!", "Alias miroir du compte Salma Berrada, même hash de mot de passe."),
                  ("Recruteur", "recruteur@hirelink.ma", "Recruteur123!", "Alias miroir d'un compte recruteur."),
                  ("Administrateur", "admin@hirelink.ma", "Admin123!", "Youssef Cherkaoui, accès à /api/v1/admin/**."),
                  ("Créatrice d'entreprise", "n.bennani@atlasdigital.ma", "Recruteur123!", "Nadia Bennani, OWNER d'Atlas Digital, ICE 001544289000035, entreprise vérifiée, formule ACCES."),
                  ("Recruteur membre", "k.benali@atlasdigital.ma", "Recruteur123!", "Membre RECRUTEUR de la même organisation, sans droit d'équipe."),
                  ("Responsable du poste", "m.alaoui@atlasdigital.ma", "Recruteur123!", "OrgRole HIRING_MANAGER, accès filtré par R14."),
              ],
              col_widths=[3.4, 4.6, 2.9, 6.1], size=9)


# ------------------------------------------------------------------- section 4
# Une exigence par ligne : (ref, exigence, preuve observable, route ou page).
MODULES_EF = [
    ("4.1 Comptes, connexion et session", [
        ("EF-01", "Créer un compte candidat ou recruteur par e-mail, avec code de vérification reçu",
         "Écran « Créer un compte », puis saisie du code à 6 chiffres",
         "POST /auth/request-otp, POST /auth/verify-register-otp"),
        ("EF-02", "Se connecter par mot de passe",
         "Jeton rendu, page d'accueil de l'espace correspondant au rôle", "POST /auth/login"),
        ("EF-03", "Se connecter par code envoyé par e-mail (sans mot de passe)",
         "Le compte sans mot de passe reçoit un code et entre", "POST /auth/request-otp, POST /auth/verify-login-otp"),
        ("EF-04", "Se connecter avec Google",
         "Bouton Google ; le compte se crée ou se rattache par `google_sub`", "POST /auth/google"),
        ("EF-05", "Se connecter par passkey (WebAuthn)",
         "Clé enregistrée puis réutilisée sur le même navigateur",
         "POST /auth/passkey/enroll/options, /finish puis /auth/passkey/login/options, /finish"),
        ("EF-06", "Double authentification TOTP avec codes de secours",
         "Page Sécurité : activation, QR, 8 codes de secours affichés une fois",
         "POST /account/mfa/setup, /mfa/enable, /mfa/backup-codes"),
        ("EF-07", "Exiger un CAPTCHA après deux échecs de connexion",
         "Épreuve image + question affichées au troisième essai", "POST /auth/captcha (l'image est rendue par le serveur)"),
        ("EF-08", "Mot de passe oublié et réinitialisation en un seul usage",
         "Lien à usage unique ; le second usage est refusé", "POST /auth/forgot, POST /auth/reset"),
        ("EF-09", "Renouveler le jeton sans re-saisir le mot de passe",
         "Navigation continue après expiration du jeton d'accès", "POST /auth/refresh (cookie HttpOnly)"),
        ("EF-10", "Voir et révoquer ses sessions et appareils",
         "Liste avec IP, étiquette d'appareil, dernière activité", "GET /account/sessions, DELETE /account/sessions/{id}"),
        ("EF-11", "Se désinscrire : désactiver le compte ou effacer ses données (RGPD)",
         "Deux actions distinctes, message de confirmation", "POST /account/deactivate, POST /account/erase"),
    ]),
    ("4.2 Profil candidat et CV", [
        ("EF-12", "Compléter son profil (identité, ville, disponibilité, expérience)",
         "Formulaire unique, jauges de complétion", "PATCH /me"),
        ("EF-13", "Choisir son niveau d'expérience dans une liste fermée",
         "Le serveur refuse un libellé hors liste", "PATCH /me — RG-09"),
        ("EF-14", "Déclarer compétences, langues et recherches",
         "Suggestion de compétences pendant la saisie", "GET /skills/suggest"),
        ("EF-15", "Préférences de contrat et de lieux",
         "Filtres pré-remplis sur la liste des offres", "PATCH /me, GET /catalog/places"),
        ("EF-16", "Importer un CV (PDF ou image) et l'analyser",
         "Le texte est extrait puis proposé en sections éditables", "POST /ocr/cv"),
        ("EF-17", "Valider section par section le CV importé",
         "Assistants de tri : compétences, expériences, langues", "POST /skills/classify, PATCH /me"),
        ("EF-18", "Conserver le CV d'origine et sa version stockée",
         "Deux champs distincts : nom affiché, nom de fichier disque", "backend/data/cvs"),
        ("EF-19", "Joindre un nouveau CV à une candidature précise",
         "Choix « Nouveau CV » dans l'assistant de candidature", "POST /applications/{id}/cv"),
        ("EF-20", "Exporter l'intégralité de ses données personnelles",
         "Fichier JSON téléchargeable", "GET /me/export"),
    ]),
    ("4.3 Découverte des offres (public)", [
        ("EF-21", "Consulter les offres publiées sans compte",
         "Grille des offres avec ville, contrat, salaire", "GET /offers/public"),
        ("EF-22", "Filtrer par ville, contrat, mode de travail, niveau, compatibilité",
         "Compteur du panneau = nombre de lignes réellement affichées", "app/candidate.html"),
        ("EF-23", "Rechercher en texte libre",
         "Résultats instantanés sur titre, ville, compétences", "GET /offers/public?q="),
        ("EF-24", "Voir le détail d'une offre et son score de compatibilité",
         "Décomposition du score (compétences demandées / présentes)", "GET /offers/{id}"),
        ("EF-25", "Consulter la vitrine publique d'une entreprise",
         "Logo, badge de vérification, offres en ligne, présentation", "GET /organizations/public/company"),
        ("EF-26", "Parcourir l'annuaire des entreprises",
         "Recherche par nom, ville, secteur", "GET /organizations/public"),
        ("EF-27", "Signaler une offre abusive",
         "La fiche offre bascule en quarantaine après seuil de signalements", "POST /offers/{id}/report"),
    ]),
    ("4.4 Candidature et pipeline", [
        ("EF-28", "Postuler en une seule action, lettre et CV inclus",
         "Message explicite si la candidature existe déjà", "POST /applications — RM01"),
        ("EF-29", "Suivre l'avancement de ses candidatures",
         "Statut, date de première décision, échéance de réponse", "GET /applications/mine"),
        ("EF-30", "Retirer sa candidature",
         "Statut WITHDRAWN, dossier grisé dans le pipeline recruteur",
         "PATCH /applications/{id}/status (status = WITHDRAWN)"),
        ("EF-31", "Voir la frise d'un dossier",
         "Chaque changement d'étape avec auteur et horodatage", "GET /applications/{id}/timeline"),
        ("EF-32", "Recruteur : voir tous les dossiers d'une offre",
         "Table triable, isolation d'organisation appliquée", "GET /applications/by-offer/{id}"),
        ("EF-33", "Recruteur : faire avancer une candidature d'une étape",
         "Le pipeline et le compteurs se remettent à jour", "PATCH /applications/{id}/status"),
        ("EF-34", "Recruteur : refuser avec un motif choisi dans un catalogue",
         "Le motif est obligatoire et enregistré",
         "PATCH /applications/{id}/status avec rejectionReason, GET /refusal-reasons"),
        ("EF-35", "Recruteur : rédiger le motif de refus avec l'aide IA",
         "Brouillon généré, modifiable avant envoi", "POST /ai/refusal-draft"),
        ("EF-36", "Shortlist et scorecard par critère",
         "Critères définis par offre, notes par évaluateur",
         "GET/PUT /offers/{offerId}/scorecard-criteria, GET/POST /applications/{appId}/scorecard"),
        ("EF-37", "Attribuer un responsable de poste (hiring manager)",
         "Le dossier devient visible pour cette personne", "PATCH /applications/{id}/assign-hm"),
        ("EF-38", "Recruteur : mesurer son rythme de réponse réel",
         "Indicateur de retard affiché sur le pipeline", "GET /applications/pace"),
    ]),
    ("4.5 Entretiens", [
        ("EF-39", "Planifier un entretien sur site ou en visioconférence",
         "Adresse obligatoire en sur site, lien obligatoire en visio", "PUT /applications/{id}/interview — RG-04"),
        ("EF-40", "Choisir la nature de l'entretien (RH, technique, manager…)",
         "Libellé affiché au candidat", "PUT /applications/{id}/interview"),
        ("EF-41", "Choisir le fuseau horaire et la durée",
         "Quatre fuseaux acceptés, durée par défaut 45 minutes", "PUT /applications/{id}/interview — RG-05"),
        ("EF-42", "Reporter un entretien",
         "La réponse du candidat est remise à zéro", "PUT /applications/{id}/interview"),
        ("EF-43", "Annuler un entretien avec une raison",
         "Le candidat est notifié de l'annulation", "POST /applications/{id}/interview/cancel"),
        ("EF-44", "Candidat : confirmer, refuser ou proposer un créneau",
         "Réponse enregistrée, vue par l'équipe", "POST /applications/{id}/interview/answer"),
        ("EF-45", "Candidat : ajouter l'entretien à son calendrier",
         "Fichier .ics téléchargé, ou 404 si aucun entretien", "GET /applications/{id}/interview.ics"),
        ("EF-46", "Rappel automatique la veille de l'entretien",
         "Une seule relance par entretien", "InterviewReminderService"),
        ("EF-47", "Agenda d'équipe avec post-it personnels",
         "Regroupement par jour, notes persistées", "app/recruiter-agenda.html"),
    ]),
    ("4.6 Organisation, équipe et collaboration", [
        ("EF-48", "Créer l'organisation depuis un compte recruteur",
         "Créateur devient OWNER", "GET /organizations/mine (ensureForRecruiter crée l'espace) — RG-01"),
        ("EF-49", "Renseigner et vérifier l'ICE",
         "Badge « Identité vérifiée » sur la vitrine publique",
         "POST /organizations/mine/ice/check, POST /organizations/mine/verify"),
        ("EF-50", "Inviter un membre par e-mail",
         "Lien d'invitation, compte rattaché à l'organisation",
         "POST /organizations/mine/invite, POST /organizations/invitations/{token}/accept"),
        ("EF-51", "Promouvoir un membre ou transférer la propriété",
         "Deux actions distinctes, tracées dans le journal",
         "PATCH /organizations/mine/members/{userId}/role, PATCH .../ownership"),
        ("EF-52", "Suspendre et rétablir un membre",
         "Le membre suspendu perd l'accès à l'espace entreprise",
         "PATCH /organizations/mine/members/{userId}/suspend, PATCH .../reinstate"),
        ("EF-53", "Notes internes et fil d'événements de l'organisation",
         "Journal horodaté des actions de l'équipe", "GET /organizations/mine/events"),
        ("EF-54", "Gérer les offres de son organisation (publier, clôturer, archiver)",
         "Chaque action a un effet visible sur la liste",
         "POST /offers/{id}/publish, /close, /archive (et /reopen, /unarchive, /unpublish)"),
        ("EF-55", "Prolonger la date limite d'une offre",
         "Nouvelle date contrôlée côté serveur", "POST /offers/{id}/extend"),
    ]),
    ("4.7 Administration, modération et facturation", [
        ("EF-56", "Modérer les offres signalées (suspendre, rétablir, dépublier)",
         "File d'attente des signalements, décision tracée", "POST /admin/offers/{id}/suspend, /restore, /unpublish"),
        ("EF-57", "Gérer les comptes (activer, suspendre, rechercher)",
         "Table des 130 comptes avec filtre", "GET /admin/users, PATCH /admin/users/{id}/active"),
        ("EF-58", "Gérer les organisations et leurs logos",
         "Logo absent = initiales, aucune image cassée", "GET /admin/organizations, PATCH /admin/organizations/{id}"),
        ("EF-59", "Gérer les plans commerciaux et les factures",
         "Une formule change les crédits IA et les sièges, rien d'autre", "GET /admin/plans, /admin/invoices"),
        ("EF-60", "Gérer le catalogue des motifs de refus",
         "Ajout, renommage, retrait d'un motif", "GET/POST /refusal-reasons"),
        ("EF-61", "Consulter statistiques, graphiques et consommation IA",
         "Entonnoir plateforme, crédits consommés par fonction", "GET /admin/stats, /admin/charts, /admin/ai-usage"),
        ("EF-62", "Consulter les e-mails réellement envoyés et leur statut",
         "Statut et erreur par destinataire, aucune promesse non tenue", "GET /admin/emails"),
        ("EF-63", "Rechercher transversalement candidats, offres et entreprises",
         "Une seule barre, résultats groupés", "GET /admin/search?q="),
        ("EF-64", "Consulter et exporter le journal d'audit",
         "Filtre par type d'action, export CSV", "GET /admin/audit"),
    ]),
    ("4.8 Notifications et assistant IA", [
        ("EF-65", "Recevoir une notification interne à chaque événement de son dossier",
         "Centre de notifications, badge non lu", "GET /notifications, NotificationService"),
        ("EF-66", "Être redirigé vers l'élément concerné depuis une notification",
         "Le lien ouvre la page et met le focus sur la ligne", "app/notifications.html + data-focus"),
        ("EF-67", "Demander une réécriture professionnelle de son profil",
         "Texte proposé, jamais appliqué sans validation", "POST /ai/profile-rewrite"),
        ("EF-68", "Se préparer à un entretien (questions attendues)",
         "Kit d'entretien généré pour l'offre choisie", "POST /ai/interview-kit"),
        ("EF-69", "Expliquer un score de compatibilité",
         "Compétences manquantes listées avec les libellés du catalogue", "POST /ai/match-explain"),
        ("EF-70", "Faire relire un texte (orthographe et ton)",
         "Corrections proposées avant envoi", "POST /ai/language-check"),
        ("EF-71", "Consulter son solde de crédits IA",
         "Coût par appel, solde restant, historique du mois", "GET /ai/credits"),
    ]),
]


def section_4(doc):
    doc.add_page_break()
    add_heading_custom(doc, "4. Exigences fonctionnelles", 1)
    add_para(doc, "Soixante-onze exigences, regroupées en huit modules. La colonne « preuve observable » "
                  "indique ce que le professeur voit à l'écran ; la colonne « implémentation » donne la route "
                  "REST ou la page qui la sert. Aucune exigence de cette liste ne décrit une fonction absente "
                  "du dépôt : les fonctions non réalisées sont nommées en section 2.3.")
    total = sum(len(m[1]) for m in MODULES_EF)
    add_caption(doc, f"Tableau 4.0 — {total} exigences fonctionnelles numérotées EF-01 à EF-{total:02d}")
    for title, rows in MODULES_EF:
        add_heading_custom(doc, title, 2)
        add_table(doc, ["Réf", "Exigence", "Preuve observable", "Implémentation"], rows,
                  col_widths=[1.3, 5.0, 5.0, 5.7], size=8)


# ------------------------------------------------------------------- section 5
# Table 5.1 : les identifiants R01…R16 et RM01 sont ceux écrits dans le code (commentaires et
# messages serveur). Table 5.2 : règles réellement codées mais sans identifiant dans le dépôt ;
# elles reçoivent ici une référence RG-nn, explicitement présentée comme une référence du document.
REGLES_CODE = [
    ("R01", "Machine d'états du pipeline : les transitions autorisées sont déclarées une seule fois "
            "(SUBMITTED → SCREENING ou REJECTED, SCREENING → SHORTLISTED ou REJECTED, SHORTLISTED → "
            "INTERVIEW ou REJECTED, INTERVIEW → REJECTED).",
     "Une transition hors table est un 400 motivé ; OFFER et HIRED n'apparaissent dans aucune transition "
     "nouvelle.", "ApplicationService (TRANSITIONS)"),
    ("R02", "Isolation par organisation : un recruteur ne voit et ne pilote que les offres et les dossiers "
            "de son organisation.",
     "403 « Aucune organisation (R02) » pour un recruteur hors organisation, « Hors organisation (R02) » sur "
     "le dossier d'un autre espace ; 403 puis 200 vérifiés en test sur /applications/by-offer.",
     "OrganizationService.requireMembership / assertSameOrg"),
    ("R03", "La frise d'un dossier n'est pas un écran public : elle est réservée aux personnes qui ont droit "
            "au dossier.",
     "403 « Accès historique refusé (R03) ».", "CatalogPrivacyController.timeline"),
    ("R04", "Un refus porte un motif pris dans le catalogue (code, par exemple PROFILE_MISMATCH).",
     "400 « Choisissez un motif de refus. » si le motif est absent, « Choisissez un motif de la liste. » s'il "
     "est hors catalogue ; first_decision_at est rempli au premier refus.",
     "ApplicationService.applyRefusalCatalog"),
    ("R05", "Checklist à trois points du dossier, pré-cochée seulement quand la donnée existe déjà.",
     "Badge « x / 3 » ; une case pré-cochée est étiquetée Auto, jamais validée à la place du recruteur.",
     "frontend/js/recruiter-pipeline.js"),
    ("R08", "Le score de compatibilité est calculé localement : aucune API tierce, aucun crédit IA consommé.",
     "Un appel de matching retire 0 crédit, visible dans /ai/credits.", "SkillMatchingService"),
    ("R11", "Retirer une candidature n'appartient qu'au candidat.",
     "403 « Seul le candidat peut retirer (R11) », 400 « Retrait impossible à cette étape (R11) ».",
     "ApplicationService.advance"),
    ("R12", "La frise est append-only : on ajoute une ligne, on n'en modifie aucune.",
     "application_status_history ne reçoit aucun UPDATE dans le code ; trois transitions = trois lignes.",
     "model/ApplicationStatusHistory"),
    ("R13", "Seul le créateur de l'espace gère l'équipe.",
     "403 « Seul le créateur de l'espace gère l'équipe (R13) », « Seul le créateur de l'espace peut le "
     "transférer », « Seul le créateur de l'espace choisit la formule ».", "OrganizationService"),
    ("R14", "Un responsable de poste ne voit que les dossiers qui lui sont attribués.",
     "403 « Dossier non assigné (R14) », « HM : dossier non assigné (R14) » ; filtrage par "
     "assigned_hm_user_id.", "OrganizationService.assertCanAccessApplication"),
    ("R15", "Les prompts d'aide automatique ne contiennent aucune donnée d'identité.",
     "Le prompt ne reçoit que compétences, intitulé du poste et niveau ; ni nom, ni e-mail, ni téléphone.",
     "AiAssistController, HttpLlmProvider, LocalLlmProvider"),
    ("R16", "Un crédit IA est débité seulement après un appel réussi.",
     "Ledger ai_usage_logs ; en cas d'échec le solde ne bouge pas.", "AiCreditService, AiAssistController"),
    ("RM01", "Une seule candidature par couple candidat / offre.",
     "409 « Candidature déjà existante (RM01) » ; une candidature retirée peut être rouverte, un doublon "
     "sur un dossier actif non.", "ApplicationService.apply"),
]

REGLES_DOCUMENT = [
    ("RG-01", "Le créateur de l'organisation en devient le propriétaire (OWNER) ; promotion d'un membre et "
              "transfert de propriété sont deux actions distinctes et journalisées.",
     "Membership.orgRole = OWNER posé à la création de l'espace.", "OrganizationService.createOwned"),
    ("RG-02", "Un dossier déjà refusé ou retiré n'est pas rejouable.",
     "Le passage d'un dossier WITHDRAWN à une autre étape est un 400 motivé.", "ApplicationService.advance"),
    ("RG-03", "Un dossier n'est planifiable en entretien que s'il est à l'étape INTERVIEW.",
     "400 « Ce dossier n'est pas à l'étape entretien. »", "ApplicationService.interviewApplication"),
    ("RG-04", "Un entretien est sur site ou en visioconférence : adresse obligatoire dans le premier cas, "
              "lien obligatoire dans le second.",
     "400 « Indiquez l'adresse de l'entretien. » / « Indiquez le lien de visioconférence. »",
     "InterviewService.schedule"),
    ("RG-05", "Le fuseau horaire d'un entretien appartient à une liste fermée de quatre valeurs.",
     "400 « Fuseau horaire non pris en charge. » pour une valeur hors liste (Mars/Phobos testé).",
     "InterviewService (ZONES)"),
    ("RG-06", "Un créneau d'entretien ne peut pas être dans le passé.",
     "400 « Le créneau d'entretien est dans le passé. »", "InterviewService.schedule"),
    ("RG-07", "Reporter un entretien remet à zéro la réponse du candidat.",
     "interview_confirmation repasse à PENDING : l'invitation redevient « en attente ».",
     "InterviewService.schedule"),
    ("RG-08", "Le rappel d'entretien de la veille part une seule fois.",
     "interview_reminder_sent passe à true et n'est plus re-déclenché.", "InterviewReminderService"),
    ("RG-09", "Le niveau d'expérience d'un profil appartient à la liste du formulaire.",
     "400 « Niveau d'expérience hors liste : Stagiaire, Junior, Confirmé, Senior, Lead, Manager ».",
     "MeController"),
    ("RG-10", "Une offre signalée au-delà du seuil est suspendue d'office et sort de la recherche publique.",
     "quarantine_reason = SUSPENDED_BY_REPORTS, seuil REPORT_SUSPEND_THRESHOLD = 3.", "JobOfferService"),
    ("RG-11", "Tout échec remonte un statut HTTP et un corps JSON portant uniquement le champ message en "
              "français ; le front affiche ce message, jamais un texte inventé.",
     "49 statuts attendus sur 49 retrouvés, 101 assertions sans échec (run newman du 6 novembre 2026).",
     "config/GlobalExceptionHandler"),
]


def section_5(doc):
    doc.add_page_break()
    add_heading_custom(doc, "5. Règles de gestion", 1)
    add_para(doc, "Chaque règle est écrite dans le service nommé, pas seulement dans l'interface : "
                  "un appel direct à l'API est soumis aux mêmes contrôles qu'une navigation. "
                  "Les messages cités sont ceux renvoyés par le serveur, relevés pendant les tests.")
    add_heading_custom(doc, "5.1 Règles portant l'identifiant écrit dans le code", 2)
    add_para(doc, "Les références R01 à R16 et RM01 sont celles des commentaires et des messages du dépôt : "
                  "les rechercher dans le code ramène à la ligne qui applique la règle.", size=10, italic=True,
             color=GREY)
    add_caption(doc, f"Tableau 5.1 — {len(REGLES_CODE)} règles identifiées dans le code")
    add_table(doc, ["Réf", "Règle", "Effet vérifié / message serveur", "Où elle est codée"], REGLES_CODE,
              col_widths=[1.1, 5.4, 6.0, 4.5], size=8)
    add_heading_custom(doc, "5.2 Règles de gestion sans identifiant dans le code", 2)
    add_para(doc, "Ces règles sont réellement appliquées par le serveur, mais le dépôt ne les nomme pas par une "
                  "référence Rxx. Elles reçoivent ici une référence RG-nn, qui est une référence de ce document "
                  "et non du code : c'est écrit pour qu'aucune référence inventée ne passe pour une référence "
                  "du dépôt.", size=10, italic=True, color=GREY)
    add_caption(doc, f"Tableau 5.2 — {len(REGLES_DOCUMENT)} règles référencées par le document seul")
    add_table(doc, ["Réf", "Règle", "Effet vérifié / message serveur", "Où elle est codée"], REGLES_DOCUMENT,
              col_widths=[1.3, 5.2, 6.0, 4.5], size=8)


# ------------------------------------------------------------------- section 6
EN = [
    ("Sécurité des accès", [
        ("EN-01", "Jeton JWT court, cookie de rafraîchissement HttpOnly, session double expiration "
                  "(inactivité et durée absolue).", "UserSession.idleExpiresAt / absoluteExpiresAt"),
        ("EN-02", "Autorisation par rôle sur chaque route (@PreAuthorize) et par organisation dans le service.",
         "403 sur /admin/stats avec un jeton candidat (testé)"),
        ("EN-03", "Politique de mot de passe au serveur, hash bcrypt, jamais en clair ni en log.",
         "security/PasswordPolicy"),
        ("EN-04", "Anti-robot : CAPTCHA après deux échecs, limitation du nombre de tentatives par IP et compte.",
         "AuthThrottle, RateLimitFilter"),
        ("EN-05", "En-têtes de sécurité sur toutes les réponses, dont Content-Security-Policy et "
                  "X-Content-Type-Options.", "SecurityHeadersFilter (relevés dans les réponses newman)"),
        ("EN-06", "Échappement systématique des valeurs injectées dans le DOM ; aucune concaténation HTML brute "
                  "des données serveur.", "js/ui.js + audits de lot 50"),
        ("EN-07", "Journaux d'audit horodatés sur les actions sensibles (connexion, refus, export, décision "
                  "d'offre) avec IP et agent.", "AuditService, GET /admin/audit"),
    ]),
    ("Données et conformité", [
        ("EN-08", "Export complet des données personnelles en un appel.", "GET /me/export"),
        ("EN-09", "Deux niveaux d'effacement : désactivation réversible ou effacement définitif.",
         "POST /account/deactivate, POST /account/erase"),
        ("EN-10", "Purge périodique des données mortes (tokens, sessions, corbeille).", "RetentionService"),
        ("EN-11", "Aucune colonne inutile : les tables mortes ont été supprimées, les colonnes lues sont "
                  "toutes affichées quelque part.", "lot F — nettoyage base"),
        ("EN-12", "Un seul vocabulaire par colonne : le serveur refuse un libellé hors liste.", "RG-09"),
    ]),
    ("Performance et robustesse", [
        ("EN-13", "Toutes les réponses de la suite lecture seule arrivent en moins de 4 secondes ; moyenne "
                  "mesurée 193 ms, pire cas 1 733 ms.", "docs/postman/resultat-lecture-seule.md"),
        ("EN-14", "Requêtes indexées sur les chemins chauds (offres publiées, candidatures par offre, "
                  "sessions par utilisateur).", "contraintes et index JPA"),
        ("EN-15", "Aucune exception non gérée : tout échec remonte un statut HTTP explicite.",
         "GlobalExceptionHandler"),
        ("EN-16", "L'application démarre sans infrastructure externe : l'e-mail et l'IA sont optionnels.",
         "--spring.mail.host= ; LocalLlmProvider sans clé"),
    ]),
    ("Ergonomie et interface", [
        ("EN-17", "Interface entièrement en français, messages à la première personne, ton sobre.",
         "copies relues page par page"),
        ("EN-18", "Un compteur de filtre annonce le nombre de lignes réellement affichées.", "lot 52"),
        ("EN-19", "Les dates sont calculées dans le fuseau local de l'utilisateur, pas en UTC.", "lot 53"),
        ("EN-20", "États à données zéro dessinés, jamais un tableau vide ni un « 0 » trompeur.", "lot 54"),
        ("EN-21", "Navigation clavier complète : focus rendu après modale, piégeage dans les voiles, "
                  "intitulés explicites.", "lots 59 et 60"),
        ("EN-22", "Reprise après session expirée : l'utilisateur revient sur la page voulue après reconnexion.",
         "lot 57"),
        ("EN-23", "Aucune promesse de visibilité ou de délai non mesurable ; les badges reflètent un fait "
                  "compté.", "lot 34"),
        ("EN-24", "Images 100 % locales : logos ENSA, université et HireLink servis depuis le dépôt.",
         "frontend/assets"),
    ]),
    ("Exploitation", [
        ("EN-25", "Deux processus suffisent : API sur 8081, interface statique sur 5500.", "docs/API.md"),
        ("EN-26", "Schéma créé par Hibernate (ddl-auto=update) ; le script SQL n'est plus la référence.",
         "25 tables, 4 seulement dans database/schema.sql"),
        ("EN-27", "Données de démonstration reproductibles par DataSeeder (comptes, organisations, offres).",
         "config/DataSeeder"),
        ("EN-28", "Les secrets ne sortent pas du poste : clés d'API dans application-local.properties "
                  "(gitignored), sauvegarde de base exclue du dépôt.", ".gitignore"),
        ("EN-29", "Consommation IA visible : coût par appel, solde, historique mensuel.", "GET /ai/credits"),
    ]),
    ("Qualité et testabilité", [
        ("EN-30", "Une collection Postman complète (73 requêtes) et une collection lecture seule (49) "
                  "rejouables sans abîmer la base de démonstration.", "docs/postman"),
        ("EN-31", "Le run lecture seule est exécuté en ligne de commande (newman) et son résultat est archivé.",
         "resultat-lecture-seule.json"),
        ("EN-32", "Les routes mortes sont retirées du code puis vérifiées 404, pas masquées côté front.",
         "GET /auth/me, GET /ai/status"),
        ("EN-33", "Le code retiré est déplacé et documenté, jamais détruit silencieusement.",
         "docs/dead-code"),
        ("EN-34", "Chaque exigence de ce document est reliée à un fichier du dépôt (section 9).",
         "matrice de traçabilité"),
    ]),
]


def section_6(doc):
    doc.add_page_break()
    add_heading_custom(doc, "6. Exigences non fonctionnelles", 1)
    add_para(doc, "Trente-quatre exigences, chacune avec le moyen qui a servi à la vérifier. Une exigence non "
                  "fonctionnelle n'est recevable que si elle est mesurée ou observée ; les mesures citées "
                  "viennent du run newman du 6 novembre 2026.")
    for index, (titre, rows) in enumerate(EN, start=1):
        add_heading_custom(doc, f"6.{index} {titre}", 2)
        add_table(doc, ["Réf", "Exigence", "Vérification"], rows,
                  col_widths=[1.3, 9.7, 6.0], size=8)


# ------------------------------------------------------- sections 7 à 10 et assemblage
import cdc_v5_sections_b as sections_b  # noqa: E402  (mêmes helpers, fichier voisin)


def main():
    doc = Document()
    for zone in doc.sections:
        zone.top_margin = Cm(2)
        zone.bottom_margin = Cm(2)
        zone.left_margin = Cm(2)
        zone.right_margin = Cm(2)
    normal = doc.styles["Normal"]
    normal.font.name = "Calibri"
    normal.font.size = Pt(11)

    add_cover(doc)
    add_sommaire(doc)
    for build in (section_1, section_2, section_3, section_4, section_5, section_6):
        build(doc)

    page_break = lambda d: d.add_page_break()
    sections_b.section_7(doc, add_heading_custom, add_table, add_para, add_caption, page_break)
    sections_b.section_8(doc, add_heading_custom, add_table, add_para, add_caption, page_break)
    sections_b.section_9(doc, add_heading_custom, add_table, add_para, add_caption, page_break)
    sections_b.section_10(doc, add_heading_custom, add_table, add_para, add_caption, page_break, add_bullets)

    ecrits = []
    for dossier in OUT_DIRS:
        dossier.mkdir(parents=True, exist_ok=True)
        chemin = dossier / FILENAME
        doc.save(str(chemin))
        ecrits.append(chemin)
    for chemin in ecrits:
        print(f"{chemin}  {chemin.stat().st_size:,} o")
    print(f"{len(doc.tables)} tableaux, {len(doc.paragraphs)} paragraphes, "
          f"{sum(len(m[1]) for m in MODULES_EF)} exigences fonctionnelles, "
          f"{sum(len(r) for _, r in EN)} exigences non fonctionnelles, "
          f"{len(sections_b.TRAÇABILITÉ)} lignes tracées")
    introuvables = [ref for ref, _, marker, root in sections_b.TRAÇABILITÉ
                    if sections_b._anchor(marker, root)[0] is None]
    print("marqueurs introuvables :", introuvables or "aucun")


if __name__ == "__main__":
    main()
