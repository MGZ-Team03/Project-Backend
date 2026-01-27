# 실시간 외국어 발음 학습 플랫폼 (SpeakTracker)

## 프로젝트 개요

AI 기반 얼굴인식과 음성 감지를 활용한 **1:N 실시간 외국어 발음 학습 플랫폼**. 학생이 외국어를 학습할 때 실제로 발음하는 시간을 자동 측정하고, 튜터가 다수의 학생을 실시간으로 모니터링하며 피드백을 제공하는 서버리스 아키텍처 기반 시스템.

### 핵심 가치

- **발음 시간 자동 측정**: 입 움직임(MAR) + 음성 감지로 실제 발음 시간 정확히 측정
- **AI 회화 연습**: Claude/ChatGPT API로 자연스러운 영어 대화, Polly TTS로 원어민 음성 제공
- **1:N 실시간 관리**: 튜터 1명이 다수 학생을 효율적으로 모니터링 및 피드백

---

## 사용자 유형

| 역할 | 설명 |
|------|------|
| **학생** | 문장 연습, AI 대화로 영어 학습 |
| **튜터** | 학생들 실시간 모니터링, 피드백 제공 |

---

## 학생 기능

### 1. 문장 연습

주어진 영어 문장을 따라 읽으며 발음 시간을 측정한다.

```
┌─────────────────────────────────────────────────┐
│  📖 문장 연습                                    │
├─────────────────────────────────────────────────┤
│  오늘 학습: 12분 32초                            │
│  발음 비율: 68%                                  │
├─────────────────────────────────────────────────┤
│  따라 읽어보세요:                                │
│                                                 │
│  "How are you doing today?"                     │
│                                                 │
│  [🔊 원어민 발음 듣기]                           │
├─────────────────────────────────────────────────┤
│  내 상태: 🎤 발음 중... (2.3초)                  │
│                                                 │
│  [⏭️ 다음 문장] [🔄 다시 듣기]                   │
└─────────────────────────────────────────────────┘
```

**플로우:**
1. 문장 표시 + 원어민 음성 재생 (Polly TTS)
2. 학생이 따라 읽음
3. AI가 입 움직임 + 음성 감지 → 발음 시간 측정
4. 다음 문장으로 이동
5. 세션 종료 시 총 발음 시간/비율 저장

---

### 2. AI 대화

Claude/ChatGPT API를 활용해 AI와 자유롭게 영어 대화한다.

```
┌─────────────────────────────────────────────────┐
│  🤖 AI 대화                                      │
├─────────────────────────────────────────────────┤
│  주제: 카페에서 주문하기 ☕                       │
├─────────────────────────────────────────────────┤
│  AI: "Hello! Welcome to the coffee shop.        │
│       What would you like to order today?"      │
│       [🔊 재생]                                  │
├─────────────────────────────────────────────────┤
│  나: "I'd like a latte, please."                │
│      (발음 시간: 2.3초)                          │
├─────────────────────────────────────────────────┤
│  AI: "Great choice! Would you like it           │
│       hot or iced?"                             │
│       [🔊 재생]                                  │
├─────────────────────────────────────────────────┤
│  [🎤 말하기] [주제 변경] [대화 종료]              │
└─────────────────────────────────────────────────┘
```

**플로우:**
1. 주제 선택 (카페 주문, 길 묻기, 면접 등)
2. AI가 먼저 대화 시작 (Claude API → Polly TTS)
3. 학생이 음성으로 응답 (Web Speech API로 STT)
4. 발화 중 발음 시간 측정
5. AI 응답 생성 → TTS → 음성 재생
6. 대화 종료 시 통계 저장

**회화 주제:**
| 주제 | 난이도 |
|------|--------|
| 자기소개 | 초급 |
| 카페 주문 | 초급 |
| 길 묻기 | 중급 |
| 영화 추천 | 중급 |
| 면접 연습 | 고급 |

---

### 3. 학습 통계

```
┌─────────────────────────────────────────────────┐
│  📊 오늘 학습 현황                               │
├─────────────────────────────────────────────────┤
│  총 학습 시간: 25분                              │
│  발음 시간: 17분 (68%)                           │
│                                                 │
│  문장 연습: 15분 (발음 72%)                      │
│  AI 대화: 10분 (발음 62%)                        │
├─────────────────────────────────────────────────┤
│  📅 이번 주                                      │
│  월: 30분 | 화: 25분 | 수: - | 목: 25분          │
└─────────────────────────────────────────────────┘
```

---

## 튜터 기능

### 1. 실시간 모니터링 대시보드

