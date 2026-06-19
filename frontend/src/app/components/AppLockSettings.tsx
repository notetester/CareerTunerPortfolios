import { useState } from "react";
import { ShieldCheck, Fingerprint } from "lucide-react";
import { Button } from "./ui/button";
import { Checkbox } from "./ui/checkbox";
import { Input } from "./ui/input";
import {
  biometricAvailable, biometricEnabled, clearLock, hasPin, setBiometricEnabled, setPin,
} from "@/platform/applock";

/** 설정 > 보안: 앱 잠금(PIN/생체) 관리. 기기 로컬 잠금이라 서버와 무관. */
export function AppLockSettings() {
  const [enabled, setEnabled] = useState(hasPin());
  const [bio, setBio] = useState(biometricEnabled());
  const [editing, setEditing] = useState(false);
  const [pin1, setPin1] = useState("");
  const [pin2, setPin2] = useState("");
  const [msg, setMsg] = useState<string | null>(null);
  const [err, setErr] = useState<string | null>(null);

  const flash = (m: string, isErr = false) => {
    if (isErr) { setErr(m); setMsg(null); } else { setMsg(m); setErr(null); }
    setTimeout(() => { setErr(null); setMsg(null); }, 2600);
  };

  const savePin = async () => {
    if (!/^\d{4,6}$/.test(pin1)) { flash("PIN은 4~6자리 숫자여야 합니다.", true); return; }
    if (pin1 !== pin2) { flash("PIN 확인이 일치하지 않습니다.", true); return; }
    await setPin(pin1);
    setEnabled(true);
    setEditing(false);
    setPin1(""); setPin2("");
    flash("앱 잠금 PIN이 설정되었습니다.");
  };

  const disable = () => {
    clearLock();
    setEnabled(false);
    setBio(false);
    setEditing(false);
    flash("앱 잠금을 해제했습니다.");
  };

  const toggleBio = (on: boolean) => {
    setBiometricEnabled(on);
    setBio(on);
  };

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between rounded-xl border border-slate-200 p-4">
        <div className="flex items-center gap-2">
          <ShieldCheck className="size-4 text-blue-600" />
          <div>
            <div className="text-sm font-semibold text-slate-800">앱 잠금 (PIN)</div>
            <div className="text-xs text-slate-500">앱 실행·복귀 시 PIN으로 한 번 더 보호합니다(이 기기에만 적용).</div>
          </div>
        </div>
        <Checkbox
          checked={enabled}
          onCheckedChange={(v) => {
            if (v === true) { setEditing(true); }
            else disable();
          }}
        />
      </div>

      {(editing || (!enabled)) && editing && (
        <div className="space-y-2 rounded-xl border border-blue-100 bg-blue-50/50 p-4">
          <div className="text-xs font-semibold text-slate-600">PIN 설정 (4~6자리 숫자)</div>
          <div className="grid gap-2 sm:grid-cols-2">
            <Input type="password" inputMode="numeric" maxLength={6} value={pin1} onChange={(e) => setPin1(e.target.value.replace(/\D/g, ""))} placeholder="PIN" />
            <Input type="password" inputMode="numeric" maxLength={6} value={pin2} onChange={(e) => setPin2(e.target.value.replace(/\D/g, ""))} placeholder="PIN 확인" />
          </div>
          <div className="flex gap-2">
            <Button size="sm" className="bg-blue-600 text-white hover:bg-blue-700" onClick={() => void savePin()}>설정</Button>
            <Button size="sm" variant="outline" onClick={() => { setEditing(false); setPin1(""); setPin2(""); if (!hasPin()) setEnabled(false); }}>취소</Button>
          </div>
        </div>
      )}

      {enabled && !editing && (
        <div className="flex flex-wrap items-center gap-2">
          <Button size="sm" variant="outline" onClick={() => setEditing(true)}>PIN 변경</Button>
          {biometricAvailable() && (
            <label className="flex items-center gap-2 rounded-lg border border-slate-200 px-3 py-1.5 text-sm">
              <Fingerprint className="size-4 text-slate-600" /> 생체 인증
              <Checkbox checked={bio} onCheckedChange={(v) => toggleBio(v === true)} />
            </label>
          )}
        </div>
      )}

      {msg && <div className="text-xs text-green-600">{msg}</div>}
      {err && <div className="text-xs text-red-600">{err}</div>}
    </div>
  );
}
