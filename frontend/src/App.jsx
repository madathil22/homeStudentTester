import { RefreshCcw } from 'lucide-react';
import { useMemo, useState } from 'react';
import { apiRequest } from './api.js';

function getRoute() {
  const [, route, token] = window.location.pathname.split('/');
  return { route: route || 'admin', token: decodeURIComponent(token || 'test-admin') };
}

export function App() {
  const { route, token } = useMemo(getRoute, []);

  if (route === 'take') return <StudentApp token={token} />;
  return <AdminApp token={token} isResults={route === 'results'} />;
}

function AdminApp({ token, isResults }) {
  const [status, setStatus] = useState('Spring/React migration shell is ready.');
  const [health, setHealth] = useState(null);

  async function checkApi() {
    setStatus('Checking Spring API...');
    try {
      const data = await apiRequest('/api/health', {
        headers: { 'x-admin-token': token }
      });
      setHealth(data.status);
      setStatus('Spring API responded.');
    } catch (error) {
      setStatus(error.message);
    }
  }

  return (
    <main className="app-shell">
      <section className="toolbar">
        <div>
          <p className="eyebrow">Parent</p>
          <h1>{isResults ? 'Results' : 'Test Admin'}</h1>
        </div>
        <button type="button" onClick={checkApi} aria-label="Refresh API status">
          <RefreshCcw size={18} />
          Check API
        </button>
      </section>

      <section className="panel">
        <h2>Migration Foundation</h2>
        <p>
          This React screen is the new shell for the existing parent workflow.
          The next migration slice will port Markdown activation, results, and
          scoring into Spring Boot.
        </p>
        <dl>
          <div>
            <dt>Admin token</dt>
            <dd>{token}</dd>
          </div>
          <div>
            <dt>Student link</dt>
            <dd>{`${window.location.origin}/take/test-paper`}</dd>
          </div>
          <div>
            <dt>API status</dt>
            <dd>{health ?? 'Not checked'}</dd>
          </div>
        </dl>
        <p className="status">{status}</p>
      </section>
    </main>
  );
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
      </section>
    </main>
  );
}
