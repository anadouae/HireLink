/** Page Support : formulaire de contact, prérempli si une session existe. */
(function () {
  var form = document.getElementById("support-form");
  if (!form || !window.HireLinkAPI) return;
  var $ = function (id) { return document.getElementById(id); };
  var params = new URLSearchParams(location.search);
  var TOPICS = ["QUESTION", "COMPTE", "ENTREPRISE", "AUTRE"];

  var session = HireLinkAPI.getSession ? HireLinkAPI.getSession() : null;
  if (session) {
    if (session.fullName) $("sp-name").value = String(session.fullName).replace(/\s*\(alias\)\s*/i, "");
    if (session.email) $("sp-email").value = session.email;
    if (session.role === "RECRUTEUR") $("sp-topic").value = "ENTREPRISE";
  }
  var topic = (params.get("topic") || "").toUpperCase();
  if (TOPICS.indexOf(topic) >= 0) $("sp-topic").value = topic;
  if (params.get("ref")) $("sp-ref").value = params.get("ref").slice(0, 200);
  if (params.get("subject")) $("sp-subject").value = params.get("subject").slice(0, 160);

  function syncTopic() {
  }
  $("sp-topic").addEventListener("change", syncTopic);
  syncTopic();

  function count() { $("sp-count").textContent = $("sp-message").value.length + " / 4000"; }
  $("sp-message").addEventListener("input", count);
  count();

  function showError(msg) {
    var e = $("sp-error");
    e.textContent = msg;
    e.hidden = !msg;
  }

  form.addEventListener("submit", async function (ev) {
    ev.preventDefault();
    showError("");
    var body = {
      topic: $("sp-topic").value,
      name: $("sp-name").value.trim(),
      email: $("sp-email").value.trim(),
      subject: $("sp-subject").value.trim(),
      message: $("sp-message").value.trim(),
      reference: $("sp-ref").value.trim(),
      website: $("sp-website").value,
    };
    if (!body.name) { showError("Indiquez votre nom."); $("sp-name").focus(); return; }
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(body.email)) { showError("Indiquez une adresse e-mail valide."); $("sp-email").focus(); return; }
    if (body.subject.length < 3) { showError("Précisez l’objet de votre demande."); $("sp-subject").focus(); return; }
    if (body.message.length < 10) { showError("Votre message est trop court : décrivez la situation en quelques mots."); $("sp-message").focus(); return; }
    var btn = $("sp-send");
    btn.disabled = true;
    btn.textContent = "Envoi…";
    try {
      var r = await HireLinkAPI.api("/support", { method: "POST", body: body });
      form.hidden = true;
      $("sp-done-text").textContent = "Merci. Votre demande est bien reçue" + (r && r.id ? " (n° " + r.id + ")" : "") + ". Nous vous répondons à " + body.email + ".";
      $("support-done").hidden = false;
      $("support-done").focus();
    } catch (err) {
      showError((err && err.message) || "L’envoi n’a pas abouti. Réessayez dans un instant.");
    } finally {
      btn.disabled = false;
      btn.textContent = "Envoyer ma demande";
    }
  });

  $("sp-again").addEventListener("click", function () {
    $("sp-subject").value = "";
    $("sp-message").value = "";
    $("sp-ref").value = "";
    count();
    syncTopic();
    $("support-done").hidden = true;
    form.hidden = false;
    $("sp-subject").focus();
  });
})();
