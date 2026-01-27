# GitHub Actions OIDC → AWS AssumeRole 설정 가이드 (dev/prod)

이 프로젝트는 **GitHub Actions에서 장기 Access Key 없이** AWS에 배포하기 위해 **OIDC 기반 AssumeRole**을 권장합니다.

## 1) GitHub OIDC Provider 생성(계정당 1회)
- AWS 콘솔 → IAM → Identity providers → **Add provider**
  - Provider type: **OpenID Connect**
  - Provider URL: `https://token.actions.githubusercontent.com`
  - Audience: `sts.amazonaws.com`

## 2) 배포용 IAM Role 2개 생성(권장: dev/prod 분리)
- 예시 Role 이름
  - `project02-speaktracker-github-actions-dev`
  - `project02-speaktracker-github-actions-prod`

### 신뢰 정책(Trust policy) 예시
아래에서 `<OWNER>/<REPO>`를 실제 GitHub 저장소로 바꾸세요.

- **dev (develop 브랜치만 허용)**:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": { "Federated": "arn:aws:iam::<ACCOUNT_ID>:oidc-provider/token.actions.githubusercontent.com" },
      "Action": "sts:AssumeRoleWithWebIdentity",
      "Condition": {
        "StringEquals": {
          "token.actions.githubusercontent.com:aud": "sts.amazonaws.com",
          "token.actions.githubusercontent.com:sub": "repo:<OWNER>/<REPO>:ref:refs/heads/develop"
        }
      }
    }
  ]
}
```

- **prod (main 브랜치만 허용)**:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": { "Federated": "arn:aws:iam::<ACCOUNT_ID>:oidc-provider/token.actions.githubusercontent.com" },
      "Action": "sts:AssumeRoleWithWebIdentity",
      "Condition": {
        "StringEquals": {
          "token.actions.githubusercontent.com:aud": "sts.amazonaws.com",
          "token.actions.githubusercontent.com:sub": "repo:<OWNER>/<REPO>:ref:refs/heads/main"
        }
      }
    }
  ]
}
```

## 3) Role에 부여할 권한(배포 최소 권한 베이스라인)
SAM 배포는 CloudFormation + (S3 아티팩트) + (Lambda/API Gateway/IAM 등 리소스 생성/수정) 권한이 필요합니다.

### 운영 팁
- **dev는 일단 넓게**(문제 없이 배포가 먼저), **prod는 좁게**(리뷰/승인 + 최소권한)로 가는 것을 권장합니다.
- 최소권한을 빡빡하게 잡으려면, 실제 생성되는 리소스(스택 이벤트)를 한 번 돌려보고 필요한 액션만 좁히는 방식이 가장 빠릅니다.

### 빠른 시작(권장X이지만 초기 검증용)
- dev Role에 AWS 관리형 `AdministratorAccess`를 임시로 붙여서 파이프라인을 먼저 붙인 뒤,
- 배포가 안정화되면 CloudTrail/스택 이벤트를 기반으로 최소권한으로 축소하세요.

## 4) GitHub 쪽 설정(Secrets/Variables)
GitHub → Settings → Environments → `dev`, `prod` 생성 후 각각 아래 값 저장:

- **Secrets (환경별)**  
  - `AWS_ROLE_ARN`: 위에서 만든 Role ARN

- **Variables (환경별)**  
  - `AWS_REGION`: 예) `ap-northeast-2`  
  - `SAM_STACK_NAME`: 예) `project02-speaktracker-dev` / `project02-speaktracker-prod`  
  - `STAGE_NAME`: `Dev` / `Prod`  
  - `FRONTEND_URL`: dev/prod 프론트 URL  
  - `SES_SENDER_EMAIL`: SES 발신자(검증된 이메일/도메인)

## 5) 워크플로우 필수 권한
워크플로우 파일에는 최소 아래가 필요합니다.

```yaml
permissions:
  id-token: write
  contents: read
```

