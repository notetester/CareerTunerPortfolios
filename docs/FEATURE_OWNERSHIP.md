# Feature Ownership Structure

CareerTuner assigns work by feature. Each assignee owns the user frontend, user backend, admin frontend, and admin backend for that feature.

Runtime source paths are the source of truth. Do not create a separate top-level admin frontend app unless the team explicitly decides that
admin needs a separate deployment/security/release boundary.

## 1. Runtime Source Mapping

| Work area | Runtime path |
| --- | --- |
| User frontend | `frontend/src/features/<feature>/` |
| Admin frontend | `frontend/src/admin/features/<feature>/` |
| User backend | `backend/src/main/java/com/careertuner/<backend-domain>/` |
| User MyBatis XML | `backend/src/main/resources/mapper/<backend-domain>/` |
| Admin backend | `backend/src/main/java/com/careertuner/admin/<backend-domain>/` |
| Admin MyBatis XML | `backend/src/main/resources/mapper/admin/<backend-domain>/` |

The current visible app still keeps early prototype pages in `frontend/src/app/pages`. As features mature, move page internals into `frontend/src/features/<feature>` and leave only route-level wiring in `frontend/src/app`.
Admin route-level wiring should stay in `frontend/src/admin`, then be mounted under `/admin/**` from the main router.

## 2. Feature Map

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

## 3. Backend Package Rule

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

## 4. Frontend Feature Rule

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
frontend/src/admin/features/<feature>/
 ├─ pages/
 ├─ components/
 ├─ api/
 ├─ hooks/
 └─ types/
```

Admin-specific layout, route guards, navigation, and shared admin UI live under:

```text
frontend/src/admin/
 ├─ components/
 ├─ features/
 ├─ hooks/
 ├─ lib/
 ├─ pages/
 └─ routes.ts
```

Keep shared primitives such as buttons, dialogs, tables, and API client helpers in the existing common frontend paths
(`frontend/src/app/components/ui`, `frontend/src/app/lib`) unless they are admin-only.

## 5. Separate Admin App Decision Rule

A separate admin frontend may be reconsidered only when at least one of these becomes a real requirement:

- Admin must be deployed to a separate domain/network boundary.
- Admin authentication/session policy cannot safely share the user SPA shell.
- Admin release cadence must be independent from the user frontend.
- Admin bundle size or dependency set becomes large enough to harm the user-facing app.

Until then, a single Vite React app keeps routing, auth, API clients, design tokens, and build tooling simpler.

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
