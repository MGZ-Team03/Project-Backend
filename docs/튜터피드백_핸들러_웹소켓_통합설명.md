# TutorFeedbackHandler와 WebSocket 통합 상세 설명

## 📊 시스템 아키텍처

```
[학생 브라우저] <--WebSocket--> [API Gateway WebSocket] <--> [SentencesWebSocketFunction]
                                                                        ↓
                                                              [CONNECTIONS_TABLE]
                                                              {
                                                                connection_id: "abc123",
                                                                user_email: "student@example.com"
                                                              }

[튜터 브라우저] --REST API--> [API Gateway REST] <--> [TutorFeedbackHandler]
                                                              ↓
                                                    [CONNECTIONS_TABLE 조회]
                                                              ↓
                                                    [API Gateway Management API]
                                                              ↓
                                                    [학생 WebSocket으로 전송]
```

---

## 🔄 상세 플로우

### Step 1: 학생이 WebSocket 연결

#### 프론트엔드 (학생)
```javascript
// 학생이 WebSocket 연결 시 user_email을 쿼리 파라미터로 전달
const ws = new WebSocket(
  'wss://abc123.execute-api.ap-northeast-2.amazonaws.com/production?user_email=student@example.com'
);

ws.onopen = () => {
  console.log('WebSocket 연결됨');
};

ws.onmessage = (event) => {
  const data = JSON.parse(event.data);
  console.log('받은 메시지:', data);
  
  // 튜터 피드백인 경우
  if (data.type === 'feedback') {
    displayFeedback(data.message, data.from);
  }
};
```

#### 백엔드 (SentencesWebSocketFunction)
```java
// handleConnect() 메서드
private APIGatewayV2WebSocketResponse handleConnect(...) {
    String connectionId = "abc123";  // API Gateway가 자동 생성
    String userEmail = "student@example.com";  // 쿼리 파라미터에서 추출
    
    // DynamoDB에 저장
    saveConnection(connectionId, userEmail, tutorEmail, context);
    // ↓
    // CONNECTIONS_TABLE:
    // {
    //   "connection_id": "abc123",
    //   "user_email": "student@example.com",
    //   "connected_at": "2026-01-14T10:00:00Z",
    //   "ttl": 1737885600
    // }
}
```

#### DynamoDB CONNECTIONS_TABLE 상태
```json
{
  "connection_id": "abc123",
  "user_email": "student@example.com",
  "tutor_email": "tutor@example.com",
  "connected_at": "2026-01-14T10:00:00Z",
  "ttl": 1737885600
}
```

---

### Step 2: 튜터가 피드백 전송 (REST API)

#### 프론트엔드 (튜터)
```javascript
// 튜터가 피드백 전송 버튼 클릭
async function sendFeedback(studentEmail, message) {
  const response = await fetch('https://api.example.com/api/tutor/feedback', {
    method: 'POST',
    headers: { 
      'Content-Type': 'application/json',
      'Authorization': 'Bearer <token>'
    },
    body: JSON.stringify({
      tutor_email: 'tutor@example.com',
      student_email: 'student@example.com',
      message: '발음이 좋아졌어요!',
      message_type: 'text'
    })
  });
  
  const result = await response.json();
  console.log('피드백 전송 완료:', result);
  // {
  //   "success": true,
  //   "message_id": "tutor@example.com#student@example.com#default#2026-01-14T10:30:00Z",
  //   "timestamp": "2026-01-14T10:30:00Z",
  //   "websocket_sent": true
  // }
}
```

#### 백엔드 (TutorFeedbackHandler)

