# 아키텍처 데이터 흐름 및 상세 설명

## 개요
이 문서는 SpeakTracker의 AWS 서버리스 아키텍처에서 데이터가 어떻게 흐르는지, 각 컴포넌트의 역할과 설정을 상세히 설명합니다.

---

## 전체 아키텍처 다이어그램

```
┌─────────────┐
│  Developer  │
└──────┬──────┘
       │ 1. git push
       ▼
┌─────────────┐
│     GIT     │
└──────┬──────┘
       │ 2. GitHub Actions Trigger
       ▼
┌─────────────┐
│Build & Test │
└──────┬──────┘
       │ 3. SAM Build & Deploy
       ▼
┌──────────────────────────────────────────┐
│          AWS Cloud (Deploy)              │
│  ┌────────────────────────────────────┐  │
│  │  Amazon CloudFront (CDN)           │  │
│  │  - React SPA 배포                  │  │
│  │  - S3 Origin                       │  │
│  └────────────┬───────────────────────┘  │
│               │                          │
│  ┌────────────▼───────────────────────┐  │
│  │  Amazon S3 (Frontend)              │  │
│  │  - React Build 파일                │  │
│  │  - 정적 에셋 (이미지, CSS, JS)     │  │
│  └────────────────────────────────────┘  │
└──────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────┐
│                    User (브라우저)                                │
└────┬──────────────────────────────────┬───────────────────────┬──┘
     │                                  │                       │
     │ 4. HTTPS Request                 │ 5. WebSocket Connect  │ 6. S3 직접 접근
     │    (API 호출)                    │    (실시간 통신)      │    (이미지 업로드)
     ▼                                  ▼                       ▼
┌─────────────────────┐   ┌──────────────────────┐   ┌──────────────────┐
│ Amazon API Gateway  │   │ Amazon API Gateway   │   │   Amazon S3      │
│   (REST API)        │   │   (WebSocket)        │   │ (User Content)   │
│                     │   │                      │   │                  │
│ - Cognito Authorizer│   │ - $connect           │   │ - 프로필 이미지  │
│ - Request Validation│   │ - $disconnect        │   │ - 오디오 파일    │
│ - Throttling        │   │ - $default           │   │ - TTS 결과       │
└──────┬──────────────┘   └──────┬───────────────┘   └──────────────────┘
       │                         │
       │ 7. Invoke Lambda        │ 8. Invoke Lambda
       ▼                         ▼
┌─────────────────────────────────────────────────────┐
│            AWS Lambda (Java)                        │
│  ┌───────────────┐  ┌───────────────┐             │
│  │ Auth Handler  │  │ Tutor Handler │  ...        │
│  │ - 회원가입    │  │ - 학생 목록   │             │
│  │ - 로그인      │  │ - 피드백 전송 │             │
│  └───────┬───────┘  └───────┬───────┘             │
│          │                  │                      │
│          │ 9. DB Read/Write │                      │
│          ▼                  ▼                      │
│  ┌──────────────────────────────────────┐          │
│  │        DynamoDB Tables               │          │
│  │ - Users                              │          │
│  │ - TutorStudents                      │          │
│  │ - LearningSessions                   │          │
│  │ - ...                                │          │
│  └──────────────────────────────────────┘          │
│                                                     │
│  ┌───────────────┐                                 │
│  │ SQS Handler   │                                 │
│  │ - TTS 처리    │                                 │
│  │ - AI 응답     │                                 │
│  └───────┬───────┘                                 │
│          │                                          │
│          │ 10. Send Message                        │
│          ▼                                          │
│  ┌──────────────────────────────────────┐          │
│  │        Amazon SQS                    │          │
│  │ - TTS Queue                          │          │
│  │ - AI Chat Queue                      │          │
│  └──────────────────────────────────────┘          │
└─────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────┐
│          External Services                          │
│  ┌───────────────┐  ┌───────────────┐              │
│  │ Amazon Cognito│  │ Claude AI API │              │
│  │ - User Pool   │  │ - Conversation│              │
│  │ - JWT Token   │  │ - Sentence Gen│              │
│  └───────────────┘  └───────────────┘              │
│  ┌───────────────┐  ┌───────────────┐              │
│  │ Amazon Polly  │  │ Amazon SES    │              │
│  │ - TTS         │  │ - Email       │              │
│  └───────────────┘  └───────────────┘              │
│  ┌───────────────┐                                  │
│  │ Amazon        │                                  │
│  │ Transcribe    │                                  │
│  │ - STT         │                                  │
│  └───────────────┘                                  │
└─────────────────────────────────────────────────────┘
```

---

## 주요 데이터 흐름 시나리오

### 시나리오 1: 사용자 회원가입 및 로그인

