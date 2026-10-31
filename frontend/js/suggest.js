/**
 * Champs à suggestions ([data-suggest] : job, place, nationality, school, level, contract, mode) :
 * liste de HireLinkSearch avec drapeaux pour les lieux et les nationalités.
 */
(function () {
  function base() {
    return ((window.HireLinkAPI && HireLinkAPI.API_BASE) || window.HIRELINK_API_BASE || "http://127.0.0.1:8081/api/v1").replace(/\/$/, "");
  }

  function mount(input) {
    const kind = input.getAttribute("data-suggest");
    if (!kind || input.dataset.suggestReady === "1") return;
    input.dataset.suggestReady = "1";
    const withFlag = kind === "place" || kind === "nationality";
    let flagEl = null;

    const combo = HireLinkSearch.combobox(input, {
      minChars: kind === "job" ? 1 : 0,
      emptyText: "Aucune proposition : vous pouvez saisir librement.",
      fetch: function (q) {
        return fetch(base() + "/catalog/suggest?kind=" + encodeURIComponent(kind) + "&q=" + encodeURIComponent(q))
          .then(function (r) { return r.ok ? r.json() : []; })
          .then(function (rows) {
            return rows.map(function (row) { return { key: row.label, label: row.label, flag: row.flag, sub: row.country }; });
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
