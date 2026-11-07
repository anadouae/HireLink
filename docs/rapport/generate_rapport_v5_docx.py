# -*- coding: utf-8 -*-
"""Rapport de projet HireLink — version 5, 6 novembre 2026.

Le document est construit à partir des faits relevés dans le dépôt au moment de la génération :
les compteurs (contrôleurs, services, tables, requêtes, mesures newman) sont écrits ici après
vérification, et les figures sont les fichiers réellement produits dans docs/diagrammes/v5 et
docs/guide-captures/v5-2026-11-06. Aucune capture et aucun diagramme n'est simulé : si un
fichier manque, le rapport le signale au lieu de l'inventer.
"""
import pathlib
import sys
from docx import Document
from docx.shared import Pt, Cm, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent.parent / "cahier-des-charges"))

import generate_cdc_v5_docx as cdc
from generate_cdc_v5_docx import (
    add_bullets, add_caption, add_heading_custom, add_logo_row, add_para, add_table,
)

REPO = pathlib.Path(r"C:\Users\daurinia\Desktop\HireLink")
ASSETS = REPO / "frontend" / "assets"
DIAG = REPO / "docs" / "diagrammes" / "v5"
SHOTS = REPO / "docs" / "guide-captures" / "v5-2026-11-06"
POSTMAN = REPO / "docs" / "postman"

OUT_DIRS = [
    REPO / "docs" / "rapport",
    pathlib.Path(r"C:\Users\daurinia\Desktop\HireLink-rapport"),
    pathlib.Path(r"C:\Users\daurinia\Desktop"),
]
FILENAME = "HireLink_Rapport_de_projet_v5.docx"

ROYAL = cdc.ROYAL
GREY = cdc.GREY

_titre_cdc = add_heading_custom


def add_heading_custom(doc, text, level=1):
    """Titre de style Word réel (volet de navigation), avec la mise en forme du document."""
    p = _titre_cdc(doc, text, level)
    p.style = doc.styles[f"Heading {min(level, 3)}"]
    cdc.set_run(p.runs[0], bold=True, size={1: 16, 2: 13, 3: 11}.get(level, 11), color=ROYAL)
    return p


def add_pied_de_page(doc):
    """« Titre · page N » centré en pied de page, hors page de garde."""
    from docx.oxml import OxmlElement
    from docx.oxml.ns import qn
    for zone in doc.sections:
        zone.different_first_page_header_footer = True
        p = zone.footer.paragraphs[0]
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        cdc.set_run(p.add_run("HireLink — rapport de projet v5 · page "), size=9, color=GREY)
        champ = OxmlElement("w:fldSimple")
        champ.set(qn("w:instr"), "PAGE \\* MERGEFORMAT")
        run = OxmlElement("w:r")
        props = OxmlElement("w:rPr")
        couleur = OxmlElement("w:color")
        couleur.set(qn("w:val"), "6B7280")
        taille = OxmlElement("w:sz")
        taille.set(qn("w:val"), "18")
        props.append(couleur)
        props.append(taille)
        run.append(props)
        texte = OxmlElement("w:t")
        texte.text = "1"
        run.append(texte)
        champ.append(run)
        p._p.append(champ)


# --------------------------------------------------------------------- figures
def add_image(doc, path: pathlib.Path, largeur_cm, legende):
    if not path.exists():
        add_para(doc, f"[figure absente : {path.name}]", italic=True, color=GREY, size=9)
        return False
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_before = Pt(6)
    p.add_run().add_picture(str(path), width=Cm(largeur_cm))
    add_caption(doc, legende)
    return True


def figure(doc, nom_fichier, legende, largeur=16.6):
    return add_image(doc, DIAG / nom_fichier, largeur, legende)


def capture(doc, nom_fichier, legende, largeur=16.2):
    return add_image(doc, SHOTS / nom_fichier, largeur, legende)


# --------------------------------------------------------------- page de garde
def add_cover(doc):
    add_logo_row(doc, size_cm=(3.2, 3.2, 3.4))
    add_para(doc, "ROYAUME DU MAROC", size=11, bold=True, color=GREY)
    add_para(doc, "Université Abdelmalek Essaâdi", size=12, bold=True)
    add_para(doc, "École Nationale des Sciences Appliquées de Tétouan", size=12, bold=True)
    add_para(doc, "Génie Informatique — Semestre 5", size=11, color=GREY)
    for _ in range(2):
        doc.add_paragraph()
    t = doc.add_paragraph()
    t.alignment = WD_ALIGN_PARAGRAPH.CENTER
    cdc.set_run(t.add_run("HireLink"), bold=True, size=40, color=ROYAL)
    s = doc.add_paragraph()
    s.alignment = WD_ALIGN_PARAGRAPH.CENTER
    cdc.set_run(s.add_run("Plateforme bilatérale de gestion du recrutement"), bold=True, size=15, color=ROYAL)
    s2 = doc.add_paragraph()
    s2.alignment = WD_ALIGN_PARAGRAPH.CENTER
    cdc.set_run(s2.add_run("Rapport de projet — version 5"), size=13, color=GREY)
    for _ in range(2):
        doc.add_paragraph()
    add_para(doc, "Résumé : application web complète (Spring Boot + MariaDB + front statique) qui"
                  " couvre la publication vérifiée d'une offre, la candidature, le pipeline, l'entretien"
                  " et la décision motivée, avec un candidat informé honnêtement du traitement de son"
                  " dossier.", size=11, italic=True, color=GREY)
    doc.add_paragraph()
    meta = [
        ("Projet", "HireLink — ATS de démonstration, testé en local"),
        ("Établissement", "ENSA Tétouan — Université Abdelmalek Essaâdi"),
        ("Filière", "Génie Informatique, semestre 5"),
        ("Année universitaire", "2026 – 2027"),
        ("Version du document", "5.0 — 6 novembre 2026"),
        ("Backend", "Spring Boot 3.3.13, Java 21 — http://localhost:8081/api/v1"),
        ("Frontend", "HTML, CSS, JavaScript statiques — http://localhost:5500"),
        ("Base de données", "MariaDB 10.4.32, schéma hirelink, 25 tables"),
        ("Livrables", "rapport (ce fichier), cahier des charges v5, 12 diagrammes, "
         "1 export BPMN 2.0, 23 captures, 2 collections Postman et leur run"),
    ]
    add_table(doc, ["Rubrique", "Valeur"], meta, col_widths=[4.6, 12.4], size=10)


def add_sommaire(doc):
    doc.add_page_break()
    add_heading_custom(doc, "Sommaire", 1)
    add_para(doc, "Douze chapitres et cinq annexes. Le chapitre 10 contient les preuves mesurées "
                  "(run newman du 6 novembre 2026) ; le chapitre 11 regroupe les limites assumées. "
                  "Chaque figure renvoie à un fichier réellement présent dans le dépôt.",
             size=10, italic=True, color=GREY)
    rows = [
        ("1", "Présentation et résumé du projet"),
        ("2", "Contexte, problématique et objectifs"),
        ("3", "Méthode de travail et organisation des séances"),
        ("4", "Architecture réalisée"),
        ("5", "Modèle de données"),
        ("6", "Acteurs, rôles et cas d'utilisation"),
        ("7", "Processus métier, BPMN et cycles de vie"),
        ("8", "Parcours commentés en captures d'écran"),
        ("9", "Fonctions clés et règles de gestion"),
        ("10", "Tests, mesures et preuves"),
        ("11", "Sécurité, RGPD, accessibilité et limites assumées"),
        ("12", "Bilan, compétences acquises et suites possibles"),
        ("A", "Annexe A — Lancer l'application"),
        ("B", "Annexe B — Comptes de démonstration"),
        ("C", "Annexe C — Inventaire des livrables"),
        ("D", "Annexe D — Reproduire les preuves"),
        ("E", "Annexe E — Glossaire"),
    ]
    for n, titre in rows:
        p = doc.add_paragraph()
        p.paragraph_format.space_after = Pt(2)
        cdc.set_run(p.add_run(f"{n}. {titre}" if n.isdigit() else titre), size=11,
                   bold=n.isdigit(), color=ROYAL if n.isdigit() else GREY)


