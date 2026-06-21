# 학습 전 사람 표본검수 샘플 (mixed)

> 총 30건 = IT/SW 15 + 비IT 15. APPLY/COMPLEMENT/HOLD 고르게 포함. 학습 전 teacher 출력을 사람이 검수한다.

## 검수 포인트(각 샘플 공통)

1. 입력에 없는 회사/역량/자격증을 만들었는가?
2. 부족역량을 보유역량처럼 말했는가?
3. HOLD인데 지원 권장처럼 말하는가?
4. 비IT 직군에 IT 표현이 섞였는가?
5. 한국어 문장이 발표/시연에 쓸 만큼 자연스러운가?

---

## IT/SW 샘플

### 1. `mix_0261` · IT_SOFTWARE · SRE
- fitScore: **84** · applyDecision: **APPLY** · 경력: 주니어(1~3년) · 회사: 온클라우드
- matchedSkills: CI/CD, Linux, Bash, Terraform, Docker
- missingRequiredSkills: (없음)
- missingPreferredSkills: Prometheus, Nginx
- assistant 출력:
```json
{
  "fitSummary": "CI/CD·Linux·Bash·Terraform 필수 요건을 전부 보유하고 우대 역량인 Docker까지 갖춰 공고 요건 충족률이 매우 높습니다. AWS 솔루션스 아키텍트 어소시에이트 자격증은 외국계 기업 인사 담당자에게 특히 설득력 있는 신호가 됩니다. Prometheus·Nginx 우대 역량을 보완하면 모니터링과 웹 서버 운영 전반을 커버하는 완성도 높은 지원서를 만들 수 있습니다.",
  "strengths": [
    "CI/CD·Linux·Bash·Terraform 전체 보유로 배포 자동화와 인프라 코드화 업무에 즉각 기여가 가능합니다.",
    "Docker 경험까지 갖춰 컨테이너 기반 애플리케이션 운영 환경에서 거부감 없이 적응할 수 있습니다.",
    "AWS 솔루션스 아키텍트 어소시에이트 자격증으로 클라우드 아키텍처 설계 역량이 공식 검증되어 있어 외국계 SRE 포지션에서 경쟁력이 높습니다."
  ],
  "risks": [
    "Prometheus 경험이 우대 역량으로 명시되어 있으나 확인되지 않아, 모니터링 체계 구축 업무에서 러닝커브가 생길 수 있습니다.",
    "Nginx 설정 경험이 없으면 웹 서버 레이어에서의 트래픽 제어·SSL 설정 업무에 즉각 기여하기 어렵습니다."
  ],
  "strategyActions": [
    "Prometheus와 Grafana를 연동해 직접 구축한 CI/CD 환경의 메트릭을 수집하고 대시보드를 만드는 실습을 진행하세요.",
    "Nginx를 리버스 프록시로 구성하는 실습 환경을 만들고, 기존 Docker·Linux 경험과 연계해 전체 서비스 스택을 직접 운영해 보세요."
  ],
  "learningTaskReasons": [
    {
      "skill": "Prometheus",
      "why": "SRE의 핵심 업무인 모니터링·경보 체계 구축에 가장 널리 쓰이는 도구로, 실습 경험이 있으면 온보딩 즉시 성과를 낼 수 있습니다."
    },
    {
      "skill": "Nginx",
      "why": "인프라 안정성 개선 업무에서 웹 서버 레이어 이해는 필수이며, IT/SaaS 환경에서 Nginx 설정 능력은 자주 요구됩니다."
    }
  ]
}
```
- 검수: [ ]1 입력외사실  [ ]2 부족역량 오인  [ ]3 HOLD 권장오류  [ ]4 IT표현 누출  [ ]5 문장 자연스러움

### 2. `mix_0171` · IT_SOFTWARE · 프론트엔드 개발자
- fitScore: **80** · applyDecision: **APPLY** · 경력: 신입 · 회사: LumiSoft
- matchedSkills: Redux, TypeScript, React, Git, Next.js, HTML/CSS, Vite
- missingRequiredSkills: (없음)
- missingPreferredSkills: JavaScript, GraphQL
- assistant 출력:
```json
{
  "fitSummary": "필수 역량인 Redux, TypeScript, React, Git, Next.js를 빠짐없이 갖추고 있으며, 우대 항목인 HTML/CSS와 Vite까지 프로필에 포함되어 있어 신입 프론트엔드 개발자로서 매우 탄탄한 역량을 보유하고 있습니다. 미보유 항목은 JavaScript와 GraphQL 두 가지뿐으로, 전반적인 기술 준비도가 높은 지원입니다.",
  "strengths": [
    "Redux, React, TypeScript, Next.js, Git, HTML/CSS, Vite 등 공고에서 요구하는 필수·우대 역량을 폭넓게 보유해 즉시 실무 투입이 가능한 수준입니다.",
    "Next.js와 Vite를 함께 다룰 수 있어 SSR 환경과 빠른 번들링 환경 모두에 적응할 수 있는 유연성을 보여줍니다.",
    "Java와 Python 경험이 있어 백엔드 팀과의 협업 시 API 구조 이해와 커뮤니케이션에서 강점을 발휘할 수 있습니다."
  ],
  "risks": [
    "JavaScript가 프로필에 명시되지 않아, TypeScript 이전의 JavaScript 실무 경험 여부를 면접에서 직접 검증받을 가능성이 있습니다.",
    "GraphQL 경험이 없어 API 연동 방식이 GraphQL 중심인 프로젝트에 합류할 경우 초기 학습 비용이 발생할 수 있습니다."
  ],
  "strategyActions": [
    "JavaScript 실무 경험을 구체적인 프로젝트 사례로 정리해 면접에서 TypeScript 전환 과정과 함께 자연스럽게 설명할 수 있도록 준비하세요.",
    "GraphQL 공식 문서와 Apollo Client 기초를 학습하고, 기존 React 프로젝트에 간단한 GraphQL 쿼리를 연동하는 예제를 만들어 두면 즉각적인 활용 의지를 보여줄 수 있습니다."
  ],
  "learningTaskReasons": [
    {
      "skill": "JavaScript",
      "why": "TypeScript 기반 개발자라도 JS 원본 동작 방식에 대한 이해가 면접 기술 검증에서 자주 확인되는 항목입니다."
    },
    {
      "skill": "GraphQL",
      "why": "외국계 IT/SaaS 기업에서 REST 대신 GraphQL을 채택하는 사례가 늘고 있어, 기초 학습만으로도 지원 직군 범위를 넓힐 수 있습니다."
    }
  ]
}
```
- 검수: [ ]1 입력외사실  [ ]2 부족역량 오인  [ ]3 HOLD 권장오류  [ ]4 IT표현 누출  [ ]5 문장 자연스러움

### 3. `mix_0182` · DATA_AI · AI 엔지니어
- fitScore: **86** · applyDecision: **APPLY** · 경력: 미들(4~7년) · 회사: 페어웍스
- matchedSkills: CUDA, Docker, MLflow, scikit-learn, PyTorch, TensorFlow
- missingRequiredSkills: (없음)
- missingPreferredSkills: Python, LLM
- assistant 출력:
```json
{
  "fitSummary": "페어웍스 AI 엔지니어 포지션에서 요구하는 필수 역량을 모두 충족하고 있어 매우 강한 지원 후보입니다. CUDA, Docker, MLflow, scikit-learn, PyTorch, TensorFlow까지 전부 보유하고 있어 모델 학습·배포·실험 관리 전 사이클을 커버할 수 있음을 증명할 수 있습니다.",
  "strengths": [
    "CUDA, PyTorch, TensorFlow, scikit-learn을 모두 보유해 다양한 프레임워크에서의 모델 개발 경험 입증 가능",
    "MLflow 보유로 실험 추적·모델 버전 관리 등 MLOps 실무 역량 확보",
    "Docker 보유로 모델 서빙 환경 컨테이너화 역량까지 갖춤"
  ],
  "risks": [
    "Python이 우대 항목에 있으나 프로필에 명시되지 않아 명시적인 Python 사용 경험 서술이 필요",
    "LLM 관련 경험이 없어 최신 거대 언어모델 응용 역량은 검증되지 않음"
  ],
  "strategyActions": [
    "이력서와 포트폴리오에 Python 기반 개발 비중을 구체적으로 서술해 Python 역량을 명확히 어필",
    "오픈소스 LLM 파인튜닝 또는 RAG 파이프라인 프로젝트를 진행해 LLM 실무 경험을 추가"
  ],
  "learningTaskReasons": [
    {
      "skill": "LLM",
      "why": "AI 엔지니어링 시장에서 LLM 관련 경험이 빠르게 필수화되고 있어, 우대 항목이더라도 보유 시 경쟁에서 명확한 우위를 점할 수 있음"
    },
    {
      "skill": "Python",
      "why": "모든 AI 프레임워크의 기반 언어인 만큼 이력서에 명시적으로 언급되지 않으면 평가자 입장에서 의문이 생길 수 있어, 작성한 코드를 공개 레포로 증명하는 것이 효과적"
    }
  ]
}
```
- 검수: [ ]1 입력외사실  [ ]2 부족역량 오인  [ ]3 HOLD 권장오류  [ ]4 IT표현 누출  [ ]5 문장 자연스러움

### 4. `mix_0025` · IT_SOFTWARE · 웹 개발자
- fitScore: **81** · applyDecision: **APPLY** · 경력: 미들(4~7년) · 회사: 스마트커머스
- matchedSkills: Docker, React, Git
- missingRequiredSkills: (없음)
- missingPreferredSkills: Spring Boot, MySQL
- assistant 출력:
```json
{
  "fitSummary": "필수 역량인 Docker·React·Git을 세 가지 모두 충족하고 있어 공고가 원하는 핵심 개발 역량을 온전히 갖춘 상태입니다. 미들급 경력 기준에도 부합하며, 추가 보유 기술인 GraphQL·Python·Linux 등이 업무 폭을 넓혀 줍니다. 우대 기술인 Spring Boot·MySQL을 보완하면 지원 경쟁력이 더욱 탄탄해집니다.",
  "strengths": [
    "Docker·React·Git 필수 기술을 모두 보유하여 배포 자동화부터 프런트엔드 개발까지 일관된 역할 수행이 가능합니다.",
    "Linux와 GraphQL 경험은 외국계 환경에서 인프라 이해 및 유연한 API 설계 역량으로 차별화될 수 있습니다.",
    "SQLD 자격증과 Python 보유로 데이터 처리 보조 업무까지 유연하게 참여할 수 있습니다."
  ],
  "risks": [
    "우대 기술인 Spring Boot 경험이 없어 백엔드 업무 지원이 필요한 상황에서는 별도 온보딩이 필요할 수 있습니다.",
    "우대 기술인 MySQL 경험이 확인되지 않아 DB 연동 설계 업무에서 팀의 도움이 필요할 수 있습니다."
  ],
  "strategyActions": [
    "Spring Boot와 MySQL을 연동한 간단한 REST API 프로젝트를 구성하면 우대 기술 두 개를 동시에 보완할 수 있습니다.",
    "기존 Docker 경험과 결합하여 컨테이너화된 Spring Boot 앱을 배포하는 예제를 만들면 포트폴리오 스토리도 강화됩니다."
  ],
  "learningTaskReasons": [
    {
      "skill": "Spring Boot",
      "why": "우대 기술이지만 풀스택 직무에서 백엔드 기여 범위를 넓히는 핵심 스택으로, 보완하면 팀 내 역할이 확장됩니다."
    },
    {
      "skill": "MySQL",
      "why": "SQLD 자격증 보유로 기본 개념은 갖춰져 있어 실무 수준 연동 실습에 드는 시간이 적고, 우대 항목 완성에 바로 이어집니다."
    }
  ]
}
```
- 검수: [ ]1 입력외사실  [ ]2 부족역량 오인  [ ]3 HOLD 권장오류  [ ]4 IT표현 누출  [ ]5 문장 자연스러움

