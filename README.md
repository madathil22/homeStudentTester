# homeStudentTester

A small family-use test-taking app for one active Markdown test at a time.

## Run Locally

Requires Node 22 or newer.

```sh
npm start
```

Then open:

```text
http://localhost:3000/admin/parent-local-secret
```

Set different local tokens with environment variables:

```sh
ADMIN_TOKEN=parent-my-secret TEST_TOKEN=test-my-secret npm start
```

## What Works

- Parent admin page at `/admin/:adminToken`
- Student test page at `/take/:testToken`
- Paste, parse, preview, and activate a Markdown question bank
- Paste and parse a Markdown answer bank
- Save student submissions to `data/app.json`
- Clear submissions
- Score objective `multiple_choice`, `multi_select`, and `text` answers
- Leave `short_response` and `essay` answers marked as `Needs review`

## Sample Content

The admin page has buttons to load sample question and answer banks from:

- `public/sample-question-bank.md`
- `public/sample-answer-bank.md`

## Tests

```sh
npm test
```
