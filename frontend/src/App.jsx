import { RefreshCcw } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import { apiRequest } from './api.js';

function getRoute() {
  const path = window.location.pathname.replace(/\/+$/, '');
  if (path === '' || path === '/') {
    return { route: 'admin', id: '' };
  }
  if (path === '/admin' || path.startsWith('/results')) {
    return { route: 'admin', id: '' };
  }
  if (path.startsWith('/take/')) {
    return { route: 'student', id: decodeURIComponent(path.slice(6)) };
  }
  return { route: 'student', id: decodeURIComponent(path.slice(1)) };
}

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
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    refreshTests();
  }, []);

  async function refreshTests() {
    setStatus('Loading test subjects...');
    try {
      const data = await apiRequest('/api/tests');
      setTests(data.tests || []);
      setStatus('Test subjects loaded.');
    } catch (error) {
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
    setStatus('Creating test subject...');
    try {
      await apiRequest('/api/test/generate', {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({ subject: subject.trim() })
      });
      setSubject('');
      await refreshTests();
      setStatus('New test subject created.');
    } catch (error) {
      setStatus(error.message);
    } finally {
      setLoading(false);
    }
  }

  async function deleteTest(id) {
    setLoading(true);
    setStatus('Deleting test subject...');
    try {
      await apiRequest(`/api/tests/${encodeURIComponent(id)}`, {
        method: 'DELETE'
      });
      await refreshTests();
      setStatus('Test subject deleted.');
    } catch (error) {
      setStatus(error.message);
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="app-shell">
      <section className="toolbar">
        <div>
          <p className="eyebrow">Teacher</p>
          <h1>Test Subjects</h1>
        </div>
        <button type="button" onClick={checkApi} aria-label="Refresh API status">
          <RefreshCcw size={18} />
          Check API
        </button>
      </section>

      <section className="panel">
        <h2>Create a Unique Test Link</h2>
        <p>Enter a subject name and generate a new unique test link for that subject.</p>
        <label>
          Subject
          <input
            type="text"
            value={subject}
            onChange={(event) => setSubject(event.target.value)}
            placeholder="e.g. Biology - Cells"
          />
        </label>
        <button type="button" onClick={createTest} disabled={loading}>
          {loading ? 'Working...' : 'Create Test'}
        </button>
        <div className="status-row">
          <span>API status: {health ?? 'Not checked'}</span>
          <span>{status}</span>
        </div>
      </section>

      <section className="panel">
        <h2>Active Test Subjects</h2>
        {tests.length === 0 ? (
          <p>No generated test subjects yet.</p>
        ) : (
          <table>
            <thead>
              <tr>
                <th>Subject</th>
                <th>Link</th>
                <th>Created</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {tests.map((test) => (
                <tr key={test.id}>
                  <td>{test.subject}</td>
                  <td>
                    <a href={new URL(test.link, window.location.origin).toString()} target="_blank" rel="noreferrer">
                      {test.link}
                    </a>
                  </td>
                  <td>{new Date(test.createdAt).toLocaleString()}</td>
                  <td>
                    <button type="button" onClick={() => deleteTest(test.id)} disabled={loading}>
                      Delete
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </main>
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