```
[1] 사용자 → CloudFront → React App
     ↓
[2] 회원가입 폼 작성 (이메일, 비밀번호, 이름, 역할)
     ↓
[3] POST /api/auth/register
     ↓
[4] API Gateway → Lambda (AuthHandler)
     ↓
[5] Lambda → Cognito.signUp()
     │         └─→ Cognito User Pool에 사용자 생성
     ↓
[6] Lambda → DynamoDB (Users 테이블)
     │         └─→ {role: "student", email: "user@example.com", name: "홍길동"}
     ↓
[7] Lambda → SES (이메일 인증 코드 발송)
     ↓
[8] Response → 클라이언트
     └─→ {"message": "회원가입 완료. 이메일을 확인하세요."}

[사용자가 이메일에서 인증 코드 확인]
     ↓
[9] POST /api/auth/confirm
     ↓
[10] Lambda → Cognito.confirmSignUp(code)
     ↓
[11] Response → 클라이언트
     └─→ {"message": "인증 완료"}

[로그인]
[12] POST /api/auth/login
     ↓
[13] Lambda → Cognito.initiateAuth(email, password)
     ↓
[14] Cognito → JWT Token 발급 (AccessToken, RefreshToken, IdToken)
     ↓
[15] Lambda → DynamoDB (Users 조회)
     │         └─→ role, name 등 추가 정보 조회
     ↓
[16] Response → 클라이언트
     └─→ {
           "accessToken": "eyJhbGc...",
           "refreshToken": "eyJhbGc...",
           "user": {"email": "user@example.com", "role": "student", "name": "홍길동"}
         }

[이후 모든 API 요청]
[17] Authorization: Bearer <accessToken>
     ↓
[18] API Gateway → Cognito Authorizer (토큰 검증)
     │         └─→ 유효하면 Lambda 호출
     │         └─→ 무효하면 401 Unauthorized 반환
```

**주요 컴포넌트**:
- **Cognito**: 인증 토큰 관리, 비밀번호 해싱, 이메일 인증
- **DynamoDB Users**: 사용자 프로필 정보 (역할, 이름 등)
- **SES**: 이메일 인증 코드, 비밀번호 재설정 이메일 발송
- **API Gateway Authorizer**: JWT 토큰 자동 검증

---

### 시나리오 2: 학생이 튜터 매칭 요청

```
[학생이 튜터 검색]
[1] GET /api/tutors?search=김튜터
     ↓
[2] Lambda → DynamoDB (Users 테이블)
     │         └─→ Query: role="tutor" AND contains(name, "김")
     ↓
[3] Response → 클라이언트
     └─→ {
           "tutors": [
             {"email": "kim@tutor.com", "name": "김튜터", "isAccepting": true, "maxStudents": 10}
           ]
         }

[학생이 튜터에게 요청]
[4] POST /api/tutors/kim@tutor.com/request
     Body: {"message": "영어 회화 실력을 향상시키고 싶습니다."}
     ↓
[5] Lambda → DynamoDB (TutorRequests 테이블)
     │         └─→ Put: {
     │               requestId: "req-uuid",
     │               studentEmail: "student@example.com",
     │               tutorEmail: "kim@tutor.com",
     │               status: "PENDING",
     │               message: "...",
     │               createdAt: timestamp,
     │               tutorEmailStatus: "kim@tutor.com#PENDING"
     │             }
     ↓
[6] Lambda → DynamoDB (Notifications 테이블)
     │         └─→ Put: {
     │               userEmail: "kim@tutor.com",
     │               type: "tutor_match",
     │               title: "새로운 매칭 요청",
     │               message: "student@example.com님이 매칭을 요청했습니다."
     │             }
     ↓
[7] Lambda → WebSocket (튜터에게 실시간 알림)
     │         └─→ WebSocketConnections 테이블에서 튜터의 connection_id 조회
     │         └─→ API Gateway Management API로 메시지 전송
     ↓
[8] Response → 학생 클라이언트
     └─→ {"requestId": "req-uuid", "status": "PENDING"}

[튜터가 승인]
[9] POST /api/tutors/requests/req-uuid/approve
     ↓
[10] Lambda → DynamoDB TransactWriteItems
     │         └─→ Update: TutorRequests (status="APPROVED", processedAt=now)
     │         └─→ Put: TutorStudents {
     │               tutorEmail: "kim@tutor.com",
     │               studentEmail: "student@example.com",
     │               assignedAt: now,
     │               status: "active",
     │               requestId: "req-uuid"
     │             }
     │         └─→ Put: Notifications {
     │               userEmail: "student@example.com",
     │               type: "tutor_match",
     │               title: "매칭 완료",
     │               message: "김튜터님과 매칭되었습니다."
     │             }
     ↓
[11] Lambda → WebSocket (학생에게 실시간 알림)
     ↓
[12] Response → 튜터 클라이언트
     └─→ {"message": "승인 완료"}
```

**주요 컴포넌트**:
- **TutorRequests 테이블**: 매칭 요청 관리, GSI로 튜터별 대기 중 요청 조회
- **TutorStudents 테이블**: 매칭 완료 후 관계 저장
- **Notifications 테이블**: 알림 이력 저장
- **WebSocket**: 실시간 알림 전송 (WebSocketConnections 테이블로 연결 관리)
- **DynamoDB Transactions**: 여러 테이블을 원자적으로 업데이트

---

### 시나리오 3: 학생의 문장 연습 (TTS + STT + 발음 평가)

