/** Centre de notifications : liste complète, filtres, lecture et suppression. */
(function () {
  const RC = HireLinkApp;
  const session = RC.mount({ active: "notifications", title: "Notifications", search: { placeholder: "Rechercher dans les notifications…", onQuery: () => paint() } });
  if (!session) return;
  const $ = (id) => document.getElementById(id);
  const esc = RC.esc;
  let rows = [];
  let view = "all";
  let loaded = false;

  const KIND_FR = { STATUS: "Candidature", INTERVIEW: "Entretien", SLA: "Délai", ASSIGN: "Assignation", APPLICATION: "Nouvelle candidature", MODERATION: "Modération", SYSTEM: "Équipe", HIRED: "Embauche" };

  function paint() {
    if (!loaded) return;
    const q = (($("rc-search") || {}).value || "").trim();
    $("n-chips").querySelectorAll("button").forEach((b) => {
      b.classList.toggle("is-on", b.dataset.v === view);
      let em = b.querySelector("em");
      if (!em) { em = document.createElement("em"); b.appendChild(em); }
      em.textContent = b.dataset.v === "unread" ? rows.filter((n) => !n.read).length : rows.length;
    });
    let list = rows.filter((n) => view === "all" || !n.read);
    list = HireLinkSearch.rank(list, q, [{ get: (n) => n.title, weight: 3 }, { get: (n) => n.body, weight: 1 }]);
    const root = $("n-list");
    if (!list.length) {
      if (q) { HireLinkSearch.emptyState(root, q, { onClear: () => { $("rc-search").value = ""; paint(); } }); return; }
      RC.empty(root, { title: view === "unread" ? "Tout est lu" : "Aucune notification", text: "Les nouvelles candidatures, changements de statut et rappels de délai apparaîtront ici." });
      return;
    }
    root.innerHTML = list.map((n) =>
      '<div class="rc-item rc-fade" data-id="' + n.id + '"><div class="rc-avatar" style="' + (n.read ? "" : "background:var(--burgundy);color:#fff") + '">' +
      RC.svg("bell").replace("<svg", '<svg width="17" height="17" fill="none" stroke="currentColor" stroke-width="1.8"') + '</div><div class="rc-item-main"><strong>' + esc(n.title) +
      '</strong><span style="white-space:normal">' + esc(n.body) + '</span></div><span class="rc-pill">' + esc(KIND_FR[n.kind] || n.kind || "Info") + '</span><small class="rc-muted" style="min-width:86px;text-align:right">' + esc(RC.ago(n.at)) +
      '</small><div class="rc-row-actions">' + (n.read ? "" : '<button type="button" class="btn btn-ghost btn-sm" data-read>Marquer lu</button>') +
      '<button type="button" class="btn btn-ghost btn-sm" data-del aria-label="Supprimer">✕</button></div></div>').join("");
    HireLinkSearch.mark(root, q);
  }

  function sync(list) { rows = list || []; loaded = true; paint(); }
  $("n-list").addEventListener("click", async (e) => {
    const row = e.target.closest(".rc-item");
    if (!row) return;
    const id = row.dataset.id;
    if (!e.target.closest("button")) {
      const n = rows.find((x) => String(x.id) === id);
      await RC.notifications.markRead(id);
      location.href = RC.notifications.target(n);
      return;
    }
    if (e.target.closest("[data-del]")) {
      try {
        await HireLinkAPI.api("/notifications/" + id, { method: "DELETE" });
        rows = rows.filter((n) => String(n.id) !== id);
        paint();
        RC.notifications.refresh();
      } catch (err) { RC.toast("Notifications", err.message, "danger"); }
    } else if (e.target.closest("[data-read]")) {
      await RC.notifications.markRead(id);
      sync(RC.notifications.all());
    }
  });
  $("n-chips").addEventListener("click", (e) => { const b = e.target.closest("button[data-v]"); if (b) { view = b.dataset.v; paint(); } });
  $("btn-readall").addEventListener("click", async (e) => {
    RC.busy(e.currentTarget, true);
    await RC.notifications.markAllRead();
    RC.busy(e.currentTarget, false);
    sync(RC.notifications.all());
  });
  document.addEventListener("rc:notifications", (e) => sync(e.detail));
  RC.skeleton($("n-list"), 4, 56);
  RC.notifications.refresh().then(() => {
    if (!loaded) sync(RC.notifications.all());
  });
})();
