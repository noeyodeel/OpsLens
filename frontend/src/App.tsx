import { useEffect, useMemo, useState } from 'react'
import './App.css'

type ErrorStatus = 'OPEN' | 'CHECKING' | 'RESOLVED' | 'HOLD'

type HisErrorLog = {
  errorId: number
  errorDt: string
  procName: string
  esbSeq: string | null
  regionCd: string
  errorCode: string
  errorMsg: string
  errorStack: string
  isHis: string
  status: ErrorStatus
  memo: string
}

type ErrorRule = {
  label: string
  severity: 'critical' | 'warning' | 'info'
  summary: string
  causeCandidates: string[]
  checkItems: string[]
  fixDirections: string[]
}

type ErrorFilter = 'ALL' | ErrorStatus

type KnowledgeDocument = {
  id: string
  title: string
  fileName: string
  kind: 'INTERFACE' | 'MANUAL'
  tags: string[]
  relatedProcedures: string[]
  relatedTables: string[]
  description: string
}

type KnowledgeSnippet = {
  id: string
  documentId: string
  title: string
  content: string
  keywords: string[]
}

type KnowledgeAnalysisResult = {
  errorId: number
  question: string
  answer: string
  summary: string
  suspectedCause: string
  checkSteps: string[]
  fixSteps: string[]
  documents: KnowledgeDocument[]
  snippets: KnowledgeSnippet[]
  generatedAt: string
  mode: 'MOCK_RAG' | 'OPENAI'
}

type HisAnalyzeApiResponse = KnowledgeAnalysisResult

type SimilarHistory = {
  errorId: number
  errorDt: string
  procName: string
  errorCode: string
  status: ErrorStatus
  memo: string
  reason: string
}

type OperationRecord = {
  errorId: number
  status: ErrorStatus
  memo: string
  lastAnalysisSummary: string
  lastAnalysisAnswer: string
  analyzedAt: string | null
  updatedAt: string
  similarHistories: SimilarHistory[]
}

const statusOptions: ErrorFilter[] = ['ALL', 'OPEN', 'CHECKING', 'RESOLVED', 'HOLD']
const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '')

function apiUrl(path: string) {
  return `${apiBaseUrl}${path}`
}

const knowledgeStats = {
  totalFiles: 55,
  interfaceFiles: 53,
  manualFiles: 2,
}

const knowledgeDocuments: KnowledgeDocument[] = [
  {
    id: 'h026-arr-acpprodm',
    title: 'HISEMS_026N ARR 외래접수 ACPPRODM',
    fileName: 'VHS-AS-ESB-인터페이스 정의서(HISEMS_026N_ARR_외래접수_ACPPRODM(외래예약접수기본)-ver1.0.xlsx',
    kind: 'INTERFACE',
    tags: ['H26', 'ACPPRODM', 'ARROPRSVNT', '외래예약접수'],
    relatedProcedures: ['H26_ACPPRODM_TO_ARROPRSVNT', 'IF_HIS_ACPPRODM_TO_ARROPRSVNT'],
    relatedTables: ['ARROPRSVNT'],
    description: '외래 예약 접수 기본 데이터의 항목, 길이, 필수 여부, 대상 테이블 매핑을 확인하는 문서입니다.',
  },
  {
    id: 'h029-arr-acppeice',
    title: 'HISEMS_029N ARR 입원처방계산 ACPPEICE',
    fileName: 'VHS-AS-ESB-인터페이스 정의서(HISEMS_029N_ARR_입원처방계산_ACPPEICE(입원계산상세)-ver1.0.xlsx',
    kind: 'INTERFACE',
    tags: ['H29', 'ACPPEICE', 'ARRIPCALNT', '입원계산상세'],
    relatedProcedures: ['H29_ACPPEICE_TO_ARRIPCALNT', 'H29_ACPPEICE_TO_ARRIPCALNT_FAST'],
    relatedTables: ['ARRIPCALNT'],
    description: '입원 계산 상세 인터페이스 정의서로 ARRIPCALNT 관련 필수값, 길이, 매핑 기준을 확인합니다.',
  },
  {
    id: 'h031-arr-acppeoce',
    title: 'HISEMS_031N ARR 외래처방계산 ACPPEOCE',
    fileName: 'VHS-AS-ESB-인터페이스 정의서(HISEMS_031N_ARR_외래처방계산_ACPPEOCE(외래계산상세)-ver2.0.xlsx',
    kind: 'INTERFACE',
    tags: ['H31', 'ACPPEOCE', 'ARROPCALNT', '외래계산상세'],
    relatedProcedures: ['H31_ACPPEOCE_TO_ARROPCALNT', 'H31_ACPPEOCE_TO_ARROPCALNT_FAST', 'IF_HIS_ACPPEOCE_TO_ARROPCALNT'],
    relatedTables: ['ARROPCALNT'],
    description: '외래 계산 상세 인터페이스 정의서로 ARROPCALNT 컬럼 길이, 필수값, 코드 매핑 규칙 확인에 사용합니다.',
  },
  {
    id: 'h032-arr-pctpcpam',
    title: 'HISEMS_032N ARR 환자번호 PCTPCPAM_DAMO',
    fileName: 'VHS-AS-ESB-인터페이스 정의서(HISEMS_032N_ARR_환자번호_PCTPCPAM_DAMO(환자기본)-ver1.0.xlsx',
    kind: 'INTERFACE',
    tags: ['H32', 'PCTPCPAM', 'PCTPCPAM_DAMO', '환자기본'],
    relatedProcedures: ['IF_HIS_PCTPCPAM_DAMO_TO_ARRPATNOMT', 'IF_HIS_PCTPCPAM_DAMO_TO_ARRPATBAMT'],
    relatedTables: ['ARRPATNOMT', 'ARRPATBAMT', 'ARROPRSVNT'],
    description: '환자 기본/환자번호 인터페이스 정의서로 환자 식별값과 관련 테이블 매핑을 확인합니다.',
  },
  {
    id: 'h036-erp-dept',
    title: 'HISEMS_036N ERP 부서기본',
    fileName: 'VHS-AS-ESB-인터페이스 정의서(HISEMS_036N_ERP_부서기본).xlsx',
    kind: 'INTERFACE',
    tags: ['H36', 'PDEDBMSM', 'CBIDEPART', '부서기본'],
    relatedProcedures: ['H36_PDEDBMSM_TO_CBIDEPART'],
    relatedTables: ['CBIDEPART'],
    description: '부서 기본 정보 인터페이스 정의서로 숫자 정밀도와 컬럼 정의 확인에 사용합니다.',
  },
  {
    id: 'h039-mod-order-master',
    title: 'HISEMS_039N MOD 오더마스터',
    fileName: 'VHS-AS-ESB-인터페이스 정의서(HISEMS_039N_MOD_오더마스터)-ver1.0.xlsx',
    kind: 'INTERFACE',
    tags: ['H39', 'CCOOCBAC', 'MODZSUGAMT', '오더마스터'],
    relatedProcedures: ['H39_CCOOCBAC_TO_MODZSUGAMT'],
    relatedTables: ['MODZSUGAMT'],
    description: '오더 마스터 항목의 코드 길이와 매핑 기준을 확인하는 문서입니다.',
  },
  {
    id: 'h040-mod-medication',
    title: 'HISEMS_040N MOD 환자투약처방',
    fileName: 'VHS-AS-ESB-인터페이스 정의서(HISEMS_040N_MOD_환자투약처방)-ver1.0.xlsx',
    kind: 'INTERFACE',
    tags: ['H40', 'MOOORDRM', 'MODDDGIJST', '환자투약처방'],
    relatedProcedures: ['H40_MOOORDRM_TO_MODDDGIJST', 'IF_HIS_MOOORDRM_TO_MODDDGIJST'],
    relatedTables: ['MODDDGIJST'],
    description: '환자 투약 처방 인터페이스 정의서로 처방 코드 길이와 필수값 확인에 사용합니다.',
  },
  {
    id: 'h042-mod-diagnosis',
    title: 'HISEMS_042N MOD 진단관리',
    fileName: 'VHS-AS-ESB-인터페이스 정의서(HISEMS_042N_MOD_진단관리)-ver1.0.xlsx',
    kind: 'INTERFACE',
    tags: ['H42', 'MOODIPCD', 'MODJSANGST', '진단관리'],
    relatedProcedures: ['H42_MOODIPCD_TO_MODJSANGST'],
    relatedTables: ['MODJSANGST'],
    description: '진단 관리 인터페이스 정의서로 상병/진단 코드 항목 정의를 확인합니다.',
  },
  {
    id: 'service-start-stop-manual',
    title: '서비스 시작 및 중지 매뉴얼',
    fileName: '[진료내역확인시스템] 서비스_시작_및_중지_매뉴얼_230816.pdf',
    kind: 'MANUAL',
    tags: ['서비스', '재기동', '운영조치', '시작중지'],
    relatedProcedures: [],
    relatedTables: [],
    description: '오류 수정 후 서비스 재기동 또는 운영 점검이 필요한 경우 참고하는 매뉴얼입니다.',
  },
  {
    id: 'deployment-manual',
    title: '배포 매뉴얼',
    fileName: '[진료내역확인시스템] 배포_매뉴얼_230816.pdf',
    kind: 'MANUAL',
    tags: ['배포', '프로시저반영', '운영반영', '릴리즈'],
    relatedProcedures: [],
    relatedTables: [],
    description: '프로시저나 매핑 로직 수정 후 운영 반영 절차를 확인하는 매뉴얼입니다.',
  },
]

