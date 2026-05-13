# homeStudentTester Spring React Migration Plan

## Goal

Convert the current Node-based local test-taking app into a Java Spring Boot API
with a React frontend while preserving the existing family-use workflow:

- Parent creates and activates one Markdown test at a time.
- Student opens a tokenized test URL and submits answers.
- Parent reviews results, objective scoring, and GPT-ready written-response text.
- Data remains local-first unless a later phase intentionally adds a database or
  hosted deployment target.

## Current State

- Runtime: Node 22+ HTTP server.
- Backend entry point: `src/server.js`.
- Parsing/scoring logic: `src/markdownParser.js`, `src/scorer.js`.
- Local persistence: `data/app.json` through `src/storage.js`.
- Frontend: static HTML, JavaScript, and CSS in `public/`.
- Tests: Node test runner in `test/parser-scorer.test.js`.
- Routes:
  - `GET /admin/:adminToken`
  - `GET /results/:adminToken`
  - `GET /take/:testToken`
  - API/static behavior implemented directly in `src/server.js`.

## Target Architecture

- Backend: Spring Boot 3.x on Java 21.
- Build: Gradle.
- Frontend: React + Vite on Node 24.
- Development ports:
  - Spring API: `8080`
  - React dev server: `5173`
  - Legacy Node server during migration: `3000`
- Suggested repository layout:

```text
backend/
  build.gradle
  settings.gradle
  src/main/java/...
  src/test/java/...
frontend/
  package.json
  src/...
  public/...
data/
  app.json
```

## Session Handoff: 2026-05-13

This is the current migration state after the first scaffold pass.

Completed:

- Read the existing README and app structure to confirm the current workflow.
- Confirmed the legacy app is a Node 22 HTTP server with static HTML/JS pages.
- Confirmed the existing API surface in `src/server.js`:
  - `GET /api/test`
  - `POST /api/test`
  - `POST /api/answers`
  - `GET /api/submissions`
  - `POST /api/submissions`
  - `DELETE /api/submissions`
  - `POST /api/score`
- Updated this plan to target Spring Boot + Gradle + React/Vite + H2.
- Added initial `backend/` Spring Boot Gradle scaffold:
  - `backend/settings.gradle`
  - `backend/build.gradle`
  - `backend/src/main/java/com/homestudenttester/HomeStudentTesterApplication.java`
  - `backend/src/main/java/com/homestudenttester/config/WebConfig.java`
  - `backend/src/main/java/com/homestudenttester/controller/HealthController.java`
  - `backend/src/main/resources/application.yml`
  - `backend/src/test/java/com/homestudenttester/HomeStudentTesterApplicationTests.java`
- Backend scaffold includes:
  - Spring Boot `3.3.5`
  - Java `21`
  - Spring Web
  - Spring Data JPA
  - Validation
  - H2 runtime dependency
  - `/api/health` endpoint
  - local CORS for Vite on `5173`
  - H2 file database at `./data/homestudenttester`
  - H2 console at `/h2-console`
- Added initial `frontend/` React + Vite scaffold:
  - `frontend/package.json`
  - `frontend/index.html`
  - `frontend/vite.config.js`
  - `frontend/src/main.jsx`
  - `frontend/src/App.jsx`
  - `frontend/src/api.js`
  - `frontend/src/styles.css`
- Frontend scaffold includes:
  - Vite dev server on `5173`
  - `/api` proxy to `http://localhost:8080`
  - placeholder parent routes for `/admin/:adminToken` and `/results/:adminToken`
  - placeholder student route for `/take/:testToken`
  - API health check button from the parent shell
- Updated `.devcontainer/devcontainer.json`:
  - Java feature now installs Gradle instead of Maven
  - `postCreateCommand` now checks `gradle -version`
  - existing ports `3000`, `5173`, and `8080` remain forwarded
- Verified the existing legacy Node tests still pass:
  - `npm test`
  - 3 tests passing

Known environment notes:

- The current pre-rebuild container did not have `gradle` installed, so
  `gradle -version` failed with `command not found`.
