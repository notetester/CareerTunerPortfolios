// 아바타 화상 면접의 온디바이스 영상 분석 (MediaPipe tasks-vision).
// Face Landmarker(blendshapes) 로 표정·시선, Pose Landmarker 로 자세를 샘플링한다.
// 영상은 서버에 올라가지 않는다 — 지표·점수(JSON)만 저장 (ADR-002).

import { FaceLandmarker, FilesetResolver, PoseLandmarker } from "@mediapipe/tasks-vision";

// wasm/모델은 공식 CDN 고정 버전 사용 (버전 mismatch 방지 — MediaPipe 공식 권장 패턴).
const WASM_URL = "https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@0.10.35/wasm";
const FACE_MODEL_URL =
  "https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task";
const POSE_MODEL_URL =
  "https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/1/pose_landmarker_lite.task";

const SAMPLE_INTERVAL_MS = 400;
/** eyeLook* blendshape 가 이 값 이상이면 시선이 카메라를 벗어난 프레임으로 본다. */
const GAZE_OFF_THRESHOLD = 0.55;

export interface VisualMetrics {
  samples: number;
  faceDetectedRatio: number; // 얼굴이 잡힌 프레임 비율 (화면 이탈 감지)
  avgSmile: number; // mouthSmile 평균 0~1
  avgBrowTension: number; // browDown(미간 찡그림) 평균 0~1
  gazeOffRatio: number; // 시선 이탈 프레임 비율
  avgShoulderTilt: number; // 어깨 기울기(좌우 y 차) 0~1 정규화 좌표 기준
  movementLevel: number; // 어깨 중심점 이동량 평균 (안절부절 지표)
}

export interface VisualScoreDetail {
  expression: number; // 표정 (미소·긴장)
  gaze: number; // 시선 처리
  posture: number; // 자세 (어깨 수평·움직임)
  presence: number; // 화면 응시 유지 (얼굴 검출 비율)
  overall: number;
}

/**
 * 웹캠 <video> 요소에서 일정 간격으로 표정/자세를 샘플링한다.
 * detectForVideo 는 동기 호출이라 메인스레드를 잠깐 점유하지만,
 * 400ms 간격 + lite 모델이라 면접 UI 에는 영향이 거의 없다.
 */
export class VisualMetricsTracker {
  private face: FaceLandmarker | null = null;
  private pose: PoseLandmarker | null = null;
  private timer: ReturnType<typeof setInterval> | null = null;
  private video: HTMLVideoElement | null = null;

  private samples = 0;
  private faceFrames = 0;
  private smileSum = 0;
  private browSum = 0;
  private gazeOffFrames = 0;
  private shoulderTiltSum = 0;
  private poseFrames = 0;
  private lastShoulderMid: { x: number; y: number } | null = null;
  private movementSum = 0;
  private movementCount = 0;

  /** 모델 로드(수 초 소요 가능) 후 샘플링 시작. 실패 시 throw — 호출부에서 영상 점수 없이 진행. */
  async start(video: HTMLVideoElement): Promise<void> {
    const vision = await FilesetResolver.forVisionTasks(WASM_URL);
    this.face = await FaceLandmarker.createFromOptions(vision, {
      baseOptions: { modelAssetPath: FACE_MODEL_URL, delegate: "GPU" },
      runningMode: "VIDEO",
      numFaces: 1,
      outputFaceBlendshapes: true,
    });
    this.pose = await PoseLandmarker.createFromOptions(vision, {
      baseOptions: { modelAssetPath: POSE_MODEL_URL, delegate: "GPU" },
      runningMode: "VIDEO",
      numPoses: 1,
    });
    this.video = video;
    this.timer = setInterval(() => this.sample(), SAMPLE_INTERVAL_MS);
  }

  finish(): VisualMetrics | null {
    this.stop();
    if (this.samples === 0) return null;
    return {
      samples: this.samples,
      faceDetectedRatio: round2(this.faceFrames / this.samples),
      avgSmile: this.faceFrames > 0 ? round2(this.smileSum / this.faceFrames) : 0,
      avgBrowTension: this.faceFrames > 0 ? round2(this.browSum / this.faceFrames) : 0,
      gazeOffRatio: this.faceFrames > 0 ? round2(this.gazeOffFrames / this.faceFrames) : 0,
      avgShoulderTilt: this.poseFrames > 0 ? round2(this.shoulderTiltSum / this.poseFrames) : 0,
      movementLevel: this.movementCount > 0 ? round3(this.movementSum / this.movementCount) : 0,
    };
  }