const knowledgeSnippets: KnowledgeSnippet[] = [
  {
    id: 'snippet-h031-required-fields',
    documentId: 'h031-arr-acppeoce',
    title: '외래계산상세 필수값 확인',
    content: 'ARROPCALNT 계열 오류는 외래처방계산 인터페이스 정의서에서 대상 컬럼의 필수 여부, 길이, 코드 매핑 기준을 먼저 확인합니다. TEC_MAT_CLS, SUGA_SEL_CLS 같은 NOT NULL 컬럼 오류는 원천 데이터와 CASE 매핑 조건을 함께 확인해야 합니다.',
    keywords: ['H31', 'ACPPEOCE', 'ARROPCALNT', 'TEC_MAT_CLS', 'SUGA_SEL_CLS', 'ORA-01400', '-1400'],
  },
  {
    id: 'snippet-h031-length',
    documentId: 'h031-arr-acppeoce',
    title: '외래계산상세 길이 초과 확인',
    content: 'ORA-12899가 ARROPCALNT 컬럼에서 발생하면 인터페이스 정의서의 항목 길이와 대상 DB 컬럼 길이를 비교합니다. 실제 값 길이가 정의서보다 길면 원천 값 보정, 코드 변환, 컬럼 확장 검토 중 하나로 조치 방향을 정합니다.',
    keywords: ['H31', 'ACPPEOCE', 'ARROPCALNT', 'EXDD_PRSNID', 'ORA-12899', '-12899'],
  },
  {
    id: 'snippet-h029-merge',
    documentId: 'h029-arr-acppeice',
    title: '입원계산상세 MERGE 조건 확인',
    content: 'ARRIPCALNT 매핑에서 ORA-38104가 발생하면 MERGE ON 절에 사용한 MED_DTTM, ESB_SEQ, 계산키 계열 컬럼이 UPDATE SET에도 포함됐는지 확인합니다. ON 절 식별 컬럼은 UPDATE 대상에서 제외하거나 별도 UPDATE로 분리합니다.',
    keywords: ['H29', 'ACPPEICE', 'ARRIPCALNT', 'MED_DTTM', 'ORA-38104', '-38104'],
  },
  {
    id: 'snippet-h040-order-length',
    documentId: 'h040-mod-medication',
    title: '투약처방 코드 길이 확인',
    content: 'MODDDGIJST 또는 투약처방 계열 ORA-12899는 처방/수가 코드 길이 불일치 가능성이 높습니다. 정의서의 SUGA_CD, ORD_CD 같은 코드 항목 길이와 원천 코드 길이를 비교합니다.',
    keywords: ['H40', 'MOOORDRM', 'MODDDGIJST', 'SUGA_CD', 'ORD_CD', 'ORA-12899', '-12899'],
  },
  {
    id: 'snippet-h032-patient',
    documentId: 'h032-arr-pctpcpam',
    title: '환자기본 식별값 확인',
    content: '환자기본 인터페이스는 환자번호와 환자 식별값 매핑이 핵심입니다. PK 중복, 환자번호 누락, 환자 관련 대상 테이블 오류가 발생하면 환자기본 정의서에서 원천 항목과 대상 항목의 매핑을 먼저 확인합니다.',
    keywords: ['H32', 'PCTPCPAM', '환자기본', 'ARRPATNOMT', 'ARRPATBAMT', 'ORA-00001', '-1'],
  },
  {
    id: 'snippet-deploy',
    documentId: 'deployment-manual',
    title: '수정 후 배포 절차 확인',
    content: '프로시저, DDL, 매핑 SQL을 수정한 뒤에는 배포 매뉴얼 기준으로 반영 대상, 반영 순서, 재처리 범위, 롤백 가능성을 확인합니다. SQL 문법 오류나 객체 누락 오류는 배포 체크리스트와 함께 확인하는 것이 좋습니다.',
    keywords: ['배포', '프로시저', 'DDL', 'ORA-00904', 'ORA-00942', 'ORA-00957', '-904', '-942', '-957'],
  },
  {
    id: 'snippet-service-restart',
    documentId: 'service-start-stop-manual',
    title: '운영 조치 후 서비스 확인',
    content: '오류 수정 후 서비스 영향이 있는 경우 서비스 시작/중지 매뉴얼 기준으로 중지, 시작, 상태 확인 순서를 점검합니다. 단순 데이터 보정인지 서비스 재기동이 필요한 배포인지 구분해야 합니다.',
    keywords: ['서비스', '재기동', '운영조치', '시작', '중지', '해결'],
  },
]
const errorRules: Record<string, ErrorRule> = {
  '-1722': {
    label: '숫자 변환 오류',
    severity: 'critical',
    summary: '숫자로 변환해야 하는 값에 문자, 공백, 특수문자 등이 포함되었을 가능성이 큽니다.',
    causeCandidates: ['TO_NUMBER 대상 값에 비숫자 데이터가 포함됨', '숫자 컬럼에 문자 타입 원천 값이 매핑됨', '지역 또는 원천 시스템별 예외 값 처리 조건이 누락됨'],
    checkItems: ['ERROR_STACK의 프로시저 라인 확인', '해당 라인의 TO_NUMBER 또는 숫자 컬럼 매핑 확인', 'ESB_SEQ 기준 원천 데이터의 숫자 대상 컬럼 확인'],
    fixDirections: ['숫자 변환 전 정규식 또는 VALIDATE_CONVERSION 검증 추가', '업무상 허용되는 예외 값은 기본값 또는 NULL 처리 규칙 정의', '원천 데이터 오류라면 ESB_SEQ 기준 보정 또는 재전송 요청'],
  },
  '-12899': {
    label: '컬럼 길이 초과',
    severity: 'warning',
    summary: '대상 컬럼의 최대 길이보다 긴 값이 INSERT 또는 UPDATE되어 발생한 오류입니다.',
    causeCandidates: ['원천 코드 또는 명칭 길이가 대상 컬럼보다 김', '코드 매핑 후 예상보다 긴 값이 생성됨', '대상 컬럼 길이 설계와 실제 업무 데이터 길이가 맞지 않음'],
    checkItems: ['ERROR_MSG의 대상 테이블, 컬럼, 실제 길이, 최대 길이 확인', 'ESB_SEQ 기준 원천 값 길이 확인', '대상 컬럼의 DATA_LENGTH와 업무 허용 길이 비교'],
    fixDirections: ['코드 컬럼이면 올바른 코드 매핑으로 변환', '정상 업무 데이터라면 컬럼 길이 확장 검토', '임의 절단은 데이터 손실 위험이 있어 업무 규칙 확인 후 적용'],
  },
  '-904': {
    label: '부적합한 컬럼명',
    severity: 'warning',
    summary: 'SQL에서 참조한 컬럼이 대상 테이블 또는 별칭에 존재하지 않을 때 발생합니다.',
    causeCandidates: ['컬럼명 오타 또는 이전 스키마 기준 컬럼 사용', '테이블 별칭이 잘못 지정됨', '마이그레이션 이후 프로시저가 최신 스키마를 반영하지 못함'],
    checkItems: ['ERROR_MSG의 컬럼명 확인', '프로시저 라인의 SELECT/INSERT/MERGE 구문 확인', 'USER_TAB_COLUMNS에서 대상 테이블 컬럼 존재 여부 확인'],
    fixDirections: ['프로시저의 컬럼명을 실제 스키마에 맞게 수정', '별칭 참조가 잘못된 경우 FROM/JOIN 별칭 정리', '스키마 변경 이력을 문서화해 재발 방지'],
  },
  '-1400': {
    label: '필수값 누락',
    severity: 'critical',
    summary: 'NOT NULL 컬럼에 NULL 값이 들어가면서 발생한 오류입니다.',
    causeCandidates: ['필수 컬럼에 매핑되는 원천 값 누락', 'CASE/DECODE 분기에서 특정 케이스가 NULL로 떨어짐', '기준정보 또는 코드 매핑 데이터 누락'],
    checkItems: ['ERROR_MSG에서 대상 테이블/컬럼 추출', 'ESB_SEQ 기준 원천 데이터 확인', '프로시저 INSERT 라인의 컬럼 매핑 확인'],
    fixDirections: ['매핑 누락이면 CASE 조건 보완', '업무상 기본값이 가능하면 명시적 기본값 처리', '원천 누락이면 보정 또는 재전송 요청'],
  },
  '-38104': {
    label: 'MERGE ON절 컬럼 업데이트',
    severity: 'warning',
    summary: 'MERGE문의 ON 절에서 참조한 컬럼을 UPDATE SET 절에서 변경하려고 할 때 발생합니다.',
    causeCandidates: ['ON 조건 컬럼이 UPDATE 대상에도 포함됨', '식별자 컬럼을 매핑 업데이트 목록에 포함함', 'MERGE 조건 변경 후 UPDATE 목록 정리가 누락됨'],
    checkItems: ['ERROR_MSG의 TG 컬럼 확인', 'MERGE ON 절과 UPDATE SET 절 비교', '해당 컬럼이 식별키인지 업무 컬럼인지 확인'],
    fixDirections: ['ON 절 참조 컬럼을 UPDATE 대상에서 제거', '갱신이 필요하면 MERGE 조건을 안정적인 키로 재설계', '식별키 변경은 별도 UPDATE 로직으로 분리'],
  },
  '-1438': {
    label: '숫자 정밀도 초과',
    severity: 'warning',
    summary: 'NUMBER 컬럼에 허용 정밀도보다 큰 숫자가 입력될 때 발생합니다.',
    causeCandidates: ['대상 NUMBER 컬럼 precision보다 큰 값 입력', '금액/수량 계산 결과 자리수 초과', '소수점 또는 반올림 처리 누락'],
    checkItems: ['대상 컬럼 NUMBER precision/scale 확인', '원천 값 또는 계산 결과 자리수 확인', '프로시저의 산술 계산식 확인'],
    fixDirections: ['업무상 정상 값이면 컬럼 정밀도 조정 검토', '계산 결과는 ROUND/TRUNC 등 명시 처리', '비정상 원천 값이면 보정 대상으로 분류'],
  },
  '-957': {
    label: '중복 컬럼명',
    severity: 'info',
    summary: 'INSERT/SELECT 구문에 같은 컬럼명이 중복 지정되어 발생합니다.',
    causeCandidates: ['INSERT 컬럼 목록 중복', 'SELECT alias 중복', '프로시저 생성 자동화 과정에서 컬럼이 중복 포함됨'],
    checkItems: ['프로시저 라인의 INSERT 컬럼 목록 확인', 'SELECT alias 중복 여부 확인', '자동 생성 SQL 템플릿 확인'],
    fixDirections: ['중복 컬럼 제거', 'alias 이름 정리', 'SQL 생성 로직에서 컬럼 중복 제거 처리 추가'],
  },
  '-1407': {
    label: 'NOT NULL 컬럼 업데이트 오류',
    severity: 'critical',
    summary: 'NOT NULL 컬럼을 NULL로 UPDATE하려고 해서 발생한 오류입니다.',
    causeCandidates: ['UPDATE 매핑 값이 NULL로 계산됨', '원천 값 누락', '기본값 처리 없이 필수 컬럼 갱신'],
    checkItems: ['ERROR_MSG의 대상 컬럼 확인', 'UPDATE SET 절 매핑 확인', '원천 값과 CASE 조건 확인'],
    fixDirections: ['NULL 업데이트 방지 조건 추가', '기본값 또는 기존값 유지 로직 검토', '원천 누락 데이터 보정'],
  },
  '-936': {
    label: 'SQL 표현식 누락',
    severity: 'info',
    summary: 'SQL 문장에서 필요한 표현식이 빠졌을 때 발생합니다.',
    causeCandidates: ['SELECT 컬럼 또는 조건식 누락', '콤마/연산자 주변 SQL 조립 오류', '동적 SQL 생성 중 빈 값 포함'],
    checkItems: ['ERROR_STACK 라인의 SQL 구문 확인', '동적 SQL이면 생성된 최종 SQL 확인', '쉼표와 조건절 위치 확인'],
    fixDirections: ['누락된 컬럼/표현식 보완', '동적 SQL 빈 문자열 방어 로직 추가', 'SQL 템플릿 테스트 케이스 추가'],
  },
  '-942': {
    label: '테이블 또는 뷰 없음',
    severity: 'warning',
    summary: '참조한 테이블 또는 뷰가 없거나 권한이 없을 때 발생합니다.',
    causeCandidates: ['테이블명 오타', '스키마 미지정 또는 다른 계정 객체 참조', '권한 누락'],
    checkItems: ['참조 객체명 확인', '현재 스키마와 소유자 확인', '권한 부여 여부 확인'],
    fixDirections: ['스키마명을 명시하거나 객체명 수정', '필요 권한 부여', '배포 대상 DB에 객체 생성 여부 확인'],
  },
  '-1': {
    label: '무결성 제약 조건 위반',
    severity: 'warning',
    summary: '주로 PK 또는 UNIQUE 제약 조건에 중복 값이 들어올 때 발생합니다.',
    causeCandidates: ['이미 존재하는 키를 다시 INSERT', 'MERGE 조건이 중복 데이터를 제대로 잡지 못함', '원천 데이터 중복'],
    checkItems: ['ERROR_MSG의 제약조건명 확인', 'PK/UNIQUE 컬럼 기준 기존 데이터 조회', 'ESB_SEQ 또는 업무 키 중복 여부 확인'],
    fixDirections: ['INSERT 전 존재 여부 확인 또는 MERGE 사용', '원천 중복이면 중복 제거 후 재처리', '업무 키 기준 중복 허용 여부 검토'],
  },
}

