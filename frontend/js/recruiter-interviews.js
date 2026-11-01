/** Entretiens : à venir / à replanifier / passés — kit, report, annulation et .ics. */
(function (global) {
  const RC = HireLinkApp;
  const S = HireLinkSearch;
  const session = RC.mount({
    active: "interviews",
    title: "Entretiens",
    search: { placeholder: "Candidat, offre, lieu…", onQuery: () => paint() },
  });
  if (!session) return;
  const $ = (id) => document.getElementById(id);
  let apps = [];
  let tab = "upcoming";
  let loaded = false;

  const cancelled = (a) => a.interviewStatus === "CANCELLED";

  function groups() {
    const limit = Date.now() - 3600e3;
    const withInterview = apps.filter((a) => a.interviewAt);
    const live = withInterview.filter((a) => a.status === "INTERVIEW" && !cancelled(a));
    const byDate = (x, y) => new Date(x.interviewAt) - new Date(y.interviewAt);
    return {
      upcoming: live.filter((a) => new Date(a.interviewAt).getTime() >= limit).sort(byDate),
      todo: withInterview.filter((a) => a.status === "INTERVIEW" && cancelled(a)).sort(byDate),
      past: withInterview
        .filter((a) => !cancelled(a) && (a.status !== "INTERVIEW" || new Date(a.interviewAt).getTime() < limit))
        .sort((x, y) => byDate(y, x)),
    };
  }

  const KIND = {
    HR: ["Entretien RH", "k-hr"], TECHNICAL: ["Technique", "k-tech"], MANAGER: ["Manager", "k-man"],
    CASE: ["Étude de cas", "k-case"], TEAM: ["Équipe", "k-team"], FINAL: ["Final", "k-final"],
  };
  const qp = new URLSearchParams(location.search);
  let groupBy = ["date", "kind", "offer"].includes(qp.get("group")) ? qp.get("group") : "date";
  let mode = ["VISIO", "ONSITE"].includes(qp.get("type")) ? qp.get("type") : "all";
  let offerFilter = qp.get("offer") || null;
  if (["upcoming", "todo", "past"].includes(qp.get("tab"))) tab = qp.get("tab");
  const ico = (n) => (global.HLIcons ? HLIcons.svg(n) : "");
  const initials = (n) => String(n || "?").split(/\s+/).filter(Boolean).slice(0, 2).map((x) => x[0].toUpperCase()).join("");

  function dayLabel(d) {
    const t = new Date(); t.setHours(0, 0, 0, 0);
    const x = new Date(d); x.setHours(0, 0, 0, 0);
    const diff = Math.round((x - t) / 864e5);
    if (diff === 0) return "Aujourd’hui";
    if (diff === 1) return "Demain";
    if (diff === -1) return "Hier";
    const s = x.toLocaleDateString("fr-FR", { weekday: "long", day: "numeric", month: "long" });
    return s.charAt(0).toUpperCase() + s.slice(1);
  }

  function item(a) {
    const k = KIND[a.interviewKind] || KIND.HR;
    const wrap = document.createElement("article");
    wrap.className = "iv-item " + k[1];
    wrap.dataset.focus = "interview:" + a.id + " application:" + a.id;
    const d = new Date(a.interviewAt);
    const visio = a.interviewType !== "ONSITE";
    const head = document.createElement("header");
    head.className = "iv-item-head";
    head.innerHTML =
      '<div class="iv-when"><b>' + d.toLocaleTimeString("fr-FR", { hour: "2-digit", minute: "2-digit" }) + "</b><small>" +
      d.toLocaleDateString("fr-FR", { day: "numeric", month: "short" }) + "</small></div>" +
      '<span class="ivx-ava" aria-hidden="true"></span>' +
      '<div class="iv-who"><strong></strong><span></span></div>' +
      '<div class="iv-tags"><span class="iv-kind"></span><span class="iv-mode ' + (visio ? "t-visio" : "t-onsite") + '">' + ico(visio ? "video" : "building") + (visio ? "Visio" : "Sur site") + "</span></div>";
    head.querySelector(".ivx-ava").textContent = initials(a.candidateName);
    head.querySelector(".iv-who strong").textContent = a.candidateName || "Candidat";
    head.querySelector(".iv-who span").textContent = a.jobTitle || "";
    head.querySelector(".iv-who strong").title = a.candidateName || "";
    head.querySelector(".iv-who span").title = a.jobTitle || "";
    head.querySelector(".iv-kind").textContent = k[0];
    const dossier = document.createElement("a");
    dossier.className = "btn btn-ghost btn-sm iv-open";
    dossier.href = "recruiter-pipeline.html?app=" + a.id + "&focus=application:" + a.id;
    dossier.textContent = "Voir le dossier";
    head.appendChild(dossier);
    const det = document.createElement("details");
    det.className = "iv-more";
    const sum = document.createElement("summary");
    sum.textContent = "Détails, report et annulation";
    det.append(sum, HireLinkInterview.card(a, { role: tab === "past" ? "readonly" : "recruiter", onChange: load }));
    if (tab === "todo") det.open = true;
    wrap.append(head, det);
    return wrap;
  }

  function groupKey(a) {
    if (groupBy === "kind") { const k = KIND[a.interviewKind] || KIND.HR; return [a.interviewKind || "HR", k[0], k[1]]; }
    if (groupBy === "offer") return ["o" + a.jobOfferId, a.jobTitle || "Offre", ""];
    const d = new Date(a.interviewAt); const key = d.toDateString();
    return [key, dayLabel(d), ""];
  }

  function chips() {
    const box = $("iv-chips"); box.replaceChildren();
    if (!offerFilter) return;
    const a = apps.find((x) => String(x.jobOfferId) === String(offerFilter));
    const c = document.createElement("button");
    c.type = "button"; c.className = "hl-ctx-chip";
    c.innerHTML = "<span>Offre : <b></b></span><i aria-hidden=\"true\">×</i>";
    c.querySelector("b").textContent = a ? a.jobTitle : "#" + offerFilter;
    c.setAttribute("aria-label", "Retirer le filtre offre");
    c.addEventListener("click", () => { offerFilter = null; paint(); });
    box.appendChild(c);
  }

  function syncUrl() {
    const u = new URL(location.href);
    const set = (k, v, def) => (v && v !== def ? u.searchParams.set(k, v) : u.searchParams.delete(k));
    set("tab", tab, "upcoming"); set("group", groupBy, "date"); set("type", mode, "all"); set("offer", offerFilter, null);
    history.replaceState(history.state, "", u.pathname + u.search + u.hash);
  }

  $("iv-toolbar").addEventListener("click", (e) => {
    const g = e.target.closest("button[data-g]"), m = e.target.closest("button[data-m]");
    if (g) groupBy = g.dataset.g;
    else if (m) mode = m.dataset.m;
    else return;
    paint();
  });

  function paint() {
    if (!loaded) return;
    syncUrl(); chips();
    $("iv-toolbar").querySelectorAll("button[data-g]").forEach((b) => b.setAttribute("aria-pressed", String(b.dataset.g === groupBy)));
    $("iv-toolbar").querySelectorAll("button[data-m]").forEach((b) => b.setAttribute("aria-pressed", String(b.dataset.m === mode)));
    const g0 = groups();
    const keep = (a) => (mode === "all" || (mode === "ONSITE") === (a.interviewType === "ONSITE")) && (!offerFilter || String(a.jobOfferId) === String(offerFilter));
    const g = { upcoming: g0.upcoming.filter(keep), todo: g0.todo.filter(keep), past: g0.past.filter(keep) };
    $("iv-tabs").querySelectorAll("button").forEach((b) => {
      b.classList.toggle("is-on", b.dataset.t === tab);
      let em = b.querySelector("em");
      if (!em) {
        em = document.createElement("em");
        b.appendChild(em);
      }
      em.textContent = g[b.dataset.t].length;
    });
    const q = (($("rc-search") || {}).value || "").trim();
    let list = S.rank(g[tab], q, [
      { get: (a) => a.candidateName, weight: 3 },
      { get: (a) => a.jobTitle, weight: 2 },
      { get: (a) => (a.interviewLocation || "") + " " + (a.interviewParticipants || ""), weight: 1 },
    ]);
    const root = $("iv-list");
    root.replaceChildren();
    if (!list.length) {
      if (q) {
        S.emptyState(root, q, {
          onClear: () => {
            $("rc-search").value = "";
            paint();
          },
        });
        return;
      }
      RC.empty(
        root,
        tab === "upcoming"
          ? {
              title: "Aucun entretien à venir",
              text: "Passez un dossier de Shortlist à Entretien dans le pipeline pour planifier un échange.",
              action: { href: "recruiter-pipeline.html", label: "Ouvrir le pipeline" },
            }
          : tab === "todo"
            ? {
                title: "Aucun entretien à replanifier",
                text: "Les entretiens annulés dont le dossier est toujours à l’étape Entretien apparaissent ici.",
              }
            : { title: "Aucun entretien passé", text: "L’historique des entretiens s’affichera ici." }
      );
      return;
    }
    const sections = new Map();
    list.forEach((a) => {
      const [key, label, cls] = groupKey(a);
      if (!sections.has(key)) {
        const sec = document.createElement("section");
        sec.className = "iv-group " + cls;
        sec.innerHTML = '<h2><i aria-hidden="true"></i><span></span><em></em></h2>';
        sec.querySelector("span").textContent = label;
        sections.set(key, { sec, n: 0 });
        root.appendChild(sec);
      }
      const s = sections.get(key);
      s.n++; s.sec.querySelector("em").textContent = s.n;
      s.sec.appendChild(item(a));
    });
    S.mark(root, q);
  }

  $("iv-tabs").addEventListener("click", (e) => {
    const b = e.target.closest("button[data-t]");
    if (b) {
      tab = b.dataset.t;
      paint();
    }
  });

  function load() {
    if (!loaded) RC.skeleton($("iv-list"), 3, 120);
    RC.data.invalidate();
    RC.data.pipeline(
      (a) => {
        apps = a || [];
        loaded = true;
        paint();
      },
      (err) => RC.errorState($("iv-list"), err.message, load)
    );
  }
  load();
  document.addEventListener("hirelink:push", load);
})(window);
