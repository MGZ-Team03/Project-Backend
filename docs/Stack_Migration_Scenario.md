# CloudFormation 스택 마이그레이션 시나리오

## 목표
- 기존 테이블 데이터 보존
- 최종적으로 스택 이름 `Project02-SpeakTracker` 유지
- 테이블 이름 `Project02-SpeakTracker-users` 등으로 복원

## 전체 프로세스 개요
```
현재 상황 → v2 생성 → 기존 리소스 정리 → v2 삭제 → 원래 이름 재생성
```

---

## Phase 1: v2 스택 생성

### 1-1. samconfig.toml 수정
```bash
cd /home/user1/mgz-team03/Project-Backend
```

**파일**: `samconfig.toml`
```toml
[default.global.parameters]
stack_name = "Project02-SpeakTracker-v2"  # v2로 변경
region = "ap-northeast-2"
```

### 1-2. v2 스택 배포
```bash
sam build
sam deploy
```

**예상 소요시간**: 5-10분

**생성되는 리소스**:
- CloudFormation 스택: `Project02-SpeakTracker-v2`
- UserPool: `Project02-SpeakTracker-v2-userpool`
- DynamoDB 테이블 (7개):
  - `Project02-SpeakTracker-v2-users`
  - `Project02-SpeakTracker-v2-tutor-students`
  - `Project02-SpeakTracker-v2-learning-sessions`
  - `Project02-SpeakTracker-v2-daily-statistics`
  - `Project02-SpeakTracker-v2-ai-conversations`
  - `Project02-SpeakTracker-v2-feedback-messages`
  - `Project02-SpeakTracker-v2-websocket-connections`

### 1-3. 배포 확인
```bash
# 스택 상태 확인
aws cloudformation describe-stacks \
  --stack-name Project02-SpeakTracker-v2 \
  --region ap-northeast-2 \
  --query 'Stacks[0].StackStatus' \
  --output text
```
**예상 결과**: `CREATE_COMPLETE`

```bash
# 테이블 확인
aws dynamodb list-tables \
  --region ap-northeast-2 \
  --query 'TableNames[?contains(@, `v2`)]' \
  --output json
```

---

## Phase 2: 데이터 마이그레이션 (선택사항)

### 2-1. 기존 테이블에서 v2 테이블로 데이터 복사

**⚠️ 주의**: 데이터가 중요한 경우만 수행

```bash
# 각 테이블별로 스캔 → 복사 스크립트 작성 필요
# 예시: users 테이블
aws dynamodb scan \
  --table-name Project02-SpeakTracker-users \
  --region ap-northeast-2 \
  --output json > users_backup.json

# Python 스크립트로 batch-write-item 실행
# (별도 스크립트 필요)
```

**또는**: DynamoDB Export/Import 기능 사용
```bash
# Export to S3 → Import from S3
# AWS 콘솔 또는 CLI 사용
```

**예상 소요시간**: 10-30분 (데이터 양에 따라)

---

## Phase 3: 기존 리소스 정리

### 3-1. 기존 v1 테이블 삭제
```bash
# 7개 테이블 일괄 삭제
for table in users tutor-students learning-sessions daily-statistics ai-conversations feedback-messages websocket-connections; do
  aws dynamodb delete-table \
    --table-name "Project02-SpeakTracker-$table" \
    --region ap-northeast-2
  echo "✓ Deleted: Project02-SpeakTracker-$table"
done
```

**예상 소요시간**: 1-2분

### 3-2. 삭제 확인
```bash
# 삭제 완료 대기
sleep 60

# 확인
for table in users tutor-students learning-sessions daily-statistics ai-conversations feedback-messages websocket-connections; do
  aws dynamodb describe-table \
    --table-name "Project02-SpeakTracker-$table" \
    --region ap-northeast-2 2>&1 | grep -q "ResourceNotFoundException" \
    && echo "✓ DELETED: $table" \
    || echo "⚠ STILL EXISTS: $table"
done
```

### 3-3. 기존 UserPool 확인 (이미 삭제됨)
```bash
aws cognito-idp list-user-pools \
  --max-results 20 \
  --region ap-northeast-2 \
  --query 'UserPools[?Name==`Project02-SpeakTracker-userpool`]'
```
**예상 결과**: `[]` (비어있음, 이미 삭제됨)

---

## Phase 4: v2 스택 삭제 준비

### 4-1. template.yaml 수정 - DeletionPolicy 제거

**⚠️ 중요**: DeletionPolicy를 제거해야 스택 삭제 시 v2 테이블도 함께 삭제됨

**파일**: `template.yaml`

