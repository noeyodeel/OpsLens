# OpsLens MVP Design

## 1. System Architecture

OpsLens uses a monolithic MVP architecture.

```text
React Frontend
  -> Spring Boot API
    -> Application Services
      -> PostgreSQL
      -> LLM API
```

### Components

- React provides the dashboard, incident detail page, analysis panel, SQL viewer, and reports.
- Spring Boot handles anomaly detection, incident creation, AI analysis orchestration, report generation, and evaluation.
- PostgreSQL stores synthetic operational data, incidents, analysis results, reports, scenarios, and evaluation results.
- The LLM API receives summarized incident context and returns structured analysis output.
- Docker keeps the local runtime reproducible.
- GitHub Actions runs automated tests.

### Technology Rationale

- Spring Boot is suitable because the project is backend-heavy and matches the target Java/Spring experience.
- PostgreSQL is practical for relational operational data and supports JSONB for flexible AI analysis results.
- React is enough for a focused dashboard-style MVP.
- A monolith is preferred over MSA because the MVP is built by one developer and the domain boundaries are still evolving.
- Redis, Kafka, and other infrastructure are intentionally excluded until there is a clear design need.

## 2. Core Domain Model

Core domains:

- ExternalInstitution
- TreatmentRecord
- RecordSubject
- VerificationRecord
- DataIngestionLog
- DetectionRule
- Incident
- IncidentMetricSnapshot
- IncidentAnalysis
- VerificationSql
- IncidentReport
- Scenario
- EvaluationResult

Relationship overview:

```text
ExternalInstitution 1:N DataIngestionLog
DetectionRule 1:N Incident
Incident 1:N IncidentMetricSnapshot
Incident 1:1 IncidentAnalysis
IncidentAnalysis 1:N VerificationSql
Incident 1:N IncidentReport
Scenario 1:N Incident
Incident 1:1 EvaluationResult
```

## 3. PostgreSQL Table Design

Initial tables:

- `external_institution`
- `treatment_records`
- `record_subjects`
- `verification_records`
- `data_ingestion_log`
- `detection_rule`
- `incident`
- `incident_metric_snapshot`
- `incident_analysis`
- `verification_sql`
- `incident_report`
- `scenario`
- `evaluation_result`

AI outputs such as suspected causes and additional checks can be stored as JSONB because the structure may evolve during MVP experimentation.

Operational data tables are implemented first because anomaly detection needs stable relational targets before incident logic is added.

Implemented operational tables:

- `external_institution`: synthetic external institutions that send operational records.
- `record_subjects`: synthetic record subjects with nullable required fields for NULL spike scenarios.
- `treatment_records`: synthetic treatment-like transmission records used for volume drop, count spike, and institution missing scenarios.
- `verification_records`: synthetic verification records used for duplicate business key and record mismatch scenarios.
- `data_ingestion_log`: batch/institution ingestion status used to explain missing or partial data.

Implemented incident tables:

- `detection_rule`: rule definitions used by anomaly detectors.
- `incident`: detected anomaly events with severity, status, target table, target institution, and anomaly type.
- `incident_metric_snapshot`: baseline/current metric values captured when an incident is created.

## 4. Anomaly Detection

MVP detection is rule-based.

Initial rules:

- Count drop: current daily count is below 50% of the 7-day average.
- Count spike: current daily count is above 200% of the 7-day average.
- NULL spike: target column NULL ratio doubles compared with baseline.
- Duplicate detection: duplicate count for a unique business key is greater than zero.
- Source missing: institution count is zero or drops by more than 70%.
- Processing failure spike: failed count ratio exceeds 10%.

Rule-based detection is explainable, reproducible, and easier to validate than a statistical or ML-based approach for the first MVP.

Implemented detector:

