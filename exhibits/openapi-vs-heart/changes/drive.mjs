// Drives one dashboard the way a person would, for run-changes.sh: open bed 2,
// read what it shows, find the button whose title is about watering, and press
// it with what its form shows. The same rule for both dashboards. Prints one
// JSON line: what was shown, which button was pressed, what the page said, and
// every request the dashboard sent the garden meanwhile, with its status.
//
//   node drive.mjs bespoke [millilitres]   the bespoke dashboard on :3001
//   node drive.mjs live [millilitres]      the dashboard that reads OpenAPI live, on :3004
//   node drive.mjs afford                  that dashboard plus the affordance link, on :3005
//   node drive.mjs artie                   the ARTIE dashboard on :3002
//   node drive.mjs claude                  the ARTIE dashboard's Ask, with Claude
const [client, typed] = process.argv.slice(2);
const WATERING = /water|irrigat/i;
const garden = process.env.GARDEN_URL ?? "http://localhost:8080";
const dashboard = { bespoke: "http://localhost:3001", live: "http://localhost:3004", afford: "http://localhost:3005" }[client]
    ?? "http://localhost:3002";

function forms(html) {
    return [...html.matchAll(/<form class="act" hx-post="([^"]+)"[^>]*>([\s\S]*?)<\/form>/g)].map(([, action, body]) => ({
        action: action.replaceAll("&amp;", "&"),
        title: body.match(/<button[^>]*>([^<]*)<\/button>/)[1],
        fields: Object.fromEntries([
            ...[...body.matchAll(/<input([^>]*)>/g)].map(([, a]) => [a.match(/name="([^"]+)"/)?.[1], a.match(/value="([^"]*)"/)?.[1] ?? ""]),
            ...[...body.matchAll(/<select[^>]*name="([^"]+)"[^>]*>\s*<option>([^<]*)<\/option>/g)].map(([, name, first]) => [name, first]),
        ].filter(([name]) => name)),
    }));
}

const post = (url, fields) => fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded", "HX-Request": "true" },
    body: new URLSearchParams(fields).toString(),
}).then((r) => r.text());

/** What the card shows about moisture: the bespoke page's moisture tile, or any state member ARTIE shows with "moisture" in its name. */
function moisture(html) {
    const bespoke = html.match(/<dd class="moisture">([^<]*)<\/dd>/);
    if (bespoke) return { label: "Moisture", value: bespoke[1] };
    const artie = html.match(/<span class="name">([^<]*moisture[^<]*)<\/span><span class="value"[^>]*>([^<]*)</i);
    return artie ? { label: artie[1], value: artie[2] } : null;
}

const message = (html) => html.match(/<p class="message[^"]*">([^<]*)<\/p>/)?.[1] ?? null;

const before = (await (await fetch(`${dashboard}/_calls`)).json()).length;
let card;
if (client === "bespoke" || client === "live" || client === "afford") {
    card = await (await fetch(`${dashboard}/beds/2/card`)).text();
} else {
    const home = await (await fetch(`${dashboard}/card`)).text();
    const lookup = forms(home).find((f) => "id" in f.fields);
    card = await post(dashboard + lookup.action, { ...lookup.fields, id: "2" });
}
const shown = { buttons: forms(card).map((f) => f.title), moisture: moisture(card) };
let pressed = null;
let answer = card;
if (client === "claude") {
    pressed = "Ask: Water bed 2 with about 250 ml";
    answer = await post(`${dashboard}/ask?at=${encodeURIComponent(`${garden}/beds/2`)}`, { intent: "Water bed 2 with about 250 ml" });
} else {
    const form = forms(card).find((f) => WATERING.test(f.title));
    if (form) {
        pressed = form.title;
        const fields = typed ? { ...form.fields, milliliters: typed } : form.fields;
        answer = await post(dashboard + form.action, fields);
    }
}
const calls = (await (await fetch(`${dashboard}/_calls`)).json()).slice(before);
console.log(JSON.stringify({ client, typed: typed ?? null, shown, pressed, said: message(answer), calls }));