```
[학생이 문장 연습 시작]
[1] POST /api/sessions/start
     Body: {"sessionType": "practice", "metadata": {"difficulty": "중"}}
     ↓
[2] Lambda → DynamoDB (LearningSessions 테이블)
     │         └─→ Put: {
     │               studentEmail: "student@example.com",
     │               timestamp: "2025-01-30T18:00:00Z",
     │               sessionType: "practice",
     │               duration: 0
     │             }
     ↓
[3] Response → 클라이언트
     └─→ {"sessionId": "sess-uuid", "startTime": "..."}

[학생이 문장 생성 요청]
[4] POST /api/sentences/generate
     Body: {"difficulty": "중", "topic": "일상 대화", "count": 5}
     ↓
[5] Lambda → Claude AI API
     │         └─→ Prompt: "Generate 5 intermediate English sentences about daily conversation"
     ↓
[6] Claude AI → Response
     └─→ [
           {"english": "How was your day?", "korean": "오늘 하루 어땠어요?"},
           ...
         ]
     ↓
[7] Lambda → 각 문장에 대해 TTS 요청
     │         └─→ SQS (TTS Queue)로 메시지 전송 (비동기 처리)
     ├─→ Message 1: {jobId: "tts-1", text: "How was your day?", voiceId: "Matthew", sessionId: "sess-uuid", index: 0}
     ├─→ Message 2: ...
     └─→ ...
     ↓
[8] Response → 클라이언트
     └─→ {
           "sentences": [...],
           "sessionId": "gen-sess-uuid"
         }

[SQS TTS Handler가 메시지 처리]
[9] SQS → Lambda (TTS Handler)
     ↓
[10] Lambda → Polly.synthesizeSpeech(text="How was your day?", voiceId="Matthew")
     ↓
[11] Polly → MP3 Audio Stream 반환
     ↓
[12] Lambda → S3.putObject("bucket/tts/sess-uuid-0.mp3", audioStream)
     ↓
[13] Lambda → DynamoDB (SentenceAudio 테이블)
     │         └─→ Put: {
     │               sessionId: "sess-uuid",
     │               sentenceIndex: 0,
     │               english: "How was your day?",
     │               korean: "오늘 하루 어땠어요?",
     │               s3key: "tts/sess-uuid-0.mp3",
     │               status: "COMPLETED",
     │               durationMs: 2000,
     │               completedAt: now
     │             }
     ↓
[14] Lambda → DynamoDB (AsyncJobStatus 테이블)
     │         └─→ Update: {
     │               jobId: "tts-1",
     │               status: "COMPLETED",
     │               audioUrl: "https://s3.amazonaws.com/bucket/tts/sess-uuid-0.mp3"
     │             }

[클라이언트가 TTS 상태 조회 (폴링 또는 WebSocket)]
[15] GET /api/tts/status/tts-1
     ↓
[16] Lambda → DynamoDB (AsyncJobStatus 조회)
     ↓
[17] Response → 클라이언트
     └─→ {
           "status": "COMPLETED",
           "audioUrl": "https://s3.amazonaws.com/bucket/tts/sess-uuid-0.mp3"
         }

[학생이 오디오 재생 후 녹음]
[클라이언트에서 Web Audio API로 녹음]
     ↓
[18] POST /api/stt/evaluate
     Content-Type: multipart/form-data
     Body: {
       audioFile: <Binary>,
       originalText: "How was your day?"
     }
     ↓
[19] Lambda → S3.putObject("bucket/user-audio/student-uuid.webm", audioFile)
     ↓
[20] Lambda → Transcribe.startTranscriptionJob(audioUri="s3://bucket/user-audio/student-uuid.webm")
     ↓
[21] Lambda → (폴링 또는 비동기) Transcribe 결과 대기
     ↓
[22] Transcribe → {"transcribedText": "How was your day"}
     ↓
[23] Lambda → 발음 평가 로직
     │         └─→ 원본: "How was your day?"
     │         └─→ 인식: "How was your day"
     │         └─→ 정확도 계산: 95% (단어 정확도)
     │         └─→ 누락 단어: [] (없음)
     │         └─→ 추가 단어: [] (없음)
     ↓
[24] Lambda → DynamoDB (PronunciationResults 테이블)
     │         └─→ Put: {
     │               studentEmail: "student@example.com",
     │               timestamp: now,
     │               originalText: "How was your day?",
     │               transcribedText: "How was your day",
     │               overallScore: 95.0,
     │               wordAccuracy: 100.0,
     │               completenessScore: 90.0,
     │               grade: "A",
     │               feedback: "Great pronunciation!",
     │               missedWords: [],
     │               extraWords: [],
     │               audioDurationMs: 2500
     │             }
     ↓
[25] Response → 클라이언트
     └─→ {
           "transcribedText": "How was your day",
           "overallScore": 95.0,
           "grade": "A",
           "feedback": "Great pronunciation!"
         }

[세션 종료]
[26] POST /api/sessions/end
     Body: {"sessionId": "sess-uuid"}
     ↓
[27] Lambda → DynamoDB (LearningSessions 테이블)
     │         └─→ Update: {
     │               duration: 1800 (초),
     │               speakingDuration: 1200 (초)
     │             }
     ↓
[28] Lambda → DynamoDB (DailyStatistics 테이블)
     │         └─→ UpdateExpression: ADD practice_count 1, total_speaking_time 1200
     ↓
[29] Response → 클라이언트
     └─→ {"message": "세션 종료", "duration": 1800}
```

