/** Assistant IA recruteur : grille d'entretien, brouillon de refus, synthèse de CV — crédits partagés. */
(function () {
  const RC = HireLinkApp;
  const session = RC.mount({ active: "assistant", title: "Assistant IA" });
  if (!session) return;
  const $ = (id) => document.getElementById(id);
  const esc = RC.esc;
  let offers = [];
  let apps = [];
  let reasons = [];

  function paintCredits(c) {
    if (!c) return;
    const used = c.used != null ? c.used : c.limit - c.remaining;
    $("ai-credits").textContent = c.remaining + " / " + c.limit;
    $("ai-plan").textContent = "Palier " + (c.plan || "ACCES") + " · crédits partagés par l’organisation";
    $("ai-meter").style.width = Math.max(0, Math.min(100, Math.round((c.remaining / Math.max(1, c.limit)) * 100))) + "%";
    void used;
  }
  function loadCredits() { return HireLinkAPI.api("/ai/credits").then(paintCredits).catch(() => { $("ai-credits").textContent = "—"; }); }

  const TABS = ["kit", "refusal", "cv"];
  function openTab(t) {
    document.querySelectorAll(".rc-tab").forEach((b) => b.classList.toggle("is-on", b.dataset.t === t));
    TABS.forEach((x) => ($("pane-" + x).hidden = x !== t));
    history.replaceState(null, "", "#" + t);
  }
  document.querySelector(".rc-tabs").addEventListener("click", (e) => { const b = e.target.closest(".rc-tab"); if (b) openTab(b.dataset.t); });

  function show(outId, data) {
    const out = $(outId);
    out.hidden = false;
    out.classList.add("rc-fade");
    const qs = Array.isArray(data.questions) && data.questions.length && !data.content ? "<ol>" + data.questions.map((q) => "<li>" + esc(q) + "</li>").join("") + "</ol>" : null;
    out.innerHTML = qs || esc(data.content || "").replace(/\n/g, "<br/>");
    const copy = document.createElement("div");
    copy.style.marginTop = ".8rem";
    copy.innerHTML = '<button type="button" class="btn btn-ghost btn-sm">Copier le texte</button> <span class="rc-muted" style="font-size:.78rem">' + esc(data.note || "À relire avant utilisation — la décision reste humaine.") + (data.provider ? " · source : " + esc(data.provider) : "") + "</span>";
    copy.querySelector("button").onclick = () => navigator.clipboard.writeText(out.innerText.replace(/Copier le texte.*/s, "").trim()).then(() => RC.toast("Copié", "Texte copié dans le presse-papiers", "ok"), () => RC.toast("Copie impossible", "Sélectionnez le texte manuellement", "warn"));
    out.appendChild(copy);
  }

  async function run(btn, outId, path, body) {
    RC.busy(btn, true);
    const out = $(outId);
    out.hidden = false;
    out.innerHTML = Mascotte.waiting("Réflexion en cours…");
    try {
      const data = await HireLinkAPI.api(path, { method: "POST", body, timeoutMs: 60000 });
      show(outId, data);
      loadCredits();
    } catch (err) {
      out.hidden = true;
      RC.toast(err.status === 402 ? "Crédits IA épuisés" : "Assistant IA", err.message, err.status === 402 ? "warn" : "danger");
    } finally { RC.busy(btn, false); }
  }

  $("kit-go").addEventListener("click", (e) => {
    const o = offers.find((x) => String(x.id) === $("kit-offer").value);
    const title = ($("kit-title").value || (o && o.title) || "").trim();
    if (!title) { RC.toast("Poste requis", "Choisissez une offre ou saisissez un intitulé.", "warn"); return; }
    run(e.currentTarget, "kit-out", "/ai/interview-kit", { jobTitle: title, skills: ($("kit-skills").value || (o && o.requiredSkills) || "").trim() });
  });
  $("kit-offer").addEventListener("change", () => {
    const o = offers.find((x) => String(x.id) === $("kit-offer").value);
    if (o) { $("kit-title").value = o.title || ""; $("kit-skills").value = o.requiredSkills || ""; }
  });

  $("ref-go").addEventListener("click", (e) => {
    const code = $("ref-reason").value;
    if (!code) { RC.toast("Motif requis", "Choisissez un motif du catalogue.", "warn"); return; }
    const r = reasons.find((x) => x.code === code);
    const o = offers.find((x) => String(x.id) === $("ref-offer").value);
    run(e.currentTarget, "ref-out", "/ai/refusal-draft", { reasonCode: code, reasonLabel: r ? r.label : "", jobTitle: o ? o.title : "" });
  });

  $("cv-go").addEventListener("click", (e) => {
    const a = apps.find((x) => String(x.id) === $("cv-app").value);
    if (!a) { RC.toast("Candidat requis", "Choisissez un dossier.", "warn"); return; }
    const cvText = [a.candidateName, a.candidateSkills, a.coverLetter, a.cvFileName].filter(Boolean).join("\n");
    run(e.currentTarget, "cv-out", "/ai/cv-summary", { cvText });
  });

  function fillSelects() {
    const opts = offers.map((o) => '<option value="' + o.id + '">' + esc(o.title) + "</option>").join("");
    $("kit-offer").innerHTML = '<option value="">— Saisie libre —</option>' + opts;
    $("ref-offer").innerHTML = '<option value="">— Non précisé —</option>' + opts;
    $("ref-reason").innerHTML = '<option value="">— Choisir un motif —</option>' + reasons.map((r) => '<option value="' + esc(r.code) + '">' + esc(r.label) + "</option>").join("");
    $("cv-app").innerHTML = '<option value="">— Choisir un dossier —</option>' + apps.map((a) => '<option value="' + a.id + '">' + esc(a.candidateName) + " — " + esc(a.jobTitle || "") + "</option>").join("");
    const pre = new URLSearchParams(location.search).get("app");
    if (pre && apps.some((a) => String(a.id) === pre)) { $("cv-app").value = pre; openTab("cv"); }
  }

  RC.data.offers((o) => { offers = o || []; fillSelects(); });
  RC.data.pipeline((a) => { apps = a || []; fillSelects(); });
  HireLinkAPI.api("/refusal-reasons").then((r) => { reasons = r || []; fillSelects(); }).catch(() => {});
  loadCredits();
  const h = location.hash.slice(1);
  if (TABS.includes(h)) openTab(h);
})();
