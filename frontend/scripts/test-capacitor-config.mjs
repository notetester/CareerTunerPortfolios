import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { createRequire } from "node:module";
import test from "node:test";

const require = createRequire(import.meta.url);
const {
  assertGeneratedCapacitorConfig,
  createCapacitorConfig,
  isLocalDevelopmentHost,
} = require("./capacitor-config-policy.cjs");

test("Capacitor 기본 프로필은 HTTPS·cleartext off·mixed content off이다", () => {
  const config = createCapacitorConfig({});
  assert.equal(config.server.androidScheme, "https");
  assert.equal(config.server.cleartext, false);
  assert.equal(config.server.url, undefined);
  assert.equal(config.android.allowMixedContent, false);
  assert.equal(config.android.webContentsDebuggingEnabled, false);
  assert.doesNotThrow(() => assertGeneratedCapacitorConfig(config, { mode: "release" }));
});

test("release 프로필은 외부 server URL과 cleartext opt-in을 fail-closed한다", () => {
  assert.throws(
    () => createCapacitorConfig({ CAP_SERVER_URL: "https://dev.example.com" }),
    /release 동기화/,
  );
  assert.throws(
    () => createCapacitorConfig({ CAP_ALLOW_CLEARTEXT: "true" }),
    /release 동기화/,
  );
  assert.throws(
    () => createCapacitorConfig({ CAP_SYNC_MODE: "production" }),
    /release 또는 debug/,
  );
});

test("HTTP live reload는 명시적 debug와 로컬 주소에서만 열린다", () => {
  assert.throws(
    () => createCapacitorConfig({
      CAP_SYNC_MODE: "debug",
      CAP_SERVER_URL: "http://192.168.0.20:5173",
    }),
    /CAP_ALLOW_CLEARTEXT=true/,
  );
  assert.throws(
    () => createCapacitorConfig({
      CAP_SYNC_MODE: "debug",
      CAP_SERVER_URL: "http://example.com:5173",
      CAP_ALLOW_CLEARTEXT: "true",
    }),
    /로컬\/LAN\/Tailscale/,
  );

  const config = createCapacitorConfig({
    CAP_SYNC_MODE: "debug",
    CAP_SERVER_URL: "http://192.168.0.20:5173",
    CAP_ALLOW_CLEARTEXT: "true",
  });
  assert.equal(config.server.url, "http://192.168.0.20:5173/");
  assert.equal(config.server.cleartext, true);
  assert.equal(config.android.allowMixedContent, false);
  assert.equal(config.android.webContentsDebuggingEnabled, true);
  assert.doesNotThrow(() => assertGeneratedCapacitorConfig(config, { mode: "debug" }));
});

test("개발용 HTTPS server URL은 cleartext 없이 사용할 수 있다", () => {
  const config = createCapacitorConfig({
    CAP_SYNC_MODE: "debug",
    CAP_SERVER_URL: "https://dev.careertuner.example",
  });
  assert.equal(config.server.url, "https://dev.careertuner.example/");
  assert.equal(config.server.cleartext, false);
  assert.throws(
    () => createCapacitorConfig({
      CAP_SYNC_MODE: "debug",
      CAP_SERVER_URL: "https://dev.careertuner.example",
      CAP_ALLOW_CLEARTEXT: "true",
    }),
    /설정하지 마세요/,
  );
});

test("로컬·사설망·Tailscale 주소 판별은 공인 HTTP 주소를 제외한다", () => {
  for (const host of ["localhost", "app.local", "127.0.0.1", "10.0.2.2", "172.16.1.2", "192.168.1.4", "100.64.0.1", "::1", "fd00::1"]) {
    assert.equal(isLocalDevelopmentHost(host), true, host);
  }
  for (const host of ["example.com", "8.8.8.8", "172.32.0.1", "100.128.0.1", "2001:4860:4860::8888"]) {
    assert.equal(isLocalDevelopmentHost(host), false, host);
  }
});

