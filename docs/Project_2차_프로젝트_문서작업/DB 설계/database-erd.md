# 데이터베이스 ERD 및 관계 설명

## 개요
이 문서는 SpeakTracker의 DynamoDB 테이블 구조와 테이블 간 관계를 정의합니다.

---

## 전체 ERD

```
┌─────────────────┐
│     Users       │ PK: role, SK: email
├─────────────────┤
│ email           │◄──────────┐
│ name            │           │
│ role            │           │ FK (tutor_email)
│ is_accepting    │           │
│ max_student     │           │
│ user_sub        │           │
│ created_at      │           │
└─────────────────┘           │
        △                     │
        │                     │
        │ FK (student_email)  │
        │                     │
┌───────┴─────────────────────┴──────┐
│       TutorStudents                │ PK: tutor_email, SK: student_email
├────────────────────────────────────┤ GSI: student_email-index
│ tutor_email (FK → Users.email)     │
│ student_email (FK → Users.email)   │
│ assigned_at                        │
│ status                             │
│ request_id                         │
│ room                               │
│ updated_at                         │
└────────────────────────────────────┘
        │
        │ FK (request_id)
        │
        ▼
┌────────────────────────────────────┐
│       TutorRequests                │ PK: request_id, SK: created_at
├────────────────────────────────────┤ GSI: student_email-created_at-index
│ request_id                         │     tutor_email_status-created_at-index
│ created_at                         │
│ student_email (FK → Users.email)   │
│ tutor_email (FK → Users.email)     │
│ tutor_email_status                 │
│ status (PENDING/APPROVED/REJECTED) │
│ message                            │
│ processed_at                       │
│ updated_at                         │
│ ttl                                │
└────────────────────────────────────┘


┌────────────────────────────────────┐
│     LearningSessions               │ PK: student_email, SK: timestamp
├────────────────────────────────────┤ GSI: tutor_email-timestamp-index
│ student_email (FK → Users.email)   │ TTL: ✓
│ timestamp                          │
│ session_type                       │
│ duration                           │
│ speaking_duration                  │
│ ttl                                │
└────────────────────────────────────┘
        │
        │ Aggregated to
        │
        ▼
┌────────────────────────────────────┐
│      DailyStatistics               │ PK: student_email, SK: date
├────────────────────────────────────┤ TTL: ✓
│ student_email (FK → Users.email)   │
│ date (YYYY-MM-DD)                  │
│ avg_net_speaking_density           │
│ avg_pace_ratio                     │
│ avg_response_latency               │
│ avg_response_quality               │
│ chat_turns_count                   │
│ pace_ration_count                  │
│ practice_count                     │
│ sessions_count                     │
│ total_recording_time               │
│ total_speaking_time                │
│ ttl                                │
└────────────────────────────────────┘


┌────────────────────────────────────┐
│      AIConversations               │ PK: student_email, SK: timestamp
├────────────────────────────────────┤ GSI: conversation_id-index
│ student_email (FK → Users.email)   │ TTL: ✓
│ timestamp                          │
│ conversation_id                    │
│ difficulty                         │
│ role                               │
│ situation                          │
│ turn_count                         │
│ message                            │
│ topic                              │
│ ttl                                │
└────────────────────────────────────┘


┌────────────────────────────────────┐
│     FeedbackMessages               │ PK: composite_key, SK: timestamp
├────────────────────────────────────┤ GSI: student_email-timestamp-index
│ composite_key (tutor#student)      │ TTL: ✓
│ timestamp                          │
│ tutor_email (FK → Users.email)     │
│ student_email (FK → Users.email)   │
│ message                            │
│ message_type (text/audio)          │
│ audio_url                          │
│ feedback_id                        │
│ session_id                         │
│ websocket_sent                     │
│ ttl                                │
└────────────────────────────────────┘


┌────────────────────────────────────┐
│   WebSocketConnections             │ PK: connection_id
├────────────────────────────────────┤ GSI: user_email-index
│ connection_id                      │     tutor_email-index
│ user_email (FK → Users.email)      │ TTL: ✓
│ connected_at                       │
│ ttl                                │
└────────────────────────────────────┘


┌────────────────────────────────────┐
│      AsyncJobStatus                │ PK: job_id
├────────────────────────────────────┤ TTL: ✓
│ job_id                             │
│ status (PENDING/PROCESSING/        │
│        COMPLETED/FAILED)           │
│ audio_url                          │
│ ttl                                │
└────────────────────────────────────┘


┌────────────────────────────────────┐
│      SentenceAudio                 │ PK: sessionId, SK: sentenceIndex
├────────────────────────────────────┤ TTL: ✓
│ sessionId                          │
│ sentenceIndex                      │
│ completedAt                        │
│ durationMs                         │
│ english                            │
│ korean                             │
│ requestAt                          │
│ s3key                              │
│ status                             │
│ voiceId                            │
│ jobId                              │
│ ttl                                │
└────────────────────────────────────┘


┌────────────────────────────────────┐
│   PronunciationResults             │ PK: student_email, SK: timestamp
├────────────────────────────────────┤ TTL: ✓
│ student_email (FK → Users.email)   │
│ timestamp                          │
│ audio_duration_ms                  │
│ completeness_score                 │
│ extra_words                        │
│ feedback                           │
│ grade                              │
│ missed_words                       │
│ original_text                      │
│ overall_score                      │
│ sentence_id                        │
│ transcribed_text                   │
│ word_accuracy                      │
│ ttl                                │
└────────────────────────────────────┘


┌────────────────────────────────────┐
│      Notifications                 │ PK: user_email, SK: notification_id_timestamp
├────────────────────────────────────┤ GSI: user_email-is_read-created_at-index
│ user_email (FK → Users.email)      │ TTL: ✓
│ notification_id_timestamp          │
│ notification_id                    │
│ type (tutor_match/feedback/system) │
│ title                              │
│ message                            │
│ is_read                            │
│ is_read_created_at                 │
│ created_at                         │
│ sent_via                           │
│ ttl                                │
└────────────────────────────────────┘
```

