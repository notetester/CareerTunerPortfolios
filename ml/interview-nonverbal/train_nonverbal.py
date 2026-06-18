"""
음성 피처 CSV → LightGBM 회귀 모델(종합 인상 점수) → model.joblib.

사용: python train_nonverbal.py --csv data/train.csv [--out model.joblib]
타겟: interview(0~1) × 100 = 0~100 인상 점수.
serve.py 가 같은 폴더의 model.joblib 을 자동 로드해 규칙 폴백 대신 사용한다.

피처 순서는 extract_features.VOICE_FEATURE_KEYS 로 고정(학습=추론 동일, ADR-006).
결측(None)은 NaN 으로 두고 LightGBM 의 기본 결측 분기 처리에 맡긴다.
"""
import argparse
import csv

import numpy as np
from lightgbm import LGBMRegressor
from sklearn.metrics import mean_absolute_error
from sklearn.model_selection import train_test_split

from extract_features import VOICE_FEATURE_KEYS

try:
    import joblib
except ImportError:
    joblib = None

TARGET_KEY = "interview"


def load(csv_path: str):
    X, y = [], []
    with open(csv_path, newline="", encoding="utf-8") as f:
        for row in csv.DictReader(f):
            vec = []
            for k in VOICE_FEATURE_KEYS:
                v = row.get(k, "")
                vec.append(float(v) if v not in ("", "None", None) else np.nan)
            X.append(vec)
            y.append(float(row[TARGET_KEY]) * 100)
    return np.array(X, dtype=float), np.array(y, dtype=float)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--csv", required=True)
    ap.add_argument("--out", default="model.joblib")
    args = ap.parse_args()

    X, y = load(args.csv)
    print(f"데이터 {len(y)} 행, 피처 {X.shape[1]}개 {VOICE_FEATURE_KEYS}")
    if len(y) < 20:
        raise SystemExit("표본이 너무 적음 — 최소 수십 행 필요(서브셋 --limit 을 늘려 받을 것)")

    X_tr, X_te, y_tr, y_te = train_test_split(X, y, test_size=0.2, random_state=42)
    model = LGBMRegressor(
        n_estimators=300, learning_rate=0.05, num_leaves=31,
        min_child_samples=20, random_state=42,
    )
    model.fit(X_tr, y_tr)

    mae = mean_absolute_error(y_te, model.predict(X_te))
    print(f"검증 MAE: {mae:.1f} (0~100 척도)")
    importances = dict(zip(VOICE_FEATURE_KEYS, [round(v) for v in model.feature_importances_.tolist()]))
    print(f"피처 중요도: {importances}")

    if joblib is None:
        raise SystemExit("joblib 미설치 (pip install joblib)")
    joblib.dump(model, args.out)
    print(f"모델 저장 → {args.out}  (serve.py 가 자동 로드)")


if __name__ == "__main__":
    main()
