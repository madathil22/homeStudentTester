# homeStudentTester Project Plan

## Goal

Build a simple family-use test-taking app for NJSLA-style practice.

The parent should be able to:

1. Paste a Markdown question bank.
2. Preview/activate the test.
3. Share a unique test link with kids.
4. Collect student submissions.
5. Paste a Markdown answer bank.
6. Compare submissions against answers.
7. Optionally use ChatGPT/OpenAI later to score written responses.

No user accounts, no complex auth, no classroom management, no multi-test scheduling. This app is for one parent running one active test at a time.

## Recommended Stack

Use a simple Node-based web app.

Preferred options:

- Next.js if we want a polished full-stack app quickly.
- Or plain Node/Express if we want minimal dependencies.

Storage:

- Start with SQLite or JSON file storage.
- Since this is one active test at a time, JSON file storage is acceptable for MVP.
- If using JSON, store data under `data/app.json`.

No Java needed.
# homeStudentTester Project Plan

## Goal

Build a simple family-use test-taking app for NJSLA-style practice.

The parent should be able to:

1. Paste a Markdown question bank.
2. Preview and activate the test.
3. Share a unique test link with kids.
4. Collect student submissions.
5. Paste a Markdown answer bank.
6. Compare submissions against answers.
7. Optionally use ChatGPT/OpenAI later to score written responses.

No user accounts, no complex auth, no classroom management, no multi-test scheduling. This app is for one parent running one active test at a time.

## Recommended Stack

Use a simple Node-based web app.

Preferred implementation:

- Plain Node/Express app for the MVP.
- SQLite or JSON file storage.
- Start with JSON file storage because only one active test is needed at a time.
- Store data under `data/app.json`.

No Java is needed. A Node 22 dev container is enough.

## Core Pages

### Parent/Admin Page

Suggested route:

```text
/admin/:adminToken
```

Features:

- Paste Question Bank Markdown.
- Parse and preview the test.
- Activate or replace the current test.
- Show generated student link.
- Clear or reset submissions.
- Paste Answer Bank Markdown.
- Score objective questions.
- View all submissions.

### Student Test Page

Suggested route:

```text
/take/:testToken
```

Features:

- Student enters name.
- App displays active test.
- Student answers all questions.
- Submit button saves attempt.
- Confirmation screen after submit.

### Parent Results Page

This can be part of the admin page or a separate page.

Suggested route:

```text
/results/:adminToken
```

Features:

- View all submissions.
- Compare student answers against the answer bank.
- Show total objective score.
- Show short response and essay answers with rubric.
- Later: button to score written answers with OpenAI.

## Secret URL Model

No login or auth for the MVP.

Use hard-to-guess tokens in URLs:

```text
/admin/parent-CHANGE-ME-LONG-RANDOM-TOKEN
/take/test-CHANGE-ME-LONG-RANDOM-TOKEN
/results/parent-CHANGE-ME-LONG-RANDOM-TOKEN
```

Store these in environment variables:

```env
ADMIN_TOKEN=parent-local-secret
TEST_TOKEN=test-local-secret
```

This is not strong production security, but it is acceptable for a private family tool.

## Question Bank Markdown Format

The app should parse a strict but friendly Markdown format.

Example:

```md
# Grade 4 ELA Practice Test

Instructions: Read each passage and answer the questions.

## Passage: The Hidden Garden

Maya found the old gate behind a wall of vines. She pushed carefully, and the gate opened with a soft creak.

The garden beyond it was wild but beautiful. Flowers leaned over the stone path, and a small fountain sat quietly in the center.

## Question 1
Type: multiple_choice
Points: 1

What is the main idea of the passage?

A. Maya dislikes gardens.
B. Maya discovers a forgotten garden.
C. Maya loses her way home.
D. Maya builds a fountain.

## Question 2
Type: short_response
Points: 2

How does Maya probably feel when she enters the garden? Use details from the passage.

## Question 3
Type: essay
Points: 4

Write a continuation of the story that shows what Maya does next.
```

Supported question types for the MVP:

```text
multiple_choice
multi_select
short_response
essay
text
```

Parsing rules:

- Test title comes from the first `#` heading.
- Passages start with `## Passage:`.
- Questions start with `## Question N`.
- Question metadata appears immediately after the question heading:
  - `Type:`
  - `Points:`
- Multiple choice options use `A.`, `B.`, `C.`, etc.
- Student should see passages and prompts, but not answers.

