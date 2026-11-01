// Load the dashboard in headless Chrome, print what it shows, click Merge on each offered bump, print again.
// Every exchange the page has with its server is written to runs/, in YAML, with GitHub's answers as they came.
import puppeteer from "puppeteer-core";
import { writeFileSync } from "node:fs";
import { stringify } from "yaml";

const browser = await puppeteer.launch({
  executablePath: "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
  headless: true,
  args: ["--use-mock-keychain"],
  userDataDir: process.env.CHROME_PROFILE ?? "/tmp/lo-to-heart-chrome",
});
const page = await browser.newPage();
const failures = [];
page.on("requestfailed", (request) => failures.push(`${request.method()} ${request.url()}`));
page.on("pageerror", (error) => failures.push(`page error: ${error.message}`));
const hosts = new Set();
page.on("request", (request) => hosts.add(new URL(request.url()).host));

const exchanges = [];
page.on("response", async (response) => {
  const request = response.request();
  if (!new URL(request.url()).pathname.startsWith("/github/")) return;
  const text = await response.text();
  exchanges.push({
    at: new Date().toISOString(),
    request: { method: request.method(), url: request.url(), headers: request.headers(),
      ...(request.postData() ? { body: JSON.parse(request.postData()) } : {}) },
    response: { status: response.status(), headers: response.headers(), body: text ? JSON.parse(text) : null },
  });
});

await page.goto("http://localhost:8500/bumps.html");
await page.waitForNetworkIdle({ idleTime: 1500 });
console.log("Shown:\n" + await page.$eval("#bumps", (element) => element.innerText));

for (const button of await page.$$("#bumps button")) await button.click();
await page.waitForFunction(() => !document.querySelector("#bumps button"), { timeout: 30000 });
await page.waitForNetworkIdle({ idleTime: 500 });
console.log("\nAfter clicking Merge:\n" + await page.$eval("#bumps", (element) => element.innerText));
console.log("\nHosts the browser talked to: " + [...hosts].join(", "));
console.log("Failures: " + (failures.length ? failures.join("; ") : "none"));
await browser.close();

const file = `runs/part-0-${new Date().toISOString().replace(/\D/g, "").slice(0, 12)}.yaml`;
const yaml = stringify({ exchanges });
if (/ghp_|github_pat_|gho_/.test(yaml)) throw new Error("A token is in the capture; not written.");
writeFileSync(file, yaml);
console.log(`${exchanges.length} exchanges written to ${file}`);