```
┌─────────────────────────────────────────────────┐
│  📋 현재 학습 중인 학생 (8명)                    │
├─────────────────────────────────────────────────┤
│  🟢 박영어   문장연습  발음 중 (85%)  12분       │
│  🟢 김스피킹  AI대화   발음 중 (78%)  23분       │
│  🟡 최토익   문장연습  듣기만 (30%)   8분  ← 주의│
│  🔴 정회화   -        5분간 미활동         ← 개입│
│  🟢 이잉글   AI대화   발음 중 (72%)  15분       │
├─────────────────────────────────────────────────┤
│  🟢 발음 중  🟡 발음 비율 낮음  🔴 미활동        │
└─────────────────────────────────────────────────┘
```

### 2. 학생 상세 + 피드백

```
┌─────────────────────────────────────────────────┐
│  👤 최토익                                       │
├─────────────────────────────────────────────────┤
│  현재: 문장 연습 중 (발음 비율 30%)              │
│  오늘 학습: 8분                                  │
│  현재 문장: "The weather is nice today"         │
├─────────────────────────────────────────────────┤
│  💬 피드백 보내기                                │
│  ┌─────────────────────────────────────────┐   │
│  │ 듣기만 하지 말고 따라 읽어보세요!        │   │
│  └─────────────────────────────────────────┘   │
│  [텍스트 전송] [TTS 전송]                        │
└─────────────────────────────────────────────────┘
```

**피드백 전송:**
- 텍스트 → 학생 화면에 팝업
- TTS → Polly로 음성 생성 → 학생에게 음성 재생

### 3. 학생 학습 이력

```
┌─────────────────────────────────────────────────┐
│  📈 박영어님 학습 이력                           │
├─────────────────────────────────────────────────┤
│  이번 주 총 학습: 2시간 15분                     │
│  평균 발음 비율: 72%                             │
├─────────────────────────────────────────────────┤
│  일별 현황:                                      │
│  1/6(월): 35분 (75%)                            │
│  1/7(화): 28분 (68%)                            │
│  1/8(수): 32분 (73%)                            │
└─────────────────────────────────────────────────┘
```

---

## 시스템 아키텍처

```
┌─────────────────────────────────────────────────────────────────┐
│                         클라이언트                               │
├────────────────────────┬────────────────────────────────────────┤
│      학생 (React)      │           튜터 (React)                 │
│  - TensorFlow.js       │   - 실시간 대시보드                    │
│  - Web Audio API       │   - 피드백 전송                        │
│  - Web Speech API      │   - 학습 이력 조회                     │
│  - WebSocket           │   - WebSocket                         │
└────────────┬───────────┴──────────────────┬─────────────────────┘
             │                              │
             ▼                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    API Gateway (REST + WebSocket)                │
└─────────────────────────────────────────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────────────────────────────────┐
│                         Lambda Functions                         │
├─────────────┬─────────────┬─────────────┬───────────────────────┤
│  WebSocket  │  REST API   │  AI 대화    │  TTS 생성             │
│  연결관리   │  CRUD       │  Claude API │  Polly                │
└─────────────┴─────────────┴─────────────┴───────────────────────┘
             │
             ▼
┌─────────────────────────────────────────────────────────────────┐
│                         AWS Services                             │
├─────────────┬─────────────┬─────────────┬───────────────────────┤
│  DynamoDB   │  S3         │  Cognito    │  Polly                │
│  데이터     │  음성파일   │  인증       │  TTS                  │
└─────────────┴─────────────┴─────────────┴───────────────────────┘
             │
             ▼
┌─────────────────────────────────────────────────────────────────┐
│                      External APIs                               │
│              Anthropic Claude API / OpenAI API                   │
└─────────────────────────────────────────────────────────────────┘
```

---

## 데이터베이스 설계 (DynamoDB)

### 1. users
```
PK: email
- name: String
- role: 'student' | 'tutor'
- created_at: DateTime
```

### 2. tutor_students
```
PK: tutor_email
SK: student_email
- assigned_at: DateTime
- status: 'active' | 'inactive'

GSI: student_email-index
```

### 3. learning_sessions
```
PK: student_email
SK: timestamp
- session_type: 'sentence' | 'ai_chat'
- duration: Number (초)
- speaking_duration: Number (초)
- speaking_ratio: Number (%)

GSI: tutor_email-timestamp-index
TTL: 90일
```

### 4. daily_statistics
```
PK: student_email
SK: date (YYYY-MM-DD)
- total_duration: Number
- speaking_duration: Number
- speaking_ratio: Number
- sentence_sessions: Number
- ai_chat_sessions: Number
```