**주요 컴포넌트**:
- **Claude AI**: 학생 레벨에 맞는 문장 생성
- **SQS**: TTS 작업을 비동기로 처리 (Lambda 타임아웃 회피)
- **Polly**: 영어 문장을 MP3 음성으로 변환
- **Transcribe**: 학생 음성을 텍스트로 변환 (STT)
- **S3**: TTS 결과, 사용자 녹음 파일 저장
- **DynamoDB**: 세션, 오디오 메타데이터, 평가 결과 저장

---

### 시나리오 4: AI 대화 (WebSocket + Claude AI)

```
[학생이 AI 대화 시작]
[1] POST /api/ai/chat/start
     Body: {"difficulty": "중", "topic": "일상 대화", "role": "친구"}
     ↓
[2] Lambda → Claude AI API
     │         └─→ System Prompt: "You are a friendly English conversation partner..."
     │         └─→ Request: "Start a casual conversation at intermediate level"
     ↓
[3] Claude AI → "Hey! Long time no see. How have you been?"
     ↓
[4] Lambda → SQS (TTS Queue)
     │         └─→ Message: {jobId: "tts-ai-1", text: "Hey! Long time...", voiceId: "Matthew"}
     ↓
[5] Lambda → DynamoDB (AIConversations 테이블)
     │         └─→ Put: {
     │               studentEmail: "student@example.com",
     │               timestamp: now,
     │               conversationId: "conv-uuid",
     │               difficulty: "중",
     │               role: "친구",
     │               situation: "오랜만에 친구와 만남",
     │               turnCount: 0,
     │               message: "Hey! Long time no see..."
     │             }
     ↓
[6] Response → 클라이언트
     └─→ {
           "conversationId": "conv-uuid",
           "firstMessage": "Hey! Long time no see...",
           "audioJobId": "tts-ai-1"
         }

[클라이언트가 TTS 완료 대기 후 음성 재생]

[학생이 응답 (음성 녹음)]
[7] POST /api/ai/chat/message
     Body: {
       conversationId: "conv-uuid",
       userMessage: "I've been great! Just busy with work.",
       audioData: "base64-encoded-webm"
     }
     ↓
[8] Lambda → SQS (AI Chat Queue) - 비동기 처리
     │         └─→ Message: {
     │               requestId: "req-xyz",
     │               conversationId: "conv-uuid",
     │               userMessage: "I've been great!...",
     │               audioData: "..."
     │             }
     ↓
[9] Response → 클라이언트 (즉시 반환)
     └─→ {
           "requestId": "req-xyz",
           "status": "PROCESSING",
           "estimatedTime": 3000
         }

[SQS AI Chat Handler가 메시지 처리]
[10] SQS → Lambda (AI Chat Handler)
     ↓
[11] Lambda → Transcribe (사용자 음성 → 텍스트)
     │         └─→ 인식 결과: "I've been great just busy with work"
     ↓
[12] Lambda → Claude AI API
     │         └─→ Context: [
     │               {role: "assistant", content: "Hey! Long time no see..."},
     │               {role: "user", content: "I've been great just busy with work"}
     │             ]
     │         └─→ Claude: "That's great to hear! What have you been working on?"
     ↓
[13] Lambda → SQS (TTS Queue)
     │         └─→ Message: {jobId: "tts-ai-2", text: "That's great to hear!..."}
     ↓
[14] Lambda → DynamoDB (AIConversations 테이블)
     │         └─→ Update: {
     │               turnCount: turnCount + 1,
     │               message: "That's great to hear!..."  # AI 응답 저장
     │             }
     ↓
[15] Lambda → DynamoDB (AsyncJobStatus 테이블)
     │         └─→ Update: {
     │               requestId: "req-xyz",
     │               status: "COMPLETED",
     │               aiMessage: "That's great to hear!...",
     │               audioJobId: "tts-ai-2"
     │             }
     ↓
[16] Lambda → WebSocket (학생에게 실시간 알림)
     │         └─→ 클라이언트에 "AI 응답 준비 완료" 메시지 전송

[클라이언트가 상태 조회 (또는 WebSocket으로 수신)]
[17] GET /api/ai/chat/status/req-xyz
     ↓
[18] Response → 클라이언트
     └─→ {
           "status": "COMPLETED",
           "aiMessage": "That's great to hear!...",
           "audioJobId": "tts-ai-2",
           "turnCount": 2
         }

[대화 계속... 10턴 정도 진행 후 종료]
```

**주요 컴포넌트**:
- **Claude AI**: 학생 레벨에 맞는 자연스러운 영어 대화 생성
- **SQS**: AI 응답 생성을 비동기로 처리 (3-5초 소요)
- **WebSocket**: AI 응답 준비 완료 시 실시간 알림
- **Transcribe**: 학생 음성 인식
- **Polly**: AI 응답을 음성으로 변환
- **AIConversations 테이블**: 대화 컨텍스트 저장 (Claude AI에 전달)

---

### 시나리오 5: 튜터의 실시간 학생 모니터링 (WebSocket)

