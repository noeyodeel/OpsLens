import './App.css'

const incidents = [
  {
    id: 'INC-20260913-001',
    severity: 'CRITICAL',
    title: 'Order ingestion volume dropped 72%',
    target: 'orders',
    status: 'Detected',
  },
  {
    id: 'INC-20260913-002',
    severity: 'WARNING',
    title: 'customer_phone NULL ratio increased',
    target: 'customers',
    status: 'Pending analysis',
  },
  {
    id: 'INC-20260913-003',
    severity: 'WARNING',
    title: 'Duplicate payment_id values found',
    target: 'payments',
    status: 'Pending review',
  },
]

function App() {
  return (
    <main className="app-shell">
      <section className="top-bar">
        <div>
          <p className="eyebrow">OpsLens MVP</p>
          <h1>Operational data incident analysis</h1>
        </div>
        <span className="status-pill">Backend: /api/health</span>
      </section>

      <section className="summary-grid" aria-label="Incident summary">
        <article>
          <span>Open incidents</span>
          <strong>3</strong>
        </article>
        <article>
          <span>Critical</span>
          <strong>1</strong>
        </article>
        <article>
          <span>Scenario coverage</span>
          <strong>3</strong>
        </article>
      </section>

      <section className="incident-panel">
        <div className="panel-heading">
          <h2>Current incidents</h2>
          <button type="button">Run detection</button>
        </div>

        <div className="incident-list">
          {incidents.map((incident) => (
            <article className="incident-row" key={incident.id}>
              <span className={`severity ${incident.severity.toLowerCase()}`}>
                {incident.severity}
              </span>
              <div>
                <strong>{incident.title}</strong>
                <p>
                  {incident.id} / {incident.target}
                </p>
              </div>
              <span className="row-status">{incident.status}</span>
            </article>
          ))}
        </div>
      </section>
    </main>
  )
}

export default App
