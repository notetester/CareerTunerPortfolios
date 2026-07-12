import { mkdir, rename, writeFile } from "node:fs/promises";
import { dirname, isAbsolute, relative, resolve, sep } from "node:path";
import { pathToFileURL } from "node:url";

import { createAssetLinksDocument } from "./assetlinks-core.mjs";

export function resolveAssetLinksOutputPath(argv, cwd = process.cwd()) {
  const index = argv.indexOf("--output");
  if (index < 0 || !argv[index + 1] || argv.length !== index + 2) {
    throw new Error("사용법: node scripts/generate-assetlinks.mjs --output dist/.well-known/assetlinks.json");
  }

  const distRoot = resolve(cwd, "dist");
  const output = resolve(cwd, argv[index + 1]);
  const fromDist = relative(distRoot, output);
  if (isAbsolute(fromDist) || fromDist === ".." || fromDist.startsWith(`..${sep}`)) {
    throw new Error("assetlinks.json 출력은 dist/ 아래만 허용됩니다.");
  }
  return output;
}

export async function generateAssetLinks({ argv = process.argv.slice(2), env = process.env } = {}) {
  const output = resolveAssetLinksOutputPath(argv);
  const document = createAssetLinksDocument(env.ANDROID_APP_LINK_SHA256_CERT_FINGERPRINTS);
  const temporary = `${output}.${process.pid}.tmp`;

  await mkdir(dirname(output), { recursive: true });
  await writeFile(temporary, `${JSON.stringify(document, null, 2)}\n`, { encoding: "utf8", flag: "wx" });
  await rename(temporary, output);
  return output;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  generateAssetLinks()
    .then((output) => console.log(`assetlinks.json 생성 완료: ${output}`))
    .catch((error) => {
      console.error(error instanceof Error ? error.message : error);
      process.exitCode = 1;
    });
}
