# 공유 스키마 데이터 격리 예제

## 1. 예제 목적

이 저장소(`saas-guide-sample-data-isolation`)는 여러 테넌트가 하나의 PostgreSQL 스키마와 `service_request` 테이블을 공유할 때 행 단위로 데이터를 격리하는 방법을 보여 주는 Spring Boot 교육 예제다. 운영용 SaaS 완성 구현은 아니다.

예제에서는 애플리케이션의 tenant 조건과 PostgreSQL Row-Level Security(RLS)를 함께 사용한다. 자동 테스트는 외부 PostgreSQL 없이 H2로 애플리케이션 격리를 확인하고, PostgreSQL 전용 RLS는 migration 계약 테스트와 수동 SQL로 확인한다.

## 2. 확인할 설계 내용

1. 모든 업무 행에 `tenant_id`를 저장한다.
2. 서버가 `X-Tenant-Id`, `X-User-Id` 헤더로 요청 범위 `TenantContext`를 만들고 응답 후 `ThreadLocal`을 정리한다.
3. 목록은 `tenant_id`, 단건·수정·삭제는 `tenant_id + id`로 조회한다. 따라서 다른 테넌트의 ID를 사용해도 `404 RESOURCE_NOT_FOUND`만 반환한다.
4. 등록 본문에는 `tenantId`를 받지 않으며, 저장할 테넌트는 서버 컨텍스트에서 가져온다. 알 수 없는 JSON 필드도 거부한다.
5. `(tenant_id, request_no)` 유일성 제약으로 같은 테넌트의 업무번호 중복만 차단한다.
6. 각 서비스 트랜잭션에서 `app.current_tenant`를 transaction-local 값으로 설정하고 RLS의 `USING`과 `WITH CHECK`를 보완 통제로 적용한다.

## 3. 구성 요소와 처리 흐름

- Java 21, Spring Boot 3.4.7, Maven Wrapper
- Spring Web, Spring Data JPA, Flyway, PostgreSQL
- 자동 테스트용 H2와 MockMvc
- 기본 애플리케이션 포트: `8080`

```text
HTTP 요청
  → X-Tenant-Id / X-User-Id 확인
  → 요청 범위 TenantContext 생성
  → @Transactional 서비스가 app.current_tenant 설정
  → tenant 조건이 명시된 Repository 실행
  → PostgreSQL RLS가 행 접근을 추가 검사
  → HTTP 응답 및 TenantContext 정리
```

주요 파일은 다음과 같다.

| 파일 | 역할 |
|---|---|
| `src/main/resources/application.yaml` | PostgreSQL 연결, Flyway 위치, RLS 활성화 설정 |
| `src/main/resources/db/migration/postgresql/V1__create_service_request_with_rls.sql` | 테이블·인덱스·유일성·RLS 정책 생성 |
| `src/test/resources/application-test.yaml` | H2 테스트 데이터베이스와 RLS 비활성화 설정 |
| `db/create-local-database.sql` | 로컬 데모 역할과 데이터베이스 준비 |
| `db/verify-data-isolation.sql` | PostgreSQL RLS 가시성 및 쓰기 차단 수동 확인 |
| `db/cleanup-demo-data.sql` | 두 데모 테넌트의 행 삭제 |
| `postman/` | Collection과 로컬 Environment |

## 4. 사전 준비

- JDK 21 (`java -version`으로 확인)
- 로컬에서 실행 중인 PostgreSQL과 `psql` 클라이언트
- Postman(수동 Collection 검증을 할 경우)

Maven은 별도 설치하지 않아도 저장소의 Maven Wrapper(`mvnw`)를 사용한다. 아래 명령은 저장소 루트에서 실행하며 Windows에서도 **Git Bash**를 사용하면 동일한 `./mvnw`, `export` 구문을 사용할 수 있다.

PostgreSQL 기본 포트 `5432` 또는 애플리케이션 포트 `8080`을 이미 다른 프로세스가 사용 중이지 않은지 확인한다. 이 저장소에는 Docker Compose 파일이 없으므로 PostgreSQL 컨테이너를 자동으로 시작하지 않는다.

## 5. 가장 빠른 실행 방법

### 단계 1. 자동 테스트로 코드 확인

목적:
외부 데이터베이스 없이 H2 migration, tenant별 조회, 타 tenant 접근 차단, 중복 업무번호, 헤더와 본문 검증을 먼저 확인한다.

명령:

```bash
./mvnw clean test
```

### 단계 2. 로컬 PostgreSQL 준비

