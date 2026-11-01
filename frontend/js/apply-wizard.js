/**
 * Parcours candidature animé — Offre → Compétences → CV → Lettre → Disponibilité → Envoi
 */
(function (global) {
  const STEPS = [
    { id: "offer", label: "Offre" },
    { id: "skills", label: "Compétences" },
    { id: "cv", label: "CV" },
    { id: "letter", label: "Lettre" },
    { id: "avail", label: "Disponibilité" },
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
      ".hl-cv-opt.is-disabled{opacity:.55;cursor:not-allowed}" +
      ".hl-cv-opt.is-disabled:hover{transform:none;border-color:rgba(26,18,20,.1)}" +
      ".hl-cv-opt input{margin-top:.2rem}" +
      ".hl-cv-opt strong{display:block;margin-bottom:.2rem}" +
      ".hl-cv-opt span{font-size:.88rem;color:#5e5558}" +
      ".hl-file-row{margin-top:.75rem;padding:.85rem;border-radius:12px;background:#f7f4f1;border:1px dashed rgba(122,32,50,.3)}" +
      ".hl-review-list{display:grid;gap:.55rem}" +
      ".hl-review-list div{display:flex;justify-content:space-between;gap:1rem;padding:.65rem .75rem;border-radius:10px;background:#f7f4f1;font-size:.9rem}" +
      ".hl-review-list strong{color:#5e5558;font-weight:600}" +
      ".hl-optional{font-size:.78rem;font-weight:700;color:#e87030;text-transform:uppercase;letter-spacing:.05em}" +
      ".hl-progress{height:3px;background:rgba(122,32,50,.1);border-radius:99px;margin-top:.85rem;overflow:hidden}" +
      ".hl-av-grid{display:grid;grid-template-columns:1fr 1fr;gap:.9rem}.hl-av-full{grid-column:1/-1}.hl-av-grid fieldset{border:0;margin:0;padding:0}.hl-av-grid legend,.hl-av-f>span{display:block;font-weight:600;font-size:.86rem;margin-bottom:.35rem}.hl-av-f small{font-weight:400;color:#8a8285}.hl-av-f .input,.hl-av-f select{width:100%}.hl-av-f input[type=number]{box-sizing:border-box;height:2.6rem;padding:0 .85rem;border:1px solid var(--line);border-radius:12px;background:var(--surface,#fff);color:var(--ink);font:inherit;-moz-appearance:textfield}.hl-av-f input[type=number]:focus{outline:none;border-color:var(--hl-wine);box-shadow:0 0 0 3px rgba(122,32,50,.15)}" +
      ".hl-seg{display:inline-flex;gap:.3rem;padding:.25rem;border-radius:999px;background:#f4f0ec}.hl-seg input{position:absolute;opacity:0;pointer-events:none}.hl-seg span{display:block;padding:.45rem .95rem;border-radius:999px;font-size:.86rem;cursor:pointer;transition:all .2s}.hl-seg input:checked+span{background:#7a2032;color:#fff;box-shadow:0 4px 12px rgba(122,32,50,.25)}.hl-seg input:focus-visible+span{outline:2px solid #c9a66b}" +
      ".hl-av-check{display:flex;gap:.6rem;align-items:center;font-size:.9rem;padding:.7rem .85rem;border-radius:12px;background:#f7f4f1;cursor:pointer}" +
      "@media(max-width:600px){.hl-av-grid{grid-template-columns:1fr}}" +
      "html[data-theme=dark] .hl-seg,html[data-theme=dark] .hl-av-check{background:rgba(255,255,255,.06)}" +
      ".hl-progress>span{display:block;height:100%;background:linear-gradient(90deg,#7a2032,#e87030);border-radius:99px;transition:width .35s cubic-bezier(.22,1,.36,1)}";
    document.head.appendChild(s);
  }

  /* Disponibilité : brouillon par offre, sinon dernières préférences du candidat. */
  const NOTICE = ["Aucun", "1 semaine", "2 semaines", "1 mois", "2 mois", "3 mois"];
  const MODES = ["Indifférent", "Sur site", "Hybride", "Télétravail"];
  const SOURCES = ["Site HireLink", "LinkedIn", "Recommandation", "Site de l’entreprise", "Salon ou école", "Autre"];
  function loadDetails(offer) {
    const base = { availability: "NOW", availableFrom: "", notice: "Aucun", salary: "", workMode: "Indifférent", relocation: false, source: "Site HireLink" };
    try {
      const draft = JSON.parse(localStorage.getItem("hirelink.apply.draft." + offer.id) || "null");
      if (draft) return Object.assign(base, draft);
      const prefs = JSON.parse(localStorage.getItem("hirelink.apply.prefs") || "null");
      if (prefs) return Object.assign(base, prefs, { source: base.source });
    } catch (_) {}
    return base;
  }
  function saveDraft(state) {
    try { localStorage.setItem("hirelink.apply.draft." + state.offer.id, JSON.stringify(state.details)); } catch (_) {}
  }
  function opts(list, cur) { return list.map((v) => '<option' + (v === cur ? " selected" : "") + ">" + esc(v) + "</option>").join(""); }
  function availText(d) {
    if (d.availability === "NOW") return "Immédiatement";
    if (!d.availableFrom) return "À préciser";
    const p = d.availableFrom.split("-");
    return "À partir du " + new Date(+p[0], +p[1] - 1, +p[2]).toLocaleDateString("fr-FR", { day: "numeric", month: "long", year: "numeric" });
  }

  async function start(offer, onDone) {
    ensureCss();

    let profile = {};
    try {
      profile = await HireLinkAPI.api("/me");
    } catch (_) {}

    const hasProfileCv = !!(profile.cvFileName && String(profile.cvFileName).trim());
    const state = {
      step: 0,
      dir: "fwd",
      offer,
      profile,
      onDone,
      hasProfileCv,
      cvSource: hasProfileCv ? "PROFILE" : "UPLOAD",
      cvFileName: hasProfileCv ? profile.cvFileName : "",
      cvFile: null,
      coverLetter: "",
      letterFile: null,
      letterFileName: "",
      skillsCsv: profile.skills || "",
      submitting: false,
      details: loadDetails(offer),
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
        const profileHint = state.hasProfileCv
          ? ("CV enregistré : " + (state.profile.cvFileName || "fichier") + " — compétences : " + skills)
          : "Aucun CV dans votre profil. Déposez-en un dans Mon profil, ou choisissez « Nouveau CV ».";
        return (
          '<div class="' +
          anim +
          '"><p class="muted" style="margin-top:0">Quel CV le recruteur verra-t-il ?</p>' +
          '<div class="hl-cv-choice">' +
          '<label class="hl-cv-opt' +
          (state.cvSource === "PROFILE" ? " is-on" : "") +
          (state.hasProfileCv ? "" : " is-disabled") +
          '" data-src="PROFILE"><input type="radio" name="cvsrc" value="PROFILE"' +
          (state.cvSource === "PROFILE" ? " checked" : "") +
          (state.hasProfileCv ? "" : " disabled") +
          " /><div><strong>Profil HireLink</strong><span>" +
          esc(profileHint) +
          "</span></div></label>" +
          '<label class="hl-cv-opt' +
          (state.cvSource === "UPLOAD" ? " is-on" : "") +
          '" data-src="UPLOAD"><input type="radio" name="cvsrc" value="UPLOAD"' +
          (state.cvSource === "UPLOAD" ? " checked" : "") +
          " /><div><strong>Nouveau CV</strong><span>PDF ou TXT — analyse des compétences optionnelle</span></div></label>" +
          "</div>" +
          '<div class="hl-file-row" id="hl-cv-upload"' +
          (state.cvSource === "UPLOAD" ? "" : " hidden") +
          ">" +
          '<label class="btn btn-soft btn-sm" style="cursor:pointer">Choisir un fichier' +
          '<input type="file" id="hl-cv-file" accept=".pdf,.txt,.md,application/pdf,text/plain" hidden /></label>' +
          '<p class="muted" id="hl-cv-name" style="margin:.55rem 0 0">' +
          (state.cvFileName ? "Fichier : " + esc(state.cvFileName) : "Aucun fichier") +
          "</p></div>" +
          '<button type="button" class="btn btn-soft btn-sm" id="hl-cv-preview" style="margin-top:.75rem"' +
          (state.cvSource === "PROFILE" && !state.hasProfileCv ? " disabled" : "") +
          ">Aperçu du CV</button></div>"
        );
      }
      if (state.step === 3) {
        return (
          '<div class="' +
          anim +
          '"><p class="muted" style="margin-top:0"><span class="hl-optional">Optionnel</span> — Lettre de motivation : rédigez-la ou importez un fichier (PDF, TXT, MD)</p>' +
          '<textarea id="hl-letter-text" class="input" rows="7" maxlength="8000" placeholder="Pourquoi ce poste, pourquoi vous ?" style="width:100%">' +
          esc(state.coverLetter) +
          "</textarea>" +
          '<div class="hl-file-row" style="margin-top:.75rem">' +
          '<label class="btn btn-soft btn-sm" style="cursor:pointer">Importer une lettre' +
          '<input type="file" id="hl-letter-file" accept=".pdf,.txt,.md,.text,application/pdf,text/plain,text/markdown" hidden /></label>' +
          '<button type="button" class="btn btn-ghost btn-sm" id="hl-letter-preview" style="margin-left:.5rem"' +
          (state.letterFile || state.coverLetter ? "" : " disabled") +
          ">Aperçu</button>" +
          '<p class="muted" id="hl-letter-name" style="margin:.55rem 0 0">' +
          (state.letterFileName
            ? "Fichier : " + esc(state.letterFileName) + " (texte importé dans la zone ci-dessus)"
            : "Formats : PDF, TXT, MD — ou rédigez directement") +
          "</p></div></div>"
        );
      }
      if (state.step === 4) {
        const d = state.details;
        const today = new Date(); const iso = today.getFullYear() + "-" + String(today.getMonth() + 1).padStart(2, "0") + "-" + String(today.getDate()).padStart(2, "0");
        return '<div class="' + anim + '"><p class="muted" style="margin-top:0">Ces informations aident le recruteur à vous proposer le bon calendrier. Elles sont mémorisées pour vos prochaines candidatures.</p>' +
          '<div class="hl-av-grid">' +
          '<fieldset class="hl-av-full"><legend>Disponibilité</legend><div class="hl-seg">' +
          '<label><input type="radio" name="hl-av" value="NOW"' + (d.availability === "NOW" ? " checked" : "") + "/><span>Immédiatement</span></label>" +
          '<label><input type="radio" name="hl-av" value="DATE"' + (d.availability === "DATE" ? " checked" : "") + "/><span>À partir d’une date</span></label></div>" +
          '<div id="hl-av-date-wrap"' + (d.availability === "DATE" ? "" : " hidden") + ' style="margin-top:.6rem"><input type="date" id="hl-av-date" class="input" min="' + iso + '" value="' + esc(d.availableFrom) + '"/></div></fieldset>' +
          '<label class="hl-av-f"><span>Préavis</span><select id="hl-av-notice" class="input">' + opts(NOTICE, d.notice) + "</select></label>" +
          '<label class="hl-av-f"><span>Prétentions salariales <small>(MAD net / mois, facultatif)</small></span><input type="number" id="hl-av-salary" class="input" min="0" max="500000" step="500" inputmode="numeric" placeholder="ex. 12 000" value="' + esc(d.salary) + '"/></label>' +
          '<label class="hl-av-f"><span>Mode de travail souhaité</span><select id="hl-av-mode" class="input">' + opts(MODES, d.workMode) + "</select></label>" +
          '<label class="hl-av-f"><span>Comment avez-vous connu l’offre ?</span><select id="hl-av-source" class="input">' + opts(SOURCES, d.source) + "</select></label>" +
          '<label class="hl-av-check hl-av-full"><input type="checkbox" id="hl-av-reloc"' + (d.relocation ? " checked" : "") + "/><span>Je suis prêt(e) à déménager pour ce poste" +
          (o.location ? " (" + esc(String(o.location).split(/[·,]/)[0].trim()) + ")" : "") + "</span></label>" +
          "</div></div>";
      }
      const dd = state.details;
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
        (state.coverLetter
          ? "Oui (" + state.coverLetter.length + " car." + (state.letterFileName ? " · " + state.letterFileName : "") + ")"
          : "Non jointe") +
        "</span></div>" +
        "<div><strong>Disponibilité</strong><span>" + esc(availText(dd)) + (dd.notice && dd.notice !== "Aucun" ? " · préavis " + esc(dd.notice) : "") + "</span></div>" +
        (dd.salary ? "<div><strong>Prétentions</strong><span>" + esc(Number(dd.salary).toLocaleString("fr-FR")) + " MAD / mois</span></div>" : "") +
        "<div><strong>Mode de travail</strong><span>" + esc(dd.workMode) + (dd.relocation ? " · mobile" : "") + "</span></div>" +
        "</div></div>"
      );
    }

    function capture() {
      if (state.step === 3) {
        const ta = overlay.querySelector("#hl-letter-text");
        if (ta) state.coverLetter = ta.value.trim();
      }
      if (state.step === 4 && overlay.querySelector("#hl-av-notice")) {
        const d = state.details;
        const r = overlay.querySelector('input[name="hl-av"]:checked');
        d.availability = r ? r.value : "NOW";
        d.availableFrom = (overlay.querySelector("#hl-av-date") || {}).value || "";
        d.notice = overlay.querySelector("#hl-av-notice").value;
        const sal = String(overlay.querySelector("#hl-av-salary").value || "").replace(/\D/g, "");
        d.salary = sal ? String(Math.min(500000, Number(sal))) : "";
        d.workMode = overlay.querySelector("#hl-av-mode").value;
        d.source = overlay.querySelector("#hl-av-source").value;
        d.relocation = !!overlay.querySelector("#hl-av-reloc").checked;
        saveDraft(state);
      }
    }

    function showPreviewModal(title, bodyHtml, revokeUrl) {
      // Aperçu superposé : le parcours de candidature reste ouvert en dessous.
      HireLink.openPreview({ title: title, bodyHtml: bodyHtml }).then(() => {
        if (revokeUrl) URL.revokeObjectURL(revokeUrl);
      });
    }

    function previewLocalFile(file, title) {
      if (!file) {
        showPreviewModal(title, "<p class='muted'>Aucun fichier.</p>");
        return;
      }
      let url = null;
      let body;
      if (/pdf$/i.test(file.name || "") || (file.type || "").indexOf("pdf") >= 0) {
        url = URL.createObjectURL(file);
        body =
          '<iframe src="' +
          url +
          '" title="Aperçu" style="width:100%;height:420px;border:0;border-radius:12px;background:#fff"></iframe>';
      } else if (/\.(txt|md)$/i.test(file.name || "") || (file.type || "").indexOf("text") >= 0) {
        const reader = new FileReader();
        reader.onload = () => {
          showPreviewModal(
            title,
            "<pre style='white-space:pre-wrap;max-height:420px;overflow:auto;margin:0;font:inherit'>" +
              esc(String(reader.result || "").slice(0, 12000)) +
              "</pre>"
          );
        };
        reader.readAsText(file);
        return;
      } else {
        body =
          "<p><strong>" +
          esc(file.name) +
          "</strong></p><p class='muted'>Aperçu intégré pour PDF et texte. Le fichier sera joint / lu si le format est supporté.</p>";
      }
      showPreviewModal(title, body, url);
    }

    async function previewProfileCv() {
      if (!state.hasProfileCv) {
        HireLink.toast("CV manquant", "Déposez d'abord un CV dans Mon profil.", "warn");
        return;
      }
      try {
        const res = await HireLinkAPI.authFetch("/me/cv");
        if (!res.ok) throw new Error("CV introuvable");
        const blob = await res.blob();
        const type = (blob.type || "").toLowerCase();
        const name = state.profile.cvFileName || "cv.pdf";
        if (type.indexOf("pdf") >= 0 || /\.pdf$/i.test(name)) {
          const url = URL.createObjectURL(blob);
          showPreviewModal(
            "Aperçu — " + name,
            '<iframe src="' +
              url +
              '" title="Aperçu CV" style="width:100%;height:420px;border:0;border-radius:12px;background:#fff"></iframe>',
            url
          );
          return;
        }
        if (type.indexOf("text") >= 0 || /\.(txt|md)$/i.test(name)) {
          const text = await blob.text();
          showPreviewModal(
            "Aperçu — " + name,
            "<pre style='white-space:pre-wrap;max-height:420px;overflow:auto;margin:0;font:inherit'>" +
              esc(text.slice(0, 12000)) +
              "</pre>"
          );
          return;
        }
        showPreviewModal(
          "Aperçu — " + name,
          "<p>Fichier enregistré : <strong>" +
            esc(name) +
            "</strong> (" +
            Math.round(blob.size / 1024) +
            " Ko).</p><p class='muted'>Aperçu intégré pour PDF et texte. Le recruteur pourra télécharger le fichier.</p>"
        );
      } catch (err) {
        HireLink.toast("Aperçu", (err && err.message) || "Impossible d'ouvrir le CV", "danger");
      }
    }

    async function importLetterFile(f) {
      state.letterFile = f;
      state.letterFileName = f.name;
      const isText = /\.(txt|md)$/i.test(f.name) || (f.type || "").indexOf("text") >= 0;
      if (isText) {
        const text = await f.text();
        state.coverLetter = String(text || "").trim().slice(0, 8000);
        HireLink.toast("Lettre", "Texte importé depuis " + f.name, "ok");
        paint();
        return;
      }
      try {
        const fd = new FormData();
        fd.append("file", f);
        const res = await HireLinkAPI.authFetch("/ocr/cv?applySkills=false", { method: "POST", body: fd });
        const data = await res.json().catch(() => ({}));
        if (!res.ok) throw new Error(data.message || "Lecture impossible");
        const text = String(data.text || data.textPreview || "").replace(/…$/, "").trim();
        if (!text) throw new Error("Aucun texte extrait");
        state.coverLetter = text.slice(0, 8000);
        HireLink.toast("Lettre", "Texte extrait de " + f.name, "ok");
      } catch (err) {
        HireLink.toast(
          "Lettre",
          (err && err.message) || "Formats lus : PDF et TXT. Collez le texte ou convertissez en PDF.",
          "warn"
        );
      }
      paint();
    }

    function bindBody() {
      if (state.step === 4) {
        const wrap = overlay.querySelector("#hl-av-date-wrap");
        overlay.querySelectorAll('input[name="hl-av"]').forEach((r) => r.addEventListener("change", () => { wrap.hidden = r.value !== "DATE" || !r.checked; capture(); }));
        overlay.querySelector(".hl-apply-body").addEventListener("change", capture);
        if (global.HLUI && HLUI.enhance) HLUI.enhance(overlay);
      }
      if (state.step === 1) {
        HireLinkSkills.mount(overlay.querySelector("#hl-apply-skills"), {
          value: state.skillsCsv,
          onChange: (csv) => {
            state.skillsCsv = csv;
          },
        });
      }
      if (state.step === 2) {
        overlay.querySelectorAll(".hl-cv-opt").forEach((el) => {
          el.onclick = () => {
            const src = el.getAttribute("data-src");
            if (src === "PROFILE" && !state.hasProfileCv) {
              HireLink.toast(
                "CV requis",
                "Le profil HireLink doit contenir un CV. Allez dans Mon profil ou choisissez « Nouveau CV ».",
                "warn"
              );
              return;
            }
            state.cvSource = src;
            if (src === "PROFILE" && state.hasProfileCv) {
              state.cvFileName = state.profile.cvFileName || state.cvFileName;
            }
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
              const res = await HireLinkAPI.authFetch("/ocr/cv", {
                method: "POST",
                body: fd,
              });
              const data = await res.json().catch(() => ({}));
              if (res.ok && data.suggestedSkills && data.suggestedSkills.length) {
                HireLink.toast("CV analysé", data.suggestedSkills.slice(0, 5).join(", "), "ok");
                state.skillsCsv = HireLinkSkills.split(state.skillsCsv)
                  .concat(data.suggestedSkills)
                  .filter(
                    (v, i, all) =>
                      all.findIndex((x) => HireLinkSearch.compact(x) === HireLinkSearch.compact(v)) === i
                  )
                  .join(", ");
              }
            } catch (_) {}
          };
        }
        const prev = overlay.querySelector("#hl-cv-preview");
        if (prev) {
          prev.onclick = () => {
            if (state.cvSource === "UPLOAD" && state.cvFile) {
              previewLocalFile(state.cvFile, "Aperçu du CV");
              return;
            }
            if (state.cvSource === "PROFILE") {
              previewProfileCv();
              return;
            }
            HireLink.toast("Aperçu", "Choisissez un CV à prévisualiser", "info");
          };
        }
      }
      if (state.step === 3) {
        const letterFile = overlay.querySelector("#hl-letter-file");
        if (letterFile) {
          letterFile.onchange = () => {
            const f = letterFile.files && letterFile.files[0];
            if (f) importLetterFile(f);
          };
        }
        const letterPrev = overlay.querySelector("#hl-letter-preview");
        if (letterPrev) {
          letterPrev.onclick = () => {
            capture();
            if (state.letterFile) {
              previewLocalFile(state.letterFile, "Aperçu de la lettre");
              return;
            }
            if (state.coverLetter) {
              showPreviewModal(
                "Aperçu de la lettre",
                "<pre style='white-space:pre-wrap;max-height:420px;overflow:auto;margin:0;font:inherit'>" +
                  esc(state.coverLetter) +
                  "</pre>"
              );
              return;
            }
            HireLink.toast("Lettre", "Rien à prévisualiser", "info");
          };
        }
      }
    }

    function canGoNext() {
      if (state.step === 4 && state.details.availability === "DATE" && !state.details.availableFrom) {
        HireLink.toast("Disponibilité", "Choisissez la date à partir de laquelle vous êtes disponible.", "warn");
        return false;
      }
      if (state.step === 1) {
        capture();
        if (!state.skillsCsv || !state.skillsCsv.trim()) {
          HireLink.toast("Compétences", "Sélectionnez au moins une compétence", "warn");
          return false;
        }
      }
      if (state.step === 2) {
        if (state.cvSource === "PROFILE" && !state.hasProfileCv) {
          HireLink.toast(
            "CV requis",
            "Le profil HireLink n'a pas de CV. Déposez-en un ou choisissez « Nouveau CV ».",
            "warn"
          );
          return false;
        }
        if (state.cvSource === "UPLOAD" && !state.cvFileName) {
          HireLink.toast("CV requis", "Choisissez un fichier PDF ou TXT", "warn");
          return false;
        }
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
            details: JSON.stringify({
              availability: state.details.availability,
              availableFrom: state.details.availability === "DATE" ? state.details.availableFrom : null,
              notice: state.details.notice,
              salary: state.details.salary ? Number(state.details.salary) : null,
              workMode: state.details.workMode,
              relocation: state.details.relocation,
              source: state.details.source,
            }),
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
        try {
          localStorage.removeItem("hirelink.apply.draft." + state.offer.id);
          const pr = Object.assign({}, state.details); delete pr.source;
          localStorage.setItem("hirelink.apply.prefs", JSON.stringify(pr));
        } catch (_) {}
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
          state.letterFile = null;
          state.letterFileName = "";
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
