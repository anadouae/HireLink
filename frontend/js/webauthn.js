/**
 * Passkeys (WebAuthn) côté navigateur : conversion base64url ⇄ ArrayBuffer et appels navigator.credentials.
 * Le serveur (Yubico java-webauthn-server) fournit les options JSON et vérifie les réponses.
 */
(function (global) {
  function b64uToBuf(s) {
    s = String(s).replace(/-/g, "+").replace(/_/g, "/");
    while (s.length % 4) s += "=";
    var bin = atob(s), buf = new Uint8Array(bin.length);
    for (var i = 0; i < bin.length; i++) buf[i] = bin.charCodeAt(i);
    return buf.buffer;
  }
  function bufToB64u(buf) {
    var b = new Uint8Array(buf), s = "";
    for (var i = 0; i < b.length; i++) s += String.fromCharCode(b[i]);
    return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
  }
  function supported() {
    return !!(global.PublicKeyCredential && navigator.credentials && navigator.credentials.create);
  }
  function parseOptions(res) {
    var pk = typeof res.publicKey === "string" ? JSON.parse(res.publicKey) : res.publicKey;
    return pk.publicKey || pk;
  }
  function friendly(err) {
    var n = err && err.name;
    if (n === "NotAllowedError") return new Error("Opération annulée ou expirée. Réessayez.");
    if (n === "InvalidStateError") return new Error("Cette passkey est déjà enregistrée sur cet appareil.");
    if (n === "SecurityError") return new Error("Les passkeys exigent l’adresse http://localhost:5500 (ou HTTPS).");
    return err instanceof Error ? err : new Error("Passkey indisponible");
  }
  /** Crée une passkey ; res = réponse de POST /account/passkeys/options. Retourne le JSON à renvoyer au serveur. */
  async function create(res) {
    var o = parseOptions(res);
    o.challenge = b64uToBuf(o.challenge);
    o.user.id = b64uToBuf(o.user.id);
    (o.excludeCredentials || []).forEach(function (c) { c.id = b64uToBuf(c.id); });
    var cred;
    try { cred = await navigator.credentials.create({ publicKey: o }); } catch (e) { throw friendly(e); }
    return {
      id: cred.id, rawId: bufToB64u(cred.rawId), type: cred.type,
      response: {
        clientDataJSON: bufToB64u(cred.response.clientDataJSON),
        attestationObject: bufToB64u(cred.response.attestationObject),
        transports: cred.response.getTransports ? cred.response.getTransports() : [],
      },
      clientExtensionResults: cred.getClientExtensionResults ? cred.getClientExtensionResults() : {},
    };
  }
  /** Se connecte avec une passkey ; res = réponse de POST /auth/passkey/login/options. */
  async function get(res) {
    var o = parseOptions(res);
    o.challenge = b64uToBuf(o.challenge);
    (o.allowCredentials || []).forEach(function (c) { c.id = b64uToBuf(c.id); });
    var cred;
    try { cred = await navigator.credentials.get({ publicKey: o }); } catch (e) { throw friendly(e); }
    return {
      id: cred.id, rawId: bufToB64u(cred.rawId), type: cred.type,
      response: {
        clientDataJSON: bufToB64u(cred.response.clientDataJSON),
        authenticatorData: bufToB64u(cred.response.authenticatorData),
        signature: bufToB64u(cred.response.signature),
        userHandle: cred.response.userHandle ? bufToB64u(cred.response.userHandle) : null,
      },
      clientExtensionResults: cred.getClientExtensionResults ? cred.getClientExtensionResults() : {},
    };
  }
  global.HireLinkWebAuthn = { supported: supported, create: create, get: get };
})(window);
