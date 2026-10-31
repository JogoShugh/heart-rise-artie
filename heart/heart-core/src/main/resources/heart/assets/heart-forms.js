// heart-forms.js: what a HEART page needs beyond htmx (spec/PLAN-HTML-DIALECT.md). It opens an act's dialog,
// shows why a greyed act is not offered, adds entries to a list field, shows a refusal in the dialog that asked,
// re-reads the resource when it changed meanwhile, and announces what an act did.
(function () {
  'use strict';

  function main() { return document.getElementById('resource'); }

  function announce(text) {
    var live = document.getElementById('announce');
    if (live) { live.textContent = ''; live.textContent = text; }
  }

  function problemOf(form) { return form && form.querySelector('.problem'); }

  function showProblem(form, html, stale) {
    var slot = problemOf(form);
    if (!slot) return;
    var parsed = new DOMParser().parseFromString(html || '', 'text/html');
    var found = parsed.querySelector('.problem');
    slot.replaceChildren();
    if (found) {
      Array.prototype.forEach.call(found.childNodes, function (n) { slot.appendChild(document.importNode(n, true)); });
    } else {
      slot.textContent = 'The server refused this. Try again, or reload the page.';
    }
    slot.classList.toggle('stale', !!stale);
    slot.hidden = false;
    markFields(form, parsed);
  }

  // A refusal of invalid input names each field by JSON Pointer; its message goes beside that field.
  function markFields(form, parsed) {
    form.querySelectorAll('.field .error').forEach(function (e) { e.remove(); });
    parsed.querySelectorAll('[data-pointer]').forEach(function (item) {
      var name = item.getAttribute('data-pointer').replace(/^\//, '').split('/')[0];
      var control = form.querySelector('[name="' + CSS.escape(name) + '"]');
      if (!control) return;
      var note = document.createElement('p');
      note.className = 'error';
      note.textContent = item.textContent;
      control.closest('.field').appendChild(note);
      control.setAttribute('aria-invalid', 'true');
    });
  }

  document.addEventListener('click', function (event) {
    var opener = event.target.closest('[data-dialog]');
    if (opener) {
      var dialog = document.getElementById(opener.getAttribute('data-dialog'));
      if (dialog) {
        var slot = dialog.querySelector('.problem');
        if (slot) slot.hidden = true;
        dialog.showModal();
      }
      return;
    }
    var greyed = event.target.closest('.act[aria-disabled="true"]');
    if (greyed) {
      var why = document.getElementById(greyed.getAttribute('aria-describedby'));
      if (why) why.hidden = !why.hidden;
      return;
    }
    var closer = event.target.closest('[data-close]');
    if (closer) { closer.closest('dialog').close(); return; }
    var adder = event.target.closest('[data-add]');
    if (adder) {
      var list = adder.parentElement.querySelector('[data-list]');
      var first = list.querySelector('input');
      var next = first.cloneNode(false);
      next.removeAttribute('id');
      next.removeAttribute('required');
      next.value = '';
      next.setAttribute('aria-label', (adder.parentElement.querySelector('label') || {}).textContent + ', another');
      list.appendChild(next);
      next.focus();
    }
  });

  // A refusal is shown in the dialog that asked, never swapped over the resource.
  document.addEventListener('htmx:beforeSwap', function (event) {
    var status = event.detail.xhr.status;
    if (status < 400) return;
    event.detail.shouldSwap = false;
    event.detail.isError = false;
    var form = event.detail.requestConfig && event.detail.requestConfig.elt;
    form = form && form.closest('form');
    if (status === 412) { refresh(form); return; }
    showProblem(form, event.detail.xhr.responseText, false);
  });

  // The resource changed since the page was read: read it again, and keep the dialog open if the act still is.
  function refresh(form) {
    var rel = form && form.getAttribute('data-rel');
    var kept = form ? new FormData(form) : null;
    htmx.ajax('GET', window.location.pathname, { target: '#resource', select: '#resource', swap: 'outerHTML' })
      .then(function () {
        var again = rel && main().querySelector('form[data-rel="' + CSS.escape(rel) + '"]');
        if (!again) {
          announce('This changed since you opened it, and that act is no longer offered.');
          var button = main().querySelector('.act[data-rel="' + CSS.escape(rel) + '"]');
          if (button) button.focus();
          return;
        }
        if (kept) kept.forEach(function (value, name) {
          var control = again.querySelector('[name="' + CSS.escape(name) + '"]');
          if (control && control.type !== 'checkbox') control.value = value;
        });
        again.closest('dialog').showModal();
        var slot = problemOf(again);
        slot.textContent = 'This changed since you opened it. Check it, then send again.';
        slot.classList.add('stale');
        slot.hidden = false;
      });
  }

  document.addEventListener('heart:done', function (event) {
    announce(event.detail && event.detail.value ? event.detail.value : 'Done.');
  });

  document.addEventListener('htmx:sendError', function (event) {
    var form = event.detail.elt && event.detail.elt.closest('form');
    showProblem(form, '<div class="problem">The server could not be reached. Check the connection, then send again.</div>');
  });
})();
