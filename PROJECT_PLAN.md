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
- Build: Maven.
- Frontend: React + Vite on Node 24.
- Development ports:
  - Spring API: `8080`
  - React dev server: `5173`
  - Legacy Node server during migration: `3000`
- Suggested repository layout:

```text
backend/
  pom.xml
  src/main/java/...
  src/test/java/...
frontend/
  package.json
  src/...
  public/...
data/
  app.json
```

## Phase 1: Foundation

- Create `backend/` Spring Boot project with Maven.
- Create `frontend/` React + Vite project.
- Keep the existing Node app runnable during migration for comparison.
- Define shared API contracts for:
  - active test retrieval
  - admin test activation
  - answer bank activation
  - submission creation
  - results retrieval
  - submission clearing
- Add CORS configuration for local React-to-Spring development.

## Phase 2: Backend Port

- Port Markdown parsing from `src/markdownParser.js` into Java services.
- Port scoring from `src/scorer.js` into Java services.
- Replace `src/storage.js` with a Spring storage service backed by `data/app.json`.
- Add DTOs for tests, answers, submissions, scores, and results.
- Implement token validation equivalent to the current `.env` behavior.
- Add JUnit tests matching the existing parser/scorer test cases.

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
- Make `npm test` and `mvn test` part of the normal verification path.
- Update DevPod/devcontainer startup commands once the new projects exist.

## Devcontainer Notes

- The workspace needs both Java 21/Maven and Node 24.
- The Spring API should run on port `8080`.
- The React dev server should run on port `5173`.
- Port `3000` is reserved for the legacy Node server while migration is in
  progress.

## Open Decisions

- Keep JSON-file storage long term, or move to SQLite/H2/PostgreSQL?
- Package React into Spring Boot for production, or keep frontend/backend
  separately deployed?
- Keep token-in-path URLs, or move admin/student tokens to headers or sessions?
- Add manual grading persistence for essays and short responses?
