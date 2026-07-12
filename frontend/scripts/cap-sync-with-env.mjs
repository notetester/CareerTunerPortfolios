import { spawnSync } from "node:child_process";
import { existsSync, readFileSync } from "node:fs";
import { createRequire } from "node:module";
import { resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const require = createRequire(import.meta.url);
const {
  assertGeneratedCapacitorConfig,
  createCapacitorConfig,
} = require("./capacitor-config-policy.cjs");

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const capBin = resolve(root, "node_modules", "@capacitor", "cli", "bin", "capacitor");

function parseArgs(rawArgs) {
  const capArgs = [];
  let cliMode;
  for (let index = 0; index < rawArgs.length; index += 1) {
    const arg = rawArgs[index];
    if (arg === "--mode") {
      cliMode = rawArgs[index + 1];
      index += 1;
    } else if (arg.startsWith("--mode=")) {
      cliMode = arg.slice("--mode=".length);
    } else {
      capArgs.push(arg);
    }
  }
  return { capArgs, cliMode };
}

function selectedPlatforms(capArgs) {
  const explicit = capArgs.filter((arg) => arg === "android" || arg === "ios");
  return explicit.length > 0 ? [...new Set(explicit)] : ["android", "ios"];
}

function generatedConfigPath(platform) {
  return platform === "android"
    ? resolve(root, "android", "app", "src", "main", "assets", "capacitor.config.json")
    : resolve(root, "ios", "App", "App", "capacitor.config.json");
}

function verifyGeneratedConfig(platform, mode, expectedConfig, explicitlySelected) {
  const configPath = generatedConfigPath(platform);
  if (!existsSync(configPath)) {
    if (explicitlySelected) {
      throw new Error(`${platform} 동기화 뒤 생성 설정을 찾지 못했습니다: ${configPath}`);
    }
    return;
  }

  const generated = JSON.parse(readFileSync(configPath, "utf8"));
  assertGeneratedCapacitorConfig(generated, { mode });
  const expectedUrl = expectedConfig.server?.url;
  const actualUrl = generated.server?.url;
  if (actualUrl !== expectedUrl) {
    throw new Error(`${platform} 생성 server.url이 요청한 프로필과 다릅니다.`);
  }
  if (generated.server?.cleartext !== expectedConfig.server?.cleartext) {
    throw new Error(`${platform} 생성 cleartext가 요청한 프로필과 다릅니다.`);
  }
  console.log(`✓ ${platform} Capacitor ${mode} 네트워크 정책 확인`);
}

try {
  const { capArgs, cliMode } = parseArgs(process.argv.slice(2));
  const mode = cliMode || process.env.CAP_SYNC_MODE || "release";
  const env = { ...process.env, CAP_SYNC_MODE: mode };
  const expectedConfig = createCapacitorConfig(env);
  const platforms = selectedPlatforms(capArgs);
  const explicitlySelected = capArgs.some((arg) => arg === "android" || arg === "ios");

  console.log(`Capacitor sync profile: ${mode}`);
  const result = spawnSync(process.execPath, [capBin, "sync", ...capArgs], {
    cwd: root,
    env,
    shell: false,
    stdio: "inherit",
  });
  if (result.error) throw result.error;
  if (result.status !== 0) process.exit(result.status ?? 1);

  for (const platform of platforms) {
    verifyGeneratedConfig(platform, mode, expectedConfig, explicitlySelected);
  }
} catch (error) {
  console.error(`Capacitor sync 차단: ${error instanceof Error ? error.message : String(error)}`);
  process.exit(1);
}
