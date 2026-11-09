/** Paramètres recruteur : compte, photo, alertes, apparence, formules. */
(function () {
  const RC = HireLinkApp;
  const session = RC.mount({ active: "settings", title: "Paramètres" });
  if (!session) return;
  const $ = (id) => document.getElementById(id);
  const photoKey = "hirelink.photo." + (session.userId || "me");

  function openTab(id) {
    document.querySelectorAll(".rc-tab").forEach((b) => b.classList.toggle("is-on", b.dataset.t === id));
    document.querySelectorAll(".rc-panel").forEach((p) => (p.hidden = p.id !== "tab-" + id));
  }
  document.querySelector(".rc-tabs").addEventListener("click", (e) => {
    const b = e.target.closest(".rc-tab");
    if (!b) return;
    openTab(b.dataset.t);
    history.replaceState(null, "", "#" + b.dataset.t);
  });
  const hash = () => { const h = location.hash.slice(1); openTab(["compte", "alertes", "apparence", "plans"].includes(h) ? h : "compte"); };
  hash();
  window.addEventListener("hashchange", hash);

  /* Compte */
  HireLinkAPI.api("/me").then((p) => {
    $("set-name").value = (p.fullName || "").replace(/\s*\(alias\)\s*/i, "");
    $("set-email").value = p.email || session.email || "";
    $("set-role").textContent = (RC.getOrg() && RC.ROLE_FR[RC.getOrg().myRole]) || "Recruteur";
    $("n-mail").checked = p.emailNotifications !== false;
    paintPhoto();
  }).catch((err) => RC.toast("Profil", err.message, "danger"));
  document.addEventListener("rc:org", (e) => { $("set-role").textContent = RC.ROLE_FR[e.detail.myRole] || "Recruteur"; refreshBilling(); });

  $("form-account").addEventListener("submit", async (e) => {
    e.preventDefault();
    const name = $("set-name").value.trim();
    if (!name) { RC.toast("Nom requis", "Indiquez votre nom complet.", "warn"); return; }
    const btn = e.submitter;
    RC.busy(btn, true);
    try {
      const p = await HireLinkAPI.api("/me", { method: "PATCH", body: { fullName: name } });
      const s = HireLinkAPI.getSession();
      if (s) { s.fullName = p.fullName || name; localStorage.setItem("hirelink.session", JSON.stringify(s)); }
      RC.toast("Compte", "Profil mis à jour", "ok");
      RC.paintProfile();
    } catch (err) { RC.toast("Compte", err.message, "danger"); } finally { RC.busy(btn, false); }
  });

  async function paintPhoto() {
    const img = $("pf-photo"), fb = $("pf-photo-fallback");
    fb.textContent = RC.initials($("set-name").value || session.fullName);
    try {
      const res = await HireLinkAPI.authFetch("/account/avatar");
      if (!res.ok) throw new Error("none");
      img.src = URL.createObjectURL(await res.blob());
      img.hidden = false;
      fb.hidden = true;
    } catch (_) {
      img.hidden = true;
      fb.hidden = false;
    }
    RC.paintProfile();
  }
  $("btn-photo-pick").addEventListener("click", () => $("pf-photo-file").click());
  $("btn-photo-clear").addEventListener("click", async () => {
    try {
      await HireLinkAPI.api("/account/avatar", { method: "DELETE" });
      paintPhoto();
      RC.toast("Photo", "Image retirée", "ok");
    } catch (err) { RC.toast("Photo", err.message, "danger"); }
  });
  $("pf-photo-file").addEventListener("change", (e) => {
    const f = e.target.files && e.target.files[0];
    if (!f) return;
    const body = new FormData();
    body.append("file", f);
    HireLinkAPI.authFetch("/account/avatar", { method: "POST", body }).then(async (res) => {
      if (!res.ok) {
        let msg = "Image refusée";
        try { msg = (await res.json()).message || msg; } catch (_) {}
        throw new Error(msg);
      }
      paintPhoto();
      RC.toast("Photo", "Image enregistrée", "ok");
    }).catch((err) => RC.toast("Photo", err.message, "danger"));
    e.target.value = "";
  });

  /* Alertes */
  $("btn-save-notif").addEventListener("click", async () => {
    try {
      await HireLinkAPI.api("/me", { method: "PATCH", body: { emailNotifications: $("n-mail").checked } });
      RC.toast("Alertes", "Préférences enregistrées", "ok");
    } catch (err) { RC.toast("Alertes", err.message, "danger"); }
  });

  /* Apparence */
  const dark = $("theme-toggle");
  dark.checked = HireLinkTheme.current() === "dark";
  dark.addEventListener("change", () => {
    HireLinkTheme.apply(dark.checked ? "dark" : "light");
    const t = $("rc-theme");
    if (t) t.innerHTML = RC.svg(dark.checked ? "sun" : "moon");
  });

  /* Formules — réservées au créateur de l'espace, comme côté serveur. */
  function refreshBilling() {
    const prem = HireLinkAPI.isPremium();
    const governs = RC.isCreator();
    $("plan-access").classList.toggle("is-current", !prem);
    $("plan-premium").classList.toggle("is-current", prem);
    $("mark-access").textContent = prem ? "Toujours disponible" : "Formule en cours";
    $("mark-premium").textContent = prem ? "Formule en cours" : "À activer";
    $("btn-billing-cancel").hidden = !prem || !governs;
    $("btn-billing-premium").hidden = prem || !governs;
    const note = document.getElementById("plan-owner-only");
    if (note) note.hidden = governs;
  }
  $("btn-billing-premium").addEventListener("click", () => { HireLinkGrowth.showPremium("recruiter"); });
  $("btn-billing-cancel").addEventListener("click", async () => {
    const ok = window.HireLink && HireLink.openModal
      ? await HireLink.openModal({ title: "Résilier mon abonnement ?", description: "L'accès déjà payé reste ouvert jusqu'à la fin de période enregistrée. Aucun remboursement n'est émis.", confirmLabel: "Résilier" })
      : true;
    if (!ok) return;
    try {
      const r = await HireLinkAPI.api("/billing/subscription", { method: "DELETE" });
      RC.toast("Formule", r.message || "Résiliation enregistrée", "info");
      refreshBilling();
    } catch (err) {
      RC.toast("Formule", err.message, "danger");
    }
  });
  if (window.HireLinkInvoices) HireLinkInvoices.mount(document.getElementById("org-invoices"), "company");
  window.addEventListener("focus", refreshBilling);
  refreshBilling();
})();