  dispose() {
    this.stop();
  }

  private stop() {
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = null;
    }
    this.face?.close();
    this.pose?.close();
    this.face = null;
    this.pose = null;
    this.video = null;
  }

  private sample() {
    const video = this.video;
    if (!video || !this.face || !this.pose || video.readyState < 2) return;
    const now = performance.now();
    this.samples += 1;

    const faceResult = this.face.detectForVideo(video, now);
    const shapes = faceResult.faceBlendshapes?.[0]?.categories;
    if (shapes && shapes.length > 0) {
      this.faceFrames += 1;
      const get = (name: string) => shapes.find((c) => c.categoryName === name)?.score ?? 0;
      this.smileSum += (get("mouthSmileLeft") + get("mouthSmileRight")) / 2;
      this.browSum += (get("browDownLeft") + get("browDownRight")) / 2;
      const gazeOff = Math.max(
        (get("eyeLookOutLeft") + get("eyeLookInRight")) / 2, // 왼쪽 응시
        (get("eyeLookInLeft") + get("eyeLookOutRight")) / 2, // 오른쪽 응시
        (get("eyeLookUpLeft") + get("eyeLookUpRight")) / 2,
        (get("eyeLookDownLeft") + get("eyeLookDownRight")) / 2,
      );
      if (gazeOff >= GAZE_OFF_THRESHOLD) this.gazeOffFrames += 1;
    }

    const poseResult = this.pose.detectForVideo(video, now + 0.1);
    const lm = poseResult.landmarks?.[0];
    if (lm && lm.length > 12) {
      this.poseFrames += 1;
      const left = lm[11]; // 왼쪽 어깨
      const right = lm[12]; // 오른쪽 어깨
      this.shoulderTiltSum += Math.abs(left.y - right.y);
      const mid = { x: (left.x + right.x) / 2, y: (left.y + right.y) / 2 };
      if (this.lastShoulderMid) {
        this.movementSum += Math.hypot(mid.x - this.lastShoulderMid.x, mid.y - this.lastShoulderMid.y);
        this.movementCount += 1;
      }
      this.lastShoulderMid = mid;
    }
  }
}

/** 영상 지표 → 항목별 점수. 기준: 자연스러운 미소, 카메라 응시, 수평 어깨, 차분한 자세. */
export function computeVisualScore(metrics: VisualMetrics): VisualScoreDetail {
  // 표정: 미소는 가산(0.05~0.3 적정), 미간 긴장은 감점.
  let expression = 60;
  expression += Math.round(Math.min(metrics.avgSmile, 0.3) * 100); // 최대 +30
  expression -= Math.round(Math.min(metrics.avgBrowTension, 0.4) * 75); // 최대 -30
  expression = clamp(expression, 0, 100);

  // 시선: 이탈 비율 0% = 100, 50%+ = 30.
  const gaze = clamp(Math.round(100 - metrics.gazeOffRatio * 140), 30, 100);

  // 자세: 어깨 기울기(0.02 이하 양호) + 움직임(0.004 이하 차분).
  let posture = 100;
  posture -= Math.round(Math.max(0, metrics.avgShoulderTilt - 0.02) * 800);
  posture -= Math.round(Math.max(0, metrics.movementLevel - 0.004) * 4000);
  posture = clamp(posture, 20, 100);

  // 화면 유지: 얼굴 검출 비율.
  const presence = clamp(Math.round(metrics.faceDetectedRatio * 100), 0, 100);

  const overall = Math.round(expression * 0.3 + gaze * 0.3 + posture * 0.25 + presence * 0.15);
  return { expression, gaze, posture, presence, overall };
}

function clamp(v: number, min: number, max: number): number {
  return Math.max(min, Math.min(max, v));
}

function round2(v: number): number {
  return Math.round(v * 100) / 100;
}

function round3(v: number): number {
  return Math.round(v * 1000) / 1000;
}
