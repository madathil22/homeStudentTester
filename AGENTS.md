# AGENTS.md

Guidance for future agent sessions working in this repository.

## Project Snapshot

`homeStudentTester` is a local family-use test-generation and test-taking app.
The current working product is a Spring Boot API plus a Vite/React frontend.

The active user-facing flow is:

1. A teacher opens the command center.
2. The teacher unlocks `/commandcenter` with `ADMIN_PASSWORD`.
3. The teacher enters a full test request, such as `3 questions for 3rd grade math`.
4. The React frontend calls `POST /api/test/generate` with `x-admin-token`.
5. The Spring backend calls OpenAI and asks for strict JSON matching the
   generated question-bank DTO shape.
6. The backend validates key constraints such as exact requested question count.
7. The backend renders that JSON into a fixed HTML test template with student
   name, elapsed-time ticker, and submit button.
8. The backend stores the generated HTML plus question-bank metadata in H2.
9. The UI lists generated tests with stable links such as `/test1`.
10. A student opens the link and the frontend embeds `/api/test/html/{testId}`.
11. The generated HTML captures student answers and elapsed time, then posts to
    `POST /api/test/{testId}/submit`.
12. The backend combines stored question-bank JSON, submitted answers, and
    timing into a scoring payload for OpenAI.
13. The command center shows the latest result for the selected test in the
    right-side results panel.

There is also older markdown question-bank, answer-bank, submission, and scoring
code still present in the backend. Treat it as legacy/migration scaffolding until
the product direction is clarified.

## Repository Layout

- `backend/` - Spring Boot 3.3.5 API, Java 21, Gradle, H2 persistence.
- `frontend/` - React 18 + Vite frontend.
- `.vscode/tasks.json` - convenience tasks for backend/frontend startup.
- `.devcontainer/devcontainer.json` - Java 21, Gradle, and Node 24 dev setup.
- `PROJECT_PLAN.md` - historical migration plan; useful context, but partly
  stale compared with the generated-HTML flow in the current README and UI.

Important current files:

- `frontend/src/App.jsx` - `/commandcenter` teacher UI, password unlock flow,
  generated-test grid/results panel, and student iframe routing.
- `frontend/src/api.js` - tiny fetch wrapper used by the React app.
- `frontend/src/styles.css` - app styling.
- `backend/src/main/java/com/homestudenttester/controller/TestApiController.java`
  - API routes for generated tests plus legacy markdown/submission endpoints.
- `backend/src/main/java/com/homestudenttester/service/OpenAiService.java` -
  OpenAI JSON question-bank generation, backend HTML rendering, generated-test
  answer scoring, and scoring-response normalization.
- `backend/src/main/java/com/homestudenttester/service/AppStateService.java` -
  H2-backed app state, generated HTML/question-bank/result metadata storage,
  legacy submissions/scoring.
- `backend/src/main/java/com/homestudenttester/service/AuthService.java` -
  `ADMIN_PASSWORD` enforcement for command-center/admin APIs.

## Current API Surface

Generated-test flow used by the frontend:

- `POST /api/test/generate` - create a generated HTML test for a subject.
- `GET /api/tests` - list generated tests.
- `DELETE /api/tests/{testId}` - delete a generated test.
- `GET /api/test/html/{testId}` - serve stored generated HTML.
- `POST /api/test/{testId}/submit` - submit generated-test answers for OpenAI scoring.
- `GET /api/health` - health check.

Admin routes require the `x-admin-token` header containing `ADMIN_PASSWORD`.
Student HTML and student generated-test submissions are not admin-password
protected.

Legacy markdown/submission flow still present:

- `GET /api/test`
- `POST /api/test`
- `POST /api/answers`
- `GET /api/submissions`
- `POST /api/submissions`
- `DELETE /api/submissions`
- `POST /api/score`

## Run Commands

Backend:

```sh
cd backend
gradle bootRun
```

Frontend:

```sh
cd frontend
npm install
npm run dev
```

VS Code task:

- `App: Start Backend + Frontend`

The backend reads OpenAI settings from environment variables. The VS Code backend
task sources the workspace-root `.env` before running Spring Boot.

## Environment

Required for generated tests:

```env
OPENAI_API_KEY=your_openai_api_key_here
ADMIN_PASSWORD=your_admin_password_here
```

Optional overrides:

```env
OPENAI_API_URL=https://api.openai.com/v1/chat/completions
OPENAI_MODEL=gpt-4.1-mini
OPENAI_MAX_TOKENS=1500
OPENAI_TEMPERATURE=0.3
```

Do not commit `.env`; it is ignored by `.gitignore`.

## Verification

Useful checks:

```sh
cd frontend
npm run build
```

```sh
cd backend
gradle test
```

There is no Gradle wrapper checked in yet, so backend commands currently depend
on system `gradle`.

## Known Gaps

- `PROJECT_PLAN.md` still describes the earlier Node-to-Spring markdown app
  migration and should not be treated as the latest product spec.
- Generated tests are stored as raw HTML plus JSON metadata. Be careful with any
  changes that alter rendering, sanitization, origin boundaries, answer capture,
  or the shape of stored question-bank/result metadata.
- The command center stores the entered admin password in browser
  `sessionStorage` and sends it as `x-admin-token`; there is not yet a real
  session/token system.
- Parser/scorer parity tests for the legacy markdown path are not implemented.

## Working Rules

- Prefer the generated JSON question-bank flow unless the user explicitly asks
  to revive or complete the markdown/submission/scoring workflow.
- Keep `.env` private and avoid printing secrets.
- Do not remove legacy markdown/scoring code casually; it may represent planned
  functionality.
- Keep changes small and aligned with the current UI before doing broad cleanup.
- If adding backend features, update `README.md` and this file when the handoff
  story changes.
