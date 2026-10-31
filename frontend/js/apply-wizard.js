/**
 * Parcours candidature animé — Offre → Compétences → CV → Lettre → Envoi
 */
(function (global) {
  const STEPS = [
    { id: "offer", label: "Offre" },
    { id: "skills", label: "Compétences" },
    { id: "cv", label: "CV" },
    { id: "letter", label: "Lettre" },
    { id: "review", label: "Envoi" },
  ];

  function esc(s) {
    return String(s == null ? "" : s)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;").replace(/'/g, "&#39;");
  }

  function ensureCss() {
    if (document.getElementById("hl-apply-css")) return;
    const s = document.createElement("style");
    s.id = "hl-apply-css";
    s.textContent =
      ".hl-apply-overlay{position:fixed;inset:0;z-index:9200;background:rgba(18,12,14,.55);backdrop-filter:blur(10px);display:flex;align-items:center;justify-content:center;padding:1rem;opacity:0;pointer-events:none;transition:opacity .28s ease}" +
      ".hl-apply-overlay.is-open{opacity:1;pointer-events:auto}" +
      ".hl-apply-card{background:#fff;border-radius:20px;width:min(760px,100%);max-height:min(92vh,900px);display:flex;flex-direction:column;box-shadow:0 32px 80px rgba(0,0,0,.32);border:1px solid rgba(122,32,50,.16);overflow:hidden;transform:translateY(16px) scale(.98);transition:transform .35s cubic-bezier(.22,1,.36,1)}" +
      ".hl-apply-overlay.is-open .hl-apply-card{transform:none}" +
      ".hl-apply-top{padding:1.2rem 1.4rem .9rem;border-bottom:1px solid rgba(26,18,20,.08);flex-shrink:0;background:linear-gradient(180deg,#fffaf7,#fff)}" +
      ".hl-apply-top h2{font-family:var(--font-display);margin:0 0 .2rem;font-size:1.4rem;color:#1a1214}" +
      ".hl-apply-top .sub{margin:0;color:#5e5558;font-size:.9rem}" +
      ".hl-apply-steps{display:flex;gap:.35rem;flex-wrap:wrap;margin-top:.9rem}" +
      ".hl-apply-step{flex:1;min-width:56px;text-align:center;font-size:.66rem;font-weight:700;letter-spacing:.04em;text-transform:uppercase;color:#8a8285;padding:.4rem .2rem;border-radius:999px;background:#f4f0ec;transition:all .25s ease}" +
      ".hl-apply-step.is-done{background:rgba(122,32,50,.12);color:#7a2032}" +
      ".hl-apply-step.is-current{background:linear-gradient(135deg,#7a2032,#e87030);color:#fff;box-shadow:0 6px 16px rgba(122,32,50,.28)}" +
      ".hl-apply-body{padding:1.2rem 1.4rem;overflow:auto;flex:1;min-height:0}" +
      ".hl-apply-foot{padding:1rem 1.4rem;border-top:1px solid rgba(26,18,20,.08);display:flex;justify-content:space-between;gap:.65rem;flex-wrap:wrap;background:#faf8f6;flex-shrink:0}" +
      ".hl-offer-detail h3{margin:0 0 .5rem;font-size:1.2rem}" +
      ".hl-offer-detail .chips{display:flex;flex-wrap:wrap;gap:.4rem;margin:.75rem 0}" +
      ".hl-offer-detail .desc{white-space:pre-wrap;line-height:1.55;color:#3a3335;font-size:.95rem;background:#f7f4f1;border-radius:12px;padding:1rem;border:1px solid rgba(26,18,20,.06);max-height:200px;overflow:auto}" +
      ".hl-cv-choice{display:grid;gap:.65rem}" +
      ".hl-cv-opt{display:flex;gap:.85rem;align-items:flex-start;padding:1rem;border-radius:14px;border:1.5px solid rgba(26,18,20,.1);cursor:pointer;transition:border-color .15s,background .15s,transform .15s}" +
      ".hl-cv-opt:hover{border-color:rgba(122,32,50,.35);transform:translateY(-1px)}" +
      ".hl-cv-opt.is-on{border-color:#7a2032;background:rgba(122,32,50,.06)}" +
      ".hl-cv-opt input{margin-top:.2rem}" +
      ".hl-cv-opt strong{display:block;margin-bottom:.2rem}" +
      ".hl-cv-opt span{font-size:.88rem;color:#5e5558}" +
      ".hl-file-row{margin-top:.75rem;padding:.85rem;border-radius:12px;background:#f7f4f1;border:1px dashed rgba(122,32,50,.3)}" +
      ".hl-review-list{display:grid;gap:.55rem}" +
      ".hl-review-list div{display:flex;justify-content:space-between;gap:1rem;padding:.65rem .75rem;border-radius:10px;background:#f7f4f1;font-size:.9rem}" +
      ".hl-review-list strong{color:#5e5558;font-weight:600}" +
      ".hl-optional{font-size:.78rem;font-weight:700;color:#e87030;text-transform:uppercase;letter-spacing:.05em}" +
      ".hl-progress{height:3px;background:rgba(122,32,50,.1);border-radius:99px;margin-top:.85rem;overflow:hidden}" +
      ".hl-progress>span{display:block;height:100%;background:linear-gradient(90deg,#7a2032,#e87030);border-radius:99px;transition:width .35s cubic-bezier(.22,1,.36,1)}";
    document.head.appendChild(s);
  }

  async function start(offer, onDone) {
    ensureCss();

    let profile = {};
    try {
      profile = await HireLinkAPI.api("/me");
    } catch (_) {}

    const state = {
      step: 0,
      dir: "fwd",
      offer,
      profile,
      onDone,
      cvSource: "PROFILE",
      cvFileName: "",
      cvFile: null,
      coverLetter: "",
      skillsCsv: profile.skills || "",
      submitting: false,
    };

    let overlay = document.getElementById("hl-apply-overlay");
    if (!overlay) {
      overlay = document.createElement("div");
      overlay.id = "hl-apply-overlay";
      overlay.className = "hl-apply-overlay";
      document.body.appendChild(overlay);
    }

    function close() {
      overlay.classList.remove("is-open");
    }

    function renderSteps() {
      return STEPS.map((s, i) => {
        let cls = "hl-apply-step";
        if (i < state.step) cls += " is-done";
        if (i === state.step) cls += " is-current";
        return '<span class="' + cls + '">' + s.label + "</span>";
      }).join("");
    }

    function bodyHtml() {
      const o = state.offer;
      const anim = "hl-wiz-panel" + (state.dir === "back" ? " is-back" : "");
      if (state.step === 0) {
        const skills = (o.requiredSkills || "")
          .split(",")
          .map((x) => x.trim())
          .filter(Boolean)
          .slice(0, 12);
        const match =
          o.matchScore != null ? Math.round(o.matchScore) + "% match" : "Matching à calculer";
        return (
          '<div class="' +
          anim +
          ' hl-offer-detail"><h3>' +
          esc(o.title) +
          "</h3>" +
          '<p class="muted" style="margin:0 0 .5rem">' +
          esc(o.recruiterName || "Entreprise") +
          " · " +
          esc(o.location || "—") +
          " · " +
          esc(o.contractType || "—") +
          "</p>" +
          '<div class="chips">' +
          '<span class="badge badge-rose">' +
          esc(match) +
          "</span>" +
          '<span class="badge badge-chrome">SLA ' +
          esc(o.responseSlaDays) +
          " j</span>" +
          skills.map((sk) => '<span class="badge badge-chrome">' + esc(sk) + "</span>").join("") +
          "</div>" +
          '<p class="muted" style="margin:.5rem 0 .35rem;font-size:.8rem;font-weight:700;text-transform:uppercase;letter-spacing:.06em">Description</p>' +
          '<div class="desc">' +
          esc(o.description || "Aucune description.") +
          "</div></div>"
        );
      }
      if (state.step === 1) {
        return (
          '<div class="' +
          anim +
          '"><p class="muted" style="margin-top:0">Vérifiez vos compétences et langues pour ce poste : tapez pour ajouter, même avec une faute.</p>' +
          '<div id="hl-apply-skills"></div></div>'
        );
      }
      if (state.step === 2) {
        const skills = state.skillsCsv || "Aucune compétence sélectionnée";
        return (
          '<div class="' +
          anim +
          '"><p class="muted" style="margin-top:0">Quel CV le recruteur verra-t-il ?</p>' +
          '<div class="hl-cv-choice">' +
          '<label class="hl-cv-opt' +
          (state.cvSource === "PROFILE" ? " is-on" : "") +
          '" data-src="PROFILE"><input type="radio" name="cvsrc" value="PROFILE"' +
          (state.cvSource === "PROFILE" ? " checked" : "") +
          " /><div><strong>Profil HireLink</strong><span>" +
          esc(skills) +
          "</span></div></label>" +
          '<label class="hl-cv-opt' +
          (state.cvSource === "UPLOAD" ? " is-on" : "") +
          '" data-src="UPLOAD"><input type="radio" name="cvsrc" value="UPLOAD"' +
          (state.cvSource === "UPLOAD" ? " checked" : "") +
          " /><div><strong>Nouveau CV</strong><span>PDF / DOC — analyse compétences optionnelle</span></div></label>" +
          "</div>" +
          '<div class="hl-file-row" id="hl-cv-upload"' +
          (state.cvSource === "UPLOAD" ? "" : " hidden") +
          ">" +
          '<label class="btn btn-soft btn-sm" style="cursor:pointer">Choisir un fichier' +
          '<input type="file" id="hl-cv-file" accept=".pdf,.txt,.md,.doc,.docx,application/pdf" hidden /></label>' +
          '<p class="muted" id="hl-cv-name" style="margin:.55rem 0 0">' +
          (state.cvFileName ? "Fichier : " + esc(state.cvFileName) : "Aucun fichier") +
          "</p></div>" +
          '<button type="button" class="btn btn-soft btn-sm" id="hl-cv-preview" style="margin-top:.75rem">Aperçu du CV</button></div>'
        );
      }
      if (state.step === 3) {
        return (
          '<div class="' +
          anim +
          '"><p class="muted" style="margin-top:0"><span class="hl-optional">Optionnel</span> — Un mot pour le recruteur (lettre de motivation)</p>' +
          '<textarea id="hl-letter-text" class="input" rows="7" maxlength="4000" placeholder="Pourquoi ce poste, pourquoi vous ?" style="width:100%">' + esc(state.coverLetter) + "</textarea>" +
          "</div>"
        );
      }
      return (
        '<div class="' +
        anim +
        '"><p class="muted" style="margin-top:0">Dernière vérif — réponse attendue sous <strong>' +
        esc(o.responseSlaDays) +
        " jours</strong>.</p>" +
        '<div class="hl-review-list">' +
        "<div><strong>Poste</strong><span>" +
        esc(o.title) +
        "</span></div>" +
        "<div><strong>Compétences et langues</strong><span>" +
        esc(state.skillsCsv || "—") +
        "</span></div>" +
        "<div><strong>CV</strong><span>" +
        (state.cvSource === "UPLOAD" ? "Nouveau · " + esc(state.cvFileName || "—") : "Profil HireLink") +
        "</span></div>" +
        "<div><strong>Lettre</strong><span>" +
        (state.coverLetter ? "Rédigée (" + state.coverLetter.length + " caractères)" : "Non jointe") +
        "</span></div></div></div>"
      );
    }

    function capture() {
      if (state.step === 3) {
        const ta = overlay.querySelector("#hl-letter-text");
        if (ta) state.coverLetter = ta.value.trim();
      }
    }

    function previewFile(file, title, fallbackHtml) {
      let body = fallbackHtml || "<p class='muted'>Aucun aperçu.</p>";
      let url = null;
      if (file && /pdf$/i.test(file.name || "")) {
        url = URL.createObjectURL(file);
        body = '<iframe src="' + url + '" title="Aperçu" style="width:100%;height:420px;border:0;border-radius:12px;background:#fff"></iframe>';
      } else if (file) {
        body = "<p><strong>" + esc(file.name) + "</strong></p><p class='muted'>Aperçu intégré pour les PDF. Le fichier DOCX est joint tel quel.</p>";
      }
      HireLink.openModal({
        title: title,
        bodyHtml: body,
        confirmLabel: "Fermer",
        hideCancel: true,
        large: true,
      }).then(() => {
        if (url) URL.revokeObjectURL(url);
      });
    }

    function bindBody() {
      if (state.step === 1) {
        HireLinkSkills.mount(overlay.querySelector("#hl-apply-skills"), {
          value: state.skillsCsv,
          onChange: (csv) => { state.skillsCsv = csv; },
        });
      }
      if (state.step === 2) {
        overlay.querySelectorAll(".hl-cv-opt").forEach((el) => {
          el.onclick = () => {
            state.cvSource = el.getAttribute("data-src");
            paint();
          };
        });
        const file = overlay.querySelector("#hl-cv-file");
        if (file) {
          file.onchange = async () => {
            const f = file.files && file.files[0];
            if (!f) return;
            state.cvFile = f;
            state.cvFileName = f.name;
            state.cvSource = "UPLOAD";
            paint();
            try {
              const fd = new FormData();
              fd.append("file", f);
              const token = HireLinkAPI.getToken();
              const res = await HireLinkAPI.authFetch("/ocr/cv", {
                method: "POST",
                body: fd,
              });
              const data = await res.json().catch(() => ({}));
              if (res.ok && data.suggestedSkills && data.suggestedSkills.length) {
                HireLink.toast("CV analysé", data.suggestedSkills.slice(0, 5).join(", "), "ok");
                state.skillsCsv = HireLinkSkills.split(state.skillsCsv).concat(data.suggestedSkills).filter((v, i, all) => all.findIndex((x) => HireLinkSearch.compact(x) === HireLinkSearch.compact(v)) === i).join(", ");
              }
            } catch (_) {}
          };
        }
        const prev = overlay.querySelector("#hl-cv-preview");
        if (prev) {
          prev.onclick = () => {
            if (state.cvSource === "UPLOAD" && state.cvFile) {
              previewFile(state.cvFile, "Aperçu du CV");
              return;
            }
            previewFile(
              null,
              "Profil HireLink",
              "<p>Le recruteur verra les compétences de votre profil.</p><p><strong>" +
                esc(state.skillsCsv || "Profil à compléter") +
                "</strong></p>"
            );
          };
        }
      }
    }

    function canGoNext() {
      if (state.step === 1) {
        capture();
        if (!state.skillsCsv || !state.skillsCsv.trim()) {
          HireLink.toast("Compétences", "Sélectionnez au moins une compétence", "warn");
          return false;
        }
      }
      if (state.step === 2 && state.cvSource === "UPLOAD" && !state.cvFileName) {
        HireLink.toast("CV requis", "Choisissez un fichier ou le profil", "warn");
        return false;
      }
      return true;
    }

    async function submit() {
      if (state.submitting) return;
      state.submitting = true;
      paint();
      try {
        if (state.skillsCsv) {
          try {
            await HireLinkAPI.api("/me", { method: "PATCH", body: { skills: state.skillsCsv } });
          } catch (_) {}
        }
        const created = await HireLinkAPI.api("/applications", {
          method: "POST",
          body: {
            jobOfferId: state.offer.id,
            cvSource: state.cvSource,
            cvFileName:
              state.cvSource === "UPLOAD"
                ? state.cvFileName
                : state.cvFileName || "Profil HireLink",
            coverLetter: state.coverLetter || null,
          },
        });
        if (state.cvSource === "UPLOAD" && state.cvFile && created && created.id) {
          const fd = new FormData();
          fd.append("file", state.cvFile);
          try {
            await HireLinkAPI.api("/applications/" + created.id + "/cv", { method: "POST", formData: fd });
          } catch (err) {
            HireLink.toast("CV", "Candidature envoyée, mais le fichier n'a pas pu être joint : " + err.message, "warn");
          }
        }
        close();
        HireLink.toast("Candidature envoyée", "Suivez-la dans Mes candidatures", "ok");
        if (typeof state.onDone === "function") state.onDone();
      } catch (err) {
        const raw = err.message || "Échec";
        const msg = /déjà existante/i.test(raw) ? "Vous avez déjà postulé à cette offre" : raw;
        HireLink.toast("Candidature", msg, "warn");
        state.submitting = false;
        paint();
      }
    }

    function paint() {
      const o = state.offer;
      const isLast = state.step === STEPS.length - 1;
      const pct = Math.round(((state.step + 1) / STEPS.length) * 100);
      overlay.innerHTML =
        '<div class="hl-apply-card" role="dialog" aria-modal="true">' +
        '<div class="hl-apply-top">' +
        "<h2>Postuler</h2>" +
        '<p class="sub">' +
        esc(o.title) +
        "</p>" +
        '<div class="hl-progress"><span style="width:' +
        pct +
        '%"></span></div>' +
        '<div class="hl-apply-steps">' +
        renderSteps() +
        "</div></div>" +
        '<div class="hl-apply-body">' +
        bodyHtml() +
        "</div>" +
        '<div class="hl-apply-foot">' +
        '<button type="button" class="btn btn-ghost" id="hl-apply-cancel">Annuler</button>' +
        "<div style='display:flex;gap:.5rem;flex-wrap:wrap'>" +
        (state.step > 0
          ? '<button type="button" class="btn btn-soft" id="hl-apply-back">Retour</button>'
          : "") +
        (state.step === 3
          ? '<button type="button" class="btn btn-ghost" id="hl-apply-skip">Passer</button>'
          : "") +
        '<button type="button" class="btn btn-primary" id="hl-apply-next"' +
        (state.submitting ? " disabled" : "") +
        ">" +
        (state.submitting ? "Envoi…" : isLast ? "Envoyer ma candidature" : "Continuer") +
        "</button></div></div></div>";

      overlay.querySelector("#hl-apply-cancel").onclick = close;
      const back = overlay.querySelector("#hl-apply-back");
      if (back) {
        back.onclick = () => {
          capture();
          state.dir = "back";
          state.step--;
          paint();
        };
      }
      const skip = overlay.querySelector("#hl-apply-skip");
      if (skip) {
        skip.onclick = () => {
          state.coverLetter = "";
          state.dir = "fwd";
          state.step++;
          paint();
        };
      }
      overlay.querySelector("#hl-apply-next").onclick = () => {
        capture();
        if (!canGoNext()) return;
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
      bindBody();
    }

    paint();
    requestAnimationFrame(() => overlay.classList.add("is-open"));
  }

  global.HireLinkApply = { start };
})(window);
