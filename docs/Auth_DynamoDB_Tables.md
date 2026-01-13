# 회원가입/로그인 DynamoDB 테이블

## 개요

SpeakTracker 프로젝트의 **회원가입 및 로그인(Auth)** 기능에서 사용하는 DynamoDB 테이블 구조입니다.

---

## 1. Users 테이블

### 테이블 정보
| 항목 | 값 |
|------|-----|
| **테이블 이름** | `${StackName}-users` (예: Project02-SpeakTracker-Auth-users) |
| **Billing Mode** | PAY_PER_REQUEST (온디맨드) |

### 스키마
| 속성 | 타입 | 키 | 설명 |
|------|------|-----|------|
| `email` | String | **PK (HASH)** | 사용자 이메일 (고유 식별자) |
| `name` | String | - | 사용자 이름 |
| `role` | String | - | 역할 (`student` 또는 `tutor`) |
| `created_at` | String | - | 가입 일시 (ISO 8601) |

### 예시 데이터
```json
{
  "email": "student1@example.com",
  "name": "홍길동",
  "role": "student",
  "created_at": "2026-01-11T10:30:00.000Z"
}
```

```json
{
  "email": "tutor1@example.com",
  "name": "김선생",
  "role": "tutor",
  "created_at": "2026-01-10T09:00:00.000Z"
}
```

### 주요 쿼리
| 작업 | API | 설명 |
|------|-----|------|
| 사용자 정보 조회 | `GetItem(email)` | 이메일로 사용자 정보 조회 |
| 사용자 등록 | `PutItem` | 회원가입 시 사용자 정보 저장 |
| 사용자 정보 수정 | `UpdateItem(email)` | 이름, 역할 등 수정 |

### 용도
- 회원가입 시 사용자 정보 저장
- 로그인 후 사용자 정보 조회 (role 포함)
- Cognito와 별개로 비즈니스 데이터 관리

---

## 2. Tutor-Students 테이블

### 테이블 정보
| 항목 | 값 |
|------|-----|
| **테이블 이름** | `${StackName}-tutor-students` (예: Project02-SpeakTracker-Auth-tutor-students) |
| **Billing Mode** | PAY_PER_REQUEST (온디맨드) |

### 스키마
| 속성 | 타입 | 키 | 설명 |
|------|------|-----|------|
| `tutor_email` | String | **PK (HASH)** | 튜터 이메일 |
| `student_email` | String | **SK (RANGE)** | 학생 이메일 |

### Global Secondary Index (GSI)
| 인덱스 이름 | 파티션 키 | 프로젝션 | 용도 |
|-------------|----------|----------|------|
| `student_email-index` | `student_email` | ALL | 학생 기준으로 튜터 조회 |

### 예시 데이터
```json
{
  "tutor_email": "tutor1@example.com",
  "student_email": "student1@example.com",
  "assigned_at": "2026-01-11T10:00:00.000Z",
  "status": "active"
}
```

```json
{
  "tutor_email": "tutor1@example.com",
  "student_email": "student2@example.com",
  "assigned_at": "2026-01-11T11:00:00.000Z",
  "status": "active"
}
```

### 주요 쿼리
| 작업 | API | 설명 |
|------|-----|------|
| 튜터의 학생 목록 | `Query(tutor_email)` | 특정 튜터가 담당하는 학생 목록 |
| 학생의 튜터 조회 | `Query(student_email)` on GSI | 특정 학생의 담당 튜터 조회 |
| 관계 등록 | `PutItem` | 튜터-학생 매칭 |
| 관계 삭제 | `DeleteItem(tutor_email, student_email)` | 매칭 해제 |

### 용도
- 튜터-학생 1:N 관계 관리
- 튜터 대시보드: 담당 학생 목록 표시
- 학생 페이지: 담당 튜터 정보 표시
- 튜터 → 학생 피드백 전송 대상 확인

---

## 테이블 관계도

```
┌─────────────────────┐
│      Users          │
│  (users 테이블)      │
├─────────────────────┤
│ PK: email           │
│ - name              │
│ - role (student/    │
│         tutor)      │
│ - created_at        │
└─────────────────────┘
         │
         │ email 참조
         ▼
┌─────────────────────────────┐
│      Tutor-Students         │
│   (tutor-students 테이블)    │
├─────────────────────────────┤
│ PK: tutor_email             │
│ SK: student_email           │
│ GSI: student_email-index    │
└─────────────────────────────┘
```

---

## Cognito vs DynamoDB

| 데이터 | 저장 위치 | 이유 |
|--------|----------|------|
| 이메일, 비밀번호 | **Cognito** | 인증/보안 |
| 이름 (기본) | **Cognito** | 토큰 payload에 포함 |
| 역할 (role) | **DynamoDB** | 비즈니스 로직, 쿼리 필요 |
| 생성일 | **DynamoDB** | 비즈니스 데이터 |
| 튜터-학생 관계 | **DynamoDB** | 관계형 데이터 |

---

## IAM 권한 (AuthFunction)

```yaml
Policies:
  - Statement:
    - Effect: Allow
      Action:
        - dynamodb:PutItem
        - dynamodb:GetItem
        - dynamodb:Query
        - dynamodb:UpdateItem
      Resource:
        - !GetAtt UsersTable.Arn
        - !GetAtt TutorStudentsTable.Arn
        - !Sub "${TutorStudentsTable.Arn}/index/*"
```

---

## 참고

- **DeletionPolicy: Retain** - 스택 삭제 시에도 테이블 보존
- **UpdateReplacePolicy: Retain** - 업데이트 시에도 데이터 보존
- 모든 테이블은 온디맨드(PAY_PER_REQUEST) 모드로 운영
