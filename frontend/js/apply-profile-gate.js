/**
 * Avant la candidature : complète formation + disponibilité si le profil est incomplet.
 * Le type de contrat vient de l'offre (déjà clair) : on l'enregistre dans le profil sans le redemander.
 * Enchaîne ensuite sur HireLinkApply.start.
 */
(function (global) {
  function esc(s) {
    return String(s == null ? "" : s)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#39;");
  }

  function has(v) {
    return !!(v != null && String(v).trim());
  }

  function formationOk(p) {
    return has(p.school) && has(p.experienceLevel) && has(p.educationDomain) && p.graduationYear != null;
  }

  function availabilityOk(p) {
    return has(p.availability);
  }

  function needsGate(p) {
    return !formationOk(p) || !availabilityOk(p);
  }

  function svg(name) {
    var paths = {
      book: '<path d="M4 5.5A2.5 2.5 0 0 1 6.5 3H12v16H6.5A2.5 2.5 0 0 0 4 21.5z"/><path d="M20 5.5A2.5 2.5 0 0 0 17.5 3H12v16h5.5A2.5 2.5 0 0 1 20 21.5z"/>',
      brief: '<rect x="3" y="7" width="18" height="13" rx="2"/><path d="M9 7V5.5A1.5 1.5 0 0 1 10.5 4h3A1.5 1.5 0 0 1 15 5.5V7M3 13h18"/>',
      bolt: '<path d="M13 2 4 14h7l-1 8 9-12h-7z"/>',
      clock: '<circle cx="12" cy="12" r="8.5"/><path d="M12 7.5V12l3 2"/>',
      cal: '<rect x="3.5" y="5" width="17" height="15" rx="2"/><path d="M8 3v4M16 3v4M3.5 10h17"/>',
    };
    return (
      '<svg class="hl-pgate-ico" viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" ' +
      'stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' +
      (paths[name] || "") +
      "</svg>"
    );
  }

  function ensureCss() {
    if (document.getElementById("hl-pgate-css")) return;
    var s = document.createElement("style");
    s.id = "hl-pgate-css";
    s.textContent =
      ".hl-pgate-overlay{position:fixed;inset:0;z-index:9300;background:rgba(18,12,14,.55);backdrop-filter:blur(10px);display:flex;align-items:center;justify-content:center;padding:1rem;opacity:0;pointer-events:none;transition:opacity .28s ease}" +
      ".hl-pgate-overlay.is-open{opacity:1;pointer-events:auto}" +
      ".hl-pgate-card{background:#fff;border-radius:20px;width:min(640px,100%);max-height:min(92vh,860px);display:flex;flex-direction:column;box-shadow:0 32px 80px rgba(0,0,0,.32);border:1px solid rgba(122,32,50,.16);overflow:hidden;transform:translateY(14px) scale(.98);transition:transform .35s cubic-bezier(.22,1,.36,1)}" +
      ".hl-pgate-overlay.is-open .hl-pgate-card{transform:none}" +
      ".hl-pgate-top{padding:1.25rem 1.4rem 1rem;border-bottom:1px solid rgba(26,18,20,.08);flex-shrink:0;position:relative}" +
      ".hl-pgate-top h2{font-family:var(--font-display,Inter,sans-serif);margin:0 0 .35rem;font-size:1.35rem;color:#1a1214}" +
      ".hl-pgate-top .sub{margin:0;color:#5e5558;font-size:.92rem;line-height:1.45}" +
      ".hl-pgate-close{position:absolute;top:.85rem;right:.85rem;border:0;background:transparent;font-size:1.35rem;line-height:1;cursor:pointer;color:#8a8285;padding:.25rem .45rem;border-radius:8px}" +
      ".hl-pgate-close:hover{background:#f4f0ec;color:#1a1214}" +
      ".hl-pgate-progress{display:flex;gap:.35rem;margin-top:.95rem}" +
      ".hl-pgate-progress>span{flex:1;height:4px;border-radius:99px;background:rgba(122,32,50,.12)}" +
      ".hl-pgate-progress>span.is-on{background:linear-gradient(90deg,#7a2032,#e87030)}" +
      ".hl-pgate-body{padding:1.15rem 1.4rem;overflow:auto;flex:1;min-height:0}" +
      ".hl-pgate-foot{padding:1rem 1.4rem;border-top:1px solid rgba(26,18,20,.08);display:flex;justify-content:space-between;gap:.65rem;flex-wrap:wrap;background:#faf8f6;flex-shrink:0}" +
      ".hl-pgate-checklist{display:grid;gap:.7rem}" +
      ".hl-pgate-item{display:flex;align-items:center;gap:.85rem;padding:1rem 1.05rem;border-radius:14px;border:1.5px solid rgba(26,18,20,.1);background:#fff}" +
      ".hl-pgate-item.is-ok{border-color:rgba(45,106,79,.35);background:rgba(45,106,79,.04)}" +
      ".hl-pgate-item .ic{width:2.4rem;height:2.4rem;border-radius:12px;display:grid;place-items:center;background:rgba(122,32,50,.08);color:#7a2032;flex-shrink:0}" +
      ".hl-pgate-item strong{display:block;font-size:.98rem;color:#1a1214}" +
      ".hl-pgate-item span{font-size:.86rem;color:#5e5558}" +
      ".hl-pgate-item .mark{margin-left:auto;width:1.5rem;height:1.5rem;border-radius:50%;border:1.5px solid rgba(26,18,20,.18);display:grid;place-items:center;color:transparent}" +
      ".hl-pgate-item.is-ok .mark{background:#2d6a4f;border-color:#2d6a4f;color:#fff}" +
      ".hl-pgate-tip{margin-top:1rem;padding:.85rem 1rem;border-radius:12px;background:rgba(122,32,50,.07);color:#7a2032;font-size:.9rem;display:flex;gap:.55rem;align-items:center}" +
      ".hl-pgate-offer{margin:0 0 1rem;padding:.85rem 1rem;border-radius:12px;background:#f7f4f1;border:1px solid rgba(26,18,20,.08);font-size:.9rem;color:#3a3335}" +
      ".hl-pgate-offer strong{color:#7a2032}" +
      ".hl-pgate-form{display:grid;gap:.85rem}" +
      ".hl-pgate-form label{display:grid;gap:.35rem;font-size:.86rem;font-weight:600;color:#3a3335}" +
      ".hl-pgate-form .form-control{width:100%;padding:.7rem .85rem;border-radius:12px;border:1.5px solid rgba(26,18,20,.12);font:inherit;background:#fff}" +
      ".hl-pgate-form .form-control:focus{outline:none;border-color:#7a2032;box-shadow:0 0 0 3px rgba(122,32,50,.12)}" +
      ".hl-pgate-avail{display:grid;gap:.65rem}" +
      ".hl-pgate-aopt{display:flex;align-items:center;gap:.85rem;padding:1rem;border-radius:14px;border:1.5px solid rgba(26,18,20,.1);cursor:pointer;background:#fff;text-align:left;width:100%;font:inherit}" +
      ".hl-pgate-aopt.is-on{border-color:#7a2032;background:rgba(122,32,50,.06)}" +
      ".hl-pgate-aopt .ic{width:2.2rem;height:2.2rem;border-radius:10px;display:grid;place-items:center;background:rgba(122,32,50,.08);color:#7a2032;flex-shrink:0}" +
      ".hl-pgate-aopt strong{display:block;color:#1a1214}" +
      ".hl-pgate-aopt span{font-size:.86rem;color:#5e5558}" +
      ".hl-pgate-aopt .tick{margin-left:auto;width:1.4rem;height:1.4rem;border-radius:50%;border:1.5px solid rgba(26,18,20,.18);flex-shrink:0}" +
      ".hl-pgate-aopt.is-on .tick{background:#7a2032;border-color:#7a2032;color:#fff;display:grid;place-items:center;font-size:.75rem}" +
      ".hl-pgate-aopt.is-on .tick::after{content:'\\2713'}" +
      ".hl-pgate-err{color:#a11;font-size:.88rem;margin:0}";
    document.head.appendChild(s);
  }

  function mountSuggest(root) {
    if (!window.HireLinkSuggest) return;
    root.querySelectorAll("[data-suggest]").forEach(function (el) {
      el.dataset.suggestReady = "";
      HireLinkSuggest.mount(el);
    });
  }

  function offerContract(offer) {
    return String((offer && (offer.contractType || offer.contract)) || "").trim();
  }

  async function ensureCompleteThenApply(offer, onDone) {
    var profile = {};
    try {
      profile = await HireLinkAPI.api("/me");
    } catch (_) {}

    var contract = offerContract(offer);

    // Contrat de l'offre ? profil (sans redemander le type).
    if (contract) {
      try {
        var prefs = String(profile.preferredContracts || "")
          .split(",")
          .map(function (s) { return s.trim(); })
          .filter(Boolean);
        var known = prefs.some(function (p) { return p.toLowerCase() === contract.toLowerCase(); });
        if (!known) {
          prefs.push(contract);
          profile =
            (await HireLinkAPI.api("/me", {
              method: "PATCH",
              body: { preferredContracts: prefs.join(", ") },
            })) || Object.assign({}, profile, { preferredContracts: prefs.join(", ") });
        }
      } catch (_) {}
    }

    if (!needsGate(profile)) {
      if (window.HireLinkApply) HireLinkApply.start(offer, onDone);
      return;
    }

    ensureCss();
    var overlay = document.createElement("div");
    overlay.className = "hl-pgate-overlay";
    overlay.innerHTML = '<div class="hl-pgate-card" role="dialog" aria-modal="true"></div>';
    document.body.appendChild(overlay);
    requestAnimationFrame(function () {
      overlay.classList.add("is-open");
    });

    var card = overlay.querySelector(".hl-pgate-card");
    var state = {
      profile: Object.assign({}, profile),
      step: "overview",
      availMode: has(profile.availability) && /^\d{4}-\d{2}-\d{2}$/.test(profile.availability)
        ? "date"
        : has(profile.availability)
          ? "now"
          : "",
      availDate: /^\d{4}-\d{2}-\d{2}$/.test(String(profile.availability || "")) ? profile.availability : "",
      error: "",
      saving: false,
      contract: contract,
      offerTitle: (offer && offer.title) || "cette offre",
    };

    function close() {
      overlay.classList.remove("is-open");
      setTimeout(function () {
        overlay.remove();
      }, 280);
    }

    function finish() {
      close();
      if (window.HireLinkApply) HireLinkApply.start(offer, onDone);
    }

    function nextIncomplete() {
      if (!formationOk(state.profile)) return "formation";
      if (!availabilityOk(state.profile)) return "availability";
      return null;
    }

    function progressBits() {
      var steps = ["overview"];
      if (!formationOk(state.profile) || state.step === "formation") steps.push("formation");
      if (!availabilityOk(state.profile) || state.step === "availability") steps.push("availability");
      var idx = Math.max(0, steps.indexOf(state.step));
      return { steps: steps, idx: idx };
    }

    function paint() {
      var fOk = formationOk(state.profile);
      var aOk = availabilityOk(state.profile);
      var prog = progressBits();
      var bars = prog.steps
        .map(function (_, i) {
          return '<span class="' + (i <= prog.idx ? "is-on" : "") + '"></span>';
        })
        .join("");

      var title = "Complétez votre profil";
      var sub =
        "Pour postuler à cette offre, nous avons besoin de quelques informations supplémentaires sur votre profil.";
      var body = "";
      var primary = "Continuer";
      var showPrev = false;

      var offerHint =
        '<p class="hl-pgate-offer">Offre : <strong>' +
        esc(state.offerTitle) +
        "</strong>" +
        (state.contract
          ? " · Contrat de l'offre : <strong>" + esc(state.contract) + "</strong> (déjà pris en compte)"
          : "") +
        "</p>";

      if (state.step === "overview") {
        body =
          offerHint +
          '<div class="hl-pgate-checklist">' +
          '<div class="hl-pgate-item' +
          (fOk ? " is-ok" : "") +
          '"><div class="ic">' +
          svg("book") +
          '</div><div><strong>Vos formations</strong><span>École, niveau, diplôme et domaines d\'études</span></div><div class="mark">' +
          (fOk ? "?" : "") +
          "</div></div>" +
          '<div class="hl-pgate-item' +
          (aOk ? " is-ok" : "") +
          '"><div class="ic">' +
          svg("cal") +
          '</div><div><strong>Votre disponibilité</strong><span>Date de démarrage souhaitée</span></div><div class="mark">' +
          (aOk ? "?" : "") +
          "</div></div>" +
          "</div>" +
          '<p class="hl-pgate-tip">' +
          svg("bolt") +
          " Cela ne vous prendra que quelques minutes !</p>";
      } else if (state.step === "formation") {
        title = "Votre dernière formation";
        sub =
          "Sélectionnez votre niveau d'étude actuel ainsi que les informations relatives à votre dernière formation.";
        primary = "Valider";
        showPrev = true;
        var y = state.profile.graduationYear != null ? String(state.profile.graduationYear) : "";
        body =
          offerHint +
          '<div class="hl-pgate-form" id="hl-pgate-form-edu">' +
          '<label>1. Nom de l\'école<input class="form-control" id="pg-school" data-suggest="school" placeholder="Recherchez votre école" value="' +
          esc(state.profile.school || "") +
          '" autocomplete="off" /></label>' +
          '<label>2. Niveau d\'étude actuel<input class="form-control" id="pg-level" data-suggest="study" placeholder="Choisissez un niveau d\'étude actuel" value="' +
          esc(state.profile.experienceLevel || "") +
          '" autocomplete="off" /></label>' +
          '<label>3. Votre domaine de formation<input class="form-control" id="pg-domain" data-suggest="domain" placeholder="Choisissez un domaine de formation" value="' +
          esc(state.profile.educationDomain || "") +
          '" autocomplete="off" /></label>' +
          '<label>4. Année d\'obtention (ou prévue)<input class="form-control" id="pg-year" type="number" min="1950" max="2100" step="1" placeholder="Ex. 2026" value="' +
          esc(y) +
          '" /></label>' +
          "</div>";
      } else if (state.step === "availability") {
        title = "Votre disponibilité";
        sub =
          "Indiquez à partir de quand vous pouvez commencer" +
          (state.contract ? " pour ce " + state.contract : "") +
          ".";
        primary = "Continuer";
        showPrev = true;
        body =
          offerHint +
          '<div class="hl-pgate-avail">' +
          '<button type="button" class="hl-pgate-aopt' +
          (state.availMode === "now" ? " is-on" : "") +
          '" data-avail="now"><div class="ic">' +
          svg("clock") +
          '</div><div><strong>Immédiatement</strong><span>Disponible dès maintenant</span></div><div class="tick"></div></button>' +
          '<button type="button" class="hl-pgate-aopt' +
          (state.availMode === "date" ? " is-on" : "") +
          '" data-avail="date"><div class="ic">' +
          svg("cal") +
          '</div><div><strong>À partir d\'une date</strong><span>Choisissez la date à partir de laquelle vous serez disponible</span></div><div class="tick"></div></button>' +
          (state.availMode === "date"
            ? '<label style="display:grid;gap:.35rem;font-size:.86rem;font-weight:600">Date de disponibilité<input class="form-control" id="pg-avail-date" type="date" value="' +
              esc(state.availDate || "") +
              '" /></label>'
            : "") +
          "</div>";
      }

      card.innerHTML =
        '<div class="hl-pgate-top">' +
        '<button type="button" class="hl-pgate-close" id="pg-x" aria-label="Fermer">×</button>' +
        "<h2>" +
        esc(title) +
        "</h2>" +
        '<p class="sub">' +
        esc(sub) +
        "</p>" +
        '<div class="hl-pgate-progress">' +
        bars +
        "</div>" +
        "</div>" +
        '<div class="hl-pgate-body">' +
        body +
        (state.error ? '<p class="hl-pgate-err" role="alert">' + esc(state.error) + "</p>" : "") +
        "</div>" +
        '<div class="hl-pgate-foot">' +
        '<button type="button" class="btn btn-ghost" id="pg-cancel">Annuler</button>' +
        '<div style="display:flex;gap:.5rem;margin-left:auto">' +
        (showPrev ? '<button type="button" class="btn btn-ghost" id="pg-prev">Précédent</button>' : "") +
        '<button type="button" class="btn btn-primary" id="pg-next">' +
        esc(primary) +
        " ?</button>" +
        "</div></div>";

      card.querySelector("#pg-x").onclick = close;
      card.querySelector("#pg-cancel").onclick = close;
      var prev = card.querySelector("#pg-prev");
      if (prev) {
        prev.onclick = function () {
          state.error = "";
          if (state.step === "formation" || state.step === "availability") state.step = "overview";
          paint();
        };
      }
      card.querySelector("#pg-next").onclick = function () {
        onPrimary();
      };

      if (state.step === "formation") mountSuggest(card);
      if (state.step === "availability") {
        card.querySelectorAll("[data-avail]").forEach(function (btn) {
          btn.onclick = function () {
            state.availMode = btn.getAttribute("data-avail");
            state.error = "";
            paint();
          };
        });
        var dateEl = card.querySelector("#pg-avail-date");
        if (dateEl) {
          dateEl.addEventListener("change", function () {
            state.availDate = dateEl.value;
          });
        }
      }
    }

    async function savePatch(body) {
      var updated = await HireLinkAPI.api("/me", { method: "PATCH", body: body });
      state.profile = Object.assign({}, state.profile, updated || {}, body);
      return updated;
    }

    async function onPrimary() {
      if (state.saving) return;
      state.error = "";
      try {
        if (state.step === "overview") {
          var next = nextIncomplete();
          if (!next) {
            finish();
            return;
          }
          state.step = next;
          paint();
          return;
        }

        if (state.step === "formation") {
          var school = (card.querySelector("#pg-school") || {}).value || "";
          var level = (card.querySelector("#pg-level") || {}).value || "";
          var domain = (card.querySelector("#pg-domain") || {}).value || "";
          var yearRaw = (card.querySelector("#pg-year") || {}).value || "";
          var year = parseInt(yearRaw, 10);
          if (!school.trim() || !level.trim() || !domain.trim() || !yearRaw || Number.isNaN(year)) {
            state.error = "Renseignez l'école, le niveau, le domaine et l'année.";
            paint();
            return;
          }
          if (year < 1950 || year > 2100) {
            state.error = "Indiquez une année entre 1950 et 2100.";
            paint();
            return;
          }
          state.saving = true;
          await savePatch({
            school: school.trim(),
            experienceLevel: level.trim(),
            educationDomain: domain.trim(),
            graduationYear: year,
          });
          state.saving = false;
          if (!availabilityOk(state.profile)) {
            state.step = "availability";
            paint();
            return;
          }
          finish();
          return;
        }

        if (state.step === "availability") {
          var availability = "";
          if (state.availMode === "now") availability = "Immédiatement";
          else if (state.availMode === "date") {
            var dateEl2 = card.querySelector("#pg-avail-date");
            availability = (dateEl2 && dateEl2.value) || state.availDate || "";
            if (!availability) {
              state.error = "Choisissez une date de disponibilité.";
              paint();
              return;
            }
          } else {
            state.error = "Indiquez votre disponibilité.";
            paint();
            return;
          }
          state.saving = true;
          await savePatch({ availability: availability });
          state.saving = false;
          finish();
        }
      } catch (err) {
        state.saving = false;
        state.error = (err && err.message) || "Enregistrement impossible";
        paint();
      }
    }

    paint();
  }

  global.HireLinkApplyGate = {
    needsGate: needsGate,
    ensureCompleteThenApply: ensureCompleteThenApply,
  };
})(window);
