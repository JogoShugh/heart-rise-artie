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

const size = (body) => (body == null ? 0 : Buffer.byteLength(typeof body === 'string' ? body : JSON.stringify(body)));
const count = (file) => {
  const steps = yaml.loadAll(fs.readFileSync(file, 'utf8')).filter(Boolean).slice(1);
  const sent = steps.flatMap((step) => step.exchanges || []);
  const bytes = sent.reduce((total, e) => total + size(e.response.body), 0);
  const sets = sent.filter((e) => e.request.method === 'GET' && shared.test(e.request.url));
  return { all: sent.length, sets: sets.length, bytes };
};

const rows = [];
const savings = [];
const size2 = (b) => (b >= 1e9 ? `${(b / 1e9).toFixed(1)} GB` : b >= 1e6 ? `${(b / 1e6).toFixed(1)} MB` : `${Math.round(b / 1e3)} KB`);
for (const forge of ['github', 'gitlab']) {
  dirs.forEach((dir, m) => {
    const runs = [501, 502, 503, 504, 505].map((v) => count(`${dir}/${forge}/pr-${v}.yaml`));
    if (m === 0) savings.push({ forge, l0: runs.reduce((t, r) => t + r.bytes, 0) / runs.length });
    if (m === 1) savings[savings.length - 1].freshSets = runs.reduce((t, r) => t + r.sets, 0) / runs.length;
    if (m === 2) Object.assign(savings[savings.length - 1], { warmSets: runs.slice(1).reduce((t, r) => t + r.sets, 0) / (runs.length - 1), cold: runs[0].bytes, warm: runs.slice(1).reduce((t, r) => t + r.bytes, 0) / (runs.length - 1) });
    const cell = (r) => `<td>${r.all}<br><span class="soft">${r.sets} sets/schemas</span></td>`;
    const total = runs.reduce((t, r) => ({ all: t.all + r.all, sets: t.sets + r.sets }), { all: 0, sets: 0 });
    rows.push(`<tr><th>${forge === 'github' ? 'Real GitHub' : 'Local GitLab'}</th><td>${modes[m]}</td>` +
      runs.map(cell).join('') + `<td><b>${total.all}</b><br><span class="soft">${total.sets} sets/schemas</span></td></tr>`);
  });
}
const extrapolated = savings.map(({ forge, l0, cold, warm, freshSets, warmSets }) => [5, 100, 10000, 1000000].map((n) => {
  const was = l0 * n;
  const now = cold + warm * (n - 1);
  return `<tr><th>${forge === 'github' ? 'Real GitHub' : 'Local GitLab'}</th><td>${n.toLocaleString('en-US')}${n === 5 ? ' (measured)' : ''}</td>` +
    `<td>${size2(was)}</td><td>${size2(now)}</td><td><b>${size2(was - now)}</b></td><td><b>${Math.round((100 * (was - now)) / was)}%</b></td>` +
    `<td>${Math.round((n - 1) * (freshSets - warmSets)).toLocaleString('en-US')}</td></tr>`;
}).join('\n  ')).join('\n  ');
const per = savings.map(({ forge, l0, cold, warm }) => `<li><b>${forge === 'github' ? 'Real GitHub' : 'Local GitLab'}:</b> ${size2(l0)} a release at L0; at L2 ${size2(cold)} for the first, then ${size2(warm)} a release once cached.</li>`).join('\n    ');
process.stdout.write(`<div class="wrap"><table>
  <tr><th>Forge</th><th>How it ran</th><th>501</th><th>502</th><th>503</th><th>504</th><th>505</th><th>All five</th></tr>
  ${rows.join('\n  ')}
</table></div>
<h3>Bytes received, and at scale</h3>
<p class="soft">The answers ARTIE received, as JSON text before compression; headers not counted. Measured per release over these five; extrapolated as one desk whose cache holds, each further release costing what a warm one did here. Requests never sent: the form set and schema fetches a fresh desk makes each release and a warm one answers from memory.</p>
<ul>
    ${per}
</ul>
<div class="wrap"><table>
  <tr><th>Forge</th><th>Releases</th><th>L0</th><th>L2, one desk</th><th>Bytes saved</th><th>Saved, %</th><th>Requests never sent</th></tr>
  ${extrapolated}
</table></div>`);
