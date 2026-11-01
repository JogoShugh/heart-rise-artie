// Load the dashboard in headless Chrome, print what it shows, click Merge on each offered bump, print again.
import puppeteer from "puppeteer-core";

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
const requests = [];
page.on("request", (request) => requests.push(new URL(request.url()).host));

await page.goto("http://localhost:8500/bumps.html");
await page.waitForFunction(() => document.querySelectorAll("#bumps p").length >= 2, { timeout: 30000 });
console.log("Shown:\n" + await page.$eval("#bumps", (element) => element.innerText));

for (const button of await page.$$("#bumps button")) await button.click();
await page.waitForFunction(() => !document.querySelector("#bumps button"), { timeout: 30000 });
console.log("\nAfter clicking Merge:\n" + await page.$eval("#bumps", (element) => element.innerText));
console.log("\nHosts the browser talked to: " + [...new Set(requests)].join(", "));
console.log("Failures: " + (failures.length ? failures.join("; ") : "none"));
await browser.close();
