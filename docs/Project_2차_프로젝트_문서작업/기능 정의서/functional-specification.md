# 기능 정의서 (Functional Specification)

## 문서 개요

**프로젝트명**: SpeakTracker - AI 기반 1:N 외국어 학습 플랫폼
**작성자**: 3조 (안창완, 강형원, 황성준)
**기술 스택**: React 18, Redux Toolkit, Material-UI, AWS (Lambda, DynamoDB, S3, Cognito, SQS, API Gateway), Whisper STT, AWS Polly TTS, MediaPipe

---

## 목차
1. [시스템 개요](#1-시스템-개요)
2. [사용자 역할 정의](#2-사용자-역할-정의)
3. [핵심 기능](#3-핵심-기능)
4. [화면별 기능 상세](#4-화면별-기능-상세)
5. [공통 기능 및 기술 구현](#5-공통-기능-및-기술-구현)
6. [비기능 요구사항](#6-비기능-요구사항)
7. [제약사항 및 전제조건](#7-제약사항-및-전제조건)
8. [향후 확장 계획](#8-향후-확장-계획)

---

## 1. 시스템 개요

### 1.1 프로젝트 배경 및 목적

**배경**:
- 학생의 발음 연습 시간을 수동으로 측정하는 데 어려움
- AI 기술을 활용한 자동 평가 및 맞춤형 학습 부재

**목적**:
- **1:N 학습 관리**: 튜터 1명이 다수(최대 10명)의 학생을 효율적으로 모니터링
- **자동 측정**: 입 움직임(MAR) + 음성 감지로 발음 연습 시간 자동 측정
- **AI 활용**: Claude AI/ChatGPT를 활용한 자연스러운 영어 대화 연습
- **즉각 피드백**: 발음 평가 자동화 및 튜터의 맞춤형 피드백 제공

### 1.2 핵심 가치 제안

| 핵심 가치 | 설명 | 기대 효과 |
|----------|------|----------|
| **발음 시간 자동 측정** | 입 움직임(MAR) + 음성 감지로 학생이 실제로 말하는 시간을 자동으로 측정 | 튜터의 관리 부담 감소 |
| **AI 회화 연습** | Claude/ChatGPT API로 자연스러운 영어 대화, Polly TTS로 원어민 음성 제공 | 학생의 회화 실력 향상 기대 |
| **1:N 관리** | 튜터 1명이 다수 학생을 효율적으로 모니터링 및 피드백 | 튜터 피로감 감소 |
| **실시간 모니터링** | WebSocket을 통한 학생 상태 실시간 확인 | 즉각적인 개입 가능 |

### 1.3 시스템 구성도

```
┌─────────────────────────────────────────────────────────┐
│                   SpeakTracker System                   │
├─────────────────────────────────────────────────────────┤
│  [학생용 기능]              [튜터용 기능]               │
│  - 문장 연습                - 학생 목록 조회            │
│  - AI 대화                  - 실시간 모니터링           │
│  - 발음 평가                - 피드백 전송               │
│  - 학습 통계                - 학습 이력 조회            │
│  - 튜터 매칭                - 매칭 요청 관리            │
├─────────────────────────────────────────────────────────┤
│  [공통 기능]                                            │
│  - 회원가입/로그인          - 프로필 관리               │
│  - 알림                     - 대시보드                  │
└─────────────────────────────────────────────────────────┘
```

---

## 2. 사용자 역할 정의

### 2.1 학생 (Student)

**역할**: 영어 회화 학습을 위해 플랫폼을 사용하는 사용자

**주요 작업**:
- 튜터 검색 및 매칭 요청
- 문장 연습 (AI 생성 문장 또는 튜터 제공 문장)
- AI와 영어 대화 연습
- 발음 평가 결과 확인
- 튜터 피드백 수신
- 학습 통계 확인

**권한**:
- 자신의 학습 데이터 조회/수정
- 튜터에게 매칭 요청 전송
- AI 대화 세션 생성

**제약사항**:
- 1명의 튜터만 배정 가능
- 다른 학생의 데이터 조회 불가

---

### 2.2 튜터 (Tutor)

**역할**: 학생의 학습을 관리하고 피드백을 제공하는 교육자

**주요 작업**:
- 학생 매칭 요청 승인/거절
- 담당 학생 목록 조회
- 학생별 학습 이력 조회
- 실시간 학생 상태 모니터링 (어떤 화면에 있는지, 학습 중인지)
- 학생에게 텍스트/음성 피드백 전송
- 학생 레벨 평가

**권한**:
- 담당 학생의 모든 학습 데이터 조회
- 학생에게 피드백 전송
- 매칭 요청 승인/거절
- 최대 학생 수 설정 (기본 10명)

**제약사항**:
- 담당하지 않는 학생의 데이터 조회 불가
- 학생의 학습 데이터 수정 불가 (조회만 가능)

---

## 3. 핵심 기능

## 3.1 인증 및 사용자 관리

### 3.1.1 회원가입

**기능 ID**: AUTH-001
**설명**: 이메일과 비밀번호로 회원가입

**입력**:
- 이메일 (필수, 유효성 검증)
- 비밀번호 (필수, 최소 8자, 영문/숫자/특수문자 포함)
- 이름 (필수)
- 역할 (필수, `student` 또는 `tutor`)

**처리 과정**:
1. 입력값 유효성 검증
2. 이메일 중복 확인
3. AWS Cognito User Pool에 사용자 등록
4. DynamoDB Users 테이블에 프로필 저장
5. 이메일 인증 코드 발송 (SES)

**출력**:
- 성공: "회원가입이 완료되었습니다. 이메일을 확인해주세요."
- 실패: 에러 메시지 (예: "이미 등록된 이메일입니다.")

**비즈니스 규칙**:
- 이메일은 고유해야 함
- 비밀번호는 암호화하여 저장 (Cognito 자동 처리)
- 이메일 인증 완료 전까지 로그인 불가

**사용자 흐름**:
```
1. 회원가입 페이지 접속
2. 정보 입력 (이메일, 비밀번호, 이름, 역할)
3. 이메일 인증 코드 수신
4. 인증 코드 입력
5. 계정 활성화 완료
6. 로그인 페이지로 이동
```

**관련 파일**:
- [SignUpPage.jsx](src/pages/SignUpPage.jsx)
- [authSlice.js](src/store/authSlice.js)
- [authApi.js](src/api/authApi.js)

---

### 3.1.2 로그인

**기능 ID**: AUTH-002
**설명**: 이메일과 비밀번호로 로그인하여 JWT 토큰 발급

**입력**:
- 이메일
- 비밀번호

**처리 과정**:
1. Cognito에 인증 요청
2. 인증 성공 시 JWT 토큰 발급 (idToken, accessToken, refreshToken)
3. DynamoDB에서 사용자 프로필 조회 (role, name 등)
4. 클라이언트에 토큰 + 프로필 반환
5. 토큰 localStorage 저장
6. 자동 로그인 지원 (토큰 유효 시)

**출력**:
- 성공: `{idToken, accessToken, refreshToken, user: {email, role, name}}`
- 실패: 에러 메시지 (예: "이메일 또는 비밀번호가 올바르지 않습니다.")

**비즈니스 규칙**:
- 이메일 인증 완료된 사용자만 로그인 가능
- idToken 유효기간: 1시간
- accessToken 유효기간: 1시간
- refreshToken 유효기간: 30일

**토큰 관리**:
- 401 응답 시 자동 토큰 갱신
- 갱신 실패 시 자동 로그아웃
- 토큰 만료 체크

**사용자 흐름**:
```
1. 로그인 페이지 접속
2. 이메일/비밀번호 입력
3. JWT 토큰 발급
4. 역할에 따라 리디렉션:
   - 학생: /student/home
   - 튜터: /tutor/dashboard
```

**관련 파일**:
- [LoginPage.jsx](src/pages/LoginPage.jsx)
- [authSlice.js](src/store/authSlice.js)
- [authApi.js](src/api/authApi.js)

---

### 3.1.3 프로필 관리

**기능 ID**: AUTH-003
**설명**: 사용자 프로필 조회 및 수정

**기능**:
- 프로필 조회: `GET /api/auth/user`
- 프로필 수정: `PUT /api/auth/profile`
- 프로필 이미지 업로드: `POST /api/auth/profile/image`

**수정 가능 항목**:
- 이름
- 자기소개
- 프로필 이미지
- 튜터의 경우: `isAccepting` (학생 수락 여부), `maxStudent` (최대 학생 수)

**프로필 이미지 업로드 프로세스**:
1. Backend에서 S3 Presigned URL 요청
2. 15분 만료 시간, PUT 권한만 부여
3. 클라이언트가 S3에 직접 업로드 (최대 5MB)
4. CloudFront URL로 이미지 표시
5. DynamoDB Users 테이블에 URL 저장

**비즈니스 규칙**:
- 이메일, 역할(role)은 수정 불가
- 프로필 이미지는 S3에 업로드 후 URL 저장 (최대 5MB)

**사용자 흐름**:
```
1. 프로필 페이지 접속
2. 현재 정보 확인
3. 수정 버튼 클릭
4. 정보 입력 (이름, 자기소개)
5. 프로필 이미지 선택 (선택사항)
6. Presigned URL 요청 → S3 업로드
7. 저장 버튼 클릭
8. 업데이트 완료 메시지 표시
```

**관련 파일**:
- [ProfilePage.jsx](src/pages/ProfilePage.jsx)
- [TutorProfilePage.jsx](src/pages/TutorProfilePage.jsx)
- [ProfileImageSection.jsx](src/components/profile/ProfileImageSection.jsx)
- [authApi.js](src/api/authApi.js)

---

## 3.2 튜터 매칭

### 3.2.1 튜터 검색

**기능 ID**: MATCH-001
**설명**: 학생이 튜터를 검색하여 목록 조회

**입력**:
- 검색어 (선택사항, 이름 또는 이메일)
- 필터 (선택사항, 예: `isAccepting=true`)

**출력**:
```json
{
  "tutors": [
    {
      "email": "tutor@example.com",
      "name": "김튜터",
      "isAccepting": true,
      "maxStudents": 10,
      "currentStudents": 7
    }
  ]
}
```

**비즈니스 규칙**:
- `isAccepting=true`인 튜터만 기본 표시
- `currentStudents >= maxStudents`인 튜터는 "정원 마감" 표시

**사용자 흐름**:
```
1. 프로필 페이지에서 "튜터 검색" 버튼 클릭
2. 튜터 검색 다이얼로그 오픈
3. 튜터 이름 검색
4. 튜터 목록에서 선택 (프로필 이미지, 이름, 자기소개 표시)
```

**관련 파일**:
- [TutorSearchDialog.jsx](src/components/student/TutorSearchDialog.jsx)
- [tutorApi.js](src/api/tutorApi.js)

---

### 3.2.2 매칭 요청 전송

**기능 ID**: MATCH-002
**설명**: 학생이 튜터에게 매칭 요청 전송

**입력**:
- 튜터 이메일
- 요청 메시지 (선택사항, 예: "영어 회화 실력을 향상시키고 싶습니다.")

**처리 과정**:
1. 학생이 이미 튜터가 있는지 확인
2. 튜터가 학생 수락 중인지 확인 (`isAccepting=true`)
3. 튜터 정원 확인 (`currentStudents < maxStudents`)
4. TutorRequests 테이블에 요청 저장 (status=PENDING)
5. 튜터에게 알림 전송 (WebSocket + Notifications 테이블)

**출력**:
- 성공: `{requestId, status: "PENDING"}`
- 실패: 에러 메시지 (예: "이미 튜터가 배정되어 있습니다.")

**비즈니스 규칙**:
- 학생은 1명의 튜터만 요청 가능 (중복 요청 방지)
- 튜터가 `isAccepting=false`이면 요청 불가

**사용자 흐름**:
```
1. 튜터 선택
2. 요청 메시지 작성
3. "요청 보내기" 클릭
4. 요청 전송 완료
5. WebSocket 알림 대기
```

**관련 파일**:
- [TutorRequestDialog.jsx](src/components/student/TutorRequestDialog.jsx)
- [tutorApi.js](src/api/tutorApi.js)
- [useWebSocket.js](src/hooks/webSocket/useWebSocket.js)

---

### 3.2.3 매칭 요청 승인/거절 (튜터)

**기능 ID**: MATCH-003
**설명**: 튜터가 학생의 매칭 요청을 승인 또는 거절

**입력**:
- 요청 ID
- 거절 사유 (거절 시 선택사항)

**처리 과정 (승인)**:
1. TutorRequests 테이블 업데이트 (status=APPROVED)
2. TutorStudents 테이블에 매칭 레코드 생성 (DynamoDB Transactions로 원자성 보장)
3. 학생의 `tutorEmail` 업데이트
4. WebSocket으로 학생에게 `TUTOR_REQUEST_APPROVED` 메시지 전송
5. Notifications 테이블에 알림 저장 ("김튜터님과 매칭되었습니다.")

**처리 과정 (거절)**:
1. TutorRequests 테이블 업데이트 (status=REJECTED)
2. WebSocket으로 학생에게 `TUTOR_REQUEST_REJECTED` 메시지 전송
3. Notifications 테이블에 알림 저장 (거절 사유 포함)

**비즈니스 규칙**:
- 승인 시 DynamoDB Transactions로 원자성 보장
- 튜터 정원 초과 시 승인 불가
- 거절 후에도 학생은 다시 요청 가능

**사용자 흐름 (튜터)**:
```
1. 대시보드 "요청 관리" 탭
2. 요청 목록 확인 (학생 이름, 이메일, 요청 메시지)
3. 학생 정보 검토
4. 승인 또는 거절 선택
5. (거절 시) 사유 입력
6. 확인 버튼 클릭
7. WebSocket으로 학생에게 즉시 알림 전송
```

**사용자 흐름 (학생)**:
```
1. 요청 전송 후 대기
2. WebSocket 알림 수신
3. 승인 시: "튜터가 배정되었습니다" 알림 + Redux 상태 업데이트
4. 거절 시: "요청이 거절되었습니다" + 사유 표시
```

**관련 파일**:
- [DashboardPage.jsx](src/pages/DashboardPage.jsx)
- [tutorApi.js](src/api/tutorApi.js)
- [useNotificationHandler.js](src/hooks/tutor/useNotificationHandler.js)
- [useStudentNotifications.js](src/hooks/student/useStudentNotifications.js)

---

## 3.3 문장 연습 (Sentence Practice)

### 3.3.1 문장 생성

**기능 ID**: PRACTICE-001
**설명**: AI가 학생 레벨에 맞는 문장 생성

**입력**:
- 난이도 (UI: `下/중/상`, API: `easy/medium/hard`)
  - 下: 8단어 이하, 기초 어휘
  - 중: 15단어 이하, 일상 어휘
  - 상: 25단어 이하, 고급 어휘
- 주제 (`small_talk`, `restaurant`, `airport`, `shopping`, `hotel`, `doctor`, `job_interview`, `directions`)
- 생성 개수 (기본 5개)

**처리 과정**:
1. Claude AI API 호출 (학생 레벨 + 주제 기반 프롬프트)
2. 생성된 문장 반환 (영어 + 한글 번역)
3. 각 문장에 대해 TTS 비동기 요청 (SQS)
4. 클라이언트에 문장 + sessionId 반환

**출력**:
```json
{
  "sentences": [
    {"english": "How was your day?", "korean": "오늘 하루 어땠어요?"},
    {"english": "I went to the park.", "korean": "나는 공원에 갔어요."}
  ],
  "sessionId": "gen-sess-123"
}
```

**비즈니스 규칙**:
- 난이도별 문장 복잡도:
  - `下`: 5-10 단어, 현재 시제 중심
  - `중`: 10-15 단어, 과거/미래 시제 포함
  - `상`: 15단어 이상, 복합 문장, 관용 표현 포함
- TTS는 비동기 처리 (즉시 반환, 폴링으로 완료 확인)

**사용자 흐름**:
```
1. 홈 화면에서 "문장 연습" 선택
2. 난이도/주제 선택 모달 (SessionConfigModal)
3. 문장 생성 완료 (5개)
4. 첫 번째 문장 표시
```

**관련 파일**:
- [PracticePage.jsx](src/pages/PracticePage.jsx)
- [SessionConfigModal.jsx](src/components/student/SessionConfigModal.jsx)
- [sentencesApi.js](src/api/sentencesApi.js)

---

### 3.3.2 TTS 음성 생성

**기능 ID**: PRACTICE-002
**설명**: 영어 문장을 원어민 음성(MP3)으로 변환

**입력**:
- 영어 문장
- 음성 ID (기본값: `Matthew`)
- 엔진 타입 (기본값: `neural`)

**처리 과정**:
1. Backend API 호출: `POST /api/tts/synthesize`
2. **캐시 히트 시**: DynamoDB SentenceAudio 테이블에서 기존 URL 즉시 반환 (200)
3. **캐시 미스 시**: SQS로 TTS 요청 전송 (비동기), jobId 반환 (202)
4. Lambda가 SQS 메시지 처리
5. Polly로 MP3 생성
6. S3에 업로드 (`tts/{sessionId}-{index}.mp3`)
7. SentenceAudio 테이블에 메타데이터 저장
8. AsyncJobStatus 테이블 업데이트 (status=COMPLETED)

**폴링 프로세스**:
1. 클라이언트가 `GET /api/tts/status/{jobId}` 주기적으로 호출
2. 지수 백오프 (1초 → 3초)
3. 최대 30-60회 시도
4. status=COMPLETED 시 audioUrl 수신

**출력** (상태 조회 API):
```json
{
  "jobId": "tts-job-123",
  "status": "COMPLETED",
  "audioUrl": "https://d1234.cloudfront.net/tts/sess-123-0.mp3",
  "durationMs": 2500
}
```

**비즈니스 규칙**:
- TTS 파일은 7일 후 S3에서 자동 삭제 (Lifecycle Policy)
- 동일 문장은 캐싱하여 재사용 (비용 절감)
- CloudFront CDN으로 전 세계 빠른 응답

**오디오 재생**:
- HTML5 Audio 요소로 재생
- 재생 완료 콜백 지원
- 참조 오디오 재생 시간 측정 (페이스 비율 계산용)

**관련 파일**:
- [useTTSAudio.js](src/hooks/useTTSAudio.js)
- [ttsApi.js](src/api/ttsApi.js)

---

### 3.3.3 발음 평가 (STT + 페이스 비율)

**기능 ID**: PRACTICE-003
**설명**: 학생의 발음을 자동으로 평가

**입력**:
- 녹음된 오디오 파일 (WebM, MP3 등)
- 원본 영어 문장
- 참조 오디오 재생 시간 (밀리초)

**로컬 STT 처리 (Whisper)**:
1. MediaRecorder API로 오디오 녹음
2. 오디오 전처리:
   - AudioContext로 PCM 디코딩
   - 16kHz 리샘플링
   - DC 오프셋 제거
   - 피크 진폭 정규화
   - 무음 자동 트리밍
3. Whisper Worker에 오디오 전송
4. 실시간 변환 진행률 표시
5. 변환된 텍스트 반환

**페이스 비율 계산**:
- **공식**: 사용자 발화 시간 / 참조 오디오 시간
- **목표**: 0.8 ~ 1.2 (적정 속도)
- **피드백**:
  - < 0.8: "너무 빨라요. 천천히 말해보세요."
  - 0.8 ~ 1.2: "좋은 속도입니다!"
  - > 1.2: "너무 느려요. 조금 더 빠르게 말해보세요."

**통계 누적**:
1. Redux Store에 실시간 업데이트
   - 연습 횟수 카운트
   - 평균 페이스 비율 계산 (러닝 평균)
   - 연습 발화 시간 누적
2. localStorage에 오늘의 통계 캐싱
3. 세션 종료 시 Backend 동기화

**출력**:
```json
{
  "transcribedText": "How was your day",
  "originalText": "How was your day?",
  "userDurationMs": 2400,
  "referenceDurationMs": 2500,
  "paceRatio": 0.96,
  "feedback": "좋은 속도입니다!",
  "audioDurationMs": 2400
}
```

**비즈니스 규칙**:
- 발음 평가 결과는 30일 보관 (TTL)
- 실시간 색상 표시 (빨강/주황/초록)

**사용자 흐름**:
```
1. 첫 번째 문장 표시
2. 참조 오디오 재생 버튼 클릭 (TTS 재생)
3. 녹음 버튼 클릭 (발화 시작)
4. 발화 완료 후 녹음 중지
5. 오디오 전처리 + Whisper STT 변환 (로컬)
6. 페이스 비율 계산 및 피드백 표시
7. Redux 통계 업데이트
8. 다음 문장으로 이동
9. 5개 문장 완료 후 연습 종료
```

**관련 파일**:
- [PracticePage.jsx](src/pages/PracticePage.jsx)
- [useWhisperSTT.js](src/hooks/useWhisperSTT.js)
- [postRecordingPipeline.js](src/utils/postRecordingPipeline.js)
- [speakingStatsSlice.js](src/store/speakingStatsSlice.js)

---

## 3.4 AI 대화 (AI Conversation)

### 3.4.1 대화 시작

**기능 ID**: CONVERSATION-001
**설명**: AI와 영어 대화 세션 시작

**입력**:
- 난이도 (UI: `상/중/하`, API: `hard/medium/easy`)
- 시나리오 (`restaurant`, `airport`, `shopping`, `hotel`, `doctor`, `job_interview`, `directions`)
- AI 역할 (예: `친구`, `선생님`, `상점 직원`)

**처리 과정**:
1. Backend API 호출: `POST /api/ai-chat/start`
2. Claude AI에 시스템 프롬프트 전송:
   ```
   You are a [역할] having a conversation at [난이도] level about [시나리오].
   Keep your responses natural and encourage the student to practice speaking.
   ```
3. AI의 첫 메시지 생성
4. TTS로 음성 변환 (비동기)
5. AIConversations 테이블에 세션 저장
6. 클라이언트에 conversationId + 첫 메시지 반환

**출력**:
```json
{
  "conversationId": "conv-abc123",
  "situation": "카페에서 친구와 만나는 상황",
  "firstMessage": "Hey! Long time no see. How have you been?",
  "audioJobId": "tts-ai-1"
}
```

**비즈니스 규칙**:
- 대화는 최대 10턴까지 진행 (비용 제어)
- 대화 컨텍스트는 AIConversations 테이블에 저장
- 30일 후 자동 삭제 (TTL)

**사용자 흐름**:
```
1. 홈 화면에서 "AI 채팅" 선택
2. 난이도/시나리오 선택 모달 (SessionConfigModal)
3. 대화 시작 (AI 인사 메시지)
4. AI 메시지 TTS 자동 재생
```

**관련 파일**:
- [ChatPage.jsx](src/pages/ChatPage.jsx)
- [SessionConfigModal.jsx](src/components/student/SessionConfigModal.jsx)
- [aiChatApi.js](src/api/aiChatApi.js)

---

### 3.4.2 대화 진행

**기능 ID**: CONVERSATION-002
**설명**: 학생의 응답을 받아 AI가 대답

**입력**:
- 대화 ID
- 학생의 음성 (오디오 파일)

**처리 과정**:
1. 음성을 Whisper로 텍스트 변환 (로컬 STT)
2. 발화 시간 측정 (useSpeechActivityTracker)
3. Backend에 메시지 전송: `POST /api/ai-chat/send`
4. 이전 대화 컨텍스트 + 학생 응답을 Claude AI에 전달
5. AI 응답 생성
6. TTS로 음성 변환
7. 턴 수 증가
8. AsyncJobStatus 테이블에 처리 상태 저장
9. 클라이언트에 AI 응답 반환

**응답 품질 측정**:
- **발화 시간 (Duration)**: 밀리초
- **단어 수 (Word Count)**: 인식된 텍스트의 단어 수
- **분당 단어 수 (WPM)**: (단어 수 / 발화 시간) × 60000
- **유창성 점수 (Fluency Score)**: 0-100
- **전체 점수 (Overall Score)**: 0-100

**응답 지연 추적**:
- AI 응답 대기 시간 측정
- 평균 응답 지연 계산
- Redux에 실시간 업데이트

**출력** (비동기 처리 후):
```json
{
  "status": "COMPLETED",
  "aiMessage": "That's great to hear! What have you been working on?",
  "audioJobId": "tts-ai-2",
  "turnCount": 2,
  "responseQuality": {
    "duration": 3200,
    "wordCount": 12,
    "wpm": 225,
    "fluencyScore": 85,
    "overallScore": 88
  }
}
```

**비즈니스 규칙**:
- 응답 생성 시간: 평균 3-5초
- SQS로 비동기 처리하여 Lambda 타임아웃 회피
- 10턴 도달 시 대화 종료 안내

**사용자 흐름**:
```
1. AI 메시지 TTS 자동 재생
2. 녹음 버튼 클릭하여 응답
3. 발화 완료 후 녹음 중지
4. Whisper STT 변환 (로컬) + 발화 시간 측정
5. 메시지 전송
6. AI 응답 대기 (로딩)
7. AI 응답 수신 및 TTS 재생
8. 응답 품질 점수 표시 (WPM, Fluency, Overall)
9. 대화 반복
```

**관련 파일**:
- [ChatPage.jsx](src/pages/ChatPage.jsx)
- [aiChatApi.js](src/api/aiChatApi.js)
- [useWhisperSTT.js](src/hooks/useWhisperSTT.js)
- [useSpeechActivityTracker.js](src/hooks/conversation/useSpeechActivityTracker.js)
- [ttsApi.js](src/api/ttsApi.js)

---

### 3.4.3 대화 종료 및 평가

**기능 ID**: CONVERSATION-003
**설명**: AI 대화 종료 및 학습 통계 업데이트

**처리 과정**:
1. 대화 세션 종료 버튼 클릭
2. Backend API 호출: `POST /api/ai-chat/end`
3. LearningSessions 테이블에 기록 (sessionType=conversation)
4. DailyStatistics 업데이트 (chat_turns_count, avg_response_quality 등)
5. 모든 메시지 히스토리 저장 (대화 목록에서 재조회 가능)
6. Redux 통계 업데이트
7. localStorage 동기화
8. Backend 통계 동기화 (중복 방지 해시 체크)

**출력**:
```json
{
  "message": "대화가 종료되었습니다.",
  "turnCount": 8,
  "duration": 1200,
  "avgResponseQuality": 85
}
```

**사용자 흐름**:
```
1. 종료 버튼으로 대화 종료
2. 대화 통계 표시 (턴 수, 지속 시간, 평균 응답 품질)
3. Redux → localStorage → Backend 동기화
4. 통계 페이지에서 대화 히스토리 조회 가능
```

**관련 파일**:
- [ChatPage.jsx](src/pages/ChatPage.jsx)
- [aiChatApi.js](src/api/aiChatApi.js)
- [speakingStatsSlice.js](src/store/speakingStatsSlice.js)
- [statsSync.js](src/utils/statsSync.js)

---

## 3.5 튜터 피드백

### 3.5.1 피드백 전송 (튜터)

**기능 ID**: FEEDBACK-001
**설명**: 튜터가 학생에게 텍스트 또는 음성 피드백 전송

**입력**:
- 학생 이메일
- 피드백 메시지
- 메시지 타입 (`text` 또는 `audio`)
- 관련 세션 ID (선택사항)

**처리 과정**:
1. Backend API 호출: `POST /api/feedback`
2. FeedbackMessages 테이블에 저장
3. (선택) TTS로 음성 피드백 자동 변환
4. 학생의 WebSocket 연결 조회 (WebSocketConnections 테이블)
5. 실시간으로 학생에게 `NEW_FEEDBACK` 메시지 전송 (WebSocket)
6. Notifications 테이블에 알림 저장
7. 연결되어 있지 않으면 websocketSent=false로 저장

**출력**:
```json
{
  "feedbackId": "fb-789012",
  "message": "피드백이 전송되었습니다.",
  "websocketSent": true
}
```

**비즈니스 규칙**:
- 음성 피드백은 TTS로 자동 변환 가능 (선택사항)
- 피드백은 90일 보관 (TTL)

**사용자 흐름 (튜터)**:
```
1. 학생 상세 페이지 진입
2. 새 피드백 입력 영역으로 스크롤
3. 메시지 입력
4. TTS 생성 (선택)
5. "전송" 버튼 클릭
6. WebSocket으로 학생에게 실시간 전달
7. 피드백 히스토리에 추가
```

**관련 파일**:
- [StudentDetailPage.jsx](src/pages/StudentDetailPage.jsx)
- [FeedbackInput.jsx](src/components/tutor/FeedbackInput.jsx)
- [feedbackApi.js](src/api/feedbackApi.js)
- [useTutorFeedback.js](src/hooks/tutor/useTutorFeedback.js)

---

### 3.5.2 피드백 수신 (학생)

**기능 ID**: FEEDBACK-002
**설명**: 학생이 받은 피드백 목록 조회 및 실시간 알림

**실시간 피드백 알림**:
1. WebSocket으로 `NEW_FEEDBACK` 메시지 수신
2. 화면 오버레이 표시 (TutorFeedbackOverlay)
3. TTS 오디오 자동 재생 (피드백 메시지)
4. "확인" 버튼으로 닫기
5. 읽음 처리

**피드백 목록 조회**:
- API: `GET /api/feedback`
- 출력:
```json
{
  "feedbacks": [
    {
      "feedbackId": "fb-789012",
      "tutorEmail": "tutor@example.com",
      "message": "오늘 발음 연습 잘했어요!",
      "timestamp": "2025-01-30T17:30:00Z",
      "isRead": false
    }
  ]
}
```

**사용자 흐름 (학생)**:
```
1. 튜터가 피드백 전송
2. WebSocket 알림 수신
3. 화면에 피드백 오버레이 표시
4. TTS 오디오 자동 재생
5. "확인" 버튼으로 닫기
6. 피드백 다이얼로그에서 히스토리 조회 가능 (FeedbackDialog)
```

**관련 파일**:
- [TutorFeedbackOverlay.jsx](src/components/student/TutorFeedbackOverlay.jsx)
- [FeedbackDialog.jsx](src/components/student/FeedbackDialog.jsx)
- [useTutorFeedback.js](src/hooks/student/useTutorFeedback.js)
- [feedbackApi.js](src/api/feedbackApi.js)

---

## 3.6 실시간 모니터링 (튜터)

### 3.6.1 학생 목록 조회

**기능 ID**: MONITOR-001
**설명**: 튜터가 담당 학생 목록 조회

**기능**:
1. **학생 목록 표시**:
   - API: `GET /api/tutor/students`
   - 배정된 모든 학생 목록
   - 학생 이름, 이메일, 프로필 이미지
   - 학습 레벨 (下/중/상)
   - 실시간 상태 (온라인/오프라인/연습중/채팅중)

2. **학습 레벨 표시**:
   - API: `GET /api/auth/students-level`
   - 로그인 시 모든 학생 레벨 조회 (1회)
   - Redux에 캐싱
   - 색상 코딩 (상: 빨강, 중: 주황, 下: 초록)

**출력**:
```json
{
  "students": [
    {
      "email": "student1@example.com",
      "name": "학생1",
      "status": "active",
      "room": "practice",
      "lastUpdated": "2025-01-30T18:30:00Z"
    }
  ]
}
```

**비즈니스 규칙**:
- `room` 값:
  - `null`: 오프라인
  - `practice`: 문장 연습 중
  - `ai-conversation`: AI 대화 중
  - `statistics`: 통계 확인 중

**사용자 흐름**:
```
1. 튜터 로그인
2. 대시보드 자동 진입
3. 학생 목록 로드
4. 학습 레벨 조회 (1회) → Redux 캐싱
5. 실시간 상태 업데이트 시청
6. 학생 카드 클릭 → 학생 상세 페이지 이동
```

**관련 파일**:
- [DashboardPage.jsx](src/pages/DashboardPage.jsx)
- [StudentCard.jsx](src/components/tutor/StudentCard.jsx)
- [tutorApi.js](src/api/tutorApi.js)
- [tutorStatsSlice.js](src/store/tutorStatsSlice.js)
- [useTutorStudents.js](src/hooks/tutor/useTutorStudents.js)

---

### 3.6.2 학생 상태 실시간 업데이트 (WebSocket)

**기능 ID**: MONITOR-002
**설명**: 학생이 화면을 이동하면 튜터에게 실시간 알림

**처리 과정**:
1. 학생이 화면 이동 시 `POST /api/student-status` 호출
2. TutorStudents 테이블 업데이트 (room, updatedAt)
3. 튜터의 WebSocket 연결 조회
4. 튜터에게 `STUDENT_STATUS_UPDATE` 메시지 전송:
   ```json
   {
     "type": "STUDENT_STATUS_UPDATE",
     "studentEmail": "student1@example.com",
     "room": "ai-conversation",
     "isLearning": true
   }
   ```
5. 튜터 대시보드 실시간 업데이트

**비즈니스 규칙**:
- 학생이 5분간 활동이 없으면 `room=null`로 자동 업데이트
- WebSocket 연결이 없으면 다음 연결 시 조회 가능

**오프라인 감지**:
- `navigator.onLine` 체크
- 페이지 닫힘 감지 (Beacon API)
- 자동 오프라인 상태 전송

**사용자 흐름 (학생)**:
```
1. 로그인 시 WebSocket 연결
2. 화면 이동 시마다 상태 업데이트 API 호출
3. 오프라인/페이지 닫힘 시 자동 오프라인 상태 전송
```

**사용자 흐름 (튜터)**:
```
1. 대시보드에서 학생 목록 시청
2. WebSocket으로 실시간 상태 업데이트 수신
3. 상태 인디케이터 실시간 업데이트 (색상 변경)
```

**관련 파일**:
- [useWebSocket.js](src/hooks/webSocket/useWebSocket.js)
- [webSocketConfig.js](src/config/webSocketConfig.js)
- [useTutorNotifications.js](src/hooks/tutor/useTutorNotifications.js)

---

### 3.6.3 학생 학습 이력 조회

**기능 ID**: MONITOR-003
**설명**: 튜터가 학생의 세션 이력 조회

**입력**:
- 학생 이메일
- 조회 기간 (선택사항, startDate, endDate)
- 세션 유형 필터 (선택사항, practice/conversation)

**기능**:
1. **주간 통계 조회**:
   - 특정 학생의 최근 7일 데이터
   - 발화 시간, 페이스 비율, 순 발화 밀도
   - 트렌드 차트 표시

2. **학습 패턴 분석**:
   - 연습 vs 채팅 비율
   - 활동 빈도
   - 평균 응답 품질

**출력**:
```json
{
  "sessions": [
    {
      "sessionId": "sess-123",
      "timestamp": "2025-01-30T18:00:00Z",
      "sessionType": "practice",
      "duration": 1800,
      "speakingDuration": 1200,
      "avgAccuracy": 88.5
    }
  ]
}
```

**사용자 흐름**:
```
1. 대시보드에서 학생 카드 클릭
2. 학생 상세 페이지 진입 (StudentDetailPage)
3. 주간 통계 차트 확인
4. 학습 패턴 분석
5. 피드백 히스토리 조회
6. 새 피드백 전송
```

**관련 파일**:
- [StudentDetailPage.jsx](src/pages/StudentDetailPage.jsx)
- [FeedbackHistory.jsx](src/components/tutor/FeedbackHistory.jsx)
- [tutorApi.js](src/api/tutorApi.js)

---

## 3.7 학습 통계

### 3.7.1 오늘의 통계

**기능 ID**: STATS-001
**설명**: 학생의 오늘 학습 통계 조회

**KPI 카드 (실시간 지표)**:
1. **총 발화 시간**
   - 오늘 하루 동안 말한 총 시간 (밀리초 → 분:초)
   - Redux Store에서 실시간 업데이트

2. **순 발화 밀도**
   - 공식: (발화 시간 / 녹음 시간) × 100
   - 녹음 중 실제로 말한 비율

3. **평균 페이스 비율**
   - 목표: 0.8 ~ 1.2
   - 실시간 색상 표시 (빨강/주황/초록)

4. **평균 응답 품질**
   - AI 채팅 응답 점수 평균
   - 0-100 점수

**출력**:
```json
{
  "date": "2025-01-30",
  "totalSpeakingTime": 3600,
  "totalRecordingTime": 5400,
  "practiceCount": 8,
  "conversationCount": 3,
  "avgAccuracy": 87.5,
  "sessionsCount": 11,
  "avgPaceRatio": 0.95,
  "avgResponseQuality": 85
}
```

**비즈니스 규칙**:
- 매일 자정에 DailyStatistics 테이블에 집계
- 실시간으로도 UpdateExpression으로 누적 가능
- Redux → localStorage → Backend 동기화

**데이터 소스**:
- **Redux Store**: 실시간 세션 데이터
- **localStorage**: 오늘의 통계 캐싱 (날짜별 키 패턴)
- **Backend API**: 주간 통계, 대화 히스토리

**사용자 흐름**:
```
1. 통계 페이지 접속
2. 오늘의 KPI 카드 확인 (실시간 업데이트)
3. localStorage에서 캐시 복원 (앱 재시작 시)
```

**관련 파일**:
- [StatsPage.jsx](src/pages/StatsPage.jsx)
- [KPICards.jsx](src/components/student/stats/KPICards.jsx)
- [speakingStatsSlice.js](src/store/speakingStatsSlice.js)
- [speakingStatsSelectors.js](src/store/speakingStatsSelectors.js)

---

### 3.7.2 주간/월간 통계

**기능 ID**: STATS-002
**설명**: 학생의 주간 또는 월간 통계 조회

**주간 학습 트렌드 차트**:
- 최근 7일 데이터
- 발화 시간 추이 (라인 차트)
- 페이스 비율 추이
- 순 발화 밀도 추이

**활동 분포 차트**:
- 연습 vs 채팅 시간 비율 (파이 차트)
- 일별 활동 비교 (바 차트)

**최근 대화 목록**:
- 최근 AI 채팅 세션 표시
- 주제, 난이도, 메시지 수
- 클릭 시 대화 상세 모달

**입력**:
- 조회 기간 (startDate, endDate)

**출력**:
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
      "practiceCount": 8
    }
  ],
  "summary": {
    "totalSpeakingTime": 18000,
    "avgAccuracy": 85.3
  }
}
```

**비즈니스 규칙**:
- 그래프로 시각화 (주간 학습 시간 추이)
- 목표 달성률 표시 (예: 일일 30분 목표)

**사용자 흐름**:
```
1. 통계 페이지 접속
2. 주간 트렌드 차트 분석
3. 활동 분포 확인 (연습 vs 채팅)
4. 최근 대화 목록 조회
5. 대화 클릭 시 상세 메시지 모달
6. 학습 진척도 확인
```

**관련 파일**:
- [StatsPage.jsx](src/pages/StatsPage.jsx)
- [WeeklySummary.jsx](src/components/student/stats/WeeklySummary.jsx)
- [LearningTrendChart.jsx](src/components/student/stats/LearningTrendChart.jsx)
- [ActivityDistributionChart.jsx](src/components/student/stats/ActivityDistributionChart.jsx)
- [RecentChats.jsx](src/components/student/stats/RecentChats.jsx)
- [statisticsApi.js](src/api/statisticsApi.js)

---

## 3.8 대시보드

### 3.8.1 학생 대시보드

**기능 ID**: DASHBOARD-001
**설명**: 학생 메인 화면에 표시되는 요약 정보

**출력**:
```json
{
  "todayGoal": {
    "targetMinutes": 30,
    "currentMinutes": 22,
    "progress": 73
  },
  "weeklyStats": {
    "totalSpeakingTime": 18000,
    "practiceCount": 42
  },
  "recentSessions": [...],
  "tutor": {
    "email": "tutor@example.com",
    "name": "김튜터"
  },
  "aiRecommendation": "AI Conversation을 시도해보세요."
}
```

**화면 구성**:
- 오늘의 학습 목표 (진행률 표시)
- AI Training Modes 카드:
  - Sentence Practice (문장 연습)
  - AI Conversation (AI 대화)
- Daily Goal 원형 차트
- AI Recommendation 박스
- 사이드바 메뉴:
  - Home
  - Sentence Practice
  - AI Conversation
  - Statistics (통계)
  - Introduce (소개)
  - Profile (프로필)

**관련 파일**:
- [HomePage.jsx](src/pages/HomePage.jsx)

---

### 3.8.2 튜터 대시보드

**기능 ID**: DASHBOARD-002
**설명**: 튜터 메인 화면에 표시되는 요약 정보

**출력**:
```json
{
  "studentsCount": 7,
  "maxStudents": 10,
  "recentActivities": [
    {
      "studentEmail": "student1@example.com",
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

**화면 구성**:
- 오늘의 학생 현황
- 담당 학생 목록:
  - 프로필 이미지
  - 이름
  - 상태 (학습 중/오프라인)
  - 현재 위치 (room)
  - 마지막 업데이트 시간
- 학생 레벨 목록:
  - 이름
  - 현재 레벨 (初/중/상)
  - 평균 정확도
- "피드백 전송" 버튼

**관련 파일**:
- [DashboardPage.jsx](src/pages/DashboardPage.jsx)
- [StudentCard.jsx](src/components/tutor/StudentCard.jsx)

---

## 3.9 알림 (Notifications)

### 3.9.1 알림 조회

**기능 ID**: NOTIF-001
**설명**: 사용자의 알림 목록 조회

**출력**:
```json
{
  "notifications": [
    {
      "notificationId": "notif-123",
      "type": "tutor_match",
      "title": "튜터 매칭 완료",
      "message": "김튜터님과 매칭되었습니다.",
      "isRead": false,
      "createdAt": "2025-01-30T10:00:00Z"
    }
  ],
  "unreadCount": 2
}
```

**알림 유형**:
- `tutor_match`: 튜터 매칭 관련
- `feedback`: 피드백 수신
- `system`: 시스템 공지

---

### 3.9.2 알림 읽음 처리

**기능 ID**: NOTIF-002
**설명**: 알림을 읽음으로 표시

**처리 과정**:
1. Notifications 테이블 업데이트 (isRead=true)
2. 읽지 않은 알림 수 재계산

---

## 4. 화면별 기능 상세

### 4.1 로그인 화면

**화면 ID**: SCREEN-001
**접근 경로**: `/login`

**표시 요소**:
- 이메일 입력 필드
- 비밀번호 입력 필드 (마스킹)
- "로그인" 버튼
- "회원가입" 링크
- "비밀번호 찾기" 링크

**기능**:
- 로그인 성공 시 역할에 따라 리디렉션:
  - 학생: `/student/home`
  - 튜터: `/tutor/dashboard`
- 로그인 실패 시 에러 메시지 표시
- "비밀번호 보기" 토글 버튼

**관련 파일**:
- [LoginPage.jsx](src/pages/LoginPage.jsx)

---

### 4.2 회원가입 화면

**화면 ID**: SCREEN-002
**접근 경로**: `/register`

**표시 요소**:
- 이메일 입력 필드 (유효성 검증)
- 비밀번호 입력 필드 (강도 표시)
- 이름 입력 필드
- 역할 선택 (라디오 버튼: 학생/튜터)
- "인증 코드 받기" 버튼

**기능**:
- 입력값 실시간 유효성 검증
- 회원가입 후 이메일 인증 화면으로 이동
- 비밀번호 강도 표시 (약함/보통/강함)

**관련 파일**:
- [SignUpPage.jsx](src/pages/SignUpPage.jsx)

---

### 4.3 학생 메인 페이지 (홈)

**화면 ID**: SCREEN-003
**접근 경로**: `/student/home`

**표시 요소**:
- 오늘의 학습 목표 (진행률 표시)
- AI Training Modes 카드:
  - Sentence Practice (문장 연습)
  - AI Conversation (AI 대화)
- Daily Goal 원형 차트
- AI Recommendation 박스
- 사이드바 메뉴

**기능**:
- 각 모드 카드 클릭 시 해당 화면으로 이동
- 실시간 진행률 업데이트
- 튜터 피드백 알림 표시

**관련 파일**:
- [HomePage.jsx](src/pages/HomePage.jsx)

---

### 4.4 튜터 찾기 화면

**화면 ID**: SCREEN-004
**접근 경로**: `/student/tutors`

**표시 요소**:
- 검색 입력 필드 (튜터 이름 또는 전문분야 검색)
- 필터 탭 (전체/법무/문법/회화)
- 튜터 카드 목록:
  - 프로필 이미지
  - 이름
  - 상태 (거부됨/등록됨/모집 중)
  - 소개 문구
  - 학생 수 (예: 학생: 1/10명)
  - "등록 요청" 또는 "거부됨" 버튼

**기능**:
- 튜터 검색 (실시간 필터링)
- 튜터 카드 클릭 시 상세 정보 모달 표시
- "등록 요청" 클릭 시 요청 메시지 입력 모달

**관련 파일**:
- [TutorSearchDialog.jsx](src/components/student/TutorSearchDialog.jsx)
- [TutorRequestDialog.jsx](src/components/student/TutorRequestDialog.jsx)

---

### 4.5 문장 연습 화면

**화면 ID**: SCREEN-005
**접근 경로**: `/student/practice`

**표시 요소**:
- Daily Progress 진행률 바
- Target Sentence 표시:
  - 영어 문장 (큰 글씨)
  - 한글 번역 (작은 글씨)
  - 문장 인덱스 (예: 문장 1 / 5, 난이도: 중)
- Your Speech Feedback:
  - 인식된 텍스트
  - 페이스 비율 (색상 표시)
- 음성 재생 버튼 (스피커 아이콘)
- 녹음 버튼 (마이크 아이콘, Ready/Recording 상태)
- "다음 문장" 버튼
- Live Preview (학생 영상, 선택적)

**기능**:
1. 음성 재생: TTS 음성 재생
2. 녹음 시작/종료: 마이크 버튼 클릭
3. 발음 평가: Whisper STT + 페이스 비율 계산
4. 피드백 표시: 페이스 비율, AI 피드백
5. 다음 문장 이동

**관련 파일**:
- [PracticePage.jsx](src/pages/PracticePage.jsx)

---

### 4.6 AI 대화 화면

**화면 ID**: SCREEN-006
**접근 경로**: `/student/ai-chat`

**표시 요소**:
- 대화 상태 표시 (Live Session / 종료 메시지)
- 대화 목록 (채팅 UI):
  - AI 메시지 (왼쪽 정렬, 파란색 배경)
  - 사용자 메시지 (오른쪽 정렬, 회색 배경)
- 번역 토글 (한글 번역 표시/숨김)
- Fluency 진행률 바
- 음성 입력 버튼
- "END SESSION" 버튼
- Live Preview (학생 영상, 선택적)

**기능**:
1. AI 메시지 자동 재생 (TTS)
2. 음성 입력 (Whisper STT)
3. 실시간 대화 진행
4. 턴 수 제한 (10턴)
5. 응답 품질 측정 (WPM, Fluency, Overall)
6. 세션 종료 시 학습 통계 업데이트

**관련 파일**:
- [ChatPage.jsx](src/pages/ChatPage.jsx)

---

### 4.7 학습 통계 화면

**화면 ID**: SCREEN-007
**접근 경로**: `/student/statistics`

**표시 요소**:
- 날짜 필터 (오늘/1주/2주/전체)
- 통계 카드 (KPI):
  - 총 발화 시간
  - 순 발화 밀도
  - 평균 페이스 비율
  - 평균 응답 품질
- 주간 학습 시간 그래프 (선 그래프)
- 활동 분포 차트 (파이 차트)
- 최근 세션 목록

**기능**:
- 날짜 필터 변경 시 그래프 업데이트
- 세션 클릭 시 상세 내역 모달 표시

**관련 파일**:
- [StatsPage.jsx](src/pages/StatsPage.jsx)
- [KPICards.jsx](src/components/student/stats/KPICards.jsx)
- [WeeklySummary.jsx](src/components/student/stats/WeeklySummary.jsx)
- [LearningTrendChart.jsx](src/components/student/stats/LearningTrendChart.jsx)

---

### 4.8 프로필 설정 화면

**화면 ID**: SCREEN-008
**접근 경로**: `/student/profile` 또는 `/tutor/profile`

**표시 요소**:
- 프로필 사진 (변경 버튼)
- 이름 입력 필드
- 이메일 (읽기 전용)
- 역할 (읽기 전용, 예: 학생)
- 튜터의 경우 추가 필드:
  - 학생 수락 여부 (토글)
  - 최대 학생 수 (숫자 입력)
- "저장" 버튼

**기능**:
- 프로필 사진 업로드 (Presigned URL, 최대 5MB)
- 이름 수정
- 저장 시 API 호출 및 성공 메시지

**관련 파일**:
- [ProfilePage.jsx](src/pages/ProfilePage.jsx)
- [TutorProfilePage.jsx](src/pages/TutorProfilePage.jsx)
- [ProfileImageSection.jsx](src/components/profile/ProfileImageSection.jsx)

---

### 4.9 튜터 대시보드

**화면 ID**: SCREEN-009
**접근 경로**: `/tutor/dashboard`

**표시 요소**:
- 오늘의 학생 현황
- 담당 학생 목록:
  - 프로필 이미지
  - 이름
  - 상태 (학습 중/오프라인)
  - 현재 위치 (room)
  - 마지막 업데이트 시간
- 학생 레벨 목록:
  - 이름
  - 현재 레벨 (初/중/상)
  - 평균 정확도
- "피드백 전송" 버튼

**기능**:
- 실시간 학생 상태 업데이트 (WebSocket)
- 학생 클릭 시 상세 이력 모달
- 피드백 전송 모달 (학생 선택 + 메시지 입력)

**관련 파일**:
- [DashboardPage.jsx](src/pages/DashboardPage.jsx)
- [StudentCard.jsx](src/components/tutor/StudentCard.jsx)

---

## 5. 공통 기능 및 기술 구현

### 5.1 실시간 음성 처리

#### 5.1.1 STT (Speech-to-Text) - Whisper

**기능 설명**: Whisper 모델 기반 로컬 STT

**Whisper 모델 로딩**:
- 앱 시작 시 Whisper 모델 사전 로드 (Whisper 모델 사전 로딩)
- Web Worker로 백그라운드 로딩
- Redux로 로딩 상태 관리 (whisperPreloadSlice)
- 진척도 표시 (0-100%)

**오디오 녹음**:
- MediaRecorder API 사용
- WebM/Opus 또는 MP4 형식
- 실시간 오디오 레벨 시각화

**오디오 전처리**:
- AudioContext로 PCM 디코딩
- 16kHz 리샘플링
- DC 오프셋 제거
- 피크 진폭 정규화
- 무음 자동 트리밍
- 직렬화된 디코딩 큐 (동시 디코딩 방지)

**STT 변환**:
- Whisper Worker에 오디오 전송
- 실시간 변환 진행률 표시
- 변환된 텍스트 반환

**관련 파일**:
- [useWhisperSTT.js](src/hooks/useWhisperSTT.js)
- [whisperPreloadSlice.js](src/store/whisperPreloadSlice.js)
- [postRecordingPipeline.js](src/utils/postRecordingPipeline.js)

---

#### 5.1.2 TTS (Text-to-Speech) - AWS Polly

**기능 설명**: AWS Polly 기반 TTS

**TTS 요청**:
- 텍스트 + Voice ID 전송
- 캐시 히트 시 즉시 URL 반환 (200)
- 캐시 미스 시 비동기 처리 (202)

**비동기 폴링**:
- `jobId`로 상태 폴링
- 지수 백오프 (1초 → 3초)
- 최대 30-60회 시도

**오디오 재생**:
- CloudFront URL로 오디오 로드
- HTML5 Audio 요소로 재생
- 재생 완료 콜백

**TTS 캐싱**:
- Backend DynamoDB 캐싱
- 동일 문장 재사용 (비용 절감)

**관련 파일**:
- [useTTSAudio.js](src/hooks/useTTSAudio.js)
- [ttsApi.js](src/api/ttsApi.js)

---

### 5.2 실시간 통신 (WebSocket)

**기능 설명**: 튜터-학생 간 실시간 알림 및 상태 업데이트

**연결 관리**:
- 로그인 시 WebSocket 연결
- 사용자 이메일 기반 채널
- 자동 재연결 (5초 백오프)

**메시지 타입**:
- `TUTOR_REQUEST_APPROVED` - 튜터 요청 승인
- `TUTOR_REQUEST_REJECTED` - 튜터 요청 거절
- `STUDENT_STATUS_UPDATE` - 학생 상태 변경
- `NEW_FEEDBACK` - 새 피드백 수신

**상태 동기화**:
- 학생: 온라인/오프라인/연습중/채팅중
- 튜터 대시보드 실시간 업데이트

**오프라인 감지**:
- `navigator.onLine` 체크
- 페이지 닫힘 감지 (Beacon API)
- 자동 오프라인 상태 전송

**관련 파일**:
- [webSocketConfig.js](src/config/webSocketConfig.js)
- [useWebSocket.js](src/hooks/webSocket/useWebSocket.js)
- [useStudentNotifications.js](src/hooks/student/useStudentNotifications.js)
- [useTutorNotifications.js](src/hooks/tutor/useTutorNotifications.js)

---

### 5.3 통계 데이터 관리

**기능 설명**: 클라이언트-서버 간 통계 데이터 동기화

**로컬 누적**:
- Redux Store에 실시간 업데이트
- 세션 중 메모리 누적
- 평균 계산 (러닝 평균)

**localStorage 캐싱**:
- 날짜별 키 패턴
- 오늘의 통계 캐싱
- 앱 재시작 시 복원

**백엔드 동기화**:
- 세션 종료 시 업로드
- 페이로드 해시 생성 (djb2)
- 중복 업로드 방지

**데이터 매핑**:
- Redux ↔ API 형식 변환
- 밀리초 ↔ 초 변환
- 퍼센트 ↔ 소수 변환

**데이터 흐름**:
```
1. 실시간 활동 → Redux 업데이트
2. Redux → localStorage 저장 (실시간)
3. 세션 종료 → 해시 계산
4. 중복 체크 (localStorage에서 마지막 해시 비교)
5. 새 데이터만 백엔드 전송
6. 동기화 정보 localStorage 저장
```

**관련 파일**:
- [speakingStatsSlice.js](src/store/speakingStatsSlice.js)
- [statsSync.js](src/utils/statsSync.js)
- [statisticsApi.js](src/api/statisticsApi.js)
- [speakingStatsSelectors.js](src/store/speakingStatsSelectors.js)

---

### 5.4 카메라 프리뷰 (MediaPipe)

**기능 설명**: 연습 중 사용자 포즈 감지 및 시각화 (선택적 기능)

**카메라 스트림**:
- getUserMedia로 웹캠 접근
- 비디오 스트림 캡처

**포즈 감지**:
- MediaPipe Pose 모델
- 실시간 랜드마크 감지
- Canvas에 시각화

**플로팅 프리뷰**:
- 드래그 가능한 프리뷰 창
- 최소화/최대화
- 화면 모서리에 고정

**관련 파일**:
- [FloatingCameraPreview.jsx](src/components/common/FloatingCameraPreview.jsx)
- [useMediaPipe.js](src/hooks/conversation/useMediaPipe.js)

---

### 5.5 난이도 및 주제 매핑

**난이도 매핑**:
| UI (한글) | API (영어) | 설명 |
|-----------|------------|------|
| 下 | easy | 초급 (8단어 이하) |
| 중 | medium | 중급 (15단어 이하) |
| 上 | hard | 고급 (25단어 이하) |

**주제 목록**:
- `small_talk` - 일상 대화
- `restaurant` - 레스토랑
- `airport` - 공항
- `shopping` - 쇼핑
- `hotel` - 호텔
- `doctor` - 병원/의사
- `job_interview` - 면접
- `directions` - 길찾기

**관련 파일**:
- [apiMappers.js](src/utils/apiMappers.js)

---

### 5.6 에러 처리

**401 Unauthorized**:
- 자동 토큰 갱신
- 원래 요청 재시도
- 갱신 실패 시 로그아웃

**네트워크 에러**:
- 재시도 로직 (최대 3회)
- 사용자 친화적 에러 메시지

**타임아웃**:
- 20초 타임아웃 (Lambda 콜드 스타트 고려)
- 타임아웃 에러 메시지

**관련 파일**:
- [api.js](src/api/api.js)
- [authSlice.js](src/store/authSlice.js)

---

## 6. 비기능 요구사항

### 6.1 성능

| 항목 | 목표 | 측정 방법 |
|------|------|----------|
| API 응답 시간 | 평균 500ms 이하 | CloudWatch Metrics |
| TTS 생성 시간 | 평균 3초 이하 | SQS 처리 시간 측정 |
| STT 처리 시간 | 평균 5초 이하 (로컬) | Whisper Worker 처리 시간 |
| AI 응답 생성 | 평균 3-5초 | Claude API 응답 시간 |
| WebSocket 지연 | 100ms 이하 | API Gateway 메트릭 |
| 동시 접속자 | 최대 200명 | Lambda 동시성 설정 |

**성능 최적화**:
1. **Whisper 모델 사전 로딩**: 앱 시작 시 백그라운드 로딩, 첫 STT 지연 제거
2. **오디오 디코딩 큐**: 직렬화된 디코딩, AudioContext 재사용 방지
3. **TTS 캐싱**: Backend DynamoDB 캐싱, 동일 문장 재사용

---

### 6.2 확장성

- **Lambda 오토 스케일링**: 자동으로 인스턴스 생성/삭제
- **DynamoDB On-Demand**: 자동으로 읽기/쓰기 용량 조정
- **SQS**: 메시지 큐로 부하 분산
- **CloudFront CDN**: 전 세계 사용자에게 빠른 응답

---

### 6.3 보안

| 항목 | 구현 방법 |
|------|----------|
| 인증 | AWS Cognito JWT 토큰 (idToken, accessToken, refreshToken) |
| 권한 관리 | API Gateway Authorizer, ProtectedRoute (RBAC) |
| 데이터 암호화 | HTTPS (TLS 1.2+) |
| 비밀번호 저장 | Cognito 자동 해싱 (bcrypt) |
| API 키 관리 | AWS Secrets Manager |
| CORS | API Gateway CORS 설정 |
| S3 업로드 | Presigned URL (15분 만료, PUT 권한만) |

**역할 기반 접근 제어 (RBAC)**:
- `ProtectedRoute` 컴포넌트
- `student` 역할: 학생 페이지만 접근
- `tutor` 역할: 튜터 페이지만 접근

**관련 파일**:
- [ProtectedRoute.jsx](src/components/common/ProtectedRoute.jsx)

---

### 6.4 가용성

- **Lambda**: 99.95% SLA (AWS 보장)
- **DynamoDB**: 99.99% SLA
- **S3**: 99.99% SLA
- **CloudFront**: 99.9% SLA

---

### 6.5 모니터링 및 로깅

- **CloudWatch Logs**: 모든 Lambda 로그 저장 (30일 보관)
- **CloudWatch Metrics**: 주요 메트릭 수집 (Invocations, Errors, Duration)
- **CloudWatch Alarms**: DLQ 메시지 발생 시 알림

---

### 6.6 사용자 경험 (UX)

**반응형 디자인**:
- Tailwind CSS 반응형 클래스
- Material-UI 브레이크포인트
- 모바일 우선 디자인

**로딩 상태 표시**:
- Circular Progress (MUI)
- Skeleton Loader
- 진척도 바

**에... (4KB 남음)