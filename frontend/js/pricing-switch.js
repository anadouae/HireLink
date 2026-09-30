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
      title: "Accélérez votre carrière",
      lead: "ROI personnel clair : activez votre recherche gratuitement, passez en Performance pour diviser votre temps de recherche.",
    },
    recruteur: {
      title: "Recrutez à la performance, sans opacité",
      lead: "ROI entreprise : automatisez votre sourcing de confiance, sécurisez les meilleurs talents avec Premium.",
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
})();
