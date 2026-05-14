import { CheckCircle2, ClipboardList, Clock3, ExternalLink, FileQuestion, LoaderCircle, LockKeyhole, LogOut, RefreshCcw, Trash2 } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import { apiRequest } from './api.js';

function getRoute() {
  const path = window.location.pathname.replace(/\/+$/, '');
  if (path === '' || path === '/') {
    return { route: 'admin', id: '' };
  }
  if (path === '/admin' || path === '/commandcenter' || path.startsWith('/results')) {
    return { route: 'admin', id: '' };
  }
  if (path.startsWith('/take/')) {
    return { route: 'student', id: decodeURIComponent(path.slice(6)) };
  }
  return { route: 'student', id: decodeURIComponent(path.slice(1)) };
}

function formatDuration(totalSeconds = 0) {
  const safeSeconds = Math.max(0, Math.round(Number(totalSeconds) || 0));
  const minutes = Math.floor(safeSeconds / 60);
  const seconds = safeSeconds % 60;
  if (minutes === 0) return `${seconds}s`;
  return `${minutes}m ${String(seconds).padStart(2, '0')}s`;
}

function formatScore(result) {
  if (!result) return 'Not submitted';
  return `${result.earned}/${result.possible}`;
}

const ADMIN_PASSWORD_STORAGE_KEY = 'homestudenttester.adminPassword';

export function App() {
  const { route, id } = useMemo(getRoute, []);

  if (route === 'student' && id) return <StudentApp id={id} />;
  return <AdminApp />;
}