**변경 전**:
```yaml
UsersTable:
  Type: AWS::DynamoDB::Table
  DeletionPolicy: Retain  # ← 이 줄 삭제
  Properties:
    TableName: !Sub '${AWS::StackName}-users'
```

**변경 후**:
```yaml
UsersTable:
  Type: AWS::DynamoDB::Table
  # DeletionPolicy 제거됨
  Properties:
    TableName: !Sub '${AWS::StackName}-users'
```

**모든 테이블에서 제거** (8개):
- `UsersTable`
- `TutorStudentsTable`
- `LearningSessionsTable`
- `DailyStatisticsTable`
- `AIConversationsTable`
- `FeedbackMessagesTable`
- `WebSocketConnectionsTable`
 

**UserPool, UserPoolClient에서도 제거**

### 4-2. 변경사항 커밋 (선택사항)
```bash
git add template.yaml
git commit -m "Remove DeletionPolicy for clean stack deletion"
```

---

## Phase 5: v2 스택 삭제

### 5-1. v2 스택 삭제
```bash
aws cloudformation delete-stack \
  --stack-name Project02-SpeakTracker-v2 \
  --region ap-northeast-2

echo "스택 삭제 시작..."
```

### 5-2. 삭제 완료 대기
```bash
aws cloudformation wait stack-delete-complete \
  --stack-name Project02-SpeakTracker-v2 \
  --region ap-northeast-2

echo "✓ 스택 삭제 완료!"
```

**예상 소요시간**: 3-5분

### 5-3. 삭제 확인
```bash
# 스택 상태 확인
aws cloudformation describe-stacks \
  --stack-name Project02-SpeakTracker-v2 \
  --region ap-northeast-2 2>&1 \
  | grep -q "does not exist" \
  && echo "✓ 스택 완전 삭제됨" \
  || echo "⚠ 스택 아직 존재"

# v2 테이블 삭제 확인
aws dynamodb list-tables \
  --region ap-northeast-2 \
  --query 'TableNames[?contains(@, `v2`)]' \
  --output json
```
**예상 결과**: `[]` (비어있어야 함)

---

## Phase 6: 원래 이름으로 재생성

### 6-1. samconfig.toml 원복
```bash
cd /home/user1/mgz-team03/Project-Backend
```

**파일**: `samconfig.toml`
```toml
[default.global.parameters]
stack_name = "Project02-SpeakTracker"  # v2 제거
region = "ap-northeast-2"
```

### 6-2. DeletionPolicy 다시 추가 (선택사항)

프로덕션 환경에서는 데이터 보호를 위해 다시 추가 권장:

**파일**: `template.yaml`
```yaml
UsersTable:
  Type: AWS::DynamoDB::Table
  DeletionPolicy: Retain  # 다시 추가
  Properties:
    TableName: !Sub '${AWS::StackName}-users'
```

### 6-3. 최종 배포
```bash
sam build
sam deploy
```

**예상 소요시간**: 5-10분

**생성되는 리소스**:
- CloudFormation 스택: `Project02-SpeakTracker`
- UserPool: `Project02-SpeakTracker-userpool`
- DynamoDB 테이블: `Project02-SpeakTracker-users` 등

### 6-4. 최종 확인
```bash
# 스택 확인
aws cloudformation describe-stacks \
  --stack-name Project02-SpeakTracker \
  --region ap-northeast-2 \
  --query 'Stacks[0].[StackName,StackStatus]' \
  --output table

# 테이블 확인
aws dynamodb list-tables \
  --region ap-northeast-2 \
  --query 'TableNames[?contains(@, `Project02-SpeakTracker`)]' \
  --output json

# UserPool 확인
aws cognito-idp list-user-pools \
  --max-results 20 \
  --region ap-northeast-2 \
  --query 'UserPools[?contains(Name, `Project02-SpeakTracker`)].[Name,Id]' \
  --output table
```

---

## Phase 7: 데이터 복원 (Phase 2 수행한 경우)

### 7-1. v2 백업에서 원래 테이블로 복사

만약 Phase 2에서 데이터를 백업했다면:

```bash
# v2 테이블 데이터 백업 (v2 삭제 전에 수행했어야 함)
# 백업본을 원래 테이블로 복원
```

**⚠️ 주의**: v2 스택을 삭제하기 전에 데이터 백업 필수!

---

## 전체 소요시간 예상

| Phase | 작업 내용 | 소요시간 |
|-------|----------|----------|
| Phase 1 | v2 스택 생성 | 5-10분 |
| Phase 2 | 데이터 마이그레이션 (선택) | 10-30분 |
| Phase 3 | 기존 리소스 정리 | 2-3분 |
| Phase 4 | template.yaml 수정 | 5분 |
| Phase 5 | v2 스택 삭제 | 3-5분 |
| Phase 6 | 원래 이름 재생성 | 5-10분 |
| Phase 7 | 데이터 복원 (선택) | 10-30분 |
| **합계** | **데이터 없이**: 약 30분<br>**데이터 포함**: 1-2시간 |

