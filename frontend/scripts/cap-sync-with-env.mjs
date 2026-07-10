import { spawnSync } from "node:child_process";
import { readFileSync, writeFileSync } from "node:fs";
import { resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const configPath = resolve(root, "capacitor.config.json");
const capBin = resolve(root, "node_modules", "@capacitor", "cli", "bin", "capacitor");
const args = ["sync", ...process.argv.slice(2)];
const original = readFileSync(configPath, "utf8");

try {
  if (process.env.CAP_SERVER_URL) {
    const config = JSON.parse(original);
    config.server = {
      ...(config.server ?? {}),
      url: process.env.CAP_SERVER_URL,
    };
    writeFileSync(configPath, `${JSON.stringify(config, null, 2)}\n`, "utf8");
  }

  const result = spawnSync(process.execPath, [capBin, ...args], {
    cwd: root,
    env: process.env,
    shell: process.platform === "win32",
    stdio: "inherit",
  });
  process.exitCode = result.status ?? 1;
} finally {
  writeFileSync(configPath, original, "utf8");
}
