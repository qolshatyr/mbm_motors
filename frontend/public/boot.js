// Plain ES5 on purpose: this file must run in browsers too old for the application bundle.
// If the application has not replaced the note, it failed to start: tell the person what to do.
(function () {
  function show() {
    var note = document.getElementById('boot-note');
    if (note) note.hidden = false;
  }
  window.addEventListener('error', show);
  setTimeout(show, 15000);
})();
