# 말모아 Backend Development Status

> 기준일: 2026-10-05
> 기준 브랜치: `feature/sangbeom-google-login`
> 기준 커밋: `2840502 feat: add personalized AAC context and event contracts`
> 문서 원칙: 코드에서 확인된 기능만 완료로 표시하며, 백엔드 기반과 실제 외부 서비스 연동을 구분한다.

---

이번 주에는 기존 백엔드 구현 내용을 전체적으로 정리하고 서버 트래픽 측정 환경을 추가했습니다. 보호자 로그인, 사용자·AAC 카드 관리, 기기 연결, 카드 사용 기록, 위치·센서 저장과 개인화 AI Context 생성까지 구현되어 있습니다. 실제 AI Provider는 아직 연결 전이며, 연동 인터페이스까지 준비된 상태입니다.

Actuator와 Micrometer를 이용해 API 요청 수, 응답시간, 오류율, CPU, 메모리, DB 연결 수와 SSE 연결 수를 확인할 수 있게 했습니다. 별도 Node.js 부하 발생기와 PostgreSQL을 사용해 Heartbeat·위치·심박수뿐 아니라 카드판 조회, 카드 사용, 표정, STT, 긴급 모드, 보호자 조회, 리포트와 SSE 연결까지 직접 시험했습니다. 자동 테스트 17개도 모두 통과했습니다.

## 1. 프로젝트 한눈에 보기

말모아는 AAC(Augmentative and Alternative Communication, 보완대체의사소통) 사용자가 자신의 의사소통 수준과 생활 맥락에 맞는 표현을 더 쉽게 선택하도록 돕는 개인화 AAC 시스템이다.

기존의 단순한 구조는 다음과 같다.

```text
상황 입력 → AI → 일반적인 문장 추천
```

말모아가 목표로 하는 구조는 다음과 같다.

```text
사용자 의사소통 수준
+ 연령과 개인 설정
+ 개인 AAC 카드
+ 중요 표현과 즐겨찾기
+ 실제 카드 사용 기록
+ 위치와 장소
+ 시간과 루틴
+ 현재 상황
→ 사용자 맞춤 단어/카드 추천
→ 사용자 맞춤 문장 1개 추천
```

핵심 차별점은 “AI가 문장을 생성한다”는 사실 자체가 아니다. 사용자의 실제 어휘, 사용 습관, 의사소통 능력, 생활 맥락을 바탕으로 **AAC 환경과 추천 결과가 사용자에게 적응한다는 점**이다.

현재 백엔드는 사용자 프로필, 개인 카드, 즐겨찾기, 중요 표현, 사용 빈도와 최근 사용 기록을 묶어 `PersonalizedAacContext`를 만드는 단계까지 구현되어 있다. 실제 생성형 AI Provider와 위치·장소·루틴 Context 결합은 다음 개발 단계다.

---

## 2. 전체 시스템 Architecture

```text
[보호자 Web/PWA]
  ├─ Google OIDC 로그인
  ├─ 사용자/카드/장소/루틴 관리
  ├─ 리포트 조회
  └─ SSE 변경 이벤트 구독
            │ Session + CSRF
            ▼
[Malmoa Spring Boot Backend]
  ├─ Auth / Guardian ownership
  ├─ AAC User / Board / Card
  ├─ Device Pairing / Device Token
  ├─ Card Usage / STT / Sensor Event
  ├─ Location / Place / Safe Zone
  ├─ Routine / Alert / Report
  ├─ PersonalizedAacContext Builder
  └─ Recommendation Provider Interface
            │
            ├─ H2: 로컬·테스트
            └─ PostgreSQL: 운영 후보, 운영 환경 검증 필요

[AAC 사용자 기기]
  ├─ QR 또는 6자리 코드 Pairing
  ├─ Device Bearer Token
  ├─ Board 조회 및 SSE 구독
  ├─ 카드/TTS/STT/센서 이벤트 전송
  └─ 위치 전송

[외부 Provider — 아직 연결되지 않음]
  ├─ 실제 LLM
  ├─ 실제 STT/TTS
  ├─ 얼굴 표정 인식 모델
  ├─ Map/Geocoding
  ├─ 외부 이미지 검색
  └─ 실제 Push 발송
```

### 핵심 인증 경계

- 보호자 API: Google OIDC 로그인 후 Session Cookie 사용, 변경 요청은 CSRF 토큰 필요
- AAC 사용자 데이터: 로그인한 보호자가 실제 해당 사용자의 보호자인지 검사
- 사용자 기기 API: Pairing 후 발급된 Device 전용 Bearer Token 사용
- 실시간 동기화: SSE(Server-Sent Events, 서버가 연결된 클라이언트에 변경을 단방향으로 전달하는 방식) 사용

---

## 3. 현재 구현 상태 요약표

