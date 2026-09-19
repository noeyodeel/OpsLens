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
const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '')

function apiUrl(path: string) {
  return `${apiBaseUrl}${path}`
}

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
      const response = await fetch(apiUrl(`/api/incidents${queryString}`))
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
      setErrorMessage('인시던트 목록을 불러오지 못했습니다. 백엔드 서버 실행 상태를 확인해주세요.')
    } finally {
      setLoading(false)
    }
  }

  const loadIncidentDetail = async (incidentNo: string) => {
    setDetailLoading(true)
    setDetailErrorMessage(null)

    try {
      const response = await fetch(apiUrl(`/api/incidents/${incidentNo}`))
      if (!response.ok) {
        throw new Error(`Incident detail request failed with ${response.status}`)
      }
      const data = (await response.json()) as IncidentDetail
      setIncidentDetail(data)
    } catch {
      setIncidentDetail(null)
      setDetailErrorMessage('인시던트 상세 정보를 불러오지 못했습니다.')
    } finally {
      setDetailLoading(false)
    }
  }

  const loadAnalysis = async (incidentNo: string) => {
    setAnalysisErrorMessage(null)

    try {
      const response = await fetch(apiUrl(`/api/incidents/${incidentNo}/analysis`))
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
      setAnalysisErrorMessage('분석 결과를 불러오지 못했습니다.')
    }
  }

  const loadReport = async (incidentNo: string, reportType: ReportType) => {
    try {
      const response = await fetch(apiUrl(`/api/incidents/${incidentNo}/reports?type=${reportType}`))
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
      setAnalysisErrorMessage(`${formatReportType(reportType)} 리포트를 불러오지 못했습니다.`)
    }
  }

  const runAnalysis = async () => {
    if (!selectedIncidentNo) {
      return
    }
    setAnalyzing(true)
    setAnalysisErrorMessage(null)

    try {
      const response = await fetch(apiUrl(`/api/incidents/${selectedIncidentNo}/analyze`), {
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
      setAnalysisErrorMessage('AI 분석을 실행하지 못했습니다. 인시던트 선택 여부와 백엔드 서버 상태를 확인해주세요.')
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
      const response = await fetch(apiUrl(`/api/incidents/${selectedIncidentNo}/reports?type=${reportType}`), {
        method: 'POST',
      })
      if (!response.ok) {
        throw new Error(`Report generation failed with ${response.status}`)
      }
      const data = (await response.json()) as IncidentReport
      setReports((current) => ({ ...current, [reportType]: data }))
      setSelectedReportType(reportType)
    } catch {
      setAnalysisErrorMessage('리포트를 생성하지 못했습니다. 먼저 AI 분석을 실행해주세요.')
    } finally {
      setGeneratingReport(null)
    }
  }

  const runDetection = async () => {
    setDetecting(true)
    setErrorMessage(null)

    try {
      const response = await fetch(apiUrl('/api/incidents/detect'), {
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
      setErrorMessage('이상 탐지를 실행하지 못했습니다. 테스트 데이터 생성 여부와 백엔드 상태를 확인해주세요.')
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
          <h1>기관 데이터 전송 이상 감지 대시보드</h1>
          <p className="page-description">
            외부 기관에서 수신된 진료 전송 데이터의 이상 징후와 분석 근거를 확인합니다.
          </p>
        </div>
        <div className="top-actions">
          <button type="button" className="secondary-button" onClick={loadIncidents} disabled={loading || detecting}>
            새로고침
          </button>
          <button type="button" onClick={runDetection} disabled={loading || detecting}>
            {detecting ? '탐지 중' : '이상 탐지 실행'}
          </button>
        </div>
      </section>

      <section className="summary-grid" aria-label="인시던트 요약">
        <article>
          <span>미해결 인시던트</span>
          <strong>{summary.open}</strong>
        </article>
        <article>
          <span>심각</span>
          <strong>{summary.critical}</strong>
        </article>
        <article>
          <span>영향 기관</span>
          <strong>{summary.institutions}</strong>
        </article>
      </section>

      <section className="dashboard-grid">
        <section className="incident-panel">
          <div className="panel-heading">
            <div>
              <h2>인시던트 목록</h2>
              <p>{selectedStatus === 'ALL' ? '전체 상태' : formatStatus(selectedStatus)} / 최근 탐지순</p>
            </div>
            <label className="filter-control">
              <span>상태</span>
              <select value={selectedStatus} onChange={(event) => setSelectedStatus(event.target.value as FilterStatus)}>
                {statusOptions.map((status) => (
                  <option key={status} value={status}>
                    {status === 'ALL' ? '전체' : formatStatus(status)}
                  </option>
                ))}
              </select>
            </label>
          </div>

          {errorMessage ? <div className="message error">{errorMessage}</div> : null}
          {loading ? <div className="message">인시던트 목록을 불러오는 중입니다.</div> : null}
          {!loading && !errorMessage && incidents.length === 0 ? (
            <div className="message">표시할 인시던트가 없습니다. 테스트 데이터를 생성한 뒤 이상 탐지를 실행해주세요.</div>
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
                  <span className={`severity ${incident.severity.toLowerCase()}`}>{formatSeverity(incident.severity)}</span>
                  <span className="incident-main">
                    <strong>{incident.summary}</strong>
                    <span>
                      {incident.incidentNo} / {incident.targetTable}
                      {incident.targetInstitutionCode ? ` / ${incident.targetInstitutionCode}` : ''}
                    </span>
                  </span>
                  <span className="incident-meta">
                    <span className="row-status">{formatStatus(incident.status)}</span>
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
              <h2>인시던트 상세</h2>
              <p>선택한 인시던트의 근거 데이터와 측정값을 확인합니다.</p>
            </div>
          </div>

          {detailErrorMessage ? <div className="message error">{detailErrorMessage}</div> : null}
          {detailLoading ? <div className="message">상세 정보를 불러오는 중입니다.</div> : null}
          {!detailLoading && !incidentDetail && !detailErrorMessage ? (
            <div className="message">상세 정보를 볼 인시던트를 선택해주세요.</div>
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
        <span className={`severity ${incident.severity.toLowerCase()}`}>{formatSeverity(incident.severity)}</span>
        <div>
          <strong>{incident.incidentNo}</strong>
          <p>{incident.summary}</p>
        </div>
      </div>

      <dl className="detail-grid">
        <div>
          <dt>상태</dt>
          <dd>{formatStatus(incident.status)}</dd>
        </div>
        <div>
          <dt>이상 유형</dt>
          <dd>{incident.anomalyType}</dd>
        </div>
        <div>
          <dt>기관</dt>
          <dd>{incident.targetInstitutionCode ?? '-'}</dd>
        </div>
        <div>
          <dt>대상 테이블</dt>
          <dd>{incident.targetTable}</dd>
        </div>
        <div>
          <dt>탐지 규칙</dt>
          <dd>{incident.detectionRuleName ?? '-'}</dd>
        </div>
        <div>
          <dt>탐지 시각</dt>
          <dd>{formatDateTime(incident.detectedAt)}</dd>
        </div>
      </dl>

      <div className="metric-section">
        <h3>측정 지표</h3>
        {incident.metricSnapshots.length === 0 ? (
          <div className="message">저장된 측정 지표가 없습니다.</div>
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
                    <dt>정상 기준</dt>
                    <dd>{formatNumber(metric.baselineValue)}</dd>
                  </div>
                  <div>
                    <dt>현재 값</dt>
                    <dd>{formatNumber(metric.currentValue)}</dd>
                  </div>
                  <div>
                    <dt>변화율</dt>
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
            <h3>AI 분석</h3>
            <p>제한된 인시던트 컨텍스트를 기반으로 생성한 분석 결과입니다.</p>
          </div>
          <button type="button" onClick={onRunAnalysis} disabled={analyzing}>
            {analyzing ? '분석 중' : 'AI 분석 실행'}
          </button>
        </div>

        {analysisErrorMessage ? <div className="message error">{analysisErrorMessage}</div> : null}
        {!analysis ? <div className="message">저장된 분석 결과가 없습니다. AI 분석을 실행해 원인 후보와 검증 SQL을 생성해주세요.</div> : null}
        {analysis ? <AnalysisView analysis={analysis} /> : null}
      </div>

      <div className="report-section">
        <div className="section-heading">
          <div>
            <h3>리포트</h3>
            <p>저장된 분석 결과를 개발자용과 업무 담당자용으로 변환합니다.</p>
          </div>
        </div>

        <div className="report-controls">
          <div className="report-tabs" role="tablist" aria-label="리포트 유형">
            {(['DEVELOPER', 'BUSINESS'] as ReportType[]).map((reportType) => (
              <button
                className={selectedReportType === reportType ? 'selected' : ''}
                key={reportType}
                onClick={() => setSelectedReportType(reportType)}
                type="button"
              >
                {formatReportType(reportType)}
              </button>
            ))}
          </div>
          <div className="report-actions">
            <button
              className="secondary-button"
              disabled={generatingReport !== null || !analysis}
              onClick={() => onGenerateReport(selectedReportType)}
              type="button"
            >
              {generatingReport === selectedReportType ? '생성 중' : '선택 리포트 생성'}
            </button>
          </div>
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
          <div className="message">저장된 {formatReportType(selectedReportType)} 리포트가 없습니다.</div>
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
          <dt>분석 시각</dt>
          <dd>{formatDateTime(analysis.analyzedAt)}</dd>
        </div>
        <div>
          <dt>분석 방식</dt>
          <dd>{analysis.mock ? '모의 분석' : 'LLM 분석'}</dd>
        </div>
        <div>
          <dt>요약</dt>
          <dd>{analysis.summary}</dd>
        </div>
        <div>
          <dt>영향 범위</dt>
          <dd>{analysis.impactScope}</dd>
        </div>
      </dl>

      <div className="analysis-list">
        <h4>원인 후보</h4>
        {analysis.suspectedCauses.map((cause) => (
          <article key={`${cause.rank}-${cause.cause}`}>
            <strong>
              #{cause.rank} {cause.cause}
            </strong>
            <p>{cause.reason}</p>
            <span>신뢰도 {formatNumber(cause.confidence)}</span>
          </article>
        ))}
      </div>

      <div className="analysis-list">
        <h4>검증 SQL</h4>
        {analysis.verificationSql.map((sql) => (
          <article key={sql.title}>
            <strong>
              {sql.title} / {sql.safe ? '안전' : '주의 필요'}
            </strong>
            <p>{sql.purpose}</p>
            <span>{sql.safetyMessage}</span>
            <pre>{sql.sql}</pre>
          </article>
        ))}
      </div>

      <div className="analysis-list">
        <h4>추가 확인 사항</h4>
        <ul>
          {analysis.additionalChecks.map((check) => (
            <li key={check}>{check}</li>
          ))}
        </ul>
      </div>
    </div>
  )
}

function formatSeverity(severity: IncidentSeverity) {
  const labels: Record<IncidentSeverity, string> = {
    INFO: '정보',
    WARNING: '주의',
    CRITICAL: '심각',
  }

  return labels[severity]
}

function formatStatus(status: IncidentStatus) {
  const labels: Record<IncidentStatus, string> = {
    DETECTED: '탐지됨',
    ANALYZING: '분석 중',
    ANALYZED: '분석 완료',
    RESOLVED: '해결됨',
    DISMISSED: '제외됨',
  }

  return labels[status]
}

function formatReportType(reportType: ReportType) {
  const labels: Record<ReportType, string> = {
    DEVELOPER: '개발자용',
    BUSINESS: '업무 담당자용',
  }

  return labels[reportType]
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
