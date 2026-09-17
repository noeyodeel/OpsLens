import { useEffect, useMemo, useState } from 'react'
import './App.css'

type IncidentSeverity = 'INFO' | 'WARNING' | 'CRITICAL'
type IncidentStatus = 'DETECTED' | 'ANALYZING' | 'ANALYZED' | 'RESOLVED' | 'DISMISSED'

type IncidentSummary = {
  id: number
  incidentNo: string
  severity: IncidentSeverity
  status: IncidentStatus
  targetTable: string
  targetInstitutionCode: string | null
  anomalyType: string
  summary: string
  detectedAt: string
  resolvedAt: string | null
  detectionRuleName: string | null
}

type MetricSnapshot = {
  id: number
  metricName: string
  baselineValue: number
  currentValue: number
  changeRate: number | null
  measuredAt: string
}

type IncidentDetail = IncidentSummary & {
  metricSnapshots: MetricSnapshot[]
}

type FilterStatus = 'ALL' | IncidentStatus

const statusOptions: FilterStatus[] = ['ALL', 'DETECTED', 'ANALYZING', 'ANALYZED', 'RESOLVED', 'DISMISSED']

function App() {
  const [incidents, setIncidents] = useState<IncidentSummary[]>([])
  const [selectedStatus, setSelectedStatus] = useState<FilterStatus>('ALL')
  const [selectedIncidentNo, setSelectedIncidentNo] = useState<string | null>(null)
  const [incidentDetail, setIncidentDetail] = useState<IncidentDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [detailLoading, setDetailLoading] = useState(false)
  const [detecting, setDetecting] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [detailErrorMessage, setDetailErrorMessage] = useState<string | null>(null)

  const queryString = selectedStatus === 'ALL' ? '' : `?status=${selectedStatus}`

  const loadIncidents = async () => {
    setLoading(true)
    setErrorMessage(null)

    try {
      const response = await fetch(`/api/incidents${queryString}`)
      if (!response.ok) {
        throw new Error(`Incident list request failed with ${response.status}`)
      }
      const data = (await response.json()) as IncidentSummary[]
      setIncidents(data)
      setSelectedIncidentNo((current) => {
        if (current && data.some((incident) => incident.incidentNo === current)) {
          return current
        }
        return data[0]?.incidentNo ?? null
      })
    } catch {
      setErrorMessage('Could not load incidents. Check that the backend server is running.')
    } finally {
      setLoading(false)
    }
  }

  const loadIncidentDetail = async (incidentNo: string) => {
    setDetailLoading(true)
    setDetailErrorMessage(null)

    try {
      const response = await fetch(`/api/incidents/${incidentNo}`)
      if (!response.ok) {
        throw new Error(`Incident detail request failed with ${response.status}`)
      }
      const data = (await response.json()) as IncidentDetail
      setIncidentDetail(data)
    } catch {
      setIncidentDetail(null)
      setDetailErrorMessage('Could not load incident detail.')
    } finally {
      setDetailLoading(false)
    }
  }

  const runDetection = async () => {
    setDetecting(true)
    setErrorMessage(null)

    try {
      const response = await fetch('/api/incidents/detect', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ targetDate: '2026-09-13' }),
      })
      if (!response.ok) {
        throw new Error(`Detection request failed with ${response.status}`)
      }
      await loadIncidents()
    } catch {
      setErrorMessage('Could not run detection. Generate test data and check the backend status.')
    } finally {
      setDetecting(false)
    }
  }

  useEffect(() => {
    void loadIncidents()
  }, [selectedStatus])

  useEffect(() => {
    if (selectedIncidentNo) {
      void loadIncidentDetail(selectedIncidentNo)
    } else {
      setIncidentDetail(null)
    }
  }, [selectedIncidentNo])

  const summary = useMemo(() => {
    const open = incidents.filter((incident) => incident.status !== 'RESOLVED' && incident.status !== 'DISMISSED').length
    const critical = incidents.filter((incident) => incident.severity === 'CRITICAL').length
    const institutions = new Set(incidents.map((incident) => incident.targetInstitutionCode).filter(Boolean)).size

    return { open, critical, institutions }
  }, [incidents])

  return (
    <main className="app-shell">
      <section className="top-bar">
        <div>
          <p className="eyebrow">OpsLens MVP</p>
          <h1>Institution Data Incident Dashboard</h1>
          <p className="page-description">
            Monitor external institution intake anomalies and inspect incident evidence.
          </p>
        </div>
        <div className="top-actions">
          <button type="button" className="secondary-button" onClick={loadIncidents} disabled={loading || detecting}>
            Refresh
          </button>
          <button type="button" onClick={runDetection} disabled={loading || detecting}>
            {detecting ? 'Detecting' : 'Run Detection'}
          </button>
        </div>
      </section>

      <section className="summary-grid" aria-label="Incident summary">
        <article>
          <span>Open incidents</span>
          <strong>{summary.open}</strong>
        </article>
        <article>
          <span>Critical</span>
          <strong>{summary.critical}</strong>
        </article>
        <article>
          <span>Institutions</span>
          <strong>{summary.institutions}</strong>
        </article>
      </section>

      <section className="dashboard-grid">
        <section className="incident-panel">
          <div className="panel-heading">
            <div>
              <h2>Incidents</h2>
              <p>{selectedStatus === 'ALL' ? 'All statuses' : selectedStatus} sorted by latest detection</p>
            </div>
            <label className="filter-control">
              <span>Status</span>
              <select value={selectedStatus} onChange={(event) => setSelectedStatus(event.target.value as FilterStatus)}>
                {statusOptions.map((status) => (
                  <option key={status} value={status}>
                    {status}
                  </option>
                ))}
              </select>
            </label>
          </div>

          {errorMessage ? <div className="message error">{errorMessage}</div> : null}
          {loading ? <div className="message">Loading incidents.</div> : null}
          {!loading && !errorMessage && incidents.length === 0 ? (
            <div className="message">No incidents to display. Generate test data, then run detection.</div>
          ) : null}

          {!loading && incidents.length > 0 ? (
            <div className="incident-list">
              {incidents.map((incident) => (
                <button
                  className={`incident-row ${incident.incidentNo === selectedIncidentNo ? 'selected' : ''}`}
                  key={incident.incidentNo}
                  onClick={() => setSelectedIncidentNo(incident.incidentNo)}
                  type="button"
                >
                  <span className={`severity ${incident.severity.toLowerCase()}`}>{incident.severity}</span>
                  <span className="incident-main">
                    <strong>{incident.summary}</strong>
                    <span>
                      {incident.incidentNo} / {incident.targetTable}
                      {incident.targetInstitutionCode ? ` / ${incident.targetInstitutionCode}` : ''}
                    </span>
                  </span>
                  <span className="incident-meta">
                    <span className="row-status">{incident.status}</span>
                    <time dateTime={incident.detectedAt}>{formatDateTime(incident.detectedAt)}</time>
                  </span>
                </button>
              ))}
            </div>
          ) : null}
        </section>

        <section className="detail-panel">
          <div className="panel-heading">
            <div>
              <h2>Incident Detail</h2>
              <p>Evidence and measured values for the selected incident</p>
            </div>
          </div>

          {detailErrorMessage ? <div className="message error">{detailErrorMessage}</div> : null}
          {detailLoading ? <div className="message">Loading detail.</div> : null}
          {!detailLoading && !incidentDetail && !detailErrorMessage ? (
            <div className="message">Select an incident to view detail.</div>
          ) : null}

          {!detailLoading && incidentDetail ? <IncidentDetailView incident={incidentDetail} /> : null}
        </section>
      </section>
    </main>
  )
}

