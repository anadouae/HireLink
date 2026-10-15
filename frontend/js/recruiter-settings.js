(function () {
  const session = HireLinkShell.mountShell({ roles: ["RECRUTEUR"], hello: false });
  if (!session) return;

  const NOTIF_KEY = "hirelink.notif.recruiter";

  function refreshBilling() {
    const prem = HireLinkProduct && HireLinkProduct.isPremium("recruiter");
    document.getElementById("plan-badge").textContent = prem ? "Premium" : "Gratuit";
    document.getElementById("plan-desc").textContent = prem
      ? "Premium actif — sièges équipe, alertes, offres mises en avant."
      : "Plan Gratuit : 1 siège, pipeline sécurisé, SLA. Passez Premium pour scaler l’équipe RH.";
    document.getElementById("btn-billing-cancel").hidden = !prem;
    document.getElementById("btn-billing-premium").textContent = prem ? "Gérer Premium" : "Activer Premium";
  }

  HireLinkAPI.api("/me")
    .then((p) => {
      document.getElementById("set-name").value = p.fullName || "";
      document.getElementById("set-company").value = p.company || "";
      document.getElementById("set-ice").value = p.companyIce || "";
    })
    .catch((err) => HireLink.toast("Profil", err.message, "danger"));

  document.getElementById("settings-profile").addEventListener("submit", async (e) => {
    e.preventDefault();
    try {
      await HireLinkAPI.api("/me", {
        method: "PATCH",
        body: {
          fullName: document.getElementById("set-name").value,
          company: document.getElementById("set-company").value,
          companyIce: document.getElementById("set-ice").value,
        },
      });
      HireLink.toast("Enregistré", "Fiche organisation mise à jour", "ok");
    } catch (err) {
      HireLink.toast("Erreur", err.message, "danger");
    }
  });

  const themeToggle = document.getElementById("theme-toggle");
  if (themeToggle && window.HireLinkTheme) {
    themeToggle.checked = HireLinkTheme.current() === "dark";
    themeToggle.addEventListener("change", () => {
      HireLinkTheme.apply(themeToggle.checked ? "dark" : "light");
    });
  }

  document.getElementById("btn-billing-premium").onclick = () => {
    if (window.HireLinkGrowth) HireLinkGrowth.showPremium("recruiter");
    setTimeout(refreshBilling, 400);
  };
  document.getElementById("btn-billing-cancel").onclick = () => {
    localStorage.removeItem("hirelink.premium.active.recruiter");
    HireLink.toast("Plan", "Retour au Gratuit", "info");
    refreshBilling();
    const barGone = document.getElementById("hl-prem-bar");
    if (!barGone && window.HireLinkProduct) HireLinkProduct.mountPremiumBar("recruiter");
  };

  try {
    const n = JSON.parse(localStorage.getItem(NOTIF_KEY) || "{}");
    if (n.sla === false) document.getElementById("n-sla").checked = false;
    if (n.neu === false) document.getElementById("n-new").checked = false;
    if (n.interview === false) document.getElementById("n-interview").checked = false;
  } catch (_) {}

  document.getElementById("btn-save-notif").onclick = () => {
    localStorage.setItem(
      NOTIF_KEY,
      JSON.stringify({
        sla: document.getElementById("n-sla").checked,
        neu: document.getElementById("n-new").checked,
        interview: document.getElementById("n-interview").checked,
      })
    );
    HireLink.toast("Notifications", "Préférences enregistrées", "ok");
  };

  // refresh after premium modal CTA
  window.addEventListener("storage", refreshBilling);
  refreshBilling();
  setInterval(refreshBilling, 2000);
})();
