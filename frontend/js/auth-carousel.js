(function () {
  const slides = Array.from(document.querySelectorAll(".auth-slide"));
  const dotsRoot = document.getElementById("carousel-dots");
  if (!slides.length || !dotsRoot) return;
  let i = 0;
  const dots = slides.map((_, idx) => {
    const b = document.createElement("button");
    b.type = "button";
    b.setAttribute("aria-label", "Slide " + (idx + 1));
    b.addEventListener("click", () => go(idx));
    dotsRoot.appendChild(b);
    return b;
  });
  function go(n) {
    i = n;
    slides.forEach((s, si) => s.classList.toggle("is-active", si === i));
    dots.forEach((d, di) => d.classList.toggle("is-active", di === i));
  }
  go(0);
  setInterval(() => go((i + 1) % slides.length), 4200);
})();
