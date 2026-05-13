import {
  BookOpenCheck,
  ClipboardCheck,
  ClipboardList,
  Eraser,
  FilePlus2,
  RefreshCcw,
  Trash2
} from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import { apiRequest } from './api.js';

function getRoute() {
  const [, route, token] = window.location.pathname.split('/');
  return { route: route || 'admin', token: decodeURIComponent(token || 'test-admin') };
}

export function App() {
  const { route, token } = useMemo(getRoute, []);

  if (route === 'take') return <StudentApp token={token} />;
  return <AdminApp token={token} initialSection={route === 'results' ? 'results' : 'creation'} />;
}

const testCatalogStorageKey = 'homestudenttester.testCatalog';

function AdminApp({ token, initialSection }) {
  const [section, setSection] = useState(initialSection);
  const [status, setStatus] = useState('Ready.');
  const [loading, setLoading] = useState(false);
  const [overview, setOverview] = useState(null);
  const [results, setResults] = useState(null);
  const [testSubject, setTestSubject] = useState('');
  const [testCatalog, setTestCatalog] = useState(readTestCatalog);

  const adminHeaders = useMemo(() => ({ 'x-admin-token': token }), [token]);

  useEffect(() => {
    refreshAdminData();
  }, []);

  useEffect(() => {
    window.localStorage.setItem(testCatalogStorageKey, JSON.stringify(testCatalog));
  }, [testCatalog]);

  async function refreshAdminData() {
    setLoading(true);
    setStatus('Refreshing admin data...');
    try {
      const [testData, submissionData] = await Promise.all([
        apiRequest('/api/test', { headers: adminHeaders }),
        apiRequest('/api/submissions', { headers: adminHeaders })
      ]);
      setOverview(testData);
      setResults(submissionData);
      setStatus('Admin data is current.');
    } catch (error) {
      setStatus(error.message);
    } finally {
      setLoading(false);
    }
  }

  async function createTest() {
    const subject = testSubject.trim();
    if (!subject) {
      setStatus('Test Subject is required.');
      return;
    }

    setLoading(true);
    setStatus('Creating test...');
    try {
      await apiRequest('/api/test', {
        method: 'POST',
        headers: { ...adminHeaders, 'content-type': 'application/json' },
        body: JSON.stringify({ rawMarkdown: buildStarterTestMarkdown(subject) })
      });
      await apiRequest('/api/answers', {
        method: 'POST',
        headers: { ...adminHeaders, 'content-type': 'application/json' },
        body: JSON.stringify({ rawMarkdown: buildStarterAnswerMarkdown() })
      });
      const testLink = `${window.location.origin}${overview?.studentLink ?? '/take/test-paper'}`;
      setTestCatalog((current) => [
        { id: window.crypto.randomUUID(), subject, testLink, createdAt: new Date().toISOString() },
        ...current
      ]);
      setTestSubject('');
      setStatus('Test created and activated.');
      await refreshAdminData();
    } catch (error) {
      setStatus(error.message);
    } finally {
      setLoading(false);
    }
  }

  async function scoreSubmissions() {
    setLoading(true);
    setStatus('Scoring submissions...');
    try {
      const data = await apiRequest('/api/score', {
        method: 'POST',
        headers: adminHeaders
      });
      setResults((current) => ({ ...current, submissions: data.submissions }));
      setStatus('Submissions scored.');
    } catch (error) {
      setStatus(error.message);
    } finally {
      setLoading(false);
    }
  }

  async function clearSubmissions() {
    setLoading(true);
    setStatus('Clearing submissions...');
    try {
      await apiRequest('/api/submissions', {
        method: 'DELETE',
        headers: adminHeaders
      });
      await refreshAdminData();
      setStatus('Submissions cleared.');
    } catch (error) {
      setStatus(error.message);
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="admin-shell">
      <header className="topbar">
        <div>
          <p className="eyebrow">Test Admin</p>
          <h1>Test Workspace</h1>
        </div>
        <button type="button" className="icon-button" onClick={refreshAdminData} disabled={loading} title="Refresh">
          <RefreshCcw size={18} />
        </button>
      </header>

      <nav className="section-tabs" aria-label="Admin sections">
        <button
          type="button"
          className={section === 'creation' ? 'active' : ''}
          onClick={() => setSection('creation')}
        >
          <FilePlus2 size={18} />
          Test Creation
        </button>
        <button
          type="button"
          className={section === 'results' ? 'active' : ''}
          onClick={() => setSection('results')}
        >
          <ClipboardList size={18} />
          Review Results
        </button>
      </nav>

      <section className="status-strip" aria-live="polite">
        <p className="status">{status}</p>
      </section>

      {section === 'creation' ? (
        <TestCreationSection
          createTest={createTest}
          loading={loading}
          overview={overview}
          setTestSubject={setTestSubject}
          testCatalog={testCatalog}
          testSubject={testSubject}
        />
      ) : (
        <ResultsSection
          clearSubmissions={clearSubmissions}
          loading={loading}
          results={results}
          scoreSubmissions={scoreSubmissions}
        />
      )}
    </main>
  );
}

function TestCreationSection({
  createTest,
  loading,
  overview,
  setTestSubject,
  testCatalog,
  testSubject
}) {
  const activeTest = overview?.activeTest?.parsed;
  const studentUrl = overview?.studentLink
    ? `${window.location.origin}${overview.studentLink}`
    : 'No student link yet';

  return (
    <section className="admin-grid">
      <div className="workspace-panel creation-panel">
        <div className="panel-heading">
          <div>
            <p className="eyebrow">Section 1</p>
            <h2>Test Creation</h2>
          </div>
        </div>

        <div className="subject-row">
          <label className="field subject-field">
            <span>Test Subject</span>
            <input
              type="text"
              value={testSubject}
              onChange={(event) => setTestSubject(event.target.value)}
              placeholder="a math test with njsla in mind for 3rd grader"
            />
          </label>
          <button type="button" onClick={createTest} disabled={loading}>
            <FilePlus2 size={18} />
            Create Test
          </button>
        </div>

        <div className="example-row">
          <span>Example</span>
          <code>An algebra test for a 7th grader focused on quadratics</code>
        </div>

        <div className="summary-grid">
          <Metric label="Active test" value={activeTest?.title ?? 'None'} />
          <Metric label="Questions" value={activeTest?.questions?.length ?? 0} />
          <Metric label="Submissions" value={overview?.submissionCount ?? 0} />
        </div>
      </div>

      <CreatedTestsGrid tests={testCatalog} fallbackLink={studentUrl} />
    </section>
  );
}

function CreatedTestsGrid({ tests, fallbackLink }) {
  const rows = tests.length ? tests : [{ id: 'active', subject: 'Current active test', testLink: fallbackLink }];

  return (
    <div className="workspace-panel tests-panel">
      <div className="panel-heading">
        <div>
          <p className="eyebrow">Created Tests</p>
          <h2>Test Links</h2>
        </div>
      </div>
      <div className="test-link-grid" role="table" aria-label="Created test links">
        <div className="test-link-header" role="row">
          <span role="columnheader">Test Subject</span>
          <span role="columnheader">Test Link</span>
        </div>
        {rows.map((test) => (
          <div className="test-link-row" role="row" key={test.id}>
            <span role="cell">{test.subject}</span>
            <a role="cell" href={test.testLink}>
              {test.testLink}
            </a>
          </div>
        ))}
      </div>
    </div>
  );
}

function ResultsSection({ clearSubmissions, loading, results, scoreSubmissions }) {
  const submissions = results?.submissions ?? [];
  const activeTest = results?.activeTest?.parsed;
  const answerBankReady = Boolean(results?.answerBank);

  return (
    <section className="admin-grid">
      <div className="workspace-panel results-panel">
        <div className="panel-heading">
          <div>
            <p className="eyebrow">Section 2</p>
            <h2>Review Results</h2>
          </div>
          <div className="button-row">
            <button type="button" onClick={scoreSubmissions} disabled={loading || !submissions.length || !answerBankReady}>
              <ClipboardCheck size={18} />
              Score
            </button>
            <button type="button" className="danger" onClick={clearSubmissions} disabled={loading || !submissions.length}>
              <Trash2 size={18} />
              Clear
            </button>
          </div>
        </div>

        <div className="summary-grid">
          <Metric label="Test" value={activeTest?.title ?? 'None'} />
          <Metric label="Submissions" value={submissions.length} />
          <Metric label="Answer bank" value={answerBankReady ? 'Ready' : 'Missing'} />
        </div>

        {submissions.length ? (
          <div className="submission-list">
            {submissions.map((submission) => (
              <SubmissionCard key={submission.id} submission={submission} />
            ))}
          </div>
        ) : (
          <div className="empty-state">
            <BookOpenCheck size={24} />
            <p>No submissions yet.</p>
          </div>
        )}
      </div>
    </section>
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

function SubmissionCard({ submission }) {
  const score = submission.score;
  const questionScores = score?.byQuestion ? Object.values(score.byQuestion) : [];

  return (
    <article className="submission-card">
      <div className="submission-heading">
        <div>
          <h3>{submission.studentName}</h3>
          <p>{formatDate(submission.submittedAt)}</p>
        </div>
        <strong>{score ? `${score.earned} / ${score.possible}` : 'Unscored'}</strong>
      </div>

      {questionScores.length ? (
        <div className="question-score-list">
          {questionScores.map((item) => (
            <div key={item.questionNumber} className="question-score">
              <span>Q{item.questionNumber}</span>
              <strong>{item.status}</strong>
              <span>{item.earned === null ? 'Review' : `${item.earned} / ${item.points}`}</span>
            </div>
          ))}
        </div>
      ) : (
        <div className="answer-preview">
          {Object.entries(submission.answers ?? {}).map(([key, value]) => (
            <p key={key}>
              <strong>Q{key}</strong>
              <span>{String(value)}</span>
            </p>
          ))}
        </div>
      )}
    </article>
  );
}

function formatDate(value) {
  if (!value) return '';
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short'
  }).format(new Date(value));
}

function readTestCatalog() {
  try {
    const stored = window.localStorage.getItem(testCatalogStorageKey);
    return stored ? JSON.parse(stored) : [];
  } catch {
    return [];
  }
}

function buildStarterTestMarkdown(subject) {
  return `# ${subject}

Instructions for the student.

## Question 1

Type: multiple_choice
Points: 1

Starter question for: ${subject}

A. First option
B. Second option
`;
}

function buildStarterAnswerMarkdown() {
  return `# Answer Bank

## Question 1

Answer: A
Points: 1

Explanation: Starter answer key.
`;
}

function StudentApp({ token }) {
  return (
    <main className="app-shell">
      <section className="toolbar">
        <div>
          <p className="eyebrow">Student</p>
          <h1>Take Test</h1>
        </div>
      </section>

      <section className="panel">
        <h2>Student Link Ready</h2>
        <p>
          This route preserves the tokenized student URL while the test-taking
          experience is rebuilt in React.
        </p>
        <dl>
          <div>
            <dt>Test token</dt>
            <dd>{token}</dd>
          </div>
        </dl>
        <button type="button" disabled>
          <Eraser size={18} />
          Student module pending
        </button>
      </section>
    </main>
  );
}
