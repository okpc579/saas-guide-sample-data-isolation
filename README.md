# saas-guide-sample-data-isolation

## 1. 프로젝트 요약

이 저장소는 **3.3 공유 스키마 데이터 격리**를 독립적으로 학습하는 교육·참조용 Spring Boot 프로젝트다. 참고 기준은 `okpc579/saas-sample`의 `560bd8b0eb3686121055dd2a65432cc1d8e70b80`이며, 통합 프로젝트를 복사하지 않고 단일 `service_request` 업무로 재구성했다. 운영용 SaaS 완성 구현이 아니다.

> 참고 저장소 원격 조회는 작업 환경의 네트워크 제한(HTTP 403)으로 실패했다. 따라서 제공된 장별 요구사항과 SHA를 설계 기준으로 삼았으며, 원본의 세부 명명은 복제하지 않았다.

## 2. 핵심 설계 개념

1. 공유 테이블의 모든 행은 `tenant_id`를 가진다.
2. 서버가 `X-Tenant-Id`와 `X-User-Id`로 만든 최소 `TenantContext`만 신뢰한다. 이는 인증 자체를 시연하는 장이 아니며, 실제 환경에서는 인증 계층이 이 헤더를 검증·확정해야 한다.
3. 목록은 `tenant_id`, 단건/수정/삭제는 `tenant_id + id`로 조회한다. 다른 테넌트 행은 존재하더라도 동일한 `404 RESOURCE_NOT_FOUND`라서 존재 여부가 노출되지 않는다.
4. 등록 DTO에는 `tenantId`가 없고 알 수 없는 필드를 거부한다. 저장값은 서버 컨텍스트에서 가져온다.
5. `(tenant_id, request_no)` 유일성으로 테넌트 내부 중복만 차단한다.
6. PostgreSQL RLS는 애플리케이션 쿼리 조건을 대체하지 않는 **보완 통제**다. 서비스 트랜잭션 시작 후 `set_config(..., true)`로 트랜잭션 로컬 테넌트를 지정한다.

## 3. 범위

### 포함

- 최소 요청 범위 Tenant Context와 공통 오류 응답
- `service_request` 등록, 목록, 단건, 수정, 삭제
- tenant-scoped Repository 메서드와 서비스 트랜잭션 경계
- PostgreSQL Flyway V1, 복합 유일성/인덱스, RLS `USING`/`WITH CHECK`
- H2 전용 테스트 migration, MockMvc 격리 테스트, RLS 정적 계약 테스트
- RLS 검증/정리 SQL 및 대상 장 전용 Postman 파일

### 의도적으로 제외

3.2의 토큰 발급·OAuth2·사용자/소속/테넌트 상태 관리, 이벤트/DLQ, Outbox/Saga/보상, 분리 스키마·DB, 동적 DataSource 라우팅을 제외했다. 이들은 3.3의 행 단위 격리 설명에 필요하지 않다. 별도 YAML 인프라 파일과 Docker Compose도 로컬 PostgreSQL을 새로 운영하는 것이 핵심이 아니므로 추가하지 않았다.

## 4. 기술 환경과 구조

- Java 21, Spring Boot 3.4.7, Maven Wrapper
- Spring Web, Data JPA, Flyway, PostgreSQL; 테스트는 H2

```text
com.example.dataisolation
├── api                 공통 오류 응답/예외 변환
├── tenant              최소 TenantContext와 요청 필터
└── dataisolation       Entity, tenant-scoped Repository, Service, Controller, RLS 세션 설정
```

서비스의 각 public 작업은 하나의 읽기 또는 쓰기 트랜잭션이다. 중복 DB 제약 위반은 409로, tenant-scoped 조회 실패는 404로 변환한다.

## 5. 처리 흐름

```text
HTTP 요청
→ 신뢰 경계에서 확정되었다고 가정한 X-Tenant-Id / X-User-Id 확인
→ 요청 범위 TenantContext 생성
→ @Transactional 서비스가 PostgreSQL 트랜잭션 로컬 app.current_tenant 설정
→ Repository가 tenant_id(및 id) 조건 적용
→ PostgreSQL RLS가 USING / WITH CHECK로 보완 검증
→ 응답 후 ThreadLocal 정리
```

## 6. 실행과 환경변수

테스트(외부 DB 불필요):

```bash
./mvnw clean test
```

PostgreSQL 데이터베이스와 비소유권 애플리케이션 역할을 준비한 뒤 실행한다.

```bash
export DB_URL='jdbc:postgresql://localhost:5432/saas_sample'
export DB_USERNAME='saas_sample_app'
export DB_PASSWORD='local-demo-password' # 로컬 데모 값
./mvnw spring-boot:run
```

