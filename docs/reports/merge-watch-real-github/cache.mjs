// What went on the network, release by release, as an HTML table on stdout: for the L0 run, the L2 run with a fresh
// desk (and so a fresh cache) for each release, and the L2 run with one desk, and one cache, for all five. A trace
// records only what reached the network: a form set or schema answered from ARTIE's cache is in none of them.
//   node cache.mjs JS_YAML L0_RUNS FRESH_RUNS ONE_DESK_RUNS
import fs from 'fs';
import vm from 'vm';

const [lib, ...dirs] = process.argv.slice(2);
const context = {};
vm.runInNewContext(fs.readFileSync(lib, 'utf8'), context);
const yaml = context.jsyaml;
const modes = ['L0, a fresh desk each release', 'L2, a fresh desk each release', 'L2, one desk for all five'];
const shared = /\/affordance-sets\/|\/schemas\//;

const count = (file) => {
  const steps = yaml.loadAll(fs.readFileSync(file, 'utf8')).filter(Boolean).slice(1);
  const sent = steps.flatMap((step) => step.exchanges || []);
  const sets = sent.filter((e) => e.request.method === 'GET' && shared.test(e.request.url));
  return { all: sent.length, sets: sets.length, notModified: sent.filter((e) => e.response.status === 304).length };
};

const rows = [];
for (const forge of ['github', 'gitlab']) {
  dirs.forEach((dir, m) => {
    const runs = [501, 502, 503, 504, 505].map((v) => count(`${dir}/${forge}/pr-${v}.yaml`));
    const cell = (r) => `<td>${r.all}<br><span class="soft">${r.sets} sets/schemas</span></td>`;
    const total = runs.reduce((t, r) => ({ all: t.all + r.all, sets: t.sets + r.sets }), { all: 0, sets: 0 });
    rows.push(`<tr><th>${forge === 'github' ? 'Real GitHub' : 'Local GitLab'}</th><td>${modes[m]}</td>` +
      runs.map(cell).join('') + `<td><b>${total.all}</b><br><span class="soft">${total.sets} sets/schemas</span></td></tr>`);
  });
}
process.stdout.write(`<div class="wrap"><table>
  <tr><th>Forge</th><th>How it ran</th><th>501</th><th>502</th><th>503</th><th>504</th><th>505</th><th>All five</th></tr>
  ${rows.join('\n  ')}
</table></div>`);