| 기능 | 상태 | 현재 확인된 범위 | 다음 작업 |
|---|---|---|---|
| Google Login | ✅ 완료 | Google OIDC, Session 로그인 유지 | 실제 배포 도메인 OAuth 설정 검증 |
| Guardian 권한 | ✅ 완료 | AAC 사용자 소유권 검사, 타 보호자 접근 차단 | 운영 권한 정책 보강 |
| AAC Board | ✅ 완료 | 사용자별 Board 조회, 버전, 초기 카드 구성 | 프론트 최종 연결 검증 |
| Card CRUD | ✅ 완료 | 카드 생성·수정·삭제, 순서, 중요 표현, 시스템 카드 | UX와 대량 정렬 API 검토 |
| `displayText` / `ttsText` | ✅ 완료 | 화면 문구와 발화 문구 분리, fallback | 실제 TTS와 연결 |
| 즐겨찾기 | ✅ 완료 | 카드별 등록·해제 및 Context 반영 | 프론트 UX 검증 |
| 카드 사용 기록 | ✅ 완료 | 사용자·Device·카드·표시 스냅샷·실제 발화문·시각 저장 | 운영 집계 성능 검증 |
| SSE | ✅ 완료 | Board/설정/위치/센서/루틴/알림 이벤트 | 재연결·다중 접속 부하 테스트 |
| Device Pairing | ✅ 완료 | QR, 6자리 코드, 만료·재발급·재사용 방지 | Rate limit 검토 |
| Device Token | ✅ 완료 | 전용 Bearer Token, 만료·폐기 | 안전한 기기 저장 방식 검증 |
| Heartbeat | ✅ 완료 | 기기 연결 상태 갱신 API | 전송 주기 정책 확정 |
| 위치 저장 | ✅ 완료 | Device 좌표 저장, 최신 위치와 기간 이력 조회 | 실제 수집 주기·백그라운드 정책 |
| 안심존 | 🟡 백엔드 기반 또는 일부 구현 | 장소·반경·활성 시간 저장, 반경 밖 위치에 이탈 알림 기록 | 진입/이탈 상태 전이, 중복 알림 억제, 실제 Push |
| 루틴 | 🟡 백엔드 기반 또는 일부 구현 | CRUD, 요일·시간·타임존, 스케줄 실행, Alert/SSE | 캘린더 UI, 실제 Push, AI Context 연결 |
| 리포트 | ✅ 완료 | TOP 5, 카테고리 비율, 긴급 횟수, 센서, 기본 PDF | 시각화 UI와 전문 보고서 품질 개선 |
| Personalized AI Context | ✅ 완료 | 프로필·카드·중요·즐겨찾기·빈도·최근 사용 결합 | 장소·GPS·루틴·시간 추가 |
| 실제 LLM | 🔴 미구현 / 검토 필요 | Provider Interface만 존재, 미설정 시 503 | Provider 선정·연결·비용/안전 검증 |
| STT 저장 | ✅ 완료 | 인식 텍스트·성공 여부·confidence·시각·Device 저장 | 보존 정책 운영값 결정 |
| 실제 STT | 🔴 미구현 / 검토 필요 | STT 엔진 없음 | 프론트 또는 외부 Provider 연결 |
| 표정 결과 저장 | ✅ 완료 | Emotion·confidence·시각을 SensorEvent로 저장 | 동의·정확도·보존 정책 확정 |
| 실제 표정 인식 | 🔴 미구현 / 검토 필요 | 카메라/모델 없음 | 프론트 모델 선정과 정확도 검증 |
| TTS 설정 | ✅ 완료 | 아동/성인 음성 유형, 속도, 발화문 분리·기록 | 실제 공급자 매핑 |
| 실제 TTS Provider | 🔴 미구현 / 검토 필요 | 현재 미리 듣기는 `CLIENT_TTS` 계약 | 공급자 선정·연결 |
| 이미지 Metadata | ✅ 완료 | 시스템/사용자 업로드/허용 외부 이미지, 출처·라이선스 | 운영 스토리지 전환 |
| 외부 이미지 Provider | 🔴 미구현 / 검토 필요 | 실제 검색/복제 없음 | 라이선스 검토 후 Provider 연결 |
| 장소 주소 검색 | 🔴 미구현 / 검토 필요 | 주소 문자열과 좌표 저장만 가능 | 주소 검색 UI/API 연결 |
| Geocoding | 🔴 미구현 / 검토 필요 | 주소→좌표 변환 없음 | Provider 조사·선정 |
| GPS 기반 AI Context | 🔴 미구현 / 검토 필요 | 위치 저장은 되지만 AI Context 미연결 | 현재 장소 판정 및 Context 확장 |
| 루틴 기반 AI Context | 🔴 미구현 / 검토 필요 | 루틴 저장·실행은 되지만 AI Context 미연결 | 현재/예정 루틴 계산 |
| 실시간 위치 정책 | 🔴 미구현 / 검토 필요 | Device 위치 수신 API만 구현 | 수집 조건·주기·배터리·PWA 한계 결정 |
| 트래픽 측정 | 🟡 백엔드 기반 또는 일부 구현 | HTTP·JVM·DB·SSE Metric과 Prometheus Endpoint 준비 | 실제 환경 측정값 수집 |
| 부하 테스트 | 🔴 미구현 / 검토 필요 | 10/100/1,000명 검증 없음 | 시나리오와 도구 선정 |
| Observability | 🟡 백엔드 기반 또는 일부 구현 | Actuator·Micrometer·Prometheus Metric 준비 | Prometheus/Grafana 배포와 Dashboard 구성 |

---

## 4. 인증 및 권한

### 왜 필요한가

AAC 사용자 프로필, 위치, 얼굴 표정 결과는 민감정보다. URL의 사용자 ID만 변경해 다른 보호자의 데이터를 읽거나 수정할 수 있으면 안 된다.

### 현재 상태 — 완료

- Google OIDC 로그인
- 서버 Session 기반 로그인 유지
- CSRF(Cross-Site Request Forgery, 사용자가 의도하지 않은 변경 요청을 막는 보안 장치) 보호
- Guardian 기반 AAC 사용자 소유권 검사
- 타 Guardian의 사용자 조회·수정 거부 테스트
- Device 전용 Bearer Token 인증
- Device 및 Token 강제 폐기
- 회원 탈퇴와 Session 무효화

### 동작 방식

```text
보호자 요청
→ 로그인 Session 확인
→ 현재 Guardian 조회
→ Guardian과 AAC 사용자의 연결 관계 확인
→ 허용 또는 403 Forbidden
```

Device는 보호자 Session을 사용하지 않는다. Pairing 성공 시 발급된 별도 Token을 전송하며, 서버는 Token의 만료·폐기 여부와 Device 상태를 검사한다.

### 남은 작업

- 운영 환경 OAuth Redirect URI와 Cookie 보안 옵션 검증
- Pairing Code 추측 공격에 대한 Rate limit
- 관리자용 전역 Guardian/상징 쓰기 API의 역할 분리
- 민감정보 열람·삭제·감사 로그 정책

---

## 5. AAC 사용자 개인 설정

### 왜 필요한가

같은 문장이 모든 AAC 사용자에게 적절하지 않다. 이해 가능한 문장 길이와 문법 복잡도가 다르므로 AI와 AAC 화면이 사용자 수준을 알아야 한다.

### 현재 상태 — 완료

- 사용자 프로필과 생년월일
- 보호자와 사용자 관계
- 등록 진행 단계 복구
- 문장 이해 수준 1~4
- 최대 권장 문장 길이
- 쉬운 단어 우선 여부
- 추상 표현 제한 여부
- 복잡한 문법 제한 여부
- 짧고 직접적인 표현 우선 여부
- 2×2 / 3×3 / 4×4 격자 설정
- TTS 음성 유형과 속도
- 아동 남/여, 성인 남/여 옵션
- 보호자 튜토리얼 완료 상태

### 동작 방식

보호자는 사용자별 Communication Profile을 조회·수정한다. 이 설정은 `PersonalizedAacContext`의 `communicationPreferences`로 전달될 수 있다.

### 남은 작업

- 실제 LLM이 각 옵션을 일관되게 지키는지 평가
- 사용자별 임상·교육 기준과 현재 1~4단계의 매핑 검토
- 보호자 설정 변경 이력 필요성 검토

---

## 6. AAC Board / Card

### 왜 필요한가

사용자가 실제 생활에서 사용하는 단어와 사진은 사람마다 다르다. 개인 카드 편집은 개인화의 핵심 데이터가 된다.