# ------------------------------------------------------------------- chapitre 1
def chapitre_1(doc):
    doc.add_page_break()
    add_heading_custom(doc, "1. Présentation et résumé du projet", 1)
    add_para(doc, "HireLink est une application web de suivi de candidatures (Applicant Tracking System) "
                  "conçue pour le marché marocain : elle met en relation une entreprise qui publie une "
                  "offre vérifiée et un candidat qui postule, puis elle suit le dossier de la réception "
                  "jusqu'à la décision motivée. Le projet a été mené en deux processus locaux — une API "
                  "Spring Boot sur le port 8081 et une interface HTML/CSS/JavaScript servie sur le port "
                  "5500 — sur une base MariaDB de vingt-cinq tables.")
    add_para(doc, "Ce qui distingue la réalisation est la discipline de vérification : une fonction "
                  "n'est décrite dans les documents que si elle est exécutée par le code, et une "
                  "information n'est affichée à un utilisateur que si elle provient d'une donnée mesurée "
                  "en base. Les promesses non tenues (visibilité « prioritaire », délai garanti, badge "
                  "gamifié sans fondement) ont été retirées du produit plutôt que masquées.")
    add_caption(doc, "Tableau 1.1 — Le projet en dix chiffres vérifiés dans le dépôt")
    add_table(doc, ["Grandeur", "Valeur", "Comment elle est obtenue"],
              [("Contrôleurs REST", "20", "inventaire du paquet controller"),
               ("Endpoints sous /api/v1", "151", "annotations @RequestMapping/@GetMapping/… "
                "relevées automatiquement"),
               ("Services métier", "51", "paquet service"),
               ("Repositories Spring Data", "24", "paquet repository"),
               ("Entités JPA", "24", "classes portant @Table"),
               ("Énumérations du domaine", "11 (+1 imbriquée)", "paquet model"),
               ("Tables réelles en base", "25", "SHOW TABLES sur le schéma hirelink"),
               ("Pages de l'interface", "27 (9 publiques, 18 applicatives)", "frontend et frontend/app"),
               ("Fichiers JavaScript / CSS", "58 / 21", "inventaire de frontend"),
               ("Données de démonstration", "130 comptes, 42 organisations, 117 offres (106 publiées), "
                "217 candidatures", "comptages SQL du 6 novembre 2026"),
               ("Exigences documentées", "71 fonctionnelles, 34 non fonctionnelles", "cahier des charges v5"),
               ("Règles de gestion", "13 portant une référence écrite dans le code, 11 référencées par "
                "le document", "chapitre 9"),
               ("Tests rejouables", "73 requêtes (dont 49 lecture seule), 101 assertions", "collections "
                "Postman et exécution newman"),
               ],
              col_widths=[4.6, 4.4, 8.0], size=9)
    add_para(doc, "Le présent rapport documente la version livrée le 6 novembre 2026. Il s'appuie sur "
                  "trois familles de figures : les douze diagrammes techniques (chapitres 4 à 7), les "
                  "vingt-trois captures d'écran de l'application en cours d'exécution (chapitre 8) et "
                  "les relevés de tests (chapitre 10).")


# ------------------------------------------------------------------- chapitre 2
def chapitre_2(doc):
    doc.add_page_break()
    add_heading_custom(doc, "2. Contexte, problématique et objectifs", 1)
    add_heading_custom(doc, "2.1 Le problème observé", 2)
    add_para(doc, "Dans les petites et moyennes entreprises marocaines, le recrutement se conduit encore "
                  "par boîtes aux lettres : l'annonce est publiée sur un ou deux sites, les candidatures "
                  "arrivent en pièces jointes, la sélection se fait dans un classeur, et l'état d'un "
                  "dossier n'est connu que de la personne qui l'a ouvert. Le candidat ne sait pas ce que "
                  "son dossier est devenu ; l'entreprise ne peut pas expliquer une décision prise trois "
                  "semaines plus tôt.")
    add_para(doc, "Les outils qui répondent à ce besoin sont presque tous étrangers et facturés par siège "
                  "dans une devise non locale, avec un engagement annuel. Une entreprise de moins de "
                  "cinquante salariés n'y a pas accès. Le second problème est la confiance : les petites "
                  "annonces non contrôlées restent un canal de fraude (demande de frais d'inscription, "
                  "fausse offre de télétravail, société sans existence légale vérifiable), et un candidat "
                  "n'a, dans les outils grand public, aucun moyen de distinguer une entreprise "
                  "immatriculée d'une coquille vide.")
    add_heading_custom(doc, "2.2 Problématique", 2)
    add_para(doc, "Comment une PME marocaine peut-elle conduire un recrutement complet — publication "
                  "vérifiée, réception des candidatures, pipeline, entretien, décision motivée et tracée — "
                  "sans licence étrangère, sans exposer le candidat à une annonce non contrôlée, et en "
                  "lui donnant une information honnête et vérifiable sur le traitement de son dossier ?",
             bold=True)
    add_heading_custom(doc, "2.3 Objectifs retenus et preuve attendue", 2)
    add_para(doc, "Un objectif n'a été retenu que s'il est vérifiable en exécutant l'application. La "
                  "colonne de droite donne le geste qui le prouve.")
    add_caption(doc, "Tableau 2.1 — Objectifs et preuve")
    add_table(doc, ["Objectif", "Preuve à l'exécution"],
              [("Publier une offre sans assistance", "Assistant en trois étapes puis publication ; moins "
                "de dix minutes, aucune correction manuelle en base."),
               ("Traiter une candidature de bout en bout", "Pipeline, changement d'étape, refus motivé : "
                "une transition et une décision par écran."),
               ("Historiser toute décision", "GET /applications/{id}/timeline après trois transitions : "
                "trois lignes, aucune modification possible."),
               ("Interdire la décision sans motif", "400 « Choisissez un motif de refus. »."),
               ("Isoler les organisations", "403 « Hors organisation (R02) » sur le dossier d'un autre "
                "espace, 200 sur le sien."),
               ("Donner au candidat un rythme, pas une promesse", "Médiane du premier recruteur-réponse "
                "sur 180 jours, minimum trois dossiers répondus, aucune étiquette négative."),
               ("Identifier l'entreprise avant de publier", "Format ICE quinze chiffres, rapprochement du "
                "nom, blocage de la publication tant que la vérification n'est pas passée."),
               ("Rendre les données personnelles exportables et effaçables", "GET /me/export, "
                "POST /account/deactivate, POST /account/erase."),
               ("Coût d'usage nul pour démarrer", "Formules ESSENTIEL et ACCES à 0 dirham ; une formule "
                "ne change que les crédits IA et le nombre de sièges."),
               ], col_widths=[6.0, 11.0], size=9)


# ------------------------------------------------------------------- chapitre 3
def chapitre_3(doc):
    doc.add_page_break()
    add_heading_custom(doc, "3. Méthode de travail et organisation des séances", 1)
    add_para(doc, "Le projet a été conduit par itérations courtes, chacune close par une vérification "
                  "dans l'application réelle plutôt que par une relecture de code. La règle de travail "
                  "tenue pendant toute la durée du projet : une correction n'est acceptée que si le "
                  "symptôme observé a d'abord été reproduit, et le contrôle est déplacé côté serveur "
                  "dès qu'il protège une donnée.")
    add_caption(doc, "Tableau 3.1 — Déroulé par séance")
    add_table(doc, ["Séance", "Travail mené", "Vérification"],
              [("1", "Audit des écrans existants et des promesses non tenues ; retrait des promesses de "
                "visibilité et des badges sans fondement.", "Relecture page par page, puis contrôle des "
                "formules réellement appliquées par le serveur."),
               ("2", "Architecture commentée, contrat front/back : inventaire des routes et des champs "
                "consommés.", "151 endpoints et 27 pages listés ; les fonctions mortes sont archivées "
                "plutôt que supprimées silencieusement."),
               ("3", "Rôles d'entreprise (propriétaire, recruteur, responsable de poste), cycle de vie "
                "complet des offres, modération administrateur.", "Tests manuels puis collection Postman "
                "complète."),
               ("4", "Crédits IA affichés, CAPTCHA produit par le serveur, badges sobres fondés sur des "
                "comptages.", "GET /ai/credits, POST /auth/captcha, comparaison avant/après en base."),
               ("5", "Nettoyage de la base de démonstration : tables et colonnes mortes supprimées, "
                "vocabulaires normalisés, routes mortes retirées.", "25 tables, plus aucune table morte ; "
                "les routes retirées répondent 404."),
               ("6", "Documentation : cahier des charges v5, douze diagrammes, vingt-trois captures, "
                "collections Postman et leur run.", "Le présent document et l'annexe C."),
               ], col_widths=[1.2, 8.4, 7.4], size=9)
    add_heading_custom(doc, "3.2 Trois décisions de méthode qui ont compté", 2)
    add_bullets(doc, [
        "Déplacer le contrôle côté serveur. Une règle tenue uniquement par le navigateur est "
        "contournable par un appel direct à l'API. Les listes fermées (fuseaux d'entretien, niveau "
        "d'expérience d'un profil) ont donc été écrites dans les services, avec un message qui rappelle "
        "le vocabulaire attendu.",
        "Ne pas promettre ce qui n'est pas mesuré. Le délai de réponse consenti par l'entreprise reste "
        "un suivi interne ; ce qui est affiché au public est un rythme calculé sur des dossiers réels, "
        "et rien ne s'affiche en dessous de trois dossiers répondus.",
        "Archiver plutôt que détruire. Le code et les colonnes retirés ont été déplacés dans docs/dead-code "
        "avec le motif du retrait, pour que la décision reste discutée en soutenance.",
    ])