const hisErrorLogs: HisErrorLog[] = [
  {
    errorId: 14381114,
    errorDt: '2026-09-22',
    procName: 'H99_AEMRTFUD_TO_ADPBEFVTCAP',
    esbSeq: null,
    regionCd: '03',
    errorCode: '-1722',
    errorMsg: 'ORA-01722: 수치가 부적합합니다',
    errorStack: 'ORA-06512: "SC.H99_AEMRTFUD_TO_ADPBEFVTCAP", 72행',
    isHis: '1',
    status: 'OPEN',
    memo: '최근까지 반복 발생 중. REGION_CD 03 기준 원천 숫자 컬럼 확인 필요.',
  },
  {
    errorId: 14380758,
    errorDt: '2026-09-14',
    procName: 'H31_ACPPEOCE_TO_ARROPCALNT',
    esbSeq: 'H202609140067501356',
    regionCd: '03',
    errorCode: '-12899',
    errorMsg: 'ORA-12899: "HIS_BS"."ARROPCALNT"."EXDD_PRSNID" 열에 대한 값이 너무 큼(실제: 7, 최대값: 6) / ESB_SEQ=H202609140067501356',
    errorStack: 'ORA-06512: "SC.H31_ACPPEOCE_TO_ARROPCALNT", 177행',
    isHis: '1',
    status: 'CHECKING',
    memo: '사용자 ID 길이 기준 확인 중.',
  },
  {
    errorId: 14378677,
    errorDt: '2026-07-10',
    procName: 'H29_ACPPEICE_TO_ARRIPCALNT',
    esbSeq: 'H202607090120083722',
    regionCd: '03',
    errorCode: '-38104',
    errorMsg: 'ORA-38104: ON 절에서 참조되는 열은 업데이트할 수 없음: "TG"."MED_DTTM" / ESB_SEQ=H202607090120083722',
    errorStack: 'ORA-06512: "SC.H29_ACPPEICE_TO_ARRIPCALNT", 142행',
    isHis: '1',
    status: 'OPEN',
    memo: '',
  },
  {
    errorId: 7086823,
    errorDt: '2026-07-07',
    procName: 'H29_ACPPEICE_TO_ARRIPCALNT_FAST',
    esbSeq: null,
    regionCd: '01',
    errorCode: '-1',
    errorMsg: 'ORA-00001: 무결성 제약 조건(HIS_BS.PK_ARRIPCALNT)에 위배됩니다',
    errorStack: 'ORA-06512: "SC.H29_ACPPEICE_TO_ARRIPCALNT_2", 10행',
    isHis: '1',
    status: 'OPEN',
    memo: 'FAST 프로시저 재처리 중 중복 키 발생 가능성.',
  },
  {
    errorId: 7086741,
    errorDt: '2026-06-30',
    procName: 'H31_ACPPEOCE_TO_ARROPCALNT_FAST',
    esbSeq: null,
    regionCd: '01',
    errorCode: '-1839',
    errorMsg: 'ORA-01839: 지정된 월에 대한 날짜가 부적합합니다',
    errorStack: 'ORA-06512: "SC.H31_ACPPEOCE_TO_ARROPCALNT_2", 9행',
    isHis: '1',
    status: 'HOLD',
    memo: '날짜 변환 오류는 다음 단계 규칙 확장 대상.',
  },
  {
    errorId: 7086714,
    errorDt: '2026-06-29',
    procName: 'H29_ACPPEICE_TO_ARRIPCALNT_FAST',
    esbSeq: null,
    regionCd: '01',
    errorCode: '-12899',
    errorMsg: 'ORA-12899: "HIS_SU"."ARRIPCALNT"."CALC_PRSNID" 열에 대한 값이 너무 큼(실제: 9, 최대값: 7)',
    errorStack: 'ORA-06512: "SC.H29_ACPPEICE_TO_ARRIPCALNT_2", 9행',
    isHis: '1',
    status: 'OPEN',
    memo: '',
  },
  {
    errorId: 7086713,
    errorDt: '2026-06-29',
    procName: 'H29_ACPPEICE_TO_ARRIPCALNT_FAST',
    esbSeq: null,
    regionCd: '01',
    errorCode: '-1400',
    errorMsg: 'ORA-01400: NULL을 ("HIS_SU"."ARRIPCALNT"."MEDDR_ID") 안에 삽입할 수 없습니다',
    errorStack: 'ORA-06512: "SC.H29_ACPPEICE_TO_ARRIPCALNT_2", 9행',
    isHis: '1',
    status: 'OPEN',
    memo: '',
  },
  {
    errorId: 7038199,
    errorDt: '2026-06-23',
    procName: 'H99_AEMRTFUD_TO_ADPBEFVTCAP',
    esbSeq: null,
    regionCd: '03',
    errorCode: '-942',
    errorMsg: 'ORA-00942: 테이블 또는 뷰가 존재하지 않습니다',
    errorStack: 'ORA-06512: "SC.H99_AEMRTFUD_TO_ADPBEFVTCAP", 72행',
    isHis: '1',
    status: 'RESOLVED',
    memo: '배포 DB 객체 생성 누락 여부 확인 후 해결된 것으로 처리.',
  },
  {
    errorId: 7038122,
    errorDt: '2026-06-17',
    procName: 'H31_ACPPEOCE_TO_ARROPCALNT',
    esbSeq: 'H202604230021923002',
    regionCd: '01',
    errorCode: '-904',
    errorMsg: 'ORA-00904: "TG"."RPY_SEQ": 부적합한 식별자 / ESB_SEQ=H202604230021923002',
    errorStack: 'ORA-06512: "SC.H31_ACPPEOCE_TO_ARROPCALNT", 179행',
    isHis: '1',
    status: 'CHECKING',
    memo: 'TG 별칭 컬럼명 확인 필요.',
  },
  {
    errorId: 3511061,
    errorDt: '2026-06-10',
    procName: 'H31_ACPPEOCE_TO_ARROPCALNT',
    esbSeq: 'H202604040018161023',
    regionCd: '01',
    errorCode: '-1400',
    errorMsg: 'ORA-01400: NULL을 ("SC_TEST_INIF"."ARROPCALNT"."TEC_MAT_CLS") 안에 삽입할 수 없습니다 / ESB_SEQ=H202604040018161023',
    errorStack: 'ORA-06512: "SC.H31_ACPPEOCE_TO_ARROPCALNT", 179행',
    isHis: '1',
    status: 'OPEN',
    memo: '',
  },
  {
    errorId: 236,
    errorDt: '2025-12-30',
    procName: 'H26_ACPPRODM_TO_ARROPRSVNT',
    esbSeq: null,
    regionCd: '01',
    errorCode: '-1400',
    errorMsg: 'ORA-01400: NULL을 ("SC_TEST_INIF"."ARROPRSVNT"."OHSPT_IHSPT_CLS") 안에 삽입할 수 없습니다',
    errorStack: 'ORA-06512: "SC.H26_ACPPRODM_TO_ARROPRSVNT", 27행',
    isHis: '1',
    status: 'RESOLVED',
    memo: '외래/입원 구분 매핑 조건 보완 필요 사례.',
  },
  {
    errorId: 222,
    errorDt: '2025-12-30',
    procName: 'IF_HIS_ACPPRODM_TO_ARROPRSVNT',
    esbSeq: null,
    regionCd: '01',
    errorCode: '-957',
    errorMsg: 'ORA-00957: 열명이 중복되었습니다',
    errorStack: 'ORA-06512: "SC.H26_ACPPRODM_TO_ARROPRSVNT", 27행',
    isHis: '1',
    status: 'HOLD',
    memo: '초기 변환 SQL 생성 오류 유형.',
  },
  {
    errorId: 173,
    errorDt: '2025-12-20',
    procName: 'H36_PDEDBMSM_TO_CBIDEPART',
    esbSeq: null,
    regionCd: '01',
    errorCode: '-1438',
    errorMsg: 'ORA-01438: 이 열에 대해 지정된 전체 자릿수보다 큰 값이 허용됩니다',
    errorStack: 'ORA-06512: "SC.H36_PDEDBMSM_TO_CBIDEPART", 27행',
    isHis: '1',
    status: 'OPEN',
    memo: '',
  },
]