### 3. learning_sessions
```
PK: student_email
SK: timestamp
- session_type: 'sentence' | 'ai_chat'
- recording_duration: Number (ms, 녹음 버튼 누른 총 시간)
- speaking_duration: Number (ms, 실제 발화 시간)
- net_speaking_density: Number (%, speaking / recording * 100)

# 문장 연습 전용 필드
- practice_records: List [{
    sentence_id: String,
    pace_ratio: Number,
    user_time: Number (ms),
    ref_time: Number (ms),
    timestamp: Number
  }]
- avg_pace_ratio: Number (세션 평균)

# AI 대화 전용 필드
- response_latencies: List [Number] (ms)
- avg_response_latency: Number (세션 평균)
- chat_turns_count: Number

GSI: tutor_email-timestamp-index
TTL: 90일
```

### 4. daily_statistics
```
PK: student_email
SK: date (YYYY-MM-DD)

# 기본 통계
- total_recording_time: Number (ms, 일별 총 녹음 시간)
- total_speaking_time: Number (ms, 일별 총 발화 시간)
- sessions_count: Number (일별 세션 수)
- practice_count: Number (문장 연습 횟수)
- chat_turns_count: Number (AI 대화 턴 수)

# 3대 지표 (일별 평균)
- avg_pace_ratio: Number (원어민 대비 속도, 1.0 = 완벽)
- avg_response_latency: Number (ms, 응답 속도)
- avg_net_speaking_density: Number (%, 녹음 시간 대비 발화 밀도)

# 상세 기록 (분석용)
- pace_ratios: List [Number] (모든 연습 기록)
- response_latencies: List [Number] (모든 대화 응답 기록)
```

### 5. ai_conversations
```
PK: student_email
SK: timestamp
- conversation_id: String
- topic: String
- messages: List (role, content)
- total_speaking_time: Number

TTL: 30일
```

### 6. feedback_messages
```
PK: tutor_email#student_email
SK: timestamp
- message: String
- type: 'text' | 'tts'
- audio_url: String (optional)

TTL: 30일
```

### 7. websocket_connections
```
PK: connection_id
- user_email: String
- user_type: 'student' | 'tutor'
- tutor_email: String

GSI: tutor_email-index
GSI: user_email-index
TTL: 2시간
```

### 8. sentences (문장 연습용)
```
PK: category
SK: sentence_id
- text: String
- audio_url: String (Polly 미리 생성)
- difficulty: 'easy' | 'medium' | 'hard'
```

---

## API 엔드포인트

### 인증
```
POST /api/auth/register
POST /api/auth/login
```

### 튜터 등록
```
GET  /api/tutors                      - 튜터 목록
POST /api/tutors/{email}/request      - 등록 요청
POST /api/tutors/requests/{id}/approve
POST /api/tutors/requests/{id}/reject
```

### 학습 세션
```
POST /api/sessions/start              - 세션 시작
POST /api/sessions/end                - 세션 종료 (통계 저장)
GET  /api/sessions/history            - 학습 이력
```

### 문장 연습
```
GET  /api/sentences?category={cat}    - 문장 목록
GET  /api/sentences/{id}/audio        - 원어민 음성 URL
```

### AI 대화
```
GET  /api/ai/topics                   - 주제 목록
POST /api/ai/chat/start               - 대화 시작
POST /api/ai/chat/message             - 메시지 전송 → AI 응답 + TTS
POST /api/ai/chat/end                 - 대화 종료
```

### 통계
```
GET  /api/statistics/today
GET  /api/statistics/weekly
```

### 튜터
```
GET  /api/tutor/students              - 담당 학생 목록
GET  /api/tutor/students/{email}      - 학생 상세
GET  /api/tutor/students/{email}/history
POST /api/tutor/feedback              - 피드백 전송
```

---

## 핵심 기술 구현

### 1. 발음 감지 (MAR + 음성)

```javascript
// 입 벌림 감지
function calculateMAR(landmarks) {
  const vertical = distance(landmarks[13], landmarks[14]);
  const horizontal = distance(landmarks[78], landmarks[308]);
  return vertical / horizontal;
}

// 음성 감지
function detectVoice(analyser) {
  const data = new Uint8Array(analyser.frequencyBinCount);
  analyser.getByteFrequencyData(data);
  const volume = data.reduce((a, b) => a + b) / data.length;
  return volume > 30;
}

// 종합 판단
function isSpeaking(landmarks, analyser) {
  return calculateMAR(landmarks) > 0.3 && detectVoice(analyser);
}
```

### 2. AI 대화 Lambda

