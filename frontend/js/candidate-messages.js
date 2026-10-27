(function () {
  const session = HireLinkShell.mountShell({ roles: ["CANDIDAT"], hello: false });
  if (!session) return;
  const box = document.getElementById("message-list");
  function esc(s) {
    return String(s == null ? "" : s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
  }
  HireLinkAPI.api("/notifications")
    .then((list) => {
      if (!list || !list.length) {
        box.innerHTML = (window.HireLinkArt ? HireLinkArt.sceneHtml("empty") : "") + "<p class='muted'>Aucun message pour le moment.</p>";
        return;
      }
      box.innerHTML = list
        .map((n) => {
          const when = n.at ? new Date(n.at).toLocaleString("fr-FR") : "";
          const face = window.HireLinkArt ? HireLinkArt.avatarHtml(n.title || n.id, "msg-avatar", HireLinkArt.moodOf((n.title || "") + " " + (n.body || ""))) : "";
          return (
            "<article class='notif-row' data-id='" +
            n.id +
            "'>" +
            face +
            "<div><strong>" +
            esc(n.title) +
            "</strong><p>" +
            esc(n.body) +
            "</p><span>" +
            when +
            (n.read ? "" : " · Non lu") +
            "</span></div></article>"
          );
        })
        .join("");
      box.querySelectorAll(".notif-row").forEach((row) => {
        row.addEventListener("click", async () => {
          const id = row.getAttribute("data-id");
          try {
            await HireLinkAPI.api("/notifications/" + id + "/read", { method: "POST" });
          } catch (_) {}
          const span = row.querySelector("span");
          if (span) span.textContent = span.textContent.replace(" · Non lu", "");
        });
      });
    })
    .catch((err) => {
      box.innerHTML = "<p class='muted'>" + err.message + "</p>";
    });
})();