```
[튜터가 대시보드 접속]
[1] WebSocket Connect
     wss://xxx.execute-api.region.amazonaws.com/prod?token=<jwt>
     ↓
[2] API Gateway → Lambda ($connect)
     ↓
[3] Lambda → Cognito Authorizer (JWT 검증)
     ↓
[4] Lambda → DynamoDB (WebSocketConnections 테이블)
     │         └─→ Put: {
     │               connectionId: "abc123",
     │               userEmail: "tutor@example.com",
     │               connectedAt: now,
     │               ttl: now + 24h
     │             }
     ↓
[5] Response → 클라이언트 (연결 성공)

[학생이 문장 연습 시작]
[6] POST /api/student-status
     Body: {room: "practice", isLearning: true}
     ↓
[7] Lambda → DynamoDB (TutorStudents 테이블)
     │         └─→ Update: {
     │               room: "practice",
     │               updatedAt: now
     │             }
     ↓
[8] Lambda → DynamoDB (WebSocketConnections 조회)
     │         └─→ Query: tutorEmail-index, tutorEmail="tutor@example.com"
     │         └─→ 결과: connectionId="abc123"
     ↓
[9] Lambda → API Gateway Management API
     │         └─→ postToConnection(connectionId="abc123", data={
     │               type: "student_status_update",
     │               studentEmail: "student@example.com",
     │               room: "practice",
     │               isLearning: true
     │             })
     ↓
[10] WebSocket → 튜터 클라이언트
     └─→ 실시간으로 학생 상태 변경 표시

[튜터가 학생에게 피드백 전송]
[11] POST /api/tutor/feedback
     Body: {
       studentEmail: "student@example.com",
       message: "오늘 발음 연습 잘했어요!",
       messageType: "text"
     }
     ↓
[12] Lambda → DynamoDB (FeedbackMessages 테이블)
     │         └─→ Put: {
     │               compositeKey: "tutor@example.com#student@example.com",
     │               timestamp: now,
     │               message: "오늘 발음 연습 잘했어요!",
     │               messageType: "text",
     │               websocketSent: false
     │             }
     ↓
[13] Lambda → DynamoDB (WebSocketConnections 조회)
     │         └─→ Query: userEmail-index, userEmail="student@example.com"
     │         └─→ 결과: connectionId="def456"
     ↓
[14] Lambda → API Gateway Management API
     │         └─→ postToConnection(connectionId="def456", data={
     │               type: "feedback",
     │               tutorEmail: "tutor@example.com",
     │               message: "오늘 발음 연습 잘했어요!"
     │             })
     ↓
[15] Lambda → DynamoDB (FeedbackMessages 업데이트)
     │         └─→ Update: websocketSent=true
     ↓
[16] Response → 튜터 클라이언트
     └─→ {"message": "피드백 전송 완료"}

[WebSocket 연결 해제]
[17] WebSocket Disconnect
     ↓
[18] API Gateway → Lambda ($disconnect)
     ↓
[19] Lambda → DynamoDB (WebSocketConnections 테이블)
     │         └─→ Delete: connectionId="abc123"
```

**주요 컴포넌트**:
- **WebSocket API Gateway**: 양방향 실시간 통신
- **WebSocketConnections 테이블**: 활성 연결 관리 (connectionId ↔ userEmail 매핑)
- **API Gateway Management API**: Lambda에서 클라이언트로 메시지 푸시
- **GSI**: userEmail-index, tutorEmail-index로 빠른 연결 조회

---

## 컴포넌트별 상세 설정

### 1. AWS Lambda 설정

| Lambda 함수 | 메모리 | 타임아웃 | 동시성 | 주요 역할 |
|------------|--------|---------|-------|----------|
| AuthHandler | 512 MB | 30초 | 100 | 회원가입, 로그인, 토큰 갱신 |
| TutorHandler | 512 MB | 30초 | 50 | 튜터 관련 API (학생 목록, 피드백) |
| StudentHandler | 512 MB | 30초 | 100 | 학생 관련 API (튜터 조회, 상태 업데이트) |
| SentenceHandler | 1024 MB | 60초 | 100 | 문장 생성 (Claude AI 호출) |
| TTSHandler | 512 MB | 60초 | 50 | SQS → TTS 처리 (Polly) |
| STTHandler | 1024 MB | 120초 | 20 | 발음 평가 (Transcribe) |
| AIChatHandler | 1024 MB | 120초 | 30 | SQS → AI 대화 처리 (Claude AI) |
| WebSocketHandler | 512 MB | 30초 | 100 | WebSocket 연결/해제/메시지 |
| SessionHandler | 512 MB | 30초 | 100 | 세션 시작/종료 |
| StatisticsHandler | 512 MB | 30초 | 50 | 통계 조회/기록 |

**환경 변수 예시**:
```yaml
Environment:
  Variables:
    DYNAMODB_USERS_TABLE: users
    DYNAMODB_TUTOR_STUDENTS_TABLE: tutor-students
    S3_BUCKET: speaktracker-storage
    SQS_TTS_QUEUE_URL: https://sqs.region.amazonaws.com/account/tts-queue
    COGNITO_USER_POOL_ID: region_xxxxx
    CLAUDE_API_KEY: sk-ant-xxx
    POLLY_VOICE_ID: Matthew
```