```java
// 1. POST /api/tutor/feedback 요청 수신
public APIGatewayProxyResponseEvent handleRequest(...) {
    return handlePostFeedback(input, context);
}

// 2. 피드백 처리
private APIGatewayProxyResponseEvent handlePostFeedback(...) {
    Map<String, Object> requestBody = {
        "tutor_email": "tutor@example.com",
        "student_email": "student@example.com",
        "message": "발음이 좋아졌어요!",
        "message_type": "text"
    };
    
    Map<String, Object> result = processFeedback(requestBody, context);
    return createResponse(200, gson.toJson(result));
}

// 3. 메인 처리 로직
private Map<String, Object> processFeedback(...) {
    // 3-1. DynamoDB FEEDBACK_TABLE에 저장
    String timestamp = saveFeedbackToDB(...);
    
    // 3-2. WebSocket으로 학생에게 전송 (핵심!)
    boolean sent = sendToStudentViaWebSocket(
        studentEmail: "student@example.com",
        tutorEmail: "tutor@example.com",
        messageText: "발음이 좋아졌어요!",
        messageType: "text",
        audioUrl: null,
        timestamp: "2026-01-14T10:30:00Z",
        context
    );
    
    return { 
        success: true, 
        websocket_sent: sent,
        message_id: "...",
        timestamp: "..."
    };
}
```

---

### Step 3: 학생 연결 ID 조회 (핵심!)

```java
// TutorFeedbackHandler의 sendToStudentViaWebSocket() 메서드
private boolean sendToStudentViaWebSocket(...) {
    // 1️⃣ CONNECTIONS_TABLE에서 학생의 connection_id 찾기
    String connectionId = getStudentConnectionId("student@example.com", context);
    
    if (connectionId == null) {
        // 학생이 오프라인 (WebSocket 연결 안 됨)
        context.getLogger().log("⚠️ Student is offline");
        return false;  // 피드백은 DB에 저장됨, WebSocket은 실패
    }
    
    // connectionId = "abc123" 찾음!
    
    // 2️⃣ 피드백 메시지 생성
    Map<String, Object> feedbackMessage = {
        "type": "feedback",
        "from": "tutor@example.com",
        "message": "발음이 좋아졌어요!",
        "messageType": "text",
        "timestamp": "2026-01-14T10:30:00Z"
    };
    
    // 3️⃣ API Gateway Management API로 WebSocket에 전송
    ApiGatewayManagementApiClient apiClient = ApiGatewayManagementApiClient.builder()
        .endpointOverride(URI.create(WEBSOCKET_ENDPOINT))
        // "https://abc123.execute-api.ap-northeast-2.amazonaws.com/production"
        .build();
    
    PostToConnectionRequest request = PostToConnectionRequest.builder()
        .connectionId("abc123")  // ← 여기가 핵심!
        .data(SdkBytes.fromUtf8String(gson.toJson(feedbackMessage)))
        .build();
    
    apiClient.postToConnection(request);  // 학생에게 실시간 전송!
    
    return true;
}
```

#### getStudentConnectionId() 상세
```java
private String getStudentConnectionId(String studentEmail, Context context) {
    // DynamoDB CONNECTIONS_TABLE에서 GSI 사용해 조회
    QueryRequest request = QueryRequest.builder()
        .tableName(CONNECTIONS_TABLE)  // "Project02-SpeakTracker-websocket-connections"
        .indexName("user_email-index")  // ← GSI 사용 (빠른 검색)
        .keyConditionExpression("user_email = :email")
        .expressionAttributeValues({
            ":email": "student@example.com"
        })
        .limit(1)
        .build();
    
    QueryResponse response = dynamoDbClient.query(request);
    
    // 결과:
    // [
    //   {
    //     "connection_id": "abc123",
    //     "user_email": "student@example.com",
    //     "connected_at": "..."
    //   }
    // ]
    
    if (response.items().isEmpty()) {
        return null;  // 학생 오프라인
    }
    
    return response.items().get(0).get("connection_id").s();  // "abc123"
}
```

---

### Step 4: 학생이 실시간으로 피드백 수신