### 5. `mix_0095` · DATA_AI · BI 분석가
- fitScore: **86** · applyDecision: **APPLY** · 경력: 주니어(1~3년) · 회사: 해치커머스
- matchedSkills: Python, SQL, Pandas, 통계, Spark
- missingRequiredSkills: (없음)
- missingPreferredSkills: Airflow
- assistant 출력:
```json
{
  "fitSummary": "Python, SQL, Pandas, 통계, Spark까지 필수·우대 기술 대부분을 보유하고 있어 데이터 수집·분석·파이프라인 업무에 즉시 기여할 수 있는 수준입니다. SQLD 자격증까지 더해 데이터 역량의 신뢰도를 높여줍니다. 우대 기술 중 Airflow 하나만 채우면 공고 요건을 거의 완벽하게 충족하는 강력한 지원 프로필이 됩니다.",
  "strengths": [
    "Python·SQL·Pandas·통계를 모두 보유하여 데이터 수집부터 분석·시각화까지 엔드투엔드 역량을 갖추고 있습니다.",
    "Spark 경험이 있어 대용량 데이터 파이프라인 구축 역량까지 입증 가능합니다.",
    "SQLD 자격증이 데이터베이스 이해도를 공식적으로 뒷받침하며, Git·Redis 경험으로 개발 협업 환경 적응력도 보여줍니다."
  ],
  "risks": [
    "Airflow가 없으면 스케줄링 기반 파이프라인 자동화 업무에서 초기 적응 시간이 필요합니다."
  ],
  "strategyActions": [
    "Airflow를 로컬 Docker 환경에서 설치하고, 기존 Python 데이터 처리 스크립트를 DAG로 전환하는 실습을 완료하세요.",
    "Airflow로 Spark 작업을 오케스트레이션하는 파이프라인 예시를 포트폴리오에 추가하면 두 기술을 동시에 어필할 수 있습니다."
  ],
  "learningTaskReasons": [
    {
      "skill": "Airflow",
      "why": "파이프라인 구축이 이 직무의 핵심 책임이며, Airflow는 공고에서 직접 언급된 우대 도구입니다. 이미 Python과 Spark를 갖추고 있어 학습 장벽이 낮습니다."
    }
  ]
}
```
- 검수: [ ]1 입력외사실  [ ]2 부족역량 오인  [ ]3 HOLD 권장오류  [ ]4 IT표현 누출  [ ]5 문장 자연스러움

### 6. `mix_0173` · IT_SOFTWARE · Java 백엔드 개발자
- fitScore: **75** · applyDecision: **COMPLEMENT_BEFORE_APPLY** · 경력: 주니어(1~3년) · 회사: 오픈클라우드
- matchedSkills: Java, JUnit, Kafka, Redis
- missingRequiredSkills: (없음)
- missingPreferredSkills: JPA, MySQL, Spring Boot, Git
- assistant 출력:
```json
{
  "fitSummary": "Java, JUnit, Kafka, Redis 등 이 공고의 필수 역량을 전부 보유하고 있어 기술 적합도 자체는 높습니다. 다만 우대 항목인 JPA, MySQL, Spring Boot, Git이 없어 실무에서 자주 사용되는 도구들의 경험 공백이 있으며, 이를 보완한 뒤 지원하면 더 설득력 있는 이력서를 만들 수 있습니다.",
  "strengths": [
    "Kafka와 Redis를 함께 보유해 메시징 큐와 캐싱 레이어 설계·운영 역량을 어필할 수 있으며, 공공 클라우드 환경의 고성능 API 서비스에 바로 기여할 수 있습니다.",
    "Java와 JUnit 보유로 코드 품질 및 테스트 작성 능력을 신뢰 있게 입증할 수 있습니다.",
    "빅데이터분석기사 자격증은 데이터 처리 관련 부가 역량을 공식적으로 증명합니다."
  ],
  "risks": [
    "Spring Boot가 우대 항목에 없어, Java 생태계에서 가장 보편적인 웹 프레임워크 경험을 면접에서 증명하지 못할 수 있습니다.",
    "JPA와 MySQL이 모두 없어 데이터 모델링과 ORM 기반 개발 경험에 대한 추가 검증이 필요할 가능성이 있습니다.",
    "Git 부재는 코드 협업 이력 자체를 보여주기 어렵게 만들어, 주니어 직군에서는 특히 불리하게 작용할 수 있습니다."
  ],
  "strategyActions": [
    "Spring Boot를 사용해 간단한 REST API 서버를 구성하고, JPA와 MySQL을 연동하는 CRUD 예제를 GitHub에 공개 저장소로 올려 포트폴리오로 제시하세요.",
    "Git 기본 워크플로(브랜치, PR, 머지)를 익히고 기존 프로젝트를 GitHub에 정리해 코드 협업 이력을 가시화하세요."
  ],
  "learningTaskReasons": [
    {
      "skill": "Spring Boot",
      "why": "Java 백엔드 개발의 사실상 표준 프레임워크로, 보유한 Java 역량과 결합해 업무 투입 속도를 크게 높일 수 있습니다."
    },
    {
      "skill": "Git",
      "why": "코드 이력 관리와 팀 협업의 기본 도구로, 주니어 개발자 지원 시 GitHub 포트폴리오와 함께 가장 먼저 확인되는 항목입니다."
    }
  ]
}
```
- 검수: [ ]1 입력외사실  [ ]2 부족역량 오인  [ ]3 HOLD 권장오류  [ ]4 IT표현 누출  [ ]5 문장 자연스러움

### 7. `mix_0088` · IT_SOFTWARE · 웹 개발자
- fitScore: **69** · applyDecision: **COMPLEMENT_BEFORE_APPLY** · 경력: 주니어(1~3년) · 회사: 그린랩스
- matchedSkills: MySQL, JavaScript, React, REST API, Spring Boot
- missingRequiredSkills: TypeScript
- missingPreferredSkills: Node.js, Git, Docker
- assistant 출력:
```json
{
  "fitSummary": "MySQL·JavaScript·React·REST API·Spring Boot 등 핵심 역량을 고루 갖추고 정보처리기사·SQLD 자격증을 보유한 점은 강점이지만, 필수 스킬인 TypeScript 부재와 Node.js·Git·Docker 우대 스킬 공백이 적합도를 제한하고 있습니다. TypeScript를 중심으로 보완하면 합격 경쟁력이 의미 있게 올라갈 수 있습니다.",
  "strengths": [
    "JavaScript·React·REST API·MySQL을 모두 보유해 프론트·백엔드 풀스택 개발 업무의 핵심 부분을 바로 담당할 수 있습니다.",
    "Spring Boot 경험은 백엔드 API 구조 이해도를 높여 주며, 풀스택 역할 수행 시 강점으로 작용합니다.",
    "정보처리기사와 SQLD 두 자격증을 보유해 개발 전반과 데이터베이스 역량의 공식 신뢰도가 높습니다."
  ],
  "risks": [
    "필수 스킬인 TypeScript가 없어 타입 기반의 대규모 프로젝트 코드 품질 관리가 어려울 수 있습니다.",
    "Git·Docker 우대 스킬 부재로 협업 워크플로우와 컨테이너 기반 배포 업무에서 추가 온보딩이 필요합니다."
  ],
  "strategyActions": [
    "기존 React 프로젝트에 TypeScript를 점진적으로 도입하는 실습을 진행하고, tsconfig 설정부터 컴포넌트 타이핑까지 경험을 쌓으세요.",
    "Git을 이용한 브랜치 전략(Git Flow 또는 GitHub Flow)을 학습하고 팀 협업 프로젝트에 적용한 경험을 정리하세요.",
    "Docker를 이용해 Spring Boot 백엔드를 컨테이너로 패키징하고 로컬에서 실행하는 예제를 구성해 포트폴리오에 포함하세요."
  ],
  "learningTaskReasons": [
    {
      "skill": "TypeScript",
      "why": "필수 요건이므로 보완 우선순위가 가장 높으며, 이미 보유한 JavaScript·React 스킬 위에서 비교적 빠르게 익힐 수 있습니다."
    },
    {
      "skill": "Docker",
      "why": "풀스택 배포 자동화 역량을 갖추면 우대 조건을 충족하고 실무 기여 범위가 넓어집니다."
    }
  ]
}
```
- 검수: [ ]1 입력외사실  [ ]2 부족역량 오인  [ ]3 HOLD 권장오류  [ ]4 IT표현 누출  [ ]5 문장 자연스러움

### 8. `mix_0018` · IT_SOFTWARE · 백엔드 개발자
- fitScore: **68** · applyDecision: **COMPLEMENT_BEFORE_APPLY** · 경력: 미들(4~7년) · 회사: 넥스트커머스
- matchedSkills: MySQL, Git, Spring Boot, Redis, JUnit
- missingRequiredSkills: Java
- missingPreferredSkills: JPA
- assistant 출력:
```json
{
  "fitSummary": "Spring Boot·MySQL·Git·Redis·JUnit 등 주요 필수 및 우대 역량을 폭넓게 갖추고 있어 백엔드 업무의 상당 부분을 바로 수행할 수 있습니다. 필수 역량 중 Java가 빠져 있다는 점이 아쉬운 부분으로, Spring Boot를 실무에서 사용했다면 Java 기초는 병행 학습이 빠를 것입니다. JPA도 우대 항목으로 남아 있어 지원 전 보완하면 더 안정적인 지원이 됩니다.",
  "strengths": [
    "Spring Boot·MySQL·Redis·JUnit을 실제로 보유하고 있어 API 개발, 성능 최적화, 테스트 자동화 모두 즉시 기여 가능합니다.",
    "Git을 활용한 협업 경험이 있어 팀 기반 개발 환경에 빠르게 적응할 수 있습니다.",
    "Pandas 보유로 데이터 분석·검증 작업도 보조적으로 수행 가능해 팀 내 활용 범위가 넓습니다."
  ],
  "risks": [
    "필수 기술인 Java 경험이 공식적으로 확인되지 않아 코드 리뷰 및 기존 레거시 코드베이스 이해에 초기 어려움이 생길 수 있습니다.",
    "우대 기술인 JPA를 보유하지 않아 ORM 기반 데이터 모델링 업무에서 추가 온보딩이 필요할 수 있습니다."
  ],
  "strategyActions": [
    "Spring Boot 프로젝트의 Java 코드 비중을 높이는 토이 프로젝트를 추가하여 Java 역량을 이력서에서 구체적으로 서술하세요.",
    "JPA와 Spring Data JPA 기초 튜토리얼을 진행하고 실제 CRUD 애플리케이션을 구현해 포트폴리오에 추가하면 우대 항목도 충족할 수 있습니다."
  ],
  "learningTaskReasons": [
    {
      "skill": "Java",
      "why": "Spring Boot 사용 경험이 있으므로 Java 문법 및 스프링 내부 코드 이해는 상대적으로 빠릅니다. 필수 항목인 만큼 학습 결과를 명시적으로 증명해야 합니다."
    },
    {
      "skill": "JPA",
      "why": "대기업 IT/SaaS 환경에서는 JPA 기반 데이터 모델링이 일반적이므로, 이를 보완하면 업무 적응 속도가 빨라집니다."
    }
  ]
}
```
- 검수: [ ]1 입력외사실  [ ]2 부족역량 오인  [ ]3 HOLD 권장오류  [ ]4 IT표현 누출  [ ]5 문장 자연스러움

