/**
 * Catalogue compétences / langages / domaines — style LinkedIn (formulaires HireLink)
 */
(function (global) {
  const DOMAINS = [
    {
      id: "dev",
      label: "Développement",
      items: [
        "Java", "Spring Boot", "Python", "JavaScript", "TypeScript", "React", "Angular", "Vue.js",
        "Node.js", "C#", ".NET", "PHP", "Laravel", "Go", "Rust", "C++", "Kotlin", "Swift",
        "HTML", "CSS", "REST", "GraphQL", "SQL", "MySQL", "PostgreSQL", "MongoDB", "Redis",
        "Docker", "Kubernetes", "CI/CD", "Git", "Linux", "AWS", "Azure", "GCP",
      ],
    },
    {
      id: "data",
      label: "Data & IA",
      items: [
        "Python", "SQL", "Pandas", "NumPy", "Power BI", "Tableau", "ETL", "Spark", "Airflow",
        "Machine Learning", "Deep Learning", "NLP", "TensorFlow", "PyTorch", "Excel avancé",
        "Data visualization", "Statistiques",
      ],
    },
    {
      id: "qa",
      label: "QA & Tests",
      items: [
        "Selenium", "Cypress", "Playwright", "JUnit", "TestNG", "Postman", "API testing",
        "Jest", "Robot Framework", "Manual testing", "Performance testing",
      ],
    },
    {
      id: "design",
      label: "Design & Produit",
      items: [
        "Figma", "UI", "UX", "Design system", "Prototypage", "User research", "Wireframing",
        "Adobe XD", "Photoshop", "Illustrator", "Product management", "Agile", "Scrum",
      ],
    },
    {
      id: "business",
      label: "Business & RH",
      items: [
        "Recrutement", "ATS", "Sourcing", "Communication", "Négociation", "Gestion de projet",
        "Marketing digital", "SEO", "CRM", "Vente B2B", "Comptabilité", "Finance",
        "Droit des affaires", "Compliance",
      ],
    },
    {
      id: "infra",
      label: "Infra & Cybersécurité",
      items: [
        "Réseaux", "Sécurité", "Pentest", "SOC", "SIEM", "Firewall", "Active Directory",
        "Terraform", "Ansible", "Monitoring", "Observabilité",
      ],
    },
  ];

  const LANGUAGES = [
    "Français", "Arabe", "Anglais", "Espagnol", "Allemand", "Italien", "Tamazight", "Portugais", "Chinois",
  ];

  const LEVELS = ["A1", "A2", "B1", "B2", "C1", "C2", "Natif"];

  function esc(s) {
    return String(s == null ? "" : s)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;").replace(/'/g, "&#39;");
  }

  function ensureCss() {
    if (document.getElementById("hl-skills-css")) return;
    const s = document.createElement("style");
    s.id = "hl-skills-css";
    s.textContent =
      ".hl-sk-wrap{display:grid;gap:1rem}" +
      ".hl-sk-search{width:100%;padding:.7rem .9rem;border-radius:12px;border:1px solid rgba(26,18,20,.12);font:inherit}" +
      ".hl-sk-selected{display:flex;flex-wrap:wrap;gap:.4rem;min-height:2.2rem;padding:.55rem;border-radius:12px;background:#f7f4f1;border:1px dashed rgba(122,32,50,.25)}" +
      ".hl-sk-chip{display:inline-flex;align-items:center;gap:.35rem;padding:.28rem .65rem;border-radius:999px;background:#7a2032;color:#fff;font-size:.78rem;font-weight:600}" +
      ".hl-sk-chip button{all:unset;cursor:pointer;opacity:.85;font-size:1rem;line-height:1}" +
      ".hl-sk-chip.is-lang{background:#1e3a5f}" +
      ".hl-sk-domain{border:1px solid rgba(26,18,20,.08);border-radius:14px;overflow:hidden;background:#fff}" +
      ".hl-sk-domain summary{cursor:pointer;padding:.75rem 1rem;font-weight:700;font-size:.88rem;list-style:none;display:flex;justify-content:space-between;align-items:center;background:#faf8f6}" +
      ".hl-sk-domain summary::-webkit-details-marker{display:none}" +
      ".hl-sk-domain[open] summary{border-bottom:1px solid rgba(26,18,20,.06)}" +
      ".hl-sk-grid{display:flex;flex-wrap:wrap;gap:.4rem;padding:.85rem 1rem 1rem}" +
      ".hl-sk-tag{padding:.32rem .7rem;border-radius:999px;border:1px solid rgba(26,18,20,.12);background:#fff;font-size:.78rem;cursor:pointer;transition:all .15s}" +
      ".hl-sk-tag:hover{border-color:rgba(122,32,50,.4)}" +
      ".hl-sk-tag.is-on{background:rgba(122,32,50,.1);border-color:#7a2032;color:#7a2032;font-weight:600}" +
      ".hl-sk-lang-row{display:flex;flex-wrap:wrap;gap:.5rem;align-items:center;margin-top:.35rem}" +
      ".hl-sk-lang-row select{padding:.35rem .5rem;border-radius:8px;border:1px solid rgba(26,18,20,.12);font-size:.8rem}" +
      ".hl-wiz-panel{animation:hlWizIn .32s cubic-bezier(.22,1,.36,1)}" +
      "@keyframes hlWizIn{from{opacity:0;transform:translateX(18px)}to{opacity:1;transform:none}}" +
      ".hl-wiz-panel.is-back{animation-name:hlWizBack}" +
      "@keyframes hlWizBack{from{opacity:0;transform:translateX(-18px)}to{opacity:1;transform:none}}";
    document.head.appendChild(s);
  }

  /**
   * @param {HTMLElement} root
   * @param {{ selected?: string[], languages?: {name:string,level:string}[], onChange?: Function }} opts
   */
  function dedupeLanguages(list) {
    const seen = new Set();
    const out = [];
    (list || []).forEach((l) => {
      const name = l && l.name ? String(l.name).trim() : "";
      if (!name) return;
      const key = name.toLowerCase();
      if (seen.has(key)) return;
      seen.add(key);
      out.push({ name, level: l.level || "B2" });
    });
    return out;
  }

  function mount(root, opts) {
    ensureCss();
    const state = {
      selected: new Set((opts.selected || []).map((x) => x.trim()).filter(Boolean)),
      languages: dedupeLanguages(opts.languages || []),
      filter: "",
      remote: [],
    };

    function emit() {
      if (typeof opts.onChange === "function") {
        opts.onChange({
          skills: Array.from(state.selected),
          skillsCsv: Array.from(state.selected).join(", "),
          languages: state.languages.slice(),
          languagesCsv: state.languages.map((l) => l.name + (l.level ? " (" + l.level + ")" : "")).join(", "),
        });
      }
    }

    function paint() {
      const q = state.filter.toLowerCase();
      const selectedHtml = Array.from(state.selected)
        .map(
          (sk) =>
            '<span class="hl-sk-chip" data-rm="' +
            esc(sk) +
            '">' +
            esc(sk) +
            ' <button type="button" aria-label="Retirer">×</button></span>'
        )
        .concat(
          state.languages.map(
            (l) =>
              '<span class="hl-sk-chip is-lang" data-rm-lang="' +
              esc(l.name) +
              '">' +
              esc(l.name) +
              (l.level ? " · " + esc(l.level) : "") +
              ' <button type="button" aria-label="Retirer">×</button></span>'
          )
        )
        .join("") || '<span class="muted" style="font-size:.85rem">Aucune sélection — cliquez ci-dessous</span>';

      const domainsHtml = DOMAINS.map((d) => {
        const items = d.items.filter((it) => !q || it.toLowerCase().includes(q));
        if (!items.length) return "";
        return (
          '<details class="hl-sk-domain" open>' +
          "<summary>" +
          esc(d.label) +
          "<span class='muted' style='font-weight:500;font-size:.75rem'>" +
          items.length +
          "</span></summary>" +
          '<div class="hl-sk-grid">' +
          items
            .map((it) => {
              const on = state.selected.has(it);
              return (
                '<button type="button" class="hl-sk-tag' +
                (on ? " is-on" : "") +
                '" data-sk="' +
                esc(it) +
                '">' +
                esc(it) +
                "</button>"
              );
            })
            .join("") +
          "</div></details>"
        );
      }).join("");

      const remoteHtml = state.remote.length
        ? '<details class="hl-sk-domain" open><summary>Autres propositions</summary><div class="hl-sk-grid">' +
          state.remote
            .map((it) => {
              const on = state.selected.has(it);
              return '<button type="button" class="hl-sk-tag' + (on ? " is-on" : "") + '" data-sk="' + esc(it) + '">' + esc(it) + "</button>";
            })
            .join("") +
          "</div></details>"
        : "";

      const langsHtml =
        '<details class="hl-sk-domain" open><summary>Langues</summary><div class="hl-sk-grid">' +
        LANGUAGES.filter((l) => !q || l.toLowerCase().includes(q))
          .map((l) => {
            const on = state.languages.some((x) => x.name === l);
            return (
              '<button type="button" class="hl-sk-tag' +
              (on ? " is-on" : "") +
              '" data-lang="' +
              esc(l) +
              '">' +
              esc(l) +
              "</button>"
            );
          })
          .join("") +
        '</div><div class="hl-sk-lang-row" style="padding:0 1rem 1rem">' +
        state.languages
          .map(
            (l, i) =>
              '<label style="font-size:.8rem">' +
              esc(l.name) +
              ' <select data-lang-lvl="' +
              i +
              '">' +
              LEVELS.map(
                (lv) =>
                  '<option value="' +
                  lv +
                  '"' +
                  (l.level === lv ? " selected" : "") +
                  ">" +
                  lv +
                  "</option>"
              ).join("") +
              "</select></label>"
          )
          .join("") +
        "</div></details>";

      root.innerHTML =
        '<div class="hl-sk-wrap">' +
        '<input class="hl-sk-search" type="search" placeholder="Rechercher une compétence ou une langue…" value="' +
        esc(state.filter) +
        '" />' +
        '<div class="hl-sk-selected">' +
        selectedHtml +
        "</div>" +
        domainsHtml +
        remoteHtml +
        langsHtml +
        "</div>";

      const search = root.querySelector(".hl-sk-search");
      search.oninput = () => {
        state.filter = search.value;
        const qv = state.filter.trim();
        paint();
        clearTimeout(state.timer);
        if (qv.length >= 2 && window.HireLinkAPI) {
          state.timer = setTimeout(() => {
            HireLinkAPI.api("/catalog/suggest?kind=skill&q=" + encodeURIComponent(qv))
              .then((rows) => {
                if (state.filter.trim() !== qv) return;
                state.remote = (rows || []).map(function (item) {
                  return item && item.label ? item.label : String(item);
                }).filter(function (item) { return !DOMAINS.some(function (d) { return d.items.indexOf(item) >= 0; }); });
                paint();
              })
              .catch(() => {});
          }, 250);
        }
        root.querySelector(".hl-sk-search").focus();
        const el = root.querySelector(".hl-sk-search");
        if (el) {
          const v = el.value;
          el.setSelectionRange(v.length, v.length);
        }
      };

      root.querySelectorAll("[data-sk]").forEach((btn) => {
        btn.onclick = () => {
          const sk = btn.getAttribute("data-sk");
          if (state.selected.has(sk)) state.selected.delete(sk);
          else state.selected.add(sk);
          paint();
          emit();
        };
      });
      root.querySelectorAll("[data-lang]").forEach((btn) => {
        btn.onclick = () => {
          const name = btn.getAttribute("data-lang");
          const idx = state.languages.findIndex((x) => x.name === name);
          if (idx >= 0) state.languages.splice(idx, 1);
          else state.languages.push({ name, level: "B2" });
          paint();
          emit();
        };
      });
      root.querySelectorAll("[data-rm]").forEach((chip) => {
        chip.querySelector("button").onclick = () => {
          state.selected.delete(chip.getAttribute("data-rm"));
          paint();
          emit();
        };
      });
      root.querySelectorAll("[data-rm-lang]").forEach((chip) => {
        chip.querySelector("button").onclick = () => {
          const name = chip.getAttribute("data-rm-lang");
          state.languages = state.languages.filter((x) => x.name !== name);
          paint();
          emit();
        };
      });
      root.querySelectorAll("[data-lang-lvl]").forEach((sel) => {
        sel.onchange = () => {
          const i = Number(sel.getAttribute("data-lang-lvl"));
          if (state.languages[i]) state.languages[i].level = sel.value;
          paint();
          emit();
        };
      });
    }

    paint();
    emit();
    return {
      getSkillsCsv: () => Array.from(state.selected).join(", "),
      getLanguagesCsv: () =>
        state.languages.map((l) => l.name + (l.level ? " (" + l.level + ")" : "")).join(", "),
      getCombinedCsv: () => {
        const s = Array.from(state.selected);
        const langs = state.languages.map((l) => l.name + (l.level ? " (" + l.level + ")" : ""));
        return s.concat(langs).join(", ");
      },
    };
  }

  global.HireLinkSkills = { DOMAINS, LANGUAGES, LEVELS, mount, ensureCss };
})(window);
