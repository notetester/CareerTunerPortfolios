# 면접 답변 평가 자체 모델 파인튜닝 (D 담당)

면접 답변 평가에 쓰는 **자체 LLM**을 직접 만든다. 학습 데이터(`interview_training_sample`)로 오픈모델
(Qwen2.5-7B-Instruct)을 LoRA 파인튜닝하고, vLLM 으로 서빙해 백엔드 평가에 연결한다.

> 목적은 최고 성능이 아니라 **"우리가 직접 학습해 서비스에 붙였다"는 증거 확보**다(로드맵 5-4).
> 품질이 OpenAI 보다 낮아도 되고, 라이브는 OpenAI 폴백을 유지한다.
> 데스크탑 GPU 확보가 불가능하므로 **시간제 GPU 임대**(RunPod 1순위)를 전제로 한다.

## 0. GPU 서버 (결정 보류 — 팀 확정)

- **RunPod**(1순위 후보): A100 40GB 또는 48GB. 시간제(켜면 과금, 끄면 중단). 7B + 4bit LoRA 는 단일 GPU 로 충분.
- 7B 기준 수천 샘플 학습은 대략 1~2시간 안쪽. **학습 끝나면 인스턴스 즉시 종료**(방치 = 과금).

## 1. 데이터 내보내기 (로컬/백엔드)

관리자 토큰으로 학습 데이터를 JSONL 로 받는다.

```bash
curl -H "Authorization: Bearer <ADMIN_TOKEN>" \
  "http://localhost:8080/api/admin/interview/training/export?limit=5000" -o export.jsonl
```

JSONL 한 줄 = `{"messages": [system, user(질문+답변), assistant(점수·피드백 JSON)]}` 형식이다.

## 2. GPU 서버에서 학습

```bash
pip install -r requirements.txt

# train/val 분리
python prepare_data.py --input export.jsonl --out-dir data --val-ratio 0.1

# LoRA 파인튜닝 (Qwen2.5-7B-Instruct, 4bit)
python finetune_lora.py --train data/train.jsonl --eval data/val.jsonl --output out/interview-lora
```

## 3. 서빙 (vLLM, OpenAI 호환)

```bash
pip install vllm
bash serve_vllm.sh out/interview-lora    # → http://<host>:8000/v1
```

## 4. 백엔드 연결

백엔드 환경변수로 평가 모델을 자체 모델로 전환한다. 미설정/실패 시 자동으로 OpenAI 로 폴백한다.

```bash
INTERVIEW_EVAL_PROVIDER=oss
INTERVIEW_EVAL_BASE_URL=http://<gpu-host>:8000/v1
INTERVIEW_EVAL_MODEL=careertuner-interview
# 자체 서버에 인증을 걸었다면: INTERVIEW_EVAL_API_KEY=...
```

연결 지점: `backend/.../interview/service/OssAnswerEvaluator.java` (`/chat/completions` 호출).

## 5. 완료 기준 (로드맵 5-4)

- [ ] LoRA 학습 1회 실제 실행
- [ ] vLLM 서빙 → `OssAnswerEvaluator` 로 평가 1회 성공
- [ ] OpenAI vs 자체 모델 평가 **나란히 비교**(점수 차이·일관성) 1장
- [ ] 라이브 실패 시 OpenAI 폴백 확인

## 파일

| 파일 | 역할 |
| --- | --- |
| `requirements.txt` | 학습 의존성 |
| `prepare_data.py` | export JSONL → train/val 분리 |
| `finetune_lora.py` | Qwen2.5-7B LoRA 파인튜닝 |
| `serve_vllm.sh` | vLLM 서빙(OpenAI 호환) |
