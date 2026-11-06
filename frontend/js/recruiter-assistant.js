/** Assistant IA recruteur : grille d'entretien, brouillon de refus, synthèse de CV — crédits partagés. */
(function () {
  const RC = HireLinkApp;
  const session = RC.mount({ active: "assistant", title: "Assistant IA" });
  if (!session) return;
  const $ = (id) => document.getElementById(id);
  const esc = RC.esc;
  let offers = [];
  let apps = [];

  function paintCredits(c) {
    if (!c) return;
    $("ai-credits").textContent = c.remaining + " / " + c.limit;
    $("ai-plan").textContent =
      "Formule " + (c.planLabel || (c.plan === "PREMIUM" ? "Premium" : "Accès")) +
      (c.monthlyMad ? " · " + c.monthlyMad + " MAD / mois" : " · incluse dans votre formule") +
      " · " + c.costPerAction + " crédit par action réussie";
    $("ai-meter").style.width = Math.max(0, Math.min(100, Math.round((c.remaining / Math.max(1, c.limit)) * 100))) + "%";
  }
  function loadCredits() {
    return HireLinkAPI.api("/ai/credits").then(paintCredits).catch(() => {
      $("ai-credits").textContent = "—";
    });
  }

  const TABS = ["kit", "cv"];
  function openTab(t) {
    document.querySelectorAll(".rc-tab").forEach((b) => b.classList.toggle("is-on", b.dataset.t === t));
    TABS.forEach((x) => ($("pane-" + x).hidden = x !== t));
    history.replaceState(null, "", "#" + t);
  }
  document.querySelector(".rc-tabs").addEventListener("click", (e) => {
    const b = e.target.closest(".rc-tab");
    if (b) openTab(b.dataset.t);
  });

  function normalizeKitQuestions(raw) {
    if (!Array.isArray(raw)) return [];
    return raw
      .map((item, i) => {
        if (item && typeof item === "object") {
          return {
            category: String(item.category || "Question " + (i + 1)).trim(),
            question: String(item.question || item.text || "").trim(),
            lookFor: String(item.lookFor || item.guide || item.answer || "").trim(),
          };
        }
        const s = String(item || "").trim();
        if (!s) return null;
        const parts = s.split(/\s+[—–-]\s+/);
        if (parts.length >= 2) {
          return { category: parts[0].trim(), question: parts.slice(1).join(" — ").trim(), lookFor: "" };
        }
        return { category: "Question " + (i + 1), question: s, lookFor: "" };
      })
      .filter((q) => q && q.question);
  }

  function kitPlainText(items) {
    return items
      .map((q, i) => {
        let t = i + 1 + ". [" + q.category + "] " + q.question;
        if (q.lookFor) t += "\n   → Écouter : " + q.lookFor;
        return t;
      })
      .join("\n\n");
  }

  function showKit(outId, data) {
    const out = $(outId);
    out.hidden = false;
    out.classList.add("rc-fade");
    const items = normalizeKitQuestions(data.questions);
    if (!items.length) {
      showPlain(outId, data);
      return;
    }
    out.innerHTML =
      '<div class="hl-kit-grid" role="list">' +
      items
        .map((q, i) => {
          const tone = "tone-" + (i % 7);
          const guide = q.lookFor
            ? '<div class="hl-kit-answer" hidden><p class="hl-kit-hint-label">Ce qu’il faut écouter</p><p>' +
              esc(q.lookFor) +
              "</p></div>"
            : "";
          return (
            '<button type="button" class="hl-kit-card ' +
            tone +
            '" role="listitem" aria-expanded="false">' +
            '<div class="hl-kit-top"><span class="hl-kit-cat">' +
            esc(q.category) +
            '</span><span class="hl-kit-n">' +
            (i + 1) +
            " / " +
            items.length +
            "</span></div>" +
            '<strong class="hl-kit-q">' +
            esc(q.question) +
            "</strong>" +
            guide +
            (q.lookFor ? '<span class="hl-kit-toggle">Voir le guide de réponse</span>' : "") +
            "</button>"
          );
        })
        .join("") +
      "</div>" +
      '<div class="hl-kit-foot">' +
      '<button type="button" class="btn btn-ghost btn-sm" id="kit-copy">Copier la grille</button>' +
      '<span class="rc-muted" style="font-size: var(--text-xs)">' +
      esc(data.note || "Cliquez une carte pour afficher le guide. À relire avant l’entretien.") +
      (data.provider ? " · " + esc(data.provider) : "") +
      "</span></div>";

    out.querySelectorAll(".hl-kit-card").forEach((card) => {
      card.addEventListener("click", () => {
        const open = card.classList.toggle("is-open");
        card.setAttribute("aria-expanded", open ? "true" : "false");
        const ans = card.querySelector(".hl-kit-answer");
        const tog = card.querySelector(".hl-kit-toggle");
        if (ans) ans.hidden = !open;
        if (tog) tog.textContent = open ? "Masquer le guide" : "Voir le guide de réponse";
      });
    });
    const copyBtn = out.querySelector("#kit-copy");
    if (copyBtn) {
      copyBtn.onclick = (e) => {
        e.stopPropagation();
        navigator.clipboard.writeText(kitPlainText(items)).then(
          () => RC.toast("Copié", "Grille copiée dans le presse-papiers", "ok"),
          () => RC.toast("Copie impossible", "Sélectionnez le texte manuellement", "warn")
        );
      };
    }
  }

  function showPlain(outId, data) {
    const out = $(outId);
    out.hidden = false;
    out.classList.add("rc-fade");
    out.innerHTML = esc(data.content || "").replace(/\n/g, "<br/>");
    const copy = document.createElement("div");
    copy.className = "hl-kit-foot";
    copy.innerHTML =
      '<button type="button" class="btn btn-ghost btn-sm">Copier le texte</button> <span class="rc-muted" style="font-size: var(--text-xs)">' +
      esc(data.note || "À relire avant utilisation — la décision reste humaine.") +
      (data.provider ? " · source : " + esc(data.provider) : "") +
      "</span>";
    copy.querySelector("button").onclick = () =>
      navigator.clipboard.writeText(String(data.content || "").trim()).then(
        () => RC.toast("Copié", "Texte copié dans le presse-papiers", "ok"),
        () => RC.toast("Copie impossible", "Sélectionnez le texte manuellement", "warn")
      );
    out.appendChild(copy);
  }

  function show(outId, data) {
    if (outId === "kit-out" && Array.isArray(data.questions) && data.questions.length) {
      showKit(outId, data);
      return;
    }
    showPlain(outId, data);
  }

  async function run(btn, outId, path, body) {
    if (!HireLinkCredits.preflight()) return;
    RC.busy(btn, true);
    const out = $(outId);
    out.hidden = false;
    out.innerHTML = Mascotte.waiting("Réflexion en cours…");
    try {
      const data = await HireLinkAPI.api(path, { method: "POST", body, timeoutMs: 60000 });
      show(outId, data);
      loadCredits();
      HireLinkCredits.spend();
    } catch (err) {
      out.hidden = true;
      if (!HireLinkCredits.guard(err)) RC.toast("Assistant IA", err.message, "danger");
    } finally {
      RC.busy(btn, false);
    }
  }

  $("kit-go").addEventListener("click", (e) => {
    const o = offers.find((x) => String(x.id) === $("kit-offer").value);
    const title = ($("kit-title").value || (o && o.title) || "").trim();
    if (!title) {
      RC.toast("Poste requis", "Choisissez une offre ou saisissez un intitulé.", "warn");
      return;
    }
    run(e.currentTarget, "kit-out", "/ai/interview-kit", {
      jobTitle: title,
      skills: ($("kit-skills").value || (o && o.requiredSkills) || "").trim(),
    });
  });
  $("kit-offer").addEventListener("change", () => {
    const o = offers.find((x) => String(x.id) === $("kit-offer").value);
    if (o) {
      $("kit-title").value = o.title || "";
      $("kit-skills").value = o.requiredSkills || "";
    }
  });

  $("cv-go").addEventListener("click", (e) => {
    const a = apps.find((x) => String(x.id) === $("cv-app").value);
    if (!a) {
      RC.toast("Candidat requis", "Choisissez un dossier.", "warn");
      return;
    }
    const cvText = [a.candidateName, a.candidateSkills, a.coverLetter, a.cvFileName].filter(Boolean).join("\n");
    run(e.currentTarget, "cv-out", "/ai/cv-summary", { cvText });
  });

  function fillSelects() {
    const opts = offers.map((o) => '<option value="' + o.id + '">' + esc(o.title) + "</option>").join("");
    $("kit-offer").innerHTML = '<option value="">— Saisie libre —</option>' + opts;
    $("cv-app").innerHTML =
      '<option value="">— Choisir un dossier —</option>' +
      apps.map((a) => '<option value="' + a.id + '">' + esc(a.candidateName) + " — " + esc(a.jobTitle || "") + "</option>").join("");
    const pre = new URLSearchParams(location.search).get("app");
    if (pre && apps.some((a) => String(a.id) === pre)) {
      $("cv-app").value = pre;
      openTab("cv");
    }
  }

  /** swr() ne prévient personne sans second callback : sans option, le select doit dire pourquoi. */
  function selectUnavailable(id, err) {
    const s = document.getElementById(id);
    if (s) s.innerHTML = '<option value="">Indisponible — ' + esc(err.message) + "</option>";
  }

  RC.data.offers((o) => {
    offers = o || [];
    fillSelects();
  }, (err) => selectUnavailable("kit-offer", err));
  RC.data.pipeline((a) => {
    apps = a || [];
    fillSelects();
  }, (err) => selectUnavailable("cv-app", err));
  loadCredits();
  HireLinkCredits.paintCosts();
  const h = location.hash.slice(1);
  if (TABS.includes(h)) openTab(h);
})();