#### 학생 브라우저
```javascript
// WebSocket onmessage 이벤트 자동 발생
ws.onmessage = (event) => {
  const data = JSON.parse(event.data);
  
  console.log(data);
  // {
  //   "type": "feedback",
  //   "from": "tutor@example.com",
  //   "message": "발음이 좋아졌어요!",
  //   "messageType": "text",
  //   "timestamp": "2026-01-14T10:30:00Z"
  // }
  
  if (data.type === 'feedback') {
    // UI에 피드백 표시
    const feedbackDiv = document.createElement('div');
    feedbackDiv.innerHTML = `
      <div class="tutor-feedback">
        <strong>튜터:</strong> ${data.message}
        <small>${data.timestamp}</small>
      </div>
    `;
    document.getElementById('feedback-list').appendChild(feedbackDiv);
    
    // 알림 표시
    showNotification('새로운 튜터 피드백이 도착했습니다!');
  }
};
```

---

## 🔑 핵심 포인트

### 1. CONNECTIONS_TABLE이 중개 역할
- WebSocket의 `connection_id`는 API Gateway가 관리 (우리가 직접 알 수 없음)
- `user_email`로 검색하여 `connection_id`를 찾아야 함
- **GSI (Global Secondary Index)**를 사용해 빠르게 검색

### 2. 두 개의 별도 Lambda 함수
- **SentencesWebSocketFunction**: WebSocket 연결 관리 ($connect, $disconnect, $default)
- **TutorFeedbackHandler**: REST API로 피드백 받아서 WebSocket으로 전송

### 3. API Gateway Management API
- REST API Lambda에서 WebSocket에 메시지를 보낼 수 있게 해주는 AWS API
- `postToConnection()` 메서드 사용
- WebSocket 엔드포인트 URL 필요: `https://{api-id}.execute-api.{region}.amazonaws.com/{stage}`

### 4. 오프라인 처리
- 학생이 WebSocket 연결 안 했으면 → `websocket_sent: false`
- 하지만 피드백은 **FEEDBACK_TABLE에 저장됨** (영구 기록)
- 나중에 학생이 접속하면 **GET API로 조회 가능**

---

## 📝 전체 데이터 흐름

```
1. 학생 WebSocket 연결
   └→ connection_id: "abc123" 생성 (API Gateway 자동)
      └→ CONNECTIONS_TABLE 저장: {"abc123" → "student@example.com"}

2. 튜터 피드백 전송 (REST)
   └→ POST /api/tutor/feedback
      └→ TutorFeedbackHandler 실행
         ├→ FEEDBACK_TABLE에 저장 (영구 기록)
         └→ CONNECTIONS_TABLE 조회
            └→ "student@example.com" → "abc123" 찾기
               └→ API Gateway Management API
                  └→ connection "abc123"로 메시지 전송
                     └→ 학생 브라우저 ws.onmessage 발생! ✨

3. 학생 WebSocket 해제
   └→ CONNECTIONS_TABLE에서 삭제
```

---

## 🗄️ DynamoDB 테이블 구조

### CONNECTIONS_TABLE (WebSocketConnectionsTable)
| 속성 | 타입 | 키 | 설명 |
|------|------|-----|------|
| connection_id | String | **PK (HASH)** | API Gateway가 생성한 연결 ID |
| user_email | String | **GSI** | 사용자 이메일 (학생/튜터) |
| tutor_email | String | **GSI** | 튜터 이메일 (선택) |
| connected_at | String | - | 연결 시각 (ISO 8601) |
| ttl | Number | - | TTL (24시간 후 자동 삭제) |

### FEEDBACK_TABLE (FeedbackMessagesTable)
| 속성 | 타입 | 키 | 설명 |
|------|------|-----|------|
| composite_key | String | **PK (HASH)** | `${tutor_email}#${student_email}#${session_id}` |
| timestamp | String | **SK (RANGE)** | 메시지 발송 시각 (ISO 8601) |
| student_email | String | **GSI** | 학생 이메일 (조회용) |
| tutor_email | String | - | 튜터 이메일 |
| message_text | String | - | 피드백 메시지 텍스트 |
| message_type | String | - | 메시지 타입 (text / tts) |
| audio_url | String | - | TTS 오디오 S3 URL (선택) |
| ttl | Number | - | TTL (30일 후 자동 삭제) |