function AdminApp() {
  const [status, setStatus] = useState('Ready to manage test subjects.');
  const [health, setHealth] = useState(null);
  const [subject, setSubject] = useState('');
  const [tests, setTests] = useState([]);
  const [selectedTestId, setSelectedTestId] = useState('');
  const [loading, setLoading] = useState(false);
  const [generationStage, setGenerationStage] = useState('idle');
  const [adminPassword, setAdminPassword] = useState(() => sessionStorage.getItem(ADMIN_PASSWORD_STORAGE_KEY) || '');

  useEffect(() => {
    if (adminPassword) {
      refreshTests(adminPassword);
    }
  }, [adminPassword]);

  const selectedTest = tests.find((test) => test.id === selectedTestId) || tests[0] || null;
  const totalSubmitted = tests.filter((test) => test.result).length;

  function adminHeaders(password = adminPassword) {
    return { 'x-admin-token': password };
  }

  function unlockCommandCenter(password) {
    const trimmedPassword = password.trim();
    sessionStorage.setItem(ADMIN_PASSWORD_STORAGE_KEY, trimmedPassword);
    setAdminPassword(trimmedPassword);
  }

  function lockCommandCenter() {
    sessionStorage.removeItem(ADMIN_PASSWORD_STORAGE_KEY);
    setAdminPassword('');
    setTests([]);
    setSelectedTestId('');
    setStatus('Command center locked.');
  }

  async function refreshTests(password = adminPassword) {
    setStatus('Loading test subjects...');
    try {
      const data = await apiRequest('/api/tests', {
        headers: adminHeaders(password)
      });
      const nextTests = data.tests || [];
      setTests(nextTests);
      setSelectedTestId((currentId) => {
        if (nextTests.some((test) => test.id === currentId)) return currentId;
        return nextTests[0]?.id || '';
      });
      setStatus('Test subjects loaded.');
    } catch (error) {
      if (error.status === 401 || error.status === 503) {
        lockCommandCenter();
      }
      setStatus(error.message);
    }
  }

  async function checkApi() {
    setStatus('Checking Spring API...');
    try {
      const data = await apiRequest('/api/health');
      setHealth(data.status);
      setStatus('Spring API responded.');
    } catch (error) {
      setStatus(error.message);
    }
  }

  async function createTest() {
    if (!subject.trim()) {
      setStatus('Test subject is required.');
      return;
    }

    setLoading(true);
    setGenerationStage('prompt');
    setStatus('Reading request...');
    try {
      await wait(250);
      setGenerationStage('generate');
      setStatus('Generating question bank...');
      await apiRequest('/api/test/generate', {
        method: 'POST',
        headers: { 'content-type': 'application/json', ...adminHeaders() },
        body: JSON.stringify({ subject: subject.trim() })
      });
      setGenerationStage('publish');
      setStatus('Publishing student link...');
      setSubject('');
      await refreshTests();
      setGenerationStage('done');
      setStatus('New test subject created.');
    } catch (error) {
      setGenerationStage('error');
      setStatus(error.message);
    } finally {
      setLoading(false);
      window.setTimeout(() => setGenerationStage('idle'), 1400);
    }
  }

  async function deleteTest(id) {
    setLoading(true);
    setStatus('Deleting test subject...');
    try {
      await apiRequest(`/api/tests/${encodeURIComponent(id)}`, {
        method: 'DELETE',
        headers: adminHeaders()
      });
      await refreshTests();
      setStatus('Test subject deleted.');
    } catch (error) {
      setStatus(error.message);
    } finally {
      setLoading(false);
    }
  }

  if (!adminPassword) {
    return <CommandCenterLogin onUnlock={unlockCommandCenter} status={status} />;
  }

  return (
    <main className="app-shell teacher-shell">
      <section className="toolbar teacher-toolbar">
        <div>
          <p className="eyebrow">Teacher</p>
          <h1>Test Command Center</h1>
        </div>
        <button type="button" onClick={checkApi} aria-label="Refresh API status" disabled={loading}>
          <RefreshCcw size={18} aria-hidden="true" />
          Check API
        </button>
        <button type="button" className="secondary" onClick={lockCommandCenter} disabled={loading}>
          <LogOut size={18} aria-hidden="true" />
          Lock
        </button>
      </section>

      <section className="teacher-create-panel">
        <div className="request-card">
          <div className="panel-heading">
            <div>
              <p className="eyebrow">Create</p>
              <h2>Generate a Student Test Link</h2>
            </div>
            <ClipboardList size={22} aria-hidden="true" />
          </div>
          <p className="helper-text">Enter the full request, including subject, grade level, and question count when needed.</p>
          <div className="request-row">
            <label className="field request-field">
              <span>Test request</span>
              <input
                type="text"
                value={subject}
                onChange={(event) => setSubject(event.target.value)}
                placeholder="e.g. 3 questions for 3rd grade math"
              />
            </label>
            <button type="button" onClick={createTest} disabled={loading}>
              {loading ? <LoaderCircle className="spin" size={18} aria-hidden="true" /> : <FileQuestion size={18} aria-hidden="true" />}
              {loading ? 'Creating' : 'Create Test'}
            </button>
          </div>
        </div>

        <div className="stage-card">
          <div className="status-line">
            <span className={health === 'ok' ? 'status-dot ok' : 'status-dot'} />
            <span>API: {health ?? 'Not checked'}</span>
          </div>
          <GenerationProgress stage={generationStage} />
          <p className="status-message">{status}</p>
        </div>
      </section>

      <section className="teacher-workspace">
        <div className="panel test-grid-panel">
          <div className="panel-heading">
            <div>
              <p className="eyebrow">Library</p>
              <h2>Generated Tests</h2>
            </div>
            <span className="count-pill">{tests.length} tests</span>
          </div>
          {tests.length === 0 ? (
            <div className="empty-state compact-empty">
              <FileQuestion size={22} aria-hidden="true" />
              <p>No generated tests yet.</p>
            </div>
          ) : (
            <div className="data-grid" role="grid" aria-label="Generated tests">
              <div className="data-grid-header" role="row">
                <span role="columnheader">Request</span>
                <span role="columnheader">Created</span>
                <span role="columnheader">Score</span>
                <span role="columnheader">Actions</span>
              </div>
              {tests.map((test) => (
                <div
                  className={`data-grid-row ${selectedTest?.id === test.id ? 'selected' : ''}`}
                  role="row"
                  tabIndex={0}
                  key={test.id}
                  onClick={() => setSelectedTestId(test.id)}
                  onKeyDown={(event) => {
                    if (event.key === 'Enter' || event.key === ' ') {
                      event.preventDefault();
                      setSelectedTestId(test.id);
                    }
                  }}
                >
                  <span className="subject-cell" role="gridcell">
                    <strong>{test.subject}</strong>
                    <small>{test.link}</small>
                  </span>
                  <span role="gridcell">{new Date(test.createdAt).toLocaleDateString()}</span>
                  <span role="gridcell">
                    <ScoreBadge result={test.result} />
                  </span>
                  <span className="row-actions" role="gridcell">
                    <a
                      href={new URL(test.link, window.location.origin).toString()}
                      target="_blank"
                      rel="noreferrer"
                      aria-label={`Open ${test.subject}`}
                      onClick={(event) => event.stopPropagation()}
                    >
                      <ExternalLink size={16} aria-hidden="true" />
                    </a>
                    <button
                      className="danger icon-button"
                      type="button"
                      onClick={(event) => {
                        event.stopPropagation();
                        deleteTest(test.id);
                      }}
                      disabled={loading}
                      aria-label={`Delete ${test.subject}`}
                    >
                      <Trash2 size={16} aria-hidden="true" />
                    </button>
                  </span>
                </div>
              ))}
            </div>
          )}
        </div>

        <ResultsPanel selectedTest={selectedTest} totalSubmitted={totalSubmitted} />
      </section>
    </main>
  );
}

function CommandCenterLogin({ onUnlock, status }) {
  const [password, setPassword] = useState('');

  function submitLogin(event) {
    event.preventDefault();
    if (!password.trim()) return;
    onUnlock(password);
  }

  return (
    <main className="app-shell login-shell">
      <section className="login-panel">
        <div className="login-icon">
          <LockKeyhole size={28} aria-hidden="true" />
        </div>
        <p className="eyebrow">Teacher</p>
        <h1>Command Center</h1>
        <p className="helper-text">Enter the admin password to manage tests and view results.</p>
        <form className="login-form" onSubmit={submitLogin}>
          <label className="field">
            <span>Admin password</span>
            <input
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              autoComplete="current-password"
              autoFocus
            />
          </label>
          <button type="submit" disabled={!password.trim()}>
            <LockKeyhole size={18} aria-hidden="true" />
            Unlock
          </button>
        </form>
        <p className="status-message">{status}</p>
      </section>
    </main>
  );
}

