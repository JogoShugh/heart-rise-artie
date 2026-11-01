// The simulated services (heart/heart-sims): built once and run as one process
// for the whole run, reset before each scenario. A request to a service's host
// (build.test) is sent to the process instead, naming the host in X-Sim-Host,
// so a client's addresses are the same here as in ARTIE's in-memory run.
import { BeforeAll, AfterAll, Before } from "@cucumber/cucumber";
import { execFileSync, spawn } from "node:child_process";
import { homedir } from "node:os";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const heartBuild = join(dirname(fileURLToPath(import.meta.url)), "../../../../heart");
const javaHome = process.env.ARTIE_JAVA_HOME ?? join(homedir(), ".sdkman/candidates/java/21.0.12-graal");
const HOSTS = new Set(["build.test"]);
const realFetch = globalThis.fetch;
let process_;
let base;

BeforeAll({ timeout: 300_000 }, async function () {
    execFileSync("./gradlew", ["-q", ":heart-sims:installSims"], { cwd: heartBuild, env: { ...process.env, JAVA_HOME: javaHome }, stdio: "ignore" });
    process_ = spawn(join(javaHome, "bin/java"), ["-cp", "heart-sims/build/sims/lib/*", "com.artie.heart.sims.MainKt", "0"], { cwd: heartBuild });
    const port = await new Promise((resolve, reject) => {
        process_.stdout.on("data", (chunk) => {
            const found = /listening on (\d+)/.exec(String(chunk));
            if (found) resolve(found[1]);
        });
        process_.on("exit", (code) => reject(new Error(`heart-sims exited with ${code}`)));
    });
    base = `http://127.0.0.1:${port}`;
    globalThis.fetch = (input, init = {}) => {
        const url = new URL(String(input));
        if (!HOSTS.has(url.host)) return realFetch(input, init);
        const headers = new Headers(init.headers);
        headers.set("X-Sim-Host", url.host);
        return realFetch(`${base}${url.pathname}${url.search}`, { ...init, headers });
    };
});

AfterAll(function () {
    globalThis.fetch = realFetch;
    process_?.kill();
});

Before(async function () {
    await control("POST", "/_sim/reset");
});

async function control(method, path, body) {
    const response = await realFetch(`${base}${path}`, { method, body });
    if (!response.ok) throw new Error(`${method} ${path}: ${response.status}`);
    return response;
}

/** The simulator, as steps use it: set up a service, make it misbehave, and read what it received. */
export const sims = {
    configureJob: (name, parameters) => control("PUT", `/_sim/build/jobs/${name}`, parameters),
    answerAct: (host, act, status, body) => control("POST", `/_sim/${host}/acts/${act}/answer`, JSON.stringify({ status, body })),
    declareSchema: (host, form, schema) => control("PUT", `/_sim/${host}/forms/${encodeURIComponent(form)}/schema`, schema),
    log: async () => (await control("GET", "/_sim/log")).json(),
};
