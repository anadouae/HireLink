/**
 * Champs à suggestions ([data-suggest] : job, place, nationality, school, level, study, domain, contract, mode, company) :
 * liste de HireLinkSearch avec drapeaux pour les lieux et les nationalités.
 * « company » : noms d'entreprises fournis par le serveur (2 caractères minimum, 300 ms d'attente, 8 au plus) ;
 * un écran peut fournir input.hlSuggestParams() → { context } (entreprises des résultats affichés).
 */
(function () {
  function base() {
    return ((window.HireLinkAPI && HireLinkAPI.API_BASE) || window.HIRELINK_API_BASE || "http://127.0.0.1:8081/api/v1").replace(/\/$/, "");
  }

  function mount(input) {
    const kind = input.getAttribute("data-suggest");
    if (!kind || input.dataset.suggestReady === "1") return;
    input.dataset.suggestReady = "1";
    const withFlag = kind === "place" || kind === "city" || kind === "nationality";
    const isCompany = kind === "company";
    let flagEl = null;

    const combo = HireLinkSearch.combobox(input, {
      minChars: isCompany ? 2 : (kind === "job" ? 1 : 0),
      delay: isCompany ? 300 : undefined,
      emptyText: isCompany
        ? "Aucune entreprise ne correspond : le texte saisi filtre quand même les résultats."
        : "Aucune proposition : vous pouvez saisir librement.",
      // Entreprise : Entrée sans suggestion choisie = filtre texte libre sur les résultats.
      onEnter: isCompany ? function () {
        input.dispatchEvent(new Event("change", { bubbles: true }));
      } : undefined,
      fetch: function (q) {
        let url = base() + "/catalog/suggest?kind=" + encodeURIComponent(kind) + "&q=" + encodeURIComponent(q);
        if (isCompany && typeof input.hlSuggestParams === "function") {
          const extra = input.hlSuggestParams() || {};
          if (extra.context) url += "&context=" + encodeURIComponent(extra.context);
        }
        // Lieu / ville : respecter le pays du formulaire (MA par défaut sur les écrans catalogue).
        if (kind === "city" || kind === "place") {
          const countryEl = document.getElementById("f-country") || document.getElementById("q-country") || document.getElementById("pf-country");
          const country = countryEl && countryEl.value && countryEl.value !== "ALL" ? countryEl.value : "";
          if (country) url += "&country=" + encodeURIComponent(country);
        }
        return fetch(url)
          .then(function (r) { return r.ok ? r.json() : []; })
          .then(function (rows) {
            return rows.map(function (row) {
              return { key: row.label, label: row.label, flag: row.flag, sub: row.country, icon: row.icon || "" };
            });
          });
      },
      onPick: function (item) {
        input.value = item.label;
        input.dispatchEvent(new Event("input", { bubbles: true }));
        input.dispatchEvent(new Event("change", { bubbles: true }));
        paintFlag();
      },
    });

    function paintFlag() {
      if (!flagEl) return;
      const flag = window.HireLinkPlaces ? HireLinkPlaces.flagOf(input.value) : "";
      flagEl.textContent = flag;
      input.classList.toggle("has-flag", !!flag);
    }

    if (withFlag && window.HireLinkPlaces) {
      flagEl = document.createElement("span");
      flagEl.className = "hl-input-flag";
      flagEl.setAttribute("aria-hidden", "true");
      combo.wrap.appendChild(flagEl);
      input.addEventListener("input", paintFlag);
      input.addEventListener("change", paintFlag);
      HireLinkPlaces.ready().then(paintFlag);
    }
  }

  function boot() {
    document.querySelectorAll("[data-suggest]").forEach(mount);
  }

  window.HireLinkSuggest = { mount: mount, boot: boot };
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", boot);
  else boot();
})();