# ------------------------------------------------------------------- chapitre 4
def chapitre_4(doc):
    doc.add_page_break()
    add_heading_custom(doc, "4. Architecture réalisée", 1)
    add_para(doc, "L'application tient en deux processus. Le premier est une API Spring Boot qui écoute "
                  "sur 8081 et sert cent cinquante et un endpoints sous /api/v1 ; le second sert les "
                  "fichiers statiques de l'interface sur 5500. La base MariaDB tourne sur 3306, dans "
                  "l'instance XAMPP locale. Il n'y a ni conteneur, ni serveur d'application dédié, ni "
                  "infrastructure externe obligatoire pour la démonstration.")
    add_heading_custom(doc, "4.1 Découpage en couches", 2)
    add_caption(doc, "Tableau 4.1 — Couches et responsabilités")
    add_table(doc, ["Couche", "Paquet", "Responsabilité", "Volume"],
              [("Contrôleurs", "ma.ensat.hirelink.controller", "routes, autorisations par annotation, "
                "mapping requête/réponse", "20 classes, 151 endpoints"),
               ("Services", "ma.ensat.hirelink.service", "règles de gestion, transactions, contrôles "
                "d'organisation", "51 classes"),
               ("Repositories", "ma.ensat.hirelink.repository", "accès JPA, requêtes nommées", "24 interfaces"),
               ("Modèle", "ma.ensat.hirelink.model", "entités @Table et énumérations du domaine",
                "24 entités, 11 enums"),
               ("Sécurité", "ma.ensat.hirelink.security", "filtre JWT, cookie de rafraîchissement, "
                "politique de mot de passe, anti-robot", "filtres et services dédiés"),
               ("Configuration", "ma.ensat.hirelink.config", "contrat d'erreur global, données de "
                "démonstration, tâches planifiées", "DataSeeder, GlobalExceptionHandler, moniteurs"),
               ("Interface", "frontend/", "pages statiques, appels REST, rendu et états",
                "27 pages, 58 JS, 21 CSS"),
               ], col_widths=[2.6, 4.2, 6.6, 3.6], size=9)
    figure(doc, "07_paquets.png", "Figure 4.1 — Diagramme de paquets : l'interface n'appelle que les "
                                  "contrôleurs, les contrôleurs délèguent aux services, les services "
                                  "aux repositories, et les seules sorties réseau sont l'e-mail, "
                                  "l'IA et les logos.")
    figure(doc, "08_deploiement.png", "Figure 4.2 — Déploiement local réellement utilisé : navigateur "
                                      "sur 5500, JVM sur 8081, MariaDB sur 3306, fichiers sur disque.")
    figure(doc, "10_stack_technologique.png", "Figure 4.3 — Pile technique, des bibliothèques réellement "
                                              "déclarées dans le POM Maven.")
    add_heading_custom(doc, "4.2 Choix techniques et motif", 2)
    add_caption(doc, "Tableau 4.2 — Ce qui a été choisi et pourquoi")
    add_table(doc, ["Choix", "Version", "Motif réel"],
              [("Spring Boot", "3.3.13", "un seul exécutable, sécurité et JPA déjà intégrés, "
                "annotations d'autorisation lisibles sur la méthode."),
               ("Java", "21 (cible), 23 (exécuté)", "records pour les DTO, text blocks, switch sur les "
                "formes — le code des services en profite directement."),
               ("Spring Data JPA / Hibernate", "6.x", "le schéma suit le modèle : vingt-quatre entités, "
                "vingt-cinq tables, ddl-auto=update."),
               ("MariaDB", "10.4.32", "instance locale déjà disponible sous XAMPP, aucune installation "
                "supérieure pour le professeur."),
               ("Frontend statique", "HTML/CSS/JavaScript", "aucune étape de build : le code rendu est "
                "le code du dépôt, ce qui rend la démonstration reproductible."),
               ("JWT court + cookie HttpOnly", "maison", "le jeton d'accès reste en mémoire du "
                "navigateur, le rafraîchissement dans un cookie inaccessible au script."),
               ("CAPTCHA et anti-robot", "maison", "l'image est produite par le serveur ; le comptage "
                "des échecs est en mémoire, ce qui est écrit comme limite au chapitre 11."),
               ("Aide automatique", "fournisseur optionnel + repli local", "sans clé d'API, un fournisseur "
                "local prend le relais : la démonstration fonctionne hors ligne."),
               ("E-mail", "optionnel", "les envois sont enregistrés dans sent_emails avec leur statut réel, "
                "affiché tel quel ; aucune promesse d'envoi non tenue."),
               ], col_widths=[3.2, 3.0, 10.8], size=9)
    add_heading_custom(doc, "4.3 Contrat de communication front / back", 2)
    add_para(doc, "Une réponse de successful renvoie le corps attendu par la page ; un échec renvoie "
                  "toujours un statut HTTP explicite et un corps JSON portant uniquement le champ message, "
                  "en français. Le front affiche ce message, jamais un texte qu'il inventerait. Cette "
                  "règle, notée RG-11 dans le cahier des charges, est vérifiée par les tests : sur les "
                  "quarante-neuf requêtes de la suite lecture seule, le statut attendu a été obtenu "
                  "quarante-neuf fois sur quarante-neuf.")
    add_caption(doc, "Tableau 4.3 — Exemples de messages réellement renvoyés, recopiés depuis le code")
    add_table(doc, ["Statut", "Message serveur", "Règle appliquée"],
              [("409", "Candidature déjà existante (RM01)", "une candidature par couple candidat/offre"),
               ("403", "Aucune organisation (R02)", "un recruteur hors espace ne voit pas de pipeline"),
               ("403", "Hors organisation (R02)", "isolation entre entreprises"),
               ("403", "Dossier non assigné (R14)", "le responsable de poste ne voit que ses dossiers"),
               ("403", "Seul le créateur de l'espace gère l'équipe (R13)", "droits du propriétaire"),
               ("403", "Accès historique refusé (R03)", "frise réservée aux ayants droit"),
               ("400", "Choisissez un motif de refus.", "décision toujours motivée"),
               ("400", "Fuseau horaire non pris en charge.", "liste fermée des fuseaux"),
               ("400", "Le créneau d'entretien est dans le passé.", "cohérence temporelle"),
               ("400", "Indiquez le lien de visioconférence.", "champ obligatoire selon le type"),
               ("400", "Niveau d'expérience hors liste : Stagiaire, Junior, Confirmé, Senior, Lead, Manager",
                "vocabulaire contrôlé côté serveur"),
               ], col_widths=[1.5, 9.0, 6.5], size=9)


