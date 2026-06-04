# Feature Ownership Structure

CareerTuner assigns work by feature. Each assignee owns the user frontend, user backend, admin frontend, and admin backend for that feature.

Nothing in this document removes a feature from the original plan. Removing or merging a feature requires a team decision.

## 1. Assignment Root

The assignment root is `features/`.

```text
features/
 ├─ auth/
 │   ├─ frontend/
 │   ├─ backend/
 │   ├─ admin-frontend/
 │   └─ admin-backend/
 ├─ home/
 ├─ dashboard/
 ├─ profile/
 ├─ applications/
 ├─ interview/
 ├─ correction/
 ├─ analysis/
 ├─ community/
 ├─ billing/
 ├─ settings/
 ├─ service/
 ├─ support/
 ├─ company/
 └─ legal/
```

Every feature keeps the same four child folders:

```text
<feature>/
 ├─ frontend/        User-facing React work
 ├─ backend/         User-facing Spring Boot/MyBatis work
 ├─ admin-frontend/  Admin React work
 └─ admin-backend/   Admin Spring Boot/MyBatis work
```

## 2. Runtime Source Mapping

The `features/` tree is the ownership and planning map. Runtime code should live in the app projects below.

| Work area | Runtime path |
| --- | --- |
| User frontend | `frontend/src/features/<feature>/` |
| User backend | `backend/src/main/java/com/careertuner/<backend-domain>/` |
| User MyBatis XML | `backend/src/main/resources/mapper/<backend-domain>/` |
| Admin frontend | `admin-frontend/src/features/<feature>/` or, until the app is bootstrapped, `features/<feature>/admin-frontend/` |
| Admin backend | `backend/src/main/java/com/careertuner/admin/<backend-domain>/` |
| Admin MyBatis XML | `backend/src/main/resources/mapper/admin/<backend-domain>/` |

The current visible app still keeps early prototype pages in `frontend/src/app/pages`. As features mature, move page internals into `frontend/src/features/<feature>` and leave only route-level wiring in `frontend/src/app`.

## 3. Feature Map

| Feature folder | User menu scope | Backend domain package |
| --- | --- | --- |
| `auth` | login, register, social login, token session | `auth`, `user` |
| `home` | public home and onboarding entry | `home` |
| `dashboard` | dashboard summary and alerts | `dashboard` |
| `profile` | basic info, resume, cover letter, career/projects, skills, certificates/education | `profile` |
| `applications` | application case, posting upload, analysis result, fit comparison, strategy, learning/certification recommendation, records | `applicationcase`, `jobposting`, `jobanalysis`, `companyanalysis`, `fitanalysis` |
| `interview` | interview mode, questions, practice, voice, avatar, evaluation, report | `interview` |
| `correction` | answer, cover letter, resume, portfolio explanation correction | `correction` |
| `analysis` | application trends, weakness, job readiness, interview score trends, recommendation direction | `analysis` |
| `community` | hired reviews, interview reviews, job questions, strategy board | `community` |
| `billing` | plans, AI usage, credit charging, payment history | `payment`, `billing` |
| `settings` | account, privacy, AI data consent, notifications | `settings` |
| `service` | feature intro, service intro, public service navigation | `serviceinfo` |
| `support` | customer center, user guide, FAQ, notices, contact | `support` |
| `company` | service/company profile, team, careers, blog, press, social channels | `company` |
| `legal` | terms, privacy policy, AI data consent document, copyright policy | `legal` |

## 4. Backend Package Rule

User APIs:

```text
backend/src/main/java/com/careertuner/<domain>/
 ├─ controller/
 ├─ service/
 ├─ mapper/
 ├─ domain/
 └─ dto/
```

Admin APIs:

```text
backend/src/main/java/com/careertuner/admin/<domain>/
 ├─ controller/
 ├─ service/
 ├─ mapper/
 ├─ domain/
 └─ dto/
```

MyBatis XML files follow the same domain names:

```text
backend/src/main/resources/mapper/<domain>/
backend/src/main/resources/mapper/admin/<domain>/
```

## 5. Frontend Feature Rule

User frontend:

```text
frontend/src/features/<feature>/
 ├─ pages/
 ├─ components/
 ├─ api/
 ├─ hooks/
 └─ types/
```

Admin frontend:

```text
admin-frontend/src/features/<feature>/
 ├─ pages/
 ├─ components/
 ├─ api/
 ├─ hooks/
 └─ types/
```

The admin frontend app can be bootstrapped as a separate Vite app when admin screens move beyond skeleton status.

## 6. Minimum Handoff Checklist

Before a feature owner marks a feature ready for integration, that feature should have:

- User route/page state
- User API client
- User backend controller/service/mapper/dto/domain
- Admin route/page state
- Admin backend controller/service/mapper/dto/domain
- MyBatis XML and sample data if persistence is needed
- Basic happy-path and failure-state UI
- Role/permission behavior for admin endpoints