---

## 리스크 및 주의사항

### ⚠️ 데이터 손실 위험
- **Phase 3**에서 기존 테이블 삭제 시 데이터 영구 손실
- **Phase 5**에서 DeletionPolicy 제거 후 v2 삭제 시 v2 데이터 손실
- **반드시 백업 후 진행**

### ⚠️ 다운타임
- Phase 3~6 사이에는 API 서비스 중단
- 프론트엔드에서 API 호출 실패 가능

### ⚠️ DeletionPolicy 관리
- Phase 4에서 제거
- Phase 6-2에서 다시 추가 (프로덕션 환경)
- 잊지 말 것!

### ⚠️ 비용
- 중복 리소스 생성으로 일시적 비용 증가 (v1 + v2 동시 존재)
- DynamoDB, Lambda 중복 실행

---

## 대안: 그냥 v2 사용

**가장 간단한 방법**:
- v2 그대로 사용 (이름만 다를 뿐 기능 동일)
- Phase 3 이후 단계 생략
- 기존 v1 테이블만 삭제하고 종료
- 추가 작업 불필요

**장점**:
- 리스크 없음
- 소요시간 최소 (10분)
- 데이터 안전

**단점**:
- 스택/리소스 이름에 "v2" 포함

---

## 체크리스트

**Phase 1 시작 전**:
- [ ] 현재 스택 상태 확인
- [ ] 기존 테이블 데이터 중요도 확인
- [ ] 백업 전략 수립

**Phase 3 시작 전**:
- [ ] v2 스택 정상 동작 확인
- [ ] 데이터 마이그레이션 완료 (필요 시)
- [ ] 기존 테이블 백업 완료

**Phase 5 시작 전**:
- [ ] template.yaml DeletionPolicy 제거 확인
- [ ] v2 데이터 백업 완료 (필요 시)

**Phase 6 시작 전**:
- [ ] 모든 이전 리소스 삭제 확인
- [ ] samconfig.toml 원복 확인

---

## 문제 발생 시 해결방법

### 문제 1: Phase 5에서 v2 테이블이 삭제 안 됨
**원인**: DeletionPolicy: Retain이 남아있음

**해결**:
```bash
# 수동 삭제
for table in users tutor-students learning-sessions daily-statistics ai-conversations feedback-messages websocket-connections sentences; do
  aws dynamodb delete-table \
    --table-name "Project02-SpeakTracker-v2-$table" \
    --region ap-northeast-2
done
```

### 문제 2: Phase 6에서 ResourceExistenceCheck 에러
**원인**: v2 리소스가 완전히 삭제되지 않음

**해결**:
```bash
# 모든 v2 리소스 확인 및 수동 삭제
aws dynamodb list-tables --region ap-northeast-2 | grep v2
aws cognito-idp list-user-pools --region ap-northeast-2 | grep v2
```

### 문제 3: 데이터 복원 실패
**원인**: 백업본이 없거나 손상됨

**해결**:
- DynamoDB Point-in-Time Recovery 사용 (활성화되어 있는 경우)
- AWS Backup에서 복원 (설정되어 있는 경우)

---

## 완료 후 검증

```bash
# 1. 스택 상태
aws cloudformation describe-stacks \
  --stack-name Project02-SpeakTracker \
  --region ap-northeast-2 \
  --query 'Stacks[0].StackStatus'

# 2. 모든 테이블 존재 확인
for table in users tutor-students learning-sessions daily-statistics ai-conversations feedback-messages websocket-connections sentences; do
  aws dynamodb describe-table \
    --table-name "Project02-SpeakTracker-$table" \
    --region ap-northeast-2 \
    --query 'Table.TableName' \
    --output text
done

# 3. UserPool 확인
aws cognito-idp list-user-pools \
  --max-results 20 \
  --region ap-northeast-2 \
  --query 'UserPools[?Name==`Project02-SpeakTracker-userpool`].[Name,Id]'

# 4. Lambda 함수 확인
aws lambda list-functions \
  --region ap-northeast-2 \
  --query 'Functions[?contains(FunctionName, `Project02-SpeakTracker`)].FunctionName'

# 5. API Gateway 확인
aws apigateway get-rest-apis \
  --region ap-northeast-2 \
  --query 'items[?contains(name, `Project02-SpeakTracker`)].[name,id]'
```

모든 리소스가 정상 확인되면 마이그레이션 완료! ✅