# ------------------------------------------------------------------- chapitre 5
def chapitre_5(doc):
    doc.add_page_break()
    add_heading_custom(doc, "5. Modèle de données", 1)
    add_para(doc, "Le schéma hirelink contient vingt-cinq tables : vingt-quatre correspondent aux entités "
                  "JPA et sont créées par Hibernate au premier démarrage, la vingt-cinquième "
                  "(job_offer_tags) est la table de jointure générée par l'association "
                  "plusieurs-à-plusieurs entre offres et étiquettes. Le fichier database/schema.sql ne "
                  "décrivant que quatre tables n'est plus la référence : la divergence est écrite ici "
                  "plutôt que masquée, et le chapitre 11 la liste comme limite de maintenance.")
    figure(doc, "02_classes.png", "Figure 5.1 — Diagramme de classes des vingt-quatre entités, avec leurs "
                                  "champs réels et les associations JPA du dépôt.", largeur=16.8)
    add_heading_custom(doc, "5.1 Les tables et ce qu'elles portent", 2)
    add_caption(doc, "Tableau 5.1 — Vingt-cinq tables")
    add_table(doc, ["Table", "Contenu"],
              [("users", "comptes, rôles, profil candidat, MFA, formule"),
               ("organizations", "entreprise, ICE, logo, présentation"),
               ("memberships", "appartenance à une organisation et rôle d'équipe"),
               ("job_offers", "offre, cycle de vie, quarantaine, échéance de réponse"),
               ("applications", "candidature, statut, entretien, CV, échéances"),
               ("application_status_history", "frise : chaque changement d'étape, append-only"),
               ("scorecard_criteria", "critères de décision d'une offre"),
               ("scorecard_entries", "note et commentaire par évaluateur"),
               ("refusal_reason_catalog", "catalogue administrable des motifs de refus"),
               ("offer_reports", "signalements des candidats, seuil de suspension"),
               ("tags", "étiquettes d'offre"),
               ("job_offer_tags", "table de liaison offres ↔ étiquettes"),
               ("organization_events", "fil d'événements de l'équipe"),
               ("organization_notes", "notes internes"),
               ("organization_invitations", "invitations en attente"),
               ("billing_invoices", "factures et avoirs"),
               ("commercial_plans", "formules : crédits IA et sièges uniquement"),
               ("notifications", "notifications internes avec lien profond"),
               ("sent_emails", "e-mails réellement envoyés et leur statut"),
               ("email_otps", "codes de connexion et de vérification"),
               ("password_reset_tokens", "jetons de réinitialisation à usage unique"),
               ("user_sessions", "sessions, révocations, appareils"),
               ("webauthn_credentials", "passkeys"),
               ("audit_logs", "journal des actions sensibles"),
               ("ai_usage_logs", "consommation IA par fonction et fournisseur"),
               ], col_widths=[5.0, 12.0], size=9)
    add_heading_custom(doc, "5.2 Vocabulaires contrôlés par le serveur", 2)
    add_caption(doc, "Tableau 5.2 — Listes fermées et endroit où elles sont imposées")
    add_table(doc, ["Vocabulaire", "Valeurs admises", "Contrôle serveur"],
              [("Niveau d'expérience d'un profil", "Stagiaire, Junior, Confirmé, Senior, Lead, Manager",
                "MeController refuse tout autre libellé (RG-09)"),
               ("Niveau demandé par une offre", "NONE, JUNIOR, INTERMEDIATE, SENIOR, EXPERT",
                "énumération ExperienceLevel, colonne typée enum en base"),
               ("Contrat", "CDI, CDD, Stage, PFE, PFA, Stage d'été, Alternance, Freelance, Temps partiel",
                "énumération ContractType"),
               ("Mode de travail", "ONSITE, HYBRID, REMOTE", "énumération WorkMode"),
               ("Fuseau d'un entretien", "Africa/Casablanca, Europe/Paris, Europe/London, UTC",
                "InterviewService.schedule, liste ZONES (RG-05)"),
               ("Type d'entretien", "ONSITE, VISIO", "adresse obligatoire dans le premier cas, lien dans "
                "le second (RG-04)"),
               ("Nature d'entretien", "RH, TECHNIQUE, MANAGER, COMPÉTENCES, PROJET, CONTRE-RÉFÉRENCE",
                "liste KIND_FR du service"),
               ("Statut de candidature", "SUBMITTED, SCREENING, SHORTLISTED, INTERVIEW, REJECTED, WITHDRAWN "
                "(+ OFFER, HIRED historiques)", "machine d'états R01, statuts terminaux RG-02"),
               ("Motif de refus", "catalogue administrable", "table refusal_reason_catalog, règle R04"),
               ("Rôle global", "CANDIDAT, RECRUTEUR, ADMINISTRATEUR", "annotations d'autorisation"),
               ("Rôle d'équipe", "OWNER, RECRUTEUR, HIRING_MANAGER", "droits distincts, règles R13 et R14"),
               ], col_widths=[4.2, 6.8, 6.0], size=9)


# ------------------------------------------------------------------- chapitre 6
def chapitre_6(doc):
    doc.add_page_break()
    add_heading_custom(doc, "6. Acteurs, rôles et cas d'utilisation", 1)
    add_para(doc, "Trois rôles globaux et trois rôles d'équipe. Un visiteur non connecté utilise l'espace "
                  "public ; un candidat dispose de son espace ; un recruteur agit dans l'espace de son "
                  "entreprise selon son rôle d'équipe ; l'administrateur modère la plateforme. Les droits "
                  "décrits ici sont ceux appliqués par le serveur, pas ceux suggérés par l'interface.")
    add_caption(doc, "Tableau 6.1 — Rôles et ce qu'ils peuvent réellement faire")
    add_table(doc, ["Rôle", "Comment il est écrit", "Droits appliqués côté serveur"],
              [("Visiteur", "aucune authentification", "offres publiées, vitrines d'entreprises, "
                "recherche, page de confiance"),
               ("Candidat", "Role.CANDIDAT", "profil et CV, candidature, suivi de ses dossiers, "
                "réponse aux invitations, notifications, export et effacement"),
               ("Recruteur membre", "Role.RECRUTEUR + OrgRole.RECRUTEUR", "offres et pipeline de sa seule "
                "organisation, entretiens, refus motivés, notes et fil d'équipe"),
               ("Créateur de l'espace", "OrgRole.OWNER", "les droits du membre, plus équipe, formule, "
                "transfert de propriété et logo"),
               ("Responsable de poste", "OrgRole.HIRING_MANAGER", "seulement les dossiers qui lui sont "
                "attribués ; ne crée pas d'offre ; évalue sur la grille"),
               ("Administrateur", "Role.ADMINISTRATEUR", "modération des offres signalées, comptes, "
                "organisations et vérification, plans et factures, statistiques, e-mails, audit"),
               ], col_widths=[3.2, 4.6, 9.2], size=9)
    figure(doc, "01a_cas_utilisation_candidat.png", "Figure 6.1 — Cas d'utilisation du candidat : espace "
                                                    "public et espace privé, avec les extensions d'analyse "
                                                    "de CV et de suggestion de compétences.")
    figure(doc, "01b_cas_utilisation_recruteur.png", "Figure 6.2 — Cas d'utilisation du recruteur et du "
                                                     "responsable de poste dans l'espace entreprise.")
    figure(doc, "01c_cas_utilisation_admin.png", "Figure 6.3 — Cas d'utilisation de l'administrateur : "
                                                 "modération, comptes, entreprises, facturation, audit.")
    add_caption(doc, "Tableau 6.2 — Exemples de refus serveur observés en test")
    add_table(doc, ["Action tentée hors droit", "Réponse du serveur"],
              [("POST /applications avec un jeton recruteur", "403 « Réservé aux candidats »"),
               ("PATCH /applications/{id}/status sur une offre d'une autre organisation",
                "403 « Hors organisation (R02) »"),
               ("GET /applications/by-offer/{id} pour un responsable de poste non assigné",
                "403 « Dossier non assigné (R14) »"),
               ("GET /admin/stats avec un jeton candidat", "403"),
               ("POST /organizations/mine/invite par un membre non créateur",
                "403 « Seul le créateur de l'espace gère l'équipe (R13) »"),
               ("PATCH /applications/{id}/status par le recruteur pour retirer",
                "403 « Seul le candidat peut retirer (R11) »"),
               ], col_widths=[9.2, 7.8], size=9)


# ------------------------------------------------------------------- chapitre 7
def chapitre_7(doc):
    doc.add_page_break()
    add_heading_custom(doc, "7. Processus métier, BPMN et cycles de vie", 1)
    add_heading_custom(doc, "7.1 Le cycle de vie d'une candidature", 2)
    add_para(doc, "Le statut d'une candidature est porté par une machine d'états écrite une seule fois "
                  "dans le service. Les transitions autorisées sont Reçue → Présélection ou Refusée, "
                  "Présélection → Shortlist ou Refusée, Shortlist → Entretien ou Refusée, Entretien → "
                  "Refusée ; le candidat peut retirer son dossier tant qu'il n'est pas à une étape "
                  "terminale. Les deux statuts historiques « offre » et « embauché » restent dans "
                  "l'énumération pour ne pas casser les données, mais aucune transition nouvelle ne mène "
                  "à eux : c'est le pendant technique de la limite assumée « HireLink ne remplace pas la "
                  "décision d'embauche ».")
    figure(doc, "06_activite_pipeline.png", "Figure 7.1 — Diagramme d'activité : les étapes réelles du "
                                            "pipeline et les points de sortie Refusée et Retirée.")
    add_heading_custom(doc, "7.2 Le processus métier en BPMN", 2)
    add_para(doc, "BPMN (Business Process Model and Notation) est un langage graphique normalisé pour "
                  "décrire un processus métier : des activités arrondies, des portes losangées pour les "
                  "choix, des événements en cercle pour le début et la fin, et des couloirs qui attribuent "
                  "chaque activité à un acteur. Il diffère en cela d'un diagramme d'activité UML : il "
                  "parle au métier et à un moteur d'ordonnancement, pas au code ; un fichier .bpmn est un "
                  "document XML échangeable, ouvrable dans Camunda Modeler, Bizagi ou draw.io, et "
                  "exécutable par un moteur BPMN.")
    add_para(doc, "Le processus de HireLink est représenté en trois couloirs — candidat, recruteur, "
                  "plateforme — avec la boucle de replanification de l'entretien et l'annulation motivée. "
                  "Le dépôt contient deux fichiers complémentaires : le schéma "
                  "docs/diagrammes/v5/09_bpmn_processus_recrutement.png et l'export BPMN 2.0 "
                  "docs/diagrammes/v5/processus_recrutement_hirelink.bpmn, généré par "
                  "docs/diagrammes/_gen_v5_bpmn_xml.py et validé comme XML avant écriture.")
    figure(doc, "09_bpmn_processus_recrutement.png", "Figure 7.2 — Processus métier complet en BPMN, trois "
                                                     "couloirs, du dépôt de candidature à la décision "
                                                     "motivée.")
    add_caption(doc, "Tableau 7.1 — Comment réutiliser le fichier BPMN")
    add_table(doc, ["Outil", "Geste", "Ce que l'on obtient"],
              [("Camunda Modeler", "File → Open → processus_recrutement_hirelink.bpmn",
                "le diagramme éditable, avec la possibilité de le rendre exécutable en branchant des "
                "services"),
               ("Bizagi / draw.io", "Importer un fichier BPMN 2.0", "mêmes couloirs et flux, pour une "
                "reprise en présentation"),
               ("PlantUML", "ouvrir 09_bpmn_processus_recrutement.puml", "la source du rendu, régénérable "
                "par la commande donnée annexe D"),
               ("Moteur BPMN (Camunda, Flowable)", "déployer la définition", "l'ordonnancement réel ; non "
                "tenté dans ce projet, écrit comme suite possible au chapitre 12"),
               ], col_widths=[3.4, 6.4, 7.2], size=9)
    add_heading_custom(doc, "7.3 Trois séquences techniques", 2)
    figure(doc, "05_sequence_authentification.png", "Figure 7.3 — Authentification : mot de passe, CAPTCHA "
                                                    "après deux échecs, double authentification, Google, "
                                                    "sessions.", largeur=16.0)
    figure(doc, "03_sequence_candidature.png", "Figure 7.4 — POST /applications : filtre JWT, anti-doublon, "
                                               "calcul déterministe du score, notification.", largeur=16.0)
    figure(doc, "04_sequence_entretien.png", "Figure 7.5 — Planification d'un entretien, réponse du "
                                             "candidat, rappel de la veille, export .ics.", largeur=16.0)