### 현재 상태 — 완료

- 사용자별 AAC Board
- 카테고리 CRUD와 순서
- 카드 CRUD와 순서
- 카드 소프트 삭제
- 즐겨찾기
- 중요 표현
- 시스템 카드와 사용자 카드 구분
- 사용자별 인증 이미지 업로드
- 이미지 출처 유형과 라이선스 Metadata

카드 문구는 다음처럼 분리한다.

```text
displayText = "물"          # 화면에 표시
ttsText     = "물 주세요."  # 실제 발화
```

`ttsText`가 별도로 저장되지 않으면 `displayText`를 fallback으로 사용한다. 기존 `text` 필드는 API 호환성을 위해 `displayText`의 별칭으로 유지한다.

### 이미지 출처

| 유형 | 의미 |
|---|---|
| `SYSTEM_DEFAULT` | 말모아 시스템이 제공하는 기본 상징 |
| `USER_UPLOAD` | 보호자가 해당 사용자에게 업로드한 이미지 |
| `EXTERNAL_ALLOWED` | 사용 허가가 확인된 외부 이미지 URL |

외부 이미지는 HTTPS URL, 출처명, 라이선스, 출처 URL이 필요하다. Pixabay나 WeTalk의 실제 데이터를 프로젝트에 포함하거나 복제하지 않았다.

### 남은 작업

- 운영 스토리지를 S3/Cloud Storage 등으로 교체
- 외부 이미지 Provider 라이선스·비용·검색 정책 확정
- 카드 대량 이동·정렬 UX와 API 효율 검토

---

## 7. 실시간 동기화

### 왜 필요한가

보호자가 카드를 수정했을 때 AAC 기기가 새로고침 없이 변경 사실을 받아야 한다.

### 현재 상태 — 완료

SSE(Server-Sent Events)가 구현되어 있다.

```text
보호자가 카드 또는 설정 수정
→ Backend DB 저장
→ SSE Event 발행
→ 연결 중인 AAC 기기 수신
→ Board 재조회 및 화면 갱신
```

확인된 이벤트에는 다음이 있다.

- `BOARD_UPDATED`
- `SETTINGS_UPDATED`
- `STATUS_UPDATED`
- `CARD_USED`
- `LOCATION_UPDATED`
- `SENSOR_EVENT`
- `STT_EVENT`
- `ROUTINE_TRIGGERED`
- `ALERT`

### 왜 WebSocket 대신 SSE인가

현재 핵심 요구는 서버가 기기에 변경 사실을 전달하는 단방향 통신이다. SSE는 HTTP 기반으로 연결과 재연결 처리가 비교적 단순하다. 기기에서 서버로 보내는 카드·위치·센서 정보는 일반 REST API로 처리한다.

### 남은 작업

- 다수 동시 연결 부하 테스트
- 네트워크 단절·재연결 시나리오 검증
- 여러 보호자와 여러 Device가 동시에 연결될 때의 연결 수 산정
- 운영 Proxy의 Timeout/Buffering 설정 확인

---

## 8. Device 및 Pairing

### 왜 필요한가

보호자 계정과 AAC 사용자 기기를 안전하게 연결하고, 기기가 보호자 Session 없이 전용 API를 사용할 수 있어야 한다.

### 현재 상태 — 완료

- QR Pairing
- 6자리 코드 Pairing
- 만료 시간
- Pairing 재발급 시 기존 세션 폐기
- 사용된 자격 증명 재사용 방지
- 여러 AAC Device 연결
- Device 목록
- Device 전용 Access Token
- Heartbeat
- Device 강제 해제 및 Token 폐기
- Device용 Board, Event, 위치, 카드 사용, 센서, STT API

### 동작 방식

```text
보호자 Pairing 요청
→ QR Token + 6자리 Code 발급
→ AAC 기기에서 Claim
→ 사용자와 Device 연결
→ Device 전용 Bearer Token 발급
→ 이후 Device API에 Token 첨부
```

Heartbeat는 “기기가 아직 연결되어 있고 동작 중임”을 서버에 알리는 신호다. API는 구현되어 있지만 실제 호출 주기는 아직 제품 정책으로 확정되지 않았다.

### 남은 작업

- Pairing·Heartbeat 호출 빈도와 Rate limit
- Token을 브라우저/PWA에서 안전하게 보관하는 방식
- 장기 미접속 Device 자동 정리 정책

---

## 9. 사용 이력

### 왜 필요한가

사용자가 실제로 선택한 표현은 개인화 추천에서 가장 직접적인 행동 데이터다.

### 현재 상태 — 완료

`CardUsageLog`에 다음 정보를 저장할 수 있다.

- AAC 사용자
- Device
- 카드 또는 직접 발화 문장
- 카드 표시 문구 Snapshot
- 실제 TTS 발화 문구
- 선택/발화 Action
- 발생 시각

이 기록을 이용해 현재 다음 값을 계산한다.

- 카드별 사용 횟수
- 최근 사용 시각
- 최근 사용 표현 목록
- 자주 사용한 카드

### 회의 질문에 대한 현재 답

> 사용자가 선택한 상징카드 기록을 바탕으로 추천이 가능한가?

가능하다. 카드 사용 빈도와 최근 사용 기록이 이미 `PersonalizedAacContext`에 반영된다. 다만 실제 LLM Provider가 연결되지 않았으므로, 이 데이터가 생성 결과를 어떻게 바꾸는지에 대한 실제 End-to-End 검증은 아직 남아 있다.

### 남은 작업

- 실제 Provider 연결 전후 결과 비교
- 선택, 취소, 발화 Action별 가중치 설계
- 장기간 기록 집계 성능 및 사전 집계 필요성 검토
- 사용자에게 기록 열람·삭제 기능을 제공할지 결정

---

## 10. 개인화 AI

### 왜 필요한가

일반적인 AI Prompt만 사용하면 사용자가 모르는 단어, 지나치게 긴 문장, 개인 카드에 없는 표현이 나올 수 있다.

### 현재 상태 — 백엔드 Context와 Contract 완료, 실제 AI 미연결

현재 구현된 `PersonalizedAacContext`는 다음 정보를 포함한다.

- 사용자 연령과 프로필 메모
- 문장 이해 수준
- 최대 권장 단어 수
- 언어 단순화 설정
- 활성 AAC 카드
- 보호자가 지정한 중요 표현
- 즐겨찾기
- 사용 빈도
- 최근 사용 시각
- 최근 발화 표현
- 요청에서 전달한 `currentSituation`

어휘 우선순위는 다음과 같다.