목적:
기본 설정이 사용하는 로그인 역할 `saas_sample_app`과 데이터베이스 `saas_sample`을 만든다. 이 작업은 PostgreSQL 관리자 권한이 필요하며 최초 한 번만 실행하면 된다.

명령:

```bash
psql -U postgres -d postgres -f db/create-local-database.sql
```

### 단계 3. 애플리케이션 실행

목적:
PostgreSQL에 연결하고 Flyway V1을 적용한 뒤 HTTP API를 `http://localhost:8080`에 연다.

명령:

```bash
export DB_URL='jdbc:postgresql://localhost:5432/saas_sample'
export DB_USERNAME='saas_sample_app'
export DB_PASSWORD='local-demo-password'
./mvnw spring-boot:run
```

로그에 애플리케이션 시작 완료가 표시될 때까지 기다린다. 이 터미널은 실행 상태로 두고, 이후 `curl`, `psql`, Postman 명령은 새 터미널에서 저장소 루트로 이동한 후 실행한다.

## 6. 상세 실행 절차

### 단계 1. 데이터베이스 생성 전제 확인

목적:
PostgreSQL 서버 접속과 관리자 계정을 확인한다. 기본 예제 이외의 서버를 쓴다면 관리자 호스트·포트 옵션을 환경에 맞게 `psql` 명령에 추가한다.

명령:

```bash
psql -U postgres -d postgres -c 'select version();'
```

### 단계 2. 데모 역할과 데이터베이스 생성

목적:
`db/create-local-database.sql`을 관리자 DB에서 실행한다. 스크립트는 역할이나 데이터베이스가 이미 있으면 다시 만들지 않으며, 애플리케이션 테이블은 만들지 않는다.

명령:

```bash
psql -U postgres -d postgres -f db/create-local-database.sql
```

### 단계 3. 연결 환경변수 설정 및 서버 시작

목적:
애플리케이션 역할로 접속한다. 시작 과정의 Flyway가 `service_request` 테이블과 RLS 정책을 자동 생성하고 Hibernate가 스키마를 검증한다.

명령:

```bash
export DB_URL='jdbc:postgresql://localhost:5432/saas_sample'
export DB_USERNAME='saas_sample_app'
export DB_PASSWORD='local-demo-password'
./mvnw spring-boot:run
```

이미 준비된 다른 PostgreSQL을 사용한다면 생성 스크립트는 생략하고 위 세 변수만 실제 JDBC URL과 자격 증명으로 바꾼다. 런타임 역할은 migration을 수행할 테이블 생성 권한과 이후 RLS 정책을 적용받을 권한이 있어야 한다.

### 단계 4. API 등록과 조회 확인

목적:
`tenant-a`의 요청을 만들고 같은 테넌트의 목록에만 나타나는지 확인한다. 응답의 `id`는 다음 단건·수정·삭제 요청 경로에 사용할 수 있다.

명령:

```bash
curl -i -X POST 'http://localhost:8080/api/data-isolation/service-requests' \
  -H 'X-Tenant-Id: tenant-a' \
  -H 'X-User-Id: user-a' \
  -H 'Content-Type: application/json' \
  -d '{"requestNo":"CURL-001","title":"격리 확인"}'

curl -i 'http://localhost:8080/api/data-isolation/service-requests' \
  -H 'X-Tenant-Id: tenant-a' \
  -H 'X-User-Id: user-a'
```

등록은 `201 Created`, 목록은 `200 OK`이고 반환 행의 `tenantId`는 `tenant-a`여야 한다. 같은 `requestNo`를 다시 등록하면 의도한 `409 DUPLICATE_REQUEST_NO`가 발생하므로 재실행 전에는 11절의 정리를 수행하거나 다른 번호를 사용한다.

### 단계 5. 나머지 API 이해

목적:
등록 응답의 실제 UUID를 `<id>`에 넣어 tenant-scoped 단건 조회, 수정, 삭제를 확인한다.

명령:

```bash
curl -i 'http://localhost:8080/api/data-isolation/service-requests/<id>' \
  -H 'X-Tenant-Id: tenant-a' -H 'X-User-Id: user-a'

curl -i -X PUT 'http://localhost:8080/api/data-isolation/service-requests/<id>' \
  -H 'X-Tenant-Id: tenant-a' -H 'X-User-Id: user-a' \
  -H 'Content-Type: application/json' \
  -d '{"title":"수정 완료","status":"IN_PROGRESS"}'

curl -i -X DELETE 'http://localhost:8080/api/data-isolation/service-requests/<id>' \
  -H 'X-Tenant-Id: tenant-a' -H 'X-User-Id: user-a'
```

각 결과는 차례로 `200`, `200`, `204 No Content`다. 상태값은 `OPEN`, `IN_PROGRESS`, `CLOSED` 중 하나다. 다른 tenant 헤더로 같은 `<id>`를 조회·수정·삭제하면 모두 `404`가 된다.

## 7. Postman 검증 절차

### 단계 1. Collection과 Environment 가져오기

목적:
Postman에 실제 제공 파일을 import하고 요청 대상과 공통 헤더 변수를 준비한다.

가져올 파일:

- `postman/data-isolation.postman_collection.json`
- `postman/local.postman_environment.json`

Environment에서 **data-isolation-local**을 선택한다. 정의된 변수는 다음과 같다.

| 변수 | 초기값 | 사용처 |
|---|---|---|
| `base_url` | `http://localhost:8080` | 모든 API 주소 |
| `tenant_id` | `tenant-a` | 정상 요청의 `X-Tenant-Id`와 검증값 |
| `user_id` | `user-a` | `X-User-Id` |
| `request_id` | 빈 값 | 등록 요청이 응답의 UUID를 자동 저장하고 이후 조회가 사용 |

### 단계 2. 기존 데모 데이터 정리

목적:
Collection이 고정 업무번호 `POSTMAN-001`을 사용하므로 이전 실행의 행 때문에 첫 등록이 `409`가 되는 것을 방지한다. 애플리케이션이 한 번 실행되어 Flyway 테이블이 생성된 뒤 수행한다.

명령:

```bash
PGPASSWORD="$DB_PASSWORD" psql -h localhost -p 5432 -U "$DB_USERNAME" -d saas_sample \
  -f db/cleanup-demo-data.sql
```

다른 DB 접속값을 사용했다면 `-h`, `-p`, `-d`도 해당 JDBC URL에 맞춘다. `DB_URL`은 JDBC 형식이므로 `psql`의 접속 문자열로 직접 넘기지 않는다.

### 단계 3. `01. 정상 처리` 폴더 실행

목적:
Collection Runner에서 실제 폴더 이름 **`01. 정상 처리`**를 먼저 실행한다.

폴더 요청은 다음 순서다.

1. **`01. 요청 등록`**: `tenant-a`에 `POSTMAN-001`을 등록하고 `201`, 응답 `tenantId`를 검사한다. 응답 `id`를 Environment의 `request_id`에 자동 저장한다.
2. **`02. 현재 테넌트 목록`**: 목록이 `200`이고 모든 항목의 `tenantId`가 `tenant_id`와 같은지 검사한다.
3. **`03. 단건 조회`**: 저장된 `request_id`로 조회하여 `200`을 검사한다.

Runner에서 폴더 요청 순서를 유지해야 한다. 첫 요청을 건너뛰면 `request_id`가 비어 있어 단건 조회가 올바르게 실행되지 않는다.

### 단계 4. `02. 격리 및 오류` 폴더 실행

목적:
정상 폴더가 만든 행과 `request_id`를 사용해 격리 및 입력 오류를 검증한다. 반드시 **`01. 정상 처리` 다음에** 실행한다.

1. **`01. 다른 테넌트에서 같은 번호 등록`**: `tenant-b`에도 `POSTMAN-001`을 등록할 수 있는지 `201`로 확인한다.
2. **`02. 같은 테넌트 중복 번호`**: `tenant-a`의 동일 번호 등록이 `409 DUPLICATE_REQUEST_NO`인지 확인한다.
3. **`03. 다른 테넌트 ID 조회`**: `tenant-a`에서 만든 `request_id`를 `tenant-b`로 조회할 때 `404 RESOURCE_NOT_FOUND`인지 확인한다.
4. **`04. 본문 tenantId 거부`**: 클라이언트가 본문에 `tenantId`를 주입하면 `400 INVALID_REQUEST`인지 확인한다.

`409`, `404`, `400`은 이 폴더가 검증하려는 정상적인 실패 응답이다. 전체 Collection을 다시 실행하려면 11절의 cleanup을 먼저 수행한다.

## 8. 자동 테스트

### 단계 1. 전체 테스트 실행

목적:
테스트 프로필의 인메모리 H2에 H2 전용 Flyway V1을 적용한 뒤 모든 테스트를 실행한다. 로컬 PostgreSQL이나 실행 중인 애플리케이션은 필요 없다.

명령:

```bash
./mvnw clean test
```

`ServiceRequestIsolationTest`는 서버 컨텍스트 기반 저장, tenant 목록 격리, 타 tenant의 조회·수정·삭제 `404`, tenant별 중복, 알 수 없는 본문 필드와 필수 헤더를 검증한다. `RepositoryContractTest`는 tenant-scoped Repository 메서드와 PostgreSQL migration의 RLS 구문을 정적으로 검증한다.

주의: H2에는 PostgreSQL RLS가 없다. 따라서 이 명령의 성공만으로 실제 RLS 동작까지 검증된 것은 아니며, 10절의 수동 SQL을 별도로 실행해야 한다.

## 9. 설정과 환경변수

| 변수 | 기본값 | 설명 |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/saas_sample` | PostgreSQL JDBC URL |
| `DB_USERNAME` | `saas_sample_app` | 애플리케이션 및 기본 migration 역할 |
| `DB_PASSWORD` | `local-demo-password` | 로컬 데모 전용 비밀번호 |

`src/main/resources/application.yaml`은 PostgreSQL migration 위치와 `demo.rls.enabled: true`를 설정한다. 서비스는 이 값이 켜진 경우 각 트랜잭션에 `set_config('app.current_tenant', ..., true)`를 적용한다.

테스트 프로필은 `src/test/resources/application-test.yaml`에서 H2 migration을 선택하고 `demo.rls.enabled: false`로 PostgreSQL 전용 호출을 끈다. 코드에 환경변수로 노출되지 않은 설정을 실행 절차에서 임의로 가정하지 않는다.

## 10. 데이터베이스·YAML·Docker 관련 절차

### 단계 1. SQL 적용 순서 이해

목적:
수동 SQL과 Flyway의 적용 주체 및 순서를 혼동하지 않도록 한다.

1. PostgreSQL 관리자로 `db/create-local-database.sql`을 실행한다.
2. 애플리케이션을 시작한다. Flyway가 `src/main/resources/db/migration/postgresql/V1__create_service_request_with_rls.sql`을 자동 적용한다. 이 migration 파일을 별도로 `psql -f`로 먼저 적용하지 않는다.
3. API 또는 Postman으로 tenant-a/tenant-b 데이터를 만든다.
4. 애플리케이션 역할로 `db/verify-data-isolation.sql`을 실행한다.
5. 필요하면 `db/cleanup-demo-data.sql`을 실행한다.

### 단계 2. PostgreSQL RLS 수동 확인

목적:
tenant 컨텍스트를 바꿀 때 SELECT 결과가 분리되고 다른 tenant 행 INSERT가 `WITH CHECK`에 의해 거부되는지 실제 PostgreSQL에서 확인한다.

명령:

```bash
PGPASSWORD="$DB_PASSWORD" psql -h localhost -p 5432 -U "$DB_USERNAME" -d saas_sample \
  -f db/verify-data-isolation.sql
```

스크립트의 첫 트랜잭션에서 tenant-a 행만 보인 뒤, tenant-a 컨텍스트로 `tenant-b` 행을 넣는 문장은 의도적으로 RLS 오류가 나야 한다. 스크립트가 `ROLLBACK`한 다음 tenant-b 컨텍스트의 SELECT에는 tenant-b 행만 보여야 한다. 검증용 INSERT는 롤백되므로 별도 정리 대상이 아니다.

### 단계 3. YAML과 Docker 범위 확인

목적:
이 저장소에서 실제로 적용할 인프라 파일의 범위를 명확히 한다.

`application.yaml`과 `application-test.yaml`은 Spring Boot가 자동으로 읽으므로 `kubectl apply` 같은 별도 명령을 실행하지 않는다. Kubernetes YAML과 Docker Compose 파일은 저장소에 없으며 Docker 관련 실행 절차도 없다. PostgreSQL은 사용자가 미리 실행해 두어야 한다.

## 11. 초기화와 정리

### 단계 1. 데모 행 초기화

목적:
tenant-a와 tenant-b의 현재 RLS 컨텍스트에서 보이는 모든 `service_request` 행을 삭제하여 curl/Postman 시나리오를 다시 실행할 수 있게 한다.

명령:

```bash
PGPASSWORD="$DB_PASSWORD" psql -h localhost -p 5432 -U "$DB_USERNAME" -d saas_sample \
  -f db/cleanup-demo-data.sql
```

이 스크립트는 두 tenant의 행만 정리하며 데이터베이스, 역할, Flyway 이력, 테이블은 삭제하지 않는다. Postman의 `request_id`는 다음 정상 등록 요청이 새 값으로 덮어쓴다.

### 단계 2. 애플리케이션 종료

목적:
Spring Boot 프로세스와 포트 `8080` 사용을 종료한다.

명령:

```bash
# spring-boot:run을 실행한 터미널에서
Ctrl+C
```

## 12. 구현하지 않은 범위

- OAuth2/JWT 발급과 검증, 사용자 소속 및 tenant 상태 관리
- 이벤트, DLQ, Outbox, Saga, 보상 트랜잭션
- tenant별 스키마 또는 데이터베이스와 동적 DataSource 라우팅
- Kubernetes YAML과 Docker Compose 기반 인프라 기동
- PostgreSQL Testcontainers 통합 테스트
- 운영용 secret, 계정 수명주기, 백업과 고가용성

## 13. 운영 적용 시 추가 고려사항

- 외부 IdP에서 인증한 tenant/user만 내부 헤더로 전달하고 외부 클라이언트의 헤더 위조를 차단해야 한다.
- migration 소유자와 제한된 런타임 역할을 분리하고 superuser·`BYPASSRLS`·테이블 소유자 동작을 검토해야 한다.
- connection pool에서 tenant 세션 값이 요청 사이에 누수되지 않도록 transaction-local 설정과 풀 초기화를 검증해야 한다.
- Bean Validation, 길이 제한, optimistic locking, 감사 로그와 보안 관측성을 추가해야 한다.
- 실제 PostgreSQL 통합 테스트, 비밀 관리자, 백업·복구·보존·고가용성 정책이 필요하다.

## 14. 문제 해결

### `Connection refused` 또는 데이터베이스 연결 실패

PostgreSQL이 실행 중인지, `DB_URL`의 호스트·포트·DB 이름이 맞는지 확인한다. `psql -U postgres -d postgres -c 'select version();'`도 실패하면 애플리케이션보다 PostgreSQL 접속 문제를 먼저 해결한다.

### `database "saas_sample" does not exist` 또는 인증 실패

`db/create-local-database.sql`을 PostgreSQL 관리자 계정으로 실행했는지 확인한다. 기본 비밀번호를 바꿨다면 `DB_USERNAME`, `DB_PASSWORD`도 실제 역할과 일치시킨다. `psql` 명령에는 JDBC URL(`jdbc:postgresql://...`)을 직접 사용할 수 없으므로 `-h`, `-p`, `-d` 옵션을 사용한다.

### 시작 중 Flyway 또는 Hibernate schema validation 실패

애플리케이션 역할이 대상 데이터베이스에서 테이블과 정책을 만들 수 있는지 확인한다. 부분 적용된 수동 테이블이 있다면 Flyway V1과 충돌할 수 있으므로 빈 데이터베이스에서 순서대로 다시 실행한다. migration SQL을 수동 실행한 뒤 애플리케이션을 시작하는 방식은 사용하지 않는다.

### API가 `400 TENANT_HEADER_REQUIRED` 또는 `USER_HEADER_REQUIRED` 반환

모든 API 요청에 비어 있지 않은 `X-Tenant-Id`와 `X-User-Id`를 모두 보냈는지 확인한다. Postman에서는 **data-isolation-local** Environment가 선택되어 `tenant_id`, `user_id`가 활성화되어 있는지도 확인한다.

### Postman 첫 등록이 `409 DUPLICATE_REQUEST_NO` 반환

이전 실행의 `POSTMAN-001` 행이 남아 있다. 11절 cleanup SQL을 실행하고 **`01. 정상 처리` → `02. 격리 및 오류`** 순서로 다시 실행한다.

### Postman 단건 조회가 잘못된 URL 또는 `404` 반환

Environment의 `request_id`가 비어 있거나 이전 실행 값일 수 있다. **`01. 정상 처리`**의 **`01. 요청 등록`**부터 순서대로 실행하여 새 응답 ID가 자동 저장되게 한다.

### RLS 검증 SQL에서 INSERT 오류 발생

tenant-a 컨텍스트에서 tenant-b 행을 삽입하는 오류는 `WITH CHECK`가 작동한다는 의도된 결과다. 반대로 해당 INSERT가 성공한다면 접속 역할이 superuser 또는 `BYPASSRLS`인지, migration에 `FORCE ROW LEVEL SECURITY`와 정책이 적용됐는지 확인한다.
