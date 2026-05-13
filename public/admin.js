const adminToken = decodeURIComponent(location.pathname.split('/').at(-1));
const headers = {
  'content-type': 'application/json',
  'x-admin-token': adminToken
};

const questionMarkdown = document.querySelector('#questionMarkdown');
const answerMarkdown = document.querySelector('#answerMarkdown');
const testStatus = document.querySelector('#testStatus');
const answerStatus = document.querySelector('#answerStatus');
const copyStatus = document.querySelector('#copyStatus');
const preview = document.querySelector('#preview');
const resultsList = document.querySelector('#resultsList');
const studentLink = document.querySelector('#studentLink');
const gptText = document.querySelector('#gptText');

document.querySelector('#saveTest').addEventListener('click', saveTest);
document.querySelector('#saveAnswers').addEventListener('click', saveAnswers);
document.querySelector('#scoreSubmissions').addEventListener('click', scoreSubmissions);
document.querySelector('#clearSubmissions').addEventListener('click', clearSubmissions);
document.querySelector('#refreshResults').addEventListener('click', loadResults);
document.querySelector('#copyGptText').addEventListener('click', copyGptText);
document.querySelector('#loadSampleTest').addEventListener('click', async () => {
  questionMarkdown.value = await fetchText('/public/sample-question-bank.md');
});
document.querySelector('#loadSampleAnswers').addEventListener('click', async () => {
  answerMarkdown.value = await fetchText('/public/sample-answer-bank.md');
});

await loadState();
await loadResults();

async function loadState() {
  const data = await request('/api/test', { headers });
  const test = data.activeTest;
  studentLink.href = data.studentLink;
  studentLink.textContent = `${location.origin}${data.studentLink}`;

  if (test) {
    questionMarkdown.value = test.rawMarkdown;
    renderPreview(test.parsed);
  }
}

async function saveTest() {
  await runWithStatus(testStatus, async () => {
    const data = await request('/api/test', {
      method: 'POST',
      headers,
      body: JSON.stringify({ rawMarkdown: questionMarkdown.value })
    });
    renderPreview(data.activeTest.parsed);
    await loadResults();
    return 'Test activated. Existing submissions were cleared.';
  });
}

async function saveAnswers() {
  await runWithStatus(answerStatus, async () => {
    await request('/api/answers', {
      method: 'POST',
      headers,
      body: JSON.stringify({ rawMarkdown: answerMarkdown.value })
    });
    return 'Answer bank saved.';
  });
}

async function scoreSubmissions() {
  await runWithStatus(answerStatus, async () => {
    await request('/api/score', { method: 'POST', headers });
    await loadResults();
    return 'Objective answers scored.';
  });
}

async function clearSubmissions() {
  await runWithStatus(testStatus, async () => {
    await request('/api/submissions', { method: 'DELETE', headers });
    await loadResults();
    return 'Submissions cleared.';
  });
}

async function loadResults() {
  const data = await request('/api/submissions', { headers });
  if (data.answerBank) answerMarkdown.value = data.answerBank.rawMarkdown;
  renderResults(data.submissions, data.activeTest?.parsed);
}

function renderPreview(test) {
  preview.classList.remove('empty');
  preview.innerHTML = `
    <div>
      <h3>${escapeHtml(test.title)}</h3>
      ${test.instructions ? `<pre class="meta">${escapeHtml(test.instructions)}</pre>` : ''}
    </div>
    ${test.passages.map(renderPassage).join('')}
    ${test.questions.map(renderQuestionPreview).join('')}
  `;
}

function renderPassage(passage) {
  return `
    <article class="passage">
      <h3>${escapeHtml(passage.title)}</h3>
      <pre>${escapeHtml(passage.body)}</pre>
    </article>
  `;
}

function renderQuestionPreview(question) {
  return `
    <article class="question">
      <div class="question-header">
        <h3>Question ${escapeHtml(question.number)}</h3>
        <span class="pill">${escapeHtml(question.type)} · ${question.points} pt${question.points === 1 ? '' : 's'}</span>
      </div>
      <pre>${escapeHtml(question.prompt)}</pre>
      ${question.options.length ? `<div class="options">${question.options.map((option) => `<div class="option"><strong>${option.label}.</strong> ${escapeHtml(option.text)}</div>`).join('')}</div>` : ''}
    </article>
  `;
}

