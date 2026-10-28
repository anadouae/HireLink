/** Paramètres recruteur : compte, photo, alertes, apparence, formules. */
(function () {
  const RC = HireLinkApp;
  const session = RC.mount({ active: "settings", title: "Paramètres" });
  if (!session) return;
  const $ = (id) => document.getElementById(id);
  const NOTIF_KEY = "hirelink.notif.recruiter";
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
  const hash = () => { const h = location.hash.slice(1); openTab(["compte", "alertes", "apparence", "plans"].includes(h) ? h : h === "image" || h === "photo-upload" ? "compte" : "compte"); };
  hash();
  window.addEventListener("hashchange", hash);

  /* Compte */
  HireLinkAPI.api("/me").then((p) => {
    $("set-name").value = (p.fullName || "").replace(/\s*\(alias\)\s*/i, "");
    $("set-email").value = p.email || session.email || "";
    $("set-role").textContent = (RC.getOrg() && RC.ROLE_FR[RC.getOrg().myRole]) || "Recruteur";
    paintPhoto();
  }).catch((err) => RC.toast("Profil", err.message, "danger"));
  document.addEventListener("rc:org", (e) => { $("set-role").textContent = RC.ROLE_FR[e.detail.myRole] || "Recruteur"; });

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

  function paintPhoto() {
    let url = "";
    try { url = localStorage.getItem(photoKey) || ""; } catch (_) {}
    const img = $("pf-photo"), fb = $("pf-photo-fallback");
    fb.textContent = RC.initials($("set-name").value || session.fullName);
    img.hidden = !url; fb.hidden = !!url;
    if (url) img.src = url;
    RC.paintProfile();
  }
  $("btn-photo-pick").addEventListener("click", () => $("pf-photo-file").click());
  $("btn-photo-clear").addEventListener("click", () => { try { localStorage.removeItem(photoKey); } catch (_) {} paintPhoto(); RC.toast("Photo", "Image retirée", "ok"); });
  $("pf-photo-file").addEventListener("change", (e) => {
    const f = e.target.files && e.target.files[0];
    if (!f) return;
    const reader = new FileReader();
    reader.onload = () => {
      const img = new Image();
      img.onload = () => {
        const size = 256, c = document.createElement("canvas");
        c.width = c.height = size;
        const side = Math.min(img.width, img.height);
        c.getContext("2d").drawImage(img, (img.width - side) / 2, (img.height - side) / 2, side, side, 0, 0, size, size);
        try { localStorage.setItem(photoKey, c.toDataURL("image/jpeg", 0.82)); } catch (_) { RC.toast("Photo", "Image trop lourde pour ce navigateur", "danger"); return; }
        paintPhoto();
        RC.toast("Photo", "Image enregistrée sur cet appareil", "ok");
      };
      img.src = reader.result;
    };
    reader.readAsDataURL(f);
    e.target.value = "";
  });

  /* Alertes */
  try {
    const n = JSON.parse(localStorage.getItem(NOTIF_KEY) || "{}");
    $("n-sla").checked = n.sla !== false; $("n-new").checked = n.neu !== false; $("n-interview").checked = n.interview !== false;
  } catch (_) {}
  $("btn-save-notif").addEventListener("click", () => {
    localStorage.setItem(NOTIF_KEY, JSON.stringify({ sla: $("n-sla").checked, neu: $("n-new").checked, interview: $("n-interview").checked }));
    RC.toast("Alertes", "Préférences enregistrées sur cet appareil", "ok");
  });

  /* Apparence */
  const dark = $("theme-toggle");
  dark.checked = HireLinkTheme.current() === "dark";
  dark.addEventListener("change", () => {
    HireLinkTheme.apply(dark.checked ? "dark" : "light");
    const t = $("rc-theme");
    if (t) t.innerHTML = RC.svg(dark.checked ? "sun" : "moon");
  });

  /* Formules */
  function refreshBilling() {
    const prem = HireLinkAPI.isPremium();
    $("plan-access").classList.toggle("is-current", !prem);
    $("plan-premium").classList.toggle("is-current", prem);
    $("mark-access").textContent = prem ? "Toujours disponible" : "Formule en cours";
    $("mark-premium").textContent = prem ? "Formule en cours" : "À activer";
    $("btn-billing-cancel").hidden = !prem;
    $("btn-billing-premium").hidden = prem;
  }
  $("btn-billing-premium").addEventListener("click", () => { HireLinkGrowth.showPremium("recruiter"); });
  $("btn-billing-cancel").addEventListener("click", async () => {
    try {
      const r = await HireLinkAPI.api("/billing/subscription", { method: "DELETE" });
      HireLinkAPI.setPlanTier(r.planTier);
      RC.toast("Formule", "Retour à la formule Accès", "info");
      refreshBilling();
    } catch (err) {
      RC.toast("Formule", err.message, "danger");
    }
  });
  window.addEventListener("focus", refreshBilling);
  refreshBilling();
})();
