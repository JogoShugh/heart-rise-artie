// The report's data, as JSON on stdout: for each forge and variant, every exchange ARTIE had with that forge's
// gateway (the scm calls), each with the calls the gateway made to the forge's own API while answering it (the
// native calls, from the gateway's record, joined by the traceparent each carries); and how each release ended.
//   node data.mjs JS_YAML RUNS_DIR
import fs from 'fs';
import vm from 'vm';
import path from 'path';

const [lib, runs] = process.argv.slice(2);
const context = {};
vm.runInNewContext(fs.readFileSync(lib, 'utf8'), context);
const yaml = context.jsyaml;
const read = (file) => yaml.loadAll(fs.readFileSync(file, 'utf8')).filter(Boolean);
const json = (file) => JSON.parse(fs.readFileSync(file, 'utf8'));
const time = (at) => new Date(at).getTime();

const forges = { github: { port: '8093' }, gitlab: { port: '8094' } };
const out = {};
for (const [forge, { port }] of Object.entries(forges)) {
  const native = read(path.join(runs, forge, 'upstream.yaml'));
  out[forge] = {};
  for (const variant of [501, 502, 503, 504, 505]) {
    const steps = read(path.join(runs, forge, `pr-${variant}.yaml`)).slice(1);
    const desk = json(path.join(runs, forge, `pr-${variant}.desk.json`));
    const dev = json(path.join(runs, forge, `pr-${variant}.dev.json`));
    const calls = [];
    steps.forEach((step, i) => (step.exchanges || []).forEach((exchange) => {
      if (!String(exchange.request.url).includes(`:${port}/`)) return;
      const parent = (exchange.request.headers || {}).traceparent;
      calls.push({
        step: i + 1,
        words: step.intent || step.say,
        relation: (step.params || {}).relation || null,
        method: exchange.request.method,
        url: exchange.request.url,
        sent: exchange.request.body ?? null,
        status: exchange.response.status,
        answer: exchange.response.body ?? null,
        native: native.filter((call) => parent && call.traceparent === parent).map((call) => ({
          method: call.request.method,
          url: call.request.url,
          sent: call.request.body ?? null,
          status: call.response.status,
          answer: call.response.body ?? null,
        })),
      });
    }));
    out[forge][variant] = {
      pull: desk.pull,
      steps: steps.length,
      seconds: Math.round((time(steps[steps.length - 1].at) - time(steps[0].at)) / 1000),
      // Promoted by this run: its own env:promote act, not whatever Dev last held (one desk serves every release).
      promoted: steps.some((step) => (step.params || {}).relation === 'env:promote' && step.outcome === 'done'),
      dev: dev.commit,
      calls,
    };
  }
}
process.stdout.write(JSON.stringify(out));
