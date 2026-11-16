# 말모아 Backend 1

발달장애인용 적응형 AAC 시스템의 Spring Boot 백엔드입니다. Google OIDC로 로그인하는 보호자 계정과 실제 AAC 사용자 프로필을 분리하며, 보호자-AAC 사용자 연결을 기준으로 접근 권한을 검사합니다.

## 기술 스택

- Java 21, Spring Boot 3.5.16, Maven Wrapper
- Spring Web, Security, OAuth2 Client, Data JPA, Validation
- H2 기본 개발/테스트 DB
- PostgreSQL 16, Flyway, Testcontainers
- springdoc-openapi 2.8.17
- JUnit 5, MockMvc, Spring Security Test

## 기본 로컬 실행

실행에는 Google OAuth 환경변수가 필요합니다. 실제 값은 저장소에 커밋하지 않습니다.

```powershell
$env:GOOGLE_CLIENT_ID="..."
$env:GOOGLE_CLIENT_SECRET="..."
$env:FRONTEND_BASE_URL="http://localhost:3000"
$env:FRONTEND_ALLOWED_ORIGINS="http://localhost:3000,http://localhost:3001"
.\mvnw.cmd spring-boot:run
```

- Health: http://localhost:8080/api/v1/health
- H2 Console: http://localhost:8080/h2-console
- Swagger UI: http://localhost:8080/swagger-ui/index.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs

기본 프로필은 `jdbc:h2:mem:malmoa`를 사용하고 샘플 카테고리와 상징을 적재합니다. PostgreSQL 프로필에서는 샘플 데이터를 자동 적재하지 않습니다.

## 테스트

```powershell
# Docker 없이 실행되는 H2 기반 기본 테스트
.\mvnw.cmd clean test

# Docker가 필요한 실제 PostgreSQL 통합 테스트
.\mvnw.cmd -Ppostgres-it verify
```

`postgres-it`은 Testcontainers PostgreSQL에서 Flyway, Hibernate `validate`, Repository CRUD, unique/FK 제약을 검증합니다. 테스트용 OAuth 값은 실제 로그인을 성공 처리하지 않으며, Google 브라우저 로그인은 실제 Client ID/Secret으로 별도 검증해야 합니다.

## PostgreSQL 로컬 실행

Docker Desktop을 먼저 실행하고 저장소 루트에서 환경 파일을 준비합니다.

```powershell
Copy-Item .env.example .env
# .env의 replace-with-local-password를 로컬 전용 비밀번호로 변경
.\scripts\postgres-up.ps1
.\scripts\run-postgres.ps1
```

일반 실행은 실제 Google OAuth 변수도 필요합니다. DB/스키마 스모크만 수행할 때는 `.\scripts\run-postgres.ps1 -Smoke`를 사용할 수 있지만 Google 로그인은 검증하지 않습니다. PostgreSQL 프로필은 Flyway V1-V5 적용 후 Hibernate `ddl-auto=validate`를 수행합니다.

```powershell
.\scripts\smoke-test.ps1
.\scripts\postgres-down.ps1              # volume 유지
.\scripts\postgres-down.ps1 -ResetData   # volume과 모든 로컬 DB 데이터 삭제
```

Compose는 PostgreSQL 포트를 `127.0.0.1`에만 공개하고 named volume `malmoa_postgres_data`를 사용합니다. 이 구성은 로컬 개발용이며 운영 배포 설정이 아닙니다. 상세 절차와 psql 조회, 오류 해결은 `docs/POSTGRESQL_LOCAL_SETUP.md`를 참고합니다.

## API 범위

- 인증: `/api/v1/auth/**`
- 권장 AAC 사용자 API: `/api/v1/me/aac-users/**`
- 호환 API: `/api/v1/users/**`
- 보호자: `/api/v1/guardians`, 사용자별 `/guardians`
- 상징: `/api/v1/categories`, `/api/v1/symbols`
- 즐겨찾기/사용기록: AAC 사용자 하위 경로
- 온보딩 설정: 격자, TTS 음성, 가입정보 summary
- 기기 연결: 보호자 pairing 발급/재발급 및 공개 QR/코드 claim

기존 `/api/v1/users/**` API는 삭제하지 않고 동일한 소유권 검사를 적용했습니다. 신규 연동은 `/api/v1/me/aac-users/**`를 사용합니다. 자세한 계약은 `docs/API_CONTRACT.md`를 참고합니다.

## 보안

- 인증 기준은 이메일이 아닌 Google OIDC `sub`입니다.
- 세션 쿠키는 HttpOnly, SameSite=Lax이며 운영에서 Secure=true를 사용합니다.
- CORS는 설정된 프론트엔드 Origin만 허용하고 credentials를 사용합니다.
- 변경 요청은 `XSRF-TOKEN` 쿠키 값을 `X-XSRF-TOKEN` 헤더로 보내야 합니다.
- `.env`, DB 비밀번호, Google 비밀번호, OAuth secret/token은 커밋하거나 로그에 출력하지 않습니다.

## 구현 범위

현재 인증, Guardian 연결, AAC 사용자 프로필/격자/TTS 온보딩, 기기 pairing/Device, 소유권, 상징, 즐겨찾기, 사용기록, Swagger, Flyway 및 자동 테스트를 포함합니다. AI 문장 생성, IoT, WebSocket, FCM, 배포는 포함하지 않습니다.