1. 보호자 지정 중요 표현
2. 즐겨찾기
3. 사용 빈도가 높은 표현
4. 최근 사용 표현
5. 시스템 핵심 어휘
6. 사용자 추가 어휘

```text
사용자 프로필 + 개인 카드 + 중요/즐겨찾기 + 사용 이력
→ PersonalizedAacContext
→ Recommendation Prompt
→ AacSentenceRecommendationProvider
→ 문장 1개
```

응답 Contract는 다음과 같다.

```json
{
  "sentence": "물 주세요."
}
```

여러 후보 배열, 설명, Markdown, 번호 목록은 허용하지 않는다. Provider가 없으면 503을 반환하며, 여러 줄이나 목록 형식으로 응답하면 502로 거부한다.

### 남은 작업

- OpenAI/Gemini/Claude 등 실제 Provider 선정과 구현
- 개인정보 전송 범위와 Provider 보관 정책 확인
- 사용자 수준별 평가 Dataset과 품질 기준
- 응답 시간·비용·장애 시 fallback 정책
- 실제 사용 기록이 결과에 영향을 주는지 E2E 검증

---

## 11. 맥락 기반 AI 추천

현재 Context는 사용자 프로필·카드·사용 이력과 요청으로 받은 자유 텍스트 상황까지 포함한다. 저장된 위치·장소·루틴을 자동으로 결합하는 기능은 아직 구현되지 않았다.

### 장소

**상태: 설계 필요**

목표 흐름:

```text
최신 GPS
→ 등록 장소의 중심 좌표·반경과 비교
→ currentPlace 판정
→ 해당 장소에서 유용한 카드 우선순위 조정
```

학교라면 선생님·화장실·친구 관련 표현, 집이라면 가족·휴식·식사 표현을 우선할 수 있다. 현재 장소 CRUD와 좌표 저장은 있지만 `currentPlace`를 AI Context에 넣는 로직은 없다.

### GPS

**상태: 검토 필요**

GPS는 장소 추론의 한 신호일 뿐이다. 실내 오차, 오래된 위치, 정확도 값, 전송 중단을 고려해야 한다. 최신 위치의 유효 시간과 최소 정확도를 먼저 정의해야 한다.

### 루틴

**상태: 구현 예정**

현재 시간과 등록 루틴을 비교해 `currentRoutine` 또는 `upcomingRoutine`을 만들 수 있다. 예를 들어 언어치료 10분 전에는 치료·준비·감정 표현의 우선순위를 높일 수 있다. 루틴 CRUD와 스케줄 실행은 있으나 AI Context 연결은 없다.

### 사용 기록

**상태: 백엔드 기반 구현 완료, 실제 AI 효과 검증 필요**

최근 카드와 사용 빈도는 Context에 이미 포함된다. 다음 단계는 동일한 상황에서 이력 유무에 따라 실제 추천이 달라지는지 검증하는 것이다.

### 안전한 규칙과 AI의 역할

긴급 카드는 AI가 임의로 삭제하거나 대체하면 안 된다. 다음과 같이 역할을 분리하는 방향이 적절하다.

```text
규칙 기반: 긴급 표현 고정, 권한, 길이 제한, 허용 어휘, 안전 정책
AI 기반: 현재 맥락에 맞는 표현 선택·문장화
```

### 단어/카드 추천과 문장 추천 분리

| 구분 | 목적 | 출력 예 | 현재 상태 |
|---|---|---|---|
| 단어/카드 추천 | 다음에 선택할 가능성이 높은 기존 카드 정렬 | 물, 주세요, 선생님, 더 | 별도 API·알고리즘 설계 필요 |
| 문장 추천 | Context를 이용해 최종 표현 1개 생성 | 물 주세요. | Contract와 Provider Interface 구현, 실제 Provider 미연결 |

두 기능은 입력 데이터가 비슷하더라도 출력과 평가 방법이 다르므로 별도 기능으로 설계한다.

---

## 12. 위치 / 안심존

### 왜 필요한가

현재 장소에 맞는 표현을 추천하고, 보호자에게 안전 관련 정보를 제공하기 위해 위치 기반 기능이 필요하다.

### 현재 상태

- ✅ Device 위치 Sample 저장
- ✅ 최신 위치 조회
- ✅ 기간별 위치 이력 조회
- ✅ 장소명·주소·좌표·반경·활성 시간 저장
- ✅ 좌표와 장소 반경 비교
- ✅ 반경 밖이면 `SAFE_ZONE_EXIT` Alert와 SSE 기록
- 🟡 실제 외부 Push 발송은 미연결
- 🔴 지속 GPS 수집 정책은 미확정

현재 구현은 위치를 받을 때 각 활성 장소의 반경 밖인지 계산한다. 운영용 안심존 기능으로 발전시키려면 “안에 있다가 밖으로 나간 순간”을 판정하는 상태 전이와 반복 알림 억제 정책을 추가 검토해야 한다.

### 중요한 구분

```text
백엔드가 위치를 받을 수 있음
≠ 기기가 항상 위치를 보내고 있음
≠ 앱 종료 상태에서도 지속 추적 가능
```

### 실시간 위치 수집 검토사항

- AAC 화면이 실행 중일 때만 전송할지
- 고정 주기 또는 이동 감지 기반으로 전송할지
- 위치 정확도와 배터리 사용량
- PWA의 백그라운드 실행 제한
- 네이티브 앱/Capacitor 전환 필요성
- 별도 IoT GPS Device 필요성
- 위치 동의, 보관기간, 열람·삭제 정책

PWA가 완전히 종료된 상태에서 지속 GPS 추적을 안정적으로 보장하기는 어렵다. 앱 종료 후 추적이 핵심 요구라면 네이티브 또는 전용 Device 방식을 별도로 검증해야 한다.

---

## 13. 주소 검색 기반 장소 등록

### 왜 필요한가

좌표를 직접 입력하는 방식은 보호자가 실제 장소에 있지 않으면 사용하기 어렵다. 주소나 장소명을 검색해 좌표를 얻는 흐름이 필요하다.

### 현재 상태 — 외부 연동 미구현

현재 백엔드는 장소명, 주소 문자열, 위도·경도, 안심존 반경을 저장한다. 주소 검색과 주소→좌표 변환은 하지 않는다.

### 목표 흐름

```text
“상명대학교” 검색
→ 후보 주소 선택
→ Geocoding(주소를 좌표로 변환)
→ 지도에서 위치 확인·조정
→ 별칭 “학교”와 반경 저장
```

### 검토할 Provider

- Kakao Map
- Naver Map
- Google Maps
- 국내 주소/Geocoding API

### 선정 기준

- 국내 주소 정확도
- 비용과 무료 한도
- 라이선스와 지도 표시 조건
- 서버/클라이언트 API Key 보안
- 검색 결과 저장 가능 범위
- 장애와 호출 제한

