(function () {
  const params = new URLSearchParams(location.search);
  let session = null;
  try { session = JSON.parse(localStorage.getItem("hirelink.session") || "null"); } catch (_) {}

  function audienceFromSession() {
    if (params.get("plan") === "recruiter") return "RECRUTEUR";
    if (params.get("plan") === "candidate") return "CANDIDAT";
    if (session && session.role === "RECRUTEUR") return "RECRUTEUR";
    if (session && session.role === "CANDIDAT") return "CANDIDAT";
    return "CANDIDAT";
  }

  const audience = audienceFromSession();
  const storageKey = "hirelink.checkout." + audience.toLowerCase();
  let cycle = params.get("cycle") === "year" ? "year" : "month";
  let plans = [];
  let selectedCode = null;
  let quote = null;
  let invoice = null;
  let step = 1;
  let paying = false;
  let lastPayment = null;
  let idempotencyKey = newKey();

  const backFile = params.get("back") || "";
  const appHref = /^[\w.-]+\.html$/.test(backFile)
    ? "app/" + backFile
    : audience === "RECRUTEUR" ? "app/recruiter.html"
    : audience === "CANDIDAT" ? "app/candidate-dashboard.html"
    : session && session.role === "ADMINISTRATEUR" ? "app/admin.html"
    : "index.html";

  const goto = document.getElementById("ck-goto-app");
  if (goto) {
    goto.href = appHref;
    goto.textContent = audience === "CANDIDAT"
      ? "Accéder à mon espace candidat"
      : "Accéder à mon espace recruteur";
  }
  const backTop = document.getElementById("ck-back-top");
  if (backTop) backTop.href = appHref;
  const brand = document.getElementById("ck-brand");
  if (brand) brand.href = appHref;

  function newKey() {
    return (crypto.randomUUID ? crypto.randomUUID() : "idem" + Date.now())
      .replace(/[^A-Za-z0-9_-]/g, "")
      .slice(0, 80);
  }

  function money(n) {
    if (n == null || n === "") return "—";
    return Number(n).toLocaleString("fr-FR", { minimumFractionDigits: 2, maximumFractionDigits: 2 }) + " MAD";
  }

  function moneyInt(n) {
    if (n == null || n === "") return "—";
    const v = Number(n);
    if (Number.isInteger(v)) return v.toLocaleString("fr-FR") + " MAD";
    return money(n);
  }

  function showErr(id, msg) {
    const el = document.getElementById(id);
    if (!el) return;
    el.hidden = !msg;
    el.textContent = msg || "";
  }

  function setStep(n) {
    step = n;
    document.getElementById("ck-step-1").hidden = n !== 1;
    document.getElementById("ck-step-2").hidden = n !== 2;
    document.getElementById("ck-step-3").hidden = n !== 3;
    document.getElementById("ck-step-fail").hidden = n !== "fail";
    document.querySelectorAll("#ck-progress li").forEach((li) => {
      const s = Number(li.getAttribute("data-step"));
      li.classList.toggle("is-active", n === s || (n === "fail" && s === 2));
      li.classList.toggle("is-done", typeof n === "number" && s < n);
    });
    window.scrollTo({ top: 0, behavior: "smooth" });
  }

  function iconFor(code) {
    const c = String(code || "").toUpperCase();
    if (c === "PREMIUM" || c === "PROFESSIONNEL" || c === "ENTREPRISE") {
      return { cls: "ck-plan-icon ck-plan-icon--gold", svg: '<svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M3 8l4.5 3L12 4l4.5 7L21 8v10H3V8z"/></svg>' };
    }
    if (c === "PERFORMANCE") {
      return { cls: "ck-plan-icon ck-plan-icon--rose", svg: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M4 19V5M4 19h16"/><path d="M8 15v-4M12 15V8M16 15v-7"/></svg>' };
    }
    if (c === "ACCES" || c === "ESSENTIEL" || c === "GRATUIT") {
      return { cls: "ck-plan-icon", svg: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="5" y="11" width="14" height="10" rx="2"/><path d="M8 11V8a4 4 0 0 1 8 0v3"/></svg>' };
    }
    return { cls: "ck-plan-icon", svg: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="8" r="3"/><path d="M5 19c1.5-3 4-5 7-5s5.5 2 7 5"/></svg>' };
  }

  function paidCode() {
    const q = new URLSearchParams(location.search).get("planCode");
    if (q) return String(q).toUpperCase();
    return audience === "RECRUTEUR" ? "PROFESSIONNEL" : "PERFORMANCE";
  }

  function selectedPlan() {
    return plans.find((p) => p.code === selectedCode) || null;
  }

  function yearlySavePct(p) {
    if (!p || !p.monthlyMad || !p.yearlyMad) return 0;
    const full = p.monthlyMad * 12;
    if (full <= 0 || p.yearlyMad >= full) return 0;
    return Math.round((1 - p.yearlyMad / full) * 100);
  }

  function paintCycle() {
    document.querySelectorAll(".ck-cycle button").forEach((b) => {
      const on = b.getAttribute("data-cycle") === cycle;
      b.classList.toggle("is-on", on);
      b.setAttribute("aria-pressed", on ? "true" : "false");
    });
    const paid = plans.find((p) => p.code === paidCode());
    const save = yearlySavePct(paid);
    const el = document.getElementById("ck-year-save");
    if (el) el.textContent = save > 0 ? "(-" + save + "%)" : "";
  }

  function paintPlans() {
    const root = document.getElementById("ck-plans");
    root.innerHTML = "";
    plans.forEach((p) => {
      const price = cycle === "year" ? p.yearlyMad : p.monthlyMad;
      const period = cycle === "year" ? "/ an" : "/ mois";
      const feats = String(p.features || "").split(/\n+/).filter(Boolean).slice(0, 5);
      const icon = iconFor(p.code);
      const blurb = feats[0]
        ? (p.credits ? p.credits + " usages IA · " + feats.slice(0, 2).join(" · ") : feats.slice(0, 2).join(" · "))
        : (p.detail || "Formule HireLink");
      const btn = document.createElement("button");
      btn.type = "button";
      btn.className = "ck-plan" + (p.code === selectedCode ? " is-selected" : "") + (price <= 0 ? " is-free-only" : "");
      btn.setAttribute("role", "option");
      btn.setAttribute("aria-selected", p.code === selectedCode ? "true" : "false");
      btn.dataset.code = p.code;
      btn.innerHTML =
        '<span class="' + icon.cls + '">' + icon.svg + "</span>" +
        '<span class="ck-plan-body"><h3></h3><p></p><ul class="ck-plan-feats"></ul></span>' +
        '<span class="ck-plan-price"><strong></strong><span></span></span>';
      btn.querySelector("h3").textContent = p.label || p.code;
      btn.querySelector("p").textContent = blurb.length > 110 ? blurb.slice(0, 107) + "…" : blurb;
      const ul = btn.querySelector(".ck-plan-feats");
      feats.forEach((f) => {
        const li = document.createElement("li");
        li.textContent = f;
        ul.appendChild(li);
      });
      const strong = btn.querySelector(".ck-plan-price strong");
      const span = btn.querySelector(".ck-plan-price span");
      if (price <= 0) {
        strong.textContent = "Gratuit";
        strong.classList.add("is-free");
        span.textContent = "formule de base";
      } else {
        strong.textContent = moneyInt(price).replace(" MAD", "");
        strong.insertAdjacentHTML("beforeend", ' <small style="font-size:0.75em;font-weight:600">MAD</small>');
        span.textContent = period;
      }
      btn.addEventListener("click", () => {
        selectedCode = p.code;
        paintPlans();
        loadQuote();
      });
      root.appendChild(btn);
    });
  }

  function quoteFromPlan(plan, cyc) {
    const amount = Number(cyc === "year" ? plan.yearlyMad : plan.monthlyMad) || 0;
    return {
      plan: plan.code,
      planLabel: plan.label,
      cycle: cyc,
      cycleLabel: cyc === "year" ? "Annuel" : "Mensuel",
      currency: "MAD",
      features: plan.features || "",
      credits: plan.credits,
      payable: amount > 0,
      amountHt: amount,
      tva: 0,
      amountTtc: amount,
      vatConfigured: false,
      message: amount > 0 ? "Montant catalogue" : "Formule gratuite : aucun paiement.",
    };
  }

  function paintTotalsFromQuote(q) {
    quote = q;
    const amountEl = document.getElementById("ck-total-amount");
    const noteEl = document.getElementById("ck-total-note");
    const labelEl = document.getElementById("ck-total-label");
    const toPay = document.getElementById("ck-to-pay");
    if (!q) {
      amountEl.textContent = "—";
      noteEl.textContent = "Devis indisponible";
      toPay.disabled = true;
      return;
    }
    const payable = q.payable !== false && Number(q.amountTtc) > 0 && selectedCode === paidCode();
    amountEl.textContent = money(q.amountTtc);
    labelEl.textContent = payable ? "Total à payer" : "Montant";
    // Le détail HT / TVA figure sur la facture, pas sur le choix de la formule.
    noteEl.textContent = Number(q.amountTtc) > 0 ? (cycle === "year" ? "Par an" : "Par mois") : "Formule gratuite";
    toPay.disabled = !payable;
    if (selectedCode !== paidCode()) {
      const paid = plans.find((p) => p.code === paidCode());
      const paidLabel = (paid && paid.label) || (audience === "RECRUTEUR" ? "Professionnel" : "Performance");
      showErr("ck-error-1", "Cette formule est gratuite : aucun paiement n’est requis. Choisissez " +
        paidLabel + " pour continuer.");
    } else {
      showErr("ck-error-1", "");
    }

    document.getElementById("ck-recap-plan").textContent = q.planLabel || "—";
    document.getElementById("ck-recap-cycle").textContent = q.cycleLabel || (cycle === "year" ? "Annuel" : "Mensuel");
    document.getElementById("ck-recap-ttc").textContent = money(q.amountTtc);

    const holder = document.getElementById("ck-holder");
    if (holder && !holder.value && session && session.fullName) holder.placeholder = session.fullName;
  }

  async function loadPlans() {
    const all = await HireLinkAPI.api("/billing/plans");
    plans = (Array.isArray(all) ? all : []).filter((p) => String(p.audience).toUpperCase() === audience);
    plans.sort((a, b) => Number(a.monthlyMad || 0) - Number(b.monthlyMad || 0));
    selectedCode = plans.some((p) => p.code === paidCode()) ? paidCode() : (plans[0] && plans[0].code);
    paintCycle();
    paintPlans();
  }

  async function loadQuote() {
    showErr("ck-error-1", "");
    const free = selectedPlan();
    if (selectedCode !== paidCode()) {
      paintTotalsFromQuote({
        plan: free && free.code,
        planLabel: free && free.label,
        cycle,
        cycleLabel: cycle === "year" ? "Annuel" : "Mensuel",
        amountHt: 0,
        amountTtc: 0,
        tva: 0,
        vatConfigured: false,
        payable: false,
        features: free && free.features,
      });
      return;
    }
    try {
      // Le serveur facture le palier demandé (Professionnel / Entreprise / Performance).
      const q = await HireLinkAPI.api("/billing/quote?cycle=" + encodeURIComponent(cycle) + "&plan=" + encodeURIComponent(paidCode()));
      paintTotalsFromQuote(q);
    } catch (err) {
      // Certains backends n'exposent pas encore /billing/quote : repli sur le catalogue /plans.
      const paid = plans.find((p) => p.code === paidCode());
      if (paid) {
        paintTotalsFromQuote(quoteFromPlan(paid, cycle));
        showErr("ck-error-1", "");
      } else {
        paintTotalsFromQuote(null);
        showErr("ck-error-1", err.message || "Impossible de charger le devis.");
      }
    }
  }

  function paintSuccess(paid) {
    lastPayment = paid;
    const inv = paid.invoice || invoice;
    const label = (inv && inv.planLabel) || (quote && quote.planLabel) || paidCode();
    const cycleLabel = inv && inv.cycle === "year" ? "Annuel" : (quote && quote.cycleLabel) || (cycle === "year" ? "Annuel" : "Mensuel");
    document.getElementById("ck-success-lead").textContent =
      "Votre abonnement " + label + " (" + cycleLabel + ") a été activé avec succès.";
    document.getElementById("ck-ok-plan").textContent = label;
    document.getElementById("ck-ok-cycle").textContent = cycleLabel;
    document.getElementById("ck-ok-amount").textContent = money(inv && inv.amountTtc != null ? inv.amountTtc : quote && quote.amountTtc);
    document.getElementById("ck-ok-ref").textContent = paid.reference || (inv && inv.number) || "—";
    if (paid.planTier) HireLinkAPI.setPlanTier(paid.planTier);

    const pdfBtn = document.getElementById("ck-pdf");
    if (pdfBtn && inv && inv.id) {
      pdfBtn.hidden = false;
      pdfBtn.onclick = async () => {
        try {
          const res = await HireLinkAPI.authFetch("/billing/invoices/" + inv.id + "/pdf");
          if (!res.ok) throw new Error("PDF indisponible");
          const blob = await res.blob();
          const url = URL.createObjectURL(blob);
          const a = document.createElement("a");
          a.href = url;
          a.download = "recapitulatif-" + (inv.number || inv.id) + ".pdf";
          document.body.appendChild(a);
          a.click();
          a.remove();
          setTimeout(() => URL.revokeObjectURL(url), 60000);
        } catch (e) {
          alert(e.message || "Impossible de télécharger le PDF.");
        }
      };
    } else if (pdfBtn) {
      pdfBtn.hidden = true;
    }
    setStep(3);
  }

  function cardLooksFilled() {
    // Contrôle de saisie seulement : les champs carte restent dans le navigateur (prestataire de paiement à brancher ici).
    const pan = String((document.getElementById("ck-pan") || {}).value || "").replace(/\D/g, "");
    const exp = String((document.getElementById("ck-exp") || {}).value || "").trim();
    const cvc = String((document.getElementById("ck-cvc") || {}).value || "").replace(/\D/g, "");
    const holder = String((document.getElementById("ck-holder") || {}).value || "").trim();
    if (!pan) return "Indiquez un numéro de carte.";
    if (!exp) return "Indiquez la date d’expiration.";
    if (!cvc) return "Indiquez le code de sécurité.";
    if (!holder) return "Indiquez le nom du titulaire.";
    return "";
  }

  async function confirmPay() {
    if (paying) return;
    const cardErr = cardLooksFilled();
    if (cardErr) {
      showErr("ck-error-2", cardErr);
      return;
    }
    paying = true;
    const btn = document.getElementById("ck-confirm");
    const status = document.getElementById("ck-status");
    btn.disabled = true;
    status.hidden = false;
    status.textContent = "Traitement du paiement…";
    showErr("ck-error-2", "");
    try {
      if (!invoice) {
        const prepared = await HireLinkAPI.api("/billing/subscribe", { method: "POST", body: { cycle, plan: paidCode() } });
        invoice = prepared.invoice;
        if (!invoice) throw new Error("Aucune facture n’a été émise.");
      }
      sessionStorage.setItem(storageKey, idempotencyKey);
      // Les champs carte restent locaux : jamais envoyés au serveur. Le paiement est toujours accepté côté API.
      const paid = await HireLinkAPI.api("/billing/payments", {
        method: "POST",
        body: {
          invoiceId: invoice.id,
          scenario: "SUCCESS",
          idempotencyKey,
        },
      });
      if (paid.status !== "SUCCEEDED") {
        idempotencyKey = newKey();
        sessionStorage.removeItem(storageKey);
        document.getElementById("ck-fail-lead").textContent =
          paid.failureReason || "Le paiement a été refusé. Aucun abonnement n’a été activé.";
        setStep("fail");
        return;
      }
      paintSuccess(paid);
    } catch (err) {
      showErr("ck-error-2", err.message || "Erreur serveur. Réessayez.");
      idempotencyKey = newKey();
    } finally {
      paying = false;
      btn.disabled = false;
      status.hidden = true;
    }
  }

  document.querySelector(".ck-cycle").addEventListener("click", (e) => {
    const btn = e.target.closest("button[data-cycle]");
    if (!btn || step !== 1) return;
    cycle = btn.getAttribute("data-cycle");
    invoice = null;
    paintCycle();
    paintPlans();
    loadQuote();
  });

  document.getElementById("ck-to-pay").addEventListener("click", () => {
    if (!quote || quote.payable === false || selectedCode !== paidCode()) return;
    showErr("ck-error-2", "");
    setStep(2);
  });

  document.getElementById("ck-back").addEventListener("click", () => {
    invoice = null;
    setStep(1);
  });

  document.getElementById("ck-confirm").addEventListener("click", confirmPay);

  const panInput = document.getElementById("ck-pan");
  if (panInput) {
    panInput.addEventListener("input", () => {
      // Carte classique : 16 chiffres max (affichés par groupes de 4).
      const digits = panInput.value.replace(/\D/g, "").slice(0, 16);
      panInput.value = digits.replace(/(\d{4})(?=\d)/g, "$1 ").trim();
    });
  }
  const expInput = document.getElementById("ck-exp");
  if (expInput) {
    expInput.addEventListener("input", () => {
      const digits = expInput.value.replace(/\D/g, "").slice(0, 4);
      expInput.value = digits.length <= 2 ? digits : digits.slice(0, 2) + " / " + digits.slice(2);
    });
  }
  const cvcInput = document.getElementById("ck-cvc");
  if (cvcInput) {
    cvcInput.addEventListener("input", () => {
      cvcInput.value = cvcInput.value.replace(/\D/g, "").slice(0, 3);
    });
  }

  document.getElementById("ck-retry").addEventListener("click", () => {
    invoice = null;
    idempotencyKey = newKey();
    setStep(2);
  });

  document.getElementById("ck-fail-back").addEventListener("click", () => {
    invoice = null;
    idempotencyKey = newKey();
    setStep(1);
  });

  (async function init() {
    try {
      await loadPlans();
      await loadQuote();

      // Restauration après rechargement (idempotence) — avant d'afficher l'étape 1
      const stored = sessionStorage.getItem(storageKey);
      if (stored) {
        try {
          const r = await HireLinkAPI.api("/billing/payments?idempotencyKey=" + encodeURIComponent(stored));
          if (r && r.status === "SUCCEEDED") {
            invoice = r.invoice || invoice;
            paintSuccess(r);
            return;
          }
          if (r && r.status === "FAILED") sessionStorage.removeItem(storageKey);
        } catch (_) { /* ignore replay errors */ }
      }
      setStep(1);
    } catch (err) {
      showErr("ck-error-1", err.message || "Connexion requise pour afficher les formules.");
    }
  })();
})();
