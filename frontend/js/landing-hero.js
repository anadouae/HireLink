/**
 * Une seule vidéo de fond. Lecture immédiate, sans carrousel.
 */
(function () {
  const video = document.getElementById("hero-video");
  if (!video) return;
  video.loop = true;
  const play = () => video.play().catch(() => {});
  if (video.readyState >= 2) play();
  else video.addEventListener("canplay", play, { once: true });
  document.addEventListener("visibilitychange", () => {
    if (!document.hidden) play();
  });
})();