test("release 생성 설정은 우회 가능한 HTTP·mixed content·외부 URL을 모두 거부한다", () => {
  const secure = createCapacitorConfig({});
  assert.throws(
    () => assertGeneratedCapacitorConfig({ ...secure, server: { ...secure.server, androidScheme: "http" } }),
    /scheme은 https/,
  );
  assert.throws(
    () => assertGeneratedCapacitorConfig({ ...secure, server: { ...secure.server, cleartext: true } }),
    /cleartext는 false/,
  );
  assert.throws(
    () => assertGeneratedCapacitorConfig({ ...secure, server: { ...secure.server, url: "https://dev.example.com" } }),
    /외부 server.url/,
  );
  assert.throws(
    () => assertGeneratedCapacitorConfig({ ...secure, server: { ...secure.server, url: "" } }),
    /외부 server.url/,
  );
  assert.throws(
    () => assertGeneratedCapacitorConfig({ ...secure, android: { allowMixedContent: true } }),
    /mixed content는 false/,
  );
  assert.throws(
    () => assertGeneratedCapacitorConfig({
      ...secure,
      android: { ...secure.android, webContentsDebuggingEnabled: true },
    }),
    /원격 디버깅은 false/,
  );
});

test("Android main은 cleartext·백업을 닫고 시스템 bar 대비를 보장하며 debug overlay만 개발용으로 연다", async () => {
  const [mainManifest, mainNetworkConfig, backupRules, dataExtractionRules, debugManifest, debugNetworkConfig, gradle, styles, stylesV27, stylesV29, mainActivity, nativeShell] = await Promise.all([
    readFile(new URL("../android/app/src/main/AndroidManifest.xml", import.meta.url), "utf8"),
    readFile(new URL("../android/app/src/main/res/xml/network_security_config.xml", import.meta.url), "utf8"),
    readFile(new URL("../android/app/src/main/res/xml/backup_rules.xml", import.meta.url), "utf8"),
    readFile(new URL("../android/app/src/main/res/xml/data_extraction_rules.xml", import.meta.url), "utf8"),
    readFile(new URL("../android/app/src/debug/AndroidManifest.xml", import.meta.url), "utf8"),
    readFile(new URL("../android/app/src/debug/res/xml/network_security_config.xml", import.meta.url), "utf8"),
    readFile(new URL("../android/app/build.gradle", import.meta.url), "utf8"),
    readFile(new URL("../android/app/src/main/res/values/styles.xml", import.meta.url), "utf8"),
    readFile(new URL("../android/app/src/main/res/values-v27/styles.xml", import.meta.url), "utf8"),
    readFile(new URL("../android/app/src/main/res/values-v29/styles.xml", import.meta.url), "utf8"),
    readFile(new URL("../android/app/src/main/java/com/careertuner/app/MainActivity.java", import.meta.url), "utf8"),
    readFile(new URL("../src/platform/nativeShell.ts", import.meta.url), "utf8"),
  ]);

  assert.match(mainManifest, /android:usesCleartextTraffic="false"/);
  assert.match(mainManifest, /android:allowBackup="false"/);
  assert.match(mainManifest, /android:dataExtractionRules="@xml\/data_extraction_rules"/);
  assert.match(mainManifest, /android:fullBackupContent="@xml\/backup_rules"/);
  assert.match(mainNetworkConfig, /cleartextTrafficPermitted="false"/);
  assert.match(backupRules, /<exclude domain="sharedpref" path="\." \/>/);
  assert.match(dataExtractionRules, /<device-transfer>/);
  assert.match(dataExtractionRules, /<exclude domain="device_sharedpref" path="\." \/>/);
  assert.match(debugManifest, /android:usesCleartextTraffic="true"/);
  assert.match(debugNetworkConfig, /cleartextTrafficPermitted="true"/);
  assert.match(gradle, /verifyReleaseNetworkSecurity/);
  assert.match(gradle, /webContentsDebuggingEnabled/);
  assert.match(gradle, /preReleaseBuild/);
  assert.match(styles, /android:statusBarColor">#050506/);
  assert.match(styles, /android:navigationBarColor">#050506/);
  assert.doesNotMatch(styles, /windowLightNavigationBar|enforceStatusBarContrast|enforceNavigationBarContrast/);
  assert.match(stylesV27, /android:windowLightNavigationBar">false/);
  assert.doesNotMatch(stylesV27, /enforceStatusBarContrast|enforceNavigationBarContrast/);
  assert.match(stylesV29, /android:enforceStatusBarContrast">false/);
  assert.match(stylesV29, /android:enforceNavigationBarContrast">false/);
  assert.match(mainActivity, /webViewParent\.setBackgroundColor\(systemChromeColor\)/);
  assert.match(nativeShell, /SystemBars\.setStyle\(\{ style: systemStyle \}\)/);
  assert.match(nativeShell, /android \|\| dark \? SystemBarsStyle\.Dark : SystemBarsStyle\.Light/);
});
