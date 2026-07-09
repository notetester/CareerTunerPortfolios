const TOTP_PERIOD_SECONDS = 30;
const TOTP_DIGITS = 6;

/**
 * RFC 6238 TOTP 코드 생성기.
 *
 * Google Authenticator 같은 앱도 같은 원리로 동작한다:
 * 1. 사용자의 secret key 를 Base32 로 디코딩한다.
 * 2. 현재 시간을 30초 단위 counter 로 바꾼다.
 * 3. HMAC-SHA1(secret, counter) 결과에서 6자리 숫자를 뽑는다.
 */
export async function generateTotpCode(secret: string, now = Date.now()): Promise<string> {
  const key = await crypto.subtle.importKey(
    "raw",
    base32ToBytes(secret),
    { name: "HMAC", hash: "SHA-1" },
    false,
    ["sign"],
  );
  const counter = Math.floor(now / 1000 / TOTP_PERIOD_SECONDS);
  const counterBytes = counterToBytes(counter);
  const signature = new Uint8Array(await crypto.subtle.sign("HMAC", key, counterBytes));
  const offset = signature[signature.length - 1] & 0x0f;
  const binary =
    ((signature[offset] & 0x7f) << 24)
    | ((signature[offset + 1] & 0xff) << 16)
    | ((signature[offset + 2] & 0xff) << 8)
    | (signature[offset + 3] & 0xff);
  const token = binary % 10 ** TOTP_DIGITS;
  return token.toString().padStart(TOTP_DIGITS, "0");
}

export function totpSecondsRemaining(now = Date.now()): number {
  return TOTP_PERIOD_SECONDS - (Math.floor(now / 1000) % TOTP_PERIOD_SECONDS);
}

function counterToBytes(counter: number): ArrayBuffer {
  const buffer = new ArrayBuffer(8);
  const view = new DataView(buffer);
  const high = Math.floor(counter / 0x100000000);
  const low = counter >>> 0;
  view.setUint32(0, high);
  view.setUint32(4, low);
  return buffer;
}

function base32ToBytes(value: string): Uint8Array<ArrayBuffer> {
  const alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
  const normalized = value.replace(/[\s=-]/g, "").toUpperCase();
  let bits = "";
  for (const char of normalized) {
    const index = alphabet.indexOf(char);
    if (index < 0) {
      throw new Error("TOTP 키 형식이 올바르지 않습니다.");
    }
    bits += index.toString(2).padStart(5, "0");
  }

  const bytes: number[] = [];
  for (let i = 0; i + 8 <= bits.length; i += 8) {
    bytes.push(Number.parseInt(bits.slice(i, i + 8), 2));
  }
  return new Uint8Array(bytes);
}
