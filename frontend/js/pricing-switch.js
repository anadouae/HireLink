(function () {
  const tabC = document.getElementById("price-tab-candidat");
  const tabR = document.getElementById("price-tab-recruteur");
  const panelC = document.getElementById("pricing-candidat");
  const panelR = document.getElementById("pricing-recruteur");
  const titleEl = document.getElementById("pricing-title");
  const leadEl = document.getElementById("pricing-lead");
  if (!tabC || !tabR || !panelC || !panelR) return;

  const copy = {
    candidat: {
      title: "Des formules simples, pour chaque rôle",
      lead: "Candidats : cherchez et postulez gratuitement, passez en Performance pour plus d’aides IA.",
    },
    recruteur: {
      title: "Des formules simples, pour chaque équipe",
      lead: "Entreprises : Essentiel (1 siège), Professionnel (5 sièges) ou Entreprise (25 sièges), selon la taille de l’équipe.",
    },
  };

  function show(which) {
    const isC = which === "candidat";
    tabC.classList.toggle("is-active", isC);
    tabR.classList.toggle("is-active", !isC);
    tabC.setAttribute("aria-selected", isC ? "true" : "false");
    tabR.setAttribute("aria-selected", isC ? "false" : "true");
    panelC.hidden = !isC;
    panelR.hidden = isC;
    const c = copy[which];
    if (c && titleEl) titleEl.textContent = c.title;
    if (c && leadEl) leadEl.textContent = c.lead;
  }

  tabC.addEventListener("click", () => show("candidat"));
  tabR.addEventListener("click", () => show("recruteur"));
  // Liens « Formules candidats / entreprises » (pied de page) : ouvrent le bon onglet.
  document.querySelectorAll("[data-pricing-open]").forEach((link) => {
    link.addEventListener("click", () => show(link.getAttribute("data-pricing-open") === "recruteur" ? "recruteur" : "candidat"));
  });
})();