# ------------------------------------------------------------------- chapitre 8
def chapitre_8(doc):
    doc.add_page_break()
    add_heading_custom(doc, "8. Parcours commentés en captures d'écran", 1)
    add_para(doc, "Vingt-trois captures prises le 6 novembre 2026 dans Chromium en 1440×900, thème clair, "
                  "sur les données de démonstration d'origine. Elles ont été réalisées en consultation "
                  "seule : aucune écriture en base pendant la série, ce qui garantit que ce que l'on voit "
                  "est l'état de référence.")
    add_heading_custom(doc, "8.1 Espace public", 2)
    capture(doc, "01-public-accueil.png", "Figure 8.1 — Accueil public : grille des offres publiées avec "
                                          "logo d'entreprise, badge de contrat, ville, fourchette de "
                                          "salaire et ancienneté.")
    capture(doc, "03-public-entreprise.png", "Figure 8.2 — Fiche publique d'une entreprise : badge "
                                             "« Identité vérifiée », statistiques comptées, compétences "
                                             "recherchées et offres rattachées.")
    capture(doc, "04-public-securite.png", "Figure 8.3 — Page publique de confiance : ce que la plateforme "
                                           "garde, ce qu'elle ne promet pas.")
    capture(doc, "02-public-connexion.png", "Figure 8.4 — Connexion : mot de passe, code envoyé par e-mail, "
                                            "passkey et Google, à l'état neutre.")
    add_heading_custom(doc, "8.2 Espace candidat", 2)
    capture(doc, "10-candidat-tableau-de-bord.png", "Figure 8.5 — Tableau de bord candidat : synthèse, "
                                                    "suivi des dossiers et prochaines étapes.")
    capture(doc, "11-candidat-offres.png", "Figure 8.6 — Recherche d'offres : panneau de filtres et nombre "
                                           "de lignes réellement affiché.")
    capture(doc, "12-candidat-detail-offre.png", "Figure 8.7 — Détail d'une offre avec décomposition du "
                                                 "score de compatibilité (71 %), sans action automatique.")
    capture(doc, "13-candidat-mes-candidatures.png", "Figure 8.8 — Mes candidatures : statuts et frise "
                                                     "d'avancement du dossier ouvert.")
    capture(doc, "14-candidat-profil.png", "Figure 8.9 — Profil et CV, avec jauges de complétion.")
    capture(doc, "15-candidat-notifications.png", "Figure 8.10 — Centre de notifications : chaque alerte "
                                                   "mène à la page et au dossier concerné.")
    add_heading_custom(doc, "8.3 Espace entreprise", 2)
    capture(doc, "20-recruteur-tableau-de-bord.png", "Figure 8.11 — Tableau de bord recruteur : ce qui est "
                                                     "à faire aujourd'hui et prochains événements.")
    capture(doc, "21-recruteur-pipeline.png", "Figure 8.12 — Pipeline : compteurs d'actifs, de retards, de "
                                              "scores élevés, d'entretiens et de conversion, puis les "
                                              "quatre colonnes d'étapes.")
    capture(doc, "22-recruteur-offres.png", "Figure 8.13 — Mes offres : statuts et compteurs de "
                                            "candidatures par offre.")
    capture(doc, "23-recruteur-candidats.png", "Figure 8.14 — Table des dossiers de l'organisation, tris "
                                               "et filtres.")
    capture(doc, "24-recruteur-entretiens.png", "Figure 8.15 — Planificateur d'entretiens : à venir, à "
                                                "replanifier, passés, regroupés par jour.")
    capture(doc, "25-recruteur-agenda.png", "Figure 8.16 — Agenda et tableau de post-it personnels, notes "
                                            "persistées.")
    capture(doc, "26-recruteur-equipe.png", "Figure 8.17 — Entreprise et équipe : fiche de l'organisation, "
                                            "accès à la vitrine publique, rôles des membres.")
    capture(doc, "27-recruteur-assistant-ia.png", "Figure 8.18 — Assistant IA : solde de crédits, formule "
                                                  "en cours et brouillons proposés avant validation "
                                                  "humaine.")
    add_heading_custom(doc, "8.4 Administration et sécurité du compte", 2)
    capture(doc, "30-admin-tableau-de-bord.png", "Figure 8.19 — Vue « à traiter » de l'administrateur : "
                                                 "offres à modérer, entreprises à vérifier, entonnoir et "
                                                 "indicateurs globaux.")
    capture(doc, "31-admin-entreprises.png", "Figure 8.20 — Gestion des organisations : ICE, formule, "
                                             "statut de vérification, équipe et volume d'offres.")
    capture(doc, "32-admin-moderation.png", "Figure 8.21 — Modération : signalements, motif de mise en "
                                            "quarantaine, rétablissement ou suspension.")
    capture(doc, "33-admin-audit.png", "Figure 8.22 — Journal d'audit horodaté, filtrable par type "
                                       "d'action et exportable.")
    capture(doc, "40-securite-compte.png", "Figure 8.23 — Sécurité du compte : politique de mot de passe, "
                                           "double authentification, passkeys et sessions actives avec "
                                           "déconnexion.")


