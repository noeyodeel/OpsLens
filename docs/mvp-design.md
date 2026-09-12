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

- SourceSystem
- Order
- Customer
- Payment
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
SourceSystem 1:N DataIngestionLog
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

- `source_system`
- `orders`
- `customers`
- `payments`
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

- `source_system`: synthetic external source systems.
- `customers`: customer master-like data with nullable phone fields for NULL spike scenarios.
- `orders`: order records used for count drop, count spike, and source missing scenarios.
- `payments`: payment records used for duplicate payment and order-payment mismatch scenarios.
- `data_ingestion_log`: batch/source ingestion status used to explain missing or partial data.

Implemented incident tables:

- `detection_rule`: rule definitions used by anomaly detectors.
- `incident`: detected anomaly events with severity, status, target table, and anomaly type.
- `incident_metric_snapshot`: baseline/current metric values captured when an incident is created.

## 4. Anomaly Detection

MVP detection is rule-based.

Initial rules:

- Count drop: current daily count is below 50% of the 7-day average.
- Count spike: current daily count is above 200% of the 7-day average.
- NULL spike: target column NULL ratio doubles compared with baseline.
- Duplicate detection: duplicate count for a unique business key is greater than zero.
- Source missing: source count is zero or drops by more than 70%.
- Processing failure spike: failed count ratio exceeds 10%.

Rule-based detection is explainable, reproducible, and easier to validate than a statistical or ML-based approach for the first MVP.

## 5. AI Agent Input and Output

The AI agent does not receive unrestricted database access.

Input:

```json
{
  "incident": {
    "targetTable": "orders",
    "anomalyType": "COUNT_DROP",
    "severity": "CRITICAL",
    "baselineValue": 12000,
    "currentValue": 3360,
    "changeRate": -72
  },
  "relatedMetrics": [
    {
      "name": "SRC_B orders",
      "baseline": 4000,
      "current": 300
    }
  ],
  "schemaHints": [
    "orders.source_system_id references source_system.id",
    "data_ingestion_log tracks batch status by source"
  ]
}
```

Output:

```json
{
  "summary": "Order volume dropped sharply compared with the baseline.",
  "impactScope": "Orders from one source system may be incomplete.",
  "suspectedCauses": [
    {
      "rank": 1,
      "cause": "Source ingestion failure",
      "reason": "The source-specific ingestion count dropped during the same window.",
      "confidence": 0.82
    }
  ],
  "verificationSql": [
    {
      "title": "Compare order count by source",
      "purpose": "Check whether the drop is isolated to a specific source.",
      "sql": "select source_system_id, count(*) from orders where ordered_at >= current_date group by source_system_id"
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
- Related source systems
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
POST   /api/scenarios/{id}/inject
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

- Orders
- Customers
- Payments
- Source systems
- Ingestion logs

The generator should use a fixed random seed so scenarios are reproducible.

Normal data is created first. Then scenario injection modifies or inserts abnormal data patterns.

Implemented normal generation API:

```text
POST /api/test-data/generate
```

Default generation profile:

- 8 days of data.
- 3 source systems.
- 20 customers per source system.
- 40 orders per source system per day.
- 1 successful payment per order.
- 1 successful `orders` ingestion log per source system per day.
- Fixed default seed: `20260913`.

The API resets existing operational data by default so repeated runs produce a clean normal baseline.

## 12. Incident Scenarios

Initial scenarios:

1. 80% order volume drop for one source system.
2. Sudden increase in `customer_phone` NULL ratio.
3. Duplicate `payment_id` values.
4. Missing order data for a specific date.
5. Batch failure increase causing partial ingestion.
6. Orders exist but matching payment data is missing.

Implemented MVP scenarios:

- `ORDER_VOLUME_DROP`: removes 80% of orders and related payments for a source/date.
- `CUSTOMER_PHONE_NULL_SPIKE`: clears phone numbers for 80% of customers in a source system.
- `DUPLICATE_PAYMENT_ID`: rewrites multiple payment rows to share the same `payment_id`.

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
7. Implement count drop detection.
8. Implement NULL spike detection.
9. Implement duplicate detection.
10. Implement source missing detection.
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
