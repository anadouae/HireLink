/* Factures : même document dans l'espace entreprise et dans l'espace du débiteur. */
(function (global) {
  function esc(s) {
    return String(s == null ? "" : s).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
  }
  function money(n) {
    return Number(n || 0).toLocaleString("fr-FR", { minimumFractionDigits: 2, maximumFractionDigits: 2 }) + " MAD";
  }
  const LABEL = { NON_PAYEE: "Non payée", PAYEE: "Payée", ANNULEE: "Annulée", AVOIR: "Avoir historique" };

  async function pdf(id, mode) {
    const res = await HireLinkAPI.authFetch("/billing/invoices/" + id + "/pdf");
    if (!res.ok) throw new Error("PDF indisponible");
    const blob = await res.blob();
    const url = URL.createObjectURL(blob);
    if (mode === "download") {
      const a = document.createElement("a");
      a.href = url;
      a.download = "facture-" + id + ".pdf";
      a.click();
    } else {
      window.open(url);
    }
    setTimeout(() => URL.revokeObjectURL(url), 60000);
  }

  function payModal(row, onDone) {
    let key = (global.crypto && crypto.randomUUID ? crypto.randomUUID() : "idem-" + Date.now()).replace(/[^A-Za-z0-9_-]/g, "").slice(0, 80);
    const overlay = document.createElement("div");
    overlay.className = "hl-pay";
    overlay.innerHTML =
      '<div class="hl-pay-card" role="dialog" aria-modal="true" aria-labelledby="hl-pay-title">' +
      '<section class="hl-pay-sum"><p>HireLink</p><h2>' + esc(row.planLabel || "Abonnement") + "</h2><p>" + esc(row.number) + "</p><strong>" + money(row.amountTtc) + "</strong></section>" +
      '<section class="hl-pay-form"><h2 id="hl-pay-title">Paiement simulé</h2><p class="hl-pay-banner">Mode démonstration — Aucun débit réel</p>' +
      '<label>Scénario<select id="hl-pay-scenario"><option value="SUCCESS">Paiement réussi</option><option value="FAILURE">Paiement refusé</option></select></label>' +
      '<p id="hl-pay-msg" class="hl-pay-msg" hidden></p><div class="hl-pay-actions"><button type="button" class="btn btn-ghost" id="hl-pay-cancel">Fermer</button><button type="button" class="btn btn-primary" id="hl-pay-go">Confirmer</button></div></section></div>';
    document.body.appendChild(overlay);
    const close = () => overlay.remove();
    overlay.querySelector("#hl-pay-cancel").onclick = close;
    overlay.querySelector("#hl-pay-go").onclick = async () => {
      const btn = overlay.querySelector("#hl-pay-go");
      const msg = overlay.querySelector("#hl-pay-msg");
      btn.disabled = true;
      msg.hidden = false;
      msg.textContent = "Traitement…";
      try {
        sessionStorage.setItem("hirelink.pay." + row.id, key);
        const r = await HireLinkAPI.api("/billing/payments", {
          method: "POST",
          body: { invoiceId: row.id, scenario: overlay.querySelector("#hl-pay-scenario").value, idempotencyKey: key },
        });
        if (r.status === "SUCCEEDED") {
          if (r.planTier) HireLinkAPI.setPlanTier(r.planTier);
          msg.textContent = "Paiement confirmé. Référence " + (r.reference || "");
          if (onDone) onDone();
        } else {
          msg.textContent = r.failureReason || "Paiement refusé. La facture reste non payée. Vous pouvez réessayer.";
          key = (global.crypto && crypto.randomUUID ? crypto.randomUUID() : "idem-" + Date.now()).replace(/[^A-Za-z0-9_-]/g, "").slice(0, 80);
          btn.disabled = false;
        }
      } catch (err) {
        msg.textContent = err.message;
        btn.disabled = false;
      }
    };
  }

  async function mount(el, scope) {
    if (!el) return;
    el.innerHTML = "<p>Chargement des factures…</p>";
    try {
      const rows = await HireLinkAPI.api("/billing/invoices?scope=" + encodeURIComponent(scope));
      if (!rows.length) {
        el.innerHTML = "<p>Aucune facture pour le moment.</p>";
        return;
      }
      el.innerHTML = '<div class="rc-tablewrap"><table class="rc-table"><thead><tr><th>N°</th><th>Date</th><th>Montant</th><th>Statut</th><th></th></tr></thead><tbody>' +
        rows.map((r) => '<tr><td>' + esc(r.number) + "</td><td>" + esc(String(r.issuedAt).slice(0, 10)) + "</td><td>" + money(r.amountTtc) +
          "</td><td>" + esc(LABEL[r.status] || r.status) + '</td><td><button type="button" class="btn btn-ghost btn-sm" data-view="' + r.id + '">Voir</button> <button type="button" class="btn btn-ghost btn-sm" data-dl="' + r.id + '">Télécharger</button>' +
          (r.canPay ? ' <button type="button" class="btn btn-primary btn-sm" data-pay="' + r.id + '">Payer</button>' : "") + "</td></tr>").join("") +
        "</tbody></table></div>";
      el.onclick = async (e) => {
        const view = e.target.closest("[data-view]");
        const dl = e.target.closest("[data-dl]");
        const pay = e.target.closest("[data-pay]");
        try {
          if (view) return pdf(view.dataset.view, "view");
          if (dl) return pdf(dl.dataset.dl, "download");
          if (pay) {
            const row = rows.find((x) => String(x.id) === pay.dataset.pay);
            payModal(row, () => mount(el, scope));
          }
        } catch (err) {
          if (global.HireLink) HireLink.toast("Facture", err.message, "danger");
        }
      };
    } catch (err) {
      el.innerHTML = "<p>" + esc(err.message) + "</p>";
    }
  }

  global.HireLinkInvoices = { mount, payModal };
})(window);
