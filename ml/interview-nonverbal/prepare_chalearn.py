"""
ChaLearn First Impressions V2 → 음성 피처 + 라벨 CSV.  (TODO: 2026-06-19 실행)

HF 미러 `yeray142/first-impressions-v2` (public, 신청 불필요, 29.5GB,
영상 1만 + Big5 성격 + 면접 통과 라벨). 서브셋(2~3천)만 다운받아 학습에 쓴다.

흐름(2026-06-19):
  1) 서브셋 다운로드 (datasets / 직접)
  2) 각 클립의 오디오 추출 → 16kHz mono 변환
  3) extract_features.extract_voice_features() 로 음성 피처 (전사 없으면 글자수/필러는 0 처리 또는 자체 STT)
  4) Big5/면접호감도 라벨과 병합 → data/train.csv, data/val.csv

★ 합성/공개 데이터는 DB 에 넣지 않고 파일로만 둔다.
"""

# from extract_features import extract_voice_features, voice_feature_vector, count_fillers

if __name__ == "__main__":
    print("TODO(2026-06-19): ChaLearn 서브셋 다운 → 오디오 16kHz → extract_voice_features → CSV")
