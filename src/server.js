import { createReadStream } from 'node:fs';
import { readFile } from 'node:fs/promises';
import { createServer } from 'node:http';
import { randomUUID } from 'node:crypto';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { parseAnswerBank, parseQuestionBank } from './markdownParser.js';
import { scoreSubmission } from './scorer.js';
import { readState, updateState } from './storage.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const publicDir = path.join(__dirname, '..', 'public');

const PORT = Number(process.env.PORT ?? 3000);
const HOST = process.env.HOST ?? '0.0.0.0';
const ADMIN_TOKEN = process.env.ADMIN_TOKEN ?? 'parent-local-secret';
const TEST_TOKEN = process.env.TEST_TOKEN ?? 'test-local-secret';

const server = createServer(async (request, response) => {
  try {
    const url = new URL(request.url, `http://${request.headers.host}`);
    const route = matchRoute(request.method, url.pathname);

    if (route) {
      await route(request, response, url);
      return;
    }

    if (request.method === 'GET' && url.pathname.startsWith('/public/')) {
      await serveStatic(response, url.pathname.replace('/public/', ''));
      return;
    }

    send(response, 404, { error: 'Not found' });
  } catch (error) {
    send(response, 500, { error: error.message || 'Unexpected server error' });
  }
});

server.listen(PORT, HOST, () => {
  console.log(`homeStudentTester running locally at http://localhost:${PORT}/admin/${ADMIN_TOKEN}`);
  console.log(`Use your Wi-Fi IP for other devices: http://YOUR-COMPUTER-IP:${PORT}/take/${TEST_TOKEN}`);
});

function matchRoute(method, pathname) {
  if (method === 'GET' && pathname === '/') return redirectToAdmin;
  if (method === 'GET' && pathname === `/admin/${ADMIN_TOKEN}`) return page('admin.html');
  if (method === 'GET' && pathname === `/results/${ADMIN_TOKEN}`) return page('admin.html');
  if (method === 'GET' && pathname === `/take/${TEST_TOKEN}`) return page('take.html');
  if (pathname.startsWith('/admin/') || pathname.startsWith('/results/')) return forbidden;
  if (pathname.startsWith('/take/')) return forbidden;

  if (method === 'GET' && pathname === '/api/test') return requireAnyToken(getTest);
  if (method === 'POST' && pathname === '/api/test') return requireAdmin(saveTest);
  if (method === 'DELETE' && pathname === '/api/submissions') return requireAdmin(clearSubmissions);
  if (method === 'GET' && pathname === '/api/submissions') return requireAdmin(getSubmissions);
  if (method === 'POST' && pathname === '/api/submissions') return requireTest(saveSubmission);
  if (method === 'POST' && pathname === '/api/answers') return requireAdmin(saveAnswers);
  if (method === 'POST' && pathname === '/api/score') return requireAdmin(scoreAll);
  return null;
}

async function redirectToAdmin(_request, response) {
  response.writeHead(302, { Location: `/admin/${ADMIN_TOKEN}` });
  response.end();
}

function page(filename) {
  return async (_request, response) => {
    response.setHeader('content-type', 'text/html; charset=utf-8');
    createReadStream(path.join(publicDir, filename)).pipe(response);
  };
}

async function serveStatic(response, filename) {
  const safeName = path.normalize(filename).replace(/^(\.\.[/\\])+/, '');
  const ext = path.extname(safeName);
  const contentTypes = {
    '.css': 'text/css; charset=utf-8',
    '.js': 'text/javascript; charset=utf-8'
  };

  response.setHeader('content-type', contentTypes[ext] ?? 'application/octet-stream');
  createReadStream(path.join(publicDir, safeName))
    .on('error', () => send(response, 404, { error: 'Not found' }))
    .pipe(response);
}

async function forbidden(_request, response) {
  send(response, 403, { error: 'Bad or missing secret token.' });
}