**IAM 권한 예시**:
```yaml
Policies:
  - DynamoDBCrudPolicy:
      TableName: !Ref UsersTable
  - S3CrudPolicy:
      BucketName: !Ref StorageBucket
  - SQSSendMessagePolicy:
      QueueName: !GetAtt TTSQueue.QueueName
  - Statement:
      - Effect: Allow
        Action:
          - cognito-idp:AdminGetUser
          - cognito-idp:AdminInitiateAuth
        Resource: !GetAtt UserPool.Arn
```

---

### 2. API Gateway 설정

#### REST API
```yaml
Type: AWS::Serverless::Api
Properties:
  StageName: prod
  Auth:
    DefaultAuthorizer: CognitoAuthorizer
    Authorizers:
      CognitoAuthorizer:
        UserPoolArn: !GetAtt UserPool.Arn
  Cors:
    AllowOrigin: "'*'"
    AllowHeaders: "'Content-Type,Authorization'"
    AllowMethods: "'GET,POST,PUT,DELETE,OPTIONS'"
  ThrottleSettings:
    BurstLimit: 200  # 버스트 시 초당 최대 요청 수
    RateLimit: 100   # 평균 초당 요청 수
  MethodSettings:
    - ResourcePath: "/*"
      HttpMethod: "*"
      LoggingLevel: INFO
      DataTraceEnabled: true
```

#### WebSocket API
```yaml
Type: AWS::ApiGatewayV2::Api
Properties:
  Name: SpeakTracker-WebSocket
  ProtocolType: WEBSOCKET
  RouteSelectionExpression: "$request.body.action"

Routes:
  ConnectRoute:
    Type: AWS::ApiGatewayV2::Route
    Properties:
      RouteKey: $connect
      AuthorizationType: AWS_IAM
      Target: !Join ["/", ["integrations", !Ref ConnectIntegration]]

  DisconnectRoute:
    Type: AWS::ApiGatewayV2::Route
    Properties:
      RouteKey: $disconnect
      Target: !Join ["/", ["integrations", !Ref DisconnectIntegration]]

  DefaultRoute:
    Type: AWS::ApiGatewayV2::Route
    Properties:
      RouteKey: $default
      Target: !Join ["/", ["integrations", !Ref DefaultIntegration]]
```

---

### 3. SQS 설정

```yaml
TTSQueue:
  Type: AWS::SQS::Queue
  Properties:
    QueueName: speaktracker-tts-queue
    VisibilityTimeout: 120  # Lambda 타임아웃보다 길게 설정
    MessageRetentionPeriod: 86400  # 1일
    ReceiveMessageWaitTimeSeconds: 20  # Long Polling
    RedrivePolicy:
      deadLetterTargetArn: !GetAtt TTSDLQueue.Arn
      maxReceiveCount: 3  # 3번 실패 시 DLQ로 이동

TTSDLQueue:
  Type: AWS::SQS::Queue
  Properties:
    QueueName: speaktracker-tts-dlq
    MessageRetentionPeriod: 1209600  # 14일

AIChatQueue:
  Type: AWS::SQS::Queue
  Properties:
    QueueName: speaktracker-ai-chat-queue
    VisibilityTimeout: 180  # AI 응답 생성 시간 고려
    MessageRetentionPeriod: 86400
    RedrivePolicy:
      deadLetterTargetArn: !GetAtt AIChatDLQueue.Arn
      maxReceiveCount: 2
```

**Lambda Event Source Mapping**:
```yaml
TTSQueueEventSourceMapping:
  Type: AWS::Lambda::EventSourceMapping
  Properties:
    EventSourceArn: !GetAtt TTSQueue.Arn
    FunctionName: !Ref TTSHandlerFunction
    BatchSize: 10  # 한 번에 처리할 메시지 수
    MaximumBatchingWindowInSeconds: 5  # 배치 대기 시간
```

---

### 4. DynamoDB 설정

**Read/Write Capacity 설정** (On-Demand 모드 권장):
```yaml
UsersTable:
  Type: AWS::DynamoDB::Table
  Properties:
    TableName: users
    BillingMode: PAY_PER_REQUEST  # On-Demand (Auto Scaling)
    # 또는 Provisioned 모드
    # BillingMode: PROVISIONED
    # ProvisionedThroughput:
    #   ReadCapacityUnits: 5
    #   WriteCapacityUnits: 5
    AttributeDefinitions:
      - AttributeName: role
        AttributeType: S
      - AttributeName: email
        AttributeType: S
    KeySchema:
      - AttributeName: role
        KeyType: HASH
      - AttributeName: email
        KeyType: RANGE
    StreamSpecification:
      StreamViewType: NEW_AND_OLD_IMAGES  # DynamoDB Streams 활성화
    PointInTimeRecoverySpecification:
      PointInTimeRecoveryEnabled: true  # 백업 활성화
```

**TTL 설정**:
```yaml
TimeToLiveSpecification:
  AttributeName: ttl
  Enabled: true
```

---

### 5. S3 설정

