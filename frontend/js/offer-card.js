/**
 * Affichage partagé des offres (module autonome, réutilisable par les autres pages : accueil, listes, détail).
 *
 *   HireLinkOffer.card(offer, options)  → <article class="offer-card"> : logo, titre, pastilles, extrait au survol
 *   HireLinkOffer.facts(offer)          → <dl class="offer-facts"> : toutes les conditions de l'offre avec icônes
 *   HireLinkOffer.compensation(offer)   → texte de rémunération (ou "" si non renseignée)
 *   HireLinkOffer.toRequest(offer, over) → corps d'une mise à jour (PUT /offers/{id}) à partir d'une offre reçue
 *   HireLinkOffer.LABELS                → libellés français des énumérations (formulaire d'offre)
 *
 * Styles : css/offer-card.css. Aucune dépendance obligatoire ; HireLinkPlaces ajoute le drapeau du lieu.
 * Options de card() :
 *   href(offer)      lien de la carte (défaut « # »)
 *   onOpen(offer, e) appelé au clic sur la carte (le lien n'est alors pas suivi)
 *   logoUrl(offer)   adresse du logo (défaut : HireLinkAPI.mediaUrl(offer.companyLogo))
 *   badge            petit texte ou nœud affiché en haut à droite (ex. « 82 % »)
 *   selected         carte mise en avant (liste + détail)
 *   statusChip       texte d'une pastille d'état supplémentaire (« Candidature envoyée », « Brouillon »…)
 *   variant          "showcase" : version vitrine de l'accueil (l'extrait remplace les pastilles au survol)
 *   footText         pied de carte (« il y a 2 jours ») avec la flèche ; sans lui, la flèche est seule en bas à droite
 *
 *   HireLinkOffer.companyCard(company, options) → même carte pour une entreprise : { name, city, verified, logo, n }
 */