### 남은 작업

- Provider 비교표 작성
- 프론트 검색 UX 설계
- Backend Proxy 필요 여부 결정
- 주소·좌표 검증 및 중복 장소 정책

---

## 14. 루틴 / 캘린더

### 왜 필요한가

반복되는 생활 일정은 사용자의 다음 의사소통 상황을 예측하는 강한 신호다.

### 현재 상태 — 백엔드 기반 구현

- 루틴 목록·생성·수정·비활성화
- 제목과 메시지
- 요일 반복
- 실행 시각
- 타임존
- 활성/비활성
- 30초 간격 Scheduler가 실행 대상을 검사
- 실행 시 Alert 기록과 `ROUTINE_TRIGGERED` SSE 발행

캘린더는 별도 데이터 모델이라기보다 루틴을 날짜·시간 형태로 보여주는 프론트 UI로 볼 수 있다.

### 남은 작업

- 프론트 캘린더와 사용자 팝업
- 실제 Push 발송 Provider
- 일정 직전/진행 중/종료 후 Context 규칙
- 루틴을 `PersonalizedAacContext`에 포함
- 서버 다중 Instance 환경에서 중복 실행 방지

---

## 15. STT / TTS / 얼굴 인식

### STT

STT(Speech-to-Text, 음성을 텍스트로 변환)는 결과 저장 Contract만 구현되어 있다.

```text
프론트 또는 외부 STT
→ 음성 인식
→ recognizedText 생성
→ Device Token으로 Backend 전송
→ 결과 저장
```

저장 정보는 사용자, Device, 텍스트, 성공 여부, 선택적 confidence, 발생 시각이다. 원본 음성은 저장하지 않는다. 실제 STT 엔진은 미연결이다.

### TTS

TTS(Text-to-Speech, 텍스트를 음성으로 읽는 기능)는 음성 유형·속도 설정, `displayText`/`ttsText` 분리, 실제 발화 문구 기록까지 구현되어 있다. 실제 서버 TTS Provider와 음성 파일 생성은 미구현이다.

### 얼굴 표정 인식

프론트나 별도 모델이 분석한 Emotion과 confidence를 Backend에서 저장할 수 있다. 현재 지원 Enum에는 `HAPPY`, `SAD`, `ANGRY`, `FEARFUL`, `SURPRISED`, `DISGUSTED`, `NEUTRAL`이 있다.

```text
카메라
→ 프론트에서 표정 분석
→ Emotion + confidence
→ Backend에 최소 결과만 전송
```

카메라 접근, 얼굴 모델, 영상 스트리밍, 원본 얼굴 저장은 구현하지 않았다. 개인정보 보호를 위해 원본 미디어를 기본 저장 방식으로 사용하지 않는다.

### 남은 작업

- STT/TTS Provider 또는 브라우저 기능 선정
- 표정 모델의 정확도·편향·동의 절차 검토
- 낮은 confidence 처리 규칙
- Event 보관기간 확정

---

## 16. 보호자 리포트

### 왜 필요한가

보호자·치료사에게 사용 패턴과 특이 상황을 설명할 수 있어야 한다.

### 현재 상태 — 기본 리포트 완료

- 기간별 카드 사용 횟수
- TOP 5 카드
- 카테고리별 사용 비율
- 긴급 이벤트 횟수
- 심박·표정 등 센서 Timeline
- 기본 Insight 문장
- 기본 PDF 다운로드

### 한계

현재 PDF는 최소한의 텍스트 보고서다. 완성된 의료·치료 제출용 디자인이나 임상 해석을 제공하는 수준은 아니다.

### 남은 작업

- 차트 중심 보호자 UI
- 장소·시간·루틴과 사용 기록의 교차 분석
- PDF 디자인과 한글 Font 품질 개선
- 전문가 제출용 항목 및 면책 문구 검토

---

## 17. 데이터 보관

### 현재 상태 — 설정 가능한 구조 완료, 실제 기간 미결정

다음 Event는 종류별 보관기간을 설정할 수 있다.

```text
APP_RETENTION_CARD_USAGE
APP_RETENTION_STT
APP_RETENTION_SENSOR
APP_RETENTION_LOCATION
```

미설정 시 자동 삭제하지 않는다. 임의의 30일·90일 값을 코드에 고정하지 않았다.

### 남은 작업

- 개인정보 중요도와 프로젝트 요구에 따른 기간 확정
- 사용자 탈퇴 시 삭제 범위 검증
- 운영 Backup과 삭제의 관계 정의
- 삭제 실행 결과에 대한 Metric·감사 로그
- 보호자 데이터 다운로드·삭제 요청 절차

---

## 18. 트래픽 / 서버 부하

### 왜 필요한가

말모아는 단순 조회 서비스가 아니다. SSE 연결, Heartbeat, 위치, 센서, 카드, STT, AI 요청이 반복되므로 사용자 수가 늘면 네트워크와 DB 부하가 빠르게 증가할 수 있다.

### 현재 상태 — 계측 기반과 1차 Script 구현, 실제 부하 결과 없음

Actuator·Micrometer·Prometheus Metric, SSE 연결 계측, k6 기반 Device Event 부하 Script를 추가했다. 실제 Requests/sec, Payload 크기, 대규모 SSE 동시 연결, DB Query 수를 측정한 결과는 아직 없다.

### 측정 지표

| 지표 | 의미 |
|---|---|
| Requests/sec | 서버가 초당 처리하는 요청 수 |
| Requests/user/min | 사용자 한 명이 분당 발생시키는 요청 수 |
| Network bytes in/out | 서버로 들어오고 나가는 데이터양 |
| SSE connection count | 동시에 유지되는 실시간 연결 수 |
| Average response time | 평균 API 응답 시간 |
| P95 / P99 latency | 느린 상위 5%·1% 요청의 응답 시간 |
| Error rate | 전체 요청 중 실패 비율 |
| DB query count | 요청 처리 과정의 DB 접근 횟수 |
| DB connection usage | Connection Pool 사용량 |
| CPU usage | 서버 CPU 사용률 |
| Memory usage | 서버 Memory와 GC 상태 |
| AI latency/cost | 외부 AI 요청 시간과 비용 |

### 가정 기반 요청 수 예시 — 확정 정책 아님

다음은 회의용 계산 예시이며 실제 주기가 아니다.

```text
Heartbeat: 60초마다
Location: 10초마다
Sensor: 5초마다
SSE: 상시 연결
Card/STT/AI: 사용 시 발생
```

주기 요청만 계산하면 사용자 한 명당 약 1,140건/시간이다.

