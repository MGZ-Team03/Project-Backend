# SpeakTracker Backend

AI 기반 1:N 외국어 발음 학습 플랫폼 백엔드 서버

---

## 프로젝트 개요

**SpeakTracker**는 AI 기반 얼굴인식과 음성 감지를 활용한 외국어 발음 학습 플랫폼입니다. 학생이 외국어를 학습할 때 실제로 발음하는 시간을 자동 측정하고, 튜터가 다수의 학생을 으로 모니터링하며 피드백을 제공하는 서버리스 아키텍처 기반 시스템입니다.

### 핵심 가치

- **발음 시간 자동 측정**: 입 움직임(MAR) + 음성 감지로 실제 발음 시간 정확히 측정
- **AI 회화 연습**: Claude/ChatGPT API로 자연스러운 영어 대화, AWS Polly TTS로 원어민 음성 제공
- **1:N 관리**: 튜터 1명이 다수 학생(최대 10명)을 효율적으로 모니터링 및 피드백

---

## Tech Stack

| Category       | Technology                     |
|----------------|--------------------------------|
| Language       | Java 21                        |
| Cloud Platform | AWS (Serverless Architecture)  |
| Database       | DynamoDB                       |
| Queue          | SQS (Simple Queue Service)     |
| Storage        | S3                             |
| Authentication | Cognito (JWT Token)            |
| API Gateway    | API Gateway (REST + WebSocket) |
| Speech Service | AWS Polly (TTS), Transcribe (STT) |
| AI             | Claude API / ChatGPT API       |
| Build Tool     | Gradle 8.11.1                  |
| Deployment     | AWS SAM (Serverless Application Model) |

---

## Documentation

| Document                                                      | Description                                       |
|---------------------------------------------------------------|---------------------------------------------------|
| [기능 정의서](./docs/Project_2차_프로젝트_문서작업/기능%20정의서/functional-specification.md) | 사용자 역할, 핵심 기능, 화면별 상세 기능                 |
| [API 명세서](./docs/Project_2차_프로젝트_문서작업/API%20명세서/api-schema.md)                            | API 상세 Request/Response 스키마                     |
| [시스템 아키텍처](./docs/Project_2차_프로젝트_문서작업/시스템%20아키텍처/)    | AWS 서버리스 아키텍처 및 데이터 흐름                          |
| [DB 설계](./docs/Project_2차_프로젝트_문서작업/DB%20설계/database-erd.md)                        | DynamoDB 테이블 구조 및 관계도                            |
| [유스케이스 & 시퀀스](./docs/Project_2차_프로젝트_문서작업/유스케이스-시퀀스/) | 주요 기능별 시퀀스 다이어그램 |
| [API 클래스 다이어그램](./docs/Project_2차_프로젝트_문서작업/API%20명세서/API%20클래스%20다이어그램/) | API 구조 클래스 다이어그램 |

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                                  Client                                      │
│                    (React 18 + Redux Toolkit + Material-UI)                  │
└─────────────────────────────────┬───────────────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                          AWS API Gateway                                     │
│                   REST API  /  WebSocket API                                 │
│  • REST: /auth, /users, /sessions, /statistics, /feedback, /notifications   │
│  • WebSocket: /connect, /disconnect, /message (실시간 상태 업데이트)          │
└───────────────────┬────────────────────────────────┬────────────────────────┘
                    │                                │
                    ▼                                ▼
┌───────────────────────────────────┐   ┌───────────────────────────────────┐
│      AWS Lambda Functions         │   │      AWS Lambda Functions         │
│  ┌─────────────────────────────┐  │   │  ┌─────────────────────────────┐  │
│  │ AuthFunction (Cognito 연동)  │  │   │  │ SentencesWebSocketFunction │  │
│  │ StudentFunction              │  │   │  │ (WebSocket 연결 관리)        │  │
│  │ TutorFunction                │  │   │  └─────────────────────────────┘  │
│  │ SessionFunction              │  │   └───────────────────────────────────┘
│  │ StatisticsFunction           │  │
│  │ DashboardFunction            │  │
│  │ STTFunction (Transcribe)     │  │
│  │ TTSFunction (Polly)          │  │
│  │ SentencesFunction            │  │
│  │ TutorRegisterFunction        │  │
│  │ StudentStatusFunction        │  │
│  └─────────────────────────────┘  │
└───────────────────┬───────────────┘
                    │
    ┌───────────────┼───────────────┬─────────────────┬──────────────────┐
    │               │               │                 │                  │
    ▼               ▼               ▼                 ▼                  ▼
