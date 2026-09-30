/**
 * Scroll reveal + timeline luxe progress
 */
(function () {
  const els = document.querySelectorAll(".reveal, .reveal-step");
  if (els.length) {
    const io = new IntersectionObserver(
      (entries) => {
        entries.forEach((e) => {
          if (!e.isIntersecting) return;
          e.target.classList.add("is-visible");
          io.unobserve(e.target);
          updateTimelineProgress();
        });
      },
      { threshold: 0.18, rootMargin: "0px 0px -48px 0px" }
    );
    els.forEach((el) => io.observe(el));
  }

  const steps = Array.from(document.querySelectorAll(".lux-step"));
  const progress = document.querySelector(".lux-timeline-progress");

  function updateTimelineProgress() {
    if (!progress || !steps.length) return;
    const visible = steps.filter((s) => s.classList.contains("is-visible")).length;
    const pct = Math.max(8, (visible / steps.length) * 100);
    progress.style.height = pct + "%";
  }

  window.addEventListener(
    "scroll",
    () => {
      if (!progress || !steps.length) return;
      const first = steps[0];
      const last = steps[steps.length - 1];
      const start = first.getBoundingClientRect().top + window.scrollY - window.innerHeight * 0.35;
      const end = last.getBoundingClientRect().top + window.scrollY - window.innerHeight * 0.25;
      const y = window.scrollY;
      let t = (y - start) / Math.max(1, end - start);
      t = Math.min(1, Math.max(0, t));
      const visibleBoost = steps.filter((s) => s.classList.contains("is-visible")).length / steps.length;
      progress.style.height = Math.max(t, visibleBoost) * 100 + "%";
    },
    { passive: true }
  );
})();
