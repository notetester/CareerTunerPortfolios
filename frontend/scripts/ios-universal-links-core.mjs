export const IOS_APP_BUNDLE_ID = "com.careertuner.app";
export const IOS_APP_LINK_HOST = "careertuner.example.com";
export const IOS_ASSOCIATED_DOMAINS_ENTITLEMENT = `applinks:${IOS_APP_LINK_HOST}`;
export const IOS_ENTITLEMENTS_PROJECT_PATH = "App/App.entitlements";
export const IOS_CUSTOM_URL_SCHEME = "careertuner";
export const IOS_USAGE_DESCRIPTIONS = Object.freeze({
  NSCameraUsageDescription:
    "화상 면접 촬영과 공고·이력서 등록에 카메라를 사용합니다. 면접 원본 영상은 답변 기록에 저장되며 사용자가 삭제할 수 있습니다.",
  NSMicrophoneUsageDescription:
    "음성·화상 면접 답변을 녹음하고 전달력을 분석하는 데 마이크를 사용합니다. 원본은 답변 기록에 저장되며 사용자가 삭제할 수 있습니다.",
  NSPhotoLibraryUsageDescription:
    "공고·이력서 사진을 선택해 지원 건으로 등록할 때 사진 보관함에 접근합니다.",
});

const APPLE_TEAM_ID_PATTERN = /^[A-Z0-9]{10}$/;
const UNIVERSAL_LINK_COMPONENTS = Object.freeze([
  Object.freeze({
    "/": "/auth/callback",
    comment: "CareerTuner native OAuth handoff result",
  }),
  Object.freeze({
    "/": "/profile/detail",
    comment: "CareerTuner native social account link result",
  }),
]);

export function parseAppleTeamIds(raw, { allowEmpty = false } = {}) {
  if (typeof raw !== "string" || !raw.trim()) {
    if (allowEmpty) return [];
    throw new Error("IOS_APP_LINK_TEAM_IDS가 비어 있습니다.");
  }

  const teamIds = raw
    .split(/[;,\n]/)
    .map((value) => value.trim().toUpperCase())
    .filter(Boolean);

  if (!teamIds.length || teamIds.some((value) => !APPLE_TEAM_ID_PATTERN.test(value))) {
    throw new Error("Apple Team ID는 영문 대문자와 숫자로 이루어진 10자리여야 합니다.");
  }
  return [...new Set(teamIds)];
}

export function createAppleAppSiteAssociation(rawTeamIds, { allowEmpty = false } = {}) {
  const teamIds = parseAppleTeamIds(rawTeamIds, { allowEmpty });
  return {
    applinks: {
      details: teamIds.length === 0
        ? []
        : [
          {
            appIDs: teamIds.map((teamId) => `${teamId}.${IOS_APP_BUNDLE_ID}`),
            components: UNIVERSAL_LINK_COMPONENTS.map((component) => ({ ...component })),
          },
        ],
    },
  };
}

export function createAssociatedDomainsEntitlements() {
  return patchAssociatedDomainsEntitlements(`<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
</dict>
</plist>
`);
}

function findMatchingArrayClose(source, arrayOpenIndex) {
  const tagPattern = /<\/?array\s*>/g;
  tagPattern.lastIndex = arrayOpenIndex;
  let depth = 0;
  for (let match = tagPattern.exec(source); match; match = tagPattern.exec(source)) {
    if (match[0].startsWith("</")) {
      depth -= 1;
      if (depth === 0) return match.index;
    } else {
      depth += 1;
    }
  }
  return -1;
}

function urlTypesContainScheme(urlTypesBlock, scheme) {
  const schemesKeyPattern = /<key>CFBundleURLSchemes<\/key>/g;
  for (let match = schemesKeyPattern.exec(urlTypesBlock); match; match = schemesKeyPattern.exec(urlTypesBlock)) {
    const keyEnd = match.index + match[0].length;
    const arrayOpenIndex = urlTypesBlock.indexOf("<array>", keyEnd);
    if (arrayOpenIndex < 0) throw new Error("CFBundleURLSchemes의 array를 찾지 못했습니다.");
    const arrayCloseIndex = findMatchingArrayClose(urlTypesBlock, arrayOpenIndex);
    if (arrayCloseIndex < 0) throw new Error("CFBundleURLSchemes의 닫는 array를 찾지 못했습니다.");
    if (urlTypesBlock.slice(arrayOpenIndex, arrayCloseIndex).includes(`<string>${scheme}</string>`)) {
      return true;
    }
    schemesKeyPattern.lastIndex = arrayCloseIndex;
  }
  return false;
}

