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

type SuspectedCause = {
  rank: number
  cause: string
  reason: string
  confidence: number
}

type VerificationSql = {
  title: string
  purpose: string
  sql: string
  safe: boolean
  safetyMessage: string
}

type IncidentAnalysis = {
  id: number
  incidentNo: string
  summary: string
  impactScope: string
  suspectedCauses: SuspectedCause[]
  verificationSql: VerificationSql[]
  additionalChecks: string[]
  mock: boolean
  analyzedAt: string
}

type ReportType = 'DEVELOPER' | 'BUSINESS'

type IncidentReport = {
  id: number
  incidentNo: string
  analysisId: number
  reportType: ReportType
  title: string
  content: string
  generatedAt: string
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
  const [analyzing, setAnalyzing] = useState(false)
  const [generatingReport, setGeneratingReport] = useState<ReportType | null>(null)
  const [analysis, setAnalysis] = useState<IncidentAnalysis | null>(null)
  const [reports, setReports] = useState<Partial<Record<ReportType, IncidentReport>>>({})
  const [selectedReportType, setSelectedReportType] = useState<ReportType>('DEVELOPER')
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [detailErrorMessage, setDetailErrorMessage] = useState<string | null>(null)
  const [analysisErrorMessage, setAnalysisErrorMessage] = useState<string | null>(null)

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

  const loadAnalysis = async (incidentNo: string) => {
    setAnalysisErrorMessage(null)

    try {
      const response = await fetch(`/api/incidents/${incidentNo}/analysis`)
      if (response.status === 404) {
        setAnalysis(null)
        return
      }
      if (!response.ok) {
        throw new Error(`Analysis request failed with ${response.status}`)
      }
      const data = (await response.json()) as IncidentAnalysis
      setAnalysis(data)
    } catch {
      setAnalysis(null)
      setAnalysisErrorMessage('Could not load analysis result.')
    }
  }

  const loadReport = async (incidentNo: string, reportType: ReportType) => {
    try {
      const response = await fetch(`/api/incidents/${incidentNo}/reports?type=${reportType}`)
      if (response.status === 404) {
        setReports((current) => ({ ...current, [reportType]: undefined }))
        return
      }
      if (!response.ok) {
        throw new Error(`Report request failed with ${response.status}`)
      }
      const data = (await response.json()) as IncidentReport
      setReports((current) => ({ ...current, [reportType]: data }))
    } catch {
      setAnalysisErrorMessage(`Could not load ${reportType.toLowerCase()} report.`)
    }
  }

  const runAnalysis = async () => {
    if (!selectedIncidentNo) {
      return
    }
    setAnalyzing(true)
    setAnalysisErrorMessage(null)

    try {
      const response = await fetch(`/api/incidents/${selectedIncidentNo}/analyze`, {
        method: 'POST',
      })
      if (!response.ok) {
        throw new Error(`Analyze request failed with ${response.status}`)
      }
      const data = (await response.json()) as IncidentAnalysis
      setAnalysis(data)
      await loadIncidents()
      await loadIncidentDetail(selectedIncidentNo)
    } catch {
      setAnalysisErrorMessage('Could not run AI analysis. Check that an incident is selected and backend is running.')
    } finally {
      setAnalyzing(false)
    }
  }

  const generateReport = async (reportType: ReportType) => {
    if (!selectedIncidentNo) {
      return
    }
    setGeneratingReport(reportType)
    setAnalysisErrorMessage(null)

    try {
      const response = await fetch(`/api/incidents/${selectedIncidentNo}/reports?type=${reportType}`, {
        method: 'POST',
      })
      if (!response.ok) {
        throw new Error(`Report generation failed with ${response.status}`)
      }
      const data = (await response.json()) as IncidentReport
      setReports((current) => ({ ...current, [reportType]: data }))
      setSelectedReportType(reportType)
    } catch {
      setAnalysisErrorMessage('Could not generate report. Run analysis before generating reports.')
    } finally {
      setGeneratingReport(null)
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
      setAnalysis(null)
      setReports({})
      void loadAnalysis(selectedIncidentNo)
      void loadReport(selectedIncidentNo, 'DEVELOPER')
      void loadReport(selectedIncidentNo, 'BUSINESS')
    } else {
      setIncidentDetail(null)
      setAnalysis(null)
      setReports({})
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

          {!detailLoading && incidentDetail ? (
            <IncidentDetailView
              analysis={analysis}
              analysisErrorMessage={analysisErrorMessage}
              analyzing={analyzing}
              generatingReport={generatingReport}
              incident={incidentDetail}
              onGenerateReport={generateReport}
              onRunAnalysis={runAnalysis}
              reports={reports}
              selectedReportType={selectedReportType}
              setSelectedReportType={setSelectedReportType}
            />
          ) : null}
        </section>
      </section>
    </main>
  )
}

type IncidentDetailViewProps = {
  analysis: IncidentAnalysis | null
  analysisErrorMessage: string | null
  analyzing: boolean
  generatingReport: ReportType | null
  incident: IncidentDetail
  onGenerateReport: (reportType: ReportType) => void
  onRunAnalysis: () => void
  reports: Partial<Record<ReportType, IncidentReport>>
  selectedReportType: ReportType
  setSelectedReportType: (reportType: ReportType) => void
}

function IncidentDetailView({
  analysis,
  analysisErrorMessage,
  analyzing,
  generatingReport,
  incident,
  onGenerateReport,
  onRunAnalysis,
  reports,
  selectedReportType,
  setSelectedReportType,
}: IncidentDetailViewProps) {
  const selectedReport = reports[selectedReportType] ?? null

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

      <div className="analysis-section">
        <div className="section-heading">
          <div>
            <h3>AI analysis</h3>
            <p>Mock analysis result based on bounded incident context</p>
          </div>
          <button type="button" onClick={onRunAnalysis} disabled={analyzing}>
            {analyzing ? 'Analyzing' : 'Run Analysis'}
          </button>
        </div>

        {analysisErrorMessage ? <div className="message error">{analysisErrorMessage}</div> : null}
        {!analysis ? <div className="message">No analysis stored yet. Run analysis to generate candidates and SQL.</div> : null}
        {analysis ? <AnalysisView analysis={analysis} /> : null}
      </div>

      <div className="report-section">
        <div className="section-heading">
          <div>
            <h3>Reports</h3>
            <p>Developer and business versions from the stored analysis</p>
          </div>
          <div className="report-actions">
            <button
              className="secondary-button"
              disabled={generatingReport !== null || !analysis}
              onClick={() => onGenerateReport('DEVELOPER')}
              type="button"
            >
              {generatingReport === 'DEVELOPER' ? 'Generating' : 'Developer'}
            </button>
            <button
              className="secondary-button"
              disabled={generatingReport !== null || !analysis}
              onClick={() => onGenerateReport('BUSINESS')}
              type="button"
            >
              {generatingReport === 'BUSINESS' ? 'Generating' : 'Business'}
            </button>
          </div>
        </div>

        <div className="report-tabs" role="tablist" aria-label="Report type">
          {(['DEVELOPER', 'BUSINESS'] as ReportType[]).map((reportType) => (
            <button
              className={selectedReportType === reportType ? 'selected' : ''}
              key={reportType}
              onClick={() => setSelectedReportType(reportType)}
              type="button"
            >
              {reportType}
            </button>
          ))}
        </div>

        {selectedReport ? (
          <article className="report-view">
            <div>
              <strong>{selectedReport.title}</strong>
              <time dateTime={selectedReport.generatedAt}>{formatDateTime(selectedReport.generatedAt)}</time>
            </div>
            <pre>{selectedReport.content}</pre>
          </article>
        ) : (
          <div className="message">No {selectedReportType.toLowerCase()} report stored yet.</div>
        )}
      </div>
    </div>
  )
}

