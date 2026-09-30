(function () {
  const session = HireLinkShell.mountShell({ roles: ["RECRUTEUR"], hello: false });
  if (!session) return;
  if (window.HireLinkProduct) HireLinkProduct.mountPremiumBar("recruiter");

  let apps = [];
  let offers = [];

  function paint() {
    const counts = {};
    apps.forEach((a) => {
      counts[a.jobOfferId] = (counts[a.jobOfferId] || 0) + 1;
    });
    const q = ((document.getElementById("search-offers-rec") || {}).value || "").trim().toLowerCase();
    const list = q
      ? offers.filter((o) =>
          ((o.title || "") + " " + (o.location || "") + " " + (o.requiredSkills || "") + " " + (o.contractType || ""))
            .toLowerCase()
            .includes(q)
        )
      : offers;
    const tbody = document.getElementById("offers-body");
    tbody.innerHTML = "";
    if (!list.length) {
      tbody.innerHTML = q
        ? "<tr><td colspan='4' class='muted'>Aucun résultat pour « " + q + " ».</td></tr>"
        : "<tr><td colspan='4' class='muted'>Aucune offre — créez-en une.</td></tr>";
      return;
    }
    list.forEach((o) => {
      const tr = document.createElement("tr");
      tr.innerHTML = "<td></td><td></td><td></td><td></td>";
      tr.children[0].textContent = o.title;
      tr.children[1].textContent = o.responseSlaDays + " j";
      tr.children[2].textContent = String(counts[o.id] || 0);
      tr.children[3].innerHTML = o.published
        ? '<span class="badge badge-ok">Publiée</span>'
        : '<span class="badge badge-warn">Brouillon</span>';
      tbody.appendChild(tr);
    });
  }

  async function load() {
    try {
      [offers, apps] = await Promise.all([
        HireLinkAPI.api("/offers/mine"),
        HireLinkAPI.api("/applications/pipeline"),
      ]);
      paint();
    } catch (err) {
      HireLink.toast("Offres", err.message, "danger");
    }
  }

  document.getElementById("btn-new-offer").addEventListener("click", () => {
    if (window.HireLinkOfferWizard) {
      HireLinkOfferWizard.start(load);
    } else {
      HireLink.toast("Erreur", "Assistant offre indisponible", "danger");
    }
  });

  const up = document.getElementById("btn-upgrade");
  if (up) up.onclick = () => HireLinkGrowth && HireLinkGrowth.showPremium("recruiter");

  HireLinkShell.bindSearch(document.getElementById("search-offers-rec"), () => paint());
  load();
})();