### 9. `mix_0040` · IT_SOFTWARE · 풀스택 개발자
- fitScore: **65** · applyDecision: **COMPLEMENT_BEFORE_APPLY** · 경력: 미들(4~7년) · 회사: 온데이터
- matchedSkills: TypeScript, REST API, MySQL, Docker, React
- missingRequiredSkills: Spring Boot
- missingPreferredSkills: JavaScript, Git
- assistant 출력:
```json
{
  "fitSummary": "TypeScript·REST API·MySQL·Docker·React를 보유해 풀스택 업무의 프론트엔드와 인프라 측면은 잘 갖춰져 있습니다. 그러나 필수 항목인 Spring Boot가 빠져 있어 백엔드 개발 업무에서는 즉시 기여가 어려울 수 있습니다. 우대 항목인 JavaScript와 Git도 부재해, 이 두 가지도 함께 보완한다면 지원 경쟁력이 눈에 띄게 높아질 것입니다.",
  "strengths": [
    "TypeScript·REST API·MySQL을 모두 보유해 데이터 모델링과 API 연동 중심의 풀스택 업무에서 즉시 역할을 맡을 수 있습니다.",
    "Docker 보유로 컨테이너 기반 배포 환경 구성과 운영에 바로 기여 가능합니다.",
    "React 경험이 있어 프론트엔드 UI 개발 업무를 독립적으로 수행할 수 있습니다."
  ],
  "risks": [
    "필수 기술인 Spring Boot가 없어 Java 백엔드 API 개발 업무를 초기에 독립적으로 맡기 어려울 수 있습니다.",
    "우대 항목인 JavaScript와 Git이 없어 협업 환경의 버전 관리와 동적 스크립팅 업무에서 일부 공백이 생길 수 있습니다."
  ],
  "strategyActions": [
    "Spring Boot로 REST API를 설계하고 MySQL과 연동하는 토이 프로젝트를 완성해 보유 중인 TypeScript·REST API 역량과의 시너지를 포트폴리오에서 보여주세요.",
    "Git 기반의 협업 워크플로(브랜치 전략, PR 리뷰 등)를 실제 팀 프로젝트나 오픈소스 기여를 통해 경험으로 정리해두세요."
  ],
  "learningTaskReasons": [
    {
      "skill": "Spring Boot",
      "why": "외국계 제조업 풀스택 포지션에서 Spring Boot는 필수 요건이므로, 백엔드 역량의 공백을 메우는 가장 우선순위 높은 학습 과제입니다."
    },
    {
      "skill": "JavaScript",
      "why": "TypeScript를 사용하더라도 JavaScript 기초 이해는 디버깅과 라이브러리 호환성 처리에서 반드시 필요하며, 우대 항목 충족에도 직접적으로 연결됩니다."
    }
  ]
}
```
- 검수: [ ]1 입력외사실  [ ]2 부족역량 오인  [ ]3 HOLD 권장오류  [ ]4 IT표현 누출  [ ]5 문장 자연스러움

### 10. `mix_0187` · IT_SOFTWARE · 웹 개발자
- fitScore: **68** · applyDecision: **COMPLEMENT_BEFORE_APPLY** · 경력: 신입 · 회사: 노바랩스
- matchedSkills: Git, TypeScript, Docker, MySQL, Spring Boot
- missingRequiredSkills: JavaScript
- missingPreferredSkills: React
- assistant 출력:
```json
{
  "fitSummary": "노바랩스 웹 개발자 포지션에 필요한 역량 상당 부분을 이미 갖추고 있어 신입 지원자로서 경쟁력이 있는 편입니다. Git, TypeScript, Docker, MySQL, Spring Boot까지 매칭되어 프론트·백엔드 전반의 실무 기반을 보여줄 수 있습니다. JavaScript와 React를 보완하면 서류 완성도가 더욱 높아질 것입니다.",
  "strengths": [
    "TypeScript, Docker, Git을 보유해 현대적인 개발·배포 환경 기본기가 탄탄함",
    "MySQL, Spring Boot까지 갖춰 백엔드 API 개발과 DB 운영 역량도 동시에 증빙 가능",
    "정보처리기사 자격증으로 신입 지원자로서 기술 역량을 공식 인정받음"
  ],
  "risks": [
    "JavaScript가 명시되지 않아 프론트엔드 기본 언어 역량이 서류상 공백",
    "React 미기재로 우대 항목 SPA 개발 경험이 확인되지 않음"
  ],
  "strategyActions": [
    "이력서에 JavaScript 활용 경험(프로젝트·업무)을 구체적으로 명시하고, TypeScript와의 연계를 서술",
    "React로 간단한 포트폴리오 사이트나 토이 프로젝트를 제작해 GitHub에 공개"
  ],
  "learningTaskReasons": [
    {
      "skill": "JavaScript",
      "why": "TypeScript를 사용한다면 사실상 JavaScript를 알고 있겠지만, 이력서에 명시하지 않으면 평가자가 의문을 가질 수 있어 반드시 기재·증빙 필요"
    },
    {
      "skill": "React",
      "why": "프론트엔드 직군에서 가장 광범위하게 사용되는 라이브러리로, 우대 항목이더라도 보유 시 풀스택 포지션 경쟁력이 눈에 띄게 높아짐"
    }
  ]
}
```
- 검수: [ ]1 입력외사실  [ ]2 부족역량 오인  [ ]3 HOLD 권장오류  [ ]4 IT표현 누출  [ ]5 문장 자연스러움

### 11. `mix_0154` · DATA_AI · 머신러닝 엔지니어
- fitScore: **28** · applyDecision: **HOLD** · 경력: 신입 · 회사: OpenCommerce
- matchedSkills: Docker, Python, 딥러닝
- missingRequiredSkills: PyTorch, TensorFlow, MLflow, scikit-learn
- missingPreferredSkills: CUDA, LLM
- assistant 출력:
```json
{
  "fitSummary": "OpenCommerce 머신러닝 엔지니어 공고에서 필수 기술 5개 중 Docker 1개만 직접 매칭되며, PyTorch·TensorFlow·MLflow·scikit-learn이 프로필에 없어 현재 수준에서 즉시 지원하기는 어렵습니다. 딥러닝과 Python 역량, ADsP·빅데이터분석기사 자격증이 기본 토대가 되지만, 실제 모델 학습 및 서빙 프레임워크 경험을 쌓는 것이 선행되어야 합니다. 지금은 핵심 프레임워크를 집중적으로 학습하고 재도전을 준비하는 시기로 삼는 것이 현실적입니다.",
  "strengths": [
    "Python과 딥러닝 역량을 보유하고 있어 AI 학습의 기반 환경은 갖추고 있으며, 이를 발판으로 프레임워크 습득 속도를 높일 수 있습니다.",
    "ADsP와 빅데이터분석기사 자격증은 데이터 및 AI 분야에 대한 공식 역량 증명으로, 학습 의지와 기반 지식을 어필하는 데 유용합니다.",
    "Docker 경험이 있어 모델 서빙 및 컨테이너 기반 배포 환경에 대한 기초 이해가 있습니다."
  ],
  "risks": [
    "PyTorch, TensorFlow, MLflow, scikit-learn 등 필수 기술 4개가 모두 누락되어 있어 모델 학습·실험 관리·평가 역량이 서면에서 확인되지 않습니다.",
    "CUDA와 LLM 경험도 없어 우대 요건까지 고려하면 현재 프로필과 공고 간 격차가 상당합니다.",
    "신입 포지션이지만 요구하는 프레임워크 스펙이 높아, 프로젝트 경험 없이는 면접 진입 자체가 어렵습니다."
  ],
  "strategyActions": [
    "PyTorch 또는 TensorFlow를 우선 선택해 공개 데이터셋으로 분류·회귀 모델을 학습하고, MLflow로 실험 결과를 기록하는 미니 프로젝트를 완성해 깃허브에 공개하세요.",
    "scikit-learn 기반 전처리 파이프라인을 구성하는 실습을 통해 머신러닝 기초 프레임워크 경험을 쌓고 포트폴리오에 추가하세요."
  ],
  "learningTaskReasons": [
    {
      "skill": "PyTorch",
      "why": "이 공고의 필수 기술이며 딥러닝 모델 학습의 핵심 프레임워크로, 이미 보유한 딥러닝 지식과 Python을 기반으로 상대적으로 빠르게 습득할 수 있습니다."
    },
    {
      "skill": "MLflow",
      "why": "실험 추적과 모델 버전 관리를 위한 도구로 필수 요건에 포함되어 있으며, 학습 진입 장벽이 낮아 PyTorch 프로젝트와 병행해 실습하기에 적합합니다."
    }
  ]
}
```
- 검수: [ ]1 입력외사실  [ ]2 부족역량 오인  [ ]3 HOLD 권장오류  [ ]4 IT표현 누출  [ ]5 문장 자연스러움

