/** Centre de notifications : liste, filtres (lu / type / date), lecture et suppression. */
(function () {
  const RC = HireLinkApp;
  const session = RC.mount({
    active: "notifications",
    title: "Notifications",
    search: { placeholder: "Rechercher dans les notifications…", onQuery: () => paint() },
  });
  if (!session) return;
  const $ = (id) => document.getElementById(id);
  const esc = RC.esc;
  let rows = [];
  let view = "all";
  let kindFilter = "all";
  let whenFilter = "all";
  let loaded = false;

  const KIND_FR = {
    STATUS: "Candidature",
    INTERVIEW: "Entretien",
    SLA: "Délai",
    ASSIGN: "Assignation",
    APPLICATION: "Nouvelle candidature",
    MODERATION: "Modération",
    SYSTEM: "Équipe",
    HIRED: "Embauche",
  };

  function notifTime(n) {
    const t = new Date(n.at || n.createdAt || 0).getTime();
    return Number.isNaN(t) ? 0 : t;
  }

  function matchesWhen(n) {
    if (whenFilter === "all") return true;
    const t = notifTime(n);
    if (!t) return false;
    const now = Date.now();
    const today0 = new Date();
    today0.setHours(0, 0, 0, 0);
    if (whenFilter === "today") return t >= today0.getTime();
    if (whenFilter === "7d") return t >= now - 7 * 864e5;
    if (whenFilter === "30d") return t >= now - 30 * 864e5;
    return true;
  }

  function paint() {
    if (!loaded) return;
    const q = (($("rc-search") || {}).value || "").trim();
    $("n-chips").querySelectorAll("button").forEach((b) => {
      b.classList.toggle("is-on", b.dataset.v === view);
      let em = b.querySelector("em");
      if (!em) {
        em = document.createElement("em");
        b.appendChild(em);
      }
      const base = rows.filter((n) => (kindFilter === "all" || n.kind === kindFilter) && matchesWhen(n));
      em.textContent = b.dataset.v === "unread" ? base.filter((n) => !n.read).length : base.length;
      b.setAttribute("aria-pressed", b.dataset.v === view ? "true" : "false");
    });
    const unreadAll = rows.filter((n) => !n.read).length;
    $("btn-readall").disabled = unreadAll === 0;
    $("btn-readall").title = unreadAll === 0 ? "Aucune notification non lue" : "";
    let list = rows.filter((n) => {
      if (view === "unread" && n.read) return false;
      if (kindFilter !== "all" && n.kind !== kindFilter) return false;
      if (!matchesWhen(n)) return false;
      return true;
    });
    list = HireLinkSearch.rank(list, q, [
      { get: (n) => n.title, weight: 3 },
      { get: (n) => n.body, weight: 1 },
      { get: (n) => KIND_FR[n.kind] || n.kind, weight: 1 },
    ]);
    const root = $("n-list");
    if (!list.length) {
      if (q) {
        HireLinkSearch.emptyState(root, q, {
          onClear: () => {
            $("rc-search").value = "";
            paint();
          },
        });
        return;
      }
      RC.empty(root, {
        title: view === "unread" ? "Tout est lu" : "Aucune notification",
        text:
          kindFilter !== "all" || whenFilter !== "all"
            ? "Aucun résultat pour ces filtres — changez le type ou la période."
            : "Les nouvelles candidatures, changements de statut et rappels de délai apparaîtront ici.",
      });
      return;
    }
    root.innerHTML = list
      .map(
        (n) =>
          '<div class="rc-item rc-fade" data-id="' +
          n.id +
          '"><div class="rc-avatar" style="' +
          (n.read ? "" : "background:var(--burgundy);color:#fff") +
          '">' +
          RC.svg("bell").replace("<svg", '<svg width="17" height="17" fill="none" stroke="currentColor" stroke-width="1.8"') +
          '</div><div class="rc-item-main"><strong>' +
          esc(n.title) +
          '</strong><span style="white-space:normal">' +
          esc(n.body) +
          '</span></div><span class="rc-pill">' +
          esc(KIND_FR[n.kind] || n.kind || "Info") +
          '</span><small class="rc-muted" style="min-width:86px;text-align:right">' +
          esc(RC.ago(n.at)) +
          '</small><div class="rc-row-actions">' +
          (n.read ? "" : '<button type="button" class="btn btn-ghost btn-sm" data-read>Marquer lu</button>') +
          '<button type="button" class="btn btn-ghost btn-sm" data-del aria-label="Supprimer">✕</button></div></div>'
      )
      .join("");
    HireLinkSearch.mark(root, q);
  }

  function sync(list) {
    rows = list || [];
    loaded = true;
    paint();
  }
  $("n-list").addEventListener("click", async (e) => {
    const row = e.target.closest(".rc-item");
    if (!row) return;
    const id = row.dataset.id;
    if (!e.target.closest("button")) {
      const n = rows.find((x) => String(x.id) === id);
      await RC.notifications.markRead(id);
      if (n) RC.notifications.open(n);
      return;
    }
    if (e.target.closest("[data-del]")) {
      try {
        await HireLinkAPI.api("/notifications/" + id, { method: "DELETE" });
        rows = rows.filter((n) => String(n.id) !== id);
        paint();
        RC.notifications.refresh();
      } catch (err) {
        RC.toast("Notifications", err.message, "danger");
      }
    } else if (e.target.closest("[data-read]")) {
      await RC.notifications.markRead(id);
      sync(RC.notifications.all());
    }
  });
  $("n-chips").addEventListener("click", (e) => {
    const b = e.target.closest("button[data-v]");
    if (b) {
      view = b.dataset.v;
      paint();
    }
  });
  const kindSel = $("n-f-kind");
  if (kindSel)
    kindSel.addEventListener("change", () => {
      kindFilter = kindSel.value || "all";
      paint();
    });
  const whenSel = $("n-f-when");
  if (whenSel)
    whenSel.addEventListener("change", () => {
      whenFilter = whenSel.value || "all";
      paint();
    });
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
