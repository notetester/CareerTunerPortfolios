# 프로필 AI 학습 데이터 스키마

이 문서는 `profile_ai_seed_samples.jsonl`의 데이터 형식을 설명합니다.

## 1. JSONL 한 줄 구조

한 줄은 하나의 학습 샘플입니다.

```json
{
  "messages": [
    {
      "role": "system",
      "content": "모델 역할 설명"
    },
    {
      "role": "user",
      "content": "사용자 프로필과 서버 평가 기준"
    },
    {
      "role": "assistant",
      "content": "CareerTuner가 원하는 결과 JSON"
    }
  ]
}
```

## 2. user 메시지에 넣을 내용

`user` 메시지에는 사용자 프로필과 서버가 분류한 직무군을 함께 넣습니다.

```json
{
  "featureType": "PROFILE_COMPLETENESS",
  "jobFamily": "SALES_MARKETING",
  "profile": {
    "desiredJob": "콘텐츠 마케터",
    "desiredIndustry": "커머스",
    "education": [],
    "career": [],
    "projects": [],
    "skills": [],
    "certificates": [],
    "languages": [],
    "portfolioLinks": [],
    "resumeText": "",
    "selfIntro": "",
    "preferences": {}
  }
}
```

서버가 이미 직무군을 분류한다는 전제로, 학습 데이터에는 `jobFamily`를 입력 컨텍스트로 넣습니다.
모델은 직무군을 다시 마음대로 바꾸는 것이 아니라, 주어진 직무군 기준으로 평가합니다.

## 3. assistant 메시지에 넣을 내용

`assistant` 메시지는 반드시 아래 JSON 형태여야 합니다.

```json
{
  "summary": "프로필 요약",
  "extractedSkills": ["추출 역량"],
  "strengths": ["강점"],
  "gaps": ["보완점"],
  "recommendations": ["추천 액션"],
  "criterionScores": [
    {
      "criterion": "GOAL_CLARITY",
      "rawScore": 80,
      "evidence": "근거",
      "improvement": "개선 방향"
    },
    {
      "criterion": "EXPERIENCE_SPECIFICITY",
      "rawScore": 70,
      "evidence": "근거",
      "improvement": "개선 방향"
    },
    {
      "criterion": "ACHIEVEMENT_EVIDENCE",
      "rawScore": 60,
      "evidence": "근거",
      "improvement": "개선 방향"
    },
    {
      "criterion": "JOB_SKILL_ALIGNMENT",
      "rawScore": 75,
      "evidence": "근거",
      "improvement": "개선 방향"
    },
    {
      "criterion": "DOCUMENT_CONSISTENCY",
      "rawScore": 70,
      "evidence": "근거",
      "improvement": "개선 방향"
    },
    {
      "criterion": "IMPROVEMENT_READINESS",
      "rawScore": 80,
      "evidence": "근거",
      "improvement": "개선 방향"
    }
  ]
}
```

## 4. 작성 규칙

- `criterionScores`에는 6개 평가 기준이 모두 있어야 합니다.
- `rawScore`는 0에서 100 사이 정수여야 합니다.
- `summary`는 1문단으로 작성합니다.
- `strengths`, `gaps`, `recommendations`는 각각 2개에서 5개 정도가 적당합니다.
- 추천 액션은 "열심히 하세요"처럼 추상적으로 쓰지 않습니다.
- 추천 액션은 사용자가 바로 수정할 수 있는 문장이어야 합니다.

좋은 추천 예시:

```text
프로젝트 설명에 사용 도구, 담당 역할, 결과 수치를 각각 한 문장씩 추가하세요.
```

나쁜 추천 예시:

```text
더 노력하세요.
```

## 5. 데이터 검수 체크리스트

학습 전에 사람이 반드시 확인해야 할 항목입니다.

- 개인정보가 들어가지 않았는가
- 실제 이메일, 전화번호, 주소가 들어가지 않았는가
- 특정 개인이나 회사를 비방하지 않는가
- 개발 직무 데이터에만 치우치지 않았는가
- 비개발 직무도 충분히 들어갔는가
- AI 응답이 JSON으로 파싱 가능한가
- 서버 검증기가 요구하는 필드가 모두 있는가

