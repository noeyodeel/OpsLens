# OpsLens

OpsLens is an AI-assisted incident analysis tool for operational data.

It detects abnormal data patterns, creates incidents, suggests possible root causes, generates safe verification SQL, and produces separate reports for developers and business users.

## Why This Project Exists

In real backend operation work, developers often repeat the same incident investigation steps:

- Check data volume changes
- Compare current data with historical baselines
- Find NULL spikes, duplicates, and missing source data
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

- `domain.datasource`: source systems, synthetic operational data, ingestion logs, and repositories
- `domain.incident`: detection rules, incidents, metric snapshots, status lifecycle, and repositories
- `application.system`: database status checks
- `api.health`: backend health endpoint
- `api.system`: database and migration status endpoint

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
