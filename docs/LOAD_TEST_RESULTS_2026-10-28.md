# 말모아 기기 트래픽 1차 로컬 부하 테스트 결과

## 결론

2026년 10월 28일 로컬 개발 PC에서 기기 텔레메트리 API의 첫 기준선 부하 테스트를 실행했다.
1,000개의 서로 다른 테스트 사용자·기기·Bearer Token을 사용한 단계에서 30초 동안 9,508건을 처리했으며, 오류는 0건이었다.

이 결과는 **로컬 H2 환경의 짧은 기준선**이다. 운영 환경에서 동시 사용자 1,000명을 수용할 수 있다는 보장으로 해석하면 안 된다.

## 실행 방식

- Spring Boot 내장 서버를 Random Port로 실제 실행
- JDK `HttpClient`와 Virtual Thread로 동시 사용자 생성
- 각 가상 사용자마다 별도 AAC 사용자, 기기, Device Bearer Token 생성
- 일반 자동 테스트와 분리된 Maven `load-test` Profile 사용
- 각 단계 실행 전 1명·3초 Warm-up 수행
- 각 단계 30초 실행
- 요청 시작 시점을 분산해 한 시점에 몰리는 현상 완화

### 요청 주기

| 요청 | Endpoint | 주기 |
|---|---|---:|
| Heartbeat | `POST /api/v1/device/heartbeat` | 60초 |
| 위치 | `POST /api/v1/device/locations` | 10초 |
| 심박수 | `POST /api/v1/device/sensor-events` | 5초 |

### 재현 명령

```powershell
.\mvnw.cmd -Pload-test test `
  "-Dload.users=1,10,100,1000" `
  "-Dload.duration.seconds=30" `
  "-Dload.request.timeout.seconds=10"
```

실행 결과 Markdown은 `target/load-test-results`에 생성된다.

## 최종 측정 결과

| 가상 기기 | 처리 요청 | 평균 RPS | 오류 | P50 | P95 | P99 | 최대 | CPU Peak | Heap Peak | 관측 DB Connection Peak |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 | 10 | 0.33 | 0 | 11.08ms | 16.03ms | 16.03ms | 16.03ms | 4.7% | 62.3MiB | 0 |
| 10 | 100 | 3.33 | 0 | 7.85ms | 12.15ms | 14.98ms | 15.31ms | 10.5% | 86.3MiB | 0 |
| 100 | 961 | 31.77 | 0 | 5.54ms | 10.38ms | 14.67ms | 25.02ms | 24.8% | 89.0MiB | 2 |
| 1,000 | 9,508 | 315.69 | 0 | 4.30ms | 8.39ms | 16.56ms | 59.52ms | 56.5% | 141.0MiB | 2 |

DB Connection은 250ms 간격 표본의 관측 Peak다. 값이 0인 단계도 DB를 사용하지 않았다는 뜻이 아니라, 매우 짧은 Connection 사용 구간을 표본이 포착하지 못했다는 뜻이다.

## 1,000개 기기 Endpoint별 결과

| Endpoint | 요청 수 | 오류 | P95 | P99 |
|---|---:|---:|---:|---:|
| Heartbeat | 512 | 0 | 7.59ms | 14.20ms |
| Location | 2,999 | 0 | 8.74ms | 16.86ms |
| Sensor | 5,997 | 0 | 8.04ms | 16.61ms |

30초 실행이므로 Heartbeat는 60초 주기의 일부만 포함된다. Location과 Sensor 요청 수는 설정 주기와 거의 일치한다.

## 해석 시 주의사항

- DB는 운영 후보인 PostgreSQL이 아니라 In-memory H2다.
- 서버와 부하 발생기가 같은 JVM·PC에서 실행돼 CPU와 Heap에는 양쪽 비용이 섞여 있다.
- Localhost HTTP/1.1이므로 실제 Network, TLS, Reverse Proxy 지연이 없다.
- 각 단계가 30초여서 장시간 Memory 증가, GC, DB Table 증가 영향은 확인하지 못했다.
- Heartbeat, Location, Heart-rate 쓰기만 측정했다.
- SSE 동시 연결, 재연결 폭주, Card Usage, STT, Report, 실제 AI 호출은 포함하지 않았다.
- 따라서 이 결과는 현재 구현의 회귀 확인과 다음 시험 규모를 정하기 위한 기준선이다.

## 다음 부하 테스트

1. PostgreSQL 환경에서 동일 Scenario 재실행
2. 서버와 부하 발생기를 별도 Process 또는 별도 장비로 분리
3. 1,000개 기기 10분 이상 지속 시험
4. SSE 10·100·1,000 동시 연결 및 재연결 폭주 시험
5. Card Usage·STT 쓰기와 보호자 Board·Report 읽기 혼합
6. 실제 AI Provider 연결 후 응답시간, 오류율, Rate Limit과 비용 측정