function IncidentDetailView({ incident }: { incident: IncidentDetail }) {
  return (
    <div className="detail-content">
      <div className="detail-title-row">
        <span className={`severity ${incident.severity.toLowerCase()}`}>{incident.severity}</span>
        <div>
          <strong>{incident.incidentNo}</strong>
          <p>{incident.summary}</p>
        </div>
      </div>

      <dl className="detail-grid">
        <div>
          <dt>Status</dt>
          <dd>{incident.status}</dd>
        </div>
        <div>
          <dt>Anomaly</dt>
          <dd>{incident.anomalyType}</dd>
        </div>
        <div>
          <dt>Institution</dt>
          <dd>{incident.targetInstitutionCode ?? '-'}</dd>
        </div>
        <div>
          <dt>Target table</dt>
          <dd>{incident.targetTable}</dd>
        </div>
        <div>
          <dt>Rule</dt>
          <dd>{incident.detectionRuleName ?? '-'}</dd>
        </div>
        <div>
          <dt>Detected</dt>
          <dd>{formatDateTime(incident.detectedAt)}</dd>
        </div>
      </dl>

      <div className="metric-section">
        <h3>Metric snapshots</h3>
        {incident.metricSnapshots.length === 0 ? (
          <div className="message">No metric snapshots recorded.</div>
        ) : (
          <div className="metric-list">
            {incident.metricSnapshots.map((metric) => (
              <article className="metric-row" key={metric.id}>
                <div>
                  <strong>{metric.metricName}</strong>
                  <p>{formatDateTime(metric.measuredAt)}</p>
                </div>
                <dl>
                  <div>
                    <dt>Baseline</dt>
                    <dd>{formatNumber(metric.baselineValue)}</dd>
                  </div>
                  <div>
                    <dt>Current</dt>
                    <dd>{formatNumber(metric.currentValue)}</dd>
                  </div>
                  <div>
                    <dt>Change</dt>
                    <dd>{metric.changeRate === null ? '-' : `${formatNumber(metric.changeRate)}%`}</dd>
                  </div>
                </dl>
              </article>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat('ko-KR', {
    dateStyle: 'short',
    timeStyle: 'short',
  }).format(new Date(value))
}

function formatNumber(value: number) {
  return new Intl.NumberFormat('ko-KR', {
    maximumFractionDigits: 4,
  }).format(value)
}

export default App
