"""
LightGBM 비언어 인상 점수 학습.  (TODO: 2026-06-19 데이터 받쳐 실행)

음성 피처(추후 영상 합류) → 종합 면접 인상 점수 회귀.
ChaLearn 라벨은 Big5 + 면접 통과 점수 → MultiOutputRegressor(LightGBM) 또는 단일 인상 점수.

흐름(2026-06-19):
  1) prepare_chalearn.py 산출 data/train.csv 로드
  2) 결측(None) 중앙값 대체 + 정규화 통계 저장(추론과 공유)
  3) MultiOutputRegressor(LGBMRegressor) 학습
  4) 모델 + 정규화 통계 저장(joblib) → serve.py 가 로드

학습=추론 동일 피처 추출(extract_features.py)이므로 train/serve skew 없음 (ADR-006).
"""

# import lightgbm as lgb
# from sklearn.multioutput import MultiOutputRegressor
# import joblib

if __name__ == "__main__":
    print("TODO(2026-06-19): data/train.csv → LightGBM 학습 → model.joblib 저장")