## Answer Bank Markdown Format

Example:

```md
# Answer Bank

## Question 1
Answer: B
Points: 1

Explanation: Maya discovers a forgotten garden behind the vines.

## Question 2
Points: 2

Rubric:
- 2: Clearly explains Maya's feelings and uses evidence from the passage.
- 1: Gives a partial explanation with little or no evidence.
- 0: Off-topic, blank, or incorrect.

Sample Answer:
Maya probably feels curious and amazed because she opens a hidden gate and sees a wild but beautiful garden.

## Question 3
Points: 4

Rubric:
- 4: Well-organized continuation with strong details and clear connection to the passage.
- 3: Clear continuation with some details.
- 2: Basic continuation with limited detail.
- 1: Minimal or unclear response.
- 0: Off-topic or blank.
```

Scoring rules:

- `multiple_choice`: exact match against `Answer: B`.
- `multi_select`: compare comma-separated answers, for example `Answer: A, C`.
- `text`: exact match or accepted match later.
- `short_response` and `essay`: mark as `Needs review` unless AI scoring is added.

Optional future accepted answers:

```md
Accept: 48, forty-eight
```

## Data Shape

For JSON storage, use something like:

```json
{
  "activeTest": {
    "rawMarkdown": "",
    "parsed": {
      "title": "",
      "instructions": "",
      "passages": [],
      "questions": []
    },
    "createdAt": ""
  },
  "answerBank": {
    "rawMarkdown": "",
    "parsed": {}
  },
  "submissions": [
    {
      "id": "",
      "studentName": "",
      "submittedAt": "",
      "answers": {
        "1": "B",
        "2": "Maya feels curious because..."
      },
      "score": null
    }
  ]
}
```

## MVP Implementation Checklist

1. Scaffold Node web app.
2. Add routes:
   - `/admin/:adminToken`
   - `/take/:testToken`
   - `/api/test`
   - `/api/submissions`
   - `/api/answers`
   - `/api/score`
3. Add Markdown parser for question bank.
4. Add Markdown parser for answer bank.
5. Add file-backed storage.
6. Build parent admin UI.
7. Build student test UI.
8. Build results UI.
9. Add deterministic scoring for objective questions.
10. Add clear errors for malformed Markdown.
11. Add sample question bank and answer bank.
12. Add README with local run instructions.

## Suggested File Structure

For a plain Node/Express version:

```text
homeStudentTester/
  package.json
  README.md
  PROJECT_PLAN.md
  .env.example
  data/
    app.json
  src/
    server.js
    storage.js
    markdownParser.js
    scorer.js
  public/
    styles.css
    admin.js
    take.js
```

For a Next.js version:

```text
homeStudentTester/
  package.json
  README.md
  PROJECT_PLAN.md
  .env.example
  data/
    app.json
  app/
    admin/[adminToken]/page.tsx
    take/[testToken]/page.tsx
    api/
      test/route.ts
      submissions/route.ts
      answers/route.ts
      score/route.ts
  lib/
    storage.ts
    markdownParser.ts
    scorer.ts
```

## Preferred First Build

Use the plain Node/Express version unless there is a strong reason to use Next.js.

Reason:

- Easier to run anywhere.
- Less framework complexity.
- Good enough for the family MVP.
- Simple file-backed storage.
- Easy to later migrate to Next.js if needed.

## Future OpenAI Scoring

Later add:

```env
OPENAI_API_KEY=...
```

Written-response scoring flow:

1. Parent clicks `Score written responses`.
2. Server sends:
   - question prompt
   - passage if relevant
   - student answer
   - rubric
   - max points
3. Model returns JSON:
   - score
   - feedback
   - parentNotes
   - evidence
4. App saves AI result to the submission.

Do not use AI for multiple choice or exact answers.

## Product Principle

Keep the app boring and reliable:

```text
Paste Markdown. Share link. Collect answers. Paste answers. Score.
```

## Prompt To Continue Later

Once the `homeStudentTester` repo is ready in DevPod, paste this prompt into Codex:

```text
Please build the MVP described in PROJECT_PLAN.md.
Use a simple Node app unless the repository already has a different stack.
Keep the first version focused on one active Markdown test, secret admin/test URLs, file-backed storage, student submissions, answer-bank parsing, and deterministic scoring for objective questions.
```

## Core Pages

### Parent/Admin Page

Suggested route:

```text
/admin/:adminToken
