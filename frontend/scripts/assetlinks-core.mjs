export const ANDROID_APP_LINK_PACKAGE = "com.careertuner.app";
export const ANDROID_APP_LINK_RELATION = "delegate_permission/common.handle_all_urls";

const SHA256_FINGERPRINT_PATTERN = /^(?:[0-9A-F]{2}:){31}[0-9A-F]{2}$/;

export function parseSha256CertificateFingerprints(raw) {
  if (typeof raw !== "string" || !raw.trim()) {
    throw new Error("ANDROID_APP_LINK_SHA256_CERT_FINGERPRINTS가 비어 있습니다.");
  }

  const fingerprints = raw
    .split(/[;,\n]/)
    .map((value) => value.trim().toUpperCase())
    .filter(Boolean);

  if (!fingerprints.length || fingerprints.some((value) => !SHA256_FINGERPRINT_PATTERN.test(value))) {
    throw new Error("Android 인증서 SHA-256 지문 형식이 올바르지 않습니다.");
  }

  return [...new Set(fingerprints)];
}

export function createAssetLinksDocument(rawFingerprints) {
  return [
    {
      relation: [ANDROID_APP_LINK_RELATION],
      target: {
        namespace: "android_app",
        package_name: ANDROID_APP_LINK_PACKAGE,
        sha256_cert_fingerprints: parseSha256CertificateFingerprints(rawFingerprints),
      },
    },
  ];
}

export function assertSigningFingerprintMatches(actualFingerprint, configuredFingerprints) {
  const actual = parseSha256CertificateFingerprints(actualFingerprint);
  if (actual.length !== 1) {
    throw new Error("APK 서명 SHA-256 지문은 하나여야 합니다.");
  }
  const configured = parseSha256CertificateFingerprints(configuredFingerprints);
  if (!configured.includes(actual[0])) {
    throw new Error("APK 서명 지문이 ANDROID_APP_LINK_SHA256_CERT_FINGERPRINTS와 일치하지 않습니다.");
  }
  return actual[0];
}

export function assertAssetLinksDocumentMatches(document, signingFingerprint) {
  const fingerprint = parseSha256CertificateFingerprints(signingFingerprint);
  if (fingerprint.length !== 1 || !Array.isArray(document)) {
    throw new Error("배포된 assetlinks.json 형식이 올바르지 않습니다.");
  }
  const matched = document.some((statement) => (
    Array.isArray(statement?.relation)
    && statement.relation.includes(ANDROID_APP_LINK_RELATION)
    && statement?.target?.namespace === "android_app"
    && statement.target.package_name === ANDROID_APP_LINK_PACKAGE
    && Array.isArray(statement.target.sha256_cert_fingerprints)
    && statement.target.sha256_cert_fingerprints
      .map((value) => String(value).toUpperCase())
      .includes(fingerprint[0])
  ));
  if (!matched) {
    throw new Error("배포된 assetlinks.json에 APK 서명 지문이 없습니다.");
  }
  return fingerprint[0];
}
