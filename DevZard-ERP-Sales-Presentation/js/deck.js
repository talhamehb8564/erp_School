(function () {
  const slides = Array.from(document.querySelectorAll(".slide"));
  const count = document.getElementById("count");
  const bar = document.getElementById("bar");
  let i = 0;

  function show(n) {
    i = Math.max(0, Math.min(slides.length - 1, n));
    slides.forEach((s, idx) => s.classList.toggle("on", idx === i));
    if (count) count.textContent = i + 1 + " / " + slides.length;
    if (bar) bar.style.width = ((i + 1) / slides.length) * 100 + "%";
    try {
      history.replaceState(null, "", "#s" + (i + 1));
    } catch (_) {}
  }

  document.getElementById("next")?.addEventListener("click", () => show(i + 1));
  document.getElementById("prev")?.addEventListener("click", () => show(i - 1));

  window.addEventListener("keydown", (e) => {
    if (e.key === "ArrowRight" || e.key === "PageDown" || e.key === " ") {
      e.preventDefault();
      show(i + 1);
    }
    if (e.key === "ArrowLeft" || e.key === "PageUp") {
      e.preventDefault();
      show(i - 1);
    }
    if (e.key === "Home") show(0);
    if (e.key === "End") show(slides.length - 1);
  });

  let x0 = null;
  window.addEventListener("touchstart", (e) => {
    x0 = e.changedTouches[0].screenX;
  }, { passive: true });
  window.addEventListener("touchend", (e) => {
    if (x0 == null) return;
    const dx = e.changedTouches[0].screenX - x0;
    if (dx < -40) show(i + 1);
    if (dx > 40) show(i - 1);
    x0 = null;
  });

  const fromHash = Number((location.hash.match(/s(\d+)/) || [])[1]);
  show(fromHash ? fromHash - 1 : 0);
})();
