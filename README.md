# OpsLens

OpsLens is an AI-assisted incident analysis tool for institution data intake operations.

It detects abnormal transmission patterns, creates incidents, suggests possible root causes, generates safe verification SQL, and produces separate reports for developers and business users.

## Why This Project Exists

This project is based on repeated verification work that happens when external institutions send operational records into a backend system.

It does not use company data or real treatment data. The MVP uses synthetic institution transmission data and intentionally injected incident scenarios.

In real backend operation work, developers often repeat the same incident investigation steps:

- Check data volume changes
- Compare current data with historical baselines
- Find NULL spikes, duplicates, and missing institution data
- Inspect ingestion logs and related tables
- Write verification SQL
- Explain the cause and status to non-developers

OpsLens focuses on automating the repetitive first-pass analysis while keeping final judgment and data modification in human hands.

## MVP Scope

- Detect operational data anomalies
- Create incidents automatically
- Analyze incidents with an LLM using summarized context
- Generate SELECT-only verification SQL
- Generate developer-focused reports
- Generate business-friendly reports
- Run synthetic incident scenarios
- Measure detection and analysis quality

## Tech Stack

- Backend: Java 17, Spring Boot 3, Spring Data JPA
- Database: PostgreSQL
- Frontend: React
- AI: LLM API
- DevOps: Docker, GitHub Actions

## Core Principle

AI does not directly modify production data or make final incident decisions.

OpsLens assists developers by collecting context, suggesting likely causes, and drafting reports. Developers remain responsible for root-cause judgment, impact assessment, fixes, and final verification.

## Documentation

- [MVP Design](docs/mvp-design.md)

## Backend Modules

- `domain.datasource`: external institutions, synthetic transmission data, ingestion logs, and repositories
- `domain.incident`: detection rules, incidents, metric snapshots, status lifecycle, and repositories
- `domain.scenario`: injected incident scenarios and expected root causes
- `application.detection`: rule-based anomaly detection and incident creation
- `application.scenario`: synthetic anomaly scenario injection
- `application.testdata`: reproducible synthetic normal data generation
- `application.system`: database status checks
- `api.incident`: incident detection endpoint
- `api.health`: backend health endpoint
- `api.system`: database and migration status endpoint
- `api.testdata`: synthetic data generation endpoint

## Local Development

Start PostgreSQL:

```bash
docker compose up -d
```

Run the backend:

```bash
cd backend
./mvnw spring-boot:run
```

Run the frontend:

```bash
cd frontend
npm install
npm run dev
```

Health check:

```bash
curl http://localhost:8080/api/health
```

Database and migration check:

```bash
curl http://localhost:8080/api/system/database
```

The database check reads the `app_metadata` table created by Flyway. Start Docker Desktop before running `docker compose up -d`.

Generate normal synthetic data:

```bash
curl -X POST http://localhost:8080/api/test-data/generate \
  -H "Content-Type: application/json" \
  -d '{"days":8,"institutionCount":3,"subjectsPerInstitution":20,"recordsPerInstitutionPerDay":40,"seed":20260913,"baseDate":"2026-09-13"}'
```

List supported scenarios:

```bash
curl http://localhost:8080/api/scenarios
```

Inject a scenario:

```bash
curl -X POST http://localhost:8080/api/scenarios/inject \
  -H "Content-Type: application/json" \
  -d '{"scenarioType":"TREATMENT_RECORD_VOLUME_DROP","targetInstitutionCode":"INST_02","targetDate":"2026-09-13"}'
```

Run anomaly detection:

```bash
curl -X POST http://localhost:8080/api/incidents/detect \
  -H "Content-Type: application/json" \
  -d '{"targetDate":"2026-09-13"}'
```

The detection endpoint currently runs:

- `VOLUME_DROP`: institution-level treatment record volume drop detection.
- `REQUIRED_FIELD_NULL_SPIKE`: institution-level required field NULL ratio detection.