```yaml
StorageBucket:
  Type: AWS::S3::Bucket
  Properties:
    BucketName: speaktracker-storage
    CorsConfiguration:
      CorsRules:
        - AllowedOrigins:
            - "*"
          AllowedMethods:
            - GET
            - PUT
            - POST
          AllowedHeaders:
            - "*"
          MaxAge: 3000
    LifecycleConfiguration:
      Rules:
        - Id: DeleteOldTTS
          Status: Enabled
          Prefix: tts/
          ExpirationInDays: 7  # TTS 파일은 7일 후 삭제
        - Id: DeleteOldUserAudio
          Status: Enabled
          Prefix: user-audio/
          ExpirationInDays: 30
    PublicAccessBlockConfiguration:
      BlockPublicAcls: true
      BlockPublicPolicy: true
      IgnorePublicAcls: true
      RestrictPublicBuckets: true
```

**S3 버킷 용도**:
- `tts/`: Polly TTS 결과 (7일 TTL)
- `user-audio/`: 학생 녹음 파일 (30일 TTL)
- `profile-images/`: 프로필 이미지 (영구 보관)
- `frontend/`: React 빌드 파일 (CloudFront Origin)

---

### 6. Cognito 설정

```yaml
UserPool:
  Type: AWS::Cognito::UserPool
  Properties:
    UserPoolName: speaktracker-users
    AutoVerifiedAttributes:
      - email
    UsernameAttributes:
      - email
    Schema:
      - Name: email
        AttributeDataType: String
        Required: true
      - Name: name
        AttributeDataType: String
        Required: true
    Policies:
      PasswordPolicy:
        MinimumLength: 8
        RequireUppercase: true
        RequireLowercase: true
        RequireNumbers: true
        RequireSymbols: true
    EmailConfiguration:
      EmailSendingAccount: COGNITO_DEFAULT
      # 또는 SES 사용
      # EmailSendingAccount: DEVELOPER
      # SourceArn: !GetAtt SESIdentity.Arn

UserPoolClient:
  Type: AWS::Cognito::UserPoolClient
  Properties:
    ClientName: speaktracker-web-client
    UserPoolId: !Ref UserPool
    GenerateSecret: false
    ExplicitAuthFlows:
      - ALLOW_USER_PASSWORD_AUTH
      - ALLOW_REFRESH_TOKEN_AUTH
    AccessTokenValidity: 1  # 1시간
    RefreshTokenValidity: 30  # 30일
    IdTokenValidity: 1
    TokenValidityUnits:
      AccessToken: hours
      RefreshToken: days
      IdToken: hours
```

---

### 7. CloudFront 설정

```yaml
CloudFrontDistribution:
  Type: AWS::CloudFront::Distribution
  Properties:
    DistributionConfig:
      Enabled: true
      Origins:
        - Id: S3Origin
          DomainName: !GetAtt FrontendBucket.RegionalDomainName
          S3OriginConfig:
            OriginAccessIdentity: !Sub "origin-access-identity/cloudfront/${CloudFrontOAI}"
      DefaultCacheBehavior:
        TargetOriginId: S3Origin
        ViewerProtocolPolicy: redirect-to-https
        AllowedMethods: [GET, HEAD, OPTIONS]
        CachedMethods: [GET, HEAD]
        ForwardedValues:
          QueryString: false
          Cookies:
            Forward: none
        MinTTL: 0
        DefaultTTL: 86400  # 1일
        MaxTTL: 31536000   # 1년
      CustomErrorResponses:
        - ErrorCode: 404
          ResponseCode: 200
          ResponsePagePath: /index.html  # React Router SPA 지원
      DefaultRootObject: index.html
```

---

## 에러 핸들링 및 재시도 전략

### 1. Lambda 에러 핸들링
```java
try {
    // 비즈니스 로직
} catch (ValidationException e) {
    return APIGatewayProxyResponseEvent()
        .withStatusCode(400)
        .withBody(gson.toJson(Map.of(
            "error", "INVALID_REQUEST",
            "message", e.getMessage()
        )));
} catch (NotFoundException e) {
    return APIGatewayProxyResponseEvent()
        .withStatusCode(404)
        .withBody(gson.toJson(Map.of(
            "error", "NOT_FOUND",
            "message", e.getMessage()
        )));
} catch (Exception e) {
    logger.error("Unexpected error", e);
    return APIGatewayProxyResponseEvent()
        .withStatusCode(500)
        .withBody(gson.toJson(Map.of(
            "error", "INTERNAL_SERVER_ERROR",
            "message", "서버 오류가 발생했습니다."
        )));
}
```

### 2. SQS 재시도 및 DLQ
- **Visibility Timeout**: Lambda 타임아웃보다 2배 길게 설정
- **maxReceiveCount**: 3회 실패 시 DLQ로 이동
- **DLQ 모니터링**: CloudWatch Alarm으로 DLQ 메시지 발생 시 알림

### 3. 외부 API 재시도 (Claude AI, Polly, Transcribe)
```java
RetryPolicy retryPolicy = RetryPolicy.builder()
    .backoffStrategy(BackoffStrategy.exponentialBackoff())
    .maxAttempts(3)
    .build();

ClaudeClient client = ClaudeClient.builder()
    .overrideConfiguration(c -> c.retryPolicy(retryPolicy))
    .build();
```

