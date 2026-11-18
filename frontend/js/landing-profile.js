/**
 * Page d’accueil — section « Profil et CV ».
 * À l’entrée dans l’écran : le CV fictif est lu, les champs de la carte de profil
 * se remplissent un à un (la barre de complétion suit), les compétences arrivent
 * en badges, puis chaque proposition est validée. Avec prefers-reduced-motion,
 * ou sans IntersectionObserver, l’état final reste affiché tel quel.
 */
(function () {
  const demo = document.querySelector("[data-pcv]");
  if (!demo) return;
  const reduced = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  if (reduced || !("IntersectionObserver" in window)) return;

  const section = demo.closest("section");
  const host = section; // les états animés sont posés sur la section (étapes + démonstration)
  const steps = Array.from(section.querySelectorAll("[data-pcv-step]"));
  const fields = Array.from(demo.querySelectorAll("[data-pcv-field]"));
  const percent = demo.querySelector("[data-pcv-percent]");
  const sub = demo.querySelector("[data-pcv-sub]");
  const replay = section.querySelector("[data-pcv-replay]");
  const subFinal = sub ? sub.textContent : "";
  const total = fields.reduce(function (sum, f) { return sum + Number(f.getAttribute("data-weight") || 0); }, 0) || 1;
  let timers = [];
  let shown = 0;
  let playing = false;

  demo.querySelectorAll(".pcv-chip").forEach(function (chip, i) { chip.style.setProperty("--c", i); });

  function later(ms, fn) { timers.push(setTimeout(fn, ms)); }

  function setStep(index) {
    steps.forEach(function (step, i) {
      step.classList.toggle("is-active", i === index);
      step.classList.toggle("is-done", i < index);
    });
  }

  function setProgress(value) {
    host.style.setProperty("--pcv-p", String(value / 100));
    const from = shown;
    const start = performance.now();
    (function tick(now) {
      const t = Math.min(1, (now - start) / 600);
      shown = Math.round(from + (value - from) * (1 - Math.pow(1 - t, 3)));
      if (percent) percent.textContent = shown + " %";
      if (t < 1) requestAnimationFrame(tick);
    })(start);
  }

  function reset() {
    timers.forEach(clearTimeout);
    timers = [];
    shown = 0;
    host.classList.remove("is-started", "is-scanning", "is-flowing", "is-done");
    fields.forEach(function (f) { f.classList.remove("is-filled", "is-valid"); });
    host.style.setProperty("--pcv-p", "0");
    if (percent) percent.textContent = "0 %";
    if (sub) sub.textContent = "CV en cours de lecture…";
    setStep(-1);
  }

  function play() {
    reset();
    playing = true;
    if (replay) replay.hidden = true;
    let t = 80;
    later(t, function () { host.classList.add("is-started"); setStep(0); });
    t += 700;
    later(t, function () { host.classList.add("is-scanning", "is-flowing"); setStep(1); });
    t += 600;
    let filled = 0;
    fields.forEach(function (field) {
      later(t, function () {
        field.classList.add("is-filled");
        filled += Number(field.getAttribute("data-weight") || 0);
        setProgress(Math.round((filled / total) * 100));
      });
      t += 560;
    });
    t += 300;
    later(t, function () { host.classList.remove("is-flowing"); setStep(2); if (sub) sub.textContent = "Vérification de chaque proposition…"; });
    t += 350;
    fields.forEach(function (field) {
      later(t, function () { field.classList.add("is-valid"); });
      t += 320;
    });
    t += 200;
    later(t, function () {
      host.classList.remove("is-scanning");
      host.classList.add("is-done");
      if (sub) sub.textContent = subFinal;
      setStep(3);
      playing = false;
      if (replay) replay.hidden = false;
    });
  }

  host.classList.add("pcv-anim");
  reset();
  if (replay) {
    replay.hidden = true;
    replay.addEventListener("click", function () { if (!playing) play(); });
  }

  const io = new IntersectionObserver(function (entries) {
    entries.forEach(function (entry) {
      if (!entry.isIntersecting) return;
      io.disconnect();
      play();
    });
  }, { threshold: 0.45 });
  io.observe(demo);
})();
