# OpsLens

OpsLens는 병원 HIS/ESB 인터페이스 운영 중 발생하는 Oracle 오류 로그를 문서 기반으로 분석하고, 처리 이력을 축적하는 포트폴리오용 운영 지원 도구입니다.

실제 운영 DB에 연결하지 않고 `HIS_MAPPING_ERROR_LOG` 형태의 mock 데이터를 사용합니다. 운영 문서는 `docs/knowledge-local`에 로컬로만 보관하며 Git에는 포함하지 않습니다.

## Problem

운영 중에는 매일 오류 로그 테이블을 조회하고, `PROC_NAME`, `ERROR_CODE`, `ERROR_MSG`, `ERROR_STACK`, `ESB_SEQ`를 기준으로 원인과 수정 방향을 찾아야 합니다.

기존 흐름은 다음과 같은 수작업이 많습니다.

- 오류 로그 테이블에서 최신 대표 오류 확인
- 프로시저명과 오류코드로 관련 인터페이스 정의서 검색
- 대상 테이블과 컬럼의 필수 여부, 길이, 매핑 기준 확인
- 이전 유사 오류 처리 내역 확인
- 수정 방향과 재처리 여부를 메모로 남김

OpsLens는 이 흐름을 대시보드, 문서 검색, AI 분석, 처리 이력 저장으로 묶는 것을 목표로 합니다.

## Key Features

- `HIS_MAPPING_ERROR_LOG` 형식의 오류 로그 대시보드
- 오류코드별 분석 규칙 제공
  - `ORA-01722`
  - `ORA-12899`
  - `ORA-01400`
  - `ORA-38104`
  - `ORA-00001`
  - 기타 Oracle 오류 유형
- 로컬 문서 폴더 기반 인터페이스 정의서/PDF 매칭
- Excel/PDF 일부 내용 추출
  - Apache POI로 Excel 행 snippet 추출
  - Apache PDFBox로 PDF 문장 snippet 추출
- OpenAI Responses API 연동 준비
  - `OPENAI_API_KEY`가 있으면 실제 LLM 분석
  - 키가 없거나 호출 실패 시 mock RAG 분석으로 fallback
- 분석 결과 저장
- 처리 상태와 운영 메모 저장
- 같은 프로시저, 같은 오류코드, 같은 대상 테이블 기준 유사 오류 이력 표시

## Architecture

```text
React Dashboard
  -> Spring Boot API
    -> HIS error mock data
    -> Knowledge document index
      -> Excel/PDF local files
    -> OpenAI Responses API
    -> Operation history store
```

현재 운영 이력은 포트폴리오 시연을 위해 인메모리로 저장합니다. 이후 PostgreSQL 테이블로 확장할 수 있도록 API 경계를 분리했습니다.

## Tech Stack

### Backend

- Java 17
- Spring Boot 3
- Spring Web
- Spring Data JPA
- Flyway
- Apache POI
- Apache PDFBox
- OpenAI Responses API 연동 구조

### Frontend

- React
- TypeScript
- Vite

### Infra

- Docker Compose
- PostgreSQL
- RabbitMQ

기존 OpsLens의 Slack/RabbitMQ 기반 운영 자동화 구조는 남아 있으며, 현재 README는 HIS 오류 분석 기능을 중심으로 설명합니다.

## Knowledge Documents

운영 문서는 다음 경로에 둡니다.

```text
docs/knowledge-local
```

이 폴더는 `.gitignore`에 포함되어 있습니다.

지원하는 파일:

- `.xlsx`
- `.xls`
- `.pdf`

문서는 실행 지시가 아니라 검색과 분석에 사용하는 참고 데이터로만 취급합니다.

## Environment

루트의 `.env.example`을 참고합니다.

```text
OPENAI_API_KEY=
OPENAI_MODEL=gpt-4.1-mini
OPENAI_BASE_URL=https://api.openai.com/v1
KNOWLEDGE_DIR=../docs/knowledge-local
VITE_API_BASE_URL=http://localhost:8080
```

실제 AI 분석을 사용하려면 실행 전에 `OPENAI_API_KEY`를 설정해야 합니다.

PowerShell:

```powershell
$env:OPENAI_API_KEY="your_openai_api_key"
$env:OPENAI_MODEL="gpt-4.1-mini"
```

키를 설정하지 않으면 mock RAG 분석으로 동작합니다.

## Run Locally

Infrastructure:

```bash
docker compose up -d
```

Backend:

```bash
cd backend
./mvnw spring-boot:run
```

Windows PowerShell:

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Frontend:

```bash
cd frontend
npm install
npm run dev
```

URLs:

```text
Backend  http://localhost:8080
Frontend http://localhost:5173
```

## Demo Scenario

1. 대시보드에서 `H31_ACPPEOCE_TO_ARROPCALNT` 오류를 선택합니다.
2. `ERROR_MSG`에서 대상 테이블과 컬럼을 확인합니다.
3. 관련 인터페이스 정의서가 자동으로 연결되는지 확인합니다.
4. `문서 기반 분석 실행`을 누릅니다.
5. 분석 결과의 원인 후보, 확인 순서, 수정 방향을 확인합니다.
6. 처리 상태를 `확인중` 또는 `해결`로 변경합니다.
7. 운영 메모에 실제 확인 내용을 입력합니다.
8. 유사 오류 처리 이력을 확인해 이전 조치 내용을 재사용합니다.

## Test

Backend:

```bash
cd backend
./mvnw test
```

Windows PowerShell:

```powershell
cd backend
.\mvnw.cmd test
```

Frontend:

```bash
cd frontend
npm run build
```

## Portfolio Message

이 프로젝트는 단순한 오류 목록 조회가 아니라, 운영자가 반복적으로 수행하던 “오류 확인 -> 관련 문서 검색 -> 원인 분석 -> 처리 이력 기록” 흐름을 하나의 도구로 재구성한 프로젝트입니다.

이력서에서는 다음처럼 요약할 수 있습니다.

> HIS/ESB 인터페이스 오류 로그를 대상으로 문서 기반 AI 분석과 처리 이력 관리를 제공하는 운영 지원 대시보드를 구현했습니다. Oracle 오류코드, 프로시저명, 대상 테이블/컬럼을 기반으로 관련 인터페이스 정의서와 매뉴얼을 매칭하고, Excel/PDF snippet을 분석 context로 구성해 원인 후보와 수정 방향을 제안하도록 설계했습니다.
