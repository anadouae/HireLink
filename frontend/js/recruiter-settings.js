(function () {
  const session = HireLinkShell.mountShell({ roles: ["RECRUTEUR"], hello: false });
  if (!session) return;

  const NOTIF_KEY = "hirelink.notif.recruiter";

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
    if (btn) btn.click();
  }

  function refreshBilling() {
    const prem = HireLinkProduct && HireLinkProduct.isPremium("recruiter");
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
      document.getElementById("set-name").value = p.fullName || "";
      document.getElementById("set-company").value = p.company || "";
      document.getElementById("set-ice").value = p.companyIce || "";
      const userId = p.id || session.userId || session.id || "me";
      const photoKey = "hirelink.photo." + userId;
      const photo = document.getElementById("pf-photo");
      const fallback = document.getElementById("pf-photo-fallback");
      const parts = String(p.fullName || "R").trim().split(/\s+/).filter(Boolean);
      const letters = ((parts[0] || "R")[0] + (parts[1] ? parts[1][0] : "")).toUpperCase();
      function paint(url) {
        const avatar = document.getElementById("avatar");
        if (url && photo) {
          photo.src = url;
          photo.hidden = false;
          if (fallback) fallback.hidden = true;
          if (avatar) {
            avatar.textContent = "";
            avatar.classList.add("has-art");
            avatar.style.backgroundImage = "url(" + url + ")";
            avatar.style.backgroundSize = "cover";
            avatar.style.backgroundPosition = "center";
          }
        } else {
          if (photo) photo.hidden = true;
          if (fallback) {
            fallback.hidden = false;
            fallback.textContent = letters;
          }
        }
      }
      let stored = "";
      try { stored = localStorage.getItem(photoKey) || ""; } catch (_) {}
      paint(stored);
      const file = document.getElementById("pf-photo-file");
      const open = () => file && file.click();
      const btn = document.getElementById("btn-photo");
      if (btn) btn.addEventListener("click", open);
      if (file) {
        file.addEventListener("change", () => {
          const picked = file.files && file.files[0];
          if (!picked) return;
          const reader = new FileReader();
          reader.onload = () => {
            const img = new Image();
            img.onload = () => {
              const size = 320;
              const canvas = document.createElement("canvas");
              canvas.width = size;
              canvas.height = size;
              const ctx = canvas.getContext("2d");
              const side = Math.min(img.width, img.height);
              ctx.drawImage(img, (img.width - side) / 2, (img.height - side) / 2, side, side, 0, 0, size, size);
              const url = canvas.toDataURL("image/jpeg", 0.82);
              try { localStorage.setItem(photoKey, url); } catch (_) {
                HireLink.toast("Photo", "Image trop lourde pour ce navigateur", "danger");
                return;
              }
              paint(url);
            };
            img.src = reader.result;
          };
          reader.readAsDataURL(picked);
        });
      }
      if (location.hash === "#photo-upload") {
        const node = document.getElementById("photo-upload");
        if (node) node.scrollIntoView({ behavior: "smooth", block: "start" });
      }
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
  HireLinkAPI.api("/applications/pipeline")
    .then((apps) => {
      const open = (apps || []).filter((a) => !["REJECTED", "WITHDRAWN"].includes(a.status));
      const late = open.filter((a) => a.slaRemainingDays != null && a.slaRemainingDays < 0);
      const note = document.getElementById("cabinet-advice");
      const side = document.getElementById("side-advice-text");
      const line = late.length
        ? late.length + " dossier" + (late.length > 1 ? "s ont" : " a") + " dépassé le délai publié. Ouvrez-les avant le reste. Le score de compatibilité aide à lire, il ne retient personne."
        : "Aucun délai dépassé. Gardez la file triée par échéance : le candidat voit la date que vous avez promise.";
      if (note) note.textContent = line;
      if (side) side.textContent = late.length
        ? "Ce mois-ci : " + late.length + " dossier" + (late.length > 1 ? "s" : "") + " hors délai. Traitez-les en premier."
        : "Ce mois-ci : les délais promis sont tenus. La lecture d’un CV ne remplace pas votre décision.";
    })
    .catch(() => {});

  refreshBilling();
  if (location.hash === "#plans") openTab("plans");
  window.addEventListener("hashchange", function () {
    if (location.hash === "#plans") openTab("plans");
  });
})();
