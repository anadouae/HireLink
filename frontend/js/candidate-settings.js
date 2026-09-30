(function () {
  const session = HireLinkShell.mountShell({ roles: ["CANDIDAT"], hello: false });
  if (!session) return;

  const NOTIF_KEY = "hirelink.notif.candidate";

  function refreshBilling() {
    const prem = HireLinkProduct && HireLinkProduct.isPremium("candidate");
    document.getElementById("plan-badge").textContent = prem ? "Premium" : "Essentiel";
    document.getElementById("plan-desc").textContent = prem
      ? "Premium actif — alertes, IA complète, priorité."
      : "Essentiel gratuit : candidatures, matching, OCR. Premium pour accélérer votre ROI perso.";
    document.getElementById("btn-billing-cancel").hidden = !prem;
  }

  HireLinkAPI.api("/me")
    .then((p) => {
      document.getElementById("set-name").value = p.fullName || "";
      document.getElementById("set-email").value = p.email || session.email || "";
    })
    .catch((err) => HireLink.toast("Compte", err.message, "danger"));

  document.getElementById("settings-account").addEventListener("submit", async (e) => {
    e.preventDefault();
    try {
      await HireLinkAPI.api("/me", {
        method: "PATCH",
        body: { fullName: document.getElementById("set-name").value },
      });
      HireLink.toast("Enregistré", "", "ok");
    } catch (err) {
      HireLink.toast("Erreur", err.message, "danger");
    }
  });

  const themeToggle = document.getElementById("theme-toggle");
  if (themeToggle && window.HireLinkTheme) {
    themeToggle.checked = HireLinkTheme.current() === "dark";
    themeToggle.addEventListener("change", () => HireLinkTheme.apply(themeToggle.checked ? "dark" : "light"));
  }

  document.getElementById("btn-billing-premium").onclick = () => {
    if (window.HireLinkGrowth) HireLinkGrowth.showPremium("candidate");
    setTimeout(refreshBilling, 400);
  };
  document.getElementById("btn-billing-cancel").onclick = () => {
    localStorage.removeItem("hirelink.premium.active.candidate");
    HireLink.toast("Plan", "Essentiel réactivé", "info");
    refreshBilling();
  };

  try {
    const n = JSON.parse(localStorage.getItem(NOTIF_KEY) || "{}");
    if (n.match === false) document.getElementById("n-match").checked = false;
    if (n.status === false) document.getElementById("n-status").checked = false;
    if (n.interview === false) document.getElementById("n-interview").checked = false;
  } catch (_) {}

  document.getElementById("btn-save-notif").onclick = () => {
    localStorage.setItem(
      NOTIF_KEY,
      JSON.stringify({
        match: document.getElementById("n-match").checked,
        status: document.getElementById("n-status").checked,
        interview: document.getElementById("n-interview").checked,
      })
    );
    HireLink.toast("Notifications", "OK", "ok");
  };

  HireLinkAPI.api("/reliability/me")
    .then((r) => {
      const b = document.getElementById("rel-badge");
      if (b) b.textContent = r.score + "/100 · " + r.label;
      const d = document.getElementById("rel-desc");
      if (d) d.textContent = "Fiabilité candidat — transparente, non bloquante.";
      const f = document.getElementById("rel-factors");
      if (f && r.factors) {
        f.textContent =
          typeof r.factors === "object" && !Array.isArray(r.factors)
            ? Object.entries(r.factors)
                .map(([k, v]) => k + " : " + v)
                .join(" · ")
            : String(r.factors);
      }
    })
    .catch(() => {});

  HireLinkAPI.api("/ai/credits", {
    headers: HireLinkProduct ? HireLinkProduct.premiumHeaders("candidate") : {},
  })
    .then((c) => {
      const badge = document.getElementById("credits-badge");
      const line = document.getElementById("credits-line");
      if (badge) badge.textContent = c.premium ? "∞ Premium" : c.remaining + "/" + c.limit;
      if (line) {
        line.textContent = c.premium
          ? "Crédits IA illimités (Premium)."
          : "Capital Free du mois : " + c.remaining + " restants sur " + c.limit + " (effet de dotation).";
      }
    })
    .catch(() => {});

  const refBtn = document.getElementById("btn-referral");
  if (refBtn) {
    refBtn.onclick = async () => {
      try {
        const data = await HireLinkAPI.api("/growth/referral-links", { method: "POST", body: {} });
        const el = document.getElementById("referral-url");
        const url = location.origin + location.pathname.replace(/app\/.*/, "") + (data.url || "");
        if (el) el.textContent = url;
        try {
          await navigator.clipboard.writeText(url);
          HireLink.toast("Parrainage", "Lien copié", "ok");
        } catch (_) {
          HireLink.toast("Parrainage", "Lien généré", "ok");
        }
      } catch (err) {
        HireLink.toast("Parrainage", err.message, "danger");
      }
    };
  }

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
        HireLink.toast("Export F21", "Fichier téléchargé", "ok");
      } catch (err) {
        HireLink.toast("Export", err.message, "danger");
      }
    };
  }

  const consentBtn = document.getElementById("btn-consent");
  if (consentBtn) {
    consentBtn.onclick = async () => {
      try {
        await HireLinkAPI.api("/me/consents", {
          method: "POST",
          body: { purpose: "recruitment", granted: true },
        });
        HireLink.toast("Consentement", "Enregistré", "ok");
      } catch (err) {
        HireLink.toast("Consentement", err.message, "danger");
      }
    };
  }

  HireLinkAPI.api("/notifications")
    .then((list) => {
      const box = document.getElementById("notif-inbox");
      if (!box) return;
      if (!list.length) {
        box.innerHTML = "<p class='muted'>Aucune notification in-app.</p>";
        return;
      }
      box.innerHTML =
        "<p class='muted' style='font-weight:600'>Boîte in-app (F19)</p>" +
        list
          .slice(0, 8)
          .map(
            (n) =>
              "<div class='ai-result' style='margin-bottom:0.4rem'><strong>" +
              (n.title || "") +
              "</strong><br/>" +
              (n.body || "") +
              "<br/><span class='muted' style='font-size:0.75rem'>" +
              (n.at || "") +
              (n.read ? "" : " · non lu") +
              "</span></div>"
          )
          .join("");
    })
    .catch(() => {});

  const params = new URLSearchParams(location.search);
  if (params.get("ref")) {
    try {
      localStorage.setItem("hirelink.referral.code", params.get("ref"));
    } catch (_) {}
  }

  refreshBilling();
  setInterval(refreshBilling, 2000);
})();
