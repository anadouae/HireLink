/** Paramètres candidat : alertes, apparence, formule et factures, données et sécurité. Le profil et le CV ont leur propre page. */
(function () {
  const RC = HireLinkApp;
  const session = RC.mount({ active: "settings", title: "Paramètres" });
  if (!session) return;
  const $ = (id) => document.getElementById(id);
  const TABS = ["alertes", "apparence", "plans", "donnees"];
  // Anciennes ancres (liens d'e-mails, favoris) : on les redirige vers l'onglet qui porte la même chose aujourd'hui.
  const LEGACY = { privacy: "alertes", parameters: "alertes", notifications: "alertes", "set-plans": "plans", compte: "donnees" };
  // Le profil, le parcours et le CV ne sont plus ici : ces anciennes ancres ouvrent la page Profil & CV.
  const MOVED = { account: "candidate-profile.html#profil", search: "candidate-profile.html#profil", parcours: "candidate-profile.html#profil", cv: "candidate-profile.html#cv", "set-cv": "candidate-profile.html#cv" };

  function openTab(id) {
    document.querySelectorAll(".rc-tab").forEach((b) => {
      const on = b.dataset.t === id;
      b.classList.toggle("is-on", on);
      b.setAttribute("aria-selected", on ? "true" : "false");
    });
    document.querySelectorAll(".rc-panel").forEach((p) => (p.hidden = p.id !== "tab-" + id));
  }
  document.querySelector(".rc-tabs").addEventListener("click", (e) => {
    const b = e.target.closest(".rc-tab");
    if (!b) return;
    openTab(b.dataset.t);
    history.replaceState(null, "", "#" + b.dataset.t);
  });
  function applyHash() {
    const h = location.hash.slice(1);
    if (MOVED[h]) { location.replace(MOVED[h]); return; }
    const id = LEGACY[h] || h;
    openTab(TABS.includes(id) ? id : "alertes");
  }
  applyHash();
  window.addEventListener("hashchange", applyHash);

  /* Alertes */
  HireLinkAPI.api("/me")
    .then((p) => { $("set-mail").checked = p.emailNotifications !== false; })
    .catch((err) => RC.toast("Paramètres", err.message, "danger"));

  $("btn-save-mail").addEventListener("click", async (e) => {
    const btn = e.currentTarget;
    RC.busy(btn, true);
    try {
      await HireLinkAPI.api("/me", { method: "PATCH", body: { emailNotifications: $("set-mail").checked } });
      RC.toast("Alertes", "Préférence enregistrée", "ok");
    } catch (err) {
      RC.toast("Alertes", err.message, "danger");
    } finally {
      RC.busy(btn, false);
    }
  });

  /* Apparence */
  const dark = $("theme-toggle");
  if (dark && window.HireLinkTheme) {
    dark.checked = HireLinkTheme.current() === "dark";
    dark.addEventListener("change", () => {
      HireLinkTheme.apply(dark.checked ? "dark" : "light");
      const t = $("rc-theme");
      if (t) t.innerHTML = RC.svg(dark.checked ? "sun" : "moon");
    });
  }

  /* Formule */
  function refreshBilling() {
    const prem = HireLinkAPI.isPremium();
    const free = document.querySelector('[data-plan-mark="gratuit"]');
    const perf = document.querySelector('[data-plan-mark="performance"]');
    if (free) free.textContent = prem ? "Toujours disponible" : "Formule en cours";
    if (perf) perf.textContent = prem ? "Formule en cours" : "À activer";
    $("plan-gratuit").classList.toggle("is-current", !prem);
    $("plan-performance").classList.toggle("is-current", !!prem);
    $("btn-billing-cancel").hidden = !prem;
    $("btn-billing-premium").hidden = !!prem;
  }

  $("btn-billing-premium").addEventListener("click", () => {
    if (window.HireLinkGrowth) HireLinkGrowth.showPremium("candidate");
  });
  $("btn-billing-cancel").addEventListener("click", async () => {
    const ok = window.HireLink && HireLink.openModal
      ? await HireLink.openModal({
          title: "Résilier mon abonnement ?",
          description: "Vous gardez Performance jusqu’à la fin de la période déjà payée, puis vous revenez à Gratuit. Aucun remboursement n’est émis.",
          confirmLabel: "Résilier",
          cancelLabel: "Garder Performance",
          danger: true,
        })
      : window.confirm("Résilier mon abonnement ?");
    if (!ok) return;
    try {
      const r = await HireLinkAPI.api("/billing/subscription", { method: "DELETE" });
      RC.toast("Formule", r.message || "Résiliation enregistrée", "info");
      refreshBilling();
    } catch (err) {
      RC.toast("Formule", err.message, "danger");
    }
  });

  if (window.HireLinkInvoices) HireLinkInvoices.mount($("my-invoices"), "mine");

  if (window.HireLinkCredits) {
    HireLinkCredits.load(true).then((c) => {
      const line = $("credits-line");
      if (!line || !c) return;
      const days = Number(c.daysToReset);
      line.textContent =
        "Il vous reste " + c.remaining + " aide" + (c.remaining > 1 ? "s" : "") + " IA sur " + c.limit +
        (Number.isFinite(days) && days >= 0 ? " · renouvellement dans " + days + " jour" + (days > 1 ? "s" : "") : "") + ".";
    }).catch(() => {
      const line = $("credits-line");
      if (line) line.textContent = "Le détail de vos aides IA est momentanément indisponible.";
    });
  }

  /* Données */
  $("btn-export-data").addEventListener("click", async (e) => {
    const btn = e.currentTarget;
    RC.busy(btn, true);
    try {
      const data = await HireLinkAPI.api("/me/export");
      const blob = new Blob([JSON.stringify(data, null, 2)], { type: "application/json" });
      const a = document.createElement("a");
      a.href = URL.createObjectURL(blob);
      a.download = "hirelink-mes-donnees.json";
      a.click();
      setTimeout(() => URL.revokeObjectURL(a.href), 2000);
      RC.toast("Copie prête", "Le fichier de votre compte a été téléchargé.", "ok");
    } catch (err) {
      RC.toast("Export", err.message, "danger");
    } finally {
      RC.busy(btn, false);
    }
  });

  refreshBilling();
})();
