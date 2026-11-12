(function () {
  const params = new URLSearchParams(location.search);
  let session = null;
  try { session = JSON.parse(localStorage.getItem("hirelink.session") || "null"); } catch (_) {}

  function audienceFromSession() {
    if (params.get("plan") === "recruiter") return "recruiter";
    if (params.get("plan") === "candidate") return "candidate";
    if (session && session.role === "RECRUTEUR") return "recruiter";
    if (session && session.role === "CANDIDAT") return "candidate";
    return "candidate";
  }

  const plan = audienceFromSession();
  let cycle = params.get("cycle") === "year" ? "year" : "month";
  let quote = null;
  let invoice = null;
  let payMethod = "card";
  let idempotencyKey = (crypto.randomUUID ? crypto.randomUUID() : "idem" + Date.now()).replace(/[^A-Za-z0-9_-]/g, "").slice(0, 80);

  const backFile = params.get("back") || "";
  const backHref = /^[\w.-]+\.html$/.test(backFile)
    ? "app/" + backFile
    : session && session.role === "ADMINISTRATEUR" ? "app/admin.html"
    : session && session.role === "RECRUTEUR" ? "app/recruiter.html"
    : session && session.role === "CANDIDAT" ? "app/candidate-dashboard.html"
    : "index.html";
  document.getElementById("pay-back").href = backHref;
  document.getElementById("pay-done").href = backHref;

  function money(n) {
    if (n == null || n === "") return "—";
    return Number(n).toLocaleString("fr-FR", { minimumFractionDigits: 2, maximumFractionDigits: 2 }) + " MAD";
  }
  function showError(msg) {
    const el = document.getElementById("pay-error");
    el.hidden = !msg;
    el.textContent = msg || "";
  }
  function setCycleButtons() {
    document.querySelectorAll(".pay-cycle button").forEach((b) => {
      const on = b.getAttribute("data-cycle") === cycle;
      b.classList.toggle("is-on", on);
      b.setAttribute("aria-pressed", on ? "true" : "false");
    });
  }
  function paintQuote(q) {
    quote = q;
    const label = q && q.planLabel
      ? q.planLabel
      : (plan === "recruiter" ? "Premium" : "Performance");
    document.getElementById("pay-plan").textContent = label;
    const cycleLabel = q && q.cycleLabel ? q.cycleLabel : (cycle === "year" ? "Annuel" : "Mensuel");
    document.getElementById("pay-sub").textContent = q && q.payable === false
      ? (q.message || "Formule gratuite")
      : ("Abonnement " + cycleLabel + " · montants issus du catalogue serveur");
    const lines = document.getElementById("pay-lines");
    lines.innerHTML = "";
    const feats = (q && q.features ? String(q.features) : "").split(/\n+/).filter(Boolean);
    feats.slice(0, 6).forEach((f) => {
      const li = document.createElement("li");
      li.textContent = f;
      lines.appendChild(li);
    });
    const htDt = document.querySelector('#pay-ht') && document.getElementById("pay-ht").closest("div")
      ? document.getElementById("pay-ht").closest("div").querySelector("dt")
      : null;
    const ttcDt = document.querySelector('#pay-ttc') && document.getElementById("pay-ttc").closest("div")
      ? document.getElementById("pay-ttc").closest("div").querySelector("dt")
      : null;
    const vatNote = document.getElementById("pay-vat-note");
    if (!q) {
      if (htDt) htDt.textContent = "Montant catalogue";
      if (ttcDt) ttcDt.textContent = "Total affiché";
      document.getElementById("pay-ht").textContent = "—";
      document.getElementById("pay-tva-label").textContent = "TVA";
      document.getElementById("pay-tva").textContent = "—";
      document.getElementById("pay-ttc").textContent = "—";
      if (vatNote) vatNote.hidden = true;
      return;
    }
    const vatOk = q.vatConfigured === true;
    if (htDt) htDt.textContent = vatOk ? "Sous-total HT" : "Montant catalogue";
    if (ttcDt) ttcDt.textContent = vatOk ? "Total TTC" : "Total affiché (simulation)";
    document.getElementById("pay-ht").textContent = money(q.amountHt);
    if (!vatOk) {
      document.getElementById("pay-tva-label").textContent = "TVA non configurée";
      document.getElementById("pay-tva").textContent = "—";
      if (vatNote) {
        vatNote.hidden = false;
        vatNote.textContent =
          "La TVA n’est pas configurée sur le serveur : ce total n’est pas un TTC fiscal définitif. Aucun paiement réel n’est effectué.";
      }
    } else {
      document.getElementById("pay-tva-label").textContent = "TVA";
      document.getElementById("pay-tva").textContent = money(q.tva);
      if (vatNote) {
        vatNote.hidden = false;
        vatNote.textContent = "Montants calculés côté serveur. Paiement simulé — aucune carte n’est débitée.";
      }
    }
    document.getElementById("pay-ttc").textContent = money(q.amountTtc);
    const submit = document.getElementById("pay-submit");
    if (submit) submit.disabled = q.payable === false;
  }
  function paintInvoice(inv) {
    invoice = inv;
    document.getElementById("pay-plan").textContent = inv.planLabel || document.getElementById("pay-plan").textContent;
    document.getElementById("pay-sub").textContent = "Facture " + inv.number + " · simulation";
    const vatOk = inv.vatConfigured === true;
    const htDt = document.getElementById("pay-ht").closest("div").querySelector("dt");
    const ttcDt = document.getElementById("pay-ttc").closest("div").querySelector("dt");
    if (htDt) htDt.textContent = vatOk ? "Sous-total HT" : "Montant catalogue";
    if (ttcDt) ttcDt.textContent = vatOk ? "Total TTC" : "Total affiché (simulation)";
    document.getElementById("pay-ht").textContent = money(inv.amountHt);
    document.getElementById("pay-tva-label").textContent = vatOk ? "TVA" : "TVA non configurée";
    document.getElementById("pay-tva").textContent = vatOk ? money(inv.tva) : "—";
    document.getElementById("pay-ttc").textContent = money(inv.amountTtc);
    const vatNote = document.getElementById("pay-vat-note");
    if (vatNote) {
      vatNote.hidden = false;
      vatNote.textContent = vatOk
        ? "Facture émise pour simulation. Aucun paiement réel."
        : "TVA non configurée : ce total n’est pas un TTC fiscal définitif. Aucun paiement réel n’est effectué.";
    }
  }

  setCycleButtons();
  paintQuote(null);

  async function loadQuote() {
    showError("");
    try {
      const q = await HireLinkAPI.api("/billing/quote?cycle=" + encodeURIComponent(cycle));
      paintQuote(q);
    } catch (err) {
      paintQuote(null);
      showError(err.message || "Impossible de charger le devis.");
    }
  }

  document.querySelector(".pay-cycle").addEventListener("click", (e) => {
    const btn = e.target.closest("button[data-cycle]");
    if (!btn || invoice) return;
    cycle = btn.getAttribute("data-cycle");
    setCycleButtons();
    loadQuote();
  });

  const methods = document.getElementById("pay-methods");
  if (methods) {
    methods.addEventListener("click", (e) => {
      const btn = e.target.closest("button[data-method]");
      if (!btn) return;
      payMethod = btn.getAttribute("data-method");
      methods.querySelectorAll("button[data-method]").forEach((b) => {
        const on = b === btn;
        b.classList.toggle("is-on", on);
        b.setAttribute("aria-pressed", on ? "true" : "false");
      });
      const hint = document.getElementById("pay-method-hint");
      if (hint) {
        hint.textContent = payMethod === "transfer"
          ? "Virement simulé : aucune RIB réelle n’est demandée."
          : "Carte de test : aucun numéro de carte n’est saisi ni transmis.";
      }
    });
  }

  function newKey() {
    return (crypto.randomUUID ? crypto.randomUUID() : "idem" + Date.now()).replace(/[^A-Za-z0-9_-]/g, "").slice(0, 80);
  }
  const stored = sessionStorage.getItem("hirelink.checkout." + plan);
  if (stored) {
    HireLinkAPI.api("/billing/payments?idempotencyKey=" + encodeURIComponent(stored)).then((r) => {
      if (r && r.status === "SUCCEEDED") finish(r);
      else if (r && r.status === "FAILED") {
        sessionStorage.removeItem("hirelink.checkout." + plan);
        showError(r.failureReason || "Le dernier paiement simulé a été refusé. Vous pouvez réessayer.");
      }
    }).catch(() => {});
  }

  loadQuote();

  document.getElementById("pay-form").addEventListener("submit", async (e) => {
    e.preventDefault();
    const btn = document.getElementById("pay-submit");
    const status = document.getElementById("pay-status");
    btn.disabled = true;
    status.hidden = false;
    status.textContent = "Traitement simulé…";
    showError("");
    try {
      if (!invoice) {
        const prepared = await HireLinkAPI.api("/billing/subscribe", { method: "POST", body: { cycle } });
        invoice = prepared.invoice;
        if (!invoice) throw new Error("Aucune facture n'a été émise.");
        paintInvoice(invoice);
      }
      sessionStorage.setItem("hirelink.checkout." + plan, idempotencyKey);
      const paid = await HireLinkAPI.api("/billing/payments", {
        method: "POST",
        body: {
          invoiceId: invoice.id,
          scenario: document.getElementById("pay-scenario").value,
          idempotencyKey,
          method: payMethod,
        },
      });
      if (paid.status !== "SUCCEEDED") {
        idempotencyKey = newKey();
        showError(paid.failureReason || "Paiement refusé. La facture reste non payée. Vous pouvez réessayer.");
        btn.disabled = false;
        status.hidden = true;
        return;
      }
      finish(paid);
    } catch (err) {
      showError(err.message);
      btn.disabled = false;
      status.hidden = true;
    }
  });

  function finish(paid) {
    const ok = paid.status === "SUCCEEDED";
    document.getElementById("pay-panel").hidden = true;
    const receipt = document.getElementById("pay-receipt");
    receipt.hidden = false;
    document.querySelector("#pay-receipt .pay-kicker").textContent = ok ? "Paiement confirmé" : "Paiement refusé";
    document.querySelector("#pay-receipt h2").textContent = ok ? "Facture payée" : "Facture toujours non payée";
    const inv = paid.invoice || invoice;
    document.getElementById("pay-receipt-body").innerHTML =
      "<div><dt>Abonnement</dt><dd>" + (inv && inv.planLabel ? inv.planLabel : "—") + "</dd></div>" +
      "<div><dt>Période</dt><dd>" + (inv && inv.cycle === "year" ? "Annuel" : "Mensuel") + "</dd></div>" +
      "<div><dt>Facture</dt><dd>" + (inv && inv.number ? inv.number : "—") + "</dd></div>" +
      "<div><dt>" + (inv && inv.vatConfigured === true ? "HT" : "Montant catalogue") + "</dt><dd>" + money(inv && inv.amountHt) + "</dd></div>" +
      "<div><dt>TVA</dt><dd>" + (inv && inv.vatConfigured === true ? money(inv.tva) : "non configurée") + "</dd></div>" +
      "<div><dt>" + (inv && inv.vatConfigured === true ? "Total TTC" : "Total affiché (simulation)") + "</dt><dd>" + money(inv && inv.amountTtc) + "</dd></div>" +
      "<div><dt>Référence</dt><dd>" + (paid.reference || "—") + "</dd></div>" +
      "<div><dt>Résultat</dt><dd>" + (ok ? "Réussi (simulation)" : (paid.failureReason || "Refusé")) + "</dd></div>";
    if (ok && paid.planTier) HireLinkAPI.setPlanTier(paid.planTier);
    const pdf = document.getElementById("pay-pdf");
    if (pdf && inv && inv.id) pdf.onclick = () => HireLinkAPI.openFile("/billing/invoices/" + inv.id + "/pdf");
  }
})();