---

## 테이블별 상세 설명

### 1. Users (사용자 기본 정보)

**용도**: 전체 사용자(학생, 튜터, 관리자)의 기본 정보 저장

**주요 쿼리 패턴**:
- 역할별 사용자 조회: `role` (PK) 기반
- 특정 사용자 조회: `role + email` (PK + SK)

**관계**:
- 1:N → `TutorStudents` (튜터로서)
- 1:N → `TutorStudents` (학생으로서)
- 1:N → `LearningSessions`
- 1:N → `DailyStatistics`
- 1:N → `AIConversations`
- 1:N → `FeedbackMessages` (발신자/수신자)
- 1:N → `PronunciationResults`
- 1:N → `Notifications`
- 1:N → `TutorRequests` (요청자/수신자)

**예상 규모**: 1,000명 (튜터 100명, 학생 900명)

**참고사항**:
- `is_accepting`: 튜터가 신규 학생을 받을 수 있는지 여부
- `max_student`: 튜터가 받을 수 있는 최대 학생 수
- `user_sub`: Cognito User Pool의 고유 식별자

---

### 2. TutorStudents (튜터-학생 매칭)

**용도**: 튜터와 학생 간의 1:N 매칭 관계 관리

**주요 쿼리 패턴**:
- 튜터가 담당하는 학생 목록: PK 쿼리 (`tutor_email`)
- 학생의 튜터 조회: GSI `student_email-index` 사용
- 특정 매칭 조회: `tutor_email + student_email` (PK + SK)

**관계**:
- N:1 → `Users` (tutor_email)
- N:1 → `Users` (student_email)
- N:1 → `TutorRequests` (request_id로 연결)

**예상 규모**: 1,000 레코드 (튜터 100명 × 학생 평균 10명)

**참고사항**:
- `status`: `active` (활성), `inactive` (비활성)
- `room`: 현재 학생이 접속 중인 방 (예: `practice`, `ai-conversation`, `null`)
- `updated_at`: WebSocket으로 실시간 상태 업데이트 시 사용

---

### 3. TutorRequests (튜터 매칭 요청)

**용도**: 학생이 튜터에게 보낸 매칭 요청 관리

**주요 쿼리 패턴**:
- 특정 요청 조회: PK 쿼리 (`request_id`)
- 학생이 보낸 요청 목록: GSI `student_email-created_at-index`
- 튜터가 받은 요청 목록: GSI `tutor_email_status-created_at-index`
  - 예: `tutor@example.com#PENDING` → 대기 중인 요청만 조회

**관계**:
- N:1 → `Users` (student_email)
- N:1 → `Users` (tutor_email)
- 1:1 → `TutorStudents` (승인 시 생성)

**예상 규모**: 월 100건 (승인/거절 후 30일 TTL로 자동 삭제)

**참고사항**:
- `status`: `PENDING` (대기), `APPROVED` (승인), `REJECTED` (거절)
- `tutor_email_status`: `{tutor_email}#{status}` 복합 키 (GSI용)
- `ttl`: 승인/거절 후 30일 뒤 자동 삭제

---

### 4. LearningSessions (학습 세션 기록)

