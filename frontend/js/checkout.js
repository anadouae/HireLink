(function () {
  const params = new URLSearchParams(location.search);
  const plan = params.get("plan") === "recruiter" ? "recruiter" : "candidate";
  const PLANS = {
    candidate: {
      title: "Candidat Premium",
      month: 39,
      points: ["Alertes de compatibilité", "Préparation d’entretien", "Profil mis en avant"],
    },
    recruiter: {
      title: "Recruteur Premium",
      month: 299,
      points: ["Alertes de délai de réponse", "Sièges d’équipe", "Offres mises en avant"],
    },
  };
  const conf = PLANS[plan];
  let cycle = "month";
  let session = null;
  try {
    session = JSON.parse(localStorage.getItem("hirelink.session") || "null");
  } catch (_) {}

  const backFile = params.get("back") || "";
  const backHref =
    /^[\w.-]+\.html$/.test(backFile)
      ? "app/" + backFile
      : session && session.role === "RECRUTEUR"
        ? "app/recruiter.html"
        : session && session.role === "CANDIDAT"
          ? "app/candidate.html"
          : "index.html";
  document.getElementById("pay-back").href = backHref;
  document.getElementById("pay-done").href = backHref;

  function money(n) {
    return n.toLocaleString("fr-FR", { minimumFractionDigits: 2, maximumFractionDigits: 2 }) + " MAD";
  }

  function totalTtc() {
    return cycle === "year" ? conf.month * 10 : conf.month;
  }

  function paintSummary() {
    const ttc = totalTtc();
    const ht = Math.round((ttc / 1.2) * 100) / 100;
    const tva = Math.round((ttc - ht) * 100) / 100;
    document.getElementById("pay-plan").textContent = conf.title;
    document.getElementById("pay-sub").textContent =
      cycle === "year" ? "Facturation annuelle, deux mois offerts." : "Facturation mensuelle, résiliable à tout moment.";
    document.getElementById("pay-lines").innerHTML = conf.points.map((p) => "<li>" + p + "</li>").join("");
    document.getElementById("pay-ht").textContent = money(ht);
    document.getElementById("pay-tva").textContent = money(tva);
    document.getElementById("pay-ttc").textContent = money(ttc);
    document.getElementById("pay-submit").textContent = "Payer " + money(ttc);
    document.querySelectorAll(".pay-cycle button").forEach((b) => {
      b.classList.toggle("is-on", b.getAttribute("data-cycle") === cycle);
    });
  }

  document.querySelector(".pay-cycle").addEventListener("click", (e) => {
    const btn = e.target.closest("button[data-cycle]");
    if (!btn) return;
    cycle = btn.getAttribute("data-cycle");
    paintSummary();
  });

  function brandOf(digits) {
    if (/^4/.test(digits)) return "Visa";
    if (/^5[1-5]/.test(digits) || /^2/.test(digits)) return "Mastercard";
    if (/^3[47]/.test(digits)) return "Amex";
    return digits ? "Carte" : "Carte";
  }

  function luhn(num) {
    let sum = 0;
    let alt = false;
    for (let i = num.length - 1; i >= 0; i--) {
      let n = parseInt(num[i], 10);
      if (alt) {
        n *= 2;
        if (n > 9) n -= 9;
      }
      sum += n;
      alt = !alt;
    }
    return sum % 10 === 0;
  }

  const number = document.getElementById("pay-number");
  number.addEventListener("input", () => {
    const digits = number.value.replace(/\D/g, "").slice(0, 16);
    number.value = digits.replace(/(\d{4})(?=\d)/g, "$1 ").trim();
    document.getElementById("pay-brand").textContent = brandOf(digits);
  });
  document.getElementById("pay-exp").addEventListener("input", (e) => {
    let v = e.target.value.replace(/\D/g, "").slice(0, 4);
    if (v.length >= 3) v = v.slice(0, 2) + "/" + v.slice(2);
    e.target.value = v;
  });
  document.getElementById("pay-cvc").addEventListener("input", (e) => {
    e.target.value = e.target.value.replace(/\D/g, "").slice(0, 4);
  });

  if (session && session.email) document.getElementById("pay-email").value = session.email;
  const holder = session && (session.fullName || session.name);
  if (holder) document.getElementById("pay-holder").value = holder;

  const savedKey = "hirelink.paymethod." + plan;
  let saved = null;
  try {
    saved = JSON.parse(localStorage.getItem(savedKey) || "null");
  } catch (_) {}
  const savedBox = document.getElementById("pay-saved");
  const newFields = document.getElementById("pay-new-fields");
  function syncMethod() {
    if (savedBox.hidden) return;
    const useNew = savedBox.querySelector('input[value="new"]').checked;
    newFields.hidden = !useNew;
    newFields.querySelectorAll("input").forEach((input) => {
      input.required = useNew;
    });
  }
  savedBox.addEventListener("change", syncMethod);
  if (saved && saved.last4) {
    savedBox.hidden = false;
    document.getElementById("pay-saved-label").textContent =
      saved.brand + " ···· " + saved.last4 + "  exp. " + saved.exp;
    savedBox.querySelector('input[value="saved"]').checked = true;
    syncMethod();
  }

  function showError(msg) {
    const el = document.getElementById("pay-error");
    el.hidden = !msg;
    el.textContent = msg || "";
  }

  function validateNew() {
    const email = document.getElementById("pay-email").value.trim();
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) return "Indiquez un e-mail valide.";
    const digits = number.value.replace(/\D/g, "");
    if (digits.length < 13 || digits.length > 16 || !luhn(digits)) return "Numéro de carte invalide.";
    const exp = document.getElementById("pay-exp").value;
    const m = exp.match(/^(\d{2})\/(\d{2})$/);
    if (!m) return "Expiration au format MM/AA.";
    const month = Number(m[1]);
    const year = 2000 + Number(m[2]);
    const now = new Date();
    if (month < 1 || month > 12) return "Mois d’expiration invalide.";
    if (year < now.getFullYear() || (year === now.getFullYear() && month < now.getMonth() + 1)) {
      return "Cette carte est expirée.";
    }
    const cvc = document.getElementById("pay-cvc").value;
    if (cvc.length < 3) return "CVC incomplet.";
    if (!document.getElementById("pay-holder").value.trim()) return "Nom sur la carte requis.";
    if (!document.getElementById("pay-address").value.trim() || !document.getElementById("pay-city").value.trim()) {
      return "Complétez l’adresse de facturation.";
    }
    if (!document.getElementById("pay-zip").value.trim()) return "Code postal requis.";
    return "";
  }

  function receipt(method) {
    const ttc = totalTtc();
    const id = "HL-" + Date.now().toString().slice(-8);
    const when = new Date().toLocaleString("fr-FR");
    localStorage.setItem("hirelink.premium.active." + plan, "1");
    const esc = (s) =>
      String(s == null ? "" : s)
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;");
    const rows = [
      ["N° de reçu", id],
      ["Date", when],
      ["Offre", conf.title],
      ["Période", cycle === "year" ? "Annuelle" : "Mensuelle"],
      ["Montant TTC", money(ttc)],
      ["Carte", method.brand + " ···· " + method.last4],
      ["Titulaire", method.holder],
      ["Facturation", method.city + ", " + method.country],
      ["E-mail", method.email],
    ];
    document.getElementById("pay-receipt-body").innerHTML = rows
      .map((r) => "<div><dt>" + esc(r[0]) + "</dt><dd>" + esc(r[1]) + "</dd></div>")
      .join("");
    document.getElementById("pay-panel").hidden = true;
    document.getElementById("pay-receipt").hidden = false;
  }

  document.getElementById("pay-demo").addEventListener("click", () => {
    showError("");
    const email = (document.getElementById("pay-email").value || (session && session.email) || "demo@hirelink.ma").trim();
    receipt({
      brand: "Démo",
      last4: "4242",
      holder: (document.getElementById("pay-holder").value || (session && session.fullName) || "Démonstration").trim(),
      email: email,
      city: document.getElementById("pay-city").value.trim() || "Tétouan",
      country: document.getElementById("pay-country").value || "Maroc",
    });
  });

  document.getElementById("pay-form").addEventListener("submit", (e) => {
    e.preventDefault();
    showError("");
    const useSaved = saved && saved.last4 && savedBox.querySelector('input[value="saved"]').checked;
    let method;
    if (useSaved) {
      const email = document.getElementById("pay-email").value.trim();
      if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
        showError("Indiquez un e-mail valide.");
        return;
      }
      method = Object.assign({}, saved, { email });
    } else {
      const err = validateNew();
      if (err) {
        showError(err);
        return;
      }
      const digits = number.value.replace(/\D/g, "");
      method = {
        brand: brandOf(digits),
        last4: digits.slice(-4),
        exp: document.getElementById("pay-exp").value,
        holder: document.getElementById("pay-holder").value.trim(),
        email: document.getElementById("pay-email").value.trim(),
        address: document.getElementById("pay-address").value.trim(),
        city: document.getElementById("pay-city").value.trim(),
        zip: document.getElementById("pay-zip").value.trim(),
        country: document.getElementById("pay-country").value,
      };
      localStorage.setItem(savedKey, JSON.stringify(method));
    }
    const btn = document.getElementById("pay-submit");
    btn.disabled = true;
    btn.textContent = "Paiement en cours…";
    setTimeout(() => receipt(method), 900);
  });

  document.getElementById("pay-change").addEventListener("click", () => {
    document.getElementById("pay-receipt").hidden = true;
    document.getElementById("pay-panel").hidden = false;
    savedBox.hidden = false;
    const again = JSON.parse(localStorage.getItem(savedKey) || "null");
    if (again) {
      document.getElementById("pay-saved-label").textContent =
        again.brand + " ···· " + again.last4 + "  exp. " + again.exp;
      saved = again;
      savedBox.querySelector('input[value="saved"]').checked = true;
      syncMethod();
    }
    const btn = document.getElementById("pay-submit");
    btn.disabled = false;
    paintSummary();
  });

  paintSummary();
})();
