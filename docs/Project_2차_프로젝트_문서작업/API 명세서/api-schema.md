# API 명세서 상세 스키마

## 개요
이 문서는 SpeakTracker API의 상세한 Request/Response 스키마를 정의합니다.

---

## 공통 사항

### 인증 (Authentication)
대부분의 API는 JWT 토큰 기반 인증을 사용합니다.

```http
Authorization: Bearer <access_token>
```

### 공통 에러 응답

| HTTP Status | Error Code | 설명 |
|------------|------------|------|
| 400 | `INVALID_REQUEST` | 잘못된 요청 파라미터 |
| 401 | `UNAUTHORIZED` | 인증 실패 또는 토큰 만료 |
| 403 | `FORBIDDEN` | 권한 없음 |
| 404 | `NOT_FOUND` | 리소스를 찾을 수 없음 |
| 500 | `INTERNAL_SERVER_ERROR` | 서버 내부 오류 |

**에러 응답 형식**:
```json
{
  "error": "ERROR_CODE",
  "message": "사용자에게 표시할 오류 메시지",
  "details": {
    "field": "오류가 발생한 필드 (선택사항)"
  }
}
```

---

## 1. 인증 (Auth)

### POST /api/auth/register
회원가입을 처리합니다.

**Request Body**:
```json
{
  "email": "student@example.com",
  "password": "SecurePass123!",
  "name": "홍길동",
  "role": "student"
}
```

**필드 설명**:
- `email` (string, required): 이메일 주소 (유효성 검증)
- `password` (string, required): 비밀번호 (최소 8자, 영문/숫자/특수문자 포함)
- `name` (string, required): 사용자 이름
- `role` (string, required): 역할 (`student`, `tutor`)

**Response (200)**:
```json
{
  "message": "회원가입이 완료되었습니다. 이메일을 확인해주세요.",
  "email": "student@example.com"
}
```

**Error (400)**:
```json
{
  "error": "EMAIL_ALREADY_EXISTS",
  "message": "이미 등록된 이메일입니다."
}
```

---

### POST /api/auth/login
로그인을 처리하고 JWT 토큰을 발급합니다.

**Request Body**:
```json
{
  "email": "student@example.com",
  "password": "SecurePass123!"
}
```

