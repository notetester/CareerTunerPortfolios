// 기기 능력(카메라/마이크 존재) 감지.
//
// enumerateDevices 는 권한 승인 전에도 기기 종류(videoinput/audioinput)는 나열해 준다.
// 카메라 없는 데스크탑에서 화상 면접 탭이 getUserMedia 실패로 죽는 대신,
// 미리 감지해 "폰으로 이어하기"(기기 핸드오프) 안내를 띄우는 데 쓴다.

import { useEffect, useState } from "react";

export interface DeviceCapabilities {
  /** 감지 완료 여부. false 인 동안은 미확정이므로 기능을 막지 않는다. */
  checked: boolean;
  /** 카메라(videoinput) 존재 여부. null = 판별 불가(미지원/오류). */
  hasCamera: boolean | null;
  /** 마이크(audioinput) 존재 여부. null = 판별 불가. */
  hasMicrophone: boolean | null;
}

export const UNKNOWN_DEVICE_CAPABILITIES: DeviceCapabilities = {
  checked: false,
  hasCamera: null,
  hasMicrophone: null,
};

/** 카메라/마이크 존재를 1회 감지한다. 판별 불가 환경에서는 null(막지 않음)로 돌려준다. */
export async function detectDeviceCapabilities(): Promise<DeviceCapabilities> {
  if (typeof navigator === "undefined" || !navigator.mediaDevices?.enumerateDevices) {
    // mediaDevices 자체가 없는 환경(비보안 오리진 등)은 mediaSupport 쪽 안내가 담당한다.
    return { checked: true, hasCamera: null, hasMicrophone: null };
  }
  try {
    const devices = await navigator.mediaDevices.enumerateDevices();
    return {
      checked: true,
      hasCamera: devices.some((d) => d.kind === "videoinput"),
      hasMicrophone: devices.some((d) => d.kind === "audioinput"),
    };
  } catch {
    return { checked: true, hasCamera: null, hasMicrophone: null };
  }
}

/**
 * 카메라/마이크 존재 감지 훅. 마운트 시 감지하고, 기기 연결/해제(devicechange)에 반응해 갱신한다.
 * hasCamera === false 처럼 "확실히 없음"일 때만 게이트로 쓰고, null 은 막지 않는다.
 */
export function useDeviceCapabilities(): DeviceCapabilities {
  const [caps, setCaps] = useState<DeviceCapabilities>(UNKNOWN_DEVICE_CAPABILITIES);

  useEffect(() => {
    let cancelled = false;
    const refresh = () => {
      void detectDeviceCapabilities().then((next) => {
        if (!cancelled) setCaps(next);
      });
    };
    refresh();
    const md = typeof navigator !== "undefined" ? navigator.mediaDevices : undefined;
    md?.addEventListener?.("devicechange", refresh);
    return () => {
      cancelled = true;
      md?.removeEventListener?.("devicechange", refresh);
    };
  }, []);

  return caps;
}
