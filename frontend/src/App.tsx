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

type FilterStatus = 'ALL' | IncidentStatus

const statusOptions: FilterStatus[] = ['ALL', 'DETECTED', 'ANALYZING', 'ANALYZED', 'RESOLVED', 'DISMISSED']

function App() {
  const [incidents, setIncidents] = useState<IncidentSummary[]>([])
  const [selectedStatus, setSelectedStatus] = useState<FilterStatus>('ALL')
  const [loading, setLoading] = useState(true)
  const [detecting, setDetecting] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

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
    } catch {
      setErrorMessage('Incident 목록을 불러오지 못했습니다. 백엔드 서버가 실행 중인지 확인해주세요.')
    } finally {
      setLoading(false)
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
      setErrorMessage('탐지를 실행하지 못했습니다. 테스트 데이터 생성과 백엔드 상태를 확인해주세요.')
    } finally {
      setDetecting(false)
    }
  }

  useEffect(() => {
    void loadIncidents()
  }, [selectedStatus])

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
            외부 기관 진료 데이터 수신 이상을 탐지하고 현재 Incident 상태를 확인합니다.
          </p>
        </div>
        <div className="top-actions">
          <button type="button" className="secondary-button" onClick={loadIncidents} disabled={loading || detecting}>
            새로고침
          </button>
          <button type="button" onClick={runDetection} disabled={loading || detecting}>
            {detecting ? '탐지 중' : '탐지 실행'}
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

      <section className="incident-panel">
        <div className="panel-heading">
          <div>
            <h2>Incident 목록</h2>
            <p>{selectedStatus === 'ALL' ? '전체 상태' : selectedStatus} 기준으로 최신 탐지순 정렬</p>
          </div>
          <label className="filter-control">
            <span>상태</span>
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
        {loading ? <div className="message">Incident 목록을 불러오는 중입니다.</div> : null}
        {!loading && !errorMessage && incidents.length === 0 ? (
          <div className="message">표시할 Incident가 없습니다. 테스트 데이터를 생성한 뒤 탐지를 실행해보세요.</div>
        ) : null}

        {!loading && incidents.length > 0 ? (
          <div className="incident-list">
            {incidents.map((incident) => (
              <article className="incident-row" key={incident.incidentNo}>
                <span className={`severity ${incident.severity.toLowerCase()}`}>{incident.severity}</span>
                <div className="incident-main">
                  <strong>{incident.summary}</strong>
                  <p>
                    {incident.incidentNo} / {incident.targetTable}
                    {incident.targetInstitutionCode ? ` / ${incident.targetInstitutionCode}` : ''}
                  </p>
                </div>
                <div className="incident-meta">
                  <span className="row-status">{incident.status}</span>
                  <time dateTime={incident.detectedAt}>{formatDateTime(incident.detectedAt)}</time>
                </div>
              </article>
            ))}
          </div>
        ) : null}
      </section>
    </main>
  )
}

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat('ko-KR', {
    dateStyle: 'short',
    timeStyle: 'short',
  }).format(new Date(value))
}

export default App
