(function () {
  const params = new URLSearchParams(location.search);
  const plan = params.get("plan") === "recruiter" ? "recruiter" : "candidate";
  let cycle = "month";
  let invoice = null;
  let idempotencyKey = (crypto.randomUUID ? crypto.randomUUID() : "idem" + Date.now()).replace(/[^A-Za-z0-9_-]/g, "").slice(0, 80);
  let session = null;
  try { session = JSON.parse(localStorage.getItem("hirelink.session") || "null"); } catch (_) {}
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
    return Number(n || 0).toLocaleString("fr-FR", { minimumFractionDigits: 2, maximumFractionDigits: 2 }) + " MAD";
  }
  function showError(msg) {
    const el = document.getElementById("pay-error");
    el.hidden = !msg;
    el.textContent = msg || "";
  }
  function paint(inv) {
    document.getElementById("pay-plan").textContent = inv ? inv.planLabel : (plan === "recruiter" ? "Recruteur Premium" : "Candidat Performance");
    document.getElementById("pay-sub").textContent = inv ? "Facture " + inv.number : "La facture est créée au moment de la confirmation. Le montant vient du serveur.";
    document.getElementById("pay-lines").innerHTML = "";
    document.getElementById("pay-ht").textContent = inv ? money(inv.amountHt) : "—";
    document.getElementById("pay-tva-label").textContent = inv && inv.vatConfigured === false ? "TVA non configurée" : "TVA";
    document.getElementById("pay-tva").textContent = inv ? (inv.vatConfigured === false ? "—" : money(inv.tva)) : "—";
    document.getElementById("pay-ttc").textContent = inv ? money(inv.amountTtc) : "—";
  }
  paint(null);
  document.querySelector(".pay-cycle").addEventListener("click", (e) => {
    const btn = e.target.closest("button[data-cycle]");
    if (!btn || invoice) return;
    cycle = btn.getAttribute("data-cycle");
    document.querySelectorAll(".pay-cycle button").forEach((b) => {
      const on = b === btn;
      b.classList.toggle("is-on", on);
      b.setAttribute("aria-pressed", on ? "true" : "false");
    });
  });

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

  document.getElementById("pay-form").addEventListener("submit", async (e) => {
    e.preventDefault();
    const btn = document.getElementById("pay-submit");
    const status = document.getElementById("pay-status");
    btn.disabled = true;
    status.hidden = false;
    status.textContent = "Traitement…";
    showError("");
    try {
      if (!invoice) {
        const prepared = await HireLinkAPI.api("/billing/subscribe", { method: "POST", body: { cycle } });
        invoice = prepared.invoice;
        if (!invoice) throw new Error("Aucune facture n'a été émise.");
        paint(invoice);
      }
      sessionStorage.setItem("hirelink.checkout." + plan, idempotencyKey);
      const paid = await HireLinkAPI.api("/billing/payments", {
        method: "POST",
        body: { invoiceId: invoice.id, scenario: document.getElementById("pay-scenario").value, idempotencyKey },
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
      "<div><dt>Facture</dt><dd>" + (inv && inv.number ? inv.number : "—") + "</dd></div>" +
      "<div><dt>Montant</dt><dd>" + money(inv && inv.amountTtc) + "</dd></div>" +
      "<div><dt>Référence</dt><dd>" + (paid.reference || "—") + "</dd></div>" +
      "<div><dt>Résultat</dt><dd>" + (ok ? "Réussi" : (paid.failureReason || "Refusé")) + "</dd></div>";
    if (ok && paid.planTier) HireLinkAPI.setPlanTier(paid.planTier);
    const pdf = document.getElementById("pay-pdf");
    if (pdf && inv && inv.id) pdf.onclick = () => HireLinkAPI.openFile("/billing/invoices/" + inv.id + "/pdf");
  }
})();