### 12. `mix_0235` · DATA_AI · 데이터 분석가
- fitScore: **48** · applyDecision: **HOLD** · 경력: 신입 · 회사: 루미웍스
- matchedSkills: dbt, SQL, Airflow
- missingRequiredSkills: Pandas, 통계
- missingPreferredSkills: 데이터시각화
- assistant 출력:
```json
{
  "fitSummary": "dbt, SQL, Airflow를 보유하고 있어 필수 역량 절반을 충족하지만, Pandas와 통계가 없어 데이터 전처리와 분석 역량 면에서 중요한 공백이 있습니다. SQLD와 ADP 자격증은 데이터 학습 의지를 보여주나, 신입 포지션에서 실무 도구 역량이 아직 부족하다는 인상을 줄 수 있어 지원 전 보완이 필요합니다.",
  "strengths": [
    "dbt와 SQL을 모두 보유하고 있어 데이터 변환 파이프라인 구축의 핵심 업무를 담당할 수 있습니다.",
    "Airflow 경험이 있어 데이터 수집·전처리 파이프라인의 스케줄링과 오케스트레이션 업무에 기여할 수 있습니다.",
    "ADP와 SQLD 자격증을 보유하고 있어 데이터 분석 전반의 이론적 기초가 갖춰져 있음을 증명할 수 있습니다."
  ],
  "risks": [
    "필수 역량인 Pandas가 없어 Python 기반 데이터 전처리와 분석 업무를 바로 수행하기 어렵습니다.",
    "필수 역량인 통계 지식이 프로필에 없어, 지표 설계와 데이터 해석 업무에서 근거 있는 분석을 제공하기 어려울 수 있습니다.",
    "우대 항목인 데이터시각화가 없어 대시보드 작성이나 인사이트 전달 업무에서 추가적인 도움이 필요할 수 있습니다."
  ],
  "strategyActions": [
    "Pandas를 이용해 실제 공개 데이터셋을 전처리하고 분석한 Jupyter 노트북을 작성해 포트폴리오에 추가하세요.",
    "기초 통계(기술통계, 가설검정, 회귀분석)를 Python으로 직접 계산하는 실습을 통해 통계 역량을 실무 수준으로 끌어올리세요.",
    "Pandas 분석 결과를 matplotlib 또는 Plotly로 시각화하는 대시보드를 만들어 데이터시각화 우대 역량도 함께 채우세요."
  ],
  "learningTaskReasons": [
    {
      "skill": "Pandas",
      "why": "데이터 분석 직무에서 Pandas는 Python 생태계의 핵심 라이브러리로, 이것 없이는 전처리와 탐색적 데이터 분석(EDA)을 독립적으로 수행할 수 없습니다."
    },
    {
      "skill": "통계",
      "why": "데이터 분석가가 단순히 데이터를 정리하는 데 그치지 않고 의미 있는 인사이트를 도출하려면 통계적 사고와 검정 방법론이 뒷받침되어야 합니다."
    }
  ]
}
```
- 검수: [ ]1 입력외사실  [ ]2 부족역량 오인  [ ]3 HOLD 권장오류  [ ]4 IT표현 누출  [ ]5 문장 자연스러움

### 13. `mix_0141` · DATA_AI · 데이터 엔지니어
- fitScore: **54** · applyDecision: **HOLD** · 경력: 미들(4~7년) · 회사: 블루커머스
- matchedSkills: Pandas, BigQuery, Spark, SQL
- missingRequiredSkills: Airflow, Python, dbt
- missingPreferredSkills: (없음)
- assistant 출력:
```json
{
  "fitSummary": "블루커머스 데이터 엔지니어 직무는 미들급(4~7년) 포지션인데, 필수 5개 스킬 중 Airflow·Python·dbt가 빠져 파이프라인 오케스트레이션과 변환 레이어의 핵심 도구 경험이 부족하다. Pandas·BigQuery·Spark·SQL은 보유하고 있어 데이터 처리 자체는 가능하지만, 파이프라인 자동화와 데이터 변환 계층을 직접 구축·운영한 경험을 보완해야 경쟁력이 생긴다.",
  "strengths": [
    "Pandas·BigQuery·SQL 보유로 데이터 분석·조회·클라우드 웨어하우스 운영 역량 입증",
    "Spark 보유로 대용량 배치 처리 환경에서 데이터 처리 가능함을 보여줄 수 있음",
    "빅데이터분석기사 자격증이 데이터 처리 분야 전문성을 공식적으로 뒷받침"
  ],
  "risks": [
    "Airflow 미보유로 파이프라인 스케줄링·오케스트레이션 실무 경험이 없어 주요 업무 직접 수행 어려움",
    "Python 미보유로 데이터 수집·전처리 스크립트 작성 역량이 이력서상 확인되지 않음",
    "dbt 미보유로 데이터 변환 레이어 설계·관리 경험 부재가 파이프라인 구축 역량 전반에 의문을 남김"
  ],
  "strategyActions": [
    "Python 기초부터 pandas 연계까지 학습하고, Airflow DAG 작성 실습 예제를 공개 리포지토리에 정리",
    "dbt Cloud 무료 플랜을 활용해 BigQuery와 연동된 dbt 변환 모델을 구성해 포트폴리오에 추가"
  ],
  "learningTaskReasons": [
    {
      "skill": "Airflow",
      "why": "데이터 엔지니어링 직무에서 파이프라인 자동화는 핵심 업무이며, Airflow 경험 없이는 미들급 기여가 어렵다"
    },
    {
      "skill": "Python",
      "why": "dbt·Airflow·Spark 모두 Python과 연동되는 경우가 많아, Python 역량 확보가 나머지 미보유 스킬 학습의 기반이 된다"
    }
  ]
}
```
- 검수: [ ]1 입력외사실  [ ]2 부족역량 오인  [ ]3 HOLD 권장오류  [ ]4 IT표현 누출  [ ]5 문장 자연스러움

### 14. `mix_0058` · IT_SOFTWARE · 풀스택 개발자
- fitScore: **25** · applyDecision: **HOLD** · 경력: 미들(4~7년) · 회사: NextCloud
- matchedSkills: React
- missingRequiredSkills: MySQL, REST API, JavaScript, TypeScript
- missingPreferredSkills: Docker, Spring Boot, Git
- assistant 출력:
```json
{
  "fitSummary": "React 하나만 필수 기술에 매칭되고 MySQL·REST API·JavaScript·TypeScript 등 풀스택 업무의 근간이 되는 필수 기술 대부분이 현재 프로필에 없어, 미들급 풀스택 포지션으로서는 준비 수준이 많이 부족한 상황입니다. 정보처리기사·SQLD 자격증이 있어 이론적 기반은 있으나, 실무 스택 전반에 걸친 체계적인 보완이 선행되어야 합니다.",
  "strengths": [
    "React 경험으로 프론트엔드 UI 컴포넌트 개발에 기여할 수 있습니다.",
    "정보처리기사와 SQLD 자격증이 데이터베이스와 소프트웨어 설계 전반의 기초 지식을 공식적으로 뒷받침합니다."
  ],
  "risks": [
    "MySQL과 REST API가 없어 백엔드 API 개발과 데이터 모델링 업무를 독립적으로 담당하기 어렵습니다.",
    "JavaScript와 TypeScript 미경험으로 React 기반 프론트엔드 코드베이스에서도 즉각적인 기여에 한계가 생깁니다.",
    "Docker·Spring Boot·Git 등 우대 기술도 전부 없어 협업 환경 적응에 상당한 시간이 필요할 수 있습니다."
  ],
  "strategyActions": [
    "JavaScript와 TypeScript를 우선 학습하여 React 컴포넌트에 타입을 적용하는 실습을 진행하고, 기존 Redux 경험과 연결해 상태관리 흐름을 완성합니다.",
    "MySQL로 간단한 데이터 모델을 설계하고 REST API 서버를 구현하는 백엔드 미니 프로젝트를 진행하여 풀스택 연결 경험을 쌓습니다.",
    "Git 협업 워크플로우를 실습하고 Docker로 개발 환경을 컨테이너화하는 과정을 추가해 우대 기술 격차를 좁힙니다."
  ],
  "learningTaskReasons": [
    {
      "skill": "MySQL",
      "why": "풀스택 개발자에게 데이터베이스 설계와 쿼리 작성은 백엔드 API 구현의 출발점이며, SQLD 자격증을 보유하고 있어 실습 학습 속도를 높일 수 있는 유리한 조건입니다."
    },
    {
      "skill": "TypeScript",
      "why": "미들급 풀스택 포지션에서 TypeScript는 코드 안전성과 협업 생산성을 높이는 핵심 언어로 자리잡았으며, React와 함께 사용하는 역량이 없으면 기존 코드베이스 기여에 제약이 생깁니다."
    }
  ]
}
```
- 검수: [ ]1 입력외사실  [ ]2 부족역량 오인  [ ]3 HOLD 권장오류  [ ]4 IT표현 누출  [ ]5 문장 자연스러움

### 15. `mix_0149` · IT_SOFTWARE · 웹 개발자
- fitScore: **20** · applyDecision: **HOLD** · 경력: 신입 · 회사: 루미클라우드
- matchedSkills: TypeScript
- missingRequiredSkills: JavaScript, Git, Node.js
- missingPreferredSkills: React, Docker
- assistant 출력:
```json
{
  "fitSummary": "루미클라우드 웹 개발자 공고의 필수 기술 4개 중 TypeScript 1개만 매칭되어 현재 프로필과의 간극이 상당히 큽니다. Git, Node.js, JavaScript 등 웹 개발 기초 역량이 아직 프로필에 뒷받침되지 않아, 지금 바로 지원하기보다는 핵심 기술을 충분히 습득한 뒤 재도전하는 것이 효과적입니다. ADP 자격증은 데이터 역량을 증명하지만 이 공고의 평가 기준과는 거리가 있습니다.",
  "strengths": [
    "TypeScript를 보유하고 있어 필수 역량 일부와 겹치며, 타입 기반 개발에 대한 이해를 갖추고 있습니다.",
    "ADP 자격증은 데이터 분석 역량을 공식적으로 증명하며, 데이터 기반 서비스 개발 포지션으로 방향을 바꿀 때 유용한 자산이 됩니다."
  ],
  "risks": [
    "JavaScript, Git, Node.js 등 필수 기술 3개가 모두 프로필에 없어 서류 통과 가능성이 낮습니다.",
    "우대 기술인 React와 Docker도 보유하지 않아 전반적인 웹 개발 스택 준비 수준이 부족합니다.",
    "신입 포지션임에도 기본 웹 개발 도구 경험이 확인되지 않아 MLflow, BigQuery 중심의 현재 프로필과 이 공고 간 방향성이 다릅니다."
  ],
  "strategyActions": [
    "JavaScript와 Node.js를 학습하고, 간단한 웹 서버 또는 REST API 프로젝트를 만들어 깃허브에 공개해 실습 경험을 쌓으세요.",
    "Git 버전 관리 기초를 익히고, 커밋 히스토리가 있는 개인 프로젝트 저장소를 포트폴리오로 활용하세요.",
    "단기적으로는 현재 보유한 MLflow, BigQuery 역량을 살릴 수 있는 데이터 엔지니어링 계열 공고를 우선 탐색하는 것도 전략적 선택입니다."
  ],
  "learningTaskReasons": [
    {
      "skill": "JavaScript",
      "why": "웹 개발 공고 대부분의 필수 요건이며, 이를 갖추지 않으면 유사 포지션 전체에서 서류 탈락이 반복될 수 있어 가장 먼저 확보해야 할 역량입니다."
    },
    {
      "skill": "Node.js",
      "why": "이 공고의 필수 기술이자 백엔드 웹 개발 진입에 핵심적인 런타임으로, JavaScript 학습과 병행하면 효율적으로 습득할 수 있습니다."
    }
  ]
}
```
- 검수: [ ]1 입력외사실  [ ]2 부족역량 오인  [ ]3 HOLD 권장오류  [ ]4 IT표현 누출  [ ]5 문장 자연스러움

---

## 비IT 샘플

### 1. `mix_0350` · MANUFACTURING_LOGISTICS · 물류 운영 담당
- fitScore: **82** · applyDecision: **APPLY** · 경력: 미들(4~7년) · 회사: 제트웍스
- matchedSkills: 6시그마, SCM, 입출고 관리, 품질 검사
- missingRequiredSkills: (없음)
- missingPreferredSkills: ERP 운영
- assistant 출력:
```json
{
  "fitSummary": "6시그마·SCM·입출고 관리 등 물류 운영 핵심 요건을 전부 갖추고, 우대 역량인 품질 검사까지 보유하고 있어 공고와의 직무 적합성이 높습니다. 생산 계획·데이터 분석·안전 관리 경험이 이력에 더해져 물류·생산 전반을 다룰 수 있는 폭넓은 역량이 강점입니다. ERP 운영 경험을 보완하면 시스템 기반 업무에도 즉시 대응할 수 있게 됩니다.",
  "strengths": [
    "6시그마·SCM·입출고 관리 등 필수 역량 3개 전부 보유하여 물류 운영 업무의 핵심 영역 즉시 담당 가능",
    "품질 검사 경험까지 갖추어 우대 항목 충족도가 높고, 품질·물류 연계 업무에서 강점 발휘 가능",
    "생산 계획·데이터 분석·안전 관리 경험이 물류 운영 전반의 이해도를 뒷받침함"
  ],
  "risks": [
    "ERP 운영 경험이 없어 발주·입출고·재고 관리 등 시스템 기반 업무 처리 시 초기 적응이 필요할 수 있음"
  ],
  "strategyActions": [
    "SAP 또는 더존 물류 모듈 기초 교육을 수강하거나, 현 직장 내 ERP 시스템 사용 기회를 확보하여 실습 이력을 만들어두는 것을 권장"
  ],
  "learningTaskReasons": [
    {
      "skill": "ERP 운영",
      "why": "스타트업이라도 물류 운영 담당자는 발주·재고·입출고 데이터를 ERP 시스템으로 관리하는 경우가 많으며, 기초 조작 능력이 없으면 초기 업무 처리 속도에 영향을 미칠 수 있음"
    }
  ]
}
```
- 검수: [ ]1 입력외사실  [ ]2 부족역량 오인  [ ]3 HOLD 권장오류  [ ]4 IT표현 누출  [ ]5 문장 자연스러움

### 2. `mix_0375` · SALES · 영업관리
- fitScore: **84** · applyDecision: **APPLY** · 경력: 신입 · 회사: 페어모빌리티
- matchedSkills: 고객 관계관리, 견적 산출, 계약 협상, 외국어 커뮤니케이션, 제안서 작성
- missingRequiredSkills: (없음)
- missingPreferredSkills: CRM, 시장 조사
- assistant 출력:
```json
{
  "fitSummary": "고객 관계관리, 견적 산출, 계약 협상, 외국어 커뮤니케이션, 제안서 작성까지 필수 요건을 빠짐없이 보유하고 있어 신입 영업관리 지원자로서 매우 높은 직무 적합도를 갖추고 있습니다. 무역영어·유통관리사 자격증은 패션·뷰티 외국계 기업의 영업 환경에서 즉시 활용할 수 있는 강점이 됩니다. CRM 툴 활용 경험만 추가한다면 완성도 있는 지원이 가능합니다.",
  "strengths": [
    "고객 관계관리부터 견적 산출, 계약 협상까지 영업 핵심 프로세스 전반을 보유하고 있어 신입임에도 현장 투입 준비도가 높습니다.",
    "외국어 커뮤니케이션과 무역영어 자격증을 동시에 갖추어 외국계 패션·뷰티 기업에서 해외 본사 및 바이어와의 소통에 즉각적으로 기여할 수 있습니다.",
    "제안서 작성 역량과 유통관리사 자격증을 통해 유통 채널 구조 이해를 바탕으로 한 설득력 있는 제안 역량을 증명할 수 있습니다."
  ],
  "risks": [
    "우대 조건인 CRM 툴 경험이 없어 고객 데이터 관리와 영업 활동 추적을 시스템적으로 수행하는 업무에서 초기 적응 시간이 필요할 수 있습니다.",
    "시장 조사 역량이 우대 사항으로 포함되어 있으나 보유 이력이 확인되지 않아, 경쟁사 분석이나 트렌드 리포트 작성 경험 어필이 어렵습니다."
  ],
  "strategyActions": [
    "Salesforce, HubSpot 등 대표적인 CRM 툴의 기초 사용법을 익히고, 실습 사례를 자기소개서나 포트폴리오에 간략히 언급하여 지원서 경쟁력을 높이세요.",
    "지원 전 패션·뷰티 시장 트렌드 리포트를 직접 작성해 보고, 시장 조사 기법(설문, 경쟁사 분석 등)을 적용한 경험을 면접 소재로 준비해 두세요."
  ],
  "learningTaskReasons": [
    {
      "skill": "CRM",
      "why": "외국계 기업 영업팀은 고객 접점 데이터를 CRM으로 관리하는 것이 일반적이며, 툴 미숙지는 팀 협업 효율과 성과 보고에 직결되므로 입사 전 기본기를 갖춰두면 유리합니다."
    }
  ]
}
```
- 검수: [ ]1 입력외사실  [ ]2 부족역량 오인  [ ]3 HOLD 권장오류  [ ]4 IT표현 누출  [ ]5 문장 자연스러움

### 3. `mix_0324` · MARKETING · 브랜드 마케터
- fitScore: **84** · applyDecision: **APPLY** · 경력: 미들(4~7년) · 회사: SmartMobility
- matchedSkills: SEO, 메타광고, 포토샵, 콘텐츠 기획, 이메일 마케팅, GA4
- missingRequiredSkills: (없음)
- missingPreferredSkills: CRM, SNS 채널 운영
- assistant 출력:
```json
{
  "fitSummary": "SEO·메타광고·포토샵·콘텐츠 기획 등 필수 역량 전부가 매칭되고 이메일 마케팅·GA4 우대 항목까지 보유하고 있어 핀테크 스타트업 브랜드 마케터 포지션과 높은 적합도를 보입니다. 미들급 경력에 걸맞은 폭넓은 역량 구성으로, 현 프로필만으로도 충분히 경쟁력 있는 지원이 가능합니다. CRM과 SNS 채널 운영 우대 역량을 보완하면 더욱 완성된 지원서를 만들 수 있습니다.",
  "strengths": [
    "SEO·메타광고·포토샵·콘텐츠 기획 필수 역량 4개가 모두 매칭되어 브랜드 마케팅의 기획부터 제작·광고까지 전 과정을 자력으로 수행할 수 있습니다.",
    "이메일 마케팅과 GA4 역량도 보유하고 있어 고객 접점 다변화 및 성과 데이터 기반 의사결정 역량을 함께 증빙할 수 있습니다.",
    "카피라이팅과 구글애즈 역량까지 갖추고 있어 광고 소재 개발과 검색 광고 운영에서도 추가 기여가 가능합니다."
  ],
  "risks": [
    "CRM 툴 활용 경험이 우대 항목으로 제시되어 있으나 보유 이력이 확인되지 않아 고객 데이터 기반 마케팅 자동화 업무에서 기여에 한계가 있을 수 있습니다.",
    "SNS 채널 운영 경험이 우대 항목이나 매칭되지 않아 소셜 채널 콘텐츠 기획 및 커뮤니티 관리 업무에서 상대적으로 약점이 될 수 있습니다."
  ],
  "strategyActions": [
    "Mailchimp나 Stibee 등 마케팅 자동화 툴에서 CRM 연동 기능을 실습하며, 세그먼트별 캠페인 설계와 성과 추적 방식을 익혀두세요.",
    "SNS 채널별(인스타그램·링크드인 등) 콘텐츠 전략 차이와 운영 지표(도달·참여율·팔로워 증가율)를 정리하고, 실제 채널 운영 사례를 포트폴리오에 담아두세요."
  ],
  "learningTaskReasons": [
    {
      "skill": "CRM",
      "why": "브랜드 마케터로서 고객 여정 전반에 걸친 캠페인 효과를 극대화하기 위해 CRM 기반 마케팅 자동화 경험이 점점 중요해지고 있는 역량입니다."
    },
    {
      "skill": "SNS 채널 운영",
      "why": "브랜드 인지도 확산의 핵심 채널로, 우대 항목이지만 핀테크 스타트업에서는 실질적인 브랜드 마케팅 성과를 내는 데 직접 연결됩니다."
    }
  ]
}
```
- 검수: [ ]1 입력외사실  [ ]2 부족역량 오인  [ ]3 HOLD 권장오류  [ ]4 IT표현 누출  [ ]5 문장 자연스러움

### 4. `mix_0299` · MANUFACTURING_LOGISTICS · 자재/구매 담당
- fitScore: **91** · applyDecision: **APPLY** · 경력: 신입 · 회사: 링크페이
- matchedSkills: 안전 관리, 6시그마, 입출고 관리, ERP 운영, 데이터 분석, 공정 관리, 생산 계획
- missingRequiredSkills: (없음)
- missingPreferredSkills: (없음)
- assistant 출력:
```json
{
  "fitSummary": "필수 역량인 안전 관리, 6시그마, 입출고 관리, ERP 운영, 데이터 분석을 모두 보유하고 있으며, 우대 항목인 공정 관리와 생산 계획까지 갖추어 직무 요건을 빠짐없이 충족합니다. 산업안전기사와 지게차운전기능사 자격증도 물류·생산관리 현장에서 즉각적인 신뢰를 줄 수 있는 강점입니다. 신입 지원자로서 이 수준의 역량 완성도는 이례적으로 높아 적극 지원을 권장합니다.",
  "strengths": [
    "안전 관리·6시그마·입출고 관리·ERP 운영·데이터 분석 등 필수 역량을 전부 보유해 직무 즉시 투입이 가능합니다.",
    "산업안전기사 자격증은 물류·생산관리 현장에서 안전 업무를 공식적으로 맡을 수 있는 자격을 증명하며 차별화 포인트가 됩니다.",
    "SCM, 재고 관리 등 우대 조건 외에도 추가 역량을 보유하고 있어 직무 확장성 측면에서도 좋은 평가를 받을 수 있습니다."
  ],
  "risks": [
    "신입 지원자이므로 실무 현장 경험의 깊이와 응용력에 대한 검증이 면접에서 중점적으로 이루어질 수 있습니다."
  ],
  "strategyActions": [
    "보유한 역량과 자격증 각각에 대해 실습·프로젝트·인턴 등 실제 적용 경험을 정리하고, 구체적인 수치와 성과로 면접에서 설명할 수 있도록 준비하세요."
  ],
  "learningTaskReasons": [
    {
      "skill": "공정 관리",
      "why": "이미 보유한 역량이지만, 스타트업 환경에서는 공정 개선 사례를 수치로 보여주는 것이 중요하므로 관련 사례를 구조화해 준비하면 면접 설득력이 높아집니다."
    }
  ]
}
```
- 검수: [ ]1 입력외사실  [ ]2 부족역량 오인  [ ]3 HOLD 권장오류  [ ]4 IT표현 누출  [ ]5 문장 자연스러움

### 5. `mix_0332` · SERVICE_CS · CS 운영 담당
- fitScore: **81** · applyDecision: **APPLY** · 경력: 신입 · 회사: 제트랩스
- matchedSkills: CS 지표 관리, 채팅 상담, 상담 매뉴얼 운영, 컴플레인 처리, VOC 분석, 상담 품질 관리
- missingRequiredSkills: (없음)
- missingPreferredSkills: 프로세스 개선, CRM
- assistant 출력:
```json
{
  "fitSummary": "CS 지표 관리·채팅 상담·상담 매뉴얼 운영·컴플레인 처리·VOC 분석 등 제트랩스 CS 운영 담당의 필수 요건을 모두 갖추고 있고, 우대 항목인 상담 품질 관리까지 보유하고 있어 신입으로서 매우 높은 역량 적합도를 보입니다. 이미 CS 업무 전반을 실전에서 다뤄 본 경험이 입사 직후 빠른 적응을 기대하게 합니다. 프로세스 개선과 CRM 관련 경험을 보완한다면 더욱 경쟁력 있는 지원이 됩니다.",
  "strengths": [
    "필수 요건 5가지(CS 지표 관리·채팅 상담·상담 매뉴얼 운영·컴플레인 처리·VOC 분석)를 전부 충족하여 신입 지원자로서 이례적으로 높은 실무 준비도를 갖추고 있습니다.",
    "우대 항목인 상담 품질 관리 역량까지 보유하고 있어 CS 운영 전반을 두루 소화할 수 있음을 보여 줍니다."
  ],
  "risks": [
    "우대 항목인 프로세스 개선 경험이 없어, 운영 효율화나 매뉴얼 개선 주도 경험에 대한 질문에서 사례 제시가 어려울 수 있습니다.",
    "CRM 툴 활용 경험이 없어 고객 데이터 기반의 체계적 관리 역량을 어필하는 데 한계가 있습니다."
  ],
  "strategyActions": [
    "상담 매뉴얼 개정이나 응대 프로세스 개선에 참여한 경험이 있다면 구체적인 사례(변경 전·후 비교 등)로 자기소개서에 서술하세요.",
    "Freshdesk·Zendesk 등 대표적인 CRM 툴의 무료 체험판을 통해 기본 기능을 익히고, 실습 경험을 이력서에 간략히 기재해 보세요."
  ],
  "learningTaskReasons": [
    {
      "skill": "CRM",
      "why": "광고/미디어 스타트업에서는 고객 데이터를 CRM으로 체계화하여 VOC 분석과 지표 관리에 연결하는 방식이 일반적입니다. CRM 기초 활용 역량을 갖추면 데이터 기반 CS 운영 능력을 더 설득력 있게 어필할 수 있습니다."
    }
  ]
}
```
- 검수: [ ]1 입력외사실  [ ]2 부족역량 오인  [ ]3 HOLD 권장오류  [ ]4 IT표현 누출  [ ]5 문장 자연스러움

### 6. `mix_0343` · SALES · B2B 영업
- fitScore: **68** · applyDecision: **COMPLEMENT_BEFORE_APPLY** · 경력: 미들(4~7년) · 회사: 블루웍스
- matchedSkills: 견적 산출, 제안서 작성, 고객 관계관리, 고객 발굴
- missingRequiredSkills: 영업 파이프라인 관리
- missingPreferredSkills: CRM
- assistant 출력:
```json
{
  "fitSummary": "견적 산출·제안서 작성·고객 발굴·고객 관계관리 등 B2B 영업의 핵심 역할을 충실히 수행해온 이력이 돋보입니다. 다만 영업 파이프라인 관리와 CRM 도구 활용 경험이 미흡하여, 이 부분을 보완하고 지원하는 것이 합격 가능성을 높이는 길입니다. 현재 역량만으로도 경쟁에 참여할 수 있는 수준이지만, 핵심 요건 하나가 빠져 있는 만큼 추가 준비가 권장됩니다.",
  "strengths": [
    "견적 산출·제안서 작성·계약 협상 등 B2B 거래 전 과정 실무 경험이 고르게 갖춰져 있음",
    "고객 발굴부터 고객 관계관리까지의 영업 사이클을 독립적으로 운영한 이력이 강점"
  ],
  "risks": [
    "영업 파이프라인 관리 역량이 필수 요건임에도 현재 보유 역량에 포함되지 않아 서류 단계에서 불리하게 작용할 수 있음",
    "CRM 도구 활용 경험 부재로 데이터 기반 영업 관리 체계 적응에 시간이 필요할 수 있음"
  ],
  "strategyActions": [
    "HubSpot, Salesforce 등 주요 CRM 도구의 무료 체험판을 활용해 파이프라인 관리 기능을 직접 실습하고 이를 이력서에 반영",
    "기존 영업 활동에서 단계별 고객 관리 프로세스를 적용한 경험이 있다면, 이를 파이프라인 관리 사례로 구체적으로 재구성하여 자기소개서에 기술"
  ],
  "learningTaskReasons": [
    {
      "skill": "영업 파이프라인 관리",
      "why": "B2B 영업에서 파이프라인 관리는 매출 예측과 고객 우선순위 결정의 핵심 도구로, 이 역량 없이는 팀 영업 체계에 통합되기 어렵기 때문에 우선 학습이 필요함"
    },
    {
      "skill": "CRM",
      "why": "F&B 업종 B2B 영업에서 CRM을 통한 고객 데이터 체계화는 재구매 유도와 이탈 방지에 직결되므로, 도구 활용 능력을 갖추면 실무 기여도를 빠르게 높일 수 있음"
    }
  ]
}
```
- 검수: [ ]1 입력외사실  [ ]2 부족역량 오인  [ ]3 HOLD 권장오류  [ ]4 IT표현 누출  [ ]5 문장 자연스러움

### 7. `mix_0334` · MARKETING · 마케팅 AE
- fitScore: **61** · applyDecision: **COMPLEMENT_BEFORE_APPLY** · 경력: 신입 · 회사: 블루테크
- matchedSkills: 구글애즈, SEO, 포토샵, 콘텐츠 기획, 이메일 마케팅
- missingRequiredSkills: GA4
- missingPreferredSkills: CRM, 데이터 분석
- assistant 출력:
```json
{
  "fitSummary": "구글애즈·SEO·포토샵·콘텐츠 기획 등 핵심 마케팅 역량을 두루 갖추고 있고, 우대 항목인 이메일 마케팅까지 보유하고 있어 블루테크 마케팅 AE 포지션에서 실무 기여가 가능한 수준입니다. 다만 필수 요건인 GA4 활용 경험이 없어 성과 분석 역량 측면에서 보완이 필요하고, CRM·데이터 분석 역량도 아직 갖추지 못한 상태입니다. GA4를 빠르게 학습해 실습 사례를 만든 뒤 지원하면 합격 가능성을 높일 수 있습니다.",
  "strengths": [
    "구글애즈·SEO·포토샵·콘텐츠 기획 등 신입에게 요구되는 주요 마케팅 실행 역량을 폭넓게 보유하고 있습니다.",
    "이메일 마케팅 역량을 추가로 갖추고 있어 콘텐츠 채널 운영 측면에서 즉시 기여할 수 있습니다."
  ],
  "risks": [
    "필수 요건인 GA4 경험이 없어 광고 성과 분석과 전환 추적 업무에서 즉시 투입이 어려울 수 있습니다.",
    "데이터 분석 역량이 없어 캠페인 성과를 수치 기반으로 보고하는 업무에서 한계가 드러날 수 있습니다."
  ],
  "strategyActions": [
    "구글 애널리틱스 공식 교육(Google Analytics Academy)에서 GA4 기초 과정을 이수하고, 데모 계정으로 리포트 구성 실습까지 진행한 뒤 이력서에 명시하세요.",
    "개인 블로그나 포트폴리오 사이트에 GA4를 직접 연동해 유입 데이터를 분석한 사례를 자기소개서에 구체적으로 서술하면 실전 경험으로 인정받을 수 있습니다."
  ],
  "learningTaskReasons": [
    {
      "skill": "GA4",
      "why": "블루테크 마케팅 AE의 필수 요건으로 지정된 도구이며, 광고 집행 후 성과 분석과 캠페인 최적화에 직접 활용됩니다. 이 역량 없이는 성과 보고 업무를 독립적으로 수행하기 어려워 서류 단계에서 불리할 수 있습니다."
    },
    {
      "skill": "데이터 분석",
      "why": "마케팅 AE 포지션에서 데이터 분석은 캠페인 효과를 수치로 증명하고 개선 방향을 도출하는 데 핵심적인 역량입니다. CRM과 연계한 데이터 기반 의사결정 능력을 기르면 중장기적으로 역량 폭이 넓어집니다."
    }
  ]
}
```
- 검수: [ ]1 입력외사실  [ ]2 부족역량 오인  [ ]3 HOLD 권장오류  [ ]4 IT표현 누출  [ ]5 문장 자연스러움

### 8. `mix_0305` · FINANCE_ACCOUNTING · 결산 담당
- fitScore: **79** · applyDecision: **COMPLEMENT_BEFORE_APPLY** · 경력: 주니어(1~3년) · 회사: 페어그룹
- matchedSkills: 부가세 신고, 전표 처리, 예산 관리, 엑셀
- missingRequiredSkills: (없음)
- missingPreferredSkills: ERP 운영, 원가 관리
- assistant 출력:
```json
{
  "fitSummary": "부가세 신고, 전표 처리, 예산 관리, 엑셀이라는 필수 역량을 빠짐없이 충족하고 있으며, 재경관리사와 전산세무 2급 자격증을 통해 세무 및 재무 실무 능력을 공식적으로 증명하고 있습니다. 주니어 경력직으로서 이 정도의 역량 완성도는 충분히 경쟁력이 있으며, ERP 운영과 원가 관리를 추가로 준비하면 지원 완성도가 더욱 높아집니다. 현 상태에서도 지원을 진지하게 검토할 만한 프로필입니다.",
  "strengths": [
    "부가세 신고, 전표 처리, 예산 관리 역량을 모두 보유하고 있어 회계/재무 기본 실무를 온전히 수행할 수 있습니다.",
    "재경관리사와 전산세무 2급 자격증은 세무·회계 실무 능력을 공식적으로 증명하며, 주니어 지원자 중 뚜렷한 차별화 요소가 됩니다.",
    "자금 관리 및 재무제표 작성 역량도 보유하고 있어 재무 보고 업무까지 담당할 수 있는 가능성을 보여줍니다."
  ],
  "risks": [
    "우대 역량인 ERP 운영 경험이 없으면 전표 처리나 예산 관리를 시스템에서 처리하는 방식에 적응하는 데 추가 시간이 필요할 수 있습니다.",
    "원가 관리 경험이 부족하면 IT/SaaS 기업의 프로젝트별 비용 배분이나 원가 분석 업무에서 즉각적인 기여가 어려울 수 있습니다."
  ],
  "strategyActions": [
    "ERP 운영 역량을 보완하기 위해 SAP, 더존, ERP 등 주요 회계 ERP의 기초 교육과정을 수강하거나 체험판을 활용해 전표 처리 실습을 해두세요.",
    "원가 관리에 대한 기본 개념과 IT 기업의 원가 구조를 학습하고, 관련 사례 분석 자료를 면접 준비에 활용하세요."
  ],
  "learningTaskReasons": [
    {
      "skill": "ERP 운영",
      "why": "회계 실무에서 전표 처리와 예산 관리는 대부분 ERP 시스템으로 진행되며, 이 공고에서도 우대 조건으로 명시된 만큼 조기 학습이 실무 적응 속도를 높입니다."
    },
    {
      "skill": "원가 관리",
      "why": "IT/SaaS 업종에서는 프로젝트 단위 원가 분석이 중요하며 우대 역량으로 명시되어 있어, 원가회계 개념 학습이 이 공고 및 유사 공고에서 경쟁력을 높여줍니다."
    }
  ]
}
```
- 검수: [ ]1 입력외사실  [ ]2 부족역량 오인  [ ]3 HOLD 권장오류  [ ]4 IT표현 누출  [ ]5 문장 자연스러움

### 9. `mix_0416` · MANUFACTURING_LOGISTICS · 물류 운영 담당
- fitScore: **79** · applyDecision: **COMPLEMENT_BEFORE_APPLY** · 경력: 주니어(1~3년) · 회사: HyperPay
- matchedSkills: 안전 관리, 입출고 관리, 생산 계획, SCM
- missingRequiredSkills: (없음)
- missingPreferredSkills: 품질 검사, 데이터 분석, ERP 운영
- assistant 출력:
```json
{
  "fitSummary": "안전 관리·입출고 관리·생산 계획·SCM 등 필수 역량 전항목을 충족하고 있어 물류 운영 담당 직무의 기본 요건을 빈틈없이 갖추고 있습니다. 품질경영기사 자격증과 6시그마·재고 관리 역량까지 보유해 현장 운영 전반에 안정적으로 기여할 수 있습니다. 우대 역량인 품질 검사·데이터 분석·ERP 운영을 보완하면 상위 지원자 그룹에서도 충분히 경쟁할 수 있는 수준이 됩니다.",
  "strengths": [
    "필수 역량(안전 관리, 입출고 관리, 생산 계획, SCM) 전항목 충족으로 물류 운영 핵심 업무 즉시 수행 가능",
    "품질경영기사 자격증으로 품질 기준 이해와 현장 적용 역량을 공식 입증",
    "6시그마 및 재고 관리 역량까지 보유해 재고 정확도 향상과 물류 낭비 제거 활동에 기여 가능"
  ],
  "risks": [
    "품질 검사 경험이 확인되지 않아 입고 검수·출하 검사 업무에서 추가 적응이 필요할 수 있음",
    "데이터 분석 역량 미보유로 물류 KPI 모니터링 및 운영 개선 보고서 작성 시 보완이 필요함",
    "ERP 운영 경험이 없어 전사 물류·재고 시스템 연동 업무에서 온보딩 기간이 길어질 수 있음"
  ],
  "strategyActions": [
    "품질경영기사 자격을 바탕으로 품질 검사 절차(수입검사·공정검사 기준 등)를 정리하고, 관련 실습 경험이 있다면 구체적인 수치와 함께 이력서에 기재한다",
    "ERP 물류 모듈(입출고·재고 관리) 기초 교육을 이수하고, SAP나 더존 ERP 기본 과정을 통해 시스템 운영 역량을 갖춘다"
  ],
  "learningTaskReasons": [
    {
      "skill": "ERP 운영",
      "why": "물류 운영 업무에서 입출고·재고 데이터를 ERP로 관리하는 것이 표준이므로, ERP 사용 경험이 없으면 업무 처리 속도와 정확성 모두에서 불이익이 생깁니다."
    },
    {
      "skill": "데이터 분석",
      "why": "SCM과 생산 계획을 담당하면 납기 준수율·재고 회전율 같은 지표를 주기적으로 분석해 보고해야 하므로, 데이터 분석 역량이 실무 기여도를 직접 좌우합니다."
    }
  ]
}
```
- 검수: [ ]1 입력외사실  [ ]2 부족역량 오인  [ ]3 HOLD 권장오류  [ ]4 IT표현 누출  [ ]5 문장 자연스러움

### 10. `mix_0399` · MARKETING · 마케팅 AE
- fitScore: **60** · applyDecision: **COMPLEMENT_BEFORE_APPLY** · 경력: 미들(4~7년) · 회사: 코어페이
- matchedSkills: 퍼포먼스 광고 운영, 데이터 분석, 구글애즈, CRM
- missingRequiredSkills: 이메일 마케팅, SEO
- missingPreferredSkills: 콘텐츠 기획
- assistant 출력:
```json
{
  "fitSummary": "퍼포먼스 광고 운영, 데이터 분석, 구글애즈 역량을 보유하고 있으며 검색광고마케터 1급과 GAIQ 자격증으로 디지털 마케팅 전문성을 뒷받침하고 있습니다. 필수 역량 중 이메일 마케팅과 SEO가 빠져 있어 추가 보완이 필요하며, 우대 항목인 콘텐츠 기획도 채워지면 종합 경쟁력이 더 높아집니다.",
  "strengths": [
    "퍼포먼스 광고 운영, 구글애즈, 데이터 분석 역량 보유로 광고 집행과 성과 분석 즉시 수행 가능",
    "검색광고마케터 1급·GAIQ 자격증으로 디지털 마케팅 전문성 공식 증명",
    "CRM 역량 보유로 고객 데이터 기반 캠페인 운영 역량 보유"
  ],
  "risks": [
    "이메일 마케팅 역량 부재로 CRM 연계 이메일 캠페인 수행 어려움",
    "SEO 역량 미보유로 콘텐츠 마케팅 채널 운영 시 검색 최적화 업무 대응 곤란"
  ],
  "strategyActions": [
    "이메일 마케팅 도구(Mailchimp, Stibee 등) 실습을 통해 발송 설정, A/B 테스트, 오픈율·클릭률 분석 경험을 쌓는다",
    "SEO 기초 원리(키워드 리서치, 메타 태그, 콘텐츠 최적화)를 학습하고 블로그나 개인 사이트에 직접 적용해 실습 사례를 만든다"
  ],
  "learningTaskReasons": [
    {
      "skill": "이메일 마케팅",
      "why": "공공기관 바이오·제약 분야 마케팅에서 이메일 캠페인은 전문가 대상 정보 전달의 주요 채널이며, 이 역량 없이는 CRM과 연계한 마케팅 자동화 실행이 어렵다"
    },
    {
      "skill": "SEO",
      "why": "콘텐츠 채널 운영 기반의 마케팅 AE 직무에서 SEO는 오가닉 트래픽 확보의 핵심 수단으로, 이 역량 부재는 채널 성장 전략 수립 시 취약점이 된다"
    }
  ]
}
```
- 검수: [ ]1 입력외사실  [ ]2 부족역량 오인  [ ]3 HOLD 권장오류  [ ]4 IT표현 누출  [ ]5 문장 자연스러움

### 11. `mix_0301` · MANUFACTURING_LOGISTICS · 물류 운영 담당
- fitScore: **23** · applyDecision: **HOLD** · 경력: 주니어(1~3년) · 회사: 스마트그룹
- matchedSkills: 생산 계획
- missingRequiredSkills: 6시그마, 입출고 관리, 안전 관리, 데이터 분석
- missingPreferredSkills: 품질 검사, 재고 관리, ERP 운영, SCM
- assistant 출력:
```json
{
  "fitSummary": "생산 계획 경험을 보유하고 있어 직무와 관련된 최소한의 접점은 있으나, 필수 역량 5개 중 4개(6시그마, 입출고 관리, 안전 관리, 데이터 분석)가 부족한 상황입니다. 우대 조건 항목도 대부분 준비되지 않아 공공기관 물류 운영 담당 직무에 즉시 지원하기에는 역량 공백이 큰 편입니다. 단계적인 역량 보완 계획을 세우고 준비를 충분히 갖춘 후 지원하는 것을 권장합니다.",
  "strengths": [
    "생산 계획 역량을 보유하고 있어 물류·생산관리 직무의 기본 업무 흐름을 이해하고 있음을 보여줄 수 있습니다.",
    "품질경영기사 자격증은 공공기관 물류 운영 직무에서 품질 관리 관련 업무 이해도를 증명하는 근거로 활용할 수 있습니다."
  ],
  "risks": [
    "6시그마, 입출고 관리, 안전 관리, 데이터 분석 등 필수 역량 대부분이 보유 이력에서 확인되지 않아 기본 직무 수행 능력에 의문이 제기될 수 있습니다.",
    "재고 관리, ERP 운영, SCM 등 우대 항목도 전무하여 경쟁자 대비 전반적인 역량 차이가 크게 벌어집니다.",
    "공정 관리 경험만으로는 입출고 및 물류 운영 실무를 대체하기 어렵고, 현업에서 요구하는 실질적인 업무 범위를 충족하지 못할 가능성이 높습니다."
  ],
  "strategyActions": [
    "6시그마 자격증(GB 이상)을 취득하거나 관련 교육과정을 이수하여 공정 개선 방법론에 대한 역량을 공식화하세요.",
    "데이터 분석 역량을 보완하기 위해 엑셀 고급 기능이나 생산·물류 데이터 분석 관련 실습을 진행하고 이력서에 반영하세요.",
    "ERP(생산·물류 모듈) 교육 과정을 수강하거나 체험판 실습을 통해 입출고 및 재고 관리 프로세스 이해도를 높이세요."
  ],
  "learningTaskReasons": [
    {
      "skill": "6시그마",
      "why": "이 공고의 필수 요건이며 물류·생산관리 직무에서 공정 개선 업무의 기반이 되는 방법론으로, 자격 취득 자체가 서류 경쟁력을 직접적으로 끌어올립니다."
    },
    {
      "skill": "입출고 관리",
      "why": "물류 운영 담당의 핵심 업무이자 필수 역량으로 명시되어 있어, 실습이나 아르바이트 등 어떤 형태로든 현장 경험을 쌓아 두는 것이 중요합니다."
    }
  ]
}
```
- 검수: [ ]1 입력외사실  [ ]2 부족역량 오인  [ ]3 HOLD 권장오류  [ ]4 IT표현 누출  [ ]5 문장 자연스러움

### 12. `mix_0360` · MANUFACTURING_LOGISTICS · 물류 운영 담당
- fitScore: **26** · applyDecision: **HOLD** · 경력: 주니어(1~3년) · 회사: 넥스트테크
- matchedSkills: 공정 관리
- missingRequiredSkills: 데이터 분석, 생산 계획, 6시그마
- missingPreferredSkills: SCM, 입출고 관리
- assistant 출력:
```json
{
  "fitSummary": "품질경영기사·물류관리사 자격증과 재고 관리·품질 검사 경험을 갖추고 있지만, 이번 공고가 요구하는 데이터 분석·생산 계획·6시그마 역량이 모두 부족하여 현 시점의 적합도는 낮습니다. 자격증이 주는 기반을 살려 핵심 부족 역량을 채운 뒤 재도전하는 방향이 훨씬 효과적입니다.",
  "strengths": [
    "품질경영기사·물류관리사 자격증을 보유하여 품질 관리 및 물류 운영 분야의 기초 지식을 공식적으로 인정받았습니다.",
    "재고 관리와 품질 검사 경험이 있어 생산·물류 현장의 기본 흐름을 이해하고 있습니다.",
    "공정 관리 역량이 있어 이번 공고의 필수 요건 중 하나인 공정 이해 측면에서 최소한의 기반은 갖추고 있습니다."
  ],
  "risks": [
    "데이터 분석 역량이 없어 생산·물류 지표를 수치로 해석하고 개선안을 도출하는 업무에서 어려움이 있을 수 있습니다.",
    "생산 계획 수립 경험이 없어 납기·생산량 조정 등 핵심 운영 업무를 처음부터 배워야 하는 상황입니다.",
    "6시그마 방법론이 없으면 공정 개선 프로젝트에서 방법론 기반의 접근을 기대하는 팀과 협업하는 데 제약이 생깁니다."
  ],
  "strategyActions": [
    "엑셀 또는 구글 시트 기반의 생산 데이터 분석 연습을 통해 기본 데이터 분석 능력을 키우고, 관련 실습 이력을 정리합니다.",
    "6시그마 그린벨트 자격 취득을 목표로 교육 과정에 등록하여 공정 개선 방법론의 기초를 체계적으로 학습합니다.",
    "생산 계획 수립 사례(MPS·MRP 개념)를 ERP 실습 교육이나 관련 교재로 학습하고 이해도를 높입니다."
  ],
  "learningTaskReasons": [
    {
      "skill": "데이터 분석",
      "why": "물류 운영 담당자는 생산량·재고 회전율·납기 준수율 등 수치를 다루는 빈도가 높아, 데이터 분석 능력 없이는 업무 개선에 기여하기 어렵습니다."
    },
    {
      "skill": "6시그마",
      "why": "공공기관 물류 운영 환경에서는 표준화된 개선 방법론을 요구하는 경우가 많아, 6시그마 기초를 갖추면 지원 경쟁력을 크게 높일 수 있습니다."
    }
  ]
}
```
- 검수: [ ]1 입력외사실  [ ]2 부족역량 오인  [ ]3 HOLD 권장오류  [ ]4 IT표현 누출  [ ]5 문장 자연스러움

### 13. `mix_0394` · HR_ADMIN · 채용 담당
- fitScore: **55** · applyDecision: **HOLD** · 경력: 주니어(1~3년) · 회사: 오픈모빌리티
- matchedSkills: ERP 운영, 인사 평가, 노무 관리, 교육 기획
- missingRequiredSkills: 문서 작성, 급여 정산
- missingPreferredSkills: 근태 관리, 사내 행사 운영, 채용 관리
- assistant 출력:
```json
{
  "fitSummary": "ERP 운영, 인사 평가, 노무 관리, 교육 기획 등 여러 인사 역량을 갖추고 있어 공고 요건의 상당 부분을 충족합니다. 다만 문서 작성과 급여 정산 등 필수 항목이 빠져 있고, 우대 항목인 근태 관리·사내 행사 운영·채용 관리도 갖추지 못해 전체 역량 밸런스를 맞추는 작업이 필요합니다.",
  "strengths": [
    "ERP 운영, 인사 평가, 노무 관리 역량 보유로 인사 행정 핵심 업무 수행 가능",
    "교육 기획 경험으로 사내 교육 운영 업무 즉시 투입 가능",
    "컴퓨터활용능력 자격증으로 사무 업무 기반 역량 보유"
  ],
  "risks": [
    "급여 정산 역량 미보유로 급여 관련 업무 즉시 수행 어려움",
    "문서 작성 항목이 필수 역량임에도 미충족 상태로 공문·보고서 작성 대응 우려",
    "채용 관리 우대 항목 미충족으로 채용 담당 직무 특성상 핵심 실무와 간극"
  ],
  "strategyActions": [
    "급여 정산 실무 흐름(세전·세후 계산, 4대보험 공제, 원천세 신고 등)을 학습하고 관련 경험을 보완한다",
    "채용 관리 역량 확보를 위해 채용 공고 작성·서류 스크리닝·면접 일정 조율 등 채용 프로세스 전반의 실습 경험을 쌓는다"
  ],
  "learningTaskReasons": [
    {
      "skill": "급여 정산",
      "why": "채용 담당이라도 인사팀 내 급여 정산 협업이 필요한 경우가 많으며, 이 역량 부재는 인사 전반 업무 커버 시 즉각적인 한계로 작용한다"
    },
    {
      "skill": "채용 관리",
      "why": "지원 직무가 채용 담당인 만큼 채용 관리 역량은 직무 정체성의 핵심 항목이며, 우대 조건으로 명시된 만큼 보유 여부가 서류 경쟁력에 직접 영향을 준다"
    }
  ]
}
```
- 검수: [ ]1 입력외사실  [ ]2 부족역량 오인  [ ]3 HOLD 권장오류  [ ]4 IT표현 누출  [ ]5 문장 자연스러움

### 14. `mix_0390` · MANUFACTURING_LOGISTICS · 물류 운영 담당
- fitScore: **31** · applyDecision: **HOLD** · 경력: 신입 · 회사: 블루소프트
- matchedSkills: SCM, 6시그마, 입출고 관리
- missingRequiredSkills: 안전 관리, 데이터 분석, 공정 관리, 생산 계획
- missingPreferredSkills: (없음)
- assistant 출력:
```json
{
  "fitSummary": "SCM, 입출고 관리, 6시그마 등 일부 물류 관련 역량은 보유하고 있으나, 이 공고의 필수 요건인 안전 관리, 데이터 분석, 공정 관리, 생산 계획이 모두 부족해 현재 프로필과 공고 요건 사이의 간격이 상당합니다. 희망 직무가 자재/구매 담당인 점도 물류 운영 공고와 방향성이 일부 다르게 느껴질 수 있습니다. 핵심 역량을 우선 확보한 뒤 지원하는 것이 합리적인 접근입니다.",
  "strengths": [
    "SCM 역량 보유로 공급망 운영의 기본 흐름 이해",
    "6시그마 및 입출고 관리 역량으로 품질·물류 운영 기초 실무 경험"
  ],
  "risks": [
    "안전 관리, 데이터 분석, 공정 관리, 생산 계획 등 필수 역량 4개 모두 부재로 업무 즉시 투입 어려움",
    "희망 직무(자재/구매)와 공고 직무(물류 운영)의 업무 영역 차이가 있어 지원 동기 설명이 필요"
  ],
  "strategyActions": [
    "공정 관리 및 생산 계획 관련 기초 교육 과정을 이수하고, 실습 경험을 이력서에 추가한다",
    "안전 관리 역량 확보를 위해 산업안전 관련 자격증 취득을 준비한다",
    "데이터 분석 도구 활용 능력을 키워 물류·생산 데이터 분석 사례를 포트폴리오로 만든다"
  ],
  "learningTaskReasons": [
    {
      "skill": "안전 관리",
      "why": "물류·생산관리 직무에서 안전 관리는 현장 업무의 기본 전제 조건이며, 해당 역량 부재는 공공기관 채용에서 특히 감점 요인이 될 수 있다"
    },
    {
      "skill": "공정 관리",
      "why": "물류 운영 담당의 핵심 업무인 공정 개선과 직결되며, 이 역량 없이는 생산·물류 흐름을 주도적으로 다루기 어렵다"
    }
  ]
}
```
- 검수: [ ]1 입력외사실  [ ]2 부족역량 오인  [ ]3 HOLD 권장오류  [ ]4 IT표현 누출  [ ]5 문장 자연스러움

### 15. `mix_0346` · DESIGN · 제품 디자이너
- fitScore: **34** · applyDecision: **HOLD** · 경력: 미들(4~7년) · 회사: CoreSoft
- matchedSkills: 영상편집
- missingRequiredSkills: 디자인 시스템, 프로토타이핑
- missingPreferredSkills: Figma, UX 리서치, 일러스트레이터, 타이포그래피
- assistant 출력:
```json
{
  "fitSummary": "영상편집 역량 하나만 필수 요건과 일치하고, 디자인 시스템·프로토타이핑 등 핵심 요건이 미충족된 상태여서 현재 시점의 지원 적합도는 낮습니다. 포토샵·브랜딩 경험과 컬러리스트기사·GTQ 자격은 디자인 분야의 기초 역량을 보여주지만, 공고가 요구하는 제품 디자이너 역할과는 방향이 일부 다릅니다. 필수 역량을 집중적으로 보완한 뒤 지원을 재고하는 것이 현실적입니다.",
  "strengths": [
    "영상편집 역량이 필수 요건과 일치하여 시각 자산 제작 업무의 일부는 즉시 담당 가능",
    "컬러리스트기사·GTQ 자격과 포토샵 실무 경험이 그래픽 디자인 기반 업무에서 강점으로 작용할 수 있음"
  ],
  "risks": [
    "디자인 시스템 설계·운영 경험이 없어 제품 디자이너의 핵심 역할인 컴포넌트 체계 구축 업무 수행이 어려운 상황",
    "프로토타이핑 역량 부재로 사용자 테스트 단계의 인터랙티브 시제품 제작 업무를 담당하기 어려움",
    "Figma·일러스트레이터·UX 리서치 등 우대 항목이 모두 미충족되어 툴 적응 기간이 상당히 필요할 수 있음"
  ],
  "strategyActions": [
    "Figma 무료 계정으로 디자인 시스템(컴포넌트 라이브러리·스타일 가이드) 실습 프로젝트를 진행하고 포트폴리오에 포함",
    "Figma 프로토타이핑 기능을 활용한 인터랙션 실습 결과물을 만들어 프로토타이핑 역량을 입증할 수 있는 사례를 준비"
  ],
  "learningTaskReasons": [
    {
      "skill": "디자인 시스템",
      "why": "공공기관 대기업 제품 디자이너 직무에서는 일관된 UI 가이드라인과 컴포넌트 체계 운영이 필수이므로, 이 역량 없이는 팀 디자인 협업 구조 자체에 참여하기 어려움"
    },
    {
      "skill": "프로토타이핑",
      "why": "사용자 리서치 결과를 빠르게 시각화하고 피드백을 반영하는 프로토타이핑 능력은 제품 디자인 사이클의 핵심으로, 조기에 습득할수록 직무 적응 기간을 단축할 수 있음"
    }
  ]
}
```
- 검수: [ ]1 입력외사실  [ ]2 부족역량 오인  [ ]3 HOLD 권장오류  [ ]4 IT표현 누출  [ ]5 문장 자연스러움