- After rebuilding the devcontainer, verify Gradle before continuing:

```sh
gradle -version
```

- A proper Gradle wrapper has not been generated yet. Recommended next step is
  to generate or add `backend/gradlew` and wrapper files once Gradle is
  available in the rebuilt container.
- Git reported many pre-existing modified files outside this scaffold work.
  Do not revert them unless the user explicitly asks.

Recommended first commands after devcontainer rebuild:

```sh
java -version
gradle -version
node -v
npm -v
npm test
cd backend && gradle test
```

## Phase 1: Foundation

Status: partially complete. The backend and frontend shells exist, but the
Gradle wrapper and full dependency verification still need to be completed after
the devcontainer rebuild.

- Create `backend/` Spring Boot project with Gradle. Done.
- Create `frontend/` React + Vite project. Done.
- Keep the existing Node app runnable during migration for comparison. Done.
- Define shared API contracts for:
  - active test retrieval
  - admin test activation
  - answer bank activation
  - submission creation
  - results retrieval
  - submission clearing
- Add CORS configuration for local React-to-Spring development. Done.
- Configure H2 as the first database target for the Spring backend. Done.
- Generate/add Gradle wrapper files. Pending.
- Verify `cd backend && gradle test`. Pending.
- Install frontend dependencies and verify `npm run build` from `frontend/`.
  Pending.

## Phase 2: Backend Port

Recommended next implementation slice:

- Create JPA entities/repositories for active tests, answer banks, and
  submissions. Done.
- Add DTOs matching the legacy JSON API responses. Done.
- Implement token validation using `app.admin-token` and `app.test-token`.
  Done.
- Implement compatible legacy API endpoints. Core implementation done:
  - `GET /api/test`
  - `POST /api/test`
  - `POST /api/answers`
  - `GET /api/submissions`
  - `POST /api/submissions`
  - `DELETE /api/submissions`
  - `POST /api/score`
- Then port `parseQuestionBank`, `parseAnswerBank`, and `scoreSubmission` with
  JUnit tests copied from `test/parser-scorer.test.js`.

- Port Markdown parsing from `src/markdownParser.js` into Java services. Core
  implementation done; JUnit parity tests pending.
- Port scoring from `src/scorer.js` into Java services. Core implementation
  done; JUnit parity tests pending.
- Replace `src/storage.js` with Spring Data JPA repositories backed by H2. Done.
- Store parsed Markdown and score details as JSON text initially so the first
  migration focuses on behavior parity before deeper relational modeling. Done.
- Add DTOs for tests, answers, submissions, scores, and results. Done.
- Implement token validation equivalent to the current `.env` behavior. Done.
- Add JUnit tests matching the existing parser/scorer test cases. Pending.

## Phase 3: React Frontend

- Convert `public/admin.html` and `public/admin.js` into React admin views.
- Convert `public/take.html` and `public/take.js` into React student views.
- Convert `public/styles.css` into either app CSS or component-scoped styles.
- Preserve existing user-facing routes:
  - `/admin/:adminToken`
  - `/results/:adminToken`
  - `/take/:testToken`
- Use Vite proxying so the React dev server can call the Spring API.

## Phase 4: Cutover

- Compare behavior between legacy Node and Spring/React flows.
- Update README with Spring/React setup and local-network usage.
- Decide whether to remove or archive the legacy Node implementation.
- Make `npm test`, frontend checks, and `./gradlew test` part of the normal
  verification path.
- Update DevPod/devcontainer startup commands once the new projects exist.

## Devcontainer Notes

- The workspace needs both Java 21/Gradle and Node 24.
- The Spring API should run on port `8080`.
- The React dev server should run on port `5173`.
- Port `3000` is reserved for the legacy Node server while migration is in
  progress.

## Open Decisions

- Keep H2 long term for local family use, or later move to SQLite/PostgreSQL?
- Package React into Spring Boot for production, or keep frontend/backend
  separately deployed?
- Keep token-in-path URLs, or move admin/student tokens to headers or sessions?
- Add manual grading persistence for essays and short responses?