**Response (200)**:
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "user": {
    "email": "student@example.com",
    "name": "홍길동",
    "role": "student",
    "userSub": "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
  }
}
```

**Error (401)**:
```json
{
  "error": "INVALID_CREDENTIALS",
  "message": "이메일 또는 비밀번호가 올바르지 않습니다."
}
```

---

### POST /api/auth/confirm
이메일 인증을 완료합니다.

**Request Body**:
```json
{
  "email": "student@example.com",
  "confirmationCode": "123456"
}
```

**Response (200)**:
```json
{
  "message": "이메일 인증이 완료되었습니다."
}
```

---

### POST /api/auth/refresh
Refresh Token으로 새로운 Access Token을 발급합니다.

**Request Body**:
```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**Response (200)**:
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "expiresIn": 3600
}
```

---

### GET /api/auth/user
현재 로그인한 사용자 정보를 조회합니다.

**Headers**:
```
Authorization: Bearer <access_token>
```

**Response (200)**:
```json
{
  "email": "student@example.com",
  "name": "홍길동",
  "role": "student",
  "userSub": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "createdAt": "2025-01-15T10:30:00Z"
}
```

---

### PUT /api/auth/profile
사용자 프로필을 수정합니다.

**Request Body**:
```json
{
  "name": "김철수",
  "isAccepting": true
}
```

**필드 설명**:
- `name` (string, optional): 변경할 이름
- `isAccepting` (boolean, optional): 튜터의 경우 학생 수락 여부

**Response (200)**:
```json
{
  "message": "프로필이 업데이트되었습니다.",
  "user": {
    "email": "student@example.com",
    "name": "김철수",
    "role": "student"
  }
}
```

---

### POST /api/auth/profile/image
프로필 이미지를 업로드합니다.

**Request**: `multipart/form-data`
```
image: <File>
```

**Response (200)**:
```json
{
  "imageUrl": "https://s3.amazonaws.com/bucket/profile/user123.jpg",
  "message": "프로필 이미지가 업로드되었습니다."
}
```

---

## 2. 튜터 (Tutor)

### GET /api/tutor/students
튜터가 담당하는 학생 목록을 조회합니다.

**Headers**:
```
Authorization: Bearer <tutor_access_token>
```

**Query Parameters**:
- `limit` (number, optional): 페이지당 항목 수 (기본값: 20, 최대: 100)
- `offset` (number, optional): 시작 위치 (기본값: 0)

**Response (200)**:
```json
{
  "students": [
    {
      "email": "student1@example.com",
      "name": "학생1",
      "assignedAt": "2025-01-10T09:00:00Z",
      "status": "active",
      "room": "practice",
      "updatedAt": 1737456000000
    },
    {
      "email": "student2@example.com",
      "name": "학생2",
      "assignedAt": "2025-01-12T14:30:00Z",
      "status": "active",
      "room": null,
      "updatedAt": 1737542400000
    }
  ],
  "total": 5,
  "limit": 20,
  "offset": 0
}
```

---

### GET /api/tutor/students/{email}
특정 학생의 상세 정보를 조회합니다.

**Path Parameters**:
- `email` (string, required): 학생 이메일

**Response (200)**:
```json
{
  "email": "student1@example.com",
  "name": "학생1",
  "role": "student",
  "assignedAt": "2025-01-10T09:00:00Z",
  "status": "active",
  "currentRoom": "ai-conversation",
  "todayStats": {
    "totalSpeakingTime": 1800,
    "totalRecordingTime": 3600,
    "practiceCount": 5,
    "avgAccuracy": 85.5
  }
}
```

---

### GET /api/tutor/students/{email}/history
학생의 학습 이력을 조회합니다.

**Path Parameters**:
- `email` (string, required): 학생 이메일

**Query Parameters**:
- `startDate` (string, optional): 조회 시작일 (YYYY-MM-DD)
- `endDate` (string, optional): 조회 종료일 (YYYY-MM-DD)
- `sessionType` (string, optional): 세션 유형 (`practice`, `conversation`)

**Response (200)**:
```json
{
  "sessions": [
    {
      "sessionId": "sess-123456",
      "timestamp": "2025-01-30T14:00:00Z",
      "sessionType": "practice",
      "duration": 1200,
      "speakingDuration": 900,
      "avgAccuracy": 88.5
    },
    {
      "sessionId": "sess-123457",
      "timestamp": "2025-01-30T16:00:00Z",
      "sessionType": "conversation",
      "duration": 1800,
      "speakingDuration": 1500,
      "turnCount": 10
    }
  ],
  "total": 25
}
```

---

### POST /api/tutor/feedback
학생에게 피드백을 전송합니다.

**Request Body**:
```json
{
  "studentEmail": "student1@example.com",
  "message": "오늘 발음 연습 잘했어요. 특히 'th' 발음이 좋아졌네요!",
  "messageType": "text",
  "sessionId": "sess-123456"
}
```

**필드 설명**:
- `studentEmail` (string, required): 학생 이메일
- `message` (string, required): 피드백 내용
- `messageType` (string, required): 메시지 유형 (`text`, `audio`)
- `sessionId` (string, optional): 관련 세션 ID

**Response (200)**:
```json
{
  "feedbackId": "fb-789012",
  "timestamp": "2025-01-30T17:30:00Z",
  "message": "피드백이 전송되었습니다.",
  "websocketSent": true
}
```

---

### GET /api/tutor/feedback
튜터가 보낸 피드백 히스토리를 조회합니다.

**Query Parameters**:
- `studentEmail` (string, optional): 특정 학생의 피드백만 조회
- `limit` (number, optional): 페이지당 항목 수
- `lastKey` (string, optional): 페이지네이션 키

**Response (200)**:
```json
{
  "feedbacks": [
    {
      "feedbackId": "fb-789012",
      "studentEmail": "student1@example.com",
      "tutorEmail": "tutor@example.com",
      "message": "오늘 발음 연습 잘했어요.",
      "messageType": "text",
      "timestamp": "2025-01-30T17:30:00Z",
      "sessionId": "sess-123456",
      "websocketSent": true
    }
  ],
  "lastKey": "encoded-pagination-key"
}
```

---

### GET /api/tutor/requests
튜터가 받은 매칭 요청 목록을 조회합니다.

**Query Parameters**:
- `status` (string, optional): 요청 상태 필터 (`PENDING`, `APPROVED`, `REJECTED`)

**Response (200)**:
```json
{
  "requests": [
    {
      "requestId": "req-123abc",
      "studentEmail": "newstudent@example.com",
      "studentName": "신규학생",
      "message": "영어 회화 실력을 향상시키고 싶습니다.",
      "status": "PENDING",
      "createdAt": 1737456000000
    }
  ],
  "total": 3
}
```

---

## 3. 학생 (Student)

### GET /api/student/tutor
내 튜터 정보를 조회합니다.

**Response (200)**:
```json
{
  "tutorEmail": "tutor@example.com",
  "tutorName": "김튜터",
  "assignedAt": "2025-01-10T09:00:00Z",
  "status": "active"
}
```

**Response (404)**: 배정된 튜터가 없는 경우
```json
{
  "error": "TUTOR_NOT_FOUND",
  "message": "배정된 튜터가 없습니다."
}
```

---

### GET /api/student-status
학생의 현재 상태를 조회합니다.

**Response (200)**:
```json
{
  "email": "student@example.com",
  "currentRoom": "ai-conversation",
  "isLearning": true,
  "lastUpdated": "2025-01-30T18:00:00Z"
}
```

---

### POST /api/student-status
학생의 상태를 업데이트합니다 (WebSocket 대신 polling 시 사용).

**Request Body**:
```json
{
  "room": "practice",
  "isLearning": true
}
```

**Response (200)**:
```json
{
  "message": "상태가 업데이트되었습니다.",
  "updatedAt": 1737561600000
}
```

---

## 4. 문장 연습 (Sentences)

### GET /api/sentences
문장 목록을 조회합니다.

**Query Parameters**:
- `difficulty` (string, optional): 난이도 (`상`, `중`, `하`)
- `topic` (string, optional): 주제 (예: `일상 대화`, `비즈니스`)
- `limit` (number, optional): 조회 개수 (기본값: 10)

**Response (200)**:
```json
{
  "sentences": [
    {
      "id": "sent-001",
      "english": "How are you doing today?",
      "korean": "오늘 어떻게 지내세요?",
      "difficulty": "하",
      "topic": "일상 대화"
    },
    {
      "id": "sent-002",
      "english": "Could you please send me the report by tomorrow?",
      "korean": "내일까지 보고서를 보내주시겠어요?",
      "difficulty": "중",
      "topic": "비즈니스"
    }
  ]
}
```

---

### GET /api/sentences/{id}/audio
문장의 TTS 오디오를 조회합니다.

**Path Parameters**:
- `id` (string, required): 문장 ID

**Response (200)**:
```json
{
  "audioUrl": "https://s3.amazonaws.com/bucket/audio/sent-001.mp3",
  "durationMs": 2500,
  "voiceId": "Matthew"
}
```

---

### POST /api/sentences/generate
AI가 학생 레벨에 맞는 문장을 생성합니다.

**Request Body**:
```json
{
  "difficulty": "중",
  "topic": "일상 대화",
  "count": 5
}
```

**Response (200)**:
```json
{
  "sentences": [
    {
      "english": "I really enjoyed our conversation yesterday.",
      "korean": "어제 우리 대화 정말 즐거웠어요."
    }
  ],
  "sessionId": "gen-session-123"
}
```

---

### POST /api/sentences/recommend
학생에게 맞춤 문장을 추천합니다.

**Request Body**:
```json
{
  "studentEmail": "student@example.com"
}
```

**Response (200)**:
```json
{
  "recommendations": [
    {
      "id": "sent-045",
      "english": "Practice makes perfect.",
      "korean": "연습이 완벽을 만든다.",
      "reason": "이전 발음 테스트에서 'p' 발음 개선 필요"
    }
  ]
}
```

---

### GET /api/sentences/audio/{sessionId}
세션별 오디오 메타데이터를 조회합니다.

**Path Parameters**:
- `sessionId` (string, required): 세션 ID

**Response (200)**:
```json
{
  "sessionId": "sess-audio-123",
  "audios": [
    {
      "sentenceIndex": 0,
      "english": "Hello, how are you?",
      "korean": "안녕하세요, 어떻게 지내세요?",
      "audioUrl": "https://s3.amazonaws.com/bucket/audio/sess-123-0.mp3",
      "durationMs": 2000,
      "status": "COMPLETED"
    }
  ]
}
```

---

### POST /api/sentences/feedback
문장 연습 결과에 대한 피드백을 제출합니다.

**Request Body**:
```json
{
  "sessionId": "sess-123",
  "sentenceId": "sent-001",
  "userAudio": "base64-encoded-audio-data"
}
```

**Response (200)**:
```json
{
  "feedback": "Good pronunciation!",
  "accuracy": 92.5,
  "improvements": ["Try to emphasize the 'th' sound more"]
}
```

---

## 5. AI 대화 (AI Chat)

### POST /api/ai/chat/start
AI 대화 세션을 시작합니다.

**Request Body**:
```json
{
  "difficulty": "중",
  "topic": "일상 대화",
  "role": "친구"
}
```

**Response (200)**:
```json
{
  "conversationId": "conv-abc123",
  "situation": "카페에서 친구와 만나는 상황",
  "firstMessage": "Hey! Long time no see. How have you been?",
  "audioUrl": "https://s3.amazonaws.com/bucket/tts/conv-abc123-0.mp3"
}
```

---

### POST /api/ai/chat/message
AI에게 메시지를 전송합니다.

**Request Body**:
```json
{
  "conversationId": "conv-abc123",
  "userMessage": "I've been great! Just busy with work.",
  "audioData": "base64-encoded-audio"
}
```

**Response (202)**: 비동기 처리
```json
{
  "requestId": "req-xyz789",
  "message": "메시지를 처리 중입니다.",
  "estimatedTime": 3000
}
```

---

### GET /api/ai/chat/status/{requestId}
AI 응답 처리 상태를 조회합니다.

**Path Parameters**:
- `requestId` (string, required): 요청 ID

**Response (200)**:
```json
{
  "status": "COMPLETED",
  "aiMessage": "That's great to hear! What have you been working on?",
  "audioUrl": "https://s3.amazonaws.com/bucket/tts/conv-abc123-1.mp3",
  "turnCount": 2
}
```

**Response (202)**: 아직 처리 중
```json
{
  "status": "PROCESSING",
  "message": "AI 응답을 생성 중입니다."
}
```

---

## 6. TTS (Text-to-Speech)

### POST /api/tts
영어 텍스트를 음성으로 변환합니다.

**Request Body**:
```json
{
  "text": "Hello, how are you?",
  "voiceId": "Matthew",
  "engine": "neural"
}
```

**필드 설명**:
- `text` (string, required): 변환할 텍스트
- `voiceId` (string, optional): 음성 ID (기본값: `Matthew`)
- `engine` (string, optional): 엔진 타입 (`neural`, `standard`)

**Response (200)**:
```json
{
  "jobId": "tts-job-123",
  "status": "PROCESSING",
  "message": "TTS 작업이 시작되었습니다."
}
```

---

### GET /api/tts/status/{jobId}
TTS 작업 상태를 조회합니다.

**Response (200)**:
```json
{
  "jobId": "tts-job-123",
  "status": "COMPLETED",
  "audioUrl": "https://s3.amazonaws.com/bucket/tts/tts-job-123.mp3",
  "durationMs": 2500
}
```

---

### POST /api/tts/korean
한국어 텍스트를 음성으로 변환합니다.

**Request Body**:
```json
{
  "text": "안녕하세요",
  "voiceId": "Seoyeon"
}
```

**Response (200)**:
```json
{
  "jobId": "tts-kr-job-456",
  "status": "PROCESSING"
}
```

---

## 7. STT (Speech-to-Text) 및 발음 평가

### POST /api/stt/evaluate
음성을 텍스트로 변환하고 발음을 평가합니다.

**Request**: `multipart/form-data`
```
audioFile: <File>
originalText: "Hello, how are you?"
```

**Response (200)**:
```json
{
  "transcribedText": "Hello how are you",
  "originalText": "Hello, how are you?",
  "overallScore": 88.5,
  "wordAccuracy": 92.3,
  "completenessScore": 85.0,
  "grade": "B",
  "feedback": "Good pronunciation! Try to emphasize punctuation pauses.",
  "missedWords": [
    {
      "word": "are",
      "reason": "발음이 명확하지 않음"
    }
  ],
  "extraWords": [],
  "audioDurationMs": 2500
}
```

---

## 8. 세션 (Sessions)

### POST /api/sessions/start
학습 세션을 시작합니다.

**Request Body**:
```json
{
  "sessionType": "practice",
  "metadata": {
    "difficulty": "중",
    "topic": "일상 대화"
  }
}
```

**Response (200)**:
```json
{
  "sessionId": "sess-789xyz",
  "startTime": "2025-01-30T18:00:00Z",
  "message": "세션이 시작되었습니다."
}
```

---

### POST /api/sessions/end
학습 세션을 종료합니다.

**Request Body**:
```json
{
  "sessionId": "sess-789xyz"
}
```

**Response (200)**:
```json
{
  "sessionId": "sess-789xyz",
  "endTime": "2025-01-30T18:30:00Z",
  "duration": 1800,
  "speakingDuration": 1200,
  "message": "세션이 종료되었습니다."
}
```

---

### GET /api/sessions/history
세션 이력을 조회합니다.

**Query Parameters**:
- `startDate` (string, optional): 조회 시작일
- `endDate` (string, optional): 조회 종료일
- `sessionType` (string, optional): 세션 유형
- `limit` (number, optional): 조회 개수

**Response (200)**:
```json
{
  "sessions": [
    {
      "sessionId": "sess-789xyz",
      "timestamp": "2025-01-30T18:00:00Z",
      "sessionType": "practice",
      "duration": 1800,
      "speakingDuration": 1200
    }
  ],
  "total": 15
}
```

---

## 9. 통계 (Statistics)

### GET /api/statistics/today
오늘의 학습 통계를 조회합니다.

**Response (200)**:
```json
{
  "date": "2025-01-30",
  "totalSpeakingTime": 3600,
  "totalRecordingTime": 5400,
  "practiceCount": 8,
  "conversationCount": 3,
  "avgAccuracy": 87.5,
  "sessionsCount": 11
}
```

---

### GET /api/statistics/weekly
주간 학습 통계를 조회합니다.

**Query Parameters**:
- `startDate` (string, optional): 시작일 (YYYY-MM-DD)
- `endDate` (string, optional): 종료일 (YYYY-MM-DD)

**Response (200)**:
```json
{
  "period": {
    "startDate": "2025-01-24",
    "endDate": "2025-01-30"
  },
  "dailyStats": [
    {
      "date": "2025-01-30",
      "totalSpeakingTime": 3600,
      "practiceCount": 8,
      "avgAccuracy": 87.5
    }
  ],
  "summary": {
    "totalSpeakingTime": 18000,
    "totalPracticeCount": 42,
    "avgAccuracy": 85.3
  }
}
```

---

### POST /api/stats/daily
일일 통계를 기록합니다 (내부 사용).

**Request Body**:
```json
{
  "studentEmail": "student@example.com",
  "date": "2025-01-30",
  "totalSpeakingTime": 3600,
  "practiceCount": 8
}
```

**Response (200)**:
```json
{
  "message": "통계가 기록되었습니다."
}
```

---

## 10. 튜터 등록 (Tutor Registration)

### GET /api/tutors
튜터 목록을 조회합니다.

**Query Parameters**:
- `search` (string, optional): 검색어 (이름 또는 이메일)
- `limit` (number, optional): 조회 개수

**Response (200)**:
```json
{
  "tutors": [
    {
      "email": "tutor1@example.com",
      "name": "김튜터",
      "isAccepting": true,
      "maxStudents": 10,
      "currentStudents": 7
    }
  ]
}
```

---

### POST /api/tutors/{email}/request
튜터에게 매칭 요청을 보냅니다.

**Path Parameters**:
- `email` (string, required): 튜터 이메일

**Request Body**:
```json
{
  "message": "영어 회화 실력을 향상시키고 싶습니다."
}
```

**Response (200)**:
```json
{
  "requestId": "req-abc123",
  "message": "매칭 요청이 전송되었습니다.",
  "status": "PENDING"
}
```

---

### GET /api/my/tutor-requests
내가 보낸 튜터 매칭 요청 목록을 조회합니다.

**Response (200)**:
```json
{
  "requests": [
    {
      "requestId": "req-abc123",
      "tutorEmail": "tutor1@example.com",
      "tutorName": "김튜터",
      "message": "영어 회화 실력을 향상시키고 싶습니다.",
      "status": "PENDING",
      "createdAt": 1737456000000
    }
  ]
}
```

---

### DELETE /api/my/tutor-requests/{id}
튜터 매칭 요청을 취소합니다.

**Path Parameters**:
- `id` (string, required): 요청 ID

**Response (200)**:
```json
{
  "message": "요청이 취소되었습니다."
}
```

---

### POST /api/tutors/requests/{id}/approve
튜터 매칭 요청을 승인합니다 (튜터 전용).

**Path Parameters**:
- `id` (string, required): 요청 ID

**Response (200)**:
```json
{
  "message": "요청이 승인되었습니다.",
  "studentEmail": "student@example.com"
}
```

---

### POST /api/tutors/requests/{id}/reject
튜터 매칭 요청을 거절합니다 (튜터 전용).

**Path Parameters**:
- `id` (string, required): 요청 ID

**Request Body**:
```json
{
  "reason": "현재 학생 정원이 가득 찼습니다."
}
```

**Response (200)**:
```json
{
  "message": "요청이 거절되었습니다."
}
```

---

## 11. 알림 (Notifications)

### GET /api/notifications
알림 목록을 조회합니다.

**Query Parameters**:
- `isRead` (boolean, optional): 읽음 여부 필터
- `limit` (number, optional): 조회 개수

**Response (200)**:
```json
{
  "notifications": [
    {
      "notificationId": "notif-123",
      "type": "tutor_match",
      "title": "튜터 매칭 완료",
      "message": "김튜터님과 매칭되었습니다.",
      "isRead": false,
      "createdAt": "2025-01-30T10:00:00Z",
      "sentVia": ["websocket", "email"]
    }
  ],
  "total": 5,
  "unreadCount": 2
}
```

---

### PUT /api/notifications/{id}
알림을 읽음 처리합니다.

**Path Parameters**:
- `id` (string, required): 알림 ID

**Response (200)**:
```json
{
  "message": "알림이 읽음 처리되었습니다."
}
```

---

## 12. 대시보드 (Dashboard)

### GET /api/dashboard
대시보드 데이터를 조회합니다 (학생/튜터 역할에 따라 다른 데이터 반환).

**Response (200)** - 학생용:
```json
{
  "role": "student",
  "todayGoal": {
    "targetMinutes": 30,
    "currentMinutes": 22,
    "progress": 73
  },
  "weeklyStats": {
    "totalSpeakingTime": 18000,
    "practiceCount": 42,
    "avgAccuracy": 85.3
  },
  "recentSessions": [
    {
      "sessionId": "sess-123",
      "timestamp": "2025-01-30T18:00:00Z",
      "sessionType": "practice",
      "duration": 1800
    }
  ],
  "tutor": {
    "email": "tutor@example.com",
    "name": "김튜터"
  },
  "aiRecommendation": "AI Conversation을 시도해보세요. 실전 대화 능력이 향상됩니다."
}
```

**Response (200)** - 튜터용:
```json
{
  "role": "tutor",
  "studentsCount": 7,
  "maxStudents": 10,
  "recentActivities": [
    {
      "studentEmail": "student1@example.com",
      "studentName": "학생1",
      "activityType": "session_completed",
      "timestamp": "2025-01-30T18:30:00Z"
    }
  ],
  "studentsLevel": [
    {
      "email": "student1@example.com",
      "name": "학생1",
      "currentLevel": "중급",
      "avgAccuracy": 85.5
    }
  ]
}
```

---

## 부록: 페이지네이션

목록 조회 API는 다음 방식 중 하나를 사용합니다:

### 1. Offset 기반 (일반적인 경우)
```
GET /api/tutor/students?limit=20&offset=40
```

### 2. Cursor 기반 (DynamoDB LastEvaluatedKey)
```
GET /api/sessions/history?limit=20&lastKey=eyJ0aW1lc3RhbXAiOi...
```

**Response**:
```json
{
  "items": [...],
  "lastKey": "next-page-cursor"
}
```

---