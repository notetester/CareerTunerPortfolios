import assert from "node:assert/strict";
import { createHash, webcrypto } from "node:crypto";
import { readFile } from "node:fs/promises";
import test from "node:test";

import {
  createNativeOAuthExchangeCoordinator,
  createPkcePair,
  nativeOAuthPendingKey,
  readPendingNativeOAuth,
  savePendingNativeOAuth,
  validateAuthorizationUrl,
} from "../src/platform/nativeOAuthCore.mjs";
import {
  isNativeOAuthCallbackPath,
  isNativeSocialLinkCallbackPath,
  toAppPath,
} from "../src/platform/deepLinkCore.mjs";
import { applyFrontendClientHeader, FRONTEND_CLIENT_HEADER } from "../src/app/lib/apiClientCore.mjs";
import {
  ANDROID_APP_LINK_PACKAGE,
  ANDROID_APP_LINK_RELATION,
  assertAssetLinksDocumentMatches,
  assertSigningFingerprintMatches,
  createAssetLinksDocument,
  parseSha256CertificateFingerprints,
} from "./assetlinks-core.mjs";
import { resolveAssetLinksOutputPath } from "./generate-assetlinks.mjs";
import { parseAppleAppSiteAssociationArgs } from "./generate-apple-app-site-association.mjs";
import {
  IOS_APP_BUNDLE_ID,
  IOS_ASSOCIATED_DOMAINS_ENTITLEMENT,
  IOS_CUSTOM_URL_SCHEME,
  IOS_USAGE_DESCRIPTIONS,
  createAppleAppSiteAssociation,
  createAssociatedDomainsEntitlements,
  parseAppleTeamIds,
  patchAssociatedDomainsEntitlements,
  patchInfoPlistCustomScheme,
  patchInfoPlistUsageDescriptions,
  patchXcodeProjectAssociatedDomains,
} from "./ios-universal-links-core.mjs";

class MemoryStorage {
  #values = new Map();
  getItem(key) { return this.#values.get(key) ?? null; }
  setItem(key, value) { this.#values.set(key, String(value)); }
  removeItem(key) { this.#values.delete(key); }
}

test("PKCE verifier는 충분히 길고 challenge는 SHA-256 base64url이다", async () => {
  const first = await createPkcePair(webcrypto);
  const second = await createPkcePair(webcrypto);
  assert.match(first.verifier, /^[A-Za-z0-9_-]{86}$/);
  assert.match(first.challenge, /^[A-Za-z0-9_-]{43}$/);
  assert.notEqual(first.verifier, second.verifier);
  assert.equal(
    first.challenge,
    createHash("sha256").update(first.verifier).digest("base64url"),
  );
});

test("pending verifier는 지원 제공자만 복원하고 만료값은 폐기한다", () => {
  const storage = new MemoryStorage();
  const now = Date.now();
  const pending = { provider: "google", verifier: "v".repeat(64), createdAt: now };
  savePendingNativeOAuth(storage, pending);
  assert.ok(storage.getItem(nativeOAuthPendingKey));
  assert.deepEqual(readPendingNativeOAuth(storage, now + 1_000), pending);
  assert.ok(storage.getItem(nativeOAuthPendingKey));

  savePendingNativeOAuth(storage, { ...pending, provider: "naver", createdAt: now - 11 * 60 * 1000 });
  assert.equal(readPendingNativeOAuth(storage, now), null);
  assert.equal(storage.getItem(nativeOAuthPendingKey), null);
});

test("동일 handoffCode 동시 콜백은 한 번만 교환하고 성공 후 verifier를 제거한다", async () => {
  const storage = new MemoryStorage();
  const now = Date.now();
  const verifier = "v".repeat(64);
  const code = "C".repeat(43);
  savePendingNativeOAuth(storage, { provider: "kakao", verifier, createdAt: now });

  let resolveExchange;
  const calls = [];
  const coordinator = createNativeOAuthExchangeCoordinator(
    storage,
    (handoffCode, handoffVerifier) => {
      calls.push({ handoffCode, handoffVerifier });
      return new Promise((resolve) => { resolveExchange = resolve; });
    },
  );

  const first = coordinator(code, now + 1_000);
  const duplicate = coordinator(code, now + 1_000);
  assert.equal(first, duplicate);
  await Promise.resolve();
  assert.deepEqual(calls, [{ handoffCode: code, handoffVerifier: verifier }]);
  await assert.rejects(coordinator("D".repeat(43), now + 1_000), /다른 소셜 로그인/);
  assert.ok(storage.getItem(nativeOAuthPendingKey));

  resolveExchange({ accessToken: "access" });
  assert.deepEqual(await first, { accessToken: "access" });
  assert.equal(storage.getItem(nativeOAuthPendingKey), null);
});

test("네트워크와 5xx 실패는 verifier를 보존해 TTL 안 재시도한다", async () => {
  const storage = new MemoryStorage();
  const now = Date.now();
  const verifier = "v".repeat(64);
  const code = "N".repeat(43);
  savePendingNativeOAuth(storage, { provider: "naver", verifier, createdAt: now });

  let attempt = 0;
  const coordinator = createNativeOAuthExchangeCoordinator(
    storage,
    async () => {
      attempt += 1;
      if (attempt === 1) throw new TypeError("network disconnected");
      if (attempt === 2) throw Object.assign(new Error("backend unavailable"), { status: 503 });
      return { accessToken: "retried" };
    },
  );

  await assert.rejects(coordinator(code, now + 1_000), /network disconnected/);
  assert.ok(storage.getItem(nativeOAuthPendingKey));
  await assert.rejects(coordinator(code, now + 2_000), /backend unavailable/);
  assert.ok(storage.getItem(nativeOAuthPendingKey));
  assert.deepEqual(await coordinator(code, now + 3_000), { accessToken: "retried" });
  assert.equal(storage.getItem(nativeOAuthPendingKey), null);
});

test("401은 공격자가 주입한 가짜 code일 수 있으므로 verifier를 보존한다", async () => {
  const storage = new MemoryStorage();
  const now = Date.now();
  const fakeCode = "U".repeat(43);
  const legitimateCode = "L".repeat(43);
  savePendingNativeOAuth(storage, { provider: "google", verifier: "v".repeat(64), createdAt: now });

  const unauthorized = Object.assign(new Error("invalid native handoff"), { status: 401 });
  const coordinator = createNativeOAuthExchangeCoordinator(
    storage,
    async (handoffCode) => {
      if (handoffCode === fakeCode) throw unauthorized;
      return { accessToken: "legitimate" };
    },
  );

  await assert.rejects(coordinator(fakeCode, now + 1_000), /invalid native handoff/);
  assert.ok(storage.getItem(nativeOAuthPendingKey));
  assert.deepEqual(await coordinator(legitimateCode, now + 2_000), { accessToken: "legitimate" });
  assert.equal(storage.getItem(nativeOAuthPendingKey), null);
});

test("교환 중 취소·새 로그인으로 pending이 바뀌면 이전 token 결과를 폐기한다", async () => {
  const storage = new MemoryStorage();
  const now = Date.now();
  const oldVerifier = "o".repeat(64);
  const newPending = { provider: "google", verifier: "n".repeat(64), createdAt: now + 1_000 };
  savePendingNativeOAuth(storage, { provider: "kakao", verifier: oldVerifier, createdAt: now });

  let resolveExchange;
  const coordinator = createNativeOAuthExchangeCoordinator(
    storage,
    () => new Promise((resolve) => { resolveExchange = resolve; }),
  );
  const staleExchange = coordinator("S".repeat(43), now + 500);
  await Promise.resolve();
  savePendingNativeOAuth(storage, newPending);
  resolveExchange({ accessToken: "stale" });

  await assert.rejects(staleExchange, /취소되었거나 새 요청/);
  assert.deepEqual(readPendingNativeOAuth(storage, now + 2_000), newPending);
});

test("제공자 authorizationUrl은 공식 HTTPS endpoint만 허용한다", () => {
  assert.equal(
    validateAuthorizationUrl("kakao", "https://kauth.kakao.com/oauth/authorize?client_id=x"),
    "https://kauth.kakao.com/oauth/authorize?client_id=x",
  );
  assert.equal(
    validateAuthorizationUrl("naver", "https://nid.naver.com/oauth2.0/authorize?client_id=x"),
    "https://nid.naver.com/oauth2.0/authorize?client_id=x",
  );
  assert.equal(
    validateAuthorizationUrl("google", "https://accounts.google.com/o/oauth2/v2/auth?client_id=x"),
    "https://accounts.google.com/o/oauth2/v2/auth?client_id=x",
  );
  assert.equal(validateAuthorizationUrl("kakao", "https://evil.example/oauth/authorize"), null);
  assert.equal(validateAuthorizationUrl("naver", "http://nid.naver.com/oauth2.0/authorize"), null);
  assert.equal(validateAuthorizationUrl("google", "https://accounts.google.com.evil.example/o/oauth2/v2/auth"), null);
  assert.equal(validateAuthorizationUrl("google", "https://accounts.google.com/o/oauth2/auth"), null);
});

test("딥링크는 manifest의 canonical host와 안전한 앱 경로만 허용한다", () => {
  const code = "A".repeat(43);
  assert.equal(toAppPath(`https://careertuner.kro.kr/auth/callback?handoffCode=${code}`), `/auth/callback?handoffCode=${code}`);
  assert.equal(toAppPath("https://careertuner.kro.kr/auth/callback?error=social_login_cancelled"), "/auth/callback?error=social_login_cancelled");
  assert.equal(toAppPath(`careertuner://auth/callback?handoffCode=${code}`), null);
  assert.equal(toAppPath(`careertuner://auth/%63allback?handoffCode=${code}`), null);
  assert.equal(toAppPath("careertuner://applications/3"), "/applications/3");
  assert.equal(toAppPath("https://careertuner.kro.kr/community?tab=hot#top"), null);
  assert.equal(toAppPath("https://careertuner.kro.kr/auth/reset-password?token=email-token"), null);
  assert.equal(toAppPath("http://careertuner.kro.kr/applications"), null);
  assert.equal(toAppPath("https://careertuner.kr/applications"), null);
  assert.equal(toAppPath("https://www.careertuner.kr/applications"), null);
  assert.equal(toAppPath("careertuner://unknown/path"), null);
  assert.equal(toAppPath("careertuner://applications/%2e%2e/admin"), null);
  assert.equal(toAppPath("careertuner://applications/%2Fadmin"), null);
  assert.equal(toAppPath("careertuner://applications/%zz"), null);
  assert.equal(toAppPath("https://careertuner.kro.kr/auth/callback?accessToken=secret"), null);
  assert.equal(toAppPath(`https://careertuner.kro.kr/auth/callback?handoffCode=${code}&handoffCode=${code}`), null);
  assert.equal(toAppPath("https://careertuner.kro.kr/auth/callback?handoffCode=short"), null);
  assert.equal(toAppPath(`https://careertuner.kro.kr/auth/callback/extra?handoffCode=${code}`), null);
  assert.equal(toAppPath("https://careertuner.kro.kr/profile/detail?socialLinked=KAKAO"), "/profile/detail?socialLinked=KAKAO");
  assert.equal(toAppPath("https://careertuner.kro.kr/profile/detail?socialLinked=NAVER&socialMock=1"), "/profile/detail?socialLinked=NAVER&socialMock=1");
  assert.equal(toAppPath("https://careertuner.kro.kr/profile/detail?socialLinkError=social_login_cancelled"), "/profile/detail?socialLinkError=social_login_cancelled");
  assert.equal(toAppPath("https://careertuner.kro.kr/profile/social-callback?socialLinked=KAKAO"), null);
  assert.equal(toAppPath("careertuner://profile/detail"), "/profile/detail");
  assert.equal(toAppPath("careertuner://profile/detail?socialLinked=KAKAO"), null);
  assert.equal(toAppPath("https://careertuner.kro.kr/profile/detail"), null);
  assert.equal(toAppPath("https://careertuner.kro.kr/profile/detail?socialMock=1"), null);
  assert.equal(toAppPath("https://careertuner.kro.kr/profile/detail?socialLinked=kakao"), null);
  assert.equal(toAppPath("https://careertuner.kro.kr/profile/detail?socialLinked=KAKAO&socialLinkError=social_login_failed"), null);
  assert.equal(toAppPath("https://careertuner.kro.kr/profile/detail?socialLinkError=arbitrary"), null);
  assert.equal(toAppPath("https://careertuner.kro.kr/profile/detail?socialLinked=KAKAO&next=/admin"), null);
  assert.equal(toAppPath("https://careertuner.kro.kr/profile/detail?socialLinked=KAKAO&socialLinked=NAVER"), null);
  assert.equal(toAppPath("https://careertuner.kro.kr/profile/detail/extra?socialLinked=KAKAO"), null);
  assert.equal(toAppPath("https://careertuner.kro.kr/profileevil?socialLinked=KAKAO"), null);
  assert.equal(isNativeOAuthCallbackPath(`/auth/callback?handoffCode=${code}`), true);
  assert.equal(isNativeOAuthCallbackPath("/applications/3"), false);
  assert.equal(isNativeSocialLinkCallbackPath("/profile/detail?socialLinked=GOOGLE"), true);
  assert.equal(isNativeSocialLinkCallbackPath("/profile/detail?socialLinkError=social_login_failed"), true);
  assert.equal(isNativeSocialLinkCallbackPath("/profile/detail?socialLinkError=arbitrary"), false);
});

test("네이티브 API 헤더는 자동 추가하되 호출자가 지정한 값을 덮어쓰지 않는다", () => {
  const nativeHeaders = applyFrontendClientHeader(new Headers(), true);
  assert.equal(nativeHeaders.get(FRONTEND_CLIENT_HEADER), "native");

  const explicit = new Headers({ [FRONTEND_CLIENT_HEADER]: "integration-test" });
  applyFrontendClientHeader(explicit, true);
  assert.equal(explicit.get(FRONTEND_CLIENT_HEADER), "integration-test");

  const webHeaders = applyFrontendClientHeader(new Headers(), false);
  assert.equal(webHeaders.has(FRONTEND_CLIENT_HEADER), false);
});

test("assetlinks는 실제 SHA-256 인증서 지문이 없거나 잘못되면 생성하지 않는다", () => {
  assert.throws(() => parseSha256CertificateFingerprints(""), /비어/);
  assert.throws(() => parseSha256CertificateFingerprints("AA:BB"), /형식/);
  assert.throws(
    () => resolveAssetLinksOutputPath(["--output", "../assetlinks.json"], "/workspace/frontend"),
    /dist\/ 아래/,
  );
});

test("assetlinks는 소문자 지문을 정규화·중복 제거해 고정 패키지에만 주입한다", () => {
  const fingerprint = Array.from({ length: 32 }, () => "ab").join(":");
  const document = createAssetLinksDocument(`${fingerprint},${fingerprint}`);

  assert.deepEqual(document, [
    {
      relation: [ANDROID_APP_LINK_RELATION],
      target: {
        namespace: "android_app",
        package_name: ANDROID_APP_LINK_PACKAGE,
        sha256_cert_fingerprints: [fingerprint.toUpperCase()],
      },
    },
  ]);
  assert.equal(assertSigningFingerprintMatches(fingerprint, fingerprint), fingerprint.toUpperCase());
  assert.equal(assertAssetLinksDocumentMatches(document, fingerprint), fingerprint.toUpperCase());
  assert.throws(
    () => assertSigningFingerprintMatches(Array.from({ length: 32 }, () => "CD").join(":"), fingerprint),
    /일치하지 않습니다/,
  );
  assert.throws(
    () => assertAssetLinksDocumentMatches([], fingerprint),
    /서명 지문이 없습니다/,
  );
});

test("AASA는 등록된 Apple Team ID와 네이티브 반환 exact path만 공개한다", () => {
  assert.throws(() => parseAppleTeamIds(""), /비어/);
  assert.throws(() => parseAppleTeamIds("SHORT"), /10자리/);
  assert.deepEqual(parseAppleTeamIds("abcde12345,ABCDE12345"), ["ABCDE12345"]);
  assert.throws(
    () => parseAppleAppSiteAssociationArgs(["--output", "../apple-app-site-association"], "/workspace/frontend"),
    /dist\/ 아래/,
  );

  const document = createAppleAppSiteAssociation("abcde12345,FGHIJ67890");
  assert.deepEqual(document, {
    applinks: {
      details: [
        {
          appIDs: [
            `ABCDE12345.${IOS_APP_BUNDLE_ID}`,
            `FGHIJ67890.${IOS_APP_BUNDLE_ID}`,
          ],
          components: [
            { "/": "/auth/callback", comment: "CareerTuner native OAuth handoff result" },
            { "/": "/profile/detail", comment: "CareerTuner native social account link result" },
          ],
        },
      ],
    },
  });
  assert.deepEqual(createAppleAppSiteAssociation("", { allowEmpty: true }), {
    applinks: { details: [] },
  });
  assert.doesNotMatch(JSON.stringify(document), /\*/);
});

test("재생성된 iOS App target Debug/Release에 Associated Domains를 멱등 적용한다", () => {
  const configuration = (id, name, extra = "") => `\t\t${id} /* ${name} */ = {
\t\t\tisa = XCBuildConfiguration;
\t\t\tbuildSettings = {
\t\t\t\tCODE_SIGN_STYLE = Automatic;
${extra}\t\t\t\tPRODUCT_BUNDLE_IDENTIFIER = ${IOS_APP_BUNDLE_ID};
\t\t\t};
\t\t\tname = ${name};
\t\t};`;
  const project = `/* Begin XCBuildConfiguration section */
${configuration("AAAAAAAAAAAAAAAAAAAAAAAA", "Debug")}
${configuration("BBBBBBBBBBBBBBBBBBBBBBBB", "Release")}
/* End XCBuildConfiguration section */`;
  const patched = patchXcodeProjectAssociatedDomains(project);

  assert.equal((patched.match(/CODE_SIGN_ENTITLEMENTS = App\/App\.entitlements;/g) ?? []).length, 2);
  assert.equal(patchXcodeProjectAssociatedDomains(patched), patched);
  assert.match(createAssociatedDomainsEntitlements(), /com\.apple\.developer\.associated-domains/);
  assert.match(createAssociatedDomainsEntitlements(), new RegExp(IOS_ASSOCIATED_DOMAINS_ENTITLEMENT));

  const existingEntitlements = `<?xml version="1.0" encoding="UTF-8"?>
<plist version="1.0">
<dict>
\t<key>aps-environment</key>
\t<string>development</string>
</dict>
</plist>
`;
  const patchedEntitlements = patchAssociatedDomainsEntitlements(existingEntitlements);
  assert.match(patchedEntitlements, /<key>aps-environment<\/key>/);
  assert.match(patchedEntitlements, new RegExp(IOS_ASSOCIATED_DOMAINS_ENTITLEMENT));
  assert.equal(patchAssociatedDomainsEntitlements(patchedEntitlements), patchedEntitlements);

  const conflicting = project.replace(
    "CODE_SIGN_STYLE = Automatic;",
    "CODE_SIGN_ENTITLEMENTS = Other.entitlements;\n\t\t\t\tCODE_SIGN_STYLE = Automatic;",
  );
  assert.throws(() => patchXcodeProjectAssociatedDomains(conflicting), /덮어쓸 수 없습니다/);
});

test("재생성된 iOS Info.plist에 careertuner custom scheme을 멱등 등록한다", () => {
  const emptyPlist = `<?xml version="1.0" encoding="UTF-8"?>
<plist version="1.0">
<dict>
\t<key>CFBundleIdentifier</key>
\t<string>$(PRODUCT_BUNDLE_IDENTIFIER)</string>
</dict>
</plist>
`;
  const patched = patchInfoPlistCustomScheme(emptyPlist);
  assert.match(patched, /<key>CFBundleURLTypes<\/key>/);
  assert.match(patched, /<key>CFBundleURLSchemes<\/key>/);
  assert.match(patched, new RegExp(`<string>${IOS_CUSTOM_URL_SCHEME}</string>`));
  assert.equal(patchInfoPlistCustomScheme(patched), patched);
  assert.equal((patched.match(/<key>CFBundleURLTypes<\/key>/g) ?? []).length, 1);
  assert.equal((patched.match(/<string>careertuner<\/string>/g) ?? []).length, 1);

  const otherScheme = emptyPlist.replace(
    "</dict>",
    `\t<key>CFBundleURLTypes</key>
\t<array>
\t\t<dict>
\t\t\t<key>CFBundleURLName</key>
\t\t\t<string>careertuner</string>
\t\t\t<key>CFBundleURLSchemes</key>
\t\t\t<array><string>other-app</string></array>
\t\t</dict>
\t</array>
</dict>`,
  );
  const appended = patchInfoPlistCustomScheme(otherScheme);
  assert.match(appended, /<string>other-app<\/string>/);
  assert.equal((appended.match(/<string>careertuner<\/string>/g) ?? []).length, 2);
  assert.match(appended, /<key>CFBundleURLSchemes<\/key>\s*<array>\s*<string>careertuner<\/string>/);
  assert.equal((appended.match(/<key>CFBundleURLTypes<\/key>/g) ?? []).length, 1);

  const usagePatched = patchInfoPlistUsageDescriptions(
    patched.replace(
      "</dict>",
      "\t<key>NSCameraUsageDescription</key>\n\t<string>원본 영상은 채점 후 즉시 폐기됩니다.</string>\n</dict>",
    ),
  );
  for (const [key, description] of Object.entries(IOS_USAGE_DESCRIPTIONS)) {
    assert.match(usagePatched, new RegExp(`<key>${key}</key>`));
    assert.match(usagePatched, new RegExp(description));
  }
  assert.doesNotMatch(usagePatched, /즉시 폐기/);
  assert.equal(patchInfoPlistUsageDescriptions(usagePatched), usagePatched);
});

test("Android manifest는 네이티브 반환 exact path만 verified App Link로 선언한다", async () => {
  const manifest = await readFile(
    new URL("../android/app/src/main/AndroidManifest.xml", import.meta.url),
    "utf8",
  );
  const verifiedFilters = [...manifest.matchAll(/<intent-filter android:autoVerify="true">[\s\S]*?<\/intent-filter>/g)]
    .map((match) => match[0]);
  const authFilter = verifiedFilters.find((filter) => filter.includes('android:path="/auth/callback"')) ?? "";
  const socialLinkFilter = verifiedFilters.find((filter) => filter.includes('android:path="/profile/detail"')) ?? "";

  assert.equal(verifiedFilters.length, 2);
  for (const filter of [authFilter, socialLinkFilter]) {
    assert.match(filter, /android:scheme="https"/);
    assert.match(filter, /android:host="careertuner\.kro\.kr"/);
    assert.doesNotMatch(filter, /android:pathPrefix=/);
    assert.doesNotMatch(filter, /android:scheme="@string\/custom_url_scheme"/);
  }
});

test("iOS CI는 재생성 프로젝트에 Associated Domains를 적용한 뒤 빌드한다", async () => {
  const workflow = await readFile(
    new URL("../../.github/workflows/ios-build.yml", import.meta.url),
    "utf8",
  );
  const configureIndex = workflow.indexOf("npm run ios:configure-links");
  const buildIndex = workflow.indexOf("xcodebuild \\", configureIndex);

  assert.ok(configureIndex > 0);
  assert.ok(buildIndex > configureIndex);
  assert.match(workflow, /applinks:careertuner\.kro\.kr/);
  assert.match(workflow, /CODE_SIGN_ENTITLEMENTS = App\/App\.entitlements/);
  assert.match(workflow, /PlistBuddy.*CFBundleURLTypes/);
  assert.match(workflow, /grep -Fq 'careertuner'/);
  assert.match(workflow, /PlistBuddy.*NSCameraUsageDescription/);
  assert.match(workflow, /PlistBuddy.*NSMicrophoneUsageDescription/);
  assert.match(workflow, /PlistBuddy.*NSPhotoLibraryUsageDescription/);
  assert.match(workflow, /-project ios\/App\/App\.xcodeproj/);
  assert.doesNotMatch(workflow, /-workspace ios\/App\/App\.xcworkspace/);
});

test("웹 배포는 Android Asset Links와 iOS AASA를 함께 게시·검증한다", async () => {
  const workflow = await readFile(
    new URL("../../.github/workflows/deploy-web.yml", import.meta.url),
    "utf8",
  );

  assert.match(workflow, /vars\.IOS_APP_LINK_TEAM_IDS/);
  assert.match(workflow, /\.well-known\/apple-app-site-association/);
  assert.match(workflow, /content-type:\[\[:space:\]\]\*application\/json/);
  assert.match(workflow, /cmp frontend\/dist\/\.well-known\/apple-app-site-association/);
  const probeInstallIndex = workflow.indexOf('sudo install -D -m 0644 "$STAGING/$AASA_REL"');
  const fullSwapIndex = workflow.indexOf('sudo rsync -a --delete');
  assert.ok(probeInstallIndex > 0 && fullSwapIndex > probeInstallIndex);
  assert.match(workflow, /trap cleanup_aasa_probe EXIT/);
  assert.match(workflow, /AASA_PROBE_COMMITTED=1/);
  assert.match(workflow, /--resolve 'careertuner\.kro\.kr:443:127\.0\.0\.1'/);
  assert.match(workflow, /CAREERTUNER_ROOT=/);
  assert.match(workflow, /default_type application\/json/);
  assert.match(workflow, /ensure_aasa_nginx_location\.py/);
  assert.match(workflow, /sudo python3/);
  assert.doesNotMatch(
    workflow,
    /if \[ -n "\$\{IOS_APP_LINK_TEAM_IDS:-\}" \]; then[\s\S]*content-type:\[\[:space:\]\]\*application\/json/,
  );
  assert.doesNotMatch(workflow, /(?:sed|tee).*nginx/i);
});

test("네이티브 OAuth와 소셜 연결 App Link 모두 시스템 Browser를 닫고 라우팅한다", async () => {
  const adapterSource = await readFile(
    new URL("../src/platform/deepLink.ts", import.meta.url),
    "utf8",
  );

  assert.match(adapterSource, /isNativeOAuthCallbackPath\(path\) \|\| isNativeSocialLinkCallbackPath\(path\)/);
  assert.match(adapterSource, /void closeNativeOAuthBrowser\(\)/);
});

test("주입된 native error App Link는 pending verifier를 소모하지 않는다", async () => {
  const callbackSource = await readFile(
    new URL("../src/app/pages/AuthCallback.tsx", import.meta.url),
    "utf8",
  );

  assert.match(callbackSource, /params\.get\("error"\)/);
  assert.doesNotMatch(callbackSource, /cancelPendingNativeOAuth/);
});

test("Android release workflow는 secrets 없이 PR 계약을 검사하고 실제 산출물은 release 서명을 사용한다", async () => {
  const workflow = await readFile(
    new URL("../../.github/workflows/android-release.yml", import.meta.url),
    "utf8",
  );

  assert.match(workflow, /secrets\.ANDROID_DEMO_KEYSTORE_BASE64/);
  assert.match(workflow, /secrets\.ANDROID_DEMO_STORE_PASSWORD/);
  assert.match(workflow, /secrets\.ANDROID_DEMO_KEY_ALIAS/);
  assert.match(workflow, /secrets\.ANDROID_DEMO_KEY_PASSWORD/);
  assert.match(workflow, /vars\.ANDROID_APP_LINK_SHA256_CERT_FINGERPRINTS/);
  assert.match(workflow, /runs-on: \[self-hosted, linux, x64, careertuner-ci\]/);
  assert.match(workflow, /android-actions\/setup-android@v3/);
  assert.match(workflow, /Verify deployed Asset Links before signing APK/);
  assert.ok(workflow.indexOf("Resolve build mode (mock | live)") < workflow.indexOf("Verify deployed Asset Links before signing APK"));
  assert.match(workflow, /if: steps\.mode\.outputs\.mode == 'live'/);
  assert.match(workflow, /https:\/\/careertuner\.kro\.kr\/\.well-known\/assetlinks\.json/);
  assert.match(workflow, /\.\/gradlew lintRelease assembleRelease/);
  assert.match(workflow, /apksigner/);
  assert.match(workflow, /command -v sdkmanager/);
  assert.match(workflow, /find "\$ANDROID_HOME" -type f -name "\$1"/);
  assert.doesNotMatch(workflow, /build-tools\/36\.0\.0\/apksigner/);
  assert.doesNotMatch(workflow, /cmdline-tools\/latest\/bin\/apkanalyzer/);
  assert.match(workflow, /Signer #1 certificate SHA-256 digest/);
  assert.match(workflow, /apkanalyzer/);
  assert.match(workflow, /android:allowBackup=\"false\"/);
  assert.match(workflow, /cleartextTrafficPermitted/);
  assert.match(workflow, /outputs\/apk\/release\/app-release\.apk/);
  assert.doesNotMatch(workflow, /\.\/gradlew assembleDebug/);
  assert.doesNotMatch(workflow.slice(0, workflow.indexOf("permissions:")), /pull_request:/);
});
