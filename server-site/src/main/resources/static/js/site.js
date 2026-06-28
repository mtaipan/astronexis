// Minimal client-side sugar (keep it lightweight)
(() => {
  // prevent accidental double submit
  document.querySelectorAll("form").forEach((f) => {
    f.addEventListener("submit", () => {
      const btn = f.querySelector('button[type="submit"]');
      if (btn) {
        btn.disabled = true;
        btn.dataset.originalText = btn.innerText;
        btn.innerText = "Переходим к оплате…";
      }
    });
  });
})();