import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
const { createCapacitorConfig } = require("./scripts/capacitor-config-policy.cjs");
const config = createCapacitorConfig(process.env);

// Capacitor CLI loads JavaScript config with require(). Named exports keep the
// resulting module namespace shaped exactly like CapacitorConfig on Node 22+.
export const appId = config.appId;
export const appName = config.appName;
export const webDir = config.webDir;
export const server = config.server;
export const android = config.android;