| 사용자 수 | 주기 요청/시간 | 24시간 연속 가정 요청/일 | 평균 요청/초 |
|---:|---:|---:|---:|
| 1 | 1,140 | 27,360 | 약 0.32 |
| 10 | 11,400 | 273,600 | 약 3.17 |
| 100 | 114,000 | 2,736,000 | 약 31.67 |
| 1,000 | 1,140,000 | 27,360,000 | 약 316.67 |

이 계산에는 카드 사용, STT, AI, 보호자 조회와 SSE 재연결이 포함되지 않는다. 반대로 실제 사용 시간이 하루 24시간보다 짧거나 이동 감지 기반 전송을 사용하면 줄어든다. 데이터 전송량은 실제 JSON Payload와 HTTP/TLS Overhead를 측정한 뒤 계산해야 한다.

### 트래픽 조사 계획

#### Step 1 — 현재 API 호출 패턴 측정

- Endpoint별 호출 횟수와 응답 시간 수집
- 실제 Request/Response Byte 측정
- SSE 연결 유지 시간과 재연결 횟수 측정

#### Step 2 — 사용자 1명의 정상 사용 시나리오 작성

- 등교·수업·점심·치료·귀가 등 시간대별 행동 정의
- 카드 사용, 위치 이동, 센서, STT, AI 호출 수 기록

#### Step 3 — 시간당 Requests / Network Traffic 계산

- 주기 Event와 사용자 행동 Event 분리
- 평균과 Peak 시간대 분리

#### Step 4 — 10 / 100 / 1,000명 가상 사용자 부하 테스트

- 후보 도구: k6, JMeter, Gatling
- 도구는 아직 확정되지 않음

#### Step 5 — SSE 동시 연결 테스트

- 연결 수, Memory, Proxy Timeout, 재연결 폭주 확인

#### Step 6 — DB와 AI API 병목 파악

- 빈도 집계 Query, 위치 적재, Event Table 증가량 확인
- 외부 AI 응답시간과 동시 요청 제한 확인

#### Step 7 — 결과에 따른 최적화

- 위치·센서 전송 주기 조정
- Batch 전송
- 사전 집계 또는 Cache
- 인덱스와 Connection Pool 조정
- AI 요청 제한과 Queue 검토

### Observability 검토

Spring Boot Actuator, Micrometer, Prometheus 형식 Metric은 적용했다. Prometheus Server와 Grafana Dashboard는 아직 배포하지 않았다. Metric Endpoint는 기본적으로 인증으로 보호하며, Private Network에서 수집할 때만 명시적으로 공개하도록 구성했다.

---

## 19. DB / Migration

### 현재 상태

Flyway V1~V7이 존재하며 H2에서 전체 Migration과 Hibernate Validate가 통과했다.

### V7 주요 변경

#### `aac_users`

- 최대 권장 문장 길이
- 쉬운 단어 우선
- 추상 표현 제한
- 복잡한 문법 제한
- 짧고 직접적인 표현 우선

#### `aac_cards`

- 선택적 `tts_text`와 표시 문구 fallback
- 이미지 출처 유형·출처명·라이선스·출처 URL
- 중요 표현
- 시스템 카드 구분

#### `card_usage_logs`

- 선택적 카드 연결
- Device 연결
- 표시 문구 Snapshot
- 실제 발화 문구

#### 신규 Table

- `stt_events`

관련 FK(Foreign Key, Table 간 관계 제약), CHECK, INDEX가 추가되었다.

### 남은 작업

- 실제 PostgreSQL 운영 환경 Migration 검증
- Event Table 장기 증가량과 Partition 필요성 검토
- 운영 Backup/Restore Drill

---

## 20. 테스트

### 현재 최종 결과

```text
Tests run: 17
Failures: 0
Errors: 0
Skipped: 0

BUILD SUCCESS
```

### 검증 범위

- 의사소통 프로필 저장·수정·조회
- Guardian 소유권과 IDOR 차단
- `displayText` / `ttsText` 분리
- `ttsText` fallback
- 카드 수정 후 SSE Payload
- 중요 표현과 즐겨찾기
- 사용 빈도 집계와 최근 사용 순서
- 개인화 Context
- AI 단일 문장 Contract
- Device Token 기반 Event 저장
- STT 결과 저장
- 원본 미디어 없는 표정 결과 저장
- Flyway V1~V7
- Hibernate Validate
- 기존 Regression Test

### 아직 검증하지 않은 영역

- 실제 LLM/STT/TTS/Push/Map Provider
- 실제 Browser/PWA 장시간 실행
- PostgreSQL 운영 배포
- SSE 대규모 동시 접속
- 10/100/1,000명 부하
- 배터리와 Mobile Background 동작

---

## 21. 현재 구현 vs 외부 연동 필요 기능

| 영역 | Backend에서 준비된 것 | 실제 외부 연동 상태 |
|---|---|---|
| AI | Context Builder, Prompt, 단일 문장 API, Provider Interface | 실제 LLM 없음 |
| STT | 결과 저장 API와 DB | 실제 음성 인식 없음 |
| TTS | 음성 설정, 발화문 분리·기록 | 실제 서버 음성 Provider 없음 |
| 표정 | Emotion/confidence 저장 | 카메라·인식 모델 없음 |
| 위치 | 좌표 수신·저장·반경 계산 | 지속 GPS 수집 정책 없음 |
| 지도 | 장소·주소·좌표 저장 | 주소 검색·Geocoding 없음 |
| 이미지 | 업로드·Metadata·라이선스 구조 | 외부 검색 Provider 없음 |
| Push | 구독 정보·Alert 이력 | VAPID/FCM/APNs 발송 없음 |
| Storage | 인증된 Local File 저장 | 운영 Object Storage 없음 |
| Observability | 일반 Application Log | Metric Dashboard 없음 |

---

## 22. 최근 회의 결정 및 검토사항

### 1. AI가 현재 맥락을 어떻게 파악할 것인가

**상태: 설계 필요**

프로필만 보지 않고 장소, GPS, 시간, 루틴, 최근 사용 카드를 묶는 `CurrentContext` 개념이 필요하다. 오래된 GPS나 낮은 정확도처럼 불확실한 정보도 표현할 수 있어야 한다.

### 2. 사용한 AAC 카드 기록을 추천에 활용

**상태: 기반 구현 완료 / 실제 AI 검증 필요**

빈도와 최근 사용은 Context에 이미 포함된다. 실제 Provider 연결 후 동일 상황에서 사용 이력이 추천 결과를 유의미하게 바꾸는지 검증한다.

### 3. 위치를 실제로 얼마나 자주 수집할 것인가

**상태: 정책 검토 필요**

