// Page objects: where each dashboard puts a bed's buttons, status and moisture.
// This is the only client-specific part of the suite. Both pages are htmx: a
// button sits in a form whose hx-post says where it goes.

/** A card's buttons, each with the form it submits. */
function forms(html) {
    return [...html.matchAll(/<form class="act" hx-post="([^"]+)"[^>]*>([\s\S]*?)<\/form>/g)].map(([, action, body]) => ({
        action: action.replaceAll("&amp;", "&"),
        title: body.match(/<button[^>]*>([^<]*)<\/button>/)[1],
        fields: Object.fromEntries([...body.matchAll(/<input([^>]*)>/g)].map(([, attrs]) => [
            attrs.match(/name="([^"]+)"/)?.[1],
            attrs.match(/value="([^"]*)"/)?.[1] ?? "",
        ]).filter(([name]) => name)),
    }));
}

function cardUrl(html) {
    return html.match(/hx-get="([^"]+)"/)[1].replaceAll("&amp;", "&");
}

async function text(url, init) {
    const answer = await fetch(url, init);
    if (!answer.ok) throw new Error(`${init?.method ?? "GET"} ${url} answered ${answer.status}: ${await answer.text()}`);
    return answer.text();
}

async function press(dashboard, html, title) {
    const form = forms(html).find((f) => f.title === title);
    if (!form) throw new Error(`no "${title}" button; the bed shows ${forms(html).map((f) => f.title).join(", ") || "none"}`);
    return text(dashboard + form.action, {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded", "HX-Request": "true" },
        body: new URLSearchParams(form.fields).toString(),
    });
}

const common = {
    buttons: (html) => forms(html).map((f) => f.title),
    press,
    refresh: (dashboard, html) => text(dashboard + cardUrl(html), { headers: { "HX-Request": "true" } }),
};

export const pages = {
    // The bespoke dashboard: a page per bed, a status badge.
    bespoke: {
        ...common,
        open: (dashboard, bed) => text(`${dashboard}/beds/${bed}/card`),
        status: (html) => html.match(/<span class="badge[^"]*">([^<]*)<\/span>/)[1],
        moisture: (html) => html.match(/<dd class="moisture">([^<]*)<\/dd>/)[1],
    },
    // The ARTIE dashboard: from the bookmark, the bed is found by the form that gets a bed by its id.
    artie: {
        ...common,
        open: async (dashboard, bed) => {
            const home = await text(`${dashboard}/card`);
            const form = forms(home).find((f) => "id" in f.fields);
            return text(dashboard + form.action, {
                method: "POST",
                headers: { "Content-Type": "application/x-www-form-urlencoded", "HX-Request": "true" },
                body: new URLSearchParams({ ...form.fields, id: String(bed) }).toString(),
            });
        },
        status: (html) => html.match(/data-member="status">([^<]*)</)[1],
        moisture: (html) => html.match(/data-member="moistureLevel">([^<]*)</)[1],
    },
    // The dashboard that reads the OpenAPI description live: a page per bed, its members as the ARTIE card shows them.
    live: {
        ...common,
        open: (dashboard, bed) => text(`${dashboard}/beds/${bed}/card`),
        status: (html) => html.match(/data-member="status">([^<]*)</)[1],
        moisture: (html) => html.match(/data-member="moistureLevel">([^<]*)</)[1],
    },
};
// The same page, from the dashboard that also follows each answer's affordance link.
pages.afford = pages.live;
