/**
 * Aide in-app — FAQ différenciée recruteur vs candidat
 */
(function (global) {
  const FAQ_CANDIDATE = [
    {
      q: "Comment compléter mon profil ?",
      a: "Paramètres ou Profil → compétences, métiers, lieux, contrats. Ou importez un CV : extraction automatique des skills.",
    },
    {
      q: "Comment postuler correctement ?",
      a: "Offres → Voir l’offre → parcours : détail → CV → lettre (opt.) → photo (opt.) → envoi. L’entreprise voit votre dossier complet.",
    },
    {
      q: "Où suivre mes candidatures ?",
      a: "Mes candidatures : liste puis détail avec timeline — soumise → présélection → shortlist → entretien. Les délais SLA employeur sont visibles.",
    },
    {
      q: "Prep entretien IA ?",
      a: "Dans le détail d’une candidature ou Profil → Assistant IA. Inclus dans votre quota mensuel ; Premium augmente le plafond.",
    },
    {
      q: "Premium candidat — à quoi ça sert ?",
      a: "Alertes matching, priorité, IA complète, profil mis en avant. Activez dans Paramètres → Facturation.",
    },
    {
      q: "Mode sombre ?",
      a: "Paramètres → Apparence. Votre préférence est mémorisée sur cet appareil.",
    },
  ];

  const FAQ_RECRUITER = [
    {
      q: "Comment publier une offre conforme ?",
      a: "Mes offres → Nouvelle offre. Renseignez compétences, lieu, contrat et SLA de réponse. L’entreprise doit être vérifiée pour publier.",
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
      a: "Au passage Shortlist → Entretien : choisissez PHONE / VISIO / ONSITE / ASYNC, date-heure, lieu ou lien, notes internes.",
    },
    {
      q: "Canaux entreprise (pas un cabinet juridique) ?",
      a: "HireLink est un ATS RH pour entreprises & organisations : offres, pipeline candidats, équipe RH, marque employeur — pas un outil juridique.",
    },
    {
      q: "Équipe & sièges Premium ?",
      a: "Entreprise & équipe → Inviter. Premium débloque multi-recruteurs, offres mises en avant, alertes SLA et reporting.",
    },
    {
      q: "Facturation & Premium ?",
      a: "Paramètres → Facturation : plan actuel, activation Premium, récap avantages. Mode sombre dans Apparence.",
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
    document.querySelectorAll('a[href*="help.html"], .js-in-app-help, #btn-help').forEach((el) => {
      el.addEventListener("click", (e) => {
        e.preventDefault();
        openHelp(role);
      });
    });
  }

  global.HireLinkHelp = { openHelp, bindHelpLinks };
})(window);
