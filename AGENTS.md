# AGENTS.md

Guidance for future agent sessions working in this repository.

## Project Snapshot

`homeStudentTester` is a local family-use test-generation and test-taking app.
The current working product is a Spring Boot API plus a Vite/React frontend.

The active user-facing flow is:

1. A teacher opens the command center.
2. The teacher unlocks `/commandcenter` with `ADMIN_PASSWORD`.
3. The teacher fills a required form with state, grade level, subject, either
   question count or time-bound length, and a free-text topic such as
   `3 digit addition`.
4. The React frontend calls `POST /api/test/generate` with `x-admin-token`.
5. The Spring backend calls OpenAI and asks for strict JSON matching the
   generated question-bank DTO shape.
6. The backend validates key constraints such as exact requested question count.
7. The backend renders that JSON into a fixed HTML test template with student
   name, elapsed-time ticker, submit button, and MathJax support for TeX math.
8. The backend stores the generated HTML plus question-bank metadata in H2.
9. The UI lists generated tests with stable links such as `/test1`.
10. A student opens the link and the frontend embeds `/api/test/html/{testId}`.
11. The generated HTML captures student answers and elapsed time, then posts to
    `POST /api/test/{testId}/submit`.
12. The backend combines stored question-bank JSON, submitted answers, and
    timing into a scoring payload for OpenAI.
13. The command center shows the latest result for the selected test in the
    right-side results panel.

## Repository Layout

- `backend/` - Spring Boot 3.3.5 API, Java 21, Gradle, H2 persistence.
- `frontend/` - React 18 + Vite frontend.
- `.vscode/tasks.json` - convenience tasks for backend/frontend startup.
- `.devcontainer/devcontainer.json` - Java 21, Gradle, and Node 24 dev setup.

Important current files:

- `frontend/src/App.jsx` - `/commandcenter` teacher UI, password unlock flow,
  structured generation form, split generated-test grid/results panel, and
  student iframe routing.
- `frontend/src/api.js` - tiny fetch wrapper used by the React app.
- `frontend/src/styles.css` - app styling.
- `backend/src/main/java/com/homestudenttester/controller/TestApiController.java`
  - API routes for generated tests.
- `backend/src/main/java/com/homestudenttester/service/OpenAiService.java` -
  OpenAI JSON question-bank generation, backend HTML rendering, generated-test
  answer scoring, MathJax-enabled test rendering, and scoring-response
  normalization.
- `backend/src/main/java/com/homestudenttester/service/AppStateService.java` -
  H2-backed generated HTML/question-bank/result metadata storage.
- `backend/src/main/java/com/homestudenttester/service/AuthService.java` -
  `ADMIN_PASSWORD` enforcement for command-center/admin APIs.
- `backend/src/main/java/com/homestudenttester/controller/ApiExceptionHandler.java`
  - user-facing API error mapping plus backend exception logging.

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

Optional overrides (Responses API recommended):

```env
OPENAI_API_URL=https://api.openai.com/v1/responses
OPENAI_MODEL=gpt-5.4-mini
OPENAI_FALLBACK_MODEL=gpt-5.5
OPENAI_MAX_OUTPUT_TOKENS=8000
OPENAI_REASONING_EFFORT=low
OPENAI_STORE=false
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

## Current Product Notes

- The teacher UI now collects structured generation inputs and composes the
  backend `subject` request string from them. The backend contract is still a
  single `subject` field for now.
- The generated-tests grid displays parsed grade level, subject, type, topic,
  score, and actions. Older free-form rows fall back gracefully when those
  fields cannot be inferred.
- Generated test HTML loads MathJax, and generation prompts ask for TeX-wrapped
  math expressions using `\( ... \)` and `\[ ... \]`.
- Backend logs now cover generation/scoring request receipt, OpenAI call timing,
  parse/validation progress, persistence, and exception paths.

## Known Gaps

- Generated tests are stored as raw HTML plus JSON metadata. Be careful with any
  changes that alter rendering, sanitization, origin boundaries, answer capture,
  or the shape of stored question-bank/result metadata.
- Existing stored tests preserve the HTML they were generated with; template
  improvements such as MathJax apply to newly generated tests unless older tests
  are regenerated.
- Time-bound generation is captured in the UI request text but is not yet
  enforced or validated as a first-class backend field the way exact question
  count is.
- The command center stores the entered admin password in browser
  `sessionStorage` and sends it as `x-admin-token`; there is not yet a real
  session/token system.

## Working Rules

- Prefer the generated JSON question-bank flow; it is now the only supported
  product path.
- Keep `.env` private and avoid printing secrets.
- Keep changes small and aligned with the current UI before doing broad cleanup.
- If adding backend features, update `README.md` and this file when the handoff
  story changes.