(function (global) {
  const LABELS = {
    workMode: { ONSITE: "Sur site", HYBRID: "Hybride", REMOTE: "Télétravail" },
    experience: {
      NONE: "Aucune expérience requise", JUNIOR: "Débutant (moins de 2 ans)", INTERMEDIATE: "Confirmé (2 à 5 ans)",
      SENIOR: "Senior (5 à 10 ans)", EXPERT: "Expert (plus de 10 ans)",
    },
    education: {
      BAC: "Bac", BAC_2: "Bac+2", BAC_3: "Bac+3 (licence)", BAC_4: "Bac+4", BAC_5: "Bac+5 (master, ingénieur)", DOCTORATE: "Doctorat",
    },
    period: { HOUR: "heure", DAY: "jour", MONTH: "mois", YEAR: "an" },
    basis: { GROSS: "brut", NET: "net" },
  };

  const ICONS = {
    money: '<rect x="3" y="6.5" width="18" height="11" rx="2"/><circle cx="12" cy="12" r="2.6"/><path d="M6.5 9.5h.01M17.5 14.5h.01"/>',
    pin: '<path d="M12 21s7-6.2 7-11.5a7 7 0 1 0-14 0C5 14.8 12 21 12 21Z"/><circle cx="12" cy="9.5" r="2.4"/>',
    briefcase: '<rect x="3" y="7" width="18" height="13" rx="2"/><path d="M9 7V5.5A1.5 1.5 0 0 1 10.5 4h3A1.5 1.5 0 0 1 15 5.5V7M3 13h18"/>',
    home: '<path d="m3 11 9-7 9 7M5.5 9.5V20h13V9.5M10 20v-5h4v5"/>',
    clock: '<circle cx="12" cy="12" r="8.5"/><path d="M12 7.5V12l3 2"/>',
    calendar: '<rect x="3.5" y="5" width="17" height="15" rx="2"/><path d="M8 3v4M16 3v4M3.5 10h17"/>',
    doc: '<path d="M7 3.5h7l4 4V20.5H7z"/><path d="M14 3.5v4h4M9.5 12h5M9.5 15.5h5"/>',
    handshake: '<path d="m3 12 4-4 3 1 3-2 4 3 4-1M3 12l5 5a2 2 0 0 0 3 0l1-1 2 2a2 2 0 0 0 3 0l4-4"/>',
    shield: '<path d="M12 3 5 6v5c0 4.4 3 8 7 9.5 4-1.5 7-5.1 7-9.5V6z"/><path d="m9 12 2 2 4-4"/>',
    layers: '<path d="m12 3 9 5-9 5-9-5zM3 13l9 5 9-5"/>',
    cap: '<path d="m2.5 9.5 9.5-5 9.5 5-9.5 5zM6.5 12v4.5c1.5 1.4 3.4 2 5.5 2s4-.6 5.5-2V12"/>',
    users: '<circle cx="9" cy="8.5" r="3.2"/><path d="M3 20c.4-3.6 2.8-5.5 6-5.5s5.6 1.9 6 5.5M16 5.6a3.2 3.2 0 0 1 0 6M18 14.7c1.8.7 2.8 2.4 3 5.3"/>',
    flag: '<path d="M5 21V4M5 4h11l-1.5 3.5L16 11H5"/>',
    gift: '<rect x="3.5" y="9" width="17" height="4" rx="1"/><path d="M5 13v7h14v-7M12 9v11M12 9c-2.5 0-4-1-4-2.5S9 4 10.5 4C12 4 12 6.5 12 9Zm0 0c2.5 0 4-1 4-2.5S15 4 13.5 4C12 4 12 6.5 12 9Z"/>',
    arrow: '<path d="M5 12h14M13 6l6 6-6 6"/>',
  };

  function icon(name, size) {
    const s = size || 16;
    return '<svg class="oc-icon" viewBox="0 0 24 24" width="' + s + '" height="' + s + '" fill="none" stroke="currentColor" ' +
      'stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false">' + ICONS[name] + "</svg>";
  }

  // --- formats ----------------------------------------------------------

  function amount(n) {
    return Number(n).toLocaleString("fr-FR");
  }

  function dateFr(iso) {
    if (!iso) return "";
    const d = new Date(String(iso).slice(0, 10) + "T12:00:00");
    return isNaN(d) ? "" : d.toLocaleDateString("fr-FR", { day: "numeric", month: "long", year: "numeric" });
  }

  function isInternship(o) {
    return /^(stage|pfe|pfa|stage d'été)$/i.test(String(o.contractType || "").trim());
  }

  /** Rémunération prête à afficher : « 8 000 – 10 000 MAD / mois brut », « Gratification · 3 000 MAD / mois »… */
  function compensation(o) {
    const type = o.compensationType;
    if (!type) return "";
    const kind = String(o.contractType || "Poste");
    if (type === "NEGOTIABLE") return "Rémunération négociable";
    if (type === "UNSPECIFIED") return "Rémunération non précisée";
    if (type === "UNPAID") return kind + " non rémunéré";
    if (!o.salaryMin) return isInternship(o) ? "Stage rémunéré" : "";
    const range = o.salaryMax ? amount(o.salaryMin) + " – " + amount(o.salaryMax) : amount(o.salaryMin);
    const per = o.salaryPeriod ? " / " + LABELS.period[o.salaryPeriod] : "";
    const basis = o.salaryBasis && !isInternship(o) ? " " + LABELS.basis[o.salaryBasis] : "";
    const money = range + " " + (o.currency || "MAD") + per + basis;
    return isInternship(o) ? "Gratification · " + money : money;
  }

  /** Les anciennes offres portent le mode de travail dans le lieu (« Tétouan · Hybride »). */
  function workModeOf(o) {
    if (o.workMode) return LABELS.workMode[o.workMode];
    const hit = String(o.location || "").match(/t[ée]l[ée]travail|remote|hybride|sur site|pr[ée]sentiel/i);
    if (!hit) return "";
    return /hybride/i.test(hit[0]) ? "Hybride" : /sur site|pr[ée]sentiel/i.test(hit[0]) ? "Sur site" : "Télétravail";
  }

  function placeOf(o) {
    const first = String(o.location || "").split(/[·|]/)[0].trim();
    return /^(t[ée]l[ée]travail|remote|hybride|sur site|pr[ée]sentiel)$/i.test(first) ? "" : first;
  }

  function flagOf(place) {
    return global.HireLinkPlaces ? HireLinkPlaces.flagOf(place) : "";
  }

  function companyOf(o) {
    return o.companyName || o.recruiterName || "Entreprise";
  }

  function initials(name) {
    const parts = String(name).trim().split(/\s+/).filter(Boolean);
    return ((parts[0] || "E")[0] + (parts[1] ? parts[1][0] : "")).toUpperCase();
  }

  function excerpt(text, max) {
    const clean = String(text || "").replace(/\s+/g, " ").trim();
    if (clean.length <= max) return clean;
    return clean.slice(0, max).replace(/\s+\S*$/, "") + "…";
  }

  function el(tag, className, text) {
    const e = document.createElement(tag);
    if (className) e.className = className;
    if (text != null) e.textContent = text;
    return e;
  }

  function chip(iconName, text, className) {
    const li = el("li", "oc-chip" + (className ? " " + className : ""));
    li.insertAdjacentHTML("beforeend", icon(iconName, 14));
    li.appendChild(el("span", "", text));
    return li;
  }

  // --- carte --------------------------------------------------------------

  /** Adresse d'un média : pages connectées (HireLinkAPI) ou pages publiques (origine de l'API). */
  function mediaUrl(path) {
    if (global.HireLinkAPI) return HireLinkAPI.mediaUrl(path);
    const s = String(path || "");
    if (!s || /google\.com\/s2\/favicons|gstatic\.com\/faviconV2/i.test(s)) return "";
    if (/^https?:\/\//i.test(s) || s.startsWith("data:")) return s;
    const origin = String(global.HIRELINK_API_BASE || "").replace(/\/api\/v1\/?$/, "");
    return s.startsWith("/") ? origin + s : s;
  }

  function logo(o, opts) {
    const box = el("span", "offer-card__logo");
    const url = opts.logoUrl ? opts.logoUrl(o) : mediaUrl(o.companyLogo);
    const fallback = function () { box.replaceChildren(document.createTextNode(initials(companyOf(o)))); };
    if (url) {
      const img = el("img");
      img.alt = "";
      img.loading = "lazy";
      img.src = url;
      img.addEventListener("error", fallback);
      box.appendChild(img);
    } else {
      fallback();
    }
    return box;
  }

  function card(o, options) {
    const opts = options || {};
    const root = el("article", "offer-card" + (opts.selected ? " is-selected" : ""));
    root.dataset.offerId = o.id;
    const link = el("a", "offer-card__link");
    link.href = opts.href ? opts.href(o) : "#";
    link.setAttribute("aria-label", o.title + " — " + companyOf(o));
    if (opts.selected) link.setAttribute("aria-current", "true");
    if (opts.onOpen) {
      link.addEventListener("click", function (e) {
        if (e.metaKey || e.ctrlKey || e.shiftKey) return;
        e.preventDefault();
        opts.onOpen(o, e);
      });
    }

    const head = el("div", "offer-card__head");
    const titles = el("div", "offer-card__titles");
    const company = el("p", "offer-card__company", companyOf(o));
    if (o.companyVerified) {
      company.insertAdjacentHTML("beforeend", '<svg class="oc-verified" viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" ' +
        'stroke-linecap="round" stroke-linejoin="round" role="img" aria-label="Entreprise vérifiée"><title>Entreprise vérifiée</title>' + ICONS.shield + "</svg>");
    }
    titles.append(company, el("h3", "offer-card__title", o.title));
    head.append(logo(o, opts), titles);
    if (opts.badge != null) {
      const badge = el("span", "offer-card__badge");
      if (opts.badge instanceof Node) badge.appendChild(opts.badge); else badge.textContent = opts.badge;
      head.appendChild(badge);
    }

    const chips = el("ul", "offer-card__chips");
    const type = o.durationMonths && o.contractType !== "CDI" ? o.contractType + " · " + o.durationMonths + " mois" : o.contractType;
    if (type) chips.appendChild(chip("briefcase", type, "oc-chip--type"));
    const place = placeOf(o);
    if (place) {
      const li = el("li", "oc-chip");
      const flag = flagOf(place);
      if (flag) li.appendChild(el("span", "oc-flag", flag)); else li.insertAdjacentHTML("beforeend", icon("pin", 14));
      li.appendChild(el("span", "", place));
      chips.appendChild(li);
    }
    const mode = workModeOf(o);
    if (mode) chips.appendChild(chip("home", mode));
    const comp = compensation(o);
    if (comp) chips.appendChild(chip("money", comp, "oc-chip--money"));
    if (o.responseSlaDays != null) chips.appendChild(chip("clock", "Réponse " + o.responseSlaDays + " j"));
    if (opts.statusChip) chips.appendChild(chip("shield", opts.statusChip, "oc-chip--status"));

    const reveal = el("div", "offer-card__reveal");
    reveal.appendChild(el("p", "offer-card__excerpt", excerpt(o.description, 170)));

    root.classList.toggle("offer-card--showcase", opts.variant === "showcase");
    link.appendChild(head);
    if (opts.variant === "showcase") {
      const swap = el("div", "offer-card__swap");
      swap.append(chips, reveal);
      link.appendChild(swap);
    } else {
      link.append(chips, reveal);
    }
    appendEnd(link, opts);
    root.appendChild(link);
    return root;
  }

  function arrowNode() {
    const arrow = el("span", "offer-card__arrow");
    arrow.setAttribute("aria-hidden", "true");
    arrow.insertAdjacentHTML("beforeend", icon("arrow", 18));
    return arrow;
  }

  function appendEnd(link, opts) {
    if (opts.footText == null) { link.appendChild(arrowNode()); return; }
    const foot = el("div", "offer-card__foot");
    foot.append(el("span", "", opts.footText), arrowNode());
    link.appendChild(foot);
  }

  /** Carte d'entreprise (accueil) : logo, nom, ville avec drapeau, état de vérification, nombre d'offres. */
  function companyCard(c, options) {
    const opts = options || {};
    const root = el("article", "offer-card offer-card--company offer-card--showcase");
    const link = el("a", "offer-card__link");
    link.href = opts.href ? opts.href(c) : "#";
    link.setAttribute("aria-label", c.name);
    const head = el("div", "offer-card__head");
    const titles = el("div", "offer-card__titles");
    titles.appendChild(el("h3", "offer-card__title", c.name));
    head.append(logo({ companyName: c.name, companyLogo: c.logo }, opts), titles);
    const chips = el("ul", "offer-card__chips");
    const li = el("li", "oc-chip");
    const flag = flagOf(c.city);
    if (flag) li.appendChild(el("span", "oc-flag", flag)); else li.insertAdjacentHTML("beforeend", icon("pin", 14));
    li.appendChild(el("span", "", c.city));
    chips.append(li, chip("shield", c.verified ? "Identité vérifiée" : "Fiche entreprise", c.verified ? "oc-chip--status" : ""));
    link.append(head, chips);
    appendEnd(link, { footText: c.n + " offre" + (c.n > 1 ? "s" : "") });
    root.appendChild(link);
    return root;
  }

  // --- détail ---------------------------------------------------------------

  function facts(o) {
    const rows = [];
    const add = function (iconName, label, value) { if (value) rows.push([iconName, label, value]); };
    const comp = compensation(o);
    add("money", "Rémunération", comp);
    add("clock", "Durée", o.durationMonths ? o.durationMonths + " mois" : "");
    if (o.startAsap) add("calendar", "Début", "Dès que possible");
    else add("calendar", "Début", dateFr(o.startDate));
    add("clock", isInternship(o) ? "Volume horaire" : o.contractType === "Alternance" ? "Rythme" : "Horaires", o.schedule);
    if (o.agreementRequired != null) add("doc", "Convention de stage", o.agreementRequired ? "Obligatoire" : "Non exigée");
    if (o.hiringPossible != null) add("handshake", "Embauche à l’issue", o.hiringPossible ? "Possible" : "Non prévue");
    add("shield", "Période d’essai", o.probationMonths ? o.probationMonths + " mois" : "");
    add("home", "Mode de travail", workModeOf(o));
    add("layers", "Expérience", o.experienceLevel ? LABELS.experience[o.experienceLevel] : "");
    add("cap", "Niveau d’études", o.educationLevel ? LABELS.education[o.educationLevel] : "");
    add("users", "Postes à pourvoir", o.positions ? String(o.positions) : "");
    add("flag", "Date limite", dateFr(o.deadline));

    const dl = el("dl", "offer-facts");
    rows.forEach(function (r) {
      const item = el("div", "offer-fact" + (r[0] === "money" ? " offer-fact--money" : ""));
      const dt = el("dt");
      dt.insertAdjacentHTML("beforeend", icon(r[0], 16));
      dt.appendChild(el("span", "", r[1]));
      item.append(dt, el("dd", "", r[2]));
      dl.appendChild(item);
    });
    if (o.benefits) {
      const item = el("div", "offer-fact offer-fact--wide");
      const dt = el("dt");
      dt.insertAdjacentHTML("beforeend", icon("gift", 16));
      dt.appendChild(el("span", "", "Avantages"));
      const dd = el("dd", "offer-benefits");
      o.benefits.split(",").forEach(function (b) { dd.appendChild(el("span", "oc-benefit", b.trim())); });
      item.append(dt, dd);
      dl.appendChild(item);
    }
    return dl;
  }

  const REQUEST_FIELDS = [
    "title", "description", "requiredSkills", "location", "contractType", "responseSlaDays", "published", "compensationType",
    "salaryMin", "salaryMax", "salaryPeriod", "salaryBasis", "currency", "durationMonths", "startDate", "startAsap",
    "agreementRequired", "hiringPossible", "probationMonths", "schedule", "workMode", "experienceLevel", "educationLevel",
    "positions", "deadline", "benefits",
  ];

  /** Une mise à jour remplace toutes les conditions : on renvoie donc l'offre entière, avec les changements voulus. */
  function toRequest(o, overrides) {
    const body = {};
    REQUEST_FIELDS.forEach(function (k) { if (o[k] != null) body[k] = o[k]; });
    return Object.assign(body, overrides || {});
  }

  global.HireLinkOffer = { toRequest: toRequest, card: card, companyCard: companyCard, mediaUrl: mediaUrl, facts: facts, compensation: compensation, icon: icon, placeOf: placeOf, workModeOf: workModeOf, dateFr: dateFr, LABELS: LABELS };
})(window);
