(function () {
  const session = HireLinkAPI.requireAuth(["ADMINISTRATEUR"]);
  if (!session) return;

  document.getElementById("side-user").textContent = session.email;
  document.getElementById("avatar").textContent = (session.fullName || "A").slice(0, 1).toUpperCase();

  async function load() {
    try {
      const stats = await HireLinkAPI.api("/admin/stats");
      const rows = [
        ["Utilisateurs", stats.users],
        ["Candidats", stats.candidates],
        ["Recruteurs", stats.recruiters],
        ["Offres", stats.offers],
        ["Publiées", stats.publishedOffers],
        ["Candidatures", stats.applications],
      ];
      const root = document.getElementById("stats");
      root.innerHTML = rows
        .map(
          ([label, value]) =>
            '<div class="stat"><div class="label">' +
            label +
            '</div><div class="value">' +
            value +
            "</div></div>"
        )
        .join("");

      const max = Math.max(1, ...rows.map((r) => Number(r[1]) || 0));
      const chart = document.getElementById("chart-bars");
      if (chart) {
        chart.innerHTML = rows
          .map((r, i) => {
            const h = Math.round(((Number(r[1]) || 0) / max) * 100);
            return (
              '<div class="chart-bar"><span style="--h:' +
              Math.max(8, h) +
              "%;--i:" +
              i +
              '"></span><small>' +
              r[0] +
              "</small></div>"
            );
          })
          .join("");
      }

      const users = await HireLinkAPI.api("/admin/users");
      const body = document.getElementById("users-body");
      body.innerHTML = "";
      users.forEach((u) => {
        const tr = document.createElement("tr");
        tr.innerHTML =
          "<td></td><td></td><td><span class='badge badge-chrome'></span></td>" +
          "<td></td><td class='row wrap'></td>";
        tr.children[0].textContent = u.fullName;
        tr.children[1].textContent = u.email;
        tr.querySelector(".badge").textContent = u.role;
        tr.children[3].textContent = u.active ? "Oui" : "Non";
        const actions = tr.children[4];
        const tgl = document.createElement("button");
        tgl.type = "button";
        tgl.className = "btn btn-sm btn-ghost";
        tgl.textContent = "Actif";
        tgl.addEventListener("click", async () => {
          await HireLinkAPI.api("/admin/users/" + u.id + "/active", {
            method: "PATCH",
            body: { active: !u.active },
          });
          HireLink.toast("Mis à jour", u.email, "ok");
          load();
        });
        actions.appendChild(tgl);
        if (u.role === "RECRUTEUR") {
          const ver = document.createElement("button");
          ver.type = "button";
          ver.className = "btn btn-sm btn-soft";
          ver.textContent = "Valider entreprise";
          ver.addEventListener("click", async () => {
            await HireLinkAPI.api("/admin/users/" + u.id + "/verify-company", { method: "PATCH" });
            HireLink.toast("Entreprise validée", u.email, "ok");
            load();
          });
          actions.appendChild(ver);
        }
        body.appendChild(tr);
      });
    } catch (err) {
      HireLink.toast("Admin", err.message, "danger");
    }
  }

  document.getElementById("btn-logout").addEventListener("click", () => {
    HireLinkAPI.clearAuth();
    location.href = "../auth.html";
  });

  if (window.HireLinkGrowth) {
    HireLinkGrowth.scheduleNudge("recruiter", 45000);
  }

  load();
})();
