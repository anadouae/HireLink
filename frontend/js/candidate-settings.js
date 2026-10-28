(function () {
  const session = HireLinkApp.mount({});
  if (!session) return;

  document.querySelectorAll("[data-settings]").forEach((btn) => {
    btn.addEventListener("click", () => {
      const id = btn.getAttribute("data-settings");
      document.querySelectorAll(".settings-panel").forEach((panel) => {
        panel.hidden = panel.id !== "set-" + id;
      });
      document.querySelectorAll("[data-settings]").forEach((other) => {
        other.classList.toggle("is-on", other === btn);
      });
    });
  });

  function openTab(id) {
    const btn = document.querySelector('[data-settings="' + id + '"]');
    if (btn) {
      btn.click();
      return;
    }
    document.querySelectorAll(".settings-panel").forEach((panel) => {
      panel.hidden = panel.id !== "set-" + id;
    });
    document.querySelectorAll("[data-settings]").forEach((other) => other.classList.remove("is-on"));
  }
  function applyHash() {
    const hash = (location.hash || "").replace("#", "");
    if (hash === "plans" || hash === "set-plans") openTab("plans");
    else if (hash === "cv" || hash === "set-cv") openTab("cv");
    else if (hash === "search" || hash === "parcours") openTab("search");
    else if (hash === "privacy" || hash === "parameters") openTab("privacy");
  }
  applyHash();
  window.addEventListener("hashchange", applyHash);

  function refreshBilling() {
    const prem = HireLinkAPI.isPremium();
    const essentiel = document.querySelector('[data-plan-mark="essentiel"]');
    const performance = document.querySelector('[data-plan-mark="performance"]');
    if (essentiel) essentiel.textContent = prem ? "Toujours disponible" : "Formule en cours";
    if (performance) performance.textContent = prem ? "Formule en cours" : "À activer";
    const foldE = document.getElementById("fold-essentiel");
    const foldP = document.getElementById("fold-performance");
    if (foldE) foldE.open = !prem;
    if (foldP) foldP.open = !!prem;
    const cancel = document.getElementById("btn-billing-cancel");
    const go = document.getElementById("btn-billing-premium");
    if (cancel) cancel.hidden = !prem;
    if (go) go.hidden = !!prem;
  }

  HireLinkAPI.api("/me")
    .then((p) => {
      const bits = String(p.fullName || "").trim().split(/\s+/);
      document.getElementById("set-first").value = p.firstName || bits[0] || "";
      document.getElementById("set-last").value = p.lastName || bits.slice(1).join(" ") || "";
      document.getElementById("set-email").value = p.email || session.email || "";
      showSettingsCv(p);
      document.getElementById("set-nat").value = p.nationality || "";
      document.getElementById("set-city").value = p.city || "";
      document.getElementById("set-birth").value = p.birthDate || "";
      document.getElementById("set-gender").value = p.gender || "";
      const dial = (p.phone || "").match(/^\+(\d+)/);
      const country = document.getElementById("set-phone-country");
      if (p.phoneCountry && dial) {
        const wanted = p.phoneCountry + "|+" + dial[1];
        if ([].some.call(country.options, (o) => o.value === wanted)) country.value = wanted;
      }
      document.getElementById("set-phone").value = dial ? (p.phone || "").replace(/^\+\d+/, "") : (p.phone || "");
      document.getElementById("set-school").value = p.school || "";
      document.getElementById("set-level").value = p.experienceLevel || "";
      document.getElementById("set-mode").value = p.workMode || "";
      document.getElementById("set-avail").value = p.availability || "";
      document.getElementById("set-role").value = p.soughtRoles || "";
      const chosen = String(p.preferredContracts || "").split(",").map((s) => s.trim());
      document.querySelectorAll("#set-contracts input").forEach((box) => {
        box.checked = chosen.indexOf(box.value) >= 0;
      });
      if (window.HireLinkSuggest) HireLinkSuggest.boot();
    })
    .catch((err) => HireLink.toast("Compte", err.message, "danger"));

  function profileBody() {
    const country = document.getElementById("set-phone-country").value.split("|");
    const local = document.getElementById("set-phone").value.replace(/\D/g, "").replace(/^0/, "");
    const contracts = Array.from(document.querySelectorAll("#set-contracts input:checked")).map((n) => n.value).join(", ");
    return {
      firstName: document.getElementById("set-first").value.trim(),
      lastName: document.getElementById("set-last").value.trim(),
      fullName: (document.getElementById("set-first").value.trim() + " " + document.getElementById("set-last").value.trim()).trim(),
      phone: local ? country[1] + local : "",
      phoneCountry: country[0],
      nationality: document.getElementById("set-nat").value.trim(),
      gender: document.getElementById("set-gender").value,
      birthDate: document.getElementById("set-birth").value,
      city: document.getElementById("set-city").value.trim(),
      school: document.getElementById("set-school").value.trim(),
      experienceLevel: document.getElementById("set-level").value.trim(),
      workMode: document.getElementById("set-mode").value,
      availability: document.getElementById("set-avail").value,
      soughtRoles: document.getElementById("set-role").value.trim(),
      preferredContracts: contracts,
    };
  }

  async function saveProfile(e) {
    e.preventDefault();
    try {
      await HireLinkAPI.api("/me", { method: "PATCH", body: profileBody() });
      HireLink.toast("Enregistré", "Profil mis à jour", "ok");
    } catch (err) {
      HireLink.toast("Erreur", err.message, "danger");
    }
  }

  document.getElementById("settings-account").addEventListener("submit", saveProfile);
  document.getElementById("settings-path").addEventListener("submit", saveProfile);

  document.getElementById("btn-billing-premium").onclick = () => {
    if (window.HireLinkGrowth) HireLinkGrowth.showPremium("candidate");
  };
  document.getElementById("btn-billing-cancel").onclick = async () => {
    try {
      const r = await HireLinkAPI.api("/billing/subscription", { method: "DELETE" });
      HireLinkAPI.setPlanTier(r.planTier);
      HireLink.toast("Plan", "Essentiel réactivé", "info");
      refreshBilling();
    } catch (err) {
      HireLink.toast("Plan", err.message, "danger");
    }
  };

  HireLinkAPI.api("/ai/credits")
    .then((c) => {
      const line = document.getElementById("credits-line");
      if (line) {
        line.textContent = c.premium
          ? "Performance est active : " + c.remaining + " synthèse(s) du CV restante(s) ce mois-ci, sur " + c.limit + "."
          : "Essentiel : " + c.remaining + " synthèse" + (Number(c.remaining) > 1 ? "s" : "") + " du CV ce mois-ci, sur " + c.limit + ".";
      }
    })
    .catch(() => {});

  Promise.all([
    HireLinkAPI.api("/me").catch(function () { return null; }),
    HireLinkAPI.api("/applications/mine").catch(function () { return []; }),
  ]).then(function (pack) {
    const me = pack[0];
    const apps = pack[1] || [];
    const box = document.getElementById("advice-box");
    if (!box) return;
    const tips = [];
    if (!me || !me.cvFileName) tips.push("Déposez votre CV. La synthèse n’a rien à lire tant que le document n’est pas là.");
    else if (!String(me.cvSections || "").trim()) tips.push("Confirmez les sections extraites du CV. Sans ça, le classement des offres travaille sur un texte incomplet.");
    else tips.push("Le CV est lisible. Lancez la synthèse pour voir ce qui manque pour le métier visé, puis ajustez les compétences.");
    const late = apps.filter(function (a) {
      return a.slaRemainingDays != null && a.slaRemainingDays < 0 && a.status !== "REJECTED" && a.status !== "WITHDRAWN";
    });
    if (late.length) tips.push(late.length + " employeur" + (late.length > 1 ? "s ont" : " a") + " dépassé le délai publié sur l’offre.");
    if (apps.some(function (a) { return a.status === "INTERVIEW"; })) tips.push("Un entretien est ouvert. Relisez le poste avant le jour J. La synthèse ne répond pas à votre place.");
    box.innerHTML = tips.map(function (t) { return "<p>" + t + "</p>"; }).join("");
  });

  const exportBtn = document.getElementById("btn-export-data");
  if (exportBtn) {
    exportBtn.onclick = async () => {
      try {
        const data = await HireLinkAPI.api("/me/export");
        const blob = new Blob([JSON.stringify(data, null, 2)], { type: "application/json" });
        const a = document.createElement("a");
        a.href = URL.createObjectURL(blob);
        a.download = "hirelink-export-" + (session.email || "me") + ".json";
        a.click();
        HireLink.toast("Copie prête", "Le fichier de votre compte a été téléchargé.", "ok");
      } catch (err) {
        HireLink.toast("Export", err.message, "danger");
      }
    };
  }

  async function showSettingsCv(me) {
    const frame = document.getElementById("set-cv-frame");
    const empty = document.getElementById("set-cv-empty");
    if (!frame) return;
    if (!me || !me.cvFileName) {
      frame.hidden = true;
      if (empty) empty.hidden = false;
      return;
    }
    try {
      const token = HireLinkAPI.getToken();
      const res = await HireLinkAPI.authFetch("/me/cv", {
      });
      if (!res.ok) return;
      const url = URL.createObjectURL(await res.blob());
      frame.src = url;
      frame.hidden = false;
      if (empty) empty.hidden = true;
    } catch (_) {}
  }

  const setCvFile = document.getElementById("set-cv-file");
  if (setCvFile) {
    setCvFile.addEventListener("change", async function () {
      const file = setCvFile.files && setCvFile.files[0];
      if (!file) return;
      const fd = new FormData();
      fd.append("file", file);
      try {
        const token = HireLinkAPI.getToken();
        const res = await HireLinkAPI.authFetch("/me/cv", {
          method: "POST",
          body: fd,
        });
        if (!res.ok) throw new Error("Envoi impossible");
        HireLink.toast("CV", "Document enregistré", "ok");
        location.reload();
      } catch (err) {
        HireLink.toast("CV", err.message, "danger");
      }
    });
  }

  refreshBilling();
})();