# ------------------------------------------------------------------- chapitre 9
def chapitre_9(doc):
    doc.add_page_break()
    add_heading_custom(doc, "9. Fonctions clés et règles de gestion", 1)
    add_para(doc, "Le cahier des charges v5 décrit soixante-onze exigences fonctionnelles réparties en "
                  "huit modules. Le tableau ci-dessous en donne la substance par module ; les règles "
                  "ensuite sont écrites dans les services, pas seulement dans l'interface, ce qui signifie "
                  "qu'un appel direct à l'API rencontre les mêmes contrôles qu'une navigation.")
    add_caption(doc, "Tableau 9.1 — Huit modules, soixante-onze exigences")
    add_table(doc, ["Module", "Exigences", "Fonctionnement central"],
              [("Comptes, connexion et session", "EF-01 à EF-11",
                "inscription par code reçu, mot de passe, code par e-mail, Google, passkey, double "
                "authentification, CAPTCHA après deux échecs, réinitialisation à usage unique, jeton "
                "renouvelé, sessions révocables, désinscription à deux niveaux"),
               ("Profil candidat et CV", "EF-12 à EF-20",
                "profil et jauges de complétion, niveaux d'expérience dans une liste fermée, compétences "
                "suggérées, import et analyse de CV puis validation section par section, conservation de "
                "l'original, CV rattaché à une candidature, export intégral"),
               ("Découverte des offres (public)", "EF-21 à EF-27",
                "offres publiées sans compte, filtres avec compteur réel, recherche en texte libre, score "
                "de compatibilité décomposé, vitrine d'entreprise, annuaire, signalement d'offre"),
               ("Candidature et pipeline", "EF-28 à EF-38",
                "candidature en une action, suivi, retrait, frise, table des dossiers d'une offre, "
                "changement d'étape, refus motivé, brouillon de motif, shortlist et grille de critères, "
                "attribution d'un responsable de poste, rythme de réponse mesuré"),
               ("Entretiens", "EF-39 à EF-47",
                "planification sur site ou en visio, nature et fuseau dans des listes fermées, report, "
                "annulation motivée, réponse du candidat, export .ics, rappel de la veille une seule fois, "
                "agenda d'équipe"),
               ("Organisation, équipe et collaboration", "EF-48 à EF-55",
                "création de l'espace, vérification de l'ICE, invitations, promotion et transfert, "
                "suspension et rétablissement, notes et fil d'événements, cycle de vie des offres, "
                "prolongation d'une échéance"),
               ("Administration, modération et facturation", "EF-56 à EF-64",
                "modération des offres signalées, comptes, organisations et logos, plans et factures, "
                "catalogue des motifs, statistiques et consommation IA, e-mails réellement envoyés, "
                "recherche transversale, journal d'audit exportable"),
               ("Notifications et assistant IA", "EF-65 à EF-71",
                "notification interne à chaque événement, redirection vers l'élément concerné, réécriture "
                "de profil, préparation d'entretien, explication d'un score, relecture de texte, solde de "
                "crédits"),
               ], col_widths=[3.8, 2.6, 10.6], size=9)
    add_heading_custom(doc, "9.1 Règles portant une référence écrite dans le code", 2)
    add_caption(doc, "Tableau 9.2 — Règles R01 à R16 et RM01")
    add_table(doc, ["Réf", "Règle", "Où elle est codée"],
              [("R01", "machine d'états du pipeline, transitions déclarées une fois",
                "ApplicationService"),
               ("R02", "isolation par organisation", "OrganizationService"),
               ("R03", "frise réservée aux ayants droit", "CatalogPrivacyController"),
               ("R04", "refus avec motif du catalogue", "ApplicationService.applyRefusalCatalog"),
               ("R05", "checklist à trois points, pré-cochée seulement si la donnée existe",
                "frontend/js/recruiter-pipeline.js"),
               ("R08", "score calculé localement, sans API tierce ni crédit IA", "SkillMatchingService"),
               ("R11", "retrait réservé au candidat", "ApplicationService.advance"),
               ("R12", "frise append-only", "model/ApplicationStatusHistory"),
               ("R13", "seul le créateur gère l'équipe", "OrganizationService"),
               ("R14", "le responsable de poste ne voit que ses dossiers assignés",
                "OrganizationService.assertCanAccessApplication"),
               ("R15", "prompts sans donnée d'identité", "AiAssistController"),
               ("R16", "crédit IA débité seulement après succès", "AiCreditService"),
               ("RM01", "une candidature par couple candidat / offre", "ApplicationService.apply"),
               ], col_widths=[1.1, 10.5, 5.4], size=9)
    add_para(doc, "Les références R06, R07, R09 et R10 n'existent pas dans le code : elles ne sont donc "
                  "pas revendiquées. Les règles réellement appliquées mais non nommées dans le dépôt "
                  "portent une référence RG-nn, explicitement présentée comme une référence du document "
                  "(RG-01 propriétaire OWNER, RG-02 statuts terminaux, RG-03 à RG-06 entretiens, RG-07 "
                  "report qui remet la réponse à zéro, RG-08 rappel unique, RG-09 liste des niveaux, "
                  "RG-10 seuil de signalement, RG-11 contrat d'erreur).",
             size=10, italic=True, color=GREY)
    add_heading_custom(doc, "9.2 Ce qui est volontairement hors du produit", 2)
    add_caption(doc, "Tableau 9.3 — Hors périmètre, avec le motif")
    add_table(doc, ["Élément", "Statut", "Vérification dans le dépôt"],
              [("Paiement en ligne (carte, CMI, Stripe)", "non implémenté",
                "aucune dépendance de passerelle ; souscrire écrit une ligne de facture, sans encaissement"),
               ("Vérification officielle auprès de la DGI ou de l'OMPIC", "non implémenté",
                "contrôle du format ICE et rapprochement du nom ; décision documentaire laissée à "
                "l'administrateur"),
               ("Application mobile native", "non implémenté", "interface responsive uniquement"),
               ("Webhooks sortants et API publique pour tiers", "non implémenté",
                "aucune route dédiée, aucune signature sortante"),
               ("Multi-langue (arabe, anglais)", "non implémenté", "libellés et messages d'erreur en français"),
               ("Notifications SMS, WhatsApp ou push", "non implémenté",
                "canaux disponibles : notifications internes et flux SSE"),
               ("Permissions sur mesure par utilisateur", "non implémenté",
                "trois rôles d'équipe fixes"),
               ("Migrations versionnées", "non implémenté", "ddl-auto=update ; aucun Flyway ni Liquibase"),
               ("Référence OpenAPI tenue", "coupée par défaut", "springdoc présent mais désactivé ; la "
                "référence reste docs/API.md"),
               ("Conteneurs et déploiement automatisé", "non traité", "aucun Dockerfile dans le dépôt"),
               ("Test de charge et test d'intrusion", "non réalisé", "écrit comme limite dans docs/SECURITE.md"),
               ("Étude statistique de biais du score", "non menée",
                "le score est déterministe et explicable, pas audité sociologiquement"),
               ], col_widths=[4.6, 2.6, 9.8], size=9)


# ------------------------------------------------------------------- chapitre 10
def chapitre_10(doc):
    doc.add_page_break()
    add_heading_custom(doc, "10. Tests, mesures et preuves", 1)
    add_heading_custom(doc, "10.1 Stratégie", 2)
    add_para(doc, "Trois niveaux. D'abord la vérification fonctionnelle dans l'interface, page par page, "
                  "au moment de chaque lot de correction. Ensuite deux collections Postman, produites par "
                  "un générateur pour ne jamais diverger du code : une collection complète de "
                  "soixante-treize requêtes réparties en quatorze dossiers, et une collection lecture seule "
                  "de quarante-neuf requêtes qui ne peut pas abîmer la base de démonstration. Enfin "
                  "l'exécution en ligne de commande avec newman, dont le résultat est archivé.")
    add_caption(doc, "Tableau 10.1 — Les deux collections")
    add_table(doc, ["Collection", "Contenu", "Usage"],
              [("HireLink_v5_api", "73 requêtes, 14 dossiers, chemins critiques, erreurs attendues, "
                "isolation entre organisations", "démonstration complète, y compris écritures"),
               ("HireLink_v5_lecture_seule", "49 requêtes GET et les trois connexions qui capturent les "
                "jetons", "rejouable avant la soutenance sans salir la base"),
               ], col_widths=[4.2, 8.4, 4.4], size=9)
    add_heading_custom(doc, "10.2 Résultat mesuré", 2)
    add_para(doc, "Exécution du 6 novembre 2026, API locale démarrée, données de démonstration d'origine. "
                  "Les chiffres ci-dessous sont recopiés de docs/postman/resultat-lecture-seule.md, établi à "
                  "partir de la sortie de newman ; le détail requête par requête est dans "
                  "docs/postman/resultat-lecture-seule.json.")
    add_caption(doc, "Tableau 10.2 — Run de la suite lecture seule")
    add_table(doc, ["Indicateur", "Valeur"],
              [("Requêtes exécutées", "49, 0 échec"),
               ("Assertions exécutées", "101, 0 échec"),
               ("Statut obtenu = statut attendu", "49 sur 49"),
               ("Temps moyen de réponse", "193 ms"),
               ("Temps minimal / maximal", "7 ms / 1 733 ms"),
               ("Durée cumulée", "environ 9,4 s"),
               ("Écritures en base", "aucune"),
               ], col_widths=[6.4, 10.6], size=9)
    add_heading_custom(doc, "10.3 Ce que chaque contrôle du run prouve", 2)
    add_caption(doc, "Tableau 10.3 — Contrôles significatifs")
    add_table(doc, ["Contrôle", "Attendu", "Ce qu'il démontre"],
              [("GET /auth/me et GET /ai/status", "404", "les deux routes mortes ont été retirées du "
                "code, pas masquées côté navigateur"),
               ("GET /skills/suggest sans jeton puis avec jeton", "401 puis 200", "la route est bien "
                "protégée par l'authentification et non par le front"),
               ("GET /applications/11/interview.ics", "404", "aucun fichier calendrier inventé quand la "
                "base ne contient pas d'entretien"),
               ("GET /applications/by-offer/15 par un recruteur d'une autre organisation", "403",
                "isolation R02 appliquée au niveau du service"),
               ("GET /applications/by-offer/1 par le recruteur propriétaire", "200",
                "la même requête réussit dans son organisation : le 403 ci-dessus n'est pas un réglage "
                "global"),
               ("GET /admin/stats avec un jeton candidat", "403", "l'autorisation par rôle tient sur la "
                "route, hors interface"),
               ("En-têtes de sécurité sur chaque réponse", "présents",
                "Content-Security-Policy et X-Content-Type-Options servis par le filtre"),
               ("Latence", "1 733 ms au pire", "le pire cas reste sous la seconde et demie ; la moyenne "
                "mesurée est de 193 ms"),
               ], col_widths=[6.4, 2.4, 8.2], size=9)
    add_heading_custom(doc, "10.4 Trois incidents réellement rencontrés, et leur correction", 2)
    add_bullets(doc, [
        "Trois assertions de la suite lecture seule échouaient au premier run. En cherchant la cause : "
        "le générateur Postman reconstruisait l'URL sans tableau de paramètres, ce qui faisait disparaître "
        "le point d'interrogation et sa chaîne de requête ; une route d'aide à la saisie avait été écrite "
        "sans jeton alors qu'elle est authentifiée ; et une candidature de démonstration sans entretien "
        "planifié ne pouvait pas produire de fichier .ics. Le générateur a été corrigé — pas les "
        "assertions affaiblies — et le second run passe entièrement.",
        "Le compte de l'organisation 26 portait une valeur illisible dans logo_url, qui cassait la vitrine "
        "publique. La colonne est passée à NULL et l'interface affiche désormais les initiales, ce qui "
        "était le comportement attendu quand aucun logo n'existe.",
        "Trois lignes de profil utilisaient un ancien vocabulaire d'expérience. Elles ont été normalisées "
        "sur la liste du formulaire, et la liste est désormais refusée par le serveur si un libellé en "
        "sort — la correction ne tient plus seulement dans le navigateur.",
    ])


