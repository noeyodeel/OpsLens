# OpsLens

Slack에서 데이터 이상 징후를 요청하면 백엔드가 분석 Job을 생성하고, Queue/Worker가 비동기로 처리한 뒤 Slack과 대시보드로 결과를 제공하는 운영 자동화 프로젝트입니다.

```text
Slack -> Spring Boot API -> RabbitMQ -> Python Worker -> PostgreSQL -> Slack / Dashboard
```

## Demo

Frontend:

```text
https://opslens-dashboard.vercel.app
```

> 프론트엔드는 Vercel에 배포되어 있으며, 백엔드/DB/Queue/Worker는 Docker Compose와 Cloudflare Tunnel 기반으로 시연합니다.

## What It Does

OpsLens는 외부 기관에서 전송되는 운영 데이터의 이상 징후를 탐지하고 분석하는 MVP입니다.

예를 들어 특정 기관의 데이터가 수신되지 않았을 때, 사용자는 Slack에서 다음처럼 요청할 수 있습니다.

```text
/opslens analyze INC-20260913-SOURCE-MISSING-INST_02
```

시스템은 분석 작업을 Queue에 넣고, Worker가 비동기로 인시던트를 분석한 뒤 Slack에 요약 결과와 대시보드 상세 링크를 응답합니다.

## Key Features

- 기관 전송 데이터 이상 탐지
- 인시던트 자동 생성 및 상세 조회
- Slack Slash Command 연동
- RabbitMQ 기반 비동기 Job 처리
- Python Worker 기반 분석 실행
- 분석 결과 저장
- 개발자용 / 업무 담당자용 리포트 생성
- Vercel 대시보드에서 상세 확인

## Architecture

```text
Slack Slash Command
        |
        v
Spring Boot Backend
        |
        +--> PostgreSQL
        |
        v
RabbitMQ Queue
        |
        v
Python AI Worker
        |
        +--> Analysis API
        +--> Slack response_url

React Dashboard
        |
        v
Spring Boot Backend
```

## Tech Stack

### Backend

- Java 17
- Spring Boot 3
- Spring Data JPA
- PostgreSQL
- Flyway
- RabbitMQ

### Frontend

- React
- TypeScript
- Vite
- Vercel

### Worker / Infra

- Python
- Docker Compose
- Cloudflare Tunnel
- Slack Slash Command

## Project Highlights

- 단순 CRUD가 아니라 Slack에서 시작되는 운영 자동화 흐름을 구현했습니다.
- 요청 처리와 분석 실행을 Queue/Worker 구조로 분리했습니다.
- 분석 Job 상태를 저장하여 비동기 작업의 진행 상태를 추적할 수 있게 했습니다.
- Slack 응답과 웹 대시보드를 연결해 실제 운영 도구처럼 사용할 수 있도록 구성했습니다.

## Current Scope

현재는 포트폴리오용 MVP로, Mock AI 분석 로직을 사용합니다. 구조상 LangGraph, VectorDB, LLM API를 Worker 내부에 추가하여 더 고도화된 AI 분석 파이프라인으로 확장할 수 있습니다.

## Run Locally

```bash
docker compose up -d
```

```bash
cd backend
./mvnw spring-boot:run
```

```bash
cd frontend
npm install
npm run dev
```

Backend:

```text
http://localhost:8080
```

Frontend:

```text
http://localhost:5173
```

## Test

Backend:

```bash
cd backend
./mvnw test
```

Frontend:

```bash
cd frontend
npm run build
```
