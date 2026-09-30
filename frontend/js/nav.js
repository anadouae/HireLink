/**
 * Navbar — état scrolled + polish hover
 */
(function () {
  const header = document.querySelector(".site-header");
  if (!header) return;

  function onScroll() {
    header.classList.toggle("is-scrolled", window.scrollY > 12);
  }

  onScroll();
  window.addEventListener("scroll", onScroll, { passive: true });
})();
