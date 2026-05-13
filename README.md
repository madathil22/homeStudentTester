# homeStudentTester

A small family-use test-taking app for one active Markdown test at a time. A
parent can paste a Markdown question bank, paste an optional Markdown answer
bank, share a student link on the local network, collect submissions, and score
objective answers.

## Run Locally

Requires Node 22 or newer.

```sh
npm start
```

Then open:

```text
http://localhost:3000/admin/test-admin
```

This project's `.env` currently uses:

```env
ADMIN_TOKEN=test-admin
TEST_TOKEN=test-paper
HOST=0.0.0.0
PORT=3000
```

So the local URLs are:

```text
http://localhost:3000/admin/test-admin
http://localhost:3000/take/test-paper
http://localhost:3000/results/test-admin
```

If `.env` is missing, the server falls back to:

```env
ADMIN_TOKEN=parent-local-secret
TEST_TOKEN=test-local-secret
HOST=0.0.0.0
PORT=3000
```

Set different tokens in `.env` before starting the app if you want different
URLs:

```env
ADMIN_TOKEN=parent-my-secret
TEST_TOKEN=test-my-secret
HOST=0.0.0.0
PORT=3000
```

For development with Node's watch mode:

```sh
npm run dev
```

## Use On Your Wi-Fi

The app reads `.env` when you run `npm start`. To allow other devices on your Wi-Fi, keep:

```env
HOST=0.0.0.0
PORT=3000
```

Start the app:

```sh
npm start
```

Find your computer's Wi-Fi IP address:

```sh
hostname -I
```

Then open the student test URL on another device using that IP:

```text
http://YOUR-COMPUTER-IP:3000/take/test-paper
```

Example:

```text
http://192.168.1.25:3000/take/test-paper
```

The parent/admin URL from another device would be:

```text
http://YOUR-COMPUTER-IP:3000/admin/test-admin
```

If another device cannot connect, make sure both devices are on the same Wi-Fi and allow Node/port `3000` through your computer firewall.

Set local tokens in `.env`:

```env
ADMIN_TOKEN=test-admin
TEST_TOKEN=test-paper
HOST=0.0.0.0
PORT=3000
```

## What Works

- Parent admin page at `/admin/:adminToken`
- Parent results page at `/results/:adminToken`
- Student test page at `/take/:testToken`
- Paste, parse, preview, and activate a Markdown question bank
- Paste and parse a Markdown answer bank
- Save student submissions to `data/app.json`
- Clear submissions
- Score objective `multiple_choice`, `multi_select`, and `text` answers
- Leave `short_response` and `essay` answers marked as `Needs review`
- Copy GPT-ready review text from the admin/results page for written-response feedback
- Load sample question and answer banks from the admin page

Activating a new test clears existing submissions. Saved test data, answer data,
and submissions are stored locally in `data/app.json`.

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

## Sample Content

The admin page has buttons to load sample question and answer banks from:

- `public/sample-question-bank.md`
- `public/sample-answer-bank.md`

## Tests

```sh
npm test
```
