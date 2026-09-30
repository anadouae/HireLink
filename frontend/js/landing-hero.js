/**
 * Hero video carousel — rotation plus rapide + fondu court
 */
(function () {
  const SOURCES = [
    "assets/videos/hero-3.mp4",
    "assets/videos/hero-1.mp4",
    "assets/videos/hero-2.mp4",
  ];
  const CLIP_MS = 6500;
  const FADE_MS = 140;
  const video = document.getElementById("hero-video");
  const dotsRoot = document.getElementById("video-dots");
  if (!video || !dotsRoot) return;

  let index = 0;
  let timer = null;

  const dots = SOURCES.map((_, i) => {
    const b = document.createElement("button");
    b.type = "button";
    b.className = "video-dot" + (i === 0 ? " is-active" : "");
    b.setAttribute("role", "tab");
    b.setAttribute("aria-selected", i === 0 ? "true" : "false");
    b.setAttribute("aria-label", "Séquence " + (i + 1));
    b.addEventListener("click", () => playAt(i));
    dotsRoot.appendChild(b);
    return b;
  });

  function setActiveDot(i) {
    dots.forEach((d, di) => {
      d.classList.toggle("is-active", di === i);
      d.setAttribute("aria-selected", di === i ? "true" : "false");
    });
  }

  function scheduleNext() {
    clearTimeout(timer);
    timer = setTimeout(() => playAt((index + 1) % SOURCES.length), CLIP_MS);
  }

  function playAt(i) {
    index = i;
    setActiveDot(i);
    video.classList.add("is-fading");
    setTimeout(() => {
      video.src = SOURCES[i];
      video.play().catch(() => {});
      video.classList.remove("is-fading");
      scheduleNext();
    }, FADE_MS);
  }

  video.addEventListener("ended", () => {
    playAt((index + 1) % SOURCES.length);
  });

  document.addEventListener("visibilitychange", () => {
    if (document.hidden) clearTimeout(timer);
    else scheduleNext();
  });

  playAt(0);
})();