주기가 짧을수록 최신성은 좋아지지만 배터리, Network, DB 부하가 증가한다. 고정 주기, 이동 감지, 앱 활성 상태, 정확도 변화 기반 전송을 비교해야 한다.

### 4. 주소 검색 기반 장소 등록

**상태: 구현 예정**

좌표 직접 입력 대신 장소 검색→주소 선택→Geocoding→지도 확인→반경 저장 흐름을 구현한다. Provider는 아직 미정이다.

### 5. 서버 트래픽과 확장성 검증

**상태: 구현 예정**

사용자 한 명의 실제 호출 패턴을 먼저 측정하고 10/100/1,000명 시나리오로 확장한다. SSE 연결과 위치·센서 적재를 별도 시험한다.

---

## 23. TODO Priority

### P0 — 핵심 기능 완성

- [ ] 실제 `AacSentenceRecommendationProvider` 연결
- [ ] 실제 AI 추천 E2E Test
- [ ] 위치·장소·루틴을 포함할 Current Context 설계
- [ ] 카드 추천과 문장 추천 역할·API 분리

### P1 — Context Awareness

- [ ] 주소 검색 / Geocoding Provider 연결
- [ ] 최신 GPS와 반경을 이용한 현재 장소 판정
- [ ] 현재/예정 루틴을 AI Context에 연결
- [ ] GPS/장소/시간을 AI Context에 연결
- [ ] 최근 카드 사용에 따른 추천 변화 검증

### P1 — 실제 입출력 연동

- [ ] 실제 TTS Provider 또는 Client TTS 정책 확정
- [ ] STT 프론트 연동
- [ ] 얼굴 표정 인식 프론트 연동
- [ ] 실제 Push Provider 연결

### P2 — 운영 검증

- [ ] 위치 수집 주기 결정
- [ ] 트래픽 계측
- [ ] 부하 테스트
- [ ] SSE 동시 접속 테스트
- [ ] DB 부하 측정
- [ ] Observability 구축
- [ ] PostgreSQL 운영 환경 검증
- [ ] Event별 보관기간 확정

---

## 24. 다음 개발 Phase

### Phase 1 — 실제 AI 연결

Provider 하나를 선정해 Interface 구현체를 추가하고 Timeout, 오류, 비용, 개인정보 전송 범위를 정의한다.

### Phase 2 — 개인화 효과 검증

중요 표현, 즐겨찾기, 빈도, 최근 사용이 다른 Test User를 만들고 실제 추천 결과를 비교한다.

### Phase 3 — Current Context

`currentPlace`, `currentRoutine`, `timeContext`, `recentCards`, 위치의 정확도·시각을 표현하는 구조를 설계한다.

### Phase 4 — 장소 등록 개선

주소 검색과 Geocoding을 연결하고 보호자가 지도에서 확인·수정할 수 있게 한다.

### Phase 5 — Device 정책

Heartbeat, GPS, Sensor의 전송 주기를 확정하고 PWA/Native/IoT 선택 기준을 만든다.

### Phase 6 — 운영 가능성 검증

Metric을 수집한 뒤 10/100/1,000명 부하와 SSE 동시 연결을 시험하고 병목을 개선한다.

---

## 25. 교수님 발표용 설명

### 30초 버전

말모아는 모든 사용자에게 같은 문장을 보여주는 AAC가 아니라, 사용자의 의사소통 수준과 개인 카드, 즐겨찾기, 실제 사용 기록을 바탕으로 맞춤 표현을 추천하는 시스템입니다. 현재 사용자·카드·기기 연결과 사용 기록, 개인화 AI Context까지 백엔드에 구현했습니다. 다음 단계에서는 실제 AI를 연결하고, 위치·장소·루틴까지 결합해 현재 상황을 이해하는 추천으로 확장하며, 동시에 서버 트래픽과 확장성을 검증할 예정입니다.

### 1분 버전

말모아의 핵심은 AI 문장 생성 자체가 아니라 사용자에게 적응하는 AAC입니다. 먼저 보호자가 사용자의 문장 이해 수준과 최대 문장 길이, 개인 카드와 중요 표현을 설정합니다. 사용자가 AAC 카드를 선택하면 그 기록이 Device와 실제 발화 문구까지 함께 쌓입니다. 현재 백엔드는 이 데이터를 이용해 중요 표현, 즐겨찾기, 자주 사용한 표현, 최근 표현 순으로 개인화 Context를 만들 수 있습니다.

```text
사용자 개인화 설정
→ AAC 사용 기록 축적
→ 위치 / 장소 / 루틴 Context
→ AI가 현재 상황 파악
→ 맞춤 단어/문장 추천
```

아직 실제 생성형 AI와 장소·루틴 Context 연결은 남아 있습니다. 다음 Phase에서 이를 구현하고, 한 명의 반복 Event부터 1,000명 수준의 SSE·위치·센서 Traffic까지 측정해 실제 서비스 가능성도 함께 검증하겠습니다.

---

## 26. 팀원 인수인계

### 현재 Git 상태

```text
Branch: feature/sangbeom-google-login
Commit: 2840502 feat: add personalized AAC context and event contracts
기준 커밋: `2840502 feat: add personalized AAC context and event contracts`
현재 Working Tree: 본 문서와 트래픽 계측·부하 테스트 기반 변경이 아직 미커밋 상태
```

### 실행과 검증

```powershell
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

### 주요 문서

- `docs/API_CONTRACT.md`
- `docs/GUARDIAN_LIVE_FEATURES.md`
- `docs/PERSONALIZED_AAC.md`
- `docs/POSTGRESQL_LOCAL_SETUP.md`

### 개인화 AI 주요 코드

```text
ai/dto/PersonalizedAacContext
ai/service/PersonalizedAacContextBuilder
ai/service/SentenceRecommendationPromptBuilder
ai/service/AacSentenceRecommendationService
ai/spi/AacSentenceRecommendationProvider
ai/controller/PersonalizedAacController
```

### 주요 API

```text
GET   /api/v1/me/aac-users/{id}/communication-profile
PATCH /api/v1/me/aac-users/{id}/communication-profile
GET   /api/v1/me/aac-users/{id}/ai/context
POST  /api/v1/me/aac-users/{id}/ai/recommendations