---

## 모니터링 및 로깅

### 1. CloudWatch Logs
- **Lambda 로그**: 자동으로 CloudWatch Logs에 저장
- **로그 그룹**: `/aws/lambda/<function-name>`
- **로그 보관 기간**: 30일 (비용 절감)

### 2. CloudWatch Metrics
주요 메트릭:
- Lambda: Invocations, Errors, Duration, Throttles
- DynamoDB: ConsumedReadCapacityUnits, ConsumedWriteCapacityUnits, UserErrors
- SQS: NumberOfMessagesSent, NumberOfMessagesReceived, ApproximateAgeOfOldestMessage
- API Gateway: Count, Latency, 4XXError, 5XXError

### 3. CloudWatch Alarms
```yaml
TTSDLQAlarm:
  Type: AWS::CloudWatch::Alarm
  Properties:
    AlarmName: speaktracker-tts-dlq-alarm
    MetricName: ApproximateNumberOfMessagesVisible
    Namespace: AWS/SQS
    Statistic: Sum
    Period: 300  # 5분
    EvaluationPeriods: 1
    Threshold: 1  # DLQ에 메시지 1개 이상 시 알람
    ComparisonOperator: GreaterThanOrEqualToThreshold
    Dimensions:
      - Name: QueueName
        Value: !GetAtt TTSDLQueue.QueueName
    AlarmActions:
      - !Ref SNSTopic  # SNS로 이메일/Slack 알림
```

### 4. X-Ray Tracing (선택사항)
```yaml
Tracing: Active  # Lambda 함수에 X-Ray 활성화
```

---

## 보안 설정

### 1. API Gateway Authorizer
```yaml
Auth:
  DefaultAuthorizer: CognitoAuthorizer
  Authorizers:
    CognitoAuthorizer:
      UserPoolArn: !GetAtt UserPool.Arn
      # JWT Claims 검증
      IdentitySource: $request.header.Authorization
```

### 2. Lambda 실행 역할 (Least Privilege)
```yaml
Policies:
  - DynamoDBCrudPolicy:
      TableName: !Ref UsersTable
  # 필요한 테이블만 명시적으로 권한 부여
```

### 3. S3 버킷 정책 (CloudFront OAI만 접근 허용)
```yaml
BucketPolicy:
  Type: AWS::S3::BucketPolicy
  Properties:
    Bucket: !Ref FrontendBucket
    PolicyDocument:
      Statement:
        - Effect: Allow
          Principal:
            CanonicalUser: !GetAtt CloudFrontOAI.S3CanonicalUserId
          Action: s3:GetObject
          Resource: !Sub "${FrontendBucket.Arn}/*"
```

### 4. Secrets Manager (API 키 관리)
```yaml
ClaudeAPIKeySecret:
  Type: AWS::SecretsManager::Secret
  Properties:
    Name: speaktracker/claude-api-key
    SecretString: !Sub '{"apiKey":"${ClaudeAPIKey}"}'

# Lambda에서 사용
# SecretsManagerPolicy:
#   SecretArn: !Ref ClaudeAPIKeySecret
```

---

## CI/CD 파이프라인

### GitHub Actions Workflow
```yaml
name: Deploy to AWS
on:
  push:
    branches:
      - main  # main 브랜치에 push 시 배포
      - develop  # develop 브랜치에 push 시 dev 환경 배포

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3

      - name: Setup Java
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'corretto'

      - name: Build with Maven
        run: mvn clean package

      - name: Setup SAM CLI
        uses: aws-actions/setup-sam@v2

      - name: SAM Build
        run: sam build

      - name: SAM Deploy (Production)
        if: github.ref == 'refs/heads/main'
        run: |
          sam deploy \
            --stack-name speaktracker-prod \
            --parameter-overrides Environment=prod \
            --no-confirm-changeset \
            --no-fail-on-empty-changeset
        env:
          AWS_ACCESS_KEY_ID: ${{ secrets.AWS_ACCESS_KEY_ID }}
          AWS_SECRET_ACCESS_KEY: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
          AWS_DEFAULT_REGION: ap-northeast-2

      - name: SAM Deploy (Development)
        if: github.ref == 'refs/heads/develop'
        run: |
          sam deploy \
            --stack-name speaktracker-dev \
            --parameter-overrides Environment=dev \
            --no-confirm-changeset \
            --no-fail-on-empty-changeset
```

---

## 환경별 설정 (dev/prod)

### SAM Template Parameters
```yaml
Parameters:
  Environment:
    Type: String
    Default: dev
    AllowedValues:
      - dev
      - prod

Conditions:
  IsProd: !Equals [!Ref Environment, prod]

Resources:
  UsersTable:
    Type: AWS::DynamoDB::Table
    Properties:
      TableName: !Sub "${Environment}-users"
      BillingMode: !If [IsProd, PAY_PER_REQUEST, PROVISIONED]
      ProvisionedThroughput:
        !If
          - IsProd
          - !Ref AWS::NoValue
          - ReadCapacityUnits: 1
            WriteCapacityUnits: 1
```
