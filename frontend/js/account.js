/* Mon compte (administration) : identité, photo et dernières connexions. */
(function () {
  const App = window.HireLinkApp;
  // Sans mount, la page reste masquée par le garde d'accès et s'affiche sans menu : c'était la cause du « blanc ».
  const session = App && App.mount ? App.mount({ active: "account", title: "Mon compte" }) : null;
  if (!session) return;
  const $ = (id) => document.getElementById(id);
  const esc = App.esc;
  let originalEmail = "";
  let photoGen = 0;

  function toast(title, text, tone) {
    App.toast(title, text, tone || "ok");
  }

  async function paintPhoto() {
    const gen = ++photoGen;
    const img = $("acc-photo");
    const fb = $("acc-fallback");
    try {
      const res = await HireLinkAPI.authFetch("/account/avatar?t=" + Date.now());
      if (gen !== photoGen) return;
      if (!res.ok || res.status === 204) throw new Error("none");
      img.src = URL.createObjectURL(await res.blob());
      img.hidden = false;
      fb.hidden = true;
    } catch (_) {
      if (gen !== photoGen) return;
      img.hidden = true;
      fb.hidden = false;
    }
    if (App.paintProfile) App.paintProfile();
  }

  function paintLogins(rows) {
    const body = $("acc-logins");
    body.innerHTML = rows.length
      ? rows.map((r) => {
          const ok = r.action === "LOGIN_SUCCESS";
          return "<tr><td>" + esc(App.fmtDateTime(r.at)) + '</td><td><span class="rc-pill ' + (ok ? "rc-pill--ok" : "rc-pill--danger") + '">' +
            (ok ? "Connexion réussie" : "Connexion refusée") + "</span></td><td>" + esc(r.ip || "—") + "</td></tr>";
        }).join("")
      : '<tr><td colspan="3" class="adm-empty-cell">Aucune connexion enregistrée pour ce compte.</td></tr>';
  }

  async function load() {
    const [p, rows] = await Promise.all([
      HireLinkAPI.api("/account/profile"),
      HireLinkAPI.api("/account/logins").catch(() => []),
    ]);
    $("acc-first").value = p.firstName || "";
    $("acc-last").value = p.lastName || "";
    $("acc-email").value = p.email || "";
    $("acc-pw-wrap").hidden = true;
    $("acc-pw").value = "";
    originalEmail = p.email || "";
    $("acc-fallback").textContent = App.initials ? App.initials(p.fullName || ((p.firstName || "") + " " + (p.lastName || ""))) : (p.firstName || "A").charAt(0);
    paintPhoto();
    paintLogins(rows || []);
  }

  $("acc-email").addEventListener("input", () => {
    $("acc-pw-wrap").hidden = $("acc-email").value.trim().toLowerCase() === originalEmail.toLowerCase();
  });
  $("account-form").addEventListener("submit", async (e) => {
    e.preventDefault();
    const btn = $("acc-save");
    btn.disabled = true;
    try {
      const p = await HireLinkAPI.api("/account/profile", { method: "PUT", body: {
        firstName: $("acc-first").value, lastName: $("acc-last").value, email: $("acc-email").value, currentPassword: $("acc-pw").value,
      } });
      originalEmail = p.email;
      const s = HireLinkAPI.getSession();
      if (s) { s.fullName = p.fullName; s.email = p.email; localStorage.setItem("hirelink.session", JSON.stringify(s)); }
      toast("Compte", "Profil enregistré.");
      await load();
    } catch (err) { toast("Compte", err.message, "danger"); }
    btn.disabled = false;
  });
  $("acc-pick").onclick = () => $("acc-file").click();
  $("acc-file").onchange = async (e) => {
    const file = e.target.files && e.target.files[0];
    if (!file) return;
    if (file.size > 1.5 * 1024 * 1024) {
      toast("Photo", "Image trop lourde : 1,5 Mo maximum.", "danger");
      e.target.value = "";
      return;
    }
    const body = new FormData();
    body.append("file", file);
    try {
      const res = await HireLinkAPI.authFetch("/account/avatar", { method: "POST", body });
      if (!res.ok) {
        let msg = "Image refusée.";
        try { msg = (await res.json()).message || msg; } catch (_) {}
        toast("Photo", msg, "danger");
      } else {
        paintPhoto();
        toast("Photo", "Image enregistrée.");
      }
    } catch (err) { toast("Photo", err.message || "Envoi impossible.", "danger"); }
    e.target.value = "";
  };
  $("acc-clear").onclick = async () => {
    const ok = await Mascotte.confirm({ title: "Supprimer la photo ?", description: "Vos initiales s’afficheront à la place.", confirmLabel: "Supprimer", danger: true });
    if (!ok) return;
    try {
      await HireLinkAPI.api("/account/avatar", { method: "DELETE" });
      paintPhoto();
      toast("Photo", "Image supprimée.");
    } catch (err) { toast("Photo", err.message, "danger"); }
  };
  load().catch((err) => toast("Compte", err.message, "danger"));
})();
