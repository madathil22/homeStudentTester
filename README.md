# homeStudentTester

A small family-use test-taking app for one active Markdown test at a time.

## Run Locally

Requires Node 22 or newer.

```sh
npm start
```

Then open:

```text
http://localhost:3000/admin/test-admin
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

Set different local tokens in `.env`:

```env
ADMIN_TOKEN=parent-my-secret
TEST_TOKEN=test-my-secret
HOST=0.0.0.0
PORT=3000
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