```javascript
// Claude API 호출
const response = await anthropic.messages.create({
  model: "claude-sonnet-4-20250514",
  max_tokens: 150,
  system: `You are an English conversation partner.
           Topic: ${topic}.
           Keep responses short (1-2 sentences).`,
  messages: conversationHistory
});

// Polly TTS 생성
const audio = await polly.synthesizeSpeech({
  Text: response.content[0].text,
  OutputFormat: 'mp3',
  VoiceId: 'Joanna',
  Engine: 'neural'
}).promise();

// S3 업로드 후 Pre-signed URL 반환
```

### 3. WebSocket Fanout

```javascript
// 학생 상태 업데이트 시 해당 튜터에게 브로드캐스트
async function notifyTutor(studentEmail, status) {
  // 학생의 튜터 조회
  const tutor = await getTutorByStudent(studentEmail);
  
  // 튜터의 WebSocket 연결 조회
  const connections = await getConnectionsByTutor(tutor.email);
  
  // 모든 연결에 전송
  await Promise.all(connections.map(conn => 
    apiGateway.postToConnection({
      ConnectionId: conn.connectionId,
      Data: JSON.stringify({ type: 'STUDENT_UPDATE', ...status })
    }).promise()
  ));
}
```


---

## 발화시간 체크 고도화

1. 원어민 대비 속도 비율 (Pace Ratio) - 유창성 지표

문장 연습(Shadowing) 모드에서 가장 의미 있는 지표입니다.

개념: (나의 발음 지속 시간) ÷ (원어민/TTS 레퍼런스 오디오 길이)

왜 필요한가?:

비율이 1.0에 가까울수록 원어민과 비슷한 속도와 리듬으로 말한 것입니다.

1.5 이상: 너무 느림 (단어 사이 멈춤이 많거나 늘어짐).

0.8 이하: 너무 빠름 (발음을 뭉개거나 급하게 읽음).

통계 활용 예시:

"오늘의 리듬 점수": 1.0에서 멀어질수록 감점하는 방식.

튜터 피드백: "박영어 학생은 평균 1.4배 느리게 말하고 있어요. 조금 더 자신 있게 이어서 말해보세요."

2. 발화 반응 속도 (Response Latency) - 숙련도 지표

AI 대화 모드에서 중요한 지표입니다. AI가 질문을 끝낸 후 내가 대답을 시작하기까지 걸리는 시간입니다.

측정 방법: (내 발음 감지 시작 시각) - (AI 음성 재생 종료 시각)

왜 필요한가?:

영어를 머릿속으로 번역하고 문장을 구성하느라 걸리는 **'망설임 시간'**을 의미합니다.

이 시간이 짧아질수록 영어를 영어로 받아들이는 '영어 뇌'가 만들어지고 있다는 증거가 됩니다.

통계 활용 예시:

그래프: 주차별 '평균 반응 속도' 변화 (3초 → 1.5초로 단축 시 성공).

레벨 판단: 반응 속도가 1초 이내면 난이도를 상향 조정.

3. 순수 발화 밀도 (Net Speaking Density) - 집중도 지표

기존 '전체 시간 대비 발음 시간'을 보완한 지표입니다. 전체 시간에서 '불가피한 시간(AI가 말하는 시간, 로딩 시간)'을 뺀 시간 대비 비율입니다.

개념: (나의 실제 발음 시간) ÷ (전체 세션 시간 - AI/TTS 오디오 재생 시간 - 시스템 로딩 시간)

왜 필요한가?:

AI가 길게 설명하는 동안 학생 비율이 떨어지는 왜곡을 방지합니다.

순수하게 **'내가 말할 기회가 주어졌을 때 얼마나 꽉 채워서 말했는가'**를 보여줍니다.

통계 활용 예시:

대화 적극성: AI 대화에서 단답형(Yes/No)으로만 대답하면 이 비율이 낮게 나옵니다. 길게 설명할수록 비율이 높아집니다.

튜터 대시보드: "최토익 학생은 듣기는 잘하지만, 답변이 너무 짧아요(밀도 20%). 더 긴 문장 유도가 필요해요."

---

## 발화시간 체크 고도화

1. 원어민 대비 속도 비율 (Pace Ratio) - 유창성 지표

문장 연습(Shadowing) 모드에서 가장 의미 있는 지표입니다.

개념: (나의 발음 지속 시간) ÷ (원어민/TTS 레퍼런스 오디오 길이)

왜 필요한가?:

비율이 1.0에 가까울수록 원어민과 비슷한 속도와 리듬으로 말한 것입니다.

