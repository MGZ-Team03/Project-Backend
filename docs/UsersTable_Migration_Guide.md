# UsersTable 마이그레이션 가이드

## 배경

DynamoDB 테이블의 **Key Schema(PK/SK)**는 생성 후 변경할 수 없습니다.  
CloudFormation/SAM에서 custom-named 리소스의 키 스키마를 변경하려고 하면 배포가 실패합니다.

### 변경 사항
| 항목 | 기존 스키마 | 새 스키마 |
|------|------------|----------|
| Partition Key (PK) | `email` | `role` |
| Sort Key (SK) | - (없음) | `email` |
| GSI | 없음 | `email-index` (email로 조회) |

---

## 방법 3: 테이블 이름 변경 후 복원

### 개요

1. **1단계**: 새 이름(`-users-v2`)으로 테이블 생성 및 배포
2. **2단계**: (선택) 나중에 원래 이름(`-users`)으로 복원

---

## 1단계: 새 이름으로 배포 (현재 단계)

### 1.1 template.yaml 수정 (완료됨)

```yaml
# 변경 전
UsersTable:
  Type: AWS::DynamoDB::Table
  Properties:
    TableName: !Sub '${AWS::StackName}-users'
    ...

# 변경 후
UsersTable:
  Type: AWS::DynamoDB::Table
  Properties:
    TableName: !Sub '${AWS::StackName}-users-v2'  # 이름 변경
    ...
```

### 1.2 배포 실행

```bash
cd /home/user1/MGZ-Team03/Project-Backend
sam build
sam deploy --no-confirm-changeset
```

### 1.3 배포 확인

```bash
# 새 테이블 생성 확인
aws dynamodb describe-table \
  --table-name project02-speaktracker-hwkang-users-v2 \
  --query 'Table.{Name:TableName,Status:TableStatus,KeySchema:KeySchema}' \
  --no-cli-pager

# 예상 결과:
# {
#     "Name": "project02-speaktracker-hwkang-users-v2",
#     "Status": "ACTIVE",
#     "KeySchema": [
#         {"AttributeName": "role", "KeyType": "HASH"},
#         {"AttributeName": "email", "KeyType": "RANGE"}
#     ]
# }
```

### 1.4 Lambda 환경 변수 확인

Lambda 함수의 `USERS_TABLE` 환경 변수가 자동으로 새 테이블 이름을 참조합니다:

```bash
aws lambda get-function-configuration \
  --function-name project02-speaktracker-hwkang-AuthFunction-XXXXX \
  --query 'Environment.Variables.USERS_TABLE' \
  --no-cli-pager

# 예상 결과: "project02-speaktracker-hwkang-users-v2"
```

---

## 2단계: 원래 이름으로 복원 (선택 사항)

> ⚠️ **주의**: 이 단계는 테이블 이름을 `-users`로 되돌리고 싶을 때만 수행하세요.  
> 대부분의 경우 `-users-v2`를 그대로 사용해도 문제없습니다.

### 2.1 현재 데이터 백업

```bash
# 테이블 전체 데이터 내보내기
aws dynamodb scan \
  --table-name project02-speaktracker-hwkang-users-v2 \
  --output json \
  --no-cli-pager > users_backup.json

# 백업 확인
cat users_backup.json | jq '.Count'
```

### 2.2 기존 -users 테이블 삭제 확인

```bash
# 기존 테이블이 완전히 삭제되었는지 확인
aws dynamodb describe-table \
  --table-name project02-speaktracker-hwkang-users \
  --no-cli-pager 2>&1

# "ResourceNotFoundException" 메시지가 나와야 함
```

### 2.3 template.yaml 이름 원복

```yaml
# 변경
UsersTable:
  Type: AWS::DynamoDB::Table
  Properties:
    TableName: !Sub '${AWS::StackName}-users'  # -v2 제거
    ...
```

### 2.4 재배포

```bash
# CloudFormation이 -users-v2 테이블을 삭제하고 -users 테이블을 생성
sam build
sam deploy --no-confirm-changeset
```

### 2.5 데이터 복원

```bash
# 백업 데이터를 새 테이블에 복원 (Python 스크립트 사용)
python3 << 'EOF'
import json
import boto3

dynamodb = boto3.resource('dynamodb', region_name='ap-northeast-2')
table = dynamodb.Table('project02-speaktracker-hwkang-users')

with open('users_backup.json', 'r') as f:
    data = json.load(f)

with table.batch_writer() as batch:
    for item in data['Items']:
        # DynamoDB JSON 형식을 일반 Python 딕셔너리로 변환
        converted_item = {}
        for key, value in item.items():
            if 'S' in value:
                converted_item[key] = value['S']
            elif 'N' in value:
                converted_item[key] = int(value['N'])
            elif 'BOOL' in value:
                converted_item[key] = value['BOOL']
        batch.put_item(Item=converted_item)

print(f"Restored {data['Count']} items")
EOF
```

### 2.6 복원 확인

```bash
aws dynamodb scan \
  --table-name project02-speaktracker-hwkang-users \
  --select COUNT \
  --no-cli-pager
```

---

## 코드 호환성 확인

### 영향받는 파일 목록

| 파일 | 변경 필요 여부 | 상태 |
|------|--------------|------|
| `AuthFunction/.../UserRepository.java` | ❌ 불필요 | `USERS_TABLE` 환경 변수 사용 |
| `TutorRegisterFunction/.../DynamoDBHelper.java` | ❌ 불필요 | `USERS_TABLE` 환경 변수 사용 |
| `DashboardFunction/.../StudentStatusCollector.java` | ❌ 불필요 | `USERS_TABLE` 환경 변수 사용 |

### 새 스키마 대응 코드 (이미 수정됨)

**UserRepository.java - findByEmail()**: GSI `email-index` 사용
```java
QueryRequest queryRequest = QueryRequest.builder()
    .tableName(usersTable)
    .indexName("email-index")  // GSI 사용
    .keyConditionExpression("email = :email")
    .expressionAttributeValues(Map.of(
        ":email", AttributeValue.builder().s(email).build()
    ))
    .limit(1)
    .build();
```

**UserRepository.java - save()**: `role` 필드 포함
```java
item.put("email", AttributeValue.builder().s(user.getEmail()).build());
item.put("role", AttributeValue.builder().s(user.getRole()).build());  // PK
```

---

## 트러블슈팅

### 오류: "CloudFormation cannot update a stack when a custom-named resource requires replacing"

**원인**: 키 스키마 변경 시 테이블 교체가 필요하지만, custom-named 리소스는 자동 교체 불가

**해결**:
1. 기존 테이블 수동 삭제:
   ```bash
   aws dynamodb delete-table --table-name project02-speaktracker-hwkang-users
   ```
2. 테이블 이름 변경 (`-users` → `-users-v2`)
3. 재배포

### 오류: "Table already exists"

**원인**: 이전 배포에서 생성된 테이블이 남아있음

**해결**:
```bash
# 테이블 상태 확인
aws dynamodb describe-table --table-name <table-name> --no-cli-pager

# 필요시 삭제
aws dynamodb delete-table --table-name <table-name>
```

---

## 요약

| 단계 | 설명 | 명령어 |
|------|------|--------|
| 1 | 테이블 이름을 `-users-v2`로 변경 | template.yaml 수정 |
| 2 | 배포 | `sam deploy --no-confirm-changeset` |
| 3 | (선택) 원래 이름 복원 | 위 가이드 참조 |

**현재 권장**: `-users-v2` 이름으로 배포 후 그대로 사용. 모든 Lambda 함수가 자동으로 새 테이블을 참조합니다.
