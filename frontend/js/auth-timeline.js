/**
 * Timeline luxe — panneau gauche auth (révélation séquentielle)
 */
(function () {
  const steps = Array.from(document.querySelectorAll(".auth-lux-step"));
  const progress = document.getElementById("auth-lux-progress");
  if (!steps.length) return;

  let i = 0;

  function setProgress(n) {
    if (!progress) return;
    const pct = ((n + 1) / steps.length) * 100;
    progress.style.height = pct + "%";
  }

  function activate(n) {
    steps.forEach((s, si) => {
      s.classList.toggle("is-visible", si <= n);
      s.classList.toggle("is-current", si === n);
    });
    setProgress(n);
  }

  // Entrée en cascade
  steps.forEach((s, si) => {
    setTimeout(() => {
      s.classList.add("is-visible");
      if (si === 0) s.classList.add("is-current");
      setProgress(si);
    }, 220 + si * 320);
  });

  // Pulse / focus qui circule (SaaS pro)
  setTimeout(() => {
    i = 0;
    setInterval(() => {
      i = (i + 1) % steps.length;
      activate(i);
    }, 2800);
  }, 220 + steps.length * 320 + 400);
})();