┌─────────┐  ┌──────────┐  ┌──────────────┐  ┌──────────┐  ┌───────────────┐
│DynamoDB │  │   SQS    │  │   S3 Bucket  │  │ Cognito  │  │ CloudWatch    │
│Tables   │  │ Queues   │  │              │  │User Pool │  │ Logs/Metrics  │
└─────────┘  └──────────┘  └──────────────┘  └──────────┘  └───────────────┘
│- Users              │  │- speaktracker-    │  │- 인증/인가 │  │- Lambda 로그 │
│- TutorStudents      │  │  tts-queue        │  │- JWT 토큰  │  │- 메트릭 수집 │
│- TutorRequests      │  │- speaktracker-    │  │발급        │  │- 알람 설정   │
│- Notifications      │  │  ai-chat-queue    │  └──────────┘  └───────────────┘
│- LearningSessions   │  │- speaktracker-    │
│- DailyStatistics    │  │  feedback-queue   │
│- AIConversations    │  │- speaktracker-    │
│- FeedbackMessages   │  │  notification-    │
│- WebSocketConnections│  │  queue           │
└─────────────────────┘  └───────────────────┘
```

---

## Lambda Functions

| Function                      | Description                                  | Trigger                  |
|-------------------------------|----------------------------------------------|--------------------------|
| **AuthFunction**              | 사용자 로그인/회원가입 (Cognito 연동)                  | API Gateway (REST)       |
| **StudentFunction**           | 학생 정보 조회/수정                                 | API Gateway (REST)       |
| **TutorFunction**             | 튜터 정보 조회/수정                                 | API Gateway (REST)       |
| **TutorRegisterFunction**     | 튜터 등록 신청 처리                                 | API Gateway (REST)       |
| **SessionFunction**           | 학습 세션 생성/조회/종료                             | API Gateway (REST)       |
| **StatisticsFunction**        | 학생/튜터 학습 통계 조회                             | API Gateway (REST)       |
| **DashboardFunction**         | 대시보드 데이터 조회 (학생 상태, 통계)                   | API Gateway (REST)       |
| **SentencesFunction**         | 연습 문장 조회/생성                                 | API Gateway (REST)       |
| **STTFunction**               | 음성 → 텍스트 변환 (AWS Transcribe)               | API Gateway (REST)       |
| **TTSFunction**               | 텍스트 → 음성 변환 (AWS Polly)                    | SQS (tts-queue)          |
| **SentencesWebSocketFunction**| WebSocket 연결 관리 및 실시간 메시지 전송              | API Gateway (WebSocket)  |
| **FeedbackPersistenceFunction**| 피드백 메시지 저장                                  | SQS (feedback-queue)     |
| **NotificationPersistenceFunction**| 알림 저장 및 전송                              | SQS (notification-queue) |
| **StudentStatusFunction**     | 학생 상태 업데이트 (발음 중, 대기 중 등)                 | API Gateway (REST)       |

---

## DynamoDB Tables

| Table                          | Partition Key       | Sort Key                    | Description              | GSI/Notes                                      |
|--------------------------------|---------------------|----------------------------|--------------------------|------------------------------------------------|
| **UsersTable**                 | role (S)            | email (S)                  | 사용자 정보 (학생/튜터/관리자)    | -                                              |
| **TutorStudentsTable**         | tutor_email (S)     | student_email (S)          | 튜터-학생 매칭 관계           | GSI: student_email-index                       |
| **TutorRequestsTable**         | request_id (S)      | created_at (N)             | 튜터 매칭 요청               | GSI: student_email-created_at-index,<br>tutor_email_status-created_at-index |
| **LearningSessionsTable**      | student_email (S)   | timestamp (N)              | 학습 세션 기록               | GSI: tutor_email-timestamp-index, TTL: 90일    |
| **DailyStatisticsTable**       | student_email (S)   | date (S)                   | 일별 학습 통계               | TTL: 1년                                       |
| **AIConversationsTable**       | student_email (S)   | timestamp (N)              | AI 대화 내역                | GSI: conversation_id-index, TTL: 30일          |
| **FeedbackMessagesTable**      | composite_key (S)   | timestamp (N)              | 튜터 피드백 메시지            | GSI: student_email-timestamp-index, TTL: 90일  |
| **WebSocketConnectionsTable**  | connection_id (S)   | -                          | WebSocket 연결 정보         | GSI: user_email-index, tutor_email-index, TTL: 24시간 |
| **AsyncJobStatusTable**        | job_id (S)          | -                          | 비동기 작업 상태 추적          | TTL: 7일                                       |
| **SentenceAudioTable**         | sessionId (S)       | sentenceIndex (N)          | 문장 연습 오디오 메타데이터      | TTL: 7일                                       |
| **PronunciationResultsTable**  | student_email (S)   | timestamp (N)              | 발음 평가 결과               | TTL: 30일                                      |
| **NotificationsTable**         | user_email (S)      | notification_id_timestamp (S) | 사용자 알림               | GSI: user_email-is_read-created_at-index, TTL: 90일 |

---

## SQS Queues

| Queue                          | Purpose                                | Consumer Function                  |
|--------------------------------|----------------------------------------|------------------------------------|
| **speaktracker-tts-queue**     | TTS 음성 생성 비동기 처리                     | TTSFunction                        |
| **speaktracker-ai-chat-queue** | AI 대화 응답 생성 비동기 처리                  | AIConversationsFunction (예정)     |
| **speaktracker-feedback-queue**| 튜터 피드백 저장 비동기 처리                    | FeedbackPersistenceFunction        |
| **speaktracker-notification-queue** | 알림 전송 비동기 처리                    | NotificationPersistenceFunction    |

---

## Project Structure

```
Project-Backend/
├── build.gradle              # 루트 Gradle 빌드 설정
├── settings.gradle           # Gradle 서브프로젝트 설정
├── template.yaml             # AWS SAM 템플릿 (전체 인프라 정의)
├── samconfig.toml            # SAM 배포 설정
├── gradlew                   # Gradle Wrapper (실행 스크립트)
├── gradlew.bat               # Gradle Wrapper (Windows)
├── README.md                 # 프로젝트 개요 및 가이드
│
├── docs/                     # 문서
│   ├── functional-specification.md     # 기능 정의서
│   ├── api-schema.md                   # API 스키마
│   ├── architecture-dataflow.md        # 아키텍처 및 데이터 흐름
│   ├── database-erd.md                 # 데이터베이스 ERD
│   ├── database-erd.puml               # PlantUML ERD 다이어그램
│   ├── database-tables.csv             # 테이블 스펙 (CSV)
│   ├── WebSocket_SQS_DynamoDB_Implementation.md  # WebSocket 구현 가이드
│   └── ...                             # 기타 문서
│
├── events/                   # 테스트 이벤트 JSON
│   └── event.json
│
├── gradle/                   # Gradle Wrapper 설정
│   └── wrapper/
│
├── AuthFunction/             # 인증/인가 Lambda
│   ├── build.gradle
│   └── src/
│       └── main/java/com/speaktracker/auth/
│
├── StudentFunction/          # 학생 관련 Lambda
│   ├── build.gradle
│   └── src/
│       └── main/java/com/speaktracker/student/
│
├── TutorFunction/            # 튜터 관련 Lambda
│   ├── build.gradle
│   └── src/
│       └── main/java/com/speaktracker/tutor/
│
├── TutorRegisterFunction/    # 튜터 등록 신청 Lambda
│   ├── build.gradle
│   └── src/
│       └── main/java/com/speaktracker/tutorregister/
│
├── SessionFunction/          # 학습 세션 Lambda
│   ├── build.gradle
│   └── src/
│       └── main/java/com/speaktracker/session/
│
├── StatisticsFunction/       # 통계 Lambda
│   ├── build.gradle
│   └── src/
│       └── main/java/com/speaktracker/statistics/
│
├── DashboardFunction/        # 대시보드 Lambda
│   ├── build.gradle
│   └── src/
│       └── main/java/com/speaktracker/dashboard/
│
├── SentencesFunction/        # 문장 연습 Lambda
│   ├── build.gradle
│   └── src/
│       └── main/java/com/speaktracker/sentences/
│
├── SentencesWebSocketFunction/  # WebSocket Lambda
│   ├── build.gradle
│   └── src/
│       └── main/java/com/speaktracker/websocket/
│
├── STTFunction/              # 음성 → 텍스트 Lambda
│   ├── build.gradle
│   └── src/
│       └── main/java/com/speaktracker/stt/
│
├── TTSFunction/              # 텍스트 → 음성 Lambda
│   ├── build.gradle
│   └── src/
│       └── main/java/com/speaktracker/tts/
│
├── FeedbackPersistenceFunction/  # 피드백 저장 Lambda
│   ├── build.gradle
│   └── src/
│       └── main/java/com/speaktracker/feedback/
│
├── NotificationPersistenceFunction/  # 알림 저장 Lambda
│   ├── build.gradle
│   └── src/
│       └── main/java/com/speaktracker/notification/
│
└── StudentStatusFunction/    # 학생 상태 업데이트 Lambda
    ├── build.gradle
    └── src/
        └── main/java/com/speaktracker/studentstatus/