function assertPlistSource(source, label) {
  if (typeof source !== "string" || !source.includes("<plist") || !source.includes("<dict>")) {
    throw new Error(`${label} 형식이 올바르지 않습니다.`);
  }
}

/** 기존 push/keychain 등의 entitlement를 보존하면서 Associated Domains만 추가한다. */
export function patchAssociatedDomainsEntitlements(entitlementsSource) {
  assertPlistSource(entitlementsSource, "iOS entitlements plist");
  const newline = entitlementsSource.includes("\r\n") ? "\r\n" : "\n";
  const keyPattern = /<key>com\.apple\.developer\.associated-domains<\/key>/g;
  const matches = [...entitlementsSource.matchAll(keyPattern)];
  if (matches.length > 1) throw new Error("iOS entitlements에 Associated Domains 키가 중복되어 있습니다.");

  if (matches.length === 1) {
    const keyEnd = matches[0].index + matches[0][0].length;
    const arrayOpenIndex = entitlementsSource.indexOf("<array>", keyEnd);
    if (arrayOpenIndex < 0) throw new Error("Associated Domains entitlement의 array를 찾지 못했습니다.");
    const arrayCloseIndex = findMatchingArrayClose(entitlementsSource, arrayOpenIndex);
    if (arrayCloseIndex < 0) throw new Error("Associated Domains entitlement의 닫는 array를 찾지 못했습니다.");
    const arrayBlock = entitlementsSource.slice(arrayOpenIndex, arrayCloseIndex);
    if (arrayBlock.includes(`<string>${IOS_ASSOCIATED_DOMAINS_ENTITLEMENT}</string>`)) {
      return entitlementsSource;
    }
    return `${entitlementsSource.slice(0, arrayCloseIndex)}\t\t<string>${IOS_ASSOCIATED_DOMAINS_ENTITLEMENT}</string>${newline}${entitlementsSource.slice(arrayCloseIndex)}`;
  }

  const rootDictClose = entitlementsSource.lastIndexOf("</dict>");
  if (rootDictClose < 0) throw new Error("iOS entitlements의 최상위 dict를 찾지 못했습니다.");
  const entry = [
    "\t<key>com.apple.developer.associated-domains</key>",
    "\t<array>",
    `\t\t<string>${IOS_ASSOCIATED_DOMAINS_ENTITLEMENT}</string>`,
    "\t</array>",
  ].join(newline);
  return `${entitlementsSource.slice(0, rootDictClose)}${entry}${newline}${entitlementsSource.slice(rootDictClose)}`;
}

function patchPlistStringValue(source, key, value) {
  const escapedKey = key.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const keyPattern = new RegExp(`<key>${escapedKey}<\\/key>`, "g");
  const matches = [...source.matchAll(keyPattern)];
  if (matches.length > 1) throw new Error(`Info.plist에 ${key}가 중복되어 있습니다.`);

  if (matches.length === 1) {
    const keyEnd = matches[0].index + matches[0][0].length;
    const stringMatch = source.slice(keyEnd).match(/^\s*<string>[\s\S]*?<\/string>/);
    if (!stringMatch) throw new Error(`Info.plist의 ${key} 문자열 값을 찾지 못했습니다.`);
    const stringStart = keyEnd + stringMatch.index;
    const leadingWhitespace = stringMatch[0].match(/^\s*/)?.[0] ?? "";
    return `${source.slice(0, stringStart)}${leadingWhitespace}<string>${value}</string>${source.slice(stringStart + stringMatch[0].length)}`;
  }

  const rootDictClose = source.lastIndexOf("</dict>");
  if (rootDictClose < 0) throw new Error("Info.plist의 최상위 dict를 찾지 못했습니다.");
  const newline = source.includes("\r\n") ? "\r\n" : "\n";
  const entry = `\t<key>${key}</key>${newline}\t<string>${value}</string>${newline}`;
  return `${source.slice(0, rootDictClose)}${entry}${source.slice(rootDictClose)}`;
}

/** 카메라·마이크·사진 선택 기능이 iOS 권한 요청 전에 필요한 설명을 멱등 보장한다. */
export function patchInfoPlistUsageDescriptions(infoPlistSource) {
  assertPlistSource(infoPlistSource, "iOS Info.plist");
  return Object.entries(IOS_USAGE_DESCRIPTIONS).reduce(
    (source, [key, value]) => patchPlistStringValue(source, key, value),
    infoPlistSource,
  );
}