| 변수 | 기본값 | 설명 |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/saas_sample` | PostgreSQL JDBC URL |
| `DB_USERNAME` | `saas_sample_app` | RLS가 적용되는 데모 애플리케이션 역할 |
| `DB_PASSWORD` | `local-demo-password` | 로컬 데모 비밀번호(운영 비밀 아님) |

기본 `application.yaml`은 PostgreSQL/RLS를 켠다. `application-test.yaml`은 H2와 H2 전용 V1을 선택하고 PostgreSQL 전용 `set_config`를 끈다. H2는 RLS를 구현하지 않으므로 자동 테스트는 애플리케이션 조건을 실제로 검증하고, RLS는 SQL 계약 테스트와 PostgreSQL 수동 SQL로 검증한다.

## 7. API와 실행 순서

모든 요청에 `X-Tenant-Id`, `X-User-Id`가 필요하며 본문에는 tenant ID를 보내지 않는다.

| 순서 | API | 역할 / 기대 결과 |
|---|---|---|
| 1 | `POST /api/data-isolation/service-requests` | 등록, 201; `requestNo`, `title`만 수신 |
| 2 | `GET /api/data-isolation/service-requests` | 현재 테넌트 목록, 200 |
| 3 | `GET /api/data-isolation/service-requests/{id}` | tenant+id 단건, 타 테넌트는 404 |
| 4 | `PUT /api/data-isolation/service-requests/{id}` | tenant+id 수정 |
| 5 | `DELETE /api/data-isolation/service-requests/{id}` | tenant+id 삭제, 204 |

| 검증 | 기대 결과 |
|---|---|
| 현재 테넌트 등록/조회 | 서버 tenant ID 저장, 201/200 |
| 두 테넌트의 같은 업무번호 | 각각 201 |
| 같은 테넌트의 중복 업무번호 | 409 `DUPLICATE_REQUEST_NO` |
| 타 테넌트 ID 조회/수정/삭제 | 404 `RESOURCE_NOT_FOUND` |
| 본문의 `tenantId` | 400 `INVALID_REQUEST` |
| Tenant 헤더 누락 | 400 `TENANT_HEADER_REQUIRED` |

## 8. SQL

- `src/main/resources/db/migration/postgresql/V1__create_service_request_with_rls.sql`: 테이블, 상태 CHECK, `(tenant_id, request_no)` 유일성, tenant/created 인덱스, 강제 RLS 정책을 한 독립 V1에 구성한다.
- `src/test/resources/db/migration/h2/V1__create_service_request.sql`: H2가 이해하는 동일 테이블/제약 구조다.
- `db/verify-data-isolation.sql`: `psql`에서 tenant-a/tenant-b 컨텍스트를 전환해 가시성과 다른 tenant INSERT 차단을 수동 확인한다.
- `db/cleanup-demo-data.sql`: 각 tenant 컨텍스트에서 데모 행을 정리한다.

```bash
psql "$DB_URL" -U "$DB_USERNAME" -f db/verify-data-isolation.sql
psql "$DB_URL" -U "$DB_USERNAME" -f db/cleanup-demo-data.sql
```

RLS는 테이블 소유자/BYPASSRLS 권한, connection pool 세션 누수, migration 역할과 런타임 역할의 분리까지 고려해야 한다. 이 예제는 `FORCE ROW LEVEL SECURITY`와 트랜잭션 로컬 설정을 사용하지만 운영 권한 설계를 대신하지 않는다.

## 9. Postman

`postman/data-isolation.postman_collection.json`과 `postman/local.postman_environment.json`을 import하고 environment를 선택한 다음 애플리케이션을 실행한다. Collection Runner에서 `01. 정상 처리` 후 `02. 격리 및 오류` 순서로 실행한다. 최초 등록이 `request_id`를 자동 저장하며 상태 코드, tenant ID, 오류 코드를 검사한다. RLS 자체는 Postman만으로 애플리케이션 쿼리 조건과 구별할 수 없으므로 위 수동 SQL 또는 PostgreSQL 통합 환경에서 확인한다.

## 10. 참고 구현과의 차이 및 운영 고려사항

독립 교육 예제를 위해 업무를 `service_request` 하나로 제한하고, 상세 인증 대신 두 검증 완료 헤더를 가정했다. 원격 원본을 확인하지 못했으므로 API/패키지 이름은 요구사항에 맞춰 새로 설계했다. 운영 적용에는 외부 IdP와 헤더 위조 차단, 사용자-tenant 소속 검증, 비밀 관리자, DB 역할/권한 분리, connection pool 초기화, 입력 Bean Validation, optimistic locking, 감사 로그, 관측성, PostgreSQL Testcontainers 통합 테스트, 백업·보존·고가용성 정책이 추가로 필요하다.
