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

  // ---- Checking a form against its act's whole schema, before anything is sent (Ajv, JSON Schema 2020-12).
  // HTML's own constraints say what one field needs; the schema says more, such as "at least one of these".
  // The server still decides: this only spares a person a round trip.

  var ajv = null;

  function schemaOf(form) {
    if (form.heartSchema !== undefined) return form.heartSchema;
    var carried = form.querySelector('script.schema');
    try { form.heartSchema = carried ? JSON.parse(carried.textContent) : null; } catch (e) { form.heartSchema = null; }
    return form.heartSchema;
  }

  function validatorOf(form) {
    if (form.heartValidate !== undefined) return form.heartValidate;
    var Ajv = window.ajv2020 && (window.ajv2020.default || window.ajv2020);
    var schema = schemaOf(form);
    form.heartValidate = null;
    if (!Ajv || !schema) return null;
    try {
      ajv = ajv || new Ajv({ allErrors: true, strict: false, validateFormats: false });
      var copy = JSON.parse(JSON.stringify(schema));
      delete copy.$id; // two forms may share one schema; each is compiled on its own
      form.heartValidate = ajv.compile(copy);
    } catch (e) { form.heartValidate = null; }
    return form.heartValidate;
  }

  // The form's fields as the act's JSON, typed as the server types them (ActRequests.fromForm).
  function inputOf(form) {
    var properties = (schemaOf(form) || {}).properties || {};
    var data = new FormData(form);
    var names = [];
    var out = {};
    data.forEach(function (_, name) { if (names.indexOf(name) < 0) names.push(name); });
    names.forEach(function (name) {
      var property = properties[name] || {};
      var type = Array.isArray(property.type) ? property.type.filter(function (t) { return t !== 'null'; })[0] : property.type;
      var given = data.getAll(name).map(String).filter(function (v) { return v.trim() !== ''; });
      // A field left blank is left out, a list with no entries too: "at least one of" means one filled in.
      if (!given.length) return;
      if (type === 'array') { out[name] = given.map(function (v) { return typed(v, (property.items || {}).type); }); return; }
      var last = given[given.length - 1];
      out[name] = type === 'boolean' && last === 'on' ? true : typed(last, type);
    });
    return out;
  }

  function typed(text, type) {
    if (type === 'integer') return /^-?\d+$/.test(text) ? Number(text) : text;
    if (type === 'number') return isNaN(Number(text)) ? text : Number(text);
    if (type === 'boolean') return text === 'true' ? true : text === 'false' ? false : text;
    return text;
  }

  function labelOf(form, name) {
    var control = name && form.querySelector('[name="' + CSS.escape(name) + '"]');
    var label = control && control.closest('.field') && control.closest('.field').querySelector('label');
    return label ? label.childNodes[0].textContent.trim() : name;
  }

  function said(form, error) {
    var p = error.params || {};
    var plural = function (n, one, many) { return n + ' ' + (n === 1 ? one : many); };
    switch (error.keyword) {
      case 'required': return 'Fill in ' + labelOf(form, p.missingProperty) + '.';
      case 'minimum': return 'At least ' + p.limit + '.';
      case 'maximum': return 'At most ' + p.limit + '.';
      case 'exclusiveMinimum': return 'More than ' + p.limit + '.';
      case 'exclusiveMaximum': return 'Less than ' + p.limit + '.';
      case 'multipleOf': return 'A multiple of ' + p.multipleOf + '.';
      case 'minLength': return 'At least ' + plural(p.limit, 'character', 'characters') + '.';
      case 'maxLength': return 'At most ' + plural(p.limit, 'character', 'characters') + '.';
      case 'minItems': return 'At least ' + plural(p.limit, 'entry', 'entries') + '.';
      case 'maxItems': return 'At most ' + plural(p.limit, 'entry', 'entries') + '.';
      case 'uniqueItems': return 'Each entry only once.';
      case 'pattern': return 'Not in the form this asks for.';
      case 'enum': return 'Choose one of: ' + (p.allowedValues || []).join(', ') + '.';
      case 'const': return 'Must be ' + p.allowedValue + '.';
      case 'type': return p.type === 'integer' ? 'A whole number.' : p.type === 'number' ? 'A number.' : 'Must be ' + p.type + '.';
      case 'additionalProperties': return labelOf(form, p.additionalProperty) + ' is not asked for.';
      default: return error.message ? error.message.charAt(0).toUpperCase() + error.message.slice(1) + '.' : 'Not valid.';
    }
  }

  // "At least one of" or "exactly one of" whose branches only require fields reads as such.
  function choiceSaid(form, keyword, branches) {
    var named = (branches || []).map(function (b) { return (b.required || []).map(function (n) { return labelOf(form, n); }).join(' and '); })
      .filter(function (words) { return words; });
    if (!named.length) return 'Check the fields: they do not fit together.';
    return (keyword === 'oneOf' ? 'Fill in exactly one of: ' : 'Fill in at least one of: ') + named.join(', ') + '.';
  }

  // What is wrong with [form], each at its field (null for the whole form); empty when it fits its schema.
  function problemsOf(form) {
    var validate = validatorOf(form);
    if (!validate || validate(inputOf(form))) return [];
    var schema = schemaOf(form);
    var problems = [];
    validate.errors.forEach(function (error) {
      var branch = /^#\/(anyOf|oneOf)\//.exec(error.schemaPath);
      if (branch) return; // said once, by the anyOf or oneOf itself
      var root = error.instancePath === '' && (error.keyword === 'anyOf' || error.keyword === 'oneOf');
      var field = error.keyword === 'required' ? error.params.missingProperty
        : error.keyword === 'additionalProperties' ? null
        : (error.instancePath.split('/')[1] || null);
      var text = root ? choiceSaid(form, error.keyword, schema[error.keyword]) : said(form, error);
      if (!problems.some(function (p) { return p.field === field && p.text === text; })) problems.push({ field: field, text: text });
    });
    return problems;
  }

  function clearProblems(form) {
    form.querySelectorAll('.field .error').forEach(function (e) { e.remove(); });
    form.querySelectorAll('[aria-invalid]').forEach(function (e) { e.removeAttribute('aria-invalid'); });
    var slot = problemOf(form);
    if (slot) { slot.hidden = true; slot.replaceChildren(); slot.classList.remove('stale'); }
  }

  // Shows [problems] in [form]: each beside its field, and what belongs to no one field at the top.
  function showChecks(form, problems) {
    var top = [];
    problems.forEach(function (problem) {
      var control = problem.field && form.querySelector('[name="' + CSS.escape(problem.field) + '"]');
      if (!control) { top.push(problem.text); return; }
      var note = document.createElement('p');
      note.className = 'error';
      note.textContent = problem.text;
      control.closest('.field').appendChild(note);
      control.setAttribute('aria-invalid', 'true');
    });
    var slot = problemOf(form);
    if (slot) {
      slot.textContent = top.length ? top.join(' ') : 'Check the fields marked below.';
      slot.hidden = false;
    }
    var first = form.querySelector('[aria-invalid="true"]');
    if (first) first.focus();
  }

  // True when [form] may be sent; otherwise its problems are shown and nothing is sent.
  function checked(form) {
    clearProblems(form);
    var problems = problemsOf(form);
    if (problems.length) showChecks(form, problems);
    return problems.length === 0;
  }

  document.addEventListener('htmx:beforeRequest', function (event) {
    var form = event.detail.elt && event.detail.elt.closest('form');
    if (form && form.querySelector('script.schema') && !checked(form)) event.preventDefault();
  });

  document.addEventListener('click', function (event) {
    var opener = event.target.closest('[data-dialog]');
    if (opener) {
      var dialog = document.getElementById(opener.getAttribute('data-dialog'));
      if (dialog) {
        dialog.querySelectorAll('form').forEach(clearProblems);
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
    if (status < 400) {
      // What the act did, said once the new page is in: htmx would fire it on the form the swap removes.
      var said = done(event.detail.xhr.getResponseHeader('HX-Trigger'));
      if (said) window.setTimeout(function () { announce(said); }, 0);
      return;
    }
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
          // The act is gone: say so, with the server's reason, beside its greyed button.
          var button = main().querySelector('.act[data-rel="' + CSS.escape(rel) + '"]');
          var why = button && document.getElementById(button.getAttribute('aria-describedby'));
          announce('This changed since you opened it. ' + (button ? button.textContent : 'That act') +
            ' is no longer offered' + (why ? ': ' + why.textContent : '.'));
          if (why) why.hidden = false;
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

  // A safe act goes to an address: its target's template, filled from the form's fields (RFC 6570, the simple
  // expressions HEART's targets use: {name} in a path, {?a,b} and {&a} in a query).
  function expand(template, data) {
    return template.replace(/\{([?&]?)([^}]+)\}/g, function (_, operator, names) {
      var parts = names.split(',').map(function (name) {
        var values = data.getAll(name).filter(function (v) { return v !== ''; });
        if (!operator) return values.map(encodeURIComponent).join(',');
        return values.map(function (v) { return encodeURIComponent(name) + '=' + encodeURIComponent(v); }).join('&');
      }).filter(function (part) { return part !== ''; });
      if (!operator) return parts.join(',');
      return parts.length ? operator + parts.join('&') : '';
    });
  }

  document.addEventListener('submit', function (event) {
    var form = event.target;
    var template = form.getAttribute('data-template');
    if (!template) return;
    event.preventDefault();
    if (!checked(form)) return;
    window.location.assign(expand(template, new FormData(form)));
  });

  // A row's acts are read from its match when a person asks: offered acts go to the match with the act's dialog
  // open; acts it won't offer say why.
  document.addEventListener('click', function (event) {
    var button = event.target.closest('[data-acts]');
    if (!button) return;
    var menu = button.parentElement.querySelector('.row-menu');
    var open = button.getAttribute('aria-expanded') === 'true';
    button.setAttribute('aria-expanded', String(!open));
    menu.hidden = open;
    if (open || menu.dataset.read) return;
    var href = button.getAttribute('data-acts');
    menu.textContent = 'Reading…';
    fetch(href, { headers: { Accept: 'text/html' }, credentials: 'same-origin' })
      .then(function (response) { return response.text(); })
      .then(function (html) {
        var page = new DOMParser().parseFromString(html, 'text/html');
        menu.replaceChildren();
        var acts = page.querySelectorAll('.acts button.act');
        if (!acts.length) { menu.textContent = 'Nothing can be done to this now.'; }
        acts.forEach(function (act) {
          var line;
          if (act.getAttribute('aria-disabled') === 'true') {
            line = document.createElement('span');
            line.className = 'greyed';
            var why = page.getElementById(act.getAttribute('aria-describedby'));
            line.textContent = act.textContent + ': ' + (why ? why.textContent : 'not now');
          } else {
            line = document.createElement('a');
            line.href = href + '#' + act.getAttribute('data-dialog');
            line.textContent = act.textContent;
          }
          menu.appendChild(line);
        });
        menu.dataset.read = 'true';
      })
      .catch(function () { menu.textContent = 'This could not be read. Open it instead.'; });
  });

  // Arriving with an act's dialog named in the address opens it.
  window.addEventListener('DOMContentLoaded', function () {
    var named = window.location.hash.slice(1);
    var dialog = named && document.getElementById(named);
    if (dialog && dialog.tagName === 'DIALOG') dialog.showModal();
  });

  function done(header) {
    try { return header ? JSON.parse(header)['heart:done'] : null; } catch (e) { return null; }
  }

  document.addEventListener('htmx:sendError', function (event) {
    var form = event.detail.elt && event.detail.elt.closest('form');
    showProblem(form, '<div class="problem">The server could not be reached. Check the connection, then send again.</div>');
  });
})();
