(function () {
  const session = HireLinkShell.mountShell({ roles: ["CANDIDAT"], hello: false });
  if (!session) return;
  if (window.HireLinkProduct) HireLinkProduct.mountPremiumBar("candidate");

  const offerList = document.getElementById("offer-list");
  let cached = [];
  const qParam = new URLSearchParams(location.search).get("q");
  if (qParam) document.getElementById("search-offers").value = qParam;

  function inferWorkMode(location, contract) {
    const blob = ((location || "") + " " + (contract || "")).toLowerCase();
    if (/remote|télétravail|teletravail|full\s*remote|100%\s*remote/.test(blob)) return "Remote";
    if (/hybride|hybrid/.test(blob)) return "Hybride";
    if (/sur\s*site|onsite|présentiel|presentiel|bureau/.test(blob)) return "Présentiel";
    return location ? "Sur site" : "Mode à préciser";
  }

  function openApply(o) {
    if (!window.HireLinkApply) {
      HireLink.toast("Erreur", "Assistant de candidature indisponible", "danger");
      return;
    }
    HireLinkApply.start(o, load);
  }

  function render(offers) {
    const q = (document.getElementById("search-offers").value || "").trim().toLowerCase();
    const list = q
      ? offers.filter((o) =>
          (o.title + " " + (o.requiredSkills || "") + " " + (o.location || "")).toLowerCase().includes(q)
        )
      : offers;
    document.getElementById("offers-count").textContent = list.length + " offre" + (list.length > 1 ? "s" : "");
    offerList.innerHTML = "";
    if (!list.length) {
      offerList.innerHTML =
        '<div class="empty"><div class="empty-icon">◎</div><p>Aucune offre pour ce filtre. Enrichissez votre profil ou élargissez la recherche.</p></div>';
      return;
    }
    list.forEach((o) => {
      const el = document.createElement("article");
      el.className = "offer";
      const matchPct = o.matchScore != null ? Math.round(o.matchScore) : null;
      const matchCls =
        matchPct == null
          ? "badge-chrome"
          : matchPct >= 80
            ? "badge-ok match-hot"
            : matchPct >= 55
              ? "badge-info"
              : "badge-rose";
      const workMode = inferWorkMode(o.location || "", o.contractType || "");
      el.innerHTML =
        "<div><h3></h3>" +
        '<p class="offer-essentials muted"></p>' +
        '<div class="offer-meta"></div>' +
        (matchPct != null
          ? '<div class="match-bar"><span style="width:' + Math.min(100, matchPct) + '%"></span></div>'
          : "") +
        "</div><div class='stack'><span class='badge " +
        matchCls +
        "'></span>" +
        "<button type='button' class='btn btn-soft btn-sm js-detail'>Voir l’offre</button>" +
        "<button type='button' class='btn btn-primary btn-sm js-apply'>Postuler</button>" +
        "<button type='button' class='btn btn-ghost btn-sm js-report'>Signaler</button></div>";
      el.querySelector("h3").textContent = o.title;
      el.querySelector(".offer-essentials").textContent = [
        o.location || "Lieu à préciser",
        workMode,
        o.contractType || null,
        o.responseSlaDays != null ? "SLA " + o.responseSlaDays + " j" : null,
      ]
        .filter(Boolean)
        .join(" · ");
      el.querySelector(".badge").textContent =
        matchPct != null ? matchPct + "% compatibilité" : "Matching à calculer";
      const meta = el.querySelector(".offer-meta");
      (o.requiredSkills || "")
        .split(",")
        .slice(0, 5)
        .forEach((t) => {
          if (!t || !String(t).trim()) return;
          const b = document.createElement("span");
          b.className = "badge badge-chrome";
          b.textContent = String(t).trim();
          meta.appendChild(b);
        });
      el.querySelector(".js-detail").addEventListener("click", () => openApply(o));
      el.querySelector(".js-apply").addEventListener("click", () => openApply(o));
      el.querySelector(".js-report").addEventListener("click", async (e) => {
        e.stopPropagation();
        const ok = await HireLink.openModal({
          title: "Signaler cette annonce",
          description: o.title,
          bodyHtml:
            '<p class="muted">3 signalements → suspension automatique (anti fake jobs).</p>' +
            '<div class="field"><label>Motif</label><select id="rep-reason">' +
            '<option value="FAKE_JOB">Fausse annonce / arnaque</option>' +
            '<option value="MONEY_ASK">Demande d’argent</option>' +
            '<option value="OTHER">Autre</option></select></div>',
          confirmLabel: "Envoyer le signalement",
          danger: true,
        });
        if (!ok) return;
        const reason = (document.getElementById("rep-reason") || {}).value || "FAKE_JOB";
        try {
          const r = await HireLinkAPI.api("/offers/" + o.id + "/report", {
            method: "POST",
            body: { reason },
          });
          HireLink.toast(
            r.suspendedNow ? "Offre suspendue" : "Signalement enregistré",
            r.suspendedNow
              ? "Seuil atteint (" + r.reportCount + "/" + r.threshold + ")"
              : r.reportCount + "/" + r.threshold + " signalements",
            r.suspendedNow ? "warn" : "ok"
          );
          if (r.suspendedNow) load();
        } catch (err) {
          HireLink.toast("Signalement", err.message, "danger");
        }
      });
      el.querySelector("h3").style.cursor = "pointer";
      el.querySelector("h3").addEventListener("click", () => openApply(o));
      offerList.appendChild(el);
    });
  }

  async function load() {
    offerList.innerHTML = '<div class="skeleton" style="height:80px"></div>';
    try {
      cached = await HireLinkAPI.api("/offers/public");
      cached.sort((a, b) => (b.matchScore || 0) - (a.matchScore || 0));
      if (window.HireLinkProduct) {
        HireLinkProduct.renderMatchAlerts(document.getElementById("match-alerts"), cached, { limit: 3 });
      }
      render(cached);
    } catch (err) {
      offerList.innerHTML = "<p class='muted'>" + err.message + "</p>";
    }
  }

  HireLinkShell.bindSearch(document.getElementById("search-offers"), () => render(cached));
  document.getElementById("btn-premium").addEventListener("click", () => {
    if (window.HireLinkGrowth) HireLinkGrowth.showPremium("candidate");
  });
  load();
})();