# ------------------------------------------------------------------- chapitre 11
def chapitre_11(doc):
    doc.add_page_break()
    add_heading_custom(doc, "11. Sécurité, RGPD, accessibilité et limites assumées", 1)
    add_heading_custom(doc, "11.1 Sécurité et protection des données", 2)
    add_caption(doc, "Tableau 11.1 — Moyens en place et preuve observée")
    add_table(doc, ["Moyen", "Détail réel", "Preuve"],
              [("Jetons", "jeton d'accès court en mémoire, cookie de rafraîchissement HttpOnly ; double "
                "expiration d'une session (inactivité et durée absolue)", "pages Sécurité et sessions, "
                "test de reprise après expiration"),
               ("Autorisations", "@PreAuthorize par rôle sur les routes, contrôle d'organisation dans les "
                "services", "403 mesurés sur /admin/stats, /applications/by-offer, invitations"),
               ("Mots de passe", "politique appliquée au serveur, hash bcrypt, aucune valeur en clair ni en "
                "log", "code du service et refus serveur"),
               ("Anti-robot", "CAPTCHA produit par le serveur après deux échecs, limitation par IP et "
                "compte", "épreuve affichée au troisième essai, réponse POST /auth/captcha"),
               ("Journaux", "audit horodaté des actions sensibles, avec IP et agent ; e-mails réellement "
                "envoyés avec leur statut", "GET /admin/audit et /admin/emails"),
               ("Secrets", "clés d'API dans application-local.properties hors du dépôt", ".gitignore"),
               ("RGPD", "export complet en un appel, désactivation réversible ou effacement définitif, "
                "purge périodique des jetons et sessions morts", "GET /me/export, POST /account/deactivate, "
                "POST /account/erase"),
               ("Aide automatique", "prompts sans donnée d'identité, aucun crédit débité sur échec",
                "règles R15 et R16"),
               ], col_widths=[2.8, 8.4, 5.8], size=9)
    add_heading_custom(doc, "11.2 Accessibilité et ergonomie", 2)
    add_bullets(doc, [
        "Navigation clavier : les modales piègent le focus, et le focus revient sur l'élément qui les a "
        "ouvertes ; les voiles portent aria-modal et les intitulés sont explicites.",
        "Un compteur de filtre annonce le nombre de lignes réellement affichées, plus le nombre de lignes "
        "renvoyées par le serveur avant filtrage local.",
        "Les dates sont calculées dans le fuseau local et non en UTC : « aujourd'hui » désigne la même "
        "journée pour l'utilisateur et pour le serveur.",
        "Les états à données zéro sont dessinés, sans tableau vide ni zéro trompeur.",
        "Après une session expirée, la reconnexion ramène l'utilisateur sur la page qu'il visait.",
        "Toutes les images sont servies depuis le dépôt : logos de l'université, de l'école et du produit.",
    ])
    add_heading_custom(doc, "11.3 Limites, écrites noir sur blanc", 2)
    add_caption(doc, "Tableau 11.2 — Limites connues et leur conséquence")
    add_table(doc, ["Limite", "Conséquence", "Position assumée"],
              [("Le compteur d'échecs de connexion est en mémoire", "un redémarrage ou un second nœud "
                "réinitialise le CAPTCHA", "acceptable en démonstration mono-poste ; à remplacer par un "
                "compteur persistant avant une mise en production"),
               ("Le schéma vient de Hibernate, database/schema.sql est obsolète",
                "recréer la base depuis le script ne reproduit que quatre tables",
                "la référence est écrite dans le modèle JPA ; le script reste comme archive"),
               ("Aucune migration versionnée", "faire évoluer le schéma en production demande une "
                "procédure à écrire", "hors du cadre d'un travail d'établissement"),
               ("La promesse finale vit hors la plateforme", "les statuts « offre » et « embauché » ne sont "
                "plus atteignables", "HireLink ne remplace pas la décision d'embauche"),
               ("Aucun encaissement réel", "une facture payante est marquée réglée sans paiement",
                "registre administratif de démonstration, écrit au chapitre 9.2"),
               ("Vérification d'entreprise sans appel officiel", "le badge « identité vérifiée » repose sur "
                "le format et la cohérence de l'ICE, plus une décision humaine",
                "aucune API payante de registre n'est branchée"),
               ("Aide automatique dépendante d'une clé", "sans clé, un repli local plus simple prend le "
                "relais", "la démonstration reste possible hors ligne"),
               ("Pas d'application mobile", "l'usage se fait dans un navigateur",
                "interface responsive, aucune application native écrite"),
               ("Stockage local des fichiers", "CV et logos vivent sur le disque de backend/data",
                "aucune migration objet n'est implémentée"),
               ("Charge et intrusion non testées", "aucun chiffre de capacité ni de résistance",
                "listé comme travail à conduire, pas comme un acquis"),
               ], col_widths=[4.6, 5.6, 6.8], size=9)


# ------------------------------------------------------------------- chapitre 12
def chapitre_12(doc):
    doc.add_page_break()
    add_heading_custom(doc, "12. Bilan, compétences acquises et suites possibles", 1)
    add_heading_custom(doc, "12.1 Ce qui a été obtenu", 2)
    add_para(doc, "Une application qui fonctionne de bout en bout sur un poste sans infrastructure, avec "
                  "trois espaces réellement séparés par le serveur, un cycle de vie complet des offres et "
                  "des candidatures, un processus d'entretien avec réponse et rappel, une couche "
                  "d'administration capable de modérer et d'expliquer, et une documentation dont chaque "
                  "affirmation se laisse retrouver dans le code.")
    add_para(doc, "Le gain le plus important n'est pas une fonctionnalité mais une discipline : chaque fois "
                  "qu'un contrôle tenait seulement dans le navigateur, il a été descendu dans le service ; "
                  "chaque fois qu'une information affichée ne correspondait à aucune donnée mesurée, elle "
                  "a été retirée. C'est ce qui permet de tenir le discours du chapitre 11.3 sans "
                  "contradiction.")
    add_caption(doc, "Tableau 12.1 — Compétences de la filière exercées sur ce projet")
    add_table(doc, ["Domaine", "Ce qui a été pratiqué"],
              [("Conception objet", "modèle de vingt-quatre entités et de onze énumérations, "
                "associations JPA, diagrammes de classes et de paquets"),
               ("Bases de données", "schéma relationnel de vingt-cinq tables, jointures, index sur les "
                "chemins chauds, comptages et normalisations écrits en SQL"),
               ("Développement web", "API REST de cent cinquante et un endpoints, contrat de réponse, "
                "pages statiques et appels authentifiés"),
               ("Sécurité applicative", "jetons, autorisations par rôle et par organisation, anti-robot, "
                "en-têtes de réponse, échappement du rendu"),
               ("Génie logiciel", "règles identifiées et tracées, matrice de traçabilité, archivage du code "
                "retiré, tests rejouables"),
               ("Modélisation des processus", "diagramme d'activité, trois séquences, processus BPMN 2.0 "
                "échangeable"),
               ("Communication technique", "cahier des charges, rapport, captures légendées, notices de "
                "lancement"),
               ], col_widths=[4.2, 12.8], size=9)
    add_heading_custom(doc, "12.2 Suites, dans l'ordre d'utilité", 2)
    add_bullets(doc, [
        "Persistance du compteur d'échecs et du comptage anti-robot, pour que la sécurité ne dépende plus "
        "d'un redémarrage.",
        "Migrations versionnées (Flyway) pour que le schéma cesse d'être implicite, et réécriture de "
        "database/schema.sql ou suppression assumée du fichier.",
        "Branchement d'un moteur BPMN sur la définition déjà exportée : l'ordonnancement deviendrait "
        "exécutable plutôt que représenté.",
        "Vérification d'entreprise par un appel officiel, avec journal de la réponse, pour transformer le "
        "badge en preuve.",
        "Étude statistique du score de compatibilité et des décisions, avant toute communication sur "
        "l'équité du processus.",
        "Test de charge et test d'intrusion, avec publication des résultats ou du refus de les publier.",
        "Boutique de logos et stockage objet, pour que la plateforme ne dépende plus d'un disque local.",
    ])
    add_heading_custom(doc, "12.3 Conclusion", 2)
    add_para(doc, "HireLink répond à la problématique posée en chapitre 2 : une petite entreprise peut "
                  "conduire un recrutement complet et tracé sans licence étrangère, et un candidat peut "
                  "savoir, sans compte payant et sans promesse creuse, qui publie l'annonce et où en est "
                  "son dossier. Le produit reste un démonstrateur : ses limites sont écrites, ses preuves "
                  "sont rejouables, et chaque exigence du cahier des charges renvoie à une ligne du dépôt.")