const errorCodeCounts = [
  { code: '-1722', count: 179 },
  { code: '-12899', count: 107 },
  { code: '-904', count: 91 },
  { code: '-1400', count: 38 },
  { code: '-38104', count: 27 },
  { code: '-1438', count: 27 },
  { code: '-957', count: 20 },
  { code: '-1407', count: 16 },
  { code: '-936', count: 13 },
  { code: '-1861', count: 10 },
]

const fallbackRule: ErrorRule = {
  label: '분류 대기 오류',
  severity: 'info',
  summary: '아직 상세 규칙이 등록되지 않은 오류입니다. ERROR_MSG와 ERROR_STACK을 기준으로 수동 확인이 필요합니다.',
  causeCandidates: ['신규 오류 코드이거나 빈도가 낮은 오류 유형입니다.'],
  checkItems: ['Oracle 오류 코드 문서 확인', 'ERROR_STACK의 프로시저 라인 확인', '동일 PROC_NAME의 과거 처리 이력 확인'],
  fixDirections: ['반복 발생하면 분석 규칙에 추가', '처리 메모를 남겨 다음 RAG 지식 문서로 전환'],
}

function App() {
  const [selectedId, setSelectedId] = useState(hisErrorLogs[0].errorId)
  const [selectedStatus, setSelectedStatus] = useState<ErrorFilter>('ALL')
  const [latestOnly, setLatestOnly] = useState(true)
  const [query, setQuery] = useState('')
  const [knowledgeQuestion, setKnowledgeQuestion] = useState('이 오류는 어떤 문서를 보고 어떻게 수정해야 해?')
  const [analysisResult, setAnalysisResult] = useState<KnowledgeAnalysisResult | null>(null)
  const [analysisLoading, setAnalysisLoading] = useState(false)
  const [analysisErrorMessage, setAnalysisErrorMessage] = useState<string | null>(null)
  const [operationRecord, setOperationRecord] = useState<OperationRecord | null>(null)
  const [operationStatus, setOperationStatus] = useState<ErrorStatus>('OPEN')
  const [operationMemo, setOperationMemo] = useState('')
  const [operationLoading, setOperationLoading] = useState(false)
  const [operationMessage, setOperationMessage] = useState<string | null>(null)

  const visibleLogs = useMemo(() => {
    const base = latestOnly ? getLatestLogs(hisErrorLogs) : hisErrorLogs
    return base.filter((log) => {
      const statusMatched = selectedStatus === 'ALL' || log.status === selectedStatus
      const normalizedQuery = query.trim().toLowerCase()
      const queryMatched =
        normalizedQuery.length === 0 ||
        [log.procName, log.errorCode, log.errorMsg, log.esbSeq ?? '', log.regionCd].some((value) => value.toLowerCase().includes(normalizedQuery))
      return statusMatched && queryMatched
    })
  }, [latestOnly, query, selectedStatus])

  const selectedLog = visibleLogs.find((log) => log.errorId === selectedId) ?? visibleLogs[0] ?? hisErrorLogs[0]
  const selectedRule = errorRules[selectedLog.errorCode] ?? fallbackRule
  const parsed = parseLog(selectedLog)
  const relatedDocuments = getRelatedDocuments(selectedLog, parsed)
  const relatedSnippets = getRelatedSnippets(selectedLog, relatedDocuments, knowledgeQuestion)
  const knowledgeAnswer = buildKnowledgeAnswer(selectedLog, selectedRule, relatedDocuments, relatedSnippets, knowledgeQuestion)
  const currentAnalysis = analysisResult?.errorId === selectedLog.errorId ? analysisResult : null

  useEffect(() => {
    let cancelled = false

    const loadOperationRecord = async () => {
      setOperationLoading(true)
      setOperationMessage(null)
      try {
        const response = await fetch(apiUrl(`/api/his-errors/${selectedLog.errorId}/operation`))
        if (!response.ok) {
          throw new Error(`operation request failed with ${response.status}`)
        }
        const data = (await response.json()) as OperationRecord
        if (!cancelled) {
          setOperationRecord(data)
          setOperationStatus(data.status)
          setOperationMemo(data.memo)
        }
      } catch {
        if (!cancelled) {
          const fallbackRecord = buildLocalOperationRecord(selectedLog)
          setOperationRecord(fallbackRecord)
          setOperationStatus(fallbackRecord.status)
          setOperationMemo(fallbackRecord.memo)
          setOperationMessage('백엔드 처리 이력 API에 연결하지 못해 로컬 mock 이력을 표시합니다.')
        }
      } finally {
        if (!cancelled) {
          setOperationLoading(false)
        }
      }
    }

    loadOperationRecord()

    return () => {
      cancelled = true
    }
  }, [selectedLog])

  const runKnowledgeAnalysis = async () => {
    setAnalysisLoading(true)
    setAnalysisErrorMessage(null)

    try {
      const response = await fetch(apiUrl(`/api/his-errors/${selectedLog.errorId}/analyze`), {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ question: knowledgeQuestion }),
      })
      if (!response.ok) {
        throw new Error(`HIS analysis request failed with ${response.status}`)
      }
      const data = (await response.json()) as HisAnalyzeApiResponse
      setAnalysisResult(data)
      await refreshOperationRecord(selectedLog, setOperationRecord, setOperationStatus, setOperationMemo)
    } catch {
      setAnalysisResult(buildKnowledgeAnalysisResult(selectedLog, selectedRule, relatedDocuments, relatedSnippets, knowledgeQuestion))
      setAnalysisErrorMessage('백엔드 AI 분석 API에 연결하지 못해 로컬 mock 분석으로 대체했습니다.')
    } finally {
      setAnalysisLoading(false)
    }
  }

  const saveOperationRecord = async () => {
    setOperationLoading(true)
    setOperationMessage(null)

    try {
      const response = await fetch(apiUrl(`/api/his-errors/${selectedLog.errorId}/operation`), {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ status: operationStatus, memo: operationMemo }),
      })
      if (!response.ok) {
        throw new Error(`operation update failed with ${response.status}`)
      }
      const data = (await response.json()) as OperationRecord
      setOperationRecord(data)
      setOperationStatus(data.status)
      setOperationMemo(data.memo)
      setOperationMessage('처리 상태와 운영 메모를 저장했습니다.')
    } catch {
      const fallbackRecord = {
        ...(operationRecord ?? buildLocalOperationRecord(selectedLog)),
        status: operationStatus,
        memo: operationMemo,
        updatedAt: new Date().toISOString(),
      }
      setOperationRecord(fallbackRecord)
      setOperationMessage('백엔드 저장 API에 연결하지 못해 현재 화면에만 임시 저장했습니다.')
    } finally {
      setOperationLoading(false)
    }
  }

  const summary = useMemo(() => {
    const openCount = hisErrorLogs.filter((log) => log.status === 'OPEN' || log.status === 'CHECKING').length
    const procedureCount = new Set(hisErrorLogs.map((log) => log.procName)).size
    const ruleCoverage = new Set(hisErrorLogs.filter((log) => errorRules[log.errorCode]).map((log) => log.errorCode)).size
    return { openCount, procedureCount, ruleCoverage, documentCount: knowledgeStats.totalFiles }
  }, [])

  return (
    <main className="app-shell">
      <section className="top-bar">
        <div>
          <p className="eyebrow">OpsLens HIS Mapping</p>
          <h1>운영 오류 로그 분석 대시보드</h1>
          <p className="page-description">
            실제 운영 DB 대신 HIS_MAPPING_ERROR_LOG 형태의 mock 데이터를 사용해 매일 확인하던 오류 로그 업무를 대시보드와 분석 가이드로 재구성했습니다.
          </p>
          <div className="hero-flow" aria-label="분석 흐름">
            <span>오류 로그</span>
            <span>문서 매칭</span>
            <span>AI 분석</span>
            <span>처리 이력</span>
          </div>
        </div>
      </section>

      <section className="summary-grid" aria-label="운영 오류 요약">
        <article>
          <span>미처리 오류</span>
          <strong>{summary.openCount}</strong>
        </article>
        <article>
          <span>대상 프로시저</span>
          <strong>{summary.procedureCount}</strong>
        </article>
        <article>
          <span>분석 규칙</span>
          <strong>{summary.ruleCoverage}</strong>
        </article>
        <article>
          <span>등록 문서</span>
          <strong>{summary.documentCount}</strong>
        </article>
      </section>

      <section className="dashboard-grid">
        <section className="incident-panel">
          <div className="panel-heading">
            <div>
              <h2>오류 로그 목록</h2>
              <p>날짜, 프로시저, 오류코드 기준 최신 대표 오류를 확인합니다.</p>
            </div>
          </div>

          <div className="toolbar">
            <label className="search-control">
              <span>검색</span>
              <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="PROC_NAME, ERROR_CODE, ESB_SEQ" />
            </label>
            <label className="filter-control">
              <span>상태</span>
              <select value={selectedStatus} onChange={(event) => setSelectedStatus(event.target.value as ErrorFilter)}>
                {statusOptions.map((status) => (
                  <option key={status} value={status}>
                    {status === 'ALL' ? '전체' : formatStatus(status)}
                  </option>
                ))}
              </select>
            </label>
            <label className="toggle-control">
              <input checked={latestOnly} onChange={(event) => setLatestOnly(event.target.checked)} type="checkbox" />
              최신 오류만
            </label>
          </div>

          <div className="incident-list">
            {visibleLogs.map((log) => {
              const rule = errorRules[log.errorCode] ?? fallbackRule
              return (
                <button
                  className={`incident-row ${log.errorId === selectedLog.errorId ? 'selected' : ''}`}
                  key={log.errorId}
                  onClick={() => setSelectedId(log.errorId)}
                  type="button"
                >
                  <span className={`severity ${rule.severity}`}>{rule.label}</span>
                  <span className="incident-main">
                    <strong>{log.procName}</strong>
                    <span>
                      {log.errorCode} / {log.regionCd} / {log.esbSeq ?? 'ESB_SEQ 없음'}
                    </span>
                  </span>
                  <span className="incident-meta">
                    <span className="row-status">{formatStatus(log.status)}</span>
                    <time dateTime={log.errorDt}>{formatDate(log.errorDt)}</time>
                  </span>
                </button>
              )
            })}
          </div>
        </section>

        <section className="detail-panel">
          <div className="panel-heading">
            <div>
              <h2>오류 상세 분석</h2>
              <p>ERROR_MSG와 ERROR_STACK에서 확인 포인트를 뽑고, 오류 코드별 수정 방향을 제안합니다.</p>
            </div>
          </div>

          <div className="detail-content">
            <div className="detail-title-row">
              <span className={`severity ${selectedRule.severity}`}>{selectedRule.label}</span>
              <div>
                <strong>{selectedLog.errorCode} / {selectedLog.procName}</strong>
                <p>{selectedLog.errorMsg}</p>
              </div>
            </div>

            <dl className="detail-grid">
              <div>
                <dt>ERROR_ID</dt>
                <dd>{selectedLog.errorId}</dd>
              </div>
              <div>
                <dt>ERROR_DT</dt>
                <dd>{formatDate(selectedLog.errorDt)}</dd>
              </div>
              <div>
                <dt>REGION_CD</dt>
                <dd>{selectedLog.regionCd}</dd>
              </div>
              <div>
                <dt>ESB_SEQ</dt>
                <dd>{selectedLog.esbSeq ?? '-'}</dd>
              </div>
              <div>
                <dt>대상 테이블</dt>
                <dd>{parsed.tableName ?? '-'}</dd>
              </div>
              <div>
                <dt>대상 컬럼</dt>
                <dd>{parsed.columnName ?? parsed.mergeColumn ?? '-'}</dd>
              </div>
              <div>
                <dt>발생 위치</dt>
                <dd>{parsed.stackLine ? `${parsed.stackProc ?? selectedLog.procName} ${parsed.stackLine}행` : selectedLog.errorStack}</dd>
              </div>
              <div>
                <dt>처리 상태</dt>
                <dd>{formatStatus(selectedLog.status)}</dd>
              </div>
            </dl>

            <section className="analysis-section primary-section">
              <div className="section-heading">
                <div>
                  <span className="section-kicker">Step 1</span>
                  <h3>처리 이력</h3>
                  <p>분석 결과와 운영 메모를 저장하고, 유사 오류의 이전 처리 내용을 참고합니다.</p>
                </div>
                <button className="analysis-run-button" disabled={operationLoading} onClick={saveOperationRecord} type="button">
                  {operationLoading ? '저장 중' : '처리 이력 저장'}
                </button>
              </div>
              <div className="operation-box">
                <label>
                  <span>처리 상태</span>
                  <select value={operationStatus} onChange={(event) => setOperationStatus(event.target.value as ErrorStatus)}>
                    {statusOptions
                      .filter((status): status is ErrorStatus => status !== 'ALL')
                      .map((status) => (
                        <option key={status} value={status}>
                          {formatStatus(status)}
                        </option>
                      ))}
                  </select>
                </label>
                <label className="operation-memo-control">
                  <span>운영 메모</span>
                  <textarea
                    value={operationMemo}
                    onChange={(event) => setOperationMemo(event.target.value)}
                    placeholder="확인한 원인, 수정 방향, 재처리 여부를 남겨두세요."
                  />
                </label>
                {operationMessage ? <div className="analysis-api-message">{operationMessage}</div> : null}
                {operationRecord ? <OperationHistoryPanel record={operationRecord} /> : null}
              </div>
            </section>

            <section className="analysis-section primary-section">
              <div className="section-heading">
                <div>
                  <span className="section-kicker">Step 2</span>
                  <h3>관련 문서</h3>
                  <p>프로시저명, 대상 테이블, 오류 유형을 기준으로 로컬 문서 폴더의 인터페이스 정의서와 운영 매뉴얼을 연결합니다.</p>
                </div>
              </div>
              <KnowledgeDocumentList documents={relatedDocuments} />
            </section>

            <section className="analysis-section primary-section">
              <div className="section-heading">
                <div>
                  <span className="section-kicker">Step 3</span>
                  <h3>문서 기반 분석</h3>
                  <p>선택한 오류와 관련 문서 snippet을 묶어 AI 분석 실행 흐름을 시뮬레이션합니다.</p>
                </div>
                <button className="analysis-run-button" disabled={analysisLoading} onClick={runKnowledgeAnalysis} type="button">
                  {analysisLoading ? '분석 중' : '문서 기반 분석 실행'}
                </button>
              </div>
              <div className="knowledge-question-box">
                <label>
                  <span>질문</span>
                  <input value={knowledgeQuestion} onChange={(event) => setKnowledgeQuestion(event.target.value)} />
                </label>
                {analysisErrorMessage ? <div className="analysis-api-message">{analysisErrorMessage}</div> : null}
                {currentAnalysis ? (
                  <KnowledgeAnalysisCard result={currentAnalysis} />
                ) : (
                  <article className="knowledge-answer pending">
                    <strong>분석 대기</strong>
                    <p>{knowledgeAnswer}</p>
                    <span>버튼을 누르면 이 답변 초안을 요약, 원인 후보, 확인 순서, 수정 방향으로 정리합니다.</span>
                  </article>
                )}
                <KnowledgeSnippetList snippets={currentAnalysis?.snippets ?? relatedSnippets} />
              </div>
            </section>

            <details className="collapsible-section">
              <summary>분석 가이드</summary>
              <div className="collapsible-content">
                <p className="collapsible-description">{selectedRule.summary}</p>
                <GuideList title="원인 후보" items={selectedRule.causeCandidates} />
                <GuideList title="확인할 항목" items={selectedRule.checkItems} />
                <GuideList title="수정 방향" items={selectedRule.fixDirections} />
              </div>
            </details>

            <details className="collapsible-section">
              <summary>확인 SQL 예시</summary>
              <div className="collapsible-content">
                <p className="collapsible-description">실제 DB에 바로 연결하지 않고, 운영자가 확인할 쿼리 형태만 제안합니다.</p>
                <pre>{buildSuggestedSql(selectedLog, parsed)}</pre>
              </div>
            </details>

            <details className="collapsible-section">
              <summary>전체 데이터 기준 오류 유형</summary>
              <div className="collapsible-content">
                <p className="collapsible-description">사용자가 제공한 전체 로그에서 집계한 ERROR_CODE 상위 유형입니다.</p>
                <div className="code-frequency-list">
                  {errorCodeCounts.map((item) => (
                    <div key={item.code}>
                      <span>{item.code}</span>
                      <strong>{errorRules[item.code]?.label ?? '규칙 추가 예정'}</strong>
                      <em>{item.count}건</em>
                    </div>
                  ))}
                </div>
              </div>
            </details>
          </div>
        </section>
      </section>
    </main>
  )
}

