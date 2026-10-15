(function () {
  const session = HireLinkShell.mountShell({ roles: ["CANDIDAT"], hello: false });
  if (!session) return;
  if (window.HireLinkProduct) HireLinkProduct.mountPremiumBar("candidate");

  let skillsApi = null;
  let dirty = false;
  const saveBar = document.getElementById("profile-save-bar");

  function setDirty(v) {
    dirty = !!v;
    if (saveBar) saveBar.classList.toggle("is-clean", !dirty);
  }

  function bindDirty() {
    ["pf-roles", "pf-loc", "pf-contracts", "pf-level"].forEach((id) => {
      const node = document.getElementById(id);
      if (node) node.addEventListener("input", () => setDirty(true));
      if (node) node.addEventListener("change", () => setDirty(true));
    });
  }

  async function showAi(endpoint, body) {
    const box = document.getElementById("ai-result");
    box.hidden = false;
    box.textContent = "Génération…";
    try {
      const premium = HireLinkProduct && HireLinkProduct.isPremium("candidate");
      const data = await HireLinkAPI.api(endpoint, {
        method: "POST",
        headers: HireLinkProduct ? HireLinkProduct.premiumHeaders("candidate") : {},
        body: Object.assign({}, body, { premium }),
      });
      box.textContent = (data.title ? data.title + "\n\n" : "") + (data.content || "");
      if (data.tips && data.tips.length) {
        box.textContent += "\n\n— Tips —\n• " + data.tips.join("\n• ");
      }
      if (data.premiumLocked && window.HireLinkGrowth) {
        HireLink.toast("Aperçu freemium", "Premium débloque le script complet", "info");
      }
    } catch (err) {
      box.textContent = err.message;
      HireLink.toast("IA", err.message, "danger");
    }
  }

  function mountSkills(csv) {
    const root = document.getElementById("pf-skills-picker");
    const parts = (csv || "")
      .split(",")
      .map((x) => x.trim())
      .filter(Boolean);
    const langs = [];
    const skills = [];
    const knownLangs = new Set(
      (window.HireLinkSkills && HireLinkSkills.LANGUAGES) || ["Français", "Arabe", "Anglais"]
    );
    parts.forEach((p) => {
      const m = p.match(/^(.+?)\s*\(([^)]+)\)\s*$/);
      const name = m ? m[1].trim() : p;
      if (knownLangs.has(name) || m) {
        langs.push({ name, level: m ? m[2].trim() : "B2" });
      } else {
        skills.push(name);
      }
    });
    if (window.HireLinkSkills) {
      skillsApi = HireLinkSkills.mount(root, {
        selected: skills,
        languages: langs.length ? langs : [],
        onChange: (v) => {
          document.getElementById("pf-skills").value = [v.skillsCsv, v.languagesCsv]
            .filter(Boolean)
            .join(", ");
          setDirty(true);
        },
      });
    }
  }

  async function load() {
    try {
      const p = await HireLinkAPI.api("/me");
      document.getElementById("pf-skills").value = p.skills || "";
      mountSkills(p.skills || "");
      document.getElementById("pf-roles").value = p.soughtRoles || "";
      document.getElementById("pf-loc").value = p.preferredLocations || "";
      document.getElementById("pf-contracts").value = p.preferredContracts || "";
      document.getElementById("pf-level").value = p.experienceLevel || "";
      setDirty(false);
    } catch (err) {
      HireLink.toast("Profil", err.message, "danger");
    }
  }

  document.getElementById("profile-form").addEventListener("submit", async (e) => {
    e.preventDefault();
    const skills =
      (skillsApi && skillsApi.getCombinedCsv()) ||
      document.getElementById("pf-skills").value;
    const btn = document.getElementById("btn-save-profile");
    if (btn) btn.disabled = true;
    try {
      await HireLinkAPI.api("/me", {
        method: "PATCH",
        body: {
          skills,
          soughtRoles: document.getElementById("pf-roles").value,
          preferredLocations: document.getElementById("pf-loc").value,
          preferredContracts: document.getElementById("pf-contracts").value,
          experienceLevel: document.getElementById("pf-level").value,
        },
      });
      setDirty(false);
      HireLink.toast("Profil enregistré", "Vos offres seront mises à jour", "ok");
    } catch (err) {
      HireLink.toast("Erreur", err.message, "danger");
    } finally {
      if (btn) btn.disabled = false;
    }
  });

  document.getElementById("cv-file").addEventListener("change", async (e) => {
    const file = e.target.files && e.target.files[0];
    if (!file) return;
    const fd = new FormData();
    fd.append("file", file);
    try {
      const token = HireLinkAPI.getToken();
      const res = await fetch(HireLinkAPI.API_BASE + "/ocr/cv", {
        method: "POST",
        headers: token ? { Authorization: "Bearer " + token } : {},
        body: fd,
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.message || data.error || "Import échoué");
      const skills = (data.suggestedSkills || []).join(", ");
      if (skills) {
        const current = document.getElementById("pf-skills").value;
        const merged = Array.from(
          new Set(
            (current + "," + skills)
              .split(",")
              .map((x) => x.trim())
              .filter(Boolean)
          )
        ).join(", ");
        document.getElementById("pf-skills").value = merged;
        mountSkills(merged);
        await HireLinkAPI.api("/me", { method: "PATCH", body: { skills: merged } });
      }
      HireLink.toast("CV importé", (data.suggestedSkills || []).slice(0, 5).join(", "), "ok");
    } catch (err) {
      HireLink.toast("Import CV", err.message, "danger");
    }
  });

  const prem = document.getElementById("btn-premium");
  if (prem) prem.onclick = () => HireLinkGrowth && HireLinkGrowth.showPremium("candidate");

  document.getElementById("btn-ai-rewrite").onclick = () =>
    showAi("/ai/profile-rewrite", {
      skills: document.getElementById("pf-skills").value,
      roles: document.getElementById("pf-roles").value,
      level: document.getElementById("pf-level").value,
    });
  document.getElementById("btn-ai-interview").onclick = () =>
    showAi("/ai/interview-prep", {
      jobTitle: document.getElementById("pf-roles").value || "Poste cible",
      skills: document.getElementById("pf-skills").value,
    });

  bindDirty();
  setDirty(false);
  load();
})();
