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
9. The UI lists generated tests with stable links such as `/test-amber-fox`.
10. A student opens the link and the frontend embeds `/api/test/html/{testId}`.
11. The generated HTML captures student answers and elapsed time, then posts to
    `POST /api/test/{testId}/submit`.
12. The backend scores `multiple_choice` and `multi_select` answers
    deterministically against stored `correctOptionLabels`, and sends only
    `free_text` questions to OpenAI for scoring.
13. The command center shows the latest result for the selected test in the
    right-side results panel.
14. The teacher can submit an answer-key correction from a wrong-answer card;
    the backend verifies the correction with OpenAI, stores approved correction
    memory, rescores the current submission, and feeds relevant memories into
    future test generation prompts.

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
  thin facade for OpenAI-backed generation, scoring, and answer-key correction
  flows.
- `backend/src/main/java/com/homestudenttester/service/OpenAiGeneratorService.java`
  - OpenAI JSON question-bank generation plus MathJax-enabled generated-test
  HTML rendering.
- `backend/src/main/java/com/homestudenttester/service/OpenAiScorerService.java`
  - deterministic objective scoring plus OpenAI free-text scoring and scoring
  response normalization.
- `backend/src/main/java/com/homestudenttester/service/OpenAiFeedbackService.java`
  - OpenAI answer-key correction verification.
- `backend/src/main/java/com/homestudenttester/service/AppStateService.java` -
  H2-backed generated HTML/question-bank/result metadata storage plus
  answer-key correction memory helpers.
- `backend/src/main/java/com/homestudenttester/utils/ServiceUtils.java` -
  shared parsing, label normalization, fingerprint, and escaping helpers.
- `backend/src/main/java/com/homestudenttester/model/AnswerKeyCorrection.java`
  - H2/JPA entity for parent-approved answer-key correction memory.
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
- `POST /api/test/{testId}/submit` - submit generated-test answers for backend
  scoring; OpenAI is used only for `free_text` questions.
- `POST /api/test/{testId}/questions/{questionNumber}/correction` - admin-only
  answer-key correction flow; OpenAI verifies the parent correction, approved
  corrections are saved, and the latest submission is rescored.
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

Backend tests intentionally cover regression-prone contracts in the generated
question-bank flow:

- deterministic objective scoring for `multiple_choice` and `multi_select`
- number-line visual rendering from structured JSON, including escaping unsafe
  model-provided labels and ignoring unsupported visual types
- question fingerprint behavior for correction memory, including visual data and
  option-order stability

There is no Gradle wrapper checked in yet, so backend commands currently depend
on system `gradle`.

## Current Product Notes

- The teacher UI now collects structured generation inputs and composes the
  backend `subject` request string from them. The backend contract is still a
  single `subject` field for now.
- The generated-tests grid displays parsed grade level, subject, type, topic,
  score, and actions. Older free-form rows fall back gracefully when those
  fields cannot be inferred.
- New generated-test links use configurable color-animal slugs from
  `app.test-link-colors` and `app.test-link-animals` in `application.yml`.
- Generated test HTML loads MathJax, and generation prompts ask for TeX-wrapped
  math expressions using `\( ... \)` and `\[ ... \]`.
- Generated questions may include an optional `visual` object. The first
  supported renderer is `number_line`, where OpenAI supplies structured numeric
  data and the backend renders safe SVG.
- Generated questions now include answer metadata; backend validation rejects
  invalid objective-question answer shapes before publishing.
- Generated-test scoring is deterministic for `multiple_choice` and
  `multi_select`; checkbox order does not matter, but the selected label set
  must exactly match `correctOptionLabels`. OpenAI scoring is reserved for
  `free_text` answers.
- Parent-approved answer-key corrections are stored separately from generated
  tests. Matching approved corrections override answer metadata during scoring
  and are summarized into future generation prompts as correction memory.
- Backend logs now cover generation/scoring request receipt, OpenAI call timing,
  parse/validation progress, persistence, token usage, and exception paths.
- The command center shows per-test token usage for generation and, when
  `free_text` questions are present, scoring separately.

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
- For any major product, architecture, testing, or debugging change, also update
  the active `WAI_Track-*.jsonl` file in the workspace root. Treat the WAI Track
  as chronological project memory: record what changed, why it changed, key
  decisions, insights, touched files, and any unresolved follow-up work so each
  future session can understand how the project moved, not only its current
  state.