function KnowledgeAnalysisCard({ result }: { result: KnowledgeAnalysisResult }) {
  return (
    <article className="knowledge-analysis-card">
      <div className="analysis-card-header">
        <div>
          <strong>문서 기반 분석 결과</strong>
          <span>{result.mode === 'MOCK_RAG' ? 'Mock RAG' : 'LLM'}</span>
        </div>
        <time dateTime={result.generatedAt}>{formatDateTime(result.generatedAt)}</time>
      </div>
      <p>{result.answer}</p>
      <dl className="analysis-result-grid">
        <div>
          <dt>요약</dt>
          <dd>{result.summary}</dd>
        </div>
        <div>
          <dt>원인 후보</dt>
          <dd>{result.suspectedCause}</dd>
        </div>
      </dl>
      <div className="analysis-result-lists">
        <GuideList title="확인 순서" items={result.checkSteps} />
        <GuideList title="수정 방향" items={result.fixSteps} />
      </div>
      <div className="analysis-source-list">
        <strong>사용 문서 출처</strong>
        {result.documents.map((document) => (
          <span key={document.id}>{document.title}</span>
        ))}
      </div>
    </article>
  )
}

function OperationHistoryPanel({ record }: { record: OperationRecord }) {
  return (
    <div className="operation-history-panel">
      <dl className="operation-analysis-summary">
        <div>
          <dt>최근 분석 저장</dt>
          <dd>{record.analyzedAt ? formatDateTime(record.analyzedAt) : '아직 저장된 분석 결과가 없습니다.'}</dd>
        </div>
        <div>
          <dt>마지막 수정</dt>
          <dd>{formatDateTime(record.updatedAt)}</dd>
        </div>
      </dl>
      {record.lastAnalysisSummary ? (
        <article className="saved-analysis-note">
          <strong>저장된 분석 요약</strong>
          <p>{record.lastAnalysisSummary}</p>
          {record.lastAnalysisAnswer ? <small>{record.lastAnalysisAnswer}</small> : null}
        </article>
      ) : null}
      <div className="similar-history-list">
        <strong>유사 오류 처리 이력</strong>
        {record.similarHistories.length === 0 ? (
          <p>아직 연결된 유사 오류 이력이 없습니다.</p>
        ) : (
          record.similarHistories.map((history) => (
            <article key={history.errorId}>
              <div>
                <span>{history.reason}</span>
                <em>{formatStatus(history.status)}</em>
              </div>
              <strong>
                {history.errorCode} / {history.procName}
              </strong>
              <p>{history.memo || '저장된 처리 메모가 없습니다.'}</p>
              <time dateTime={history.errorDt}>{formatDate(history.errorDt)}</time>
            </article>
          ))
        )}
      </div>
    </div>
  )
}