function renderResults(submissions, test) {
  gptText.value = buildGptReviewText(submissions, test);

  if (!submissions.length) {
    resultsList.className = 'results empty';
    resultsList.textContent = 'No submissions yet.';
    return;
  }

  resultsList.className = 'results';
  resultsList.innerHTML = submissions.map((submission) => {
    const score = submission.score;
    const answerRows = (test?.questions ?? []).map((question) => {
      const scored = score?.byQuestion?.[question.number];
      const earned = scored?.earned ?? 'Review';
      const status = scored?.status ?? 'Unscored';
      return `
        <div class="question">
          <div class="question-header">
            <strong>Question ${escapeHtml(question.number)}</strong>
            <span class="pill">${escapeHtml(status)} · ${earned}/${question.points}</span>
          </div>
          <p class="meta">Answer</p>
          <pre>${escapeHtml(submission.answers?.[question.number] ?? '')}</pre>
          ${scored?.rubric ? `<p class="meta">Rubric</p><pre>${escapeHtml(scored.rubric)}</pre>` : ''}
        </div>
      `;
    }).join('');

    return `
      <article class="submission">
        <div class="submission-header">
          <div>
            <h3>${escapeHtml(submission.studentName)}</h3>
            <p class="meta">${new Date(submission.submittedAt).toLocaleString()}</p>
          </div>
          <span class="pill">${score ? `${score.earned}/${score.possible}` : 'Unscored'}</span>
        </div>
        <div class="answer-list">${answerRows}</div>
      </article>
    `;
  }).join('');
}

async function copyGptText() {
  copyStatus.className = 'status';
  copyStatus.textContent = '';

  if (!gptText.value.trim()) {
    copyStatus.className = 'status error';
    copyStatus.textContent = 'There is no submission text to copy yet.';
    return;
  }

  try {
    await navigator.clipboard.writeText(gptText.value);
    copyStatus.className = 'status ok';
    copyStatus.textContent = 'Copied.';
  } catch {
    gptText.focus();
    gptText.select();
    copyStatus.className = 'status';
    copyStatus.textContent = 'Text selected. Use Ctrl+C or Command+C to copy.';
  }
}

function buildGptReviewText(submissions, test) {
  if (!test) return '';
  if (!submissions.length) return '';

  const questionList = (test.questions ?? []).map((question) => {
    const options = question.options?.length
      ? `\nChoices:\n${question.options.map((option) => `${option.label}. ${option.text}`).join('\n')}`
      : '';

    return [
      `Question ${question.number}`,
      `Type: ${question.type}`,
      `Points: ${question.points}`,
      `Prompt: ${question.prompt}${options}`
    ].join('\n');
  }).join('\n\n');

  const submissionList = submissions.map((submission) => {
    const answers = (test.questions ?? []).map((question) => {
      const answer = submission.answers?.[question.number] || '[blank]';
      return `Question ${question.number}: ${answer}`;
    }).join('\n');

    return [
      `Student: ${submission.studentName}`,
      `Submitted: ${new Date(submission.submittedAt).toLocaleString()}`,
      'Answers:',
      answers
    ].join('\n');
  }).join('\n\n');

  return [
    'Please review these Grade 3 math practice-test submissions.',
    'Give encouraging, age-appropriate feedback. For short responses, note what is correct, what needs fixing, and a suggested score when possible.',
    '',
    `Test Title: ${test.title}`,
    '',
    'Questions:',
    questionList,
    '',
    'Student Submissions:',
    submissionList
  ].join('\n');
}

async function runWithStatus(element, callback) {
  element.className = 'status';
  element.textContent = 'Working...';
  try {
    const message = await callback();
    element.className = 'status ok';
    element.textContent = message;
  } catch (error) {
    element.className = 'status error';
    element.textContent = error.message;
  }
}

async function request(path, options = {}) {
  const response = await fetch(path, options);
  const data = await response.json();
  if (!response.ok) throw new Error(data.error || 'Request failed');
  return data;
}

async function fetchText(path) {
  const response = await fetch(path);
  return response.text();
}

function escapeHtml(value) {
  return String(value)
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#039;');
}