```

---

## Getting Started

### Prerequisites

- Java 21
- Gradle 8.11.1
- AWS CLI
- AWS SAM CLI
- AWS 계정 및 자격 증명 설정

### Build

전체 프로젝트 빌드:

```bash
./gradlew clean build
```

개별 Function 빌드:

```bash
cd AuthFunction
./gradlew clean build
```

### Local Testing

SAM을 사용한 로컬 테스트:

```bash
# API Gateway 이벤트로 테스트
sam local invoke AuthFunction -e events/event.json

# 로컬 API Gateway 시작
sam local start-api

# 로컬에서 Lambda 함수 호출
curl http://localhost:3000/auth/login
```

### Deploy

AWS에 배포:

```bash
# SAM 빌드
sam build

# 배포 (처음 배포 시 가이드 실행)
sam deploy --guided

# 이후 배포
sam deploy
```

배포 후 API Gateway 엔드포인트 확인:

```bash
aws cloudformation describe-stacks \
  --stack-name speaktracker-prod \
  --query "Stacks[0].Outputs"
```

---

## WebSocket Testing

WebSocket API 테스트 사이트:
- https://piehost.com/websocket-tester

연결 URL 형식:
```
wss://{api-id}.execute-api.{region}.amazonaws.com/{stage}
```

메시지 예시:
```json
{
  "action": "sendMessage",
  "studentId": "student-123",
  "status": "speaking",
  "speakingTime": 45.5
}
```

---

## Environment Variables

Lambda 함수에서 사용하는 환경 변수:

| Variable                  | Description                  |
|---------------------------|------------------------------|
| `USERS_TABLE`             | UsersTable 이름               |
| `TUTOR_STUDENTS_TABLE`    | TutorStudentsTable 이름       |
| `TUTOR_REQUESTS_TABLE`    | TutorRequestsTable 이름       |
| `NOTIFICATIONS_TABLE`     | NotificationsTable 이름       |
| `SESSIONS_TABLE`          | LearningSessionsTable 이름    |
| `STATISTICS_TABLE`        | DailyStatisticsTable 이름     |
| `AI_CONVERSATIONS_TABLE`  | AIConversationsTable 이름     |
| `FEEDBACK_TABLE`          | FeedbackMessagesTable 이름    |
| `CONNECTIONS_TABLE`       | WebSocketConnectionsTable 이름 |

---

## API Endpoints

### REST API

| Method | Path                        | Description              |
|--------|----------------------------|--------------------------|
| POST   | /auth/login                | 로그인                     |
| POST   | /auth/register             | 회원가입                   |
| GET    | /students/{studentId}      | 학생 정보 조회              |
| PUT    | /students/{studentId}      | 학생 정보 수정              |
| GET    | /tutors/{tutorId}          | 튜터 정보 조회              |
| POST   | /tutor-register            | 튜터 등록 신청              |
| GET    | /sessions                  | 세션 목록 조회              |
| POST   | /sessions                  | 세션 생성                  |
| PUT    | /sessions/{sessionId}      | 세션 종료                  |
| GET    | /statistics/{userId}       | 통계 조회                  |
| GET    | /dashboard                 | 대시보드 데이터 조회         |
| GET    | /sentences                 | 연습 문장 조회              |
| POST   | /stt                       | 음성 → 텍스트 변환          |
| POST   | /feedback                  | 피드백 전송                |
| GET    | /notifications             | 알림 목록 조회              |

### WebSocket API

| Action           | Description                      |
|------------------|----------------------------------|
| `$connect`       | WebSocket 연결                    |
| `$disconnect`    | WebSocket 연결 해제                |
| `sendMessage`    | 실시간 메시지 전송 (학생 상태 업데이트)  |

---

## Monitoring & Logging

### CloudWatch Logs

각 Lambda 함수는 자동으로 CloudWatch Logs에 로그를 기록합니다:

```
/aws/lambda/AuthFunction
/aws/lambda/StudentFunction
/aws/lambda/TutorFunction
...
```

### CloudWatch Metrics

주요 모니터링 메트릭:
- Lambda 호출 횟수
- Lambda 실행 시간
- Lambda 오류율
- SQS 큐 메시지 수
- DynamoDB 읽기/쓰기 용량

---

## Team

**3조**: 안창완, 강형원, 황성준

---

## License

This project is licensed under the MIT License.
