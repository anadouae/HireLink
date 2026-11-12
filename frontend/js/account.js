(function () {
  const $ = (id) => document.getElementById(id);
  let originalEmail = "";
  let photoGen = 0;

  function toast(title, text, tone) {
    if (window.HireLink) HireLink.toast(title, text, tone || "ok");
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
    if (window.App && App.paintProfile) App.paintProfile();
  }

  async function load() {
    const p = await HireLinkAPI.api("/account/profile");
    $("acc-first").value = p.firstName || "";
    $("acc-last").value = p.lastName || "";
    $("acc-email").value = p.email || "";
    originalEmail = p.email || "";
    $("acc-fallback").textContent = ((p.firstName || "A").charAt(0) + (p.lastName || "")).slice(0, 2);
    paintPhoto();
    const rows = await HireLinkAPI.api("/account/logins");
    $("acc-logins").innerHTML = rows.length
      ? rows.map((r) => "<tr><td>" + r.at + "</td><td>" + r.result + "</td><td>" + (r.ip || "—") + "</td></tr>").join("")
      : '<tr><td colspan="3">Aucune connexion enregistrée pour ce compte.</td></tr>';
  }

  $("acc-email").addEventListener("input", () => {
    $("acc-pw-wrap").hidden = $("acc-email").value.trim().toLowerCase() === originalEmail.toLowerCase();
  });
  $("account-form").addEventListener("submit", async (e) => {
    e.preventDefault();
    try {
      const p = await HireLinkAPI.api("/account/profile", { method: "PUT", body: {
        firstName: $("acc-first").value, lastName: $("acc-last").value, email: $("acc-email").value, currentPassword: $("acc-pw").value
      }});
      originalEmail = p.email;
      const s = HireLinkAPI.getSession();
      if (s) { s.fullName = p.fullName; s.email = p.email; localStorage.setItem("hirelink.session", JSON.stringify(s)); }
      toast("Compte", "Profil enregistré");
      load();
    } catch (err) { toast("Compte", err.message, "danger"); }
  });
  $("acc-pick").onclick = () => $("acc-file").click();
  $("acc-file").onchange = async (e) => {
    const file = e.target.files && e.target.files[0];
    if (!file) return;
    const body = new FormData();
    body.append("file", file);
    const res = await HireLinkAPI.authFetch("/account/avatar", { method: "POST", body });
    if (!res.ok) {
      let msg = "Image refusée";
      try { msg = (await res.json()).message || msg; } catch (_) {}
      toast("Photo", msg, "danger");
    } else {
      paintPhoto();
      toast("Photo", "Image enregistrée");
    }
    e.target.value = "";
  };
  $("acc-clear").onclick = async () => {
    await HireLinkAPI.api("/account/avatar", { method: "DELETE" });
    paintPhoto();
    toast("Photo", "Image supprimée");
  };
  load().catch((err) => toast("Compte", err.message, "danger"));
})();