function wait(ms) {
  return new Promise((resolve) => window.setTimeout(resolve, ms));
}

function GenerationProgress({ stage }) {
  const stages = [
    { id: 'prompt', label: 'Read request' },
    { id: 'generate', label: 'Build JSON' },
    { id: 'publish', label: 'Publish link' }
  ];
  const stageIndex = stages.findIndex((item) => item.id === stage);

  return (
    <div className="generation-progress" aria-label="Test generation progress">
      {stages.map((item, index) => {
        const complete = stage === 'done' || stageIndex > index;
        const active = stage === item.id;
        return (
          <div className={`stage-step ${complete ? 'complete' : ''} ${active ? 'active' : ''}`} key={item.id}>
            {complete ? <CheckCircle2 size={16} aria-hidden="true" /> : active ? <LoaderCircle className="spin" size={16} aria-hidden="true" /> : <span />}
            <span>{item.label}</span>
          </div>
        );
      })}
    </div>
  );
}

function ScoreBadge({ result }) {
  if (!result) return <span className="score-badge pending">Pending</span>;
  return <span className="score-badge">{formatScore(result)}</span>;
}

function ResultsPanel({ selectedTest, totalSubmitted }) {
  if (!selectedTest) {
    return (
      <aside className="panel results-detail-panel">
        <div className="empty-state compact-empty">
          <ClipboardList size={22} aria-hidden="true" />
          <p>Select a test to inspect results.</p>
        </div>
      </aside>
    );
  }

  const result = selectedTest.result;

  return (
    <aside className="panel results-detail-panel">
      <div className="panel-heading">
        <div>
          <p className="eyebrow">Results</p>
          <h2>{selectedTest.subject}</h2>
        </div>
        <ScoreBadge result={result} />
      </div>

      <div className="link-box selected-link">
        <span>Student link</span>
        <a href={new URL(selectedTest.link, window.location.origin).toString()} target="_blank" rel="noreferrer">
          {selectedTest.link}
          <ExternalLink size={15} aria-hidden="true" />
        </a>
      </div>

      {result ? (
        <>
          <div className="result-metrics">
            <Metric label="Questions" value={result.questionCount} />
            <Metric label="Total time" value={formatDuration(result.elapsedSeconds)} />
            <Metric label="Avg / question" value={formatDuration(result.averageSecondsPerQuestion)} />
            <Metric label="Final score" value={formatScore(result)} />
          </div>
          <section className="wrong-answer-panel">
            <div className="section-title">
              <h3>Wrong Answers</h3>
              <span>{result.wrongAnswers?.length || 0}</span>
            </div>
            {result.wrongAnswers?.length > 0 ? (
              <div className="wrong-answer-list">
                {result.wrongAnswers.map((wrongAnswer) => (
                  <article className="wrong-answer-card" key={`${selectedTest.id}-${wrongAnswer.questionNumber}`}>
                    <strong>Question {wrongAnswer.questionNumber}</strong>
                    <p>{wrongAnswer.prompt}</p>
                    <dl>
                      <div>
                        <dt>Submitted</dt>
                        <dd>{wrongAnswer.studentAnswer || 'No answer'}</dd>
                      </div>
                      <div>
                        <dt>Expected</dt>
                        <dd>{wrongAnswer.expectedAnswer || 'Not provided'}</dd>
                      </div>
                      <div>
                        <dt>Feedback</dt>
                        <dd>{wrongAnswer.explanation || 'No explanation provided.'}</dd>
                      </div>
                    </dl>
                  </article>
                ))}
              </div>
            ) : (
              <div className="empty-state compact-empty">
                <CheckCircle2 size={22} aria-hidden="true" />
                <p>No wrong answers recorded.</p>
              </div>
            )}
          </section>
        </>
      ) : (
        <div className="pending-result">
          <Clock3 size={22} aria-hidden="true" />
          <p>No student submission for this test yet.</p>
          <span>{totalSubmitted} tests have results so far.</span>
        </div>
      )}
    </aside>
  );
}

function Metric({ label, value }) {
  return (
    <div className="metric">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

function StudentApp({ id }) {
  return (
    <main className="app-shell">
      <section className="toolbar">
        <div>
          <p className="eyebrow">Student</p>
          <h1>Take Test</h1>
        </div>
      </section>

      <section className="panel student-panel">
        <h2>Test Preview</h2>
        <p>Open the test in the embedded preview. If the page does not render, the backend may not be running.</p>
        <iframe
          title="Student Test"
          src={`/api/test/html/${encodeURIComponent(id)}`}
          style={{ width: '100%', minHeight: '80vh', border: '1px solid #ccc' }}
        />
      </section>
    </main>
  );
}
