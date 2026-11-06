/**
 * Aide in-app — FAQ différenciée recruteur vs candidat
 */
(function (global) {
  const FAQ_CANDIDATE = [
    {
      q: "Comment compléter mon profil ?",
      a: "Paramètres ou Profil → compétences, métiers, lieux, contrats. Ou importez un CV : les compétences en sont extraites automatiquement.",
    },
    {
      q: "Comment postuler correctement ?",
      a: "Offres → Voir l’offre → parcours : détail → CV → lettre (opt.) → photo (opt.) → envoi. L’entreprise voit votre dossier complet.",
    },
    {
      q: "Où suivre mes candidatures ?",
      a: "Mes candidatures : liste puis détail avec timeline — soumise → présélection → shortlist → entretien. Une entreprise reconnue « Réactif » répond en général sous trois jours ; aucun délai ne vous est promis ailleurs.",
    },
    {
      q: "Prep entretien IA ?",
      a: "Dans le détail d’une candidature ou Profil → Assistant IA. Inclus dans votre quota mensuel ; le plan Performance le porte de 5 à 40 usages.",
    },
    {
      q: "Performance candidat — à quoi ça sert ?",
      a: "Le plan Performance (39 MAD/mois) passe votre quota IA de 5 à 40 usages : réécriture de profil, préparation d’entretien, explication du score et contrôle de la langue. Rien n’est « mis en avant » — le matching reste calculé sur vos compétences.",
    },
    {
      q: "Mode sombre ?",
      a: "Paramètres → Apparence. Votre préférence est mémorisée sur cet appareil.",
    },
  ];

  const FAQ_RECRUITER = [
    {
      q: "Comment publier une offre conforme ?",
      a: "Mes offres → Nouvelle offre. Renseignez le poste, le lieu (une ville), le contrat et les compétences. L’entreprise doit être vérifiée pour publier.",
    },
    {
      q: "Pipeline : comment avancer en sécurité ?",
      a: "Chaque avancée exige : identité, lecture du CV, respect du délai de 1ʳᵉ réponse promis sur l’offre, et une note RH. Pour l’entretien : type + date obligatoires.",
    },
    {
      q: "C’est quoi le délai de 1ʳᵉ réponse ?",
      a: "À la création de l’offre, vous choisissez en combien de jours vous vous engagez à donner une première réponse (ex. 7 jours). Ce n’est pas un délai inventé par dossier : le compteur démarre à la candidature et le candidat le voit. Avancer ou refuser avant l’échéance = engagement tenu.",
    },
    {
      q: "Organiser un entretien ?",
      a: "Au passage Shortlist → Entretien : choisissez visioconférence ou sur site, la nature de l’entretien, la date et l’heure, le lieu ou le lien.",
    },
    {
      q: "Canaux entreprise (pas un cabinet juridique) ?",
      a: "HireLink est un ATS RH pour entreprises & organisations : offres, pipeline candidats, équipe RH, marque employeur — pas un outil juridique.",
    },
    {
      q: "Équipe & sièges Premium ?",
      a: "Entreprise & équipe → Inviter. Premium monte l’équipe à 25 sièges (5 en Accès) et le quota IA à 200 usages par mois. Vos offres ne sont pas mieux classées : le tri reste celui des compétences.",
    },
    {
      q: "Facturation & Premium ?",
      a: "Paramètres → Facturation : formule actuelle, activation Premium, plafond de sièges et de crédits IA. Seul le créateur de l’espace change la formule. Mode sombre dans Apparence.",
    },
  ];

  async function openHelp(role) {
    const isRec = role === "RECRUTEUR" || role === "recruiter";
    const faq = isRec ? FAQ_RECRUITER : FAQ_CANDIDATE;
    const items = faq
      .map(
        (f) =>
          "<details class='help-item'><summary>" +
          f.q +
          "</summary><p>" +
          f.a +
          "</p></details>"
      )
      .join("");
    await HireLink.openModal({
      title: isRec ? "Centre d’aide · Recruteur RH" : "Centre d’aide · Candidat",
      description: isRec
        ? "Normes ATS, SLA, entretiens et gouvernance d’équipe — sans quitter votre espace."
        : "Profil, candidatures, matching et Premium — tout reste dans votre espace.",
      bodyHtml: "<div class='help-drawer'>" + items + "</div>",
      confirmLabel: "Fermer",
      hideCancel: true,
      large: true,
    });
  }

  function bindHelpLinks(role) {
    const userRole = role || (global.HireLinkAPI && global.HireLinkAPI.getSession ? global.HireLinkAPI.getSession()?.role : null) || document.body?.dataset?.role || "CANDIDAT";
    document.querySelectorAll('a[href*="help.html"], .js-in-app-help, #btn-help').forEach((el) => {
      el.removeEventListener("click", el.__hirelinkHelpHandler);
      const handler = (e) => {
        e.preventDefault();
        openHelp(userRole);
      };
      el.__hirelinkHelpHandler = handler;
      el.addEventListener("click", handler);
    });
  }

  global.HireLinkHelp = { openHelp, bindHelpLinks };

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", () => {
      try {
        const s = global.HireLinkAPI && global.HireLinkAPI.getSession ? global.HireLinkAPI.getSession() : null;
        bindHelpLinks((s && s.role) || (document.body && document.body.dataset && document.body.dataset.role) || "CANDIDAT");
      } catch (_) {}
    });
  } else {
    try {
      const s = global.HireLinkAPI && global.HireLinkAPI.getSession ? global.HireLinkAPI.getSession() : null;
      bindHelpLinks((s && s.role) || (document.body && document.body.dataset && document.body.dataset.role) || "CANDIDAT");
    } catch (_) {}
  }
})(window);
