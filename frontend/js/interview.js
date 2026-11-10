/**
 * Entretiens : formulaire de planification (champs selon le mode), carte d'affichage commune aux deux espaces,
 * report / annulation (recruteur) et téléchargement du fichier calendrier (.ics).
 * L'entretien est une convocation décidée par le recruteur : le candidat consulte, il ne répond pas.
 *
 *   HireLinkInterview.form(initialApp?)  → { el, isValid(), validate(), values() }  (values() = corps de InterviewRequest)
 *   HireLinkInterview.card(app, opts)    → <section class="iv-card"> ; opts.role = "recruiter" | "readonly",
 *                                          opts.onChange() après une action réussie
 */
(function (global) {
  const TYPES = [
    { value: "VISIO", label: "Visioconférence", hint: "Meet, Teams, Zoom…", icon: "video", cls: "t-visio" },
    { value: "ONSITE", label: "Sur site", hint: "Dans vos locaux", icon: "building", cls: "t-onsite" },
  ];
  const KINDS = [["HR", "Entretien RH"], ["TECHNICAL", "Entretien technique"], ["MANAGER", "Avec le manager"], ["CASE", "Étude de cas"], ["TEAM", "Rencontre avec l’équipe"], ["FINAL", "Entretien final"]];
  const DURATIONS = [15, 30, 45, 60, 90, 120];
  const ZONES = [["Africa/Casablanca", "Casablanca (UTC+1)"], ["Europe/Paris", "Paris"], ["Europe/London", "Londres"], ["UTC", "UTC"]];
  const TYPE_FR = { VISIO: "Visioconférence", ONSITE: "Sur site", PHONE: "Téléphone" };
  const KIND_FR = { HR: "Entretien RH", TECHNICAL: "Entretien technique", MANAGER: "Entretien avec le manager", CASE: "Étude de cas", TEAM: "Rencontre avec l’équipe", FINAL: "Entretien final" };

  function h(tag, attrs, children) {
    const e = document.createElement(tag);
    Object.keys(attrs || {}).forEach((k) => {
      if (attrs[k] == null || attrs[k] === false) return;
      if (k === "text") e.textContent = attrs[k]; else e.setAttribute(k, attrs[k] === true ? "" : attrs[k]);
    });
    [].concat(children || []).forEach((c) => { if (c) e.append(c); });
    return e;
  }

  const pad = (n) => String(n).padStart(2, "0");

  /** Date et heure de l'entretien, lues dans son propre fuseau (pas dans celui du navigateur). */
  function partsIn(instant, zone) {
    const f = new Intl.DateTimeFormat("fr-CA", { timeZone: zone, year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", hourCycle: "h23" });
    const p = {};
    f.formatToParts(new Date(instant)).forEach((x) => { p[x.type] = x.value; });
    return { date: p.year + "-" + p.month + "-" + p.day, time: p.hour + ":" + p.minute };
  }

  function whenText(app) {
    const zone = app.interviewTimeZone || "Africa/Casablanca";
    const d = new Date(app.interviewAt);
    const day = new Intl.DateTimeFormat("fr-FR", { timeZone: zone, weekday: "long", day: "numeric", month: "long", year: "numeric" }).format(d);
    const time = new Intl.DateTimeFormat("fr-FR", { timeZone: zone, hour: "2-digit", minute: "2-digit" }).format(d).replace(":", "h");
    return { day: day.charAt(0).toUpperCase() + day.slice(1), time: time, zone: zone };
  }

  // ------------------------------------------------------------ formulaire

  function form(app) {
    const zone = (app && app.interviewTimeZone) || "Africa/Casablanca";
    const when = app && app.interviewAt ? partsIn(app.interviewAt, zone) : { date: "", time: "10:00" };
    const s = {
      type: app && app.interviewType === "ONSITE" ? "ONSITE" : "VISIO",
      kind: (app && app.interviewKind) || "HR",
      date: when.date, time: when.time, zone: zone,
      duration: String((app && app.interviewDurationMinutes) || 45),
      location: (app && app.interviewLocation) || "", mapUrl: (app && app.interviewMapUrl) || "",
      videoUrl: (app && app.interviewVideoUrl) || "",
      participants: (app && app.interviewParticipants) || "", instructions: (app && app.interviewNotes) || "",
    };
    const root = h("div", { class: "iv-form" });
    const touched = new Set();

    function errors() {
      const e = {};
      if (!s.date) e.date = "Choisissez la date.";
      else if (!s.time) e.time = "Choisissez l’heure.";
      else if (new Date(s.date + "T" + s.time + ":00") < new Date(Date.now() - 24 * 3600e3)) e.date = "Cette date est déjà passée.";
      if (s.type === "ONSITE" && !s.location.trim()) e.location = "Indiquez l’adresse de l’entretien.";
      if (s.type === "VISIO") {
        if (!s.videoUrl.trim()) e.videoUrl = "Indiquez le lien de visioconférence.";
        else if (!/^https?:\/\/\S+$/i.test(s.videoUrl.trim())) e.videoUrl = "Le lien doit commencer par https://";
      }
      if (s.type === "ONSITE" && s.mapUrl.trim() && !/^https?:\/\/\S+$/i.test(s.mapUrl.trim())) e.mapUrl = "Le lien doit commencer par https://";
      return e;
    }

    function show() {
      const errs = errors();
      root.querySelectorAll("[data-iv]").forEach((w) => {
        const msg = touched.has(w.dataset.iv) ? errs[w.dataset.iv] : "";
        w.classList.toggle("has-error", !!msg);
        w.querySelector(".iv-error").textContent = msg || "";
        w.querySelectorAll("input,select,textarea").forEach((c) => { if (msg) c.setAttribute("aria-invalid", "true"); else c.removeAttribute("aria-invalid"); });
      });
    }

    function field(key, label, control, wide, hint) {
      const w = h("div", { class: "iv-field" + (wide ? " is-wide" : ""), "data-iv": key }, [
        h("label", { for: "iv-" + key, text: label }), control,
        hint ? h("small", { class: "iv-hint", text: hint }) : null,
        h("p", { class: "iv-error", role: "alert" }),
      ]);
      control.id = "iv-" + key;
      return w;
    }
    function input(key, type, attrs) {
      const i = h("input", Object.assign({ type: type }, attrs));
      i.value = s[key];
      i.addEventListener("input", () => { s[key] = i.value; show(); });
      i.addEventListener("blur", () => { touched.add(key); show(); });
      return i;
    }
    function select(key, options) {
      const sel = h("select", {}, options.map((o) => h("option", { value: o[0], text: o[1] })));
      sel.value = s[key];
      sel.addEventListener("change", () => { s[key] = sel.value; });
      return sel;
    }

    function paint() {
      root.replaceChildren();
      const types = h("div", { class: "iv-types", role: "radiogroup", "aria-label": "Mode de l’entretien" }, TYPES.map((t) => {
        const id = "iv-type-" + t.value;
        const r = h("input", { type: "radio", name: "iv-type", id: id, value: t.value });
        r.checked = s.type === t.value;
        r.addEventListener("change", () => { s.type = t.value; paint(); const n = root.querySelector("#" + id); if (n) n.focus(); });
        const ico = h("span", { class: "iv-type-ico " + t.cls, "aria-hidden": "true" });
        ico.innerHTML = (global.HLIcons ? HLIcons.svg(t.icon) : "") + '<span class="iv-light-green" title="Créneau direct"></span>';
        return h("div", {}, [r, h("label", { for: id }, [ico, h("span", { class: "iv-type-txt" }, [h("strong", {}, [document.createTextNode(t.label + " "), h("span", { class: "iv-pulse-badge", text: "En ligne" })]), h("small", { text: t.hint })])])]);
      }));
      root.append(
        h("div", { class: "iv-field is-wide" }, [h("span", { class: "iv-label", text: "Mode de l’entretien" }), types]),
        h("div", { class: "iv-grid" }, [
          field("kind", "Nature de l’entretien", select("kind", KINDS)),
          field("duration", "Durée", select("duration", DURATIONS.map((d) => [String(d), d + " min"]))),
          field("date", "Date", input("date", "date", { min: global.HireLinkApp.localDate() })),
          field("time", "Heure", input("time", "time", { step: 300 })),
          field("zone", "Fuseau horaire", select("zone", ZONES), true),
        ]),
      );
      const conditional = [];
      if (s.type === "ONSITE") {
        conditional.push(
          field("location", "Adresse *", input("location", "text", { maxlength: 255, placeholder: "Bureau Casablanca, 12 rue Moulay Youssef, 3ᵉ étage" }), true),
          field("mapUrl", "Lien du plan (facultatif)", input("mapUrl", "url", { maxlength: 300, placeholder: "https://maps.google.com/…" }), true, "Google Maps, Waze… : le candidat pourra s’y rendre en un clic."),
        );
      } else {
        conditional.push(field("videoUrl", "Lien de la visioconférence *", input("videoUrl", "url", { maxlength: 300, placeholder: "https://meet.google.com/abc-defg-hij" }), true));
      }
      root.append(
        h("div", { class: "iv-grid" }, conditional),
        h("div", { class: "iv-grid" }, [
          field("participants", "Participants (facultatif)", input("participants", "text", { maxlength: 300, placeholder: "Sara Benali (RH), Karim Alaoui (CTO)" }), true),
          field("instructions", "Consignes pour le candidat (facultatif)", (() => {
            const t = h("textarea", { rows: 3, maxlength: 1000, placeholder: "Documents à apporter, durée du test technique, accès à l’immeuble…" });
            t.value = s.instructions;
            t.addEventListener("input", () => { s.instructions = t.value; });
            return t;
          })(), true),
        ]),
      );
      show();
    }
    paint();

    return {
      el: root,
      isValid() { return !Object.keys(errors()).length; },
      validate() {
        const errs = errors();
        Object.keys(errs).forEach((k) => touched.add(k));
        show();
        const first = Object.keys(errs)[0];
        if (first) { const c = root.querySelector('[data-iv="' + first + '"] input, [data-iv="' + first + '"] select'); if (c) c.focus(); }
        return !first;
      },
      values() {
        return {
          type: s.type, kind: s.kind, startsAt: s.date + "T" + s.time + ":00", timeZone: s.zone, durationMinutes: Number(s.duration),
          location: s.type === "ONSITE" ? s.location.trim() : null, mapUrl: s.type === "ONSITE" ? s.mapUrl.trim() : null,
          videoUrl: s.type === "VISIO" ? s.videoUrl.trim() : null,
          participants: s.participants.trim() || null, instructions: s.instructions.trim() || null,
        };
      },
    };
  }

  // ------------------------------------------------------------ affichage

  function download(app) {
    return HireLinkAPI.authFetch("/applications/" + app.id + "/interview.ics").then((res) => {
      if (!res.ok) throw new Error("Calendrier indisponible");
      return res.blob();
    }).then((blob) => {
      const url = URL.createObjectURL(blob);
      const a = h("a", { href: url, download: "entretien-hirelink.ics" });
      document.body.appendChild(a);
      a.click();
      a.remove();
      setTimeout(() => URL.revokeObjectURL(url), 10000);
    });
  }

  function row(label, value) {
    const dd = h("dd");
    if (value instanceof Node) dd.append(value); else dd.textContent = value;
    return h("div", {}, [h("dt", { text: label }), dd]);
  }

  function link(url, text) {
    return h("a", { href: url, target: "_blank", rel: "noopener noreferrer", text: text });
  }

  function failure(err) {
    HireLink.toast("Action impossible", err.message, "danger");
  }

  function card(app, opts) {
    const o = opts || {};
    const role = o.role || "readonly";
    const cancelled = app.interviewStatus === "CANCELLED";
    const when = whenText(app);
    const root = h("section", { class: "iv-card" + (cancelled ? " is-cancelled" : ""), "aria-label": "Entretien" });
    const modeBadge = h("span", { class: "iv-mode" }, [
      h("span", { class: "iv-light-green", "aria-hidden": "true" }),
      document.createTextNode(" " + (TYPE_FR[app.interviewType] || "Entretien"))
    ]);
    root.append(h("header", {}, [
      h("div", { class: "iv-head-info" }, [h("span", { class: "iv-kind", text: KIND_FR[app.interviewKind] || "Entretien" }), modeBadge]),
      cancelled ? h("span", { class: "badge badge-danger", text: "Annulé" }) : null,
    ]));
    root.append(h("p", { class: "iv-when" }, [h("strong", { text: when.day }), document.createTextNode(" à "), h("strong", { text: when.time }),
      h("small", { text: " · " + (app.interviewDurationMinutes || 45) + " min · " + when.zone })]));
    if (!cancelled && app.interviewAt) {
      const ics = h("button", { type: "button", class: "btn btn-ghost btn-sm", text: "Ajouter à mon agenda (.ics)" });
      ics.onclick = () => download(app).catch(failure);
      root.append(h("p", { class: "iv-ics" }, [ics]));
    }
    const dl = h("dl", { class: "iv-facts" });
    if (app.interviewType === "ONSITE") {
      dl.append(row("Adresse", app.interviewLocation || "—"));
      if (app.interviewMapUrl) dl.append(row("Plan", link(app.interviewMapUrl, "Ouvrir l’itinéraire")));
    } else if (!cancelled && app.interviewType === "VISIO" && app.interviewVideoUrl) {
      dl.append(row("Lien", link(app.interviewVideoUrl, "Rejoindre la visioconférence")));
    } else if (app.interviewType === "PHONE") {
      dl.append(row("Numéro", app.interviewLocation || "—"));
    } else if (app.interviewLocation) {
      dl.append(row("Accès", app.interviewLocation));
    }
    if (app.interviewParticipants) dl.append(row("Participants", app.interviewParticipants));
    if (app.interviewNotes) dl.append(row("Consignes", app.interviewNotes));
    if (cancelled && app.interviewCancelReason) dl.append(row("Motif d’annulation", app.interviewCancelReason));
    root.append(dl);

    const actions = h("div", { class: "iv-actions" });
    if (!cancelled && role === "recruiter") actions.append(...recruiterActions(app, o));
    if (actions.children.length) root.append(actions);
    return root;
  }

  function done(opts, title, text) {
    HireLink.toast(title, text, "ok");
    if (opts.onChange) opts.onChange();
  }

  function recruiterActions(app, opts) {
    const move = h("button", { type: "button", class: "btn btn-soft btn-sm", text: "Reporter" });
    move.addEventListener("click", async () => {
      const f = form(app);
      const ok = await HireLink.openModal({ title: "Reporter l’entretien", description: "Le nouveau créneau est enregistré dans HireLink. L’e-mail part seulement si la messagerie du serveur est active ; sinon il est journalisé, pas envoyé.", bodyHtml: '<div id="iv-slot"></div>', confirmLabel: "Reporter le créneau", large: true, confirmDisabled: true,
        onConfirm: () => HireLinkAPI.api("/applications/" + app.id + "/interview", { method: "PUT", body: f.values() }),
        onReady: (api) => {
          api.root.querySelector("#iv-slot").appendChild(f.el);
          const refresh = () => api.setConfirmEnabled(f.isValid());
          f.el.addEventListener("input", refresh);
          f.el.addEventListener("change", refresh);
          refresh();
        } });
      if (ok) done(opts, "Entretien reporté", "Le candidat voit le nouveau créneau dans HireLink.");
    });
    const cancel = h("button", { type: "button", class: "btn btn-ghost btn-sm iv-danger", text: "Annuler l’entretien" });
    cancel.addEventListener("click", async () => {
      const reason = await Mascotte.confirm({
        title: "Annuler l’entretien ?",
        description: "Le motif est enregistré. Le candidat le voit dans HireLink. L’e-mail part seulement si la messagerie est active.",
        confirmLabel: "Annuler l’entretien",
        cancelLabel: "Garder l’entretien",
        danger: true,
        field: { label: "Motif (obligatoire)", placeholder: "Ex. Poste pourvu, imprévu côté équipe…", required: true },
      });
      if (!reason) return;
      HireLinkAPI.api("/applications/" + app.id + "/interview/cancel", { method: "POST", body: { reason } })
        .then(() => done(opts, "Entretien annulé", "L’annulation est visible dans HireLink.")).catch(failure);
    });
    return [move, cancel];
  }

  /**
   * Fenêtre de planification pour un dossier sans créneau : réutilise le formulaire du kit.
   * opts.onDone() est appelé après un envoi réussi.
   */
  function plan(app, opts) {
    const o = opts || {};
    const f = form(app);
    HireLink.openDrawer({
      title: "Planifier un entretien",
      description: (app.candidateName || "Candidat") + " — le créneau est enregistré dans HireLink. L’e-mail de convocation part seulement si la messagerie du serveur est active.",
      bodyHtml: '<div id="iv-plan"></div>',
      confirmLabel: "Planifier et prévenir",
      confirmDisabled: true,
      wide: true,
      onConfirm: () => HireLinkAPI.api("/applications/" + app.id + "/interview", { method: "PUT", body: f.values() }),
      onReady: (api) => {
        api.root.querySelector("#iv-plan").appendChild(f.el);
        const refresh = () => api.setConfirmEnabled(f.isValid());
        f.el.addEventListener("input", refresh);
        f.el.addEventListener("change", refresh);
        refresh();
      },
    }).then((ok) => {
      if (!ok) return;
      HireLink.toast("Entretien planifié", "Le créneau est dans HireLink. L’e-mail n’est pas annoncé comme envoyé.", "ok");
      if (o.onDone) o.onDone();
    });
  }

  /** « Jeudi 5 novembre 2026 à 10h30 · Visioconférence » pour une ligne de texte. */
  function summary(app) {
    const w = whenText(app);
    return w.day + " à " + w.time + " · " + (TYPE_FR[app.interviewType] || "");
  }

  global.HireLinkInterview = { form: form, card: card, plan: plan, summary: summary, whenText: whenText, download: download, TYPE_FR: TYPE_FR, KIND_FR: KIND_FR };
})(window);