**용도**: 학생의 문장 연습 및 AI 대화 세션 기록

**주요 쿼리 패턴**:
- 학생의 세션 이력: PK 쿼리 (`student_email`)
- 튜터가 학생 세션 조회: GSI `tutor_email-timestamp-index`
- 특정 기간 세션 조회: SK 조건 (`timestamp BETWEEN`)

**관계**:
- N:1 → `Users` (student_email)

**데이터 흐름**:
1. 세션 시작 → `LearningSessions` 레코드 생성
2. 세션 종료 → `duration`, `speaking_duration` 업데이트
3. 일별 집계 → `DailyStatistics` 테이블에 요약

**예상 규모**: 일 1,000건 → 90일 TTL로 90,000건 유지

**참고사항**:
- `session_type`: `practice` (문장 연습), `conversation` (AI 대화)
- `ttl`: 90일 후 자동 삭제 (장기 통계는 DailyStatistics 참조)

---

### 5. DailyStatistics (일별 학습 통계)

**용도**: 학생의 일별 학습 데이터 요약 (LearningSessions에서 집계)

**주요 쿼리 패턴**:
- 특정 날짜 통계: PK + SK 쿼리 (`student_email + date`)
- 주간/월간 통계: SK 범위 쿼리 (`date BETWEEN`)

**관계**:
- N:1 → `Users` (student_email)

**데이터 집계 방식**:
- Lambda 함수가 매일 자정에 전날 `LearningSessions` 데이터를 집계
- 또는 세션 종료 시 실시간 업데이트 (UpdateExpression ADD 사용)

**예상 규모**: 학생 900명 × 365일 = 328,500건 (1년 TTL)

**참고사항**:
- `avg_` 접두사: 평균값
- `_count` 접미사: 횟수
- `total_` 접두사: 합계
- `ttl`: 1년 후 자동 삭제

---

### 6. AIConversations (AI 대화 내역)

**용도**: 학생과 AI 간의 대화 세션 메타데이터 저장

**주요 쿼리 패턴**:
- 학생의 대화 이력: PK 쿼리 (`student_email`)
- 특정 대화 조회: GSI `conversation_id-index`

**관계**:
- N:1 → `Users` (student_email)

**예상 규모**: 일 500건 → 30일 TTL로 15,000건 유지

**참고사항**:
- `difficulty`: `상`, `중`, `하`
- `role`: AI의 역할 (예: `친구`, `선생님`, `상점 직원`)
- `situation`: 대화 상황 설명
- `turn_count`: 대화 턴 수
- `message`: AI가 추천한 문장 (선택사항)
- `ttl`: 30일 후 자동 삭제 (상세 메시지는 별도 저장소 또는 삭제)

---

### 7. FeedbackMessages (튜터 피드백 메시지)

**용도**: 튜터가 학생에게 보낸 피드백 메시지 저장

**주요 쿼리 패턴**:
- 튜터-학생 간 피드백 조회: PK 쿼리 (`composite_key = tutor#student`)
- 학생이 받은 피드백 조회: GSI `student_email-timestamp-index`

**관계**:
- N:1 → `Users` (tutor_email)
- N:1 → `Users` (student_email)

**예상 규모**: 월 2,000건 → 90일 TTL로 6,000건 유지

**참고사항**:
- `composite_key`: `{tutor_email}#{student_email}` 형식
- `message_type`: `text` (텍스트), `audio` (음성 피드백)
- `audio_url`: TTS로 생성된 음성 피드백 URL (선택사항)
- `session_id`: 특정 세션과 연결된 피드백인 경우 기록
- `websocket_sent`: WebSocket으로 전송 여부 (실시간 알림)

---

### 8. WebSocketConnections (WebSocket 연결 상태)

**용도**: 현재 활성화된 WebSocket 연결 관리

**주요 쿼리 패턴**:
- 연결 ID로 조회: PK 쿼리 (`connection_id`)
- 사용자 이메일로 연결 조회: GSI `user_email-index`
- 튜터 이메일로 연결 조회: GSI `tutor_email-index` (튜터 대시보드용)

**관계**:
- N:1 → `Users` (user_email)

**예상 규모**: 최대 200건 (동시 접속 사용자 수)

**참고사항**:
- WebSocket 연결 시 레코드 생성
- WebSocket 연결 해제 시 레코드 삭제
- `ttl`: 연결 후 24시간 뒤 자동 삭제 (오래된 좀비 연결 정리)

---

### 9. AsyncJobStatus (비동기 작업 상태)

**용도**: SQS로 처리되는 비동기 작업(TTS, AI 응답 등)의 상태 추적

