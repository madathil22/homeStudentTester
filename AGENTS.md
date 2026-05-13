# AGENTS.md

Guidance for future agent sessions working in this repository.

## Project Snapshot

`homeStudentTester` is a local family-use test-generation and test-taking app.
The current working product is a Spring Boot API plus a Vite/React frontend.

The active user-facing flow is:

1. A teacher opens the admin UI.
2. The teacher enters a subject.
3. The React frontend calls `POST /api/test/generate`.
4. The Spring backend calls OpenAI and asks for a complete HTML test document.
5. The backend stores that generated HTML in H2.
6. The UI lists generated tests with stable links such as `/test1`.
7. A student opens the link and the frontend embeds `/api/test/html/{testId}`.

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

- `frontend/src/App.jsx` - admin/student routing and the main visible UI.
- `frontend/src/api.js` - tiny fetch wrapper used by the React app.
- `frontend/src/styles.css` - app styling.
- `backend/src/main/java/com/homestudenttester/controller/TestApiController.java`
  - API routes for generated tests plus legacy markdown/submission endpoints.
- `backend/src/main/java/com/homestudenttester/service/OpenAiService.java` -
  OpenAI chat-completions request and HTML extraction.
- `backend/src/main/java/com/homestudenttester/service/AppStateService.java` -
  H2-backed app state, generated HTML storage, legacy submissions/scoring.
- `backend/src/main/java/com/homestudenttester/service/AuthService.java` -
  currently has auth checks disabled.

## Current API Surface

Generated-test flow used by the frontend:

- `POST /api/test/generate` - create a generated HTML test for a subject.
- `GET /api/tests` - list generated tests.
- `DELETE /api/tests/{testId}` - delete a generated test.
- `GET /api/test/html/{testId}` - serve stored generated HTML.
- `GET /api/health` - health check.

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

- Auth checks are disabled in `AuthService`, even though older planning notes
  mention token validation.
- `PROJECT_PLAN.md` still describes the earlier Node-to-Spring markdown app
  migration and should not be treated as the latest product spec.
- The README's active flow is newer than parts of the backend implementation.
- Generated tests are stored as raw HTML. Be careful with any changes that alter
  rendering, sanitization, or origin boundaries.
- The frontend iframe currently points directly at stored HTML and does not
  collect student answers.
- Parser/scorer parity tests for the legacy markdown path are not implemented.

## Working Rules

- Prefer the generated-HTML flow unless the user explicitly asks to revive or
  complete the markdown/submission/scoring workflow.
- Keep `.env` private and avoid printing secrets.
- Do not remove legacy markdown/scoring code casually; it may represent planned
  functionality.
- Keep changes small and aligned with the current UI before doing broad cleanup.
- If adding backend features, update `README.md` and this file when the handoff
  story changes.