GET   /api/v1/me/aac-users/{id}/board
GET   /api/v1/me/aac-users/{id}/events
POST  /api/v1/device/card-usage
POST  /api/v1/device/stt-events
POST  /api/v1/device/expression-events
POST  /api/v1/device/locations
```

### 주의사항

- 보호자 API는 Session과 CSRF가 필요하다.
- 사용자 데이터 접근 시 기존 `AacUserAccessService`를 재사용한다.
- Device Event는 `DeviceAuthService` 인증을 거친다.
- 실제 Provider가 없으면 AI 추천 API의 503은 정상 동작이다.
- 위치·STT·표정·카드 기록 보관기간은 현재 미설정이다.
- Push 구독 저장과 실제 Push 전송을 혼동하지 않는다.
- 새 DB 변경은 V7 다음 번호의 Flyway Migration으로 작성한다.

---

## 교수님 예상 질문과 모범 답변

### 1. 사용자가 선택한 카드는 실제 AI 추천에 활용되나요?

백엔드는 선택 기록에서 빈도와 최근 사용 순서를 계산해 개인화 AI Context에 넣도록 구현되어 있습니다. 다만 실제 LLM Provider는 아직 연결되지 않아 최종 생성 문장이 얼마나 달라지는지는 다음 E2E Test에서 검증해야 합니다.

### 2. AI는 현재 상황을 어떻게 알 수 있나요?

현재는 요청에서 전달한 상황 설명과 사용자 프로필·카드·사용 이력을 사용합니다. 저장된 위치·장소·루틴을 자동으로 결합하는 기능은 다음 단계에서 `CurrentContext`로 설계할 예정입니다.

### 3. GPS만으로 상황을 판단하나요?

아닙니다. GPS는 현재 장소를 추정하는 하나의 신호입니다. 시간, 등록 장소, 루틴, 최근 카드, 위치 정확도와 수집 시각을 함께 사용해야 합니다.

### 4. 위치는 어떻게 실시간으로 추적하나요?

현재 Backend는 Device가 보낸 위치를 저장하고 반경을 계산할 수 있습니다. 실제 기기가 위치를 얼마나 자주 측정하고 보내는지는 아직 확정되지 않았으며 배터리와 Traffic을 함께 고려해 결정해야 합니다.

### 5. 앱이 꺼져 있어도 위치를 받을 수 있나요?

현재 PWA 구조만으로 앱 종료 후 지속 추적을 안정적으로 보장한다고 말할 수 없습니다. 이 요구가 필수라면 Native/Capacitor 또는 별도 IoT Device가 필요한지 검증해야 합니다.

### 6. 자주 가는 장소는 어떻게 등록하나요?

현재는 장소명, 주소, 좌표, 반경을 Backend에 저장할 수 있습니다. 보호자가 장소명이나 주소를 검색해 자동 등록하는 기능은 아직 없습니다.

### 7. 왜 주소 검색 기능이 필요한가요?

보호자가 좌표를 직접 알기 어렵고 반드시 현장에 있을 수도 없기 때문입니다. 주소 검색과 Geocoding을 사용하면 장소를 더 정확하고 편리하게 등록할 수 있습니다.

### 8. 위치를 너무 자주 보내면 서버에 부담이 없나요?

부담이 증가합니다. Network, DB 적재량뿐 아니라 Device 배터리도 사용합니다. 실제 이동 중일 때만 보내거나 전송 주기를 조정하는 정책을 부하 테스트 결과와 함께 결정할 계획입니다.

### 9. 사용자 100명/1,000명이 사용하면 서버가 버틸 수 있나요?

현재는 실제 부하 테스트를 하지 않았으므로 버틴다고 단정할 수 없습니다. 예시 주기만 적용해도 1,000명에서 평균 약 317 Requests/sec가 될 수 있어 실제 Scenario 기반 검증이 필요합니다.

### 10. 실제 트래픽을 어떻게 측정할 계획인가요?

먼저 한 사용자의 API 호출과 Byte를 측정하고, k6·JMeter·Gatling 중 적합한 도구를 선정해 10/100/1,000명 Scenario를 실행합니다. SSE 연결 수, 응답시간, 오류율, DB Query, CPU와 Memory를 함께 측정합니다.

### 11. AI가 여러 문장을 추천하면 오히려 사용자가 어려워하지 않나요?

그 문제를 방지하기 위해 현재 최종 추천 API는 문장 하나만 반환하도록 Contract를 제한했습니다. 여러 후보나 설명·목록은 Backend에서 거부합니다.

### 12. 긴급 상황도 AI가 판단하나요?

긴급 표현과 상태는 AI가 임의로 제거하거나 대체하지 않는 규칙 기반 영역으로 두는 방향입니다. AI는 보조 추천을 담당하고 안전 기능은 명시적 규칙과 보호자 설정으로 관리해야 합니다.

### 13. 개인정보인 위치나 얼굴 데이터는 어떻게 관리하나요?

위치는 Device Token으로 인증된 요청만 저장하며 Guardian 소유권 검사를 적용합니다. 얼굴은 원본 사진·영상을 저장하지 않고 분석된 Emotion과 confidence만 받습니다. 보관기간은 설정 가능한 구조만 준비되어 있으며 실제 기간과 동의·열람·삭제 정책은 운영 전 확정해야 합니다.

---

# 지금 당장 해야 할 다음 작업

### 1단계 — 실제 `AacSentenceRecommendationProvider` 연결

Provider를 선정하고 현재 Interface 구현체를 만든다. Timeout, 오류, 비용, 개인정보 전송 범위와 응답 검증을 함께 정의한다.

### 2단계 — 사용 기록 기반 개인화 E2E 확인

즐겨찾기·중요 표현·사용 빈도·최근 카드가 다른 Test User를 만들고 동일한 상황에서 추천이 실제로 달라지는지 확인한다.

### 3단계 — `CurrentContext` 설계

다음 정보를 구조화해 `PersonalizedAacContext`에 결합한다.

- `currentPlace`
- `currentRoutine` / `upcomingRoutine`
- `timeContext`
- `recentCards`
- 위치 수집 시각과 정확도

### 4단계 — 주소 검색 / Geocoding 도입

국내 주소 정확도, 가격, 라이선스를 비교해 Provider를 선정하고 장소 검색→좌표 확인→반경 저장 흐름을 구현한다.

### 5단계 — GPS 수집 정책 결정

앱 활성 상태, 이동 여부, 전송 주기, 정확도 변화, 배터리, PWA Background 제한을 비교해 정책을 확정한다.

### 6단계 — 트래픽 계측 및 부하 테스트

한 사용자 Scenario를 계측하고 10/100/1,000명, SSE 동시 연결, 위치·센서 적재, DB와 AI 병목을 검증한다.

> 다음 Phase의 핵심은 AI를 실제로 연결하는 것에서 끝나는 것이 아니라, 사용 기록·위치·장소·루틴을 결합해 ‘현재 상황을 이해하는 개인화 AAC 추천 시스템’으로 확장하고, 동시에 실제 서비스가 감당할 수 있는 트래픽인지 검증하는 것이다.
