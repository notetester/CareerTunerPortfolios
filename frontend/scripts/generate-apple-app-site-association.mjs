import { mkdir, rename, writeFile } from "node:fs/promises";
import { dirname, isAbsolute, relative, resolve, sep } from "node:path";
import { pathToFileURL } from "node:url";

import { createAppleAppSiteAssociation } from "./ios-universal-links-core.mjs";

export function parseAppleAppSiteAssociationArgs(argv, cwd = process.cwd()) {
  const outputIndex = argv.indexOf("--output");
  const allowEmpty = argv.includes("--allow-empty");
  const expectedLength = allowEmpty ? 3 : 2;
  if (outputIndex < 0 || !argv[outputIndex + 1] || argv.length !== expectedLength) {
    throw new Error(
      "사용법: node scripts/generate-apple-app-site-association.mjs [--allow-empty] --output dist/.well-known/apple-app-site-association",
    );
  }

  const distRoot = resolve(cwd, "dist");
  const output = resolve(cwd, argv[outputIndex + 1]);
  const fromDist = relative(distRoot, output);
  if (isAbsolute(fromDist) || fromDist === ".." || fromDist.startsWith(`..${sep}`)) {
    throw new Error("apple-app-site-association 출력은 dist/ 아래만 허용됩니다.");
  }
  return { allowEmpty, output };
}

export async function generateAppleAppSiteAssociation({
  argv = process.argv.slice(2),
  env = process.env,
} = {}) {
  const { allowEmpty, output } = parseAppleAppSiteAssociationArgs(argv);
  const document = createAppleAppSiteAssociation(env.IOS_APP_LINK_TEAM_IDS, { allowEmpty });
  const temporary = `${output}.${process.pid}.tmp`;

  await mkdir(dirname(output), { recursive: true });
  await writeFile(temporary, `${JSON.stringify(document, null, 2)}\n`, { encoding: "utf8", flag: "wx" });
  await rename(temporary, output);
  return { configured: document.applinks.details.length > 0, output };
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  generateAppleAppSiteAssociation()
    .then(({ configured, output }) => {
      if (!configured) {
        console.warn("IOS_APP_LINK_TEAM_IDS 미설정: iOS Universal Link를 거부하는 빈 AASA를 생성했습니다.");
      }
      console.log(`apple-app-site-association 생성 완료: ${output}`);
    })
    .catch((error) => {
      console.error(error instanceof Error ? error.message : error);
      process.exitCode = 1;
    });
}

