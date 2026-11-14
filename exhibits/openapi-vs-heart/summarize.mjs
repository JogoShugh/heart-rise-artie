// Reads the suite's Cucumber JSON reports in results/ and prints one matrix:
// per release and client, each scenario's result and, for a failure, its first line.
import { readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";

const dir = process.argv[2];
const runs = readdirSync(dir).filter((f) => f.includes("--") && f.endsWith(".json")).sort().map((file) => {
    const [release, client] = file.replace(".json", "").split("--");
    const scenarios = JSON.parse(readFileSync(join(dir, file), "utf8")).flatMap((feature) =>
        feature.elements.map((scenario) => {
            const steps = scenario.steps.filter((s) => s.keyword.trim() !== "Before" && s.keyword.trim() !== "After");
            const failed = steps.find((s) => s.result.status === "failed");
            return {
                scenario: scenario.name,
                passed: steps.every((s) => s.result.status === "passed"),
                ...(failed && { failedAt: failed.name, why: failed.result.error_message?.split("\n")[0] }),
            };
        }));
    return { release, client, passed: scenarios.filter((s) => s.passed).length, of: scenarios.length, scenarios };
});
console.log(JSON.stringify(runs, null, 2));
