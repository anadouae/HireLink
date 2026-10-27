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

  const links = Array.from(header.querySelectorAll(".nav a[href^='#']"));
  const sections = links
    .map(function (link) { return document.querySelector(link.getAttribute("href")); })
    .filter(Boolean);

  function markSection() {
    const line = window.scrollY + 96;
    let current = sections[0];
    sections.forEach(function (section) {
      if (section.offsetTop <= line) current = section;
    });
    links.forEach(function (link) {
      link.classList.toggle("is-current", !!current && link.getAttribute("href") === "#" + current.id);
    });
  }

  markSection();
  window.addEventListener("scroll", markSection, { passive: true });
})();
