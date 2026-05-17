# homeStudentTester

> Local family-use test generation, test taking, and AI-assisted scoring.

`homeStudentTester` turns a teacher request into a structured JSON question bank
with OpenAI, renders that data into a consistent HTML test page, stores the test
locally in H2, and gives students a stable link such as `/test-amber-fox`.

## 🔎 Overview

| Area       | Details                                           |
| ---------- | ------------------------------------------------- |
| Teacher UI | `/commandcenter`, locked with `ADMIN_PASSWORD`    |
| Student UI | Stable generated links such as `/test-amber-fox`, `/test-blue-fox` |
| Backend    | Spring Boot 3.3.5, Java 21, H2                    |
| Frontend   | React 18, Vite, lucide-react                      |
| AI flow    | OpenAI JSON question generation and scoring       |
| Storage    | Generated HTML plus question-bank/result metadata |

## 🧭 Flow

1. Teacher opens `/commandcenter`.
2. Teacher unlocks the command center with `ADMIN_PASSWORD`.
3. Teacher fills in structured test details:
   - state, defaulting to `NJ`
   - grade level
   - subject
   - either question count or time-bound length
   - free-text topic, such as `3 digit addition`
4. Backend asks OpenAI for strict JSON matching the question-bank shape.
5. Backend validates constraints such as exact requested question count.
6. Backend renders the JSON into a fixed HTML template with:
   - student name field
   - elapsed-time ticker
   - answer inputs
   - submit button
   - MathJax support for TeX-formatted math expressions
7. Student opens the generated link and submits answers.
8. Backend sends the stored question bank, submitted answers, and timing to
   OpenAI for scoring.
9. The command center displays the selected test's result summary and
   wrong-answer details.

## ⚡ Quick Start

### 🔐 1. Configure `.env`

Create a local `.env` at the workspace root:

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

Keep `.env` private. It is ignored by Git.

Readable generated-test links are built from the `app.test-link-colors` and
`app.test-link-animals` arrays in `backend/src/main/resources/application.yml`.

### 🧩 2. Start The Backend

From `/workspaces/homestudenttester/backend`:

```sh
gradle bootRun
```

The VS Code backend task sources the workspace-root `.env` before starting
Spring Boot.

### 🖥️ 3. Start The Frontend

From `/workspaces/homestudenttester/frontend`:

```sh
npm install
npm run dev
```

Open the Vite URL, typically `http://localhost:5173`, then navigate to
`/commandcenter`.

## 🎛️ Command Center

The command center is the teacher workspace.

- Unlocks with `ADMIN_PASSWORD`.
- Creates generated tests from required structured inputs.
- Shows generation progress.
- Lists generated tests in a selectable grid split by grade level, subject,
  type, topic, score, and actions.
- Opens or deletes generated test links.
- Shows a right-side results panel for the selected test.

Result details include:

- number of questions
- total time taken
- average time per question
- final score
- generation and scoring token usage per test record
- wrong-answer feedback

## 📝 Student Experience

Students open links like `/test-amber-fox`.

The generated page includes:

- the test title and instructions
- student name field
- questions rendered from JSON
- elapsed-time ticker
- submit button
- rendered math notation for TeX expressions such as `\(f(x)=ax^2+bx+c\)`

On submit, the page sends answers and elapsed time to the backend. The backend
scores the submission through OpenAI and stores the latest result for the
teacher to review.

## 🔌 API Reference

Teacher endpoints require the `x-admin-token` header containing
`ADMIN_PASSWORD`.

| Method   | Endpoint                    | Purpose                                        |
| -------- | --------------------------- | ---------------------------------------------- |
| `POST`   | `/api/test/generate`        | Create a generated test from a teacher request |
| `GET`    | `/api/tests`                | List generated tests and latest results        |
| `DELETE` | `/api/tests/{testId}`       | Delete a generated test                        |
| `GET`    | `/api/test/html/{testId}`   | Serve stored generated HTML                    |
| `POST`   | `/api/test/{testId}/submit` | Submit student answers for scoring             |
| `GET`    | `/api/health`               | Health check                                   |

Student HTML and student submissions do not require the admin password.

## 🧱 Generated Question JSON

OpenAI returns JSON shaped around the existing DTOs:

```json
{
  "title": "string",
  "instructions": "string",
  "passages": [{ "id": "p1", "title": "string", "body": "string" }],
  "questions": [
    {
      "number": "1",
      "type": "multiple_choice",
      "points": 1,
      "prompt": "string",
      "options": [{ "label": "A", "text": "string" }],
      "passageIds": [],
      "correctOptionLabels": ["A"],
      "expectedAnswer": ""
    }
  ]
}
```

Supported generated question types:

- `multiple_choice`
- `multi_select`
- `free_text`

The backend also normalizes text-style aliases such as `short_answer`,
`short_response`, `essay`, and `text` to `free_text`.

Generated questions include answer metadata so the backend can reject invalid
question shapes before publishing: `multiple_choice` must have exactly one
correct option, `multi_select` must have more than one correct option, and
`free_text` must provide an `expectedAnswer`.

For math-heavy tests, generation now asks OpenAI to return TeX-formatted math
inside strings, using `\( ... \)` for inline math and `\[ ... \]` for display
math. The stored HTML template loads MathJax so those expressions render like
textbook notation on the student sheet.

## 📁 Project Layout

```txt
backend/   Spring Boot API, OpenAI integration, H2 persistence
frontend/  React command center and student route wrapper
.vscode/   Convenience tasks
```

Important files:

- `frontend/src/App.jsx` - command center, student route, results panel
- `frontend/src/api.js` - fetch helper
- `frontend/src/styles.css` - app styling
- `backend/src/main/java/com/homestudenttester/controller/TestApiController.java` - API routes
- `backend/src/main/java/com/homestudenttester/service/OpenAiService.java` - OpenAI generation/scoring and HTML rendering
- `backend/src/main/java/com/homestudenttester/service/AppStateService.java` - H2-backed generated-test state
- `backend/src/main/java/com/homestudenttester/service/AuthService.java` - admin password enforcement
- `backend/src/main/java/com/homestudenttester/controller/ApiExceptionHandler.java` - API error mapping and backend error logging

## ✅ Verification

Frontend:

```sh
cd frontend
npm test
```

Backend:

```sh
cd backend
gradle test
```

There is no Gradle wrapper checked in yet, so backend commands depend on system
`gradle`.

## 🔍 Backend Diagnostics

The generated-test path now logs the major backend stages so failures are easier
to diagnose from the server console:

- request receipt
- OpenAI generation/scoring start
- response status and duration
- parse and validation stages
- generated-test/result persistence
- exception paths, including non-200 OpenAI responses and I/O failures

The logs intentionally avoid printing secrets such as `OPENAI_API_KEY`.
