/** Agenda : vue calendrier des rendez-vous (filtres), distincte de la page Entretiens. */
(function () {
  const RC = HireLinkApp;
  const S = HireLinkSearch;
  const session = RC.mount({
    active: "agenda",
    title: "Agenda",
    search: { placeholder: "Candidat, offre, lieu…", onQuery: () => paint() },
  });
  if (!session) return;
  const $ = (id) => document.getElementById(id);
  let apps = [];
  let loaded = false;
  const filters = { type: "all", kind: "all", when: "week", offer: "all" };

  const TYPE_FR = { VISIO: "Visio", ONSITE: "Sur site", PHONE: "Téléphone", ASYNC: "Asynchrone" };
  const KIND_FR = { HR: "RH", TECHNICAL: "Technique", MANAGER: "Manager", FINAL: "Final" };

  function startOfDay(d) {
    const x = new Date(d);
    x.setHours(0, 0, 0, 0);
    return x.getTime();
  }

  function dayKey(iso) {
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) return "";
    return d.getFullYear() + "-" + String(d.getMonth() + 1).padStart(2, "0") + "-" + String(d.getDate()).padStart(2, "0");
  }

  function dayLabel(key) {
    const [y, m, d] = key.split("-").map(Number);
    const date = new Date(y, m - 1, d);
    const today0 = startOfDay(Date.now());
    const t0 = startOfDay(date);
    const prefix = t0 === today0 ? "Aujourd’hui · " : t0 === today0 + 864e5 ? "Demain · " : "";
    return prefix + date.toLocaleDateString("fr-MA", { weekday: "long", day: "numeric", month: "long" });
  }

  function matchesWhen(a) {
    if (filters.when === "all") return true;
    const t = new Date(a.interviewAt).getTime();
    if (Number.isNaN(t)) return false;
    const today0 = startOfDay(Date.now());
    if (filters.when === "today") return t >= today0 && t < today0 + 864e5;
    if (filters.when === "week") return t >= today0 && t < today0 + 7 * 864e5;
    if (filters.when === "month") return t >= today0 && t < today0 + 30 * 864e5;
    return true;
  }

  function upcoming() {
    const now = Date.now() - 3600e3;
    return apps.filter((a) => {
      if (!a.interviewAt || a.status !== "INTERVIEW" || a.interviewStatus === "CANCELLED") return false;
      if (new Date(a.interviewAt).getTime() < now) return false;
      if (filters.type !== "all" && a.interviewType !== filters.type) return false;
      if (filters.kind !== "all" && (a.interviewKind || "HR") !== filters.kind) return false;
      if (filters.offer !== "all" && String(a.jobOfferId) !== String(filters.offer)) return false;
      if (!matchesWhen(a)) return false;
      return true;
    });
  }

  function rebuildOfferFilter() {
    const sel = $("ag-f-offer");
    if (!sel) return;
    const prev = sel.value || "all";
    const map = new Map();
    apps.forEach((a) => {
      if (a.jobOfferId == null) return;
      if (!map.has(a.jobOfferId)) map.set(a.jobOfferId, a.jobTitle || "Offre #" + a.jobOfferId);
    });
    sel.innerHTML = ['<option value="all">Toutes les offres</option>']
      .concat(
        Array.from(map.entries())
          .sort((a, b) => String(a[1]).localeCompare(String(b[1]), "fr"))
          .map(([id, title]) => '<option value="' + id + '">' + RC.esc(title) + "</option>")
      )
      .join("");
    sel.value = Array.from(map.keys()).some((id) => String(id) === String(prev)) || prev === "all" ? prev : "all";
    filters.offer = sel.value;
  }

  function row(a) {
    const el = document.createElement("article");
    el.className = "ag-row";
    const time = document.createElement("time");
    time.dateTime = a.interviewAt;
    const d = new Date(a.interviewAt);
    time.textContent = Number.isNaN(d.getTime())
      ? "—"
      : d.toLocaleTimeString("fr-FR", { hour: "2-digit", minute: "2-digit" });
    const main = document.createElement("div");
    main.className = "ag-row-main";
    const title = document.createElement("strong");
    title.textContent = a.candidateName || "Candidat";
    const meta = document.createElement("span");
    meta.textContent = [
      a.jobTitle || "",
      KIND_FR[a.interviewKind] || "",
      TYPE_FR[a.interviewType] || "",
      a.interviewLocation || "",
    ]
      .filter(Boolean)
      .join(" · ");
    main.append(title, meta);
    const actions = document.createElement("div");
    actions.className = "ag-row-actions";
    const dossier = document.createElement("a");
    dossier.className = "btn btn-ghost btn-sm";
    dossier.href = "recruiter-pipeline.html?app=" + a.id;
    dossier.textContent = "Dossier";
    const iv = document.createElement("a");
    iv.className = "btn btn-ghost btn-sm";
    iv.href = "recruiter-interviews.html";
    iv.textContent = "Entretien";
    actions.append(dossier, iv);
    el.append(time, main, actions);
    return el;
  }

  function paint() {
    if (!loaded) return;
    const q = (($("rc-search") || {}).value || "").trim();
    let list = S.rank(upcoming(), q, [
      { get: (a) => a.candidateName, weight: 3 },
      { get: (a) => a.jobTitle, weight: 2 },
      { get: (a) => a.interviewLocation || "", weight: 1 },
    ]).sort((x, y) => new Date(x.interviewAt) - new Date(y.interviewAt));

    const root = $("ag-list");
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
      RC.empty(root, {
        title: "Rien sur cette période",
        text: "Aucun rendez-vous à venir avec ces filtres. Planifiez depuis le pipeline ou élargissez la période.",
        action: { href: "recruiter-pipeline.html", label: "Ouvrir le pipeline" },
      });
      return;
    }

    const byDay = new Map();
    list.forEach((a) => {
      const k = dayKey(a.interviewAt);
      if (!k) return;
      if (!byDay.has(k)) byDay.set(k, []);
      byDay.get(k).push(a);
    });
    byDay.forEach((items, key) => {
      const day = document.createElement("section");
      day.className = "ag-day";
      const h = document.createElement("h2");
      h.textContent = dayLabel(key);
      day.appendChild(h);
      items.forEach((a) => day.appendChild(row(a)));
      root.appendChild(day);
    });
    S.mark(root, q);
  }

  function resetFilters() {
    filters.type = "all";
    filters.kind = "all";
    filters.when = "week";
    filters.offer = "all";
    $("ag-f-type").value = "all";
    $("ag-f-kind").value = "all";
    $("ag-f-when").value = "week";
    $("ag-f-offer").value = "all";
    paint();
  }

  ["ag-f-type", "ag-f-kind", "ag-f-when", "ag-f-offer"].forEach((id) => {
    $(id).addEventListener("change", () => {
      filters[id.replace("ag-f-", "")] = $(id).value || "all";
      paint();
    });
  });
  $("ag-f-reset").addEventListener("click", resetFilters);

  function load() {
    if (!loaded) RC.skeleton($("ag-list"), 3, 88);
    RC.data.invalidate();
    RC.data.pipeline(
      (a) => {
        apps = a || [];
        loaded = true;
        rebuildOfferFilter();
        paint();
      },
      (err) => RC.errorState($("ag-list"), err.message, load)
    );
  }
  load();
  document.addEventListener("hirelink:push", load);
})();
