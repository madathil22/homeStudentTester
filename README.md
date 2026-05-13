# homeStudentTester

A family-use test-generation and test-taking app powered by Spring Boot and Vite.
The current flow generates a unique HTML test for each subject via OpenAI, saves
it in the backend, and exposes a unique student route per generated test.

## Current State

- Backend is Spring Boot (Java 21) and stores generated test HTML in H2.
- Frontend is Vite + React and runs on `localhost:5173` / devpod forwarded port.
- Unique links are generated per subject, for example:
  - `/test1`
  - `/test2`
- The admin UI can create tests, list generated subjects, and delete them inline.
- Student routes render the generated HTML page for the requested test ID.

## Run Locally

### Backend

From `/workspaces/homestudenttester/backend`:

```sh
gradle bootRun
```

The backend reads `.env` from the workspace root when launched from the VS Code
task configured to source `.env`.

### Frontend

From `/workspaces/homestudenttester/frontend`:

```sh
npm install
npm run dev
```

Open the forwarded dev URL shown by Vite, typically `http://localhost:5174`.

## Environment Variables

The backend supports these overrides in `.env`:

```env
OPENAI_API_KEY=your_openai_api_key_here
OPENAI_API_URL=https://api.openai.com/v1/chat/completions
OPENAI_MODEL=gpt-4.1-mini
OPENAI_MAX_TOKENS=1500
OPENAI_TEMPERATURE=0.3
```

> **Important:** `OPENAI_API_KEY` is required for test generation. Without a
> valid key, the backend cannot call OpenAI and the test generation endpoint
> will fail.

### Optional Overrides

- `OPENAI_API_URL` — custom OpenAI API endpoint
- `OPENAI_MODEL` — model to use for generation
- `OPENAI_MAX_TOKENS` — maximum tokens per request
- `OPENAI_TEMPERATURE` — response randomness

## .env and Git

`.env` should be local-only and not tracked in Git. If it is already tracked,
remove it from tracking with:

```sh
git rm --cached .env
```

Then commit the change and keep `.env` listed in `.gitignore`.

## API Endpoints

The backend currently exposes:

- `POST /api/test/generate` — create a generated test for a subject
- `GET /api/tests` — list generated tests
- `DELETE /api/tests/{testId}` — delete a generated test
- `GET /api/test/html/{testId}` — serve the generated HTML for a test
- `GET /api/health` — health check

## How It Works

1. Admin enters a test subject in the frontend.
2. Frontend calls `/api/test/generate`.
3. Backend calls OpenAI with a strong system prompt.
4. Backend stores the returned HTML and returns a unique link.
5. Frontend displays the unique test link in the generated tests table.
6. Student opens `/test1`, `/test2`, etc. and sees the rendered test.

## Markdown Format

Question banks must start with a `#` title and include at least one
`## Question N` section.

Supported question types:

- `multiple_choice`
- `multi_select`
- `short_response`
- `essay`
- `text`

Each question supports:

```md
## Question 1
Type: multiple_choice
Points: 1

Question prompt goes here.

A. First option
B. Second option
```

Passages can be added with:

```md
## Passage: Passage Title
Passage text goes here.
```

Answer banks can include `Answer:`, `Accept:`, `Points:`, `Explanation:`,
`Rubric:`, and `Sample Answer:` fields under matching `## Question N` headings.
For `multi_select`, separate correct answers with commas, such as `Answer: A, C`.

## Tests

```sh
npm test
```
