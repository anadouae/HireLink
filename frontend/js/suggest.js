(function () {
  const base = function () {
    return (window.HIRELINK_API_BASE || (window.HireLinkAPI && HireLinkAPI.API_BASE) || "http://127.0.0.1:8081/api/v1");
  };

  function labelOf(row) {
    if (row && typeof row === "object") return row.label || "";
    return String(row || "");
  }

  function mount(input) {
    if (!input || input.dataset.suggestReady === "1") return;
    const kind = input.getAttribute("data-suggest");
    if (!kind) return;
    input.dataset.suggestReady = "1";
    input.setAttribute("autocomplete", "off");
    const wrap = document.createElement("div");
    wrap.className = "suggest-wrap";
    input.parentNode.insertBefore(wrap, input);
    wrap.appendChild(input);
    const menu = document.createElement("div");
    menu.className = "suggest-menu";
    menu.hidden = true;
    wrap.appendChild(menu);
    let timer = 0;
    let rows = [];

    function close() { menu.hidden = true; }

    function paint(list) {
      rows = list || [];
      if (!rows.length || document.activeElement !== input) { close(); return; }
      menu.innerHTML = rows.map(function (row, i) {
        const label = labelOf(row);
        const flag = row && row.flag ? row.flag + " " : "";
        const country = row && row.country ? "<small>" + row.country + "</small>" : "";
        return "<button type='button' data-i='" + i + "'>" + flag + label + country + "</button>";
      }).join("");
      menu.hidden = false;
      menu.querySelectorAll("button").forEach(function (btn) {
        btn.addEventListener("mousedown", function (e) {
          e.preventDefault();
          const row = rows[Number(btn.getAttribute("data-i"))];
          input.value = labelOf(row);
          input.dispatchEvent(new Event("change", { bubbles: true }));
          close();
        });
      });
    }

    function load(q) {
      const url = base().replace(/\/$/, "") + "/catalog/suggest?kind=" + encodeURIComponent(kind) + "&q=" + encodeURIComponent(q || "");
      fetch(url).then(function (r) { return r.ok ? r.json() : []; }).then(paint).catch(function () { close(); });
    }

    input.addEventListener("focus", function () { load(input.value.trim()); });
    input.addEventListener("input", function () {
      clearTimeout(timer);
      timer = setTimeout(function () { load(input.value.trim()); }, 180);
    });
    input.addEventListener("blur", function () { setTimeout(close, 160); });
  }

  function boot() {
    document.querySelectorAll("[data-suggest]").forEach(mount);
  }

  window.HireLinkSuggest = { labelOf: labelOf, mount: mount, boot: boot };
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", boot);
  else boot();
})();