---

## 🧪 테스트 시나리오

### 1. 학생 온라인 + 튜터 피드백 전송
```bash
# 1. 학생 WebSocket 연결 (wscat 사용)
wscat -c "wss://your-api.execute-api.ap-northeast-2.amazonaws.com/production?user_email=student@example.com"

# 2. 튜터 피드백 전송
curl -X POST https://your-api/api/tutor/feedback \
  -H "Content-Type: application/json" \
  -d '{
    "tutor_email": "tutor@example.com",
    "student_email": "student@example.com",
    "message": "발음이 좋아졌어요!",
    "message_type": "text"
  }'

# 3. 학생 WebSocket에서 메시지 수신 확인
# {"type":"feedback","from":"tutor@example.com","message":"발음이 좋아졌어요!",...}
```

### 2. 학생 오프라인 + 튜터 피드백 전송
```bash
# 1. 학생 WebSocket 연결 안 됨

# 2. 튜터 피드백 전송
curl -X POST https://your-api/api/tutor/feedback \
  -H "Content-Type: application/json" \
  -d '{...}'

# 응답:
# {
#   "success": true,
#   "websocket_sent": false,  ← 오프라인
#   "message_id": "...",
#   "timestamp": "..."
# }

# 3. 나중에 학생이 접속 후 히스토리 조회
curl "https://your-api/api/tutor/feedback?student_email=student@example.com&limit=10"

# 응답:
# {
#   "messages": [
#     {
#       "composite_key": "tutor@example.com#student@example.com#default",
#       "timestamp": "2026-01-14T10:30:00Z",
#       "message_text": "발음이 좋아졌어요!",
#       "message_type": "text"
#     }
#   ],
#   "count": 1
# }
```

---

## ⚠️ 주의사항

### 1. WebSocket 엔드포인트 환경 변수 설정
```yaml
# template.yaml
TutorFunction:
  Properties:
    Environment:
      Variables:
        WEBSOCKET_ENDPOINT: !Sub 'https://${WebSocketApi}.execute-api.${AWS::Region}.amazonaws.com/${WebSocketApiStage}'
```

### 2. IAM 권한 설정
TutorFunction이 API Gateway Management API를 호출하려면 권한 필요:
```yaml
# CommonLambdaRole에 추가
Policies:
  - PolicyName: WebSocketManageConnections
    PolicyDocument:
      Version: '2012-10-17'
      Statement:
        - Effect: Allow
          Action:
            - 'execute-api:ManageConnections'
          Resource:
            - !Sub 'arn:aws:execute-api:${AWS::Region}:${AWS::AccountId}:${WebSocketApi}/*'
```

### 3. CORS 설정
프론트엔드에서 REST API 호출 시 CORS 필요:
```java
private APIGatewayProxyResponseEvent createResponse(int statusCode, String body) {
    Map<String, String> headers = new HashMap<>();
    headers.put("Content-Type", "application/json");
    headers.put("Access-Control-Allow-Origin", "*");
    headers.put("Access-Control-Allow-Headers", "Content-Type,Authorization");
    headers.put("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
    
    return new APIGatewayProxyResponseEvent()
            .withStatusCode(statusCode)
            .withHeaders(headers)
            .withBody(body);
}
```

---

## 🚀 다음 단계

1. ✅ WebSocket 연결 관리 구현
2. ✅ 피드백 전송 및 실시간 전달 구현
3. ⏳ AWS Polly TTS 통합
4. ⏳ 프론트엔드 UI 구현
5. ⏳ 통합 테스트
6. ⏳ 에러 처리 및 재시도 로직 강화

---

## 📚 참고 자료

- [AWS API Gateway WebSocket](https://docs.aws.amazon.com/apigateway/latest/developerguide/apigateway-websocket-api.html)
- [API Gateway Management API](https://docs.aws.amazon.com/apigatewaymanagementapi/latest/api/Welcome.html)
- [DynamoDB Global Secondary Indexes](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/GSI.html)
