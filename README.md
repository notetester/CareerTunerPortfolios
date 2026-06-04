# CareerTuner

채용공고에 맞춰 내 스펙과 면접 답변을 조정하는 **AI 취업 전략·가상 면접 준비 플랫폼**.

공고를 업로드하면 AI가 요구조건·기업 현황을 분석하고, 내 프로필과 비교해 **지원 전략·부족 역량·학습 방향·예상 질문**을 만들어 주며, **AI 모의면접 → 답변 평가·첨삭 → 장기 취업 경향 분석**까지 하나의 "지원 건" 안에서 관리한다.

## 모노레포 구성

```text
CareerTuner/
 ├─ backend/    Spring Boot 4 + MyBatis + MySQL  (REST API · :8080)
 ├─ frontend/   React 18 + Vite + TypeScript      (반응형 웹/PWA · :5173)
 └─ docs/       기획 및 아키텍처 문서
```

IntelliJ Ultimate에서 이 루트 폴더를 열면 backend(Spring Boot)와 frontend(npm)를 한 IDE에서 관리할 수 있다.

## 빠른 시작

**백엔드** (JDK 21 필요)
```bash
cd backend
./gradlew bootRun        # Windows: .\gradlew.bat bootRun
# http://localhost:8080/api/health
```

**프런트엔드** (Node 20+ 권장)
```bash
cd frontend
npm install
npm run dev
# http://localhost:5173  (/api 요청은 8080으로 프록시)
```

**데이터베이스** — MySQL 8에 스키마 적용
```bash
mysql -u root -p < backend/src/main/resources/db/schema.sql
```

## 기술 스택

Spring Boot 4.0.6 · Java 21 · MyBatis · MySQL 8 · Spring Security · springdoc-openapi
／ React 18 · Vite 6 · TypeScript · Tailwind CSS v4 · shadcn/ui · react-router 7

> 영속성은 **MyBatis만** 사용한다(JPA 미사용).

## 문서

- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — 전체 아키텍처/데이터 모델/로드맵
- [docs/planning/기획.txt](docs/planning/기획.txt) — **기획 원본(최우선)**
- [backend/README.md](backend/README.md) · [frontend/README.md](frontend/README.md)

## 브랜치 전략

작업 브랜치: **`LEE-JEONG-GUCK`**
