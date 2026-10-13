/**
 * Assistant création d'offre — étapes animées + compétences / langues organisées
 */
(function (global) {
  const STEPS = [
    { id: "info", label: "Poste" },
    { id: "skills", label: "Compétences" },
    { id: "publish", label: "Publication" },
  ];

  function esc(s) {
    return String(s == null ? "" : s)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
  }

  function ensureShellCss() {
    if (document.getElementById("hl-offer-wiz-css")) return;
    const s = document.createElement("style");
    s.id = "hl-offer-wiz-css";
    s.textContent =
      ".hl-ow-overlay{position:fixed;inset:0;z-index:9300;background:rgba(18,12,14,.55);backdrop-filter:blur(8px);display:flex;align-items:center;justify-content:center;padding:1rem;opacity:0;pointer-events:none;transition:opacity .22s ease}" +
      ".hl-ow-overlay.is-open{opacity:1;pointer-events:auto}" +
      ".hl-ow-card{background:#fff;border-radius:18px;width:min(760px,100%);max-height:min(92vh,900px);display:flex;flex-direction:column;box-shadow:0 28px 70px rgba(0,0,0,.28);border:1px solid rgba(122,32,50,.18);overflow:hidden}" +
      ".hl-ow-top{padding:1.15rem 1.35rem .85rem;border-bottom:1px solid rgba(26,18,20,.08);flex-shrink:0}" +
      ".hl-ow-top h2{font-family:Fraunces,Georgia,serif;margin:0 0 .25rem;font-size:1.35rem}" +
      ".hl-ow-steps{display:flex;gap:.35rem;flex-wrap:wrap;margin-top:.85rem}" +
      ".hl-ow-step{flex:1;min-width:72px;text-align:center;font-size:.68rem;font-weight:700;letter-spacing:.04em;text-transform:uppercase;color:#8a8285;padding:.35rem .2rem;border-radius:999px;background:#f4f0ec}" +
      ".hl-ow-step.is-done{background:rgba(122,32,50,.12);color:#7a2032}" +
      ".hl-ow-step.is-current{background:linear-gradient(135deg,#7a2032,#e87030);color:#fff}" +
      ".hl-ow-body{padding:1.15rem 1.35rem;overflow:auto;flex:1;min-height:0}" +
      ".hl-ow-foot{padding:.95rem 1.35rem;border-top:1px solid rgba(26,18,20,.08);display:flex;justify-content:space-between;gap:.65rem;flex-wrap:wrap;background:#faf8f6;flex-shrink:0}";
    document.head.appendChild(s);
  }

  async function start(onDone, existing) {
    ensureShellCss();
    if (global.HireLinkSkills) HireLinkSkills.ensureCss();

    const state = {
      step: 0,
      dir: "fwd",
      editId: existing && existing.id ? existing.id : null,
      title: (existing && existing.title) || "",
      description: (existing && existing.description) || "",
      location: (existing && existing.location) || "Maroc · Hybride",
      contractType: (existing && existing.contractType) || "CDI",
      responseSlaDays: (existing && existing.responseSlaDays) || 7,
      published: existing ? !!existing.published : true,
      skillsCsv: (existing && existing.requiredSkills) || "Java, Spring Boot, MySQL",
      languagesCsv: "Français (Natif), Anglais (B2)",
      skillsApi: null,
      submitting: false,
    };

    let overlay = document.getElementById("hl-ow-overlay");
    if (!overlay) {
      overlay = document.createElement("div");
      overlay.id = "hl-ow-overlay";
      overlay.className = "hl-ow-overlay";
      document.body.appendChild(overlay);
    }

    function close() {
      overlay.classList.remove("is-open");
    }

    function stepsHtml() {
      return STEPS.map((s, i) => {
        let cls = "hl-ow-step";
        if (i < state.step) cls += " is-done";
        if (i === state.step) cls += " is-current";
        return '<span class="' + cls + '">' + s.label + "</span>";
      }).join("");
    }

    function bodyHtml() {
      const anim = "hl-wiz-panel" + (state.dir === "back" ? " is-back" : "");
      if (state.step === 0) {
        return (
          '<div class="' +
          anim +
          '"><div class="stack">' +
          '<div class="field"><label>Titre du poste *</label><input id="ow-title" value="' +
          esc(state.title) +
          '" placeholder="Ex. Développeur Full-Stack Java" /></div>' +
          '<div class="field"><label>Description *</label><textarea id="ow-desc" rows="4" placeholder="Missions, contexte, stack…">' +
          esc(state.description) +
          "</textarea></div>" +
          '<div class="row" style="gap:.75rem;flex-wrap:wrap">' +
          '<div class="field" style="flex:1;min-width:140px"><label>Lieu</label><input id="ow-loc" value="' +
          esc(state.location) +
          '" /></div>' +
          '<div class="field" style="flex:1;min-width:120px"><label>Contrat</label><select id="ow-contract">' +
          ["CDI", "CDD", "Stage", "Alternance", "PFE", "Freelance"]
            .map(
              (c) =>
                '<option value="' +
                c +
                '"' +
                (state.contractType === c ? " selected" : "") +
                ">" +
                c +
                "</option>"
            )
            .join("") +
          "</select></div>" +
          '<div class="field" style="flex:1;min-width:100px"><label>SLA (jours)</label><input id="ow-sla" type="number" min="1" max="60" value="' +
          state.responseSlaDays +
          '" /></div></div>' +
          '<p class="muted" style="margin:0;font-size:.85rem">Le délai de réponse (SLA) est visible du candidat.</p></div></div>'
        );
      }
      if (state.step === 1) {
        return (
          '<div class="' +
          anim +
          '"><p class="muted" style="margin-top:0">Sélectionnez les compétences et langues requises — catalogue par domaine (comme LinkedIn).</p>' +
          '<div id="ow-skills-root"></div></div>'
        );
      }
      return (
        '<div class="' +
        anim +
        '"><div class="hl-review-list" style="margin-bottom:1rem">' +
        "<div><strong>Poste</strong><span>" +
        esc(state.title || "—") +
        "</span></div>" +
        "<div><strong>Lieu / contrat</strong><span>" +
        esc(state.location) +
        " · " +
        esc(state.contractType) +
        "</span></div>" +
        "<div><strong>SLA</strong><span>" +
        state.responseSlaDays +
        " jours</span></div>" +
        "<div><strong>Compétences</strong><span>" +
        esc(state.skillsCsv || "—") +
        "</span></div>" +
        "<div><strong>Langues</strong><span>" +
        esc(state.languagesCsv || "—") +
        "</span></div></div>" +
        '<label class="row" style="gap:.5rem;align-items:center"><input id="ow-pub" type="checkbox"' +
        (state.published ? " checked" : "") +
        " /> Publier immédiatement</label>" +
        '<p class="muted" style="font-size:.85rem;margin:.5rem 0 0">Entreprise vérifiée requise pour publier.</p></div>'
      );
    }

    function captureStep() {
      if (state.step === 0) {
        const t = overlay.querySelector("#ow-title");
        const d = overlay.querySelector("#ow-desc");
        const l = overlay.querySelector("#ow-loc");
        const c = overlay.querySelector("#ow-contract");
        const s = overlay.querySelector("#ow-sla");
        if (t) state.title = t.value.trim();
        if (d) state.description = d.value.trim();
        if (l) state.location = l.value.trim();
        if (c) state.contractType = c.value;
        if (s) state.responseSlaDays = Number(s.value || 7);
      }
      if (state.step === 1 && state.skillsApi) {
        state.skillsCsv = state.skillsApi.getSkillsCsv();
        state.languagesCsv = state.skillsApi.getLanguagesCsv();
      }
      if (state.step === 2) {
        const p = overlay.querySelector("#ow-pub");
        if (p) state.published = p.checked;
      }
    }

    function canNext() {
      if (state.step === 0) {
        if (!state.title) {
          HireLink.toast("Titre requis", "", "warn");
          return false;
        }
        if (!state.description) {
          HireLink.toast("Description requise", "", "warn");
          return false;
        }
      }
      if (state.step === 1) {
        if (!state.skillsCsv || !state.skillsCsv.trim()) {
          HireLink.toast("Compétences", "Sélectionnez au moins une compétence", "warn");
          return false;
        }
      }
      return true;
    }

    async function submit() {
      if (state.submitting) return;
      state.submitting = true;
      paint();
      const requiredSkills = [state.skillsCsv, state.languagesCsv].filter(Boolean).join(", ");
      try {
        const lang = await HireLinkAPI.api("/ai/language-check", {
          method: "POST",
          body: { title: state.title, description: state.description },
        });
        if (lang.flagged) {
          const cont = await HireLink.openModal({
            title: "Formulation à revoir",
            description: lang.summary,
            bodyHtml:
              "<ul>" +
              (lang.suggestions || []).map((x) => "<li>" + esc(x) + "</li>").join("") +
              "</ul>",
            confirmLabel: "Publier quand même",
            cancelLabel: "Corriger",
            large: true,
          });
          if (!cont) {
            state.submitting = false;
            state.step = 0;
            paint();
            return;
          }
        }
        await HireLinkAPI.api(state.editId ? "/offers/" + state.editId : "/offers", {
          method: state.editId ? "PUT" : "POST",
          body: {
            title: state.title,
            description: state.description,
            requiredSkills,
            location: state.location,
            contractType: state.contractType,
            responseSlaDays: state.responseSlaDays,
            published: state.published,
          },
        });
        close();
        HireLink.toast(
          state.editId ? "Offre mise à jour" : "Offre créée",
          state.published ? "Publiée" : "Brouillon",
          "ok"
        );
        if (typeof onDone === "function") onDone();
      } catch (err) {
        HireLink.toast("Erreur", err.message, "danger");
        state.submitting = false;
        paint();
      }
    }

    function paint() {
      const isLast = state.step === STEPS.length - 1;
      overlay.innerHTML =
        '<div class="hl-ow-card" role="dialog" aria-modal="true">' +
        '<div class="hl-ow-top"><h2>' +
        (state.editId ? "Modifier l’offre" : "Nouvelle offre") +
        "</h2>" +
        '<p class="muted" style="margin:0;font-size:.9rem">Étape ' +
        (state.step + 1) +
        " / " +
        STEPS.length +
        '</p><div class="hl-ow-steps">' +
        stepsHtml() +
        "</div></div>" +
        '<div class="hl-ow-body">' +
        bodyHtml() +
        "</div>" +
        '<div class="hl-ow-foot">' +
        '<button type="button" class="btn btn-ghost" id="ow-cancel">Annuler</button>' +
        "<div style='display:flex;gap:.5rem'>" +
        (state.step > 0 ? '<button type="button" class="btn btn-soft" id="ow-back">Retour</button>' : "") +
        '<button type="button" class="btn btn-primary" id="ow-next"' +
        (state.submitting ? " disabled" : "") +
        ">" +
        (state.submitting
          ? state.editId
            ? "Enregistrement…"
            : "Création…"
          : isLast
            ? state.editId
              ? "Enregistrer"
              : "Créer l’offre"
            : "Continuer") +
        "</button></div></div></div>";

      overlay.querySelector("#ow-cancel").onclick = close;
      const back = overlay.querySelector("#ow-back");
      if (back) {
        back.onclick = () => {
          captureStep();
          state.dir = "back";
          state.step--;
          paint();
        };
      }
      overlay.querySelector("#ow-next").onclick = () => {
        captureStep();
        if (!canNext()) return;
        if (isLast) submit();
        else {
          state.dir = "fwd";
          state.step++;
          paint();
        }
      };
      overlay.onclick = (e) => {
        if (e.target === overlay) close();
      };

      if (state.step === 1 && global.HireLinkSkills) {
        const root = overlay.querySelector("#ow-skills-root");
        const preSkills = (state.skillsCsv || "")
          .split(",")
          .map((x) => x.trim())
          .filter(Boolean);
        const preLangs = (state.languagesCsv || "")
          .split(",")
          .map((x) => x.trim())
          .filter(Boolean)
          .map((chunk) => {
            const m = chunk.match(/^(.+?)\s*\(([^)]+)\)\s*$/);
            return m ? { name: m[1].trim(), level: m[2].trim() } : { name: chunk, level: "B2" };
          });
        state.skillsApi = HireLinkSkills.mount(root, {
          selected: preSkills,
          languages: preLangs,
          onChange: (v) => {
            state.skillsCsv = v.skillsCsv;
            state.languagesCsv = v.languagesCsv;
          },
        });
      }
    }

    paint();
    overlay.classList.add("is-open");
  }

  global.HireLinkOfferWizard = {
    start,
    edit: function (offer, onDone) {
      return start(onDone, offer);
    },
  };
})(window);
