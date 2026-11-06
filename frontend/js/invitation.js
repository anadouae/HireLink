(function () {
  const info = document.getElementById("invitation-info");
  const form = document.getElementById("invitation-form");
  const token = new URLSearchParams(location.search).get("token") || "";
  const endpoint = HireLinkAPI.API_BASE + "/organizations/invitations/" + encodeURIComponent(token);
  if (!token) {
    info.textContent = "Le lien d’invitation est incomplet.";
    return;
  }
  fetch(endpoint).then(async (response) => {
    const body = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(body.message || "Invitation invalide ou expirée.");
    info.textContent = "Vous êtes invité(e) à rejoindre « " + body.organization + " » en tant que " +
      (body.roleLabel || body.orgRole) + ". Acceptez, puis choisissez votre mot de passe personnel.";
    form.hidden = false;
  }).catch((error) => { info.textContent = error.message; });
  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    const btn = form.querySelector('button[type="submit"]');
    // Un double « Accepter » consomme deux fois le même jeton : le serveur marque l’invitation
    // acceptée seulement à la fin, donc les deux requêtes concurrentes ajoutent chacune leur ligne au journal.
    if (btn.disabled) return;
    const password = document.getElementById("invitation-password").value;
    const confirmation = document.getElementById("invitation-password-confirm").value;
    if (password !== confirmation) {
      HireLink.toast("Erreur", "Les deux mots de passe sont différents.", "warn");
      return;
    }
    btn.disabled = true;
    try {
      const response = await fetch(endpoint + "/accept", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ password })
      });
      const body = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(body.message || "Impossible d’accepter l’invitation.");
      HireLink.toast("Invitation acceptée", "Vous pouvez maintenant vous connecter.", "ok");
      setTimeout(() => { location.href = "auth.html?mode=login"; }, 500);
    } catch (error) {
      btn.disabled = false;
      HireLink.toast("Échec", error.message, "danger");
    }
  });
})();