**주요 쿼리 패턴**:
- 작업 ID로 상태 조회: PK 쿼리 (`job_id`)

**관계**: 없음 (독립적)

**예상 규모**: 일 1,000건 → 7일 TTL로 7,000건 유지

**참고사항**:
- `status`:
  - `PENDING`: 대기 중
  - `PROCESSING`: 처리 중
  - `COMPLETED`: 완료
  - `FAILED`: 실패
- `audio_url`: TTS 완료 시 S3 URL 저장
- `ttl`: 7일 후 자동 삭제

---

### 10. SentenceAudio (문장 연습 세션별 오디오)

**용도**: 문장 연습 세션의 TTS 오디오 메타데이터 관리

**주요 쿼리 패턴**:
- 세션 ID로 오디오 목록 조회: PK 쿼리 (`sessionId`)
- 특정 문장 오디오 조회: PK + SK 쿼리 (`sessionId + sentenceIndex`)

**관계**: 없음 (세션 ID로만 연결)

**예상 규모**: 일 5,000건 → 7일 TTL로 35,000건 유지

**참고사항**:
- `sessionId`: 클라이언트가 생성한 세션 ID (UUID)
- `sentenceIndex`: 문장 순서 (0부터 시작)
- `s3key`: S3에 저장된 오디오 파일 경로
- `status`: `PENDING`, `COMPLETED`, `FAILED`
- `jobId`: AsyncJobStatus와 연결 (선택사항)
- `ttl`: 7일 후 자동 삭제 (S3 파일도 Lifecycle Policy로 삭제)

---

### 11. PronunciationResults (발음 평가 결과)

**용도**: 학생의 발음 평가 결과 저장

**주요 쿼리 패턴**:
- 학생의 평가 이력: PK 쿼리 (`student_email`)
- 특정 평가 조회: PK + SK 쿼리 (`student_email + timestamp`)

**관계**:
- N:1 → `Users` (student_email)

**예상 규모**: 일 2,000건 → 30일 TTL로 60,000건 유지

**참고사항**:
- `overall_score`: 전체 점수 (0-100)
- `word_accuracy`: 단어 정확도
- `completeness_score`: 완성도 점수
- `grade`: 등급 (`A+`, `A`, `B`, `C` 등)
- `extra_words`: 추가로 말한 단어 목록 (List 타입)
- `missed_words`: 누락된 단어 목록 (List 타입)
- `sentence_id`: 평가한 문장 ID (선택사항)
- `ttl`: 30일 후 자동 삭제 (장기 통계는 DailyStatistics 참조)

---

### 12. Notifications (사용자 알림)

**용도**: 사용자에게 전송된 알림 메시지 저장

**주요 쿼리 패턴**:
- 사용자의 알림 목록: PK 쿼리 (`user_email`)
- 읽지 않은 알림 조회: GSI `user_email-is_read-created_at-index`
  - 예: `user@example.com#false` → 읽지 않은 알림만 조회

**관계**:
- N:1 → `Users` (user_email)

**예상 규모**: 월 5,000건 → 90일 TTL로 15,000건 유지

**참고사항**:
- `notification_id_timestamp`: `{notification_id}#{timestamp}` 복합 키 (SK)
- `type`:
  - `tutor_match`: 튜터 매칭 관련
  - `feedback`: 피드백 수신
  - `system`: 시스템 공지
- `is_read_created_at`: `{is_read}#{created_at}` 복합 키 (GSI용)
- `sent_via`: 전송 채널 목록 (예: `["websocket", "email"]`)
- `ttl`: 90일 후 자동 삭제

---

## 인덱스 전략 요약

| 테이블 | GSI 이름 | Partition Key | Sort Key | 용도 |
|--------|---------|---------------|----------|------|
| TutorStudents | student_email-index | student_email | - | 학생의 튜터 조회 |
| TutorRequests | student_email-created_at-index | student_email | created_at | 학생이 보낸 요청 목록 |
| TutorRequests | tutor_email_status-created_at-index | tutor_email_status | created_at | 튜터가 받은 요청 목록 (상태별) |
| LearningSessions | tutor_email-timestamp-index | tutor_email | timestamp | 튜터가 학생 세션 조회 |
| AIConversations | conversation_id-index | conversation_id | - | 대화 ID로 조회 |
| FeedbackMessages | student_email-timestamp-index | student_email | timestamp | 학생이 받은 피드백 조회 |
| WebSocketConnections | user_email-index | user_email | - | 사용자 연결 조회 |
| WebSocketConnections | tutor_email-index | tutor_email | - | 튜터 연결 조회 |
| Notifications | user_email-is_read-created_at-index | user_email | is_read_created_at | 읽지 않은 알림 조회 |