# ------------------------------------------------------------------------ annexes
def annexes(doc):
    doc.add_page_break()
    add_heading_custom(doc, "Annexe A — Lancer l'application", 1)
    add_para(doc, "Deux processus suffisent. La base doit tourner (service mysql de XAMPP), puis l'API, "
                  "puis l'interface.")
    add_caption(doc, "Tableau A.1 — Commandes")
    add_table(doc, ["Étape", "Commande"],
              [("1. Base de données", "démarrer le service mysql depuis le panneau de contrôle XAMPP "
                "(MariaDB 10.4.32, port 3306)"),
               ("2. API", "cd backend && mvn spring-boot:run"),
               ("3. Interface", "python -m http.server 5500 --directory frontend"),
               ("4. Ouverture", "http://localhost:5500 puis connexion avec un compte de l'annexe B"),
               ("5. Sans SMTP ni clé d'IA", "mvn spring-boot:run "
                "-Dspring-boot.run.arguments=--spring.mail.host= (les envois sont alors enregistrés, "
                "non envoyés)"),
               ], col_widths=[3.6, 13.4], size=9)
    add_heading_custom(doc, "Annexe B — Comptes de démonstration", 1)
    add_caption(doc, "Tableau B.1 — Comptes semés par DataSeeder, utilisés pendant les tests")
    add_table(doc, ["Espace", "Identifiant", "Mot de passe", "Droits réels"],
              [("Candidat", "candidat@hirelink.ma", "Candidat123!", "profil, candidatures, notifications"),
               ("Recruteur membre", "recruteur@hirelink.ma", "Recruteur123!", "offres et pipeline de son "
                "organisation"),
               ("Créatrice d'espace", "n.bennani@atlasdigital.ma", "Recruteur123!", "OWNER : équipe, "
                "formule, transfert"),
               ("Responsable de poste", "m.alaoui@atlasdigital.ma", "Recruteur123!", "OrgRole "
                "HIRING_MANAGER, accès filtré par R14"),
               ("Administrateur", "admin@hirelink.ma", "Admin123!", "modération, comptes, organisations, "
                "plans, audit"),
               ], col_widths=[3.4, 4.8, 3.0, 5.8], size=9)
    add_heading_custom(doc, "Annexe C — Inventaire des livrables", 1)
    add_caption(doc, "Tableau C.1 — Ce que le dépôt contient pour la soutenance")
    add_table(doc, ["Livrable", "Chemin", "Contenu"],
              [("Cahier des charges v5", "docs/cahier-des-charges/HireLink_Cahier_des_charges_v5.docx",
                "dix sections, 71 exigences fonctionnelles, 34 non fonctionnelles, 41 lignes de "
                "traçabilité calculées sur le dépôt"),
               ("Rapport de projet", "docs/rapport/HireLink_Rapport_de_projet_v5.docx", "ce document"),
               ("Diagrammes", "docs/diagrammes/v5/", "12 sources .puml et 12 PNG : cas d'utilisation (3), "
                "classes, séquences (3), activité, paquets, déploiement, BPMN, pile technique"),
               ("Processus BPMN", "docs/diagrammes/v5/processus_recrutement_hirelink.bpmn",
                "export BPMN 2.0 XML, trois couloirs, importable dans Camunda Modeler ou Bizagi"),
               ("Captures d'écran", "docs/guide-captures/v5-2026-11-06/", "23 images et leur légende"),
               ("Tests Postman", "docs/postman/", "2 collections, le générateur, le run lu et son JSON"),
               ("Notices", "docs/API.md, docs/SECURITE.md, docs/GUIDE_INSTALLATION_ET_TEST.md",
                "contrat d'API, limites de sécurité, installation"),
               ("Code retiré", "docs/dead-code/", "routes et colonnes supprimées, avec le motif"),
               ], col_widths=[3.2, 6.0, 7.8], size=9)
    add_heading_custom(doc, "Annexe D — Reproduire les preuves", 1)
    add_caption(doc, "Tableau D.1 — Commandes de reproduction")
    add_table(doc, ["Objectif", "Commande"],
              [("Régénérer les 12 diagrammes", "python docs/diagrammes/_gen_v5_plantuml.py"),
               ("Un seul diagramme", "python docs/diagrammes/_gen_v5_plantuml.py --only 04"),
               ("Régénérer l'export BPMN", "python docs/diagrammes/_gen_v5_bpmn_xml.py"),
               ("Régénérer les collections Postman", "python docs/postman/gen_postman_v5.py"),
               ("Exécuter la suite lecture seule",
                "newman run docs/postman/HireLink_v5_lecture_seule.postman_collection.json "
                "--reporter-json-export docs/postman/resultat-lecture-seule.json"),
               ("Recalculer les volumétries", "comptages SQL du chapitre 1 (users, job_offers, "
                "organizations, applications)"),
               ("Régénérer les documents", "python docs/cahier-des-charges/generate_cdc_v5_docx.py puis "
                "python docs/rapport/generate_rapport_v5_docx.py"),
               ], col_widths=[5.0, 12.0], size=9)
    add_para(doc, "Le rendu des diagrammes passe par le serveur PlantUML public : ni Java ni Graphviz "
                  "n'est installé sur ce poste. Le script essaie une requête, puis une seconde en POST, "
                  "puis attend et recommence, parce que ce serveur renvoie parfois une page de "
                  "consentement anti-robot ou un HTTP 400 sur une source trop longue. Deux pièges ont été "
                  "relevés : un libellé de cas d'utilisation contenant un retour à la ligne dans la forme "
                  "longue fait échouer le rendu, et le mot-clé cloud n'est pas admis dans un diagramme de "
                  "séquence.", size=10)
    add_heading_custom(doc, "Annexe E — Glossaire", 1)
    add_caption(doc, "Tableau E.1 — Termes utilisés")
    add_table(doc, ["Terme", "Sens retenu ici"],
              [("ATS", "Applicant Tracking System : logiciel qui suit les candidatures d'une entreprise."),
               ("ICE", "Identifiant Commun de l'Entreprise marocain, quinze chiffres, utilisé ici comme "
                "preuve d'existence légale."),
               ("Pipeline", "colonnes d'étapes d'une candidature : Reçues, Présélection, Shortlist, "
                "Entretien."),
               ("Scorecard", "grille de critères et de notes remplie par les évaluateurs."),
               ("SLA de réponse", "délai que l'entreprise s'engage à tenir pour répondre ; suivi interne, "
                "jamais affiché comme promesse au candidat."),
               ("Responsable de poste", "membre de l'équipe qui ne voit que les dossiers qui lui sont "
                "attribués."),
               ("Formule", "abonnement : change les crédits IA et les sièges, jamais la visibilité des "
                "offres."),
               ("Crédit IA", "unité de consommation d'un appel à un modèle de langage, comptée par "
                "fonction."),
               ("Passkey", "clé WebAuthn stockée dans le navigateur ou le système, sans mot de passe."),
               ("TOTP", "code à six chiffres à durée limitée produit par une application "
                "d'authentification."),
               ("BPMN", "Business Process Model and Notation : langage graphique normalisé pour décrire "
                "un processus métier, échangeable en XML."),
               ("DataSeeder", "classe qui remplit la base de démonstration au premier démarrage."),
               ("newman", "exécuteur en ligne de commande des collections Postman."),
               ], col_widths=[3.2, 13.8], size=9)


# -------------------------------------------------------------------------- main
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
    for build in (chapitre_1, chapitre_2, chapitre_3, chapitre_4, chapitre_5, chapitre_6,
                  chapitre_7, chapitre_8, chapitre_9, chapitre_10, chapitre_11, chapitre_12):
        build(doc)
    annexes(doc)
    add_pied_de_page(doc)

    ecrits = []
    for dossier in OUT_DIRS:
        dossier.mkdir(parents=True, exist_ok=True)
        chemin = dossier / FILENAME
        doc.save(str(chemin))
        ecrits.append(chemin)
    for chemin in ecrits:
        print(f"{chemin}  {chemin.stat().st_size:,} o")
    figures = len(doc.inline_shapes)
    mots = sum(len(p.text.split()) for p in doc.paragraphs)
    mots += sum(len(c.text.split()) for t in doc.tables for r in t.rows for c in r.cells)
    print(f"{len(doc.tables)} tableaux, {figures} figures insérées, {mots} mots environ")


if __name__ == "__main__":
    main()
