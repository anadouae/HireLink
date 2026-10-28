(function () {
  const s = HireLinkAPI.getSession();
  if (!s || !HireLinkAPI.getToken()) { location.href = "auth.html?mode=login"; return; }
  document.getElementById("form").addEventListener("submit", async (e) => {
    e.preventDefault();
    const p1 = document.getElementById("password").value;
    const p2 = document.getElementById("password2").value;
    if (p1 !== p2) { HireLink.toast("Erreur", "Confirmation différente", "warn"); return; }
    try {
      const auth = await HireLinkAPI.api("/auth/set-password", { method: "POST", body: { password: p1 } });
      HireLinkAPI.setAuth(auth);
      localStorage.removeItem("hirelink.pw.skip");
      HireLink.toast("OK", "Mot de passe enregistré", "ok");
      setTimeout(() => (location.href = HireLinkAPI.redirectForRole(auth.role)), 350);
    } catch (err) { HireLink.toast("Échec", err.message, "danger"); }
  });
  document.getElementById("btn-skip").addEventListener("click", () => {
    localStorage.setItem("hirelink.pw.skip", "1");
    location.href = HireLinkAPI.redirectForRole(s.role);
  });
  document.getElementById("btn-back").addEventListener("click", () => {
    location.href = HireLinkAPI.redirectForRole(s.role);
  });
})();
