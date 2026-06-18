"""
ChaLearn First Impressions V2 → 음성 피처 + 라벨 CSV (학습 입력).

라벨은 공식 chalearnlap(dataset 24)의 annotation pickle 에 들어 있다(HF 미러 yeray142 는
라벨 미업로드 — age_group 만). pickle 구조:
    {"interview": {"파일명.mp4": 0~1}, "openness": {...}, "extraversion": {...}, ...}  # 6키

사용:
  python prepare_chalearn.py --video-dir <mp4폴더> --annotation annotation_training.pkl \
         [--transcription transcription_training.pkl] --out data/train.csv [--limit 500]

각 mp4 → ffmpeg 16kHz mono wav → extract_voice_features → 피처 + interview 라벨 1행.
추출은 serve 와 동일한 extract_features 코드를 쓴다(train/serve skew 차단, ADR-006).

NOTE: ChaLearn 은 영어(YouTube) 라 한국어 군말(FILLER_TOKENS)은 0 이 되고 말속도(글자수/분)도
      언어 의존이다. 피치/성량/발화시간은 음향이라 언어 무관. 학습 후 피처 중요도로 speechRate·
      filler 사용 여부를 판단한다. transcription 이 없으면 두 값은 결측(None)으로 둔다.
★ 공개 데이터는 DB 에 넣지 않고 파일(data/)로만 둔다.
"""
import argparse
import csv
import os
import pickle
import subprocess
import tempfile

from extract_features import VOICE_FEATURE_KEYS, count_fillers, extract_voice_features

TARGET_KEY = "interview"  # 면접 호감도(0~1) → 종합 인상 점수 타겟


def to_wav16k(src: str) -> str:
    dst = tempfile.NamedTemporaryFile(delete=False, suffix=".16k.wav").name
    subprocess.run(
        ["ffmpeg", "-y", "-i", src, "-ar", "16000", "-ac", "1", dst],
        check=True,
        capture_output=True,
    )
    return dst


def load_pickle(path: str):
    with open(path, "rb") as f:
        try:
            return pickle.load(f)
        except UnicodeDecodeError:
            f.seek(0)
            return pickle.load(f, encoding="latin1")  # Python2 로 만든 오래된 pkl 대비


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--video-dir", required=True, help="mp4 클립 폴더 (annotation 키와 파일명 일치)")
    ap.add_argument("--annotation", required=True, help="annotation_*.pkl (라벨)")
    ap.add_argument("--transcription", help="(선택) transcription_*.pkl ({파일명: 텍스트})")
    ap.add_argument("--out", required=True, help="출력 csv")
    ap.add_argument("--limit", type=int, default=0, help="앞 N개만 (서브셋 검증)")
    args = ap.parse_args()

    labels = load_pickle(args.annotation)
    if TARGET_KEY not in labels:
        raise SystemExit(f"annotation 에 '{TARGET_KEY}' 키가 없음. 실제 키: {list(labels.keys())}")
    target = labels[TARGET_KEY]
    transcription = load_pickle(args.transcription) if args.transcription else {}

    # video_dir 재귀로 mp4 파일명 → 전체경로 인덱스.
    # Kaggle 미러는 train-N/training80_XX/ 중첩 구조라 평면 join 으론 못 찾는다.
    video_index = {}
    for root, _, files in os.walk(args.video_dir):
        for fn in files:
            if fn.lower().endswith(".mp4"):
                video_index[fn] = os.path.join(root, fn)
    print(f"mp4 {len(video_index)}개 인덱싱 ({args.video_dir})")

    os.makedirs(os.path.dirname(os.path.abspath(args.out)), exist_ok=True)
    fieldnames = ["video"] + VOICE_FEATURE_KEYS + [TARGET_KEY]
    written, skipped = 0, 0
    with open(args.out, "w", newline="", encoding="utf-8") as out:
        writer = csv.DictWriter(out, fieldnames=fieldnames)
        writer.writeheader()
        for name in target:
            if args.limit and written >= args.limit:
                break
            # annotation 키가 확장자 없이 올 수도 있어 ".mp4" 보정까지 시도.
            mp4 = video_index.get(name) or video_index.get(f"{name}.mp4")
            if not mp4:
                skipped += 1
                continue
            wav = None
            try:
                wav = to_wav16k(mp4)
                text = transcription.get(name, "")
                chars = len(text.replace(" ", "")) if text else 0
                filler = count_fillers([text]) if text else 0
                m = extract_voice_features(wav, chars, filler)
                row = {"video": name, TARGET_KEY: target[name]}
                row.update({k: m.get(k) for k in VOICE_FEATURE_KEYS})
                writer.writerow(row)
                written += 1
                if written % 100 == 0:
                    print(f"  {written} 행 추출...")
            except Exception as e:  # noqa: BLE001 — 한 클립 실패가 전체를 막지 않게
                skipped += 1
                print(f"  skip {name}: {e}")
            finally:
                if wav and os.path.exists(wav):
                    os.remove(wav)
    print(f"완료: {written} 행 작성, {skipped} 건 스킵 → {args.out}")


if __name__ == "__main__":
    main()
