// Prints, as YAML, the merge step of a release trace: what ARTIE sent, and what the pull request said after.
//   node merge-step.mjs JS_YAML TRACE
import fs from 'fs';
import vm from 'vm';

const [lib, trace] = process.argv.slice(2);
const context = {};
vm.runInNewContext(fs.readFileSync(lib, 'utf8'), context);
const yaml = context.jsyaml;
const step = yaml.loadAll(fs.readFileSync(trace, 'utf8'))
  .find((doc) => doc && doc.params && doc.params.relation === 'scm:merge');
const exchange = step.exchanges[0];
const after = exchange.response.body || {};
const kept = ['state', 'merged', 'mergeable_state', 'merge_commit_sha', 'upstream'];
const shown = {
  intent: step.intent,
  sent: { method: exchange.request.method, url: exchange.request.url, body: exchange.request.body },
  answered: exchange.response.status,
  'the pull request after': Object.fromEntries(kept.filter((k) => k in after).map((k) => [k, after[k]])),
};
process.stdout.write(yaml.dump(shown, { lineWidth: 100 }));
