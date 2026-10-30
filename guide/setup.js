(function () {
  var steps = Array.prototype.slice.call(document.querySelectorAll('[data-step]'));
  var KEY = 'den-tv-guide-done';
  var done = {};
  try { done = JSON.parse(localStorage.getItem(KEY) || '{}') || {}; } catch (e) { done = {}; }
  function paint() {
    var n = 0;
    steps.forEach(function (s) {
      var on = !!done[s.id];
      s.classList.toggle('done', on);
      var box = document.getElementById('done-' + s.id);
      if (box) box.checked = on;
      if (on) n++;
    });
    document.getElementById('count').textContent = n === steps.length ? 'All done' : n + ' of ' + steps.length + ' done';
    document.getElementById('trackFill').style.width = (n / steps.length * 100) + '%';
  }
  steps.forEach(function (s) {
    var box = document.getElementById('done-' + s.id);
    if (!box) return;
    box.addEventListener('change', function () {
      done[s.id] = box.checked;
      try { localStorage.setItem(KEY, JSON.stringify(done)); } catch (e) { /* this browser keeps nothing; the page still works */ }
      paint();
      if (box.checked) {
        var next = steps[steps.indexOf(s) + 1];
        if (next && !done[next.id]) next.scrollIntoView({ block: 'start' });
      }
    });
  });
  paint();
  var copy = document.getElementById('copyCode');
  copy.addEventListener('click', function () {
    var ok = function () { copy.textContent = 'Copied'; setTimeout(function () { copy.textContent = 'Copy the code'; }, 1600); };
    try {
      navigator.clipboard.writeText('3476905').then(ok, function () { copy.textContent = 'The code is 3476905'; });
    } catch (e) { copy.textContent = 'The code is 3476905'; }
  });
})();