1.5 이상: 너무 느림 (단어 사이 멈춤이 많거나 늘어짐).

0.8 이하: 너무 빠름 (발음을 뭉개거나 급하게 읽음).

통계 활용 예시:

"오늘의 리듬 점수": 1.0에서 멀어질수록 감점하는 방식.

튜터 피드백: "박영어 학생은 평균 1.4배 느리게 말하고 있어요. 조금 더 자신 있게 이어서 말해보세요."

2. 발화 반응 속도 (Response Latency) - 숙련도 지표

AI 대화 모드에서 중요한 지표입니다. AI가 질문을 끝낸 후 내가 대답을 시작하기까지 걸리는 시간입니다.

측정 방법: (내 발음 감지 시작 시각) - (AI 음성 재생 종료 시각)

왜 필요한가?:

영어를 머릿속으로 번역하고 문장을 구성하느라 걸리는 **'망설임 시간'**을 의미합니다.

이 시간이 짧아질수록 영어를 영어로 받아들이는 '영어 뇌'가 만들어지고 있다는 증거가 됩니다.

통계 활용 예시:

그래프: 주차별 '평균 반응 속도' 변화 (3초 → 1.5초로 단축 시 성공).

레벨 판단: 반응 속도가 1초 이내면 난이도를 상향 조정.

3. 순수 발화 밀도 (Net Speaking Density) - 집중도 지표

기존 '전체 시간 대비 발음 시간'을 보완한 지표입니다. 전체 시간에서 '불가피한 시간(AI가 말하는 시간, 로딩 시간)'을 뺀 시간 대비 비율입니다.

개념: (나의 실제 발음 시간) ÷ (전체 세션 시간 - AI/TTS 오디오 재생 시간 - 시스템 로딩 시간)

왜 필요한가?:

AI가 길게 설명하는 동안 학생 비율이 떨어지는 왜곡을 방지합니다.

순수하게 **'내가 말할 기회가 주어졌을 때 얼마나 꽉 채워서 말했는가'**를 보여줍니다.

통계 활용 예시:

대화 적극성: AI 대화에서 단답형(Yes/No)으로만 대답하면 이 비율이 낮게 나옵니다. 길게 설명할수록 비율이 높아집니다.

튜터 대시보드: "최토익 학생은 듣기는 잘하지만, 답변이 너무 짧아요(밀도 20%). 더 긴 문장 유도가 필요해요."


## 예상 일정 (2주)

### Week 1: 핵심 기능

| Day | 작업 |
|-----|------|
| 1 | 인프라 셋업 (Cognito, API Gateway, DynamoDB) |
| 2 | 기본 UI (로그인, 학생/튜터 화면 레이아웃) |
| 3 | 발음 감지 (TensorFlow.js + Web Audio) |
| 4 | 문장 연습 기능 (문장 표시, Polly TTS, 발음 측정) |
| 5 | WebSocket 기본 (연결, 상태 전송) |
| 6-7 | 튜터 대시보드 (학생 목록, 실시간 상태) |

### Week 2: AI 대화 + 마무리

| Day | 작업 |
|-----|------|
| 8-9 | AI 대화 기능 (Claude API + Polly TTS) |
| 10 | 튜터 피드백 기능 (텍스트/TTS 전송) |
| 11 | 통계 기능 (일별/주별 학습 현황) |
| 12 | WebSocket fanout (1:N 브로드캐스트) |
| 13-14 | 통합 테스트, 버그 수정, 배포 |

---

## 면접 어필 포인트

**Q: 프로젝트 소개해주세요**
```
"영어 학습 시 실제로 발음하는 시간을 AI로 측정하는 플랫폼입니다.
TensorFlow.js로 입 움직임, Web Audio로 음성을 동시에 감지해서
정확한 발음 시간을 측정합니다.

또한 Claude API로 AI와 자유롭게 영어 대화할 수 있고,
튜터가 학생들을 실시간으로 모니터링하며 피드백할 수 있습니다."
```

**Q: 가장 어려웠던 점은?**
```
"1명의 튜터가 다수의 학생을 실시간으로 모니터링하는 부분입니다.
학생이 1초마다 상태를 전송하면, 해당 튜터에게 연결된 
모든 WebSocket을 찾아서 브로드캐스트해야 합니다.

DynamoDB GSI로 튜터별 연결을 빠르게 조회하고,
Promise.all로 병렬 전송해서 해결했습니다."
```

**Q: AI API 비용 관리는?**
```
"max_tokens를 150으로 제한해서 짧은 응답만 받고,
대화 컨텍스트도 최근 5턴만 유지해서 토큰을 줄였습니다."
```