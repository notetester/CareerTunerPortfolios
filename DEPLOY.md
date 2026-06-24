# 배포 가이드 (AWS EC2 + Docker)

> ⚠️ 인프라/공통 영역 문서 — dev 반영 전 팀 합의 필요.
> 백엔드(Spring) + Qdrant(RAG)는 Docker, MySQL 은 기존 팀 원격 인스턴스 재사용, 프런트는 정적 배포.

## 0. 준비물
- AWS 계정, EC2 인스턴스(Ubuntu 22.04, t3.small 이상 권장 — Realtime/AI 부하)
- 도메인(선택) + HTTPS
- `OPENAI_API_KEY` (Realtime 권한 포함), `JWT_SECRET`

## 1. EC2 기본 세팅
```bash
sudo apt update && sudo apt install -y docker.io docker-compose-plugin git
sudo usermod -aG docker $USER   # 재로그인
```
보안그룹 인바운드: 22(SSH, 내 IP만), 80/443(웹), (선택) 8080.

## 2. 코드 받고 환경변수
```bash
git clone <repo> && cd finalProject
cat > .env <<'EOF'
OPENAI_API_KEY=sk-...
INTERVIEW_REALTIME_MODEL=gpt-4o-realtime-preview   # 실제 Realtime 모델 id 로 교체
JWT_SECRET=<32바이트 이상 랜덤>
DB_HOST=<팀 MySQL 호스트>
DB_NAME=<DB 이름>
DB_USERNAME=<DB 사용자>
DB_PASSWORD=<DB 비밀번호>
JOB_POSTING_AI_WORKER_ENABLED=true
JOB_POSTING_AI_WORKER_BASE_URL=http://job-posting-worker:8091
JOB_POSTING_AI_WORKER_TIMEOUT=30s
JOB_POSTING_WORKER_INSTALL_OCR=true
EOF
```
> `.env` 는 절대 커밋하지 않는다(.gitignore 처리됨). 실제 접속값은 팀 채널/시크릿으로 공유.

## 3. DB 마이그레이션 (최초 1회)
`backend/src/main/resources/db/schema.sql` 을 팀 MySQL 에 적용.
이번에 추가된 것: `file_asset` 테이블, `interview_question.parent_question_id`.
```bash
mysql -h $DB_HOST -u $DB_USERNAME -p team1_db < backend/src/main/resources/db/schema.sql
```

## 4. 빌드 & 기동
```bash
docker compose --env-file .env up -d --build
docker compose logs -f backend     # 부팅 확인
curl localhost:8080/api/health     # 헬스체크
```
- `backend`(:8080) + `qdrant`(:6333) 컨테이너가 뜸.
- 영상 업로드 한도는 compose 의 `SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE` 로 조정(기본 50MB).

## 5. Nginx 리버스프록시 + HTTPS (선택, 권장)
```nginx
server {
  server_name api.example.com;
  location /api/ { proxy_pass http://127.0.0.1:8080; proxy_set_header Host $host; }
  # Realtime 은 프런트→OpenAI 직결이라 WS 프록시 불필요. /api 만 프록시.
}
```
```bash
sudo apt install -y certbot python3-certbot-nginx && sudo certbot --nginx -d api.example.com
```

## 6. 프런트엔드
```bash
cd frontend
echo "VITE_API_BASE_URL=https://api.example.com/api" > .env.production
npm ci && npm run build       # dist/ 정적 산출물
```
배포: S3+CloudFront / Vercel / Netlify / 기존 GH Pages 데모 파이프라인 중 택1.
- 데모 안정성: 백엔드/키 이슈 대비 **목 모드(`VITE_USE_MOCK=true`) 폴백** 빌드도 하나 준비.

## 7. 시연 체크리스트
- [ ] 헬스체크 200
- [ ] 로그인/시드 계정 동작
- [ ] 면접 세션 생성 → 질문 생성 → 답변 평가 → 리포트
- [ ] **실시간 음성 면접관**(OPENAI_API_KEY Realtime 권한 + 모델 id 확인)
- [ ] 음성/영상 업로드·재생
- [ ] 결제 화면(목업)

## 메모
- 비밀키는 절대 커밋 금지(.env 는 gitignore). EC2 에서는 `.env` 또는 SSM 파라미터스토어 사용.
- AI 비용: Realtime/Vision/멀티에이전트는 세션당 비용↑ → `ai_usage_log`/credit 으로 제한.