function KnowledgeDocumentList({ documents }: { documents: KnowledgeDocument[] }) {
  if (documents.length === 0) {
    return <div className="empty-documents">연결된 문서가 없습니다. 반복 발생 오류라면 인터페이스 정의서를 인덱스에 추가하세요.</div>
  }

  return (
    <div className="knowledge-document-list">
      {documents.map((document) => (
        <article key={document.id}>
          <div>
            <span className={`document-kind ${document.kind.toLowerCase()}`}>{document.kind === 'INTERFACE' ? '정의서' : '매뉴얼'}</span>
            <strong>{document.title}</strong>
          </div>
          <p>{document.description}</p>
          <small>{document.fileName}</small>
          <div className="document-tags">
            {document.tags.slice(0, 4).map((tag) => (
              <span key={`${document.id}-${tag}`}>{tag}</span>
            ))}
          </div>
        </article>
      ))}
    </div>
  )
}

function KnowledgeSnippetList({ snippets }: { snippets: KnowledgeSnippet[] }) {
  return (
    <div className="knowledge-snippet-list">
      {snippets.map((snippet) => (
        <article key={snippet.id}>
          <strong>{snippet.title}</strong>
          <p>{snippet.content}</p>
        </article>
      ))}
    </div>
  )
}

function GuideList({ title, items }: { title: string; items: string[] }) {
  return (
    <div className="analysis-list">
      <h4>{title}</h4>
      <ul>
        {items.map((item) => (
          <li key={item}>{item}</li>
        ))}
      </ul>
    </div>
  )
}