- Institution-level treatment record volume drop detection compares each active external institution's target-day record count with the previous 7-day average.
- A `COUNT_DROP` incident is created when the current count is below 50% of the baseline average.
- The detector records baseline count, current count, and change rate as an `incident_metric_snapshot`.
- Duplicate incident creation is prevented for the same target table, anomaly type, external institution, and detection window.
- Institution-level required field NULL spike detection checks the current NULL ratio of `record_subjects.required_field_value`.
- A `NULL_SPIKE` incident is created when the current NULL ratio is greater than 20%.
- In the current synthetic data profile, the baseline required field NULL ratio is expected to be 0%, so the detector records the percentage-point increase as the change rate.
- Institution-level duplicate key detection checks whether multiple `verification_records` rows share the same `verification_record_key`.
- A `DUPLICATE_DETECTED` incident is created when the duplicate row count is greater than zero.
- The duplicate detector records the duplicate row count as an `incident_metric_snapshot` so repeated detection can be validated without creating duplicate incidents.
- Institution-level missing data detection checks whether an active external institution has a previous 7-day baseline but zero treatment records on the target date.
- A `SOURCE_MISSING` incident is created when the current institution record count is zero.
- Full missing data is handled separately from volume drop detection so a total non-receipt is classified as missing source data, while partial drops remain `COUNT_DROP`.

## 5. AI Agent Input and Output

The AI agent does not receive unrestricted database access.

Input:

```json
{
  "incident": {
    "targetTable": "treatment_records",
    "anomalyType": "COUNT_DROP",
    "severity": "CRITICAL",
    "baselineValue": 12000,
    "currentValue": 3360,
    "changeRate": -72
  },
  "relatedMetrics": [
    {
      "name": "INST_B treatment_records",
      "baseline": 4000,
      "current": 300
    }
  ],
  "schemaHints": [
    "treatment_records.external_institution_id references external_institution.id",
    "data_ingestion_log tracks batch status by institution"
  ]
}
```

Output:

```json
{
  "summary": "Treatment record intake volume dropped sharply compared with the baseline.",
  "impactScope": "Records from one external institution may be incomplete.",
  "suspectedCauses": [
    {
      "rank": 1,
      "cause": "Institution ingestion failure",
      "reason": "The institution-specific ingestion volume dropped during the same window.",
      "confidence": 0.82
    }
  ],
  "verificationSql": [
    {
      "title": "Compare treatment record count by institution",
      "purpose": "Check whether the drop is isolated to a specific institution.",
      "sql": "select external_institution_id, count(*) from treatment_records where recorded_at >= current_date group by external_institution_id"
    }
  ]
}
```

## 6. Safe AI Data Access

Safety rules:

- The AI does not connect directly to PostgreSQL.
- The backend builds a limited incident context.
- Generated SQL is SELECT-only.
- `UPDATE`, `DELETE`, `INSERT`, `DROP`, `ALTER`, and similar statements are blocked.
- SQL is stored for developer review before execution.
- Production data modification is outside MVP scope.

Backend components:

- `AiContextBuilder`
- `LlmIncidentAnalyzer`
- `SqlSafetyValidator`
- `VerificationSqlService`

## 7. Report Generation

OpsLens generates two reports from the same analysis result.

Developer report:

- Target table
- Detection time
- Baseline and current metric
- Change rate
- Related external institutions
- Suspected causes
- Verification SQL
- Additional checks

Business report:

- What happened
- Possible business impact
- Whether existing data may be affected
- What is being checked
- Current status
- Whether the business user needs to take action

This is a key differentiator because operational backend work often requires both technical diagnosis and audience-aware communication.

## 8. REST API

Initial endpoints:

```text
GET    /api/incidents
GET    /api/incidents/{id}
POST   /api/incidents/detect
POST   /api/incidents/{id}/analyze
GET    /api/incidents/{id}/analysis
GET    /api/incidents/{id}/reports?type=DEVELOPER
GET    /api/incidents/{id}/verification-sql
GET    /api/scenarios
POST   /api/scenarios/inject
POST   /api/test-data/generate
GET    /api/evaluations
POST   /api/evaluations/incidents/{id}
```

Authentication is excluded from the MVP. In a real service, Spring Security and role-based access would be added.

## 9. Backend Package Structure

```text
com.opslens
  domain
    incident
    detection
    analysis
    report
    datasource
    scenario
    evaluation
  application
    detection
    incident
    analysis
    report
    scenario
    evaluation
  infrastructure
    persistence
    llm
    scheduler
    sql
  api
    incident
    analysis
    report
    scenario
    evaluation
  common
    exception
    time
    response
```

This package structure keeps the monolith understandable while making domain responsibilities clear.

