// Steps for garden-care.feature. They talk to a dashboard over HTTP, as a
// browser would, through a page object chosen by CLIENT (bespoke or artie).
// The garden itself is touched only to reset it and to report the frost.
import { Before, Given, When, Then, setDefaultTimeout } from "@cucumber/cucumber";
import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { pages } from "./pages.mjs";

setDefaultTimeout(30_000);

const garden = process.env.GARDEN_URL ?? "http://localhost:8080";
const dashboard = process.env.DASHBOARD_URL;
const page = pages[process.env.CLIENT];
if (!page || !dashboard) throw new Error("set CLIENT (bespoke or artie) and DASHBOARD_URL");

Before(async function () {
    this.cards = {};
    this.callsBefore = (await calls()).length;
});

async function calls() {
    return (await fetch(`${dashboard}/_calls`)).json();
}

Given("the garden is at its seed state", function () {
    execFileSync(process.env.GARDEN_RESET, { stdio: "ignore" });
});

Given("a hard frost has hit the garden", async function () {
    // The operator's report: weather, not a dashboard act.
    const answer = await fetch(`${garden}/actuator/frost`, {
        method: "POST", headers: { Authorization: `Bearer ${process.env.GARDEN_TOKEN}` },
    });
    assert.equal(answer.status, 200, "the frost endpoint answered");
});

When("the operator opens bed {int}", async function (bed) {
    this.cards[bed] = await page.open(dashboard, bed);
});

When("the operator presses {string} on bed {int}", async function (title, bed) {
    this.cards[bed] = await page.press(dashboard, this.cards[bed], title);
});

When("the operator presses every button bed {int} shows", async function (bed) {
    for (const title of page.buttons(this.cards[bed])) {
        this.cards[bed] = await page.press(dashboard, this.cards[bed], title);
    }
});

Then("bed {int} shows exactly the buttons {string}", async function (bed, titles) {
    this.cards[bed] = await page.refresh(dashboard, this.cards[bed]);
    assert.deepEqual(page.buttons(this.cards[bed]), titles.split(",").map((t) => t.trim()));
});

Then("bed {int}'s moisture is {int}", async function (bed, moisture) {
    assert.equal(page.moisture(this.cards[bed]), String(moisture));
});

Then("bed {int}'s status reads frozen", async function (bed) {
    const status = page.status(this.cards[bed]);
    assert.match(status, /frozen/i, `the status reads "${status}"`);
});

Then("the dashboard sent nothing the garden refused", async function () {
    const refused = (await calls()).slice(this.callsBefore).filter((c) => c.status >= 400);
    assert.deepEqual(refused, [], "requests the garden refused");
});