export function patchInfoPlistCustomScheme(infoPlistSource) {
  assertPlistSource(infoPlistSource, "iOS Info.plist");

  const newline = infoPlistSource.includes("\r\n") ? "\r\n" : "\n";
  const urlTypesKeyPattern = /<key>CFBundleURLTypes<\/key>/g;
  const keyMatches = [...infoPlistSource.matchAll(urlTypesKeyPattern)];
  if (keyMatches.length > 1) throw new Error("Info.plist에 CFBundleURLTypes가 중복되어 있습니다.");

  const urlTypeEntry = [
    "\t\t<dict>",
    "\t\t\t<key>CFBundleURLName</key>",
    `\t\t\t<string>${IOS_APP_BUNDLE_ID}</string>`,
    "\t\t\t<key>CFBundleURLSchemes</key>",
    "\t\t\t<array>",
    `\t\t\t\t<string>${IOS_CUSTOM_URL_SCHEME}</string>`,
    "\t\t\t</array>",
    "\t\t</dict>",
  ].join(newline);

  if (keyMatches.length === 1) {
    const keyEnd = keyMatches[0].index + keyMatches[0][0].length;
    const arrayOpenIndex = infoPlistSource.indexOf("<array>", keyEnd);
    if (arrayOpenIndex < 0) throw new Error("CFBundleURLTypes의 array를 찾지 못했습니다.");
    const arrayCloseIndex = findMatchingArrayClose(infoPlistSource, arrayOpenIndex);
    if (arrayCloseIndex < 0) throw new Error("CFBundleURLTypes의 닫는 array를 찾지 못했습니다.");

    const urlTypesBlock = infoPlistSource.slice(arrayOpenIndex, arrayCloseIndex);
    if (urlTypesContainScheme(urlTypesBlock, IOS_CUSTOM_URL_SCHEME)) return infoPlistSource;
    return `${infoPlistSource.slice(0, arrayCloseIndex)}${urlTypeEntry}${newline}\t${infoPlistSource.slice(arrayCloseIndex)}`;
  }

  const rootDictClose = infoPlistSource.lastIndexOf("</dict>");
  if (rootDictClose < 0) throw new Error("Info.plist의 최상위 dict를 찾지 못했습니다.");
  const urlTypesBlock = [
    "\t<key>CFBundleURLTypes</key>",
    "\t<array>",
    urlTypeEntry,
    "\t</array>",
  ].join(newline);
  return `${infoPlistSource.slice(0, rootDictClose)}${urlTypesBlock}${newline}${infoPlistSource.slice(rootDictClose)}`;
}

export function patchXcodeProjectAssociatedDomains(projectSource) {
  if (typeof projectSource !== "string" || !projectSource.includes("/* Begin XCBuildConfiguration section */")) {
    throw new Error("Xcode project.pbxproj 형식이 올바르지 않습니다.");
  }

  let targetConfigurationCount = 0;
  let patchedConfigurationCount = 0;
  const configurationPattern = /(\t\t[0-9A-F]+ \/\* (?:Debug|Release) \*\/ = \{[\s\S]*?\n\t\t\};)/g;
  const patched = projectSource.replace(configurationPattern, (block) => {
    if (!block.includes(`PRODUCT_BUNDLE_IDENTIFIER = ${IOS_APP_BUNDLE_ID};`)) return block;
    targetConfigurationCount += 1;

    const existing = block.match(/^[ \t]*CODE_SIGN_ENTITLEMENTS = ([^;]+);/m);
    if (existing) {
      if (existing[1] !== IOS_ENTITLEMENTS_PROJECT_PATH) {
        throw new Error(`기존 CODE_SIGN_ENTITLEMENTS(${existing[1]})를 자동으로 덮어쓸 수 없습니다.`);
      }
      patchedConfigurationCount += 1;
      return block;
    }

    const marker = /^(\t+)(CODE_SIGN_STYLE = Automatic;)$/m;
    if (!marker.test(block)) {
      throw new Error("App target의 CODE_SIGN_STYLE 설정을 찾지 못했습니다.");
    }
    patchedConfigurationCount += 1;
    return block.replace(marker, `$1CODE_SIGN_ENTITLEMENTS = ${IOS_ENTITLEMENTS_PROJECT_PATH};\n$1$2`);
  });

  if (targetConfigurationCount !== 2 || patchedConfigurationCount !== 2) {
    throw new Error(`App target Debug/Release 설정 2개를 기대했지만 ${targetConfigurationCount}개를 찾았습니다.`);
  }
  return patched;
}
