const testToken = decodeURIComponent(location.pathname.split('/').at(-1));
const testTitle = document.querySelector('#testTitle');
const testContent = document.querySelector('#testContent');
const testForm = document.querySelector('#testForm');
const submitStatus = document.querySelector('#submitStatus');

const state = await request('/api/test', {
  headers: {
    'x-test-token': testToken
  }
});
const test = state.activeTest?.parsed;

if (!test) {
  testTitle.textContent = 'No active test';
  testContent.textContent = 'Ask your parent to activate a test.';
  testForm.querySelector('button').disabled = true;
} else {
  testTitle.textContent = test.title;
  renderTest(test);
}

testForm.addEventListener('submit', async (event) => {
  event.preventDefault();
  submitStatus.className = 'status';
  submitStatus.textContent = 'Submitting...';

  try {
    const formData = new FormData(testForm);
    const answers = {};
    for (const question of test.questions) {
      const value = formData.getAll(`q-${question.number}`);
      answers[question.number] = Array.isArray(value) && value.length > 1 ? value.join(', ') : value[0] ?? '';
    }

    await request('/api/submissions', {
      method: 'POST',
      headers: {
        'content-type': 'application/json',
        'x-test-token': testToken
      },
      body: JSON.stringify({
        studentName: formData.get('studentName'),
        answers
      })
    });

    testForm.innerHTML = '<section class="panel"><h2>Submitted</h2><p>Your answers were saved.</p></section>';
  } catch (error) {
    submitStatus.className = 'status error';
    submitStatus.textContent = error.message;
  }
});

function renderTest(activeTest) {
  testContent.innerHTML = `
    ${activeTest.instructions ? `<pre class="meta">${escapeHtml(activeTest.instructions)}</pre>` : ''}
    ${activeTest.passages.map(renderPassage).join('')}
    ${activeTest.questions.map(renderQuestion).join('')}
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

function renderQuestion(question) {
  const input = question.options.length ? renderOptions(question) : renderTextInput(question);
  return `
    <article class="question">
      <div class="question-header">
        <h3>Question ${escapeHtml(question.number)}</h3>
        <span class="pill">${question.points} pt${question.points === 1 ? '' : 's'}</span>
      </div>
      <pre>${escapeHtml(question.prompt)}</pre>
      ${input}
    </article>
  `;
}

function renderOptions(question) {
  const type = question.type === 'multi_select' ? 'checkbox' : 'radio';
  const required = type === 'radio' ? 'required' : '';
  return `
    <div class="options">
      ${question.options.map((option) => `
        <label class="option">
          <input type="${type}" name="q-${question.number}" value="${option.label}" ${required}>
          <span><strong>${option.label}.</strong> ${escapeHtml(option.text)}</span>
        </label>
      `).join('')}
    </div>
  `;
}

function renderTextInput(question) {
  if (question.type === 'text') {
    return `<input class="answer-control" name="q-${question.number}" required>`;
  }
  return `<textarea class="answer-control" name="q-${question.number}" required></textarea>`;
}

async function request(path, options = {}) {
  const response = await fetch(path, options);
  const data = await response.json();
  if (!response.ok) throw new Error(data.error || 'Request failed');
  return data;
}

function escapeHtml(value) {
  return String(value)
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#039;');
}
