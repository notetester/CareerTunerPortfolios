import { readFile } from "node:fs/promises";

import {
  assertAssetLinksDocumentMatches,
  assertSigningFingerprintMatches,
} from "./assetlinks-core.mjs";

try {
  const fingerprint = assertSigningFingerprintMatches(
    process.env.ANDROID_SIGNING_SHA256_CERT_FINGERPRINT,
    process.env.ANDROID_APP_LINK_SHA256_CERT_FINGERPRINTS,
  );
  if (process.env.ANDROID_ASSETLINKS_FILE) {
    const document = JSON.parse(await readFile(process.env.ANDROID_ASSETLINKS_FILE, "utf8"));
    assertAssetLinksDocumentMatches(document, fingerprint);
  }
  console.log("APK 서명 지문과 verified App Link 지문이 일치합니다.");
} catch (error) {
  console.error(error instanceof Error ? error.message : error);
  process.exitCode = 1;
}