async function getTest(_request, response) {
  const state = await readState();
  send(response, 200, {
    activeTest: state.activeTest,
    hasAnswerBank: Boolean(state.answerBank),
    submissionCount: state.submissions.length,
    studentLink: `/take/${TEST_TOKEN}`,
    adminLink: `/admin/${ADMIN_TOKEN}`,
    resultsLink: `/results/${ADMIN_TOKEN}`
  });
}

async function saveTest(request, response) {
  const body = await readJson(request);
  const rawMarkdown = String(body.rawMarkdown ?? '');
  const parsed = parseQuestionBank(rawMarkdown);
  const state = await updateState((current) => ({
    ...current,
    activeTest: {
      rawMarkdown,
      parsed,
      createdAt: new Date().toISOString()
    },
    submissions: []
  }));

  send(response, 200, { activeTest: state.activeTest, submissionsCleared: true });
}

async function saveAnswers(request, response) {
  const body = await readJson(request);
  const rawMarkdown = String(body.rawMarkdown ?? '');
  const parsed = parseAnswerBank(rawMarkdown);
  const state = await updateState((current) => ({
    ...current,
    answerBank: {
      rawMarkdown,
      parsed,
      createdAt: new Date().toISOString()
    }
  }));

  send(response, 200, { answerBank: state.answerBank });
}

async function getSubmissions(_request, response) {
  const state = await readState();
  send(response, 200, {
    submissions: state.submissions,
    activeTest: state.activeTest,
    answerBank: state.answerBank
  });
}

async function saveSubmission(request, response) {
  const state = await readState();
  if (!state.activeTest?.parsed) {
    send(response, 400, { error: 'No active test is available.' });
    return;
  }

  const body = await readJson(request);
  const studentName = String(body.studentName ?? '').trim();
  if (!studentName) {
    send(response, 400, { error: 'Student name is required.' });
    return;
  }

  const submission = {
    id: randomUUID(),
    studentName,
    submittedAt: new Date().toISOString(),
    answers: body.answers ?? {},
    score: null
  };

  await updateState((current) => ({
    ...current,
    submissions: [...current.submissions, submission]
  }));

  send(response, 201, { submission });
}

async function clearSubmissions(_request, response) {
  await updateState((current) => ({ ...current, submissions: [] }));
  send(response, 200, { ok: true });
}

async function scoreAll(_request, response) {
  const state = await updateState((current) => {
    if (!current.activeTest?.parsed) throw new Error('No active test to score.');
    if (!current.answerBank?.parsed) throw new Error('No answer bank to score against.');

    return {
      ...current,
      submissions: current.submissions.map((submission) => ({
        ...submission,
        score: scoreSubmission(current.activeTest.parsed, current.answerBank.parsed, submission)
      }))
    };
  });

  send(response, 200, { submissions: state.submissions });
}

function requireAdmin(handler) {
  return async (request, response, url) => {
    if (request.headers['x-admin-token'] !== ADMIN_TOKEN) {
      send(response, 403, { error: 'Bad or missing admin token.' });
      return;
    }
    await handler(request, response, url);
  };
}

function requireTest(handler) {
  return async (request, response, url) => {
    if (request.headers['x-test-token'] !== TEST_TOKEN) {
      send(response, 403, { error: 'Bad or missing test token.' });
      return;
    }
    await handler(request, response, url);
  };
}

function requireAnyToken(handler) {
  return async (request, response, url) => {
    const hasAdminToken = request.headers['x-admin-token'] === ADMIN_TOKEN;
    const hasTestToken = request.headers['x-test-token'] === TEST_TOKEN;
    if (!hasAdminToken && !hasTestToken) {
      send(response, 403, { error: 'Bad or missing secret token.' });
      return;
    }
    await handler(request, response, url);
  };
}

async function readJson(request) {
  const chunks = [];
  for await (const chunk of request) chunks.push(chunk);
  if (!chunks.length) return {};
  return JSON.parse(Buffer.concat(chunks).toString('utf8'));
}

function send(response, statusCode, payload) {
  response.writeHead(statusCode, { 'content-type': 'application/json; charset=utf-8' });
  response.end(JSON.stringify(payload));
}
