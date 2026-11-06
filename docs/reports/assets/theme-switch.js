// The light and dark switch every generated report shares: a button with id "theme" switches the page's
// data-theme; the viewer's system theme holds until they choose, and the choice is kept in this browser where it can.
(function () {
  var root = document.documentElement, button = document.getElementById('theme'), key = 'heart-report-theme';
  if (!button) return;
  function current() {
    return root.dataset.theme || (matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light');
  }
  function label() { button.textContent = current() === 'dark' ? '◑ Light' : '◐ Dark'; }
  try { var kept = localStorage.getItem(key); if (kept === 'light' || kept === 'dark') root.dataset.theme = kept; } catch (e) {}
  label();
  button.addEventListener('click', function () {
    root.dataset.theme = current() === 'dark' ? 'light' : 'dark';
    try { localStorage.setItem(key, root.dataset.theme); } catch (e) {}
    label();
  });
  matchMedia('(prefers-color-scheme: dark)').addEventListener('change', label);
})();
