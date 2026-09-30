/**
 * Synaptic network — burgundy / orange / blanc, connexions en mouvement
 */
(function () {
  const canvas = document.getElementById("synapse-bg");
  if (!canvas) return;
  const ctx = canvas.getContext("2d");

  /* Burgundy · Orange · Blanc · Noir doux */
  const COLORS = [
    [122, 32, 50],
    [232, 112, 48],
    [255, 248, 240],
    [26, 18, 20],
  ];

  const LINK_DIST = 168;
  const SPEED = 0.72;

  let w = 0;
  let h = 0;
  let nodes = [];
  let t = 0;
  let raf = 0;
  let running = false;
  let inView = true;
  let resizeTimer = null;
  let mouse = { x: -9999, y: -9999, active: false };

  function resize() {
    w = canvas.width = window.innerWidth;
    h = canvas.height = window.innerHeight;
    const count = Math.min(72, Math.floor((w * h) / 16000));
    nodes = Array.from({ length: count }, () => ({
      x: Math.random() * w,
      y: Math.random() * h,
      vx: (Math.random() - 0.5) * SPEED,
      vy: (Math.random() - 0.5) * SPEED,
      c: Math.floor(Math.random() * COLORS.length),
      r: 1.6 + Math.random() * 1.8,
      pulse: Math.random() * Math.PI * 2,
    }));
  }

  function mix(a, b, u) {
    return a.map((v, i) => Math.round(v + (b[i] - v) * u));
  }

  function shouldRun() {
    return inView && !document.hidden;
  }

  function frame() {
    if (!shouldRun()) {
      running = false;
      raf = 0;
      return;
    }
    t += 0.012;
    ctx.clearRect(0, 0, w, h);

    const phase = (Math.sin(t * 0.55) + 1) / 2;
    const i0 = Math.floor(t * 0.35) % COLORS.length;
    const i1 = (i0 + 1) % COLORS.length;
    const base = mix(COLORS[i0], COLORS[i1], phase);

    nodes.forEach((n) => {
      n.pulse += 0.04;
      n.x += n.vx;
      n.y += n.vy;
      /* dérive douce vers le curseur dans la zone hero */
      if (mouse.active) {
        const mdx = mouse.x - n.x;
        const mdy = mouse.y - n.y;
        const md = Math.hypot(mdx, mdy);
        if (md < 220 && md > 1) {
          n.vx += (mdx / md) * 0.012;
          n.vy += (mdy / md) * 0.012;
        }
      }
      const sp = Math.hypot(n.vx, n.vy);
      if (sp > SPEED * 1.6) {
        n.vx *= 0.96;
        n.vy *= 0.96;
      }
      if (n.x < 0 || n.x > w) n.vx *= -1;
      if (n.y < 0 || n.y > h) n.vy *= -1;
      n.x = Math.max(0, Math.min(w, n.x));
      n.y = Math.max(0, Math.min(h, n.y));
    });

    for (let i = 0; i < nodes.length; i++) {
      for (let j = i + 1; j < nodes.length; j++) {
        const a = nodes[i];
        const b = nodes[j];
        const dx = a.x - b.x;
        const dy = a.y - b.y;
        const d = Math.hypot(dx, dy);
        if (d < LINK_DIST) {
          const travel = (Math.sin(t * 2.2 + i * 0.35 + j * 0.17) + 1) / 2;
          const alpha = (1 - d / LINK_DIST) * (0.12 + travel * 0.28);
          const col = mix(COLORS[a.c], COLORS[b.c], 0.35 + travel * 0.3);
          const merged = mix(col, base, 0.25);
          ctx.strokeStyle =
            "rgba(" + merged[0] + "," + merged[1] + "," + merged[2] + "," + alpha + ")";
          ctx.lineWidth = 0.8 + travel * 1.4;
          ctx.beginPath();
          ctx.moveTo(a.x, a.y);
          ctx.lineTo(b.x, b.y);
          ctx.stroke();

          /* point qui voyage sur la connexion */
          if (travel > 0.55 && alpha > 0.12) {
            const u = (Math.sin(t * 3 + i + j) + 1) / 2;
            const px = a.x + (b.x - a.x) * u;
            const py = a.y + (b.y - a.y) * u;
            ctx.fillStyle =
              "rgba(" + merged[0] + "," + merged[1] + "," + merged[2] + "," + alpha * 1.4 + ")";
            ctx.beginPath();
            ctx.arc(px, py, 1.4, 0, Math.PI * 2);
            ctx.fill();
          }
        }
      }
    }

    nodes.forEach((n) => {
      const beat = 0.7 + Math.sin(n.pulse) * 0.3;
      const col = mix(COLORS[n.c], base, 0.4);
      ctx.fillStyle =
        "rgba(" + col[0] + "," + col[1] + "," + col[2] + "," + 0.35 + beat * 0.35 + ")";
      ctx.beginPath();
      ctx.arc(n.x, n.y, n.r * beat, 0, Math.PI * 2);
      ctx.fill();
    });

    raf = requestAnimationFrame(frame);
  }

  function start() {
    if (running || !shouldRun()) return;
    running = true;
    raf = requestAnimationFrame(frame);
  }

  function stop() {
    running = false;
    if (raf) cancelAnimationFrame(raf);
    raf = 0;
  }

  window.addEventListener("resize", () => {
    clearTimeout(resizeTimer);
    resizeTimer = setTimeout(resize, 180);
  });

  document.addEventListener("visibilitychange", () => {
    if (document.hidden) stop();
    else start();
  });

  window.addEventListener(
    "pointermove",
    (e) => {
      mouse.x = e.clientX;
      mouse.y = e.clientY;
      mouse.active = true;
    },
    { passive: true }
  );
  window.addEventListener(
    "pointerleave",
    () => {
      mouse.active = false;
    },
    { passive: true }
  );

  if ("IntersectionObserver" in window) {
    const io = new IntersectionObserver(
      (entries) => {
        inView = entries.some((e) => e.isIntersecting);
        if (inView) start();
        else stop();
      },
      { threshold: 0.05 }
    );
    io.observe(canvas);
  }

  resize();
  start();
})();
