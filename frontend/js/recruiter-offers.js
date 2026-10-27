(function () {
  const session = HireLinkShell.mountShell({ roles: ["RECRUTEUR"], hello: false });
  if (!session) return;
  if (window.HireLinkProduct) HireLinkProduct.mountPremiumBar("recruiter");

  let apps = [];
  let offers = [];
  let offerView = "all";

  function paint() {
    const counts = {};
    apps.forEach((a) => {
      counts[a.jobOfferId] = (counts[a.jobOfferId] || 0) + 1;
    });
    const q = ((document.getElementById("search-offers-rec") || {}).value || "").trim().toLowerCase();
    const searched = q
      ? offers.filter((o) =>
          ((o.title || "") + " " + (o.location || "") + " " + (o.requiredSkills || "") + " " + (o.contractType || ""))
            .toLowerCase()
            .includes(q)
        )
      : offers.slice();
    const viewCounts = {
      all: searched.length,
      published: searched.filter((o) => o.published).length,
      draft: searched.filter((o) => !o.published).length,
      with: searched.filter((o) => (counts[o.id] || 0) > 0).length,
    };
    const statusBar = document.getElementById("offer-status-filter");
    if (statusBar) {
      statusBar.querySelectorAll("button").forEach((b) => {
        const key = b.getAttribute("data-ov") || "all";
        b.classList.toggle("is-on", key === offerView);
        let badge = b.querySelector(".filter-count");
        if (!badge) {
          badge = document.createElement("span");
          badge.className = "filter-count";
          b.appendChild(badge);
        }
        badge.textContent = String(viewCounts[key] || 0);
      });
    }
    const summary = document.getElementById("offers-rec-summary");
    const list = searched.filter((o) => {
      if (offerView === "published") return !!o.published;
      if (offerView === "draft") return !o.published;
      if (offerView === "with") return (counts[o.id] || 0) > 0;
      return true;
    });
    if (summary) {
      summary.textContent = list.length + " offre" + (list.length > 1 ? "s" : "");
    }
    const tbody = document.getElementById("offers-body");
    tbody.innerHTML = "";
    if (!list.length) {
      tbody.innerHTML = q
        ? "<tr><td colspan='4' class='muted'>Aucun résultat pour « " + q + " ».</td></tr>"
        : "<tr><td colspan='5' class='muted'>Aucune offre pour ce filtre. Le bouton Nouvelle offre est au-dessus du tableau.</td></tr>";
      return;
    }
    list.forEach((o) => {
      const tr = document.createElement("tr");
      tr.innerHTML = "<td></td><td></td><td></td><td></td><td></td>";
      tr.children[0].textContent = o.title;
      tr.children[1].textContent = o.responseSlaDays + " j";
      tr.children[2].textContent = String(counts[o.id] || 0);
      tr.children[3].innerHTML = o.published
        ? '<span class="badge badge-ok">Publiée</span>'
        : '<span class="badge badge-warn">Brouillon</span>';
      const actions = document.createElement("div");
      actions.style.display = "flex";
      actions.style.gap = "0.35rem";
      const btnPipe = document.createElement("a");
      btnPipe.className = "btn btn-soft btn-sm";
      btnPipe.href = "recruiter-pipeline.html?offer=" + encodeURIComponent(o.id);
      btnPipe.textContent = "Candidatures";
      const btnEdit = document.createElement("button");
      btnEdit.className = "btn btn-ghost btn-sm";
      btnEdit.textContent = "Modifier";
      btnEdit.onclick = () => {
        if (window.HireLinkOfferWizard && HireLinkOfferWizard.edit) {
          HireLinkOfferWizard.edit(o, load);
        } else {
          HireLink.toast("Info", "Ouvrez l'assistant pour modifier (recréer / PUT)", "info");
        }
      };
      const btnDel = document.createElement("button");
      btnDel.className = "btn btn-ghost btn-sm";
      btnDel.textContent = "Supprimer";
      btnDel.onclick = async () => {
        if (!confirm("Supprimer l'offre « " + o.title + " » ?")) return;
        try {
          await HireLinkAPI.api("/offers/" + o.id, { method: "DELETE" });
          HireLink.toast("Offre", "Supprimée", "ok");
          load();
        } catch (err) {
          HireLink.toast("Erreur", err.message, "danger");
        }
      };
      actions.appendChild(btnPipe);
      actions.appendChild(btnEdit);
      actions.appendChild(btnDel);
      tr.children[4].appendChild(actions);
      tbody.appendChild(tr);
    });
  }

  let offerStamp = "";

  async function load(quiet) {
    try {
      [offers, apps] = await Promise.all([
        HireLinkAPI.api("/offers/mine"),
        HireLinkAPI.api("/applications/pipeline"),
      ]);
      const nextStamp = (offers || []).map((o) => o.id + ":" + (o.published ? "1" : "0") + ":" + (o.title || "")).join("|")
        + "~" + (apps || []).map((a) => a.id + ":" + a.status + ":" + (a.jobOfferId || "")).join("|");
      if (quiet && nextStamp === offerStamp) return;
      offerStamp = nextStamp;
      paint();
    } catch (err) {
      if (!quiet) HireLink.toast("Offres", err.message, "danger");
    }
  }

  if (location.hash === "#new" && window.HireLinkOfferWizard) {
    HireLinkOfferWizard.start(load);
    history.replaceState(null, "", location.pathname);
  }

  const statusFilter = document.getElementById("offer-status-filter");
  if (statusFilter) {
    statusFilter.addEventListener("click", (e) => {
      const btn = e.target.closest("button[data-ov]");
      if (!btn) return;
      offerView = btn.getAttribute("data-ov") || "all";
      paint();
    });
  }
  document.getElementById("btn-new-offer").addEventListener("click", () => {
    if (window.HireLinkOfferWizard) {
      HireLinkOfferWizard.start(load);
    } else {
      HireLink.toast("Erreur", "Assistant offre indisponible", "danger");
    }
  });

  HireLinkShell.bindSearch(document.getElementById("search-offers-rec"), () => paint());
  load();
  if (window.HireLinkShell && HireLinkShell.watch) HireLinkShell.watch(function () { return load(true); });
})();
