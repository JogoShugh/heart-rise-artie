// Prints, as YAML, how a release trace's pull request offered scm:merge before the merge, in its own dialect, and
// what ARTIE sent: the form under _forms (HAL Schema Forms) or the template under _templates (HAL-FORMS).
//   node merge-offer.mjs JS_YAML TRACE
import fs from 'fs';
import { createRequire } from 'module';

const [lib, trace] = process.argv.slice(2);
const yaml = createRequire(import.meta.url)(fs.realpathSync(lib));
const steps = yaml.loadAll(fs.readFileSync(trace, 'utf8')).slice(1);
const merge = steps.findIndex((s) => s.params && s.params.relation === 'scm:merge');
let offered = null;
for (const step of steps.slice(0, merge + 1)) {
  for (const ex of step.exchanges || []) {
    const body = ex.response && ex.response.body;
    const form = body && ((body._forms && body._forms['scm:merge']) || (body._templates && body._templates['scm:merge']));
    if (form) offered = { as: ex.response.headers['content-type'], under: body._forms ? '_forms' : '_templates', form };
  }
}
const sent = steps[merge].exchanges.find((ex) => ex.request.method !== 'GET');
const { schema, ...rest } = offered.form;
const shown = {
  'offered as': offered.as,
  [`${offered.under}: scm:merge`]: schema ? { ...rest, schema: { required: schema.required, properties: schema.properties } } : rest,
  'ARTIE sent': { method: sent.request.method, path: new URL(sent.request.url).pathname, body: sent.request.body },
  answered: sent.response.status,
};
process.stdout.write(yaml.dump(shown, { lineWidth: 100 }));