function AnalysisView({ analysis }: { analysis: IncidentAnalysis }) {
  return (
    <div className="analysis-content">
      <dl className="detail-grid">
        <div>
          <dt>Analyzed</dt>
          <dd>{formatDateTime(analysis.analyzedAt)}</dd>
        </div>
        <div>
          <dt>Mode</dt>
          <dd>{analysis.mock ? 'MOCK' : 'LLM'}</dd>
        </div>
        <div>
          <dt>Summary</dt>
          <dd>{analysis.summary}</dd>
        </div>
        <div>
          <dt>Impact</dt>
          <dd>{analysis.impactScope}</dd>
        </div>
      </dl>

      <div className="analysis-list">
        <h4>Suspected causes</h4>
        {analysis.suspectedCauses.map((cause) => (
          <article key={`${cause.rank}-${cause.cause}`}>
            <strong>
              #{cause.rank} {cause.cause}
            </strong>
            <p>{cause.reason}</p>
            <span>Confidence {formatNumber(cause.confidence)}</span>
          </article>
        ))}
      </div>

      <div className="analysis-list">
        <h4>Verification SQL</h4>
        {analysis.verificationSql.map((sql) => (
          <article key={sql.title}>
            <strong>
              {sql.title} / {sql.safe ? 'SAFE' : 'UNSAFE'}
            </strong>
            <p>{sql.purpose}</p>
            <span>{sql.safetyMessage}</span>
            <pre>{sql.sql}</pre>
          </article>
        ))}
      </div>

      <div className="analysis-list">
        <h4>Additional checks</h4>
        <ul>
          {analysis.additionalChecks.map((check) => (
            <li key={check}>{check}</li>
          ))}
        </ul>
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