## 10. Frontend Structure

Pages:

- Dashboard
- IncidentDetail
- ScenarioLab
- EvaluationDashboard

Components:

- IncidentList
- SeverityBadge
- MetricComparison
- AnalysisPanel
- CauseCandidateList
- SqlViewer
- ReportTabs
- ScenarioInjector
- EvaluationSummary

The UI should focus on the incident workflow instead of becoming a generic admin CRUD interface.

## 11. Synthetic Test Data

Synthetic data is generated for:

- Treatment records
- Record subjects
- Verification records
- External institutions
- Ingestion logs

The generator should use a fixed random seed so scenarios are reproducible.

Normal data is created first. Then scenario injection modifies or inserts abnormal data patterns.

Implemented normal generation API:

```text
POST /api/test-data/generate
```

Default generation profile:

- 8 days of data.
- 3 external institutions.
- 20 record subjects per external institution.
- 40 treatment records per external institution per day.
- 1 successful verification record per treatment record.
- 1 successful `treatment_records` ingestion log per external institution per day.
- Fixed default seed: `20260913`.

The API resets existing operational data by default so repeated runs produce a clean normal baseline.

## 12. Incident Scenarios

Initial scenarios:

1. 80% treatment record volume drop for one external institution.
2. Sudden increase in `required_field_value` NULL ratio.
3. Duplicate `verification_record_key` values.
4. Missing treatment record data for a specific date.
5. Batch failure increase causing partial ingestion.
6. Treatment records exist but matching verification data is missing.

Implemented MVP scenarios:

- `TREATMENT_RECORD_VOLUME_DROP`: removes 80% of treatment records and related verification records for an institution/date.
- `REQUIRED_FIELD_NULL_SPIKE`: clears required field values for 80% of record subjects in an external institution.
- `DUPLICATE_RECORD_KEY`: rewrites multiple verification records to share the same `verification_record_key`.
- `INSTITUTION_DATA_MISSING`: removes all treatment and verification records for an institution/date to simulate a full missing transmission.

Each scenario stores:

- Scenario type
- Injected date range
- Expected incident type
- Expected root cause
- Expected related tables
- Expected verification pattern

Implemented scenario APIs:

```text
GET  /api/scenarios
POST /api/scenarios/inject
```

Implemented detection API:

```text
POST /api/incidents/detect
```

The current MVP implementation detects `COUNT_DROP` incidents for the `treatment_records` table, `NULL_SPIKE` incidents for `record_subjects.required_field_value`, `DUPLICATE_DETECTED` incidents for `verification_records.verification_record_key`, and `SOURCE_MISSING` incidents for institution-level missing treatment record transmissions.

## 13. MVP Evaluation

Metrics:

- Anomaly detection success rate
- Whether the expected root cause appears in AI candidates
- Whether the expected root cause appears in top 3 candidates
- Incorrect cause suggestion rate
- Generated SQL validity
- Manual analysis time
- AI-assisted analysis time

Example final result:

```text
Detection success: 9 / 10
Expected cause included: 8 / 9
Top 3 cause accuracy: 7 / 9
Valid verification SQL: 8 / 9
Average manual analysis time: 25 minutes
Average AI-assisted analysis time: 8 minutes
```

## 14. Implementation Plan

Small independently testable tasks:

1. Create Spring Boot, React, and Docker Compose skeleton.
2. Add PostgreSQL connection, Flyway migration, and database status check.
3. Create operational data tables, JPA entities, and repository tests.
4. Create incident tables, JPA entities, status lifecycle methods, and repository tests.
5. Implement seed-based synthetic normal data generation API.
6. Implement scenario table, supported scenario API, and anomaly injection service.
7. Implement volume drop detection.
8. Implement required field NULL spike detection.
9. Implement duplicate record key detection.
10. Implement institution missing detection.
11. Implement incident list API.
12. Implement incident detail API.
13. Build React dashboard.
14. Build React incident detail page.
15. Implement AI context builder.
16. Implement mock LLM adapter.
17. Store structured AI analysis results.
18. Implement SQL safety validator.
19. Build developer report view.
20. Build business report view.
21. Implement evaluation result calculation.
22. Build evaluation dashboard.

Each task should include unit or integration tests before moving to the next task.