async function refreshOperationRecord(
  log: HisErrorLog,
  setOperationRecord: (record: OperationRecord) => void,
  setOperationStatus: (status: ErrorStatus) => void,
  setOperationMemo: (memo: string) => void,
) {
  const response = await fetch(apiUrl(`/api/his-errors/${log.errorId}/operation`))
  if (!response.ok) {
    throw new Error(`operation refresh failed with ${response.status}`)
  }
  const data = (await response.json()) as OperationRecord
  setOperationRecord(data)
  setOperationStatus(data.status)
  setOperationMemo(data.memo)
}

function buildLocalOperationRecord(log: HisErrorLog): OperationRecord {
  const now = new Date().toISOString()
  return {
    errorId: log.errorId,
    status: log.status,
    memo: log.memo,
    lastAnalysisSummary: '',
    lastAnalysisAnswer: '',
    analyzedAt: null,
    updatedAt: now,
    similarHistories: getSimilarHistories(log),
  }
}

function getSimilarHistories(selectedLog: HisErrorLog): SimilarHistory[] {
  const selectedProc = selectedLog.procName.replace('_FAST', '')
  return hisErrorLogs
    .filter((log) => log.errorId !== selectedLog.errorId)
    .map((log) => {
      const proc = log.procName.replace('_FAST', '')
      let reason: string | null = null
      if (proc === selectedProc && log.errorCode === selectedLog.errorCode) {
        reason = '같은 프로시저와 같은 오류코드'
      } else if (proc === selectedProc) {
        reason = '같은 프로시저'
      } else if (log.errorCode === selectedLog.errorCode) {
        reason = '같은 오류코드'
      }
      return reason
        ? {
            errorId: log.errorId,
            errorDt: log.errorDt,
            procName: log.procName,
            errorCode: log.errorCode,
            status: log.status,
            memo: log.memo,
            reason,
          }
        : null
    })
    .filter((history): history is SimilarHistory => history !== null)
    .slice(0, 4)
}

function getLatestLogs(logs: HisErrorLog[]) {
  const latest = new Map<string, HisErrorLog>()
  logs.forEach((log) => {
    const key = `${log.errorDt}|${log.procName}|${log.errorCode}`
    const current = latest.get(key)
    if (!current || log.errorId > current.errorId) {
      latest.set(key, log)
    }
  })
  return Array.from(latest.values()).sort((a, b) => b.errorId - a.errorId)
}