---

## TTL (Time To Live) 전략

| 테이블 | TTL 기간 | 목적 |
|--------|---------|------|
| LearningSessions | 90일 | 원본 세션은 삭제, DailyStatistics로 집계 유지 |
| DailyStatistics | 1년 | 장기 통계 보관 |
| AIConversations | 30일 | 대화 메타데이터 단기 보관 |
| FeedbackMessages | 90일 | 피드백 이력 보관 |
| WebSocketConnections | 24시간 | 좀비 연결 정리 |
| AsyncJobStatus | 7일 | 작업 상태 단기 보관 |
| SentenceAudio | 7일 | TTS 오디오 메타데이터 |
| PronunciationResults | 30일 | 발음 평가 결과 |
| TutorRequests | 30일 | 승인/거절 후 정리 |
| Notifications | 90일 | 알림 이력 보관 |

---

## 쿼리 패턴 예시

### 1. 튜터가 담당 학생 목록 조회
```python
# PK 쿼리
dynamodb.query(
    TableName='tutor-students',
    KeyConditionExpression='tutor_email = :tutor',
    ExpressionAttributeValues={':tutor': 'tutor@example.com'}
)
```

### 2. 학생의 튜터 조회
```python
# GSI 쿼리
dynamodb.query(
    TableName='tutor-students',
    IndexName='student_email-index',
    KeyConditionExpression='student_email = :student',
    ExpressionAttributeValues={':student': 'student@example.com'}
)
```

### 3. 튜터가 받은 대기 중인 요청 조회
```python
# GSI 쿼리 (복합 키 활용)
dynamodb.query(
    TableName='tutor-requests',
    IndexName='tutor_email_status-created_at-index',
    KeyConditionExpression='tutor_email_status = :key',
    ExpressionAttributeValues={':key': 'tutor@example.com#PENDING'},
    ScanIndexForward=False  # 최신순 정렬
)
```

### 4. 학생의 주간 통계 조회
```python
# SK 범위 쿼리
dynamodb.query(
    TableName='daily-statistics',
    KeyConditionExpression='student_email = :student AND #date BETWEEN :start AND :end',
    ExpressionAttributeNames={'#date': 'date'},
    ExpressionAttributeValues={
        ':student': 'student@example.com',
        ':start': '2025-01-24',
        ':end': '2025-01-30'
    }
)
```

### 5. 읽지 않은 알림 조회
```python
# GSI 쿼리 (복합 키 활용)
dynamodb.query(
    TableName='notifications',
    IndexName='user_email-is_read-created_at-index',
    KeyConditionExpression='user_email = :user AND begins_with(is_read_created_at, :prefix)',
    ExpressionAttributeValues={
        ':user': 'student@example.com',
        ':prefix': 'false#'
    },
    ScanIndexForward=False
)
```

---

## 데이터 정합성 관리

### 1. 참조 무결성 (Referential Integrity)
DynamoDB는 외래 키 제약조건이 없으므로 애플리케이션 레벨에서 관리:

**예시**: 튜터-학생 매칭 생성 시
```java
// 1. TutorRequests 상태 업데이트
updateTutorRequest(requestId, "APPROVED");

// 2. TutorStudents 레코드 생성
createTutorStudent(tutorEmail, studentEmail, requestId);

// 3. Notifications 생성
createNotification(studentEmail, "tutor_match", "튜터 매칭 완료");
```

### 2. 중복 방지
- `TutorStudents`: PK/SK로 자동 방지 (같은 튜터-학생 쌍은 1개만 존재)
- `TutorRequests`: 클라이언트에서 중복 요청 방지 로직 필요

### 3. 트랜잭션
중요한 작업은 DynamoDB Transactions 사용:
```java
// 튜터 요청 승인 시 원자성 보장
TransactWriteItems([
    Update(TutorRequests, status="APPROVED"),
    Put(TutorStudents, ...),
    Put(Notifications, ...)
])
```

---

## 마이그레이션 가이드

### 기존 테이블 수정 시 주의사항

1. **PK/SK 변경 불가**: 새 테이블 생성 후 데이터 이관 필요
2. **GSI 추가**: 기존 테이블에 추가 가능하지만 백필 시간 소요
3. **TTL 활성화**: 언제든지 추가 가능 (기존 데이터는 즉시 삭제되지 않음)

### 데이터 백업
```bash
# DynamoDB Export to S3
aws dynamodb export-table-to-point-in-time \
    --table-arn arn:aws:dynamodb:region:account:table/users \
    --s3-bucket my-backup-bucket \
    --export-format DYNAMODB_JSON
```

---