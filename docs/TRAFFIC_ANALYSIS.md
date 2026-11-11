# 말모아 Traffic 분석 및 부하 테스트

> H2 예비 결과: [`LOAD_TEST_RESULTS_2026-10-28.md`](./LOAD_TEST_RESULTS_2026-10-28.md)
>
> PostgreSQL 분리 프로세스 결과: [`LOAD_TEST_ONE_PAGE_SUMMARY.md`](./LOAD_TEST_ONE_PAGE_SUMMARY.md)
>
> 전체 기능 혼합 및 SSE 결과: [`FULL_SERVICE_LOAD_TEST_2026-10-29.md`](./FULL_SERVICE_LOAD_TEST_2026-10-29.md)

## 현재 구현된 계측 기반

- Spring Boot Actuator
- Micrometer와 Prometheus 형식 Metric
- URI·HTTP Method·Status별 요청 수 및 응답시간
- JVM, CPU, Memory, DB Connection Pool Metric
- 현재 SSE 연결 수
- SSE 누적 Open/Close 수
- Event 유형별 SSE 발행 수

주요 Metric:

```text
http_server_requests_seconds_count
http_server_requests_seconds_sum
http_server_requests_seconds_max
malmoa_sse_connections_active
malmoa_sse_connections_opened_total
malmoa_sse_connections_closed_total
malmoa_sse_events_published_total
jvm_memory_used_bytes
process_cpu_usage
hikaricp_connections_active
```

`GET /actuator/health`와 `GET /actuator/info`는 공개된다. `/actuator/metrics/**`와 `/actuator/prometheus`는 기본적으로 인증이 필요하다. Prometheus가 Private Network에서 수집할 때만 `PUBLIC_METRICS_ENABLED=true`로 설정하고, Public Internet에는 노출하지 않는다.

## 1차 부하 테스트 실행

현재는 특정 외부 부하 도구를 최종 선정하지 않았다. 실제 첫 기준선은 프로젝트 안의 독립 실행형 Spring Boot Test와 JDK `HttpClient`로 실행했다. k6 Script는 외부 부하 발생기를 사용할 때의 후보로 유지한다.

### 도구 설치 없이 로컬 기준선 실행

```powershell
.\mvnw.cmd -Pload-test test `
  "-Dload.users=1,10,100,1000" `
  "-Dload.duration.seconds=30" `
  "-Dload.request.timeout.seconds=10"
```

이 실행은 테스트 전용 사용자·기기·Token을 자동 생성하고 실제 HTTP Endpoint를 호출한다. 결과는 `target/load-test-results`에 생성된다. H2와 Localhost를 사용하는 개발 기준선이므로 운영 용량 판정에는 PostgreSQL과 분리된 부하 발생 환경이 추가로 필요하다.

### PostgreSQL 분리 프로세스 시험

`scripts/load/prepare-postgres-load.mjs`가 테스트 전용 사용자·기기·Token SQL을 만들고, `scripts/load/device-traffic-node.mjs`가 별도 Node.js 프로세스에서 실제 HTTP 요청을 보낸다. Token 원문과 실행 결과는 Git에서 제외되는 `target` 아래에 저장한다.

이번 실행에서는 750개 기기까지 임시 기준을 통과했고 1,000개 기기는 오류율 2.16%, P95 5.26초로 실패했다. 따라서 1,000개 10분 시험은 진행하지 않고 병목 개선 후 재시험한다.

### 1명 기준

```powershell
$env:BASE_URL="http://localhost:8080"
$env:DEVICE_TOKENS="token-for-test-device"
$env:USERS="1"
$env:DURATION="10m"
$env:HEARTBEAT_SECONDS="60"
$env:LOCATION_SECONDS="10"
$env:SENSOR_SECONDS="5"
k6 run scripts/load/device-traffic.js
```

### 10 / 100 / 1,000명

`USERS`와 `DEVICE_TOKENS`를 늘려 단계적으로 실행한다. 한 Token을 많은 사용자처럼 공유하면 실제 인증·DB 접근 패턴을 재현하지 못하므로 가능한 한 사용자별 Token을 사용한다.

```powershell
$env:USERS="10"   # 이후 100, 1000
$env:DEVICE_TOKENS="token-1,token-2,token-3"
k6 run scripts/load/device-traffic.js
```

## 반드시 따로 측정할 시나리오

현재 k6 Script는 Heartbeat, Location, Heart-rate API의 기준 부하를 만든다. 다음은 후속 Scenario로 분리한다.

- SSE 10/100/1,000 동시 연결과 재연결 폭주
- Card Usage와 STT Event 쓰기
- 보호자 Board/Report 읽기
- 실제 AI Provider 연결 후 AI 응답시간·오류율·비용
- Safe-zone 외부 위치가 반복될 때 Alert 적재량
- Routine Scheduler의 다중 Instance 중복 실행

## 결과 기록 Template

| 항목 | 1명 | 10명 | 100명 | 1,000명 |
|---|---:|---:|---:|---:|
| 평균 Requests/sec |  |  |  |  |
| P95 latency |  |  |  |  |
| P99 latency |  |  |  |  |
| Error rate |  |  |  |  |
| Network in/out |  |  |  |  |
| CPU peak |  |  |  |  |
| Memory peak |  |  |  |  |
| DB active connections |  |  |  |  |
| SSE active connections |  |  |  |  |

## 통과 기준 초안

아래 값은 제품의 최종 SLO가 아니라 첫 측정을 위한 임시 기준이다.

- Error rate < 1%
- P95 < 500ms
- P99 < 1,000ms
- DB Connection Pool 고갈 없음
- SSE 연결 수가 종료 후 원래 값으로 복귀
- 부하 종료 후 Memory가 지속 증가하지 않음

측정 결과를 얻은 뒤 실제 사용성과 배포 사양을 바탕으로 기준을 확정한다.
