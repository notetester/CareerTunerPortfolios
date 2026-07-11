import { mkdir, readFile, rename, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { pathToFileURL } from "node:url";

import {
  patchAssociatedDomainsEntitlements,
  patchInfoPlistCustomScheme,
  patchInfoPlistUsageDescriptions,
  patchXcodeProjectAssociatedDomains,
} from "./ios-universal-links-core.mjs";

async function atomicWrite(path, contents) {
  const temporary = `${path}.${process.pid}.tmp`;
  await mkdir(dirname(path), { recursive: true });
  await writeFile(temporary, contents, { encoding: "utf8", flag: "wx" });
  await rename(temporary, path);
}

async function readOptionalUtf8(path) {
  try {
    return await readFile(path, "utf8");
  } catch (error) {
    if (error?.code === "ENOENT") {
      return `<?xml version="1.0" encoding="UTF-8"?>\n<plist version="1.0">\n<dict>\n</dict>\n</plist>\n`;
    }
    throw error;
  }
}

export async function configureIosAssociatedDomains({ cwd = process.cwd() } = {}) {
  const iosProjectRoot = resolve(cwd, "ios", "App");
  const projectPath = resolve(iosProjectRoot, "App.xcodeproj", "project.pbxproj");
  const entitlementsPath = resolve(iosProjectRoot, "App", "App.entitlements");
  const infoPlistPath = resolve(iosProjectRoot, "App", "Info.plist");
  const [projectSource, infoPlistSource, entitlementsSource] = await Promise.all([
    readFile(projectPath, "utf8"),
    readFile(infoPlistPath, "utf8"),
    readOptionalUtf8(entitlementsPath),
  ]);
  const patchedProject = patchXcodeProjectAssociatedDomains(projectSource);
  const patchedInfoPlist = patchInfoPlistUsageDescriptions(
    patchInfoPlistCustomScheme(infoPlistSource),
  );
  const patchedEntitlements = patchAssociatedDomainsEntitlements(entitlementsSource);

  if (patchedProject !== projectSource) await atomicWrite(projectPath, patchedProject);
  if (patchedInfoPlist !== infoPlistSource) await atomicWrite(infoPlistPath, patchedInfoPlist);
  if (patchedEntitlements !== entitlementsSource) await atomicWrite(entitlementsPath, patchedEntitlements);
  return { entitlementsPath, infoPlistPath, projectPath };
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  configureIosAssociatedDomains()
    .then(({ entitlementsPath, infoPlistPath }) => {
      console.log(`iOS 네이티브 링크 설정 완료: ${entitlementsPath}, ${infoPlistPath}`);
    })
    .catch((error) => {
      console.error(`iOS Associated Domains 설정 실패: ${error instanceof Error ? error.message : error}`);
      process.exitCode = 1;
    });
}