function parseLog(log: HisErrorLog) {
  const objectMatch = log.errorMsg.match(/"[^"]+"\."([^"]+)"\."([^"]+)"/)
  const mergeMatch = log.errorMsg.match(/"TG"\."([^"]+)"/)
  const stackMatch = log.errorStack.match(/"SC\.([^"]+)",\s*(\d+)행/)

  return {
    tableName: objectMatch?.[1] ?? null,
    columnName: objectMatch?.[2] ?? null,
    mergeColumn: mergeMatch?.[1] ?? null,
    stackProc: stackMatch?.[1] ?? null,
    stackLine: stackMatch?.[2] ?? null,
  }
}

function getRelatedDocuments(log: HisErrorLog, parsed: ReturnType<typeof parseLog>) {
  const procName = log.procName.replace('_FAST', '')
  const tableName = parsed.tableName ?? ''
  const scored = knowledgeDocuments
    .map((document) => {
      let score = 0
      if (document.relatedProcedures.some((procedure) => procName.includes(procedure) || procedure.includes(procName))) score += 5
      if (document.relatedTables.some((table) => tableName.includes(table) || log.errorMsg.includes(table))) score += 4
      if (document.tags.some((tag) => procName.includes(tag) || log.errorMsg.includes(tag))) score += 2
      if (document.kind === 'MANUAL') score += log.status === 'RESOLVED' ? 2 : 1
      if (document.id === 'deployment-manual' && ['-904', '-936', '-957', '-942', '-38104'].includes(log.errorCode)) score += 1
      if (document.id === 'service-start-stop-manual' && log.status === 'RESOLVED') score += 1
      return { document, score }
    })
    .filter((item) => item.score > 0)
    .sort((a, b) => b.score - a.score)

  return scored.slice(0, 4).map((item) => item.document)
}

function getRelatedSnippets(log: HisErrorLog, documents: KnowledgeDocument[], question: string) {
  const documentIds = new Set(documents.map((document) => document.id))
  const questionTerms = question.toLowerCase().split(/\s+/).filter(Boolean)
  return knowledgeSnippets
    .map((snippet) => {
      let score = documentIds.has(snippet.documentId) ? 5 : 0
      if (snippet.keywords.some((keyword) => log.procName.includes(keyword) || log.errorMsg.includes(keyword) || log.errorCode === keyword)) score += 4
      if (snippet.keywords.some((keyword) => questionTerms.some((term) => keyword.toLowerCase().includes(term) || term.includes(keyword.toLowerCase())))) score += 2
      return { snippet, score }
    })
    .filter((item) => item.score > 0)
    .sort((a, b) => b.score - a.score)
    .slice(0, 3)
    .map((item) => item.snippet)
}

function buildKnowledgeAnswer(
  log: HisErrorLog,
  rule: ErrorRule,
  documents: KnowledgeDocument[],
  snippets: KnowledgeSnippet[],
  question: string,
) {
  const primaryDocument = documents[0]
  const primarySnippet = snippets[0]
  const documentTitle = primaryDocument ? primaryDocument.title : '관련 인터페이스 정의서'
  const snippetSummary = primarySnippet ? primarySnippet.content : rule.summary
  const trimmedQuestion = question.trim() || '이 오류는 어떻게 확인해야 해?'

  return `${trimmedQuestion} 선택된 오류는 ${log.procName}에서 발생한 ${rule.label}입니다. 먼저 ${documentTitle}를 기준으로 대상 컬럼의 필수 여부, 길이, 매핑 규칙을 확인하세요. ${snippetSummary} 이후 ERROR_STACK의 라인과 ESB_SEQ 기준 원천 데이터를 대조하고, 수정이 프로시저나 DDL 변경이라면 배포 매뉴얼의 운영 반영 절차를 함께 확인하는 흐름이 적절합니다.`
}

function buildKnowledgeAnalysisResult(
  log: HisErrorLog,
  rule: ErrorRule,
  documents: KnowledgeDocument[],
  snippets: KnowledgeSnippet[],
  question: string,
): KnowledgeAnalysisResult {
  const answer = buildKnowledgeAnswer(log, rule, documents, snippets, question)
  const primaryDocument = documents[0]
  const primarySnippet = snippets[0]

  return {
    errorId: log.errorId,
    question: question.trim() || '이 오류는 어떻게 확인해야 해?',
    answer,
    summary: `${log.errorCode} ${rule.label}가 ${log.procName}에서 발생했습니다. ${primaryDocument ? primaryDocument.title : '관련 정의서'} 기준으로 매핑 규칙을 먼저 확인합니다.`,
    suspectedCause: primarySnippet
      ? primarySnippet.content
      : `${rule.causeCandidates[0]} 가능성이 높습니다. 관련 문서가 부족하면 처리 이력을 추가해 지식 인덱스를 확장합니다.`,
    checkSteps: [
      `ERROR_STACK 기준 ${log.procName} 프로시저 발생 라인을 확인합니다.`,
      log.esbSeq ? `ESB_SEQ=${log.esbSeq} 원천 데이터를 조회합니다.` : 'ESB_SEQ가 없으므로 발생 일자와 프로시저 기준 원천 데이터를 좁혀 확인합니다.',
      primaryDocument ? `${primaryDocument.title}에서 대상 항목의 필수 여부, 길이, 매핑 규칙을 확인합니다.` : '관련 인터페이스 정의서를 추가로 연결합니다.',
      '확인 SQL 예시를 기준으로 대상 컬럼 정의와 원천 값을 비교합니다.',
    ],
    fixSteps: [
      ...rule.fixDirections.slice(0, 2),
      '프로시저 또는 DDL 변경이 필요하면 배포 매뉴얼 기준으로 반영 절차와 롤백 가능성을 확인합니다.',
      '해결 후 처리 메모를 남겨 다음 유사 오류의 검색 지식으로 재사용합니다.',
    ],
    documents,
    snippets,
    generatedAt: new Date().toISOString(),
    mode: 'MOCK_RAG',
  }
}

function buildSuggestedSql(log: HisErrorLog, parsed: ReturnType<typeof parseLog>) {
  const table = parsed.tableName ?? 'TARGET_TABLE'
  const column = parsed.columnName ?? parsed.mergeColumn ?? 'TARGET_COLUMN'
  const esbSeq = log.esbSeq ?? ':ESB_SEQ'

  if (log.errorCode === '-1400' || log.errorCode === '-12899' || log.errorCode === '-1407') {
    return `-- 원천 데이터 확인\nSELECT *\nFROM SOURCE_TABLE\nWHERE ESB_SEQ = '${esbSeq}';\n\n-- 대상 컬럼 정의 확인\nSELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, DATA_LENGTH, NULLABLE\nFROM USER_TAB_COLUMNS\nWHERE TABLE_NAME = '${table}'\n  AND COLUMN_NAME = '${column}';`
  }

  if (log.errorCode === '-1722') {
    return `-- 숫자 변환 대상 값 확인\nSELECT *\nFROM SOURCE_TABLE\nWHERE ESB_SEQ = '${esbSeq}'\n  AND REGEXP_LIKE(TARGET_VALUE, '[^0-9.]');\n\n-- 프로시저 라인 확인\nSELECT LINE, TEXT\nFROM USER_SOURCE\nWHERE NAME = '${log.procName}'\nORDER BY LINE;`
  }

  if (log.errorCode === '-38104') {
    return `-- MERGE 구문 확인\nSELECT LINE, TEXT\nFROM USER_SOURCE\nWHERE NAME = '${log.procName}'\n  AND (UPPER(TEXT) LIKE '%MERGE%' OR UPPER(TEXT) LIKE '%${column}%')\nORDER BY LINE;`
  }

  return `-- 프로시저 소스 확인\nSELECT LINE, TEXT\nFROM USER_SOURCE\nWHERE NAME = '${log.procName}'\nORDER BY LINE;`
}

function formatStatus(status: ErrorFilter) {
  const labels: Record<ErrorFilter, string> = {
    ALL: '전체',
    OPEN: '미확인',
    CHECKING: '확인중',
    RESOLVED: '해결',
    HOLD: '보류',
  }
  return labels[status]
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium' }).format(new Date(value))
}

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat('ko-KR', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value))
}

export default App














