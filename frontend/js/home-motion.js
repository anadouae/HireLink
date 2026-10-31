/**
 * Page d’accueil — défilement inertiel, scènes épinglées, révélations et compteurs.
 * Sans mouvement (prefers-reduced-motion, bibliothèques absentes) la page reste
 * statique et lisible ; seuls les liens d’ancre et le carrousel d’entreprises sont câblés.
 */
(function () {
  const reduced = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  const motion = !reduced && window.gsap && window.ScrollTrigger && window.Lenis;
  const progressBar = document.querySelector(".scroll-progress");
  let lenis = null;

  /* ---------- Navigation : ancres, défilement programmé, barre de progression ---------- */

  function scrollToY(y) {
    if (lenis) lenis.scrollTo(y, { duration: 1.4 });
    else window.scrollTo({ top: y, behavior: reduced ? "auto" : "smooth" });
  }

  function scrollToElement(el) {
    scrollToY(el.getBoundingClientRect().top + window.scrollY);
  }

  document.addEventListener("click", function (e) {
    const link = e.target.closest("a[href^='#']");
    if (!link || e.defaultPrevented || link.getAttribute("href") === "#") return;
    const target = document.querySelector(link.getAttribute("href"));
    if (!target) return;
    e.preventDefault();
    scrollToElement(target);
  });
  document.addEventListener("landing:scroll-to", function (e) { scrollToElement(e.target); });

  function updateProgress() {
    const max = document.documentElement.scrollHeight - window.innerHeight;
    progressBar.style.transform = "scaleX(" + (max > 0 ? window.scrollY / max : 0) + ")";
  }
  if (progressBar) {
    updateProgress();
    window.addEventListener("scroll", updateProgress, { passive: true });
  }

  document.querySelectorAll("[data-rail]").forEach(function (btn) {
    const track = document.getElementById("company-grid");
    btn.hidden = false;
    btn.addEventListener("click", function () {
      track.scrollBy({ left: Number(btn.getAttribute("data-rail")) * track.clientWidth * 0.8, behavior: reduced ? "auto" : "smooth" });
    });
  });

  if (!motion) return;

  /* ---------- Mise en route ---------- */

  const gsap = window.gsap;
  const ScrollTrigger = window.ScrollTrigger;
  gsap.registerPlugin(ScrollTrigger);
  document.documentElement.classList.add("motion");

  lenis = new window.Lenis({ lerp: 0.09, wheelMultiplier: 0.95, autoRaf: false });
  lenis.on("scroll", ScrollTrigger.update);
  gsap.ticker.add(function (time) { lenis.raf(time * 1000); });
  gsap.ticker.lagSmoothing(0);

  /* ---------- Texte : mots masqués puis révélés ---------- */

  function wrapWord(content, index) {
    const outer = document.createElement("span");
    const inner = document.createElement("span");
    outer.className = "w";
    inner.className = "w-in";
    inner.style.setProperty("--i", index);
    inner.append(content);
    outer.append(inner);
    return outer;
  }

  function splitWords(el, wrap) {
    const parts = document.createDocumentFragment();
    let index = 0;
    Array.from(el.childNodes).forEach(function (node) {
      if (node.nodeType !== Node.TEXT_NODE) {
        parts.append(wrap(node, index++));
        return;
      }
      node.textContent.split(/(\s+)/).forEach(function (piece) {
        if (!piece) return;
        parts.append(/^\s+$/.test(piece) ? " " : wrap(piece, index++));
      });
    });
    el.replaceChildren(parts);
  }

  document.querySelectorAll("[data-split]").forEach(function (el) { splitWords(el, wrapWord); });

  /* ---------- Révélations à l’entrée dans l’écran ---------- */

  const reveal = new IntersectionObserver(function (entries) {
    entries.forEach(function (entry) {
      if (!entry.isIntersecting) return;
      entry.target.classList.add("is-in");
      reveal.unobserve(entry.target);
    });
  }, { threshold: 0.15, rootMargin: "0px 0px -6% 0px" });

  function observeChildren(group) {
    Array.from(group.children).forEach(function (child, i) {
      if (child.dataset.revealed) return;
      child.dataset.revealed = "1";
      child.style.setProperty("--d", (i % 4) * 80 + "ms");
      reveal.observe(child);
    });
  }

  document.querySelectorAll("[data-split], [data-reveal], .scene-step").forEach(function (el) { reveal.observe(el); });
  document.querySelectorAll("[data-reveal-group]").forEach(function (group) {
    observeChildren(group);
    new MutationObserver(function () { observeChildren(group); }).observe(group, { childList: true });
  });

  /* ---------- Héros : parallaxe douce et halo qui suit le curseur ---------- */

  const hero = document.querySelector(".hero");
  const heroScroll = { trigger: hero, start: "top top", end: "bottom top", scrub: true };
  gsap.to(".hero-bg", { yPercent: 16, ease: "none", scrollTrigger: heroScroll });
  gsap.to(".hero-title", { y: -50, ease: "none", scrollTrigger: heroScroll });

  const spot = hero.querySelector(".hero-spot");
  const spotX = gsap.quickTo(spot, "x", { duration: 0.8, ease: "power3.out" });
  const spotY = gsap.quickTo(spot, "y", { duration: 0.8, ease: "power3.out" });
  hero.addEventListener("pointermove", function (e) {
    if (e.pointerType !== "mouse") return;
    const box = hero.getBoundingClientRect();
    hero.classList.add("has-pointer");
    spotX(e.clientX - box.left);
    spotY(e.clientY - box.top);
  });
  hero.addEventListener("pointerleave", function () { hero.classList.remove("has-pointer"); });

  /* ---------- Manifeste : les mots s’allument avec le défilement ---------- */

  const manifesto = document.querySelector("[data-scrub-words]");
  splitWords(manifesto, function (content) {
    const word = document.createElement("span");
    word.append(content);
    return word;
  });
  gsap.fromTo(manifesto.children, { opacity: 0.16 }, {
    opacity: 1,
    ease: "none",
    stagger: 0.12,
    scrollTrigger: { trigger: manifesto, start: "top 78%", end: "bottom 48%", scrub: true }
  });

  /* ---------- Scènes épinglées (écrans larges) ---------- */

  function pinScene(scene) {
    const steps = Array.from(scene.querySelectorAll("[data-scene-step]"));
    const dots = Array.from(scene.querySelectorAll(".scene-dots button"));
    const parts = steps.map(function (step) {
      return { text: step.querySelector(".step-text"), media: step.querySelector(".step-media") };
    });
    const count = steps.length;
    const listeners = new AbortController();
    let active = 0;

    scene.classList.add("is-pinned");
    scene.querySelector(".scene-track").style.setProperty("--steps", count);

    function setActive(index) {
      if (index === active) return;
      active = index;
      dots.forEach(function (dot, i) {
        dot.classList.toggle("is-active", i === index);
        if (i === index) dot.setAttribute("aria-current", "true");
        else dot.removeAttribute("aria-current");
      });
    }

    const timeline = gsap.timeline({
      defaults: { ease: "power2.inOut" },
      scrollTrigger: {
        trigger: scene.querySelector(".scene-track"),
        start: "top top",
        end: "bottom bottom",
        scrub: 0.6,
        onUpdate: function (self) { setActive(Math.min(count - 1, Math.floor(self.progress * count))); }
      }
    });

    parts.forEach(function (part, i) {
      if (i === 0) return;
      const at = i - 0.25;
      const previous = parts[i - 1];
      timeline.fromTo(part.media, { opacity: 0, yPercent: 16, scale: 1.05 }, { opacity: 1, yPercent: 0, scale: 1, duration: 0.4 }, at);
      timeline.fromTo(part.text, { opacity: 0, y: 36 }, { opacity: 1, y: 0, duration: 0.4 }, at + 0.05);
      timeline.to(previous.text, { opacity: 0, y: -36, duration: 0.3 }, at);
      timeline.to(previous.media, { opacity: 0.22, yPercent: -7, scale: 0.92, duration: 0.4 }, at);
      if (i > 1) timeline.to(parts[i - 2].media, { opacity: 0, scale: 0.86, duration: 0.4 }, at);
    });
    timeline.set({}, {}, count);

    function goTo(index) {
      const trigger = timeline.scrollTrigger;
      scrollToY(trigger.start + ((index + 0.5) / count) * (trigger.end - trigger.start));
    }
    dots.forEach(function (dot, i) {
      dot.addEventListener("click", function () { goTo(i); }, { signal: listeners.signal });
    });
    /* Un lien atteint au clavier dans une étape masquée la ramène à l’écran. */
    steps.forEach(function (step, i) {
      step.addEventListener("focusin", function () { if (i !== active) goTo(i); }, { signal: listeners.signal });
    });

    return function unpin() {
      listeners.abort();
      scene.classList.remove("is-pinned");
      scene.querySelector(".scene-track").style.removeProperty("--steps");
    };
  }

  gsap.matchMedia().add("(min-width: 1024px) and (min-height: 620px)", function () {
    const unpins = Array.from(document.querySelectorAll("[data-scene]")).map(pinScene);
    return function () { unpins.forEach(function (unpin) { unpin(); }); };
  });

  /* ---------- Parcours : le fil se déroule, les étapes s’allument ---------- */

  const path = document.querySelector("[data-path]");
  const pathSteps = Array.from(path.querySelectorAll(".path-step"));
  gsap.to(path, {
    "--p": 1,
    ease: "none",
    scrollTrigger: {
      trigger: path,
      start: "top 70%",
      end: "bottom 55%",
      scrub: true,
      onUpdate: function (self) {
        pathSteps.forEach(function (step, i) {
          step.classList.toggle("is-reached", self.progress >= i / (pathSteps.length - 1) - 0.001);
        });
      }
    }
  });

  /* ---------- Compteurs : les chiffres montent quand ils apparaissent ---------- */

  document.addEventListener("landing:offers-loaded", function () {
    document.querySelectorAll("[data-stat], #offer-count").forEach(function (el) {
      const target = parseInt(el.textContent, 10);
      if (isNaN(target)) return;
      const counter = { value: 0 };
      el.textContent = "0";
      ScrollTrigger.create({
        trigger: el,
        start: "top 92%",
        once: true,
        onEnter: function () {
          gsap.to(counter, {
            value: target,
            duration: 1.8,
            ease: "power3.out",
            onUpdate: function () { el.textContent = String(Math.round(counter.value)); }
          });
        }
      });
    });
  });

  /* ---------- Cartes de formules : lueur qui suit le curseur ---------- */

  document.querySelectorAll(".pricing").forEach(function (list) {
    list.addEventListener("pointermove", function (e) {
      const card = e.target.closest(".price-card");
      if (!card) return;
      const box = card.getBoundingClientRect();
      card.style.setProperty("--mx", e.clientX - box.left + "px");
      card.style.setProperty("--my", e.clientY - box.top + "px");
    });
  });

  /* ---------- Hauteurs : les listes se remplissent après coup ---------- */

  const refresh = gsap.delayedCall(0.2, function () { ScrollTrigger.refresh(); }).pause();
  new ResizeObserver(function () { refresh.restart(true); }).observe(document.querySelector("main"));
  document.fonts.ready.then(function () { refresh.restart(true); });
})();
