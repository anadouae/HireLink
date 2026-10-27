(function () {
  const session = HireLinkShell.mountShell({ roles: ["CANDIDAT"], hello: false });
  if (!session) return;

  const STATUS = {
    SUBMITTED: "Envoyée",
    SCREENING: "En étude",
    SHORTLISTED: "Retenue",
    INTERVIEW: "Entretien",
    REJECTED: "Refusée",
    WITHDRAWN: "Retirée",
  };
  const CLOSED = ["REJECTED", "WITHDRAWN"];

  function esc(s) {
    return String(s == null ? "" : s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
  }

  function firstName(full) {
    const part = String(full || "").trim().split(/\s+/)[0];
    return part || "Bonjour";
  }

  function cvScore(me) {
    if (!me) return 0;
    let score = 0;
    if (me.cvFileName) score += 25;
    if (String(me.cvSections || "").trim()) score += 25;
    if (String(me.skills || "").trim()) score += 25;
    if (String(me.soughtRoles || "").trim()) score += 15;
    if (me.nationality || me.city) score += 10;
    return Math.min(100, score);
  }

  async function showDashCv(me) {
    const frame = document.getElementById("dash-cv-frame");
    const empty = document.getElementById("dash-cv-empty");
    const open = document.getElementById("cv-open");
    if (!me || !me.cvFileName) {
      if (frame) frame.hidden = true;
      if (empty) empty.hidden = false;
      return;
    }
    try {
      const token = HireLinkAPI.getToken();
      const res = await fetch(HireLinkAPI.API_BASE + "/me/cv", {
        headers: token ? { Authorization: "Bearer " + token } : {},
      });
      if (!res.ok) return;
      const blob = await res.blob();
      const url = URL.createObjectURL(blob);
      if (frame) {
        frame.src = url;
        frame.hidden = false;
      }
      if (empty) empty.hidden = true;
      if (open) {
        open.href = url;
        open.target = "_blank";
      }
    } catch (_) {}
  }

  const cvFile = document.getElementById("dash-cv-file");
  if (cvFile) {
    cvFile.addEventListener("change", async function () {
      const file = cvFile.files && cvFile.files[0];
      if (!file) return;
      const fd = new FormData();
      fd.append("file", file);
      try {
        const token = HireLinkAPI.getToken();
        const res = await fetch(HireLinkAPI.API_BASE + "/me/cv", {
          method: "POST",
          headers: token ? { Authorization: "Bearer " + token } : {},
          body: fd,
        });
        if (!res.ok) throw new Error("Envoi impossible");
        HireLink.toast("CV", "Document enregistré", "ok");
        location.reload();
      } catch (err) {
        HireLink.toast("CV", err.message, "danger");
      }
    });
  }

  const search = document.getElementById("dash-search");
  if (search) {
    search.addEventListener("keydown", function (e) {
      if (e.key !== "Enter") return;
      const q = search.value.trim();
      location.href = q ? "candidate.html?q=" + encodeURIComponent(q) : "candidate.html";
    });
  }

  Promise.all([
    HireLinkAPI.api("/me").catch(function () { return null; }),
    HireLinkAPI.api("/applications/mine").catch(function () { return []; }),
    HireLinkAPI.api("/offers/public").catch(function () { return []; }),
  ]).then(function (pack) {
    const me = pack[0];
    const apps = pack[1] || [];
    const offers = pack[2] || [];
    const sent = {};
    apps.forEach(function (a) {
      const key = a.jobOfferId || a.offerId;
      if (key) sent[key] = a.status;
    });
    const active = apps.filter(function (a) { return CLOSED.indexOf(a.status) < 0; });
    const interviews = apps.filter(function (a) { return a.status === "INTERVIEW"; });
    const openOffers = offers.filter(function (o) {
      const st = sent[o.id];
      return !st || st === "WITHDRAWN";
    });
    openOffers.sort(function (a, b) { return (b.matchScore || 0) - (a.matchScore || 0); });
    const best = openOffers.length && openOffers[0].matchScore != null ? Math.round(openOffers[0].matchScore) : null;

    const emailEl = document.getElementById("hello-email");
    if (emailEl) emailEl.textContent = (me && me.email) || session.email || "";
    const nat = document.getElementById("id-nat");
    const level = document.getElementById("id-level");
    const avail = document.getElementById("id-avail");
    const city = document.getElementById("id-city");
    if (nat) nat.textContent = (me && me.nationality) || "À renseigner";
    if (level) level.textContent = (me && me.experienceLevel) || "À renseigner";
    if (avail) avail.textContent = (me && me.availability) || "À renseigner";
    if (city) city.textContent = (me && (me.city || me.preferredLocations)) || "À renseigner";

    document.getElementById("hello").textContent = (me && me.fullName) || session.fullName || "Votre profil";
    const sentEl = document.getElementById("st-sent");
    if (sentEl) sentEl.textContent = String(apps.length);
    const vis = document.getElementById("st-visible");
    if (vis) vis.textContent = me && me.cvFileName ? "1" : "0";
    const spont = document.getElementById("st-spont");
    if (spont) spont.textContent = "0";
    const score = cvScore(me);
    const scoreEl = document.getElementById("dash-score");
    const bar = document.getElementById("dash-score-bar");
    if (scoreEl) scoreEl.textContent = score + "%";
    if (bar) bar.style.width = score + "%";
    showDashCv(me);

    const strong = openOffers.filter(function (o) { return (o.matchScore || 0) >= 60; }).length;
    const line = document.getElementById("hello-line");
    if (!apps.length && !(me && me.cvFileName)) {
      line.textContent = "Déposez votre CV, puis envoyez une première candidature. Le tableau se remplit ensuite tout seul.";
    } else if (interviews.length) {
      line.textContent = interviews.length + " entretien" + (interviews.length > 1 ? "s" : "") + " à préparer. Le reste de vos dossiers suit son cours.";
    } else if (strong) {
      line.textContent = strong + " offre" + (strong > 1 ? "s" : "") + " dépasse" + (strong > 1 ? "nt" : "") + " 60 % de compatibilité et n’" + (strong > 1 ? "ont" : "a") + " pas encore été envoyée" + (strong > 1 ? "s" : "") + ".";
    } else {
      line.textContent = active.length + " dossier" + (active.length > 1 ? "s" : "") + " en cours. La synthèse peut relire le CV si une étape bloque.";
    }

    const actions = [];
    if (!me || !me.cvFileName) {
      actions.push({ title: "Déposer le CV", text: "Le recruteur lit le document original et les sections extraites.", href: "candidate-profile.html", label: "Ouvrir le profil" });
    }
    if (!me || !String(me.skills || "").trim()) {
      actions.push({ title: "Renseigner les compétences", text: "Elles servent à calculer la compatibilité avec chaque offre.", href: "candidate-profile.html", label: "Compléter" });
    }
    interviews.slice(0, 1).forEach(function (a) {
      actions.push({ title: "Préparer " + (a.jobTitle || "l’entretien"), text: "Le dossier est au stade entretien. Relisez le poste avant le jour J.", href: "candidate-applications.html", label: "Voir le dossier" });
    });
    if (strong && actions.length < 3) {
      actions.push({ title: "Postuler aux offres proches", text: strong + " poste" + (strong > 1 ? "s correspondent" : " correspond") + " à votre profil.", href: "candidate.html?scope=mine", label: "Voir les offres" });
    }
    if (!actions.length) {
      actions.push({ title: "Rien d’urgent", text: "Votre profil est en place. Parcourez le catalogue ou suivez vos dossiers.", href: "candidate.html", label: "Voir les offres" });
    }
    const next = document.getElementById("next-actions");
    if (next) {
      next.innerHTML = actions.slice(0, 3).map(function (a) {
        return '<article class="dash-action"><div><strong>' + esc(a.title) + '</strong><p>' + esc(a.text) + '</p></div><a class="btn btn-primary btn-sm" href="' + a.href + '">' + esc(a.label) + "</a></article>";
      }).join("");
    }

    const reco = document.getElementById("reco-list");
    const top = openOffers.slice(0, 4);
    reco.innerHTML = top.length
      ? top.map(function (o) {
          const place = (o.location || "").split(/[·,]/)[0].trim();
          return '<article class="reco-card"><div><strong>' + esc(o.title || "Offre") + '</strong><p>' + esc(o.companyName || "Entreprise") + (place ? " · " + esc(place) : "") + (o.contractType ? " · " + esc(o.contractType) : "") + '</p><p class="muted">' + esc(String(o.description || "").slice(0, 160)) + '</p></div><a class="btn btn-primary btn-sm" href="candidate.html?offer=' + encodeURIComponent(o.id) + '">Postuler</a></article>';
        }).join("")
      : '<p class="muted">Aucune offre ouverte pour le moment.</p>';

    const stages = ["SUBMITTED", "SCREENING", "SHORTLISTED", "INTERVIEW"];
    const stageCount = {};
    stages.forEach(function (key) {
      stageCount[key] = apps.filter(function (a) { return a.status === key; }).length;
    });
    const stageMax = Math.max(1, stageCount.SUBMITTED, stageCount.SCREENING, stageCount.SHORTLISTED, stageCount.INTERVIEW);
    const funnel = document.getElementById("funnel");
    if (funnel) {
      funnel.innerHTML = stages.map(function (key) {
        const n = stageCount[key];
        const width = Math.round((n / stageMax) * 100);
        return '<div class="funnel-row"><span>' + STATUS[key] + '</span><div class="reliability-track"><span style="width:' + width + '%"></span></div><b>' + n + "</b></div>";
      }).join("");
    }
    const openApps = apps.filter(function (a) { return CLOSED.indexOf(a.status) < 0; });
    const late = openApps.filter(function (a) { return a.slaRemainingDays != null && a.slaRemainingDays < 0; });
    const watched = openApps.filter(function (a) { return a.slaRemainingDays != null; });
    const lateEl = document.getElementById("st-late");
    if (lateEl) lateEl.textContent = String(late.length);
    const advice = document.getElementById("side-advice-text");
    if (advice) {
      if (!me || !me.cvFileName) advice.textContent = "Ce mois-ci : déposez le CV. La synthèse et le classement des offres lisent ce document.";
      else if (interviews.length) advice.textContent = "Ce mois-ci : un entretien est ouvert. Relisez le poste. La synthèse ne répond pas à votre place.";
      else if (late.length) advice.textContent = "Ce mois-ci : " + late.length + " employeur" + (late.length > 1 ? "s ont" : " a") + " dépassé le délai qu’ils avaient affiché.";
      else advice.textContent = "Ce mois-ci : la synthèse peut relire votre CV et dire ce qui manque. Elle ne postule pas.";
    }
    const sla = document.getElementById("sla-readout");
    if (sla) {
      if (!watched.length) {
        sla.textContent = "Aucun délai de première réponse n’est encore suivi. Il apparaît dès qu’une entreprise publie un engagement sur l’offre.";
      } else if (late.length) {
        sla.textContent = late.length + " employeur" + (late.length > 1 ? "s ont" : " a") + " dépassé le délai qu’ils avaient publié. " + (watched.length - late.length) + " dossier" + (watched.length - late.length > 1 ? "s restent" : " reste") + " dans les temps.";
      } else {
        sla.textContent = watched.length + " dossier" + (watched.length > 1 ? "s sont" : " est") + " encore dans le délai promis par l’employeur.";
      }
    }
    const checks = [
      [!!(me && me.cvFileName), "CV déposé"],
      [!!(me && String(me.cvSections || "").trim()), "Sections du CV confirmées"],
      [!!(me && String(me.skills || "").trim()), "Compétences utilisées pour le score"],
      [!!(me && String(me.soughtRoles || "").trim()), "Métier recherché"],
    ];
    const done = checks.filter(function (c) { return c[0]; }).length;
    const readyScore = document.getElementById("ready-score");
    const readyPct = Math.round((done / checks.length) * 100);
    if (readyScore) readyScore.textContent = readyPct + "%";
    const readyBar = document.getElementById("ready-bar");
    if (readyBar) readyBar.style.width = readyPct + "%";
    const ready = document.getElementById("ready");
    if (ready) {
      ready.innerHTML = checks.map(function (c) {
        return '<p class="ready-line' + (c[0] ? " is-on" : "") + '">' + (c[0] ? "✓" : "○") + " " + c[1] + "</p>";
      }).join("");
    }

    const recent = document.getElementById("recent-list");
    const last = apps.slice(0, 4);
    recent.innerHTML = last.length
      ? last.map(function (a) {
          const pct = a.matchScore == null ? "" : " · " + Math.round(a.matchScore) + " %";
          return '<article class="dash-action"><div><strong>' + esc(a.jobTitle || "Candidature") + '</strong><p>' + esc(STATUS[a.status] || a.status || "") + pct + '</p></div><a class="btn btn-soft btn-sm" href="candidate-applications.html">Suivre</a></article>';
        }).join("")
      : '<p class="muted">Aucune candidature envoyée. Commencez par une offre qui correspond à vos compétences.</p>';
  }).catch(function (err) {
    HireLink.toast("Tableau de bord", err.message, "danger");
  });
})();
