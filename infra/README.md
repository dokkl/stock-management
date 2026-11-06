# 배포 인프라 (AWS Lightsail, Terraform)

계정 `378040395734`, 리전 `ap-northeast-2`(서울).

```text
GitHub (main 머지)
  └─ CI (./gradlew check) 통과
      └─ Deploy 워크플로 (OIDC 역할, 장기 키 없음)
          ├─ docker build → Lightsail 컨테이너 레지스트리에 푸시
          ├─ SSM(SecureString) 값으로 컨테이너 환경 변수 구성
          └─ 배포 → 헬스 체크(/actuator/health) → 스모크 테스트

https://<domain_name>  (Route 53 CNAME, Lightsail 인증서)
  └─ Lightsail 컨테이너 서비스 (app :8080)
      └─ Lightsail 관리형 PostgreSQL (공개 모드 꺼짐, 자동 백업)
```

| 디렉터리 | 내용 |
|---|---|
| `bootstrap/` | Terraform 상태 버킷(S3, 버전 관리·암호화). 최초 1회, 로컬 상태 |
| `terraform/` | 컨테이너 서비스, DB, 인증서·DNS, SSM 파라미터, GitHub OIDC 배포 역할 |
| `scripts/render-deployment.sh` | 배포 설정(JSON) 생성. Deploy 워크플로가 쓴다 |

Terraform이 만드는 것은 인프라까지다. 이미지와 배포(deployment)는 GitHub Actions가 만든다.

## 처음 한 번

### 1. AWS 로그인

`378040395734` 계정 자격 증명이 필요하다(지금 로컬 프로필에는 없다). 예:

```bash
aws configure sso --profile stock          # 또는 aws configure --profile stock
export AWS_PROFILE=stock
aws sts get-caller-identity                # Account 가 378040395734 인지 확인
```

Terraform은 `allowed_account_ids`로 다른 계정에서의 실행을 막는다.

### 2. 상태 버킷

```bash
cd infra/bootstrap
terraform init && terraform apply
```

### 3. 인프라

```bash
cd infra/terraform
cp terraform.tfvars.example terraform.tfvars   # domain_name, hosted_zone_name 채우기
aws lightsail get-relational-database-blueprints --region ap-northeast-2 \
  --query "blueprints[?engine=='postgres'].blueprintId"   # 최신 PostgreSQL 버전 확인 → db_blueprint_id
terraform init
terraform plan
terraform apply
```

- 인증서 검증(DNS)이 끝날 때까지 apply가 기다린다(보통 몇 분, 최대 30분).
- 계정에 GitHub OIDC 공급자가 이미 있으면 `create_github_oidc_provider = false`.
- DB는 `prevent_destroy`라 `terraform destroy`로 지워지지 않는다(실수 방지). 지울 때는 그 줄을 먼저 뺀다.

### 4. 앱 비밀값(SSM)

`terraform output operator_parameters_to_fill`에 나온 파라미터에 값을 넣는다. `CHANGE_ME`가 남아 있으면 배포가 실패한다.

```bash
P=/stock-management/prod
put() { aws ssm put-parameter --overwrite --type SecureString --name "$P/$1" --value "$2" > /dev/null; }
put APP_CRYPTO_KEY      "$(openssl rand -base64 32)"   # 한 번 정하면 바꾸지 않는다(저장된 AI 키를 못 읽게 된다)
put KAKAO_CLIENT_ID     '...'
put KAKAO_CLIENT_SECRET 'EMPTY'                         # Client Secret을 켜지 않았으면 EMPTY
put KAKAO_ADMIN_KEY     '...'
put TOSS_CLIENT_ID      '...'
put TOSS_CLIENT_SECRET  '...'
put ADMIN_KAKAO_IDS     '...'                           # 관리자 카카오 회원번호. 첫 배포 전에 넣는다
```

DB 접속 정보(`SPRING_DATASOURCE_*`)는 Terraform이 넣는다. 값을 바꾼 뒤에는 Deploy 워크플로를 다시 실행해야 반영된다.

### 5. 카카오 개발자 콘솔

- 플랫폼 > Web 사이트 도메인: `https://<domain_name>`
- 카카오 로그인 > Redirect URI: `terraform output kakao_redirect_uri`

### 6. GitHub 설정

저장소 Settings에서:

- **Environments → `production`** 생성, Deployment branches를 `main`만 허용(필요하면 Required reviewers로 수동 승인).
  배포 역할은 이 환경의 잡만 맡을 수 있다.
- **Variables**(Actions):
  - `AWS_DEPLOY_ROLE_ARN` = `terraform output -raw deploy_role_arn`
  - `APP_DOMAIN` = `domain_name` 값(예: `stock.seeplus.net`)
  - `TOSS_ALLOWED_IP` = 토스에 등록한 IP(7단계)

### 7. 토스 API 허용 IP

토스증권 Open API는 등록한 IP에서만 호출을 받는다(아니면 `403 IP address not allowed`). 컨테이너 서비스의 나가는 IP는
배포마다 바뀌므로, 토스 호출만 고정 IP 프록시(`egress_proxy.tf`: Lightsail nano 인스턴스 + 고정 IP + squid)로 보낸다.
앱은 SSM의 `TOSS_PROXY_URL`(프록시 사설 IP:3128)을 쓴다.

```bash
terraform output -raw toss_allowed_ip   # 이 IP를 토스 개발자 콘솔에 등록하고 저장소 변수 TOSS_ALLOWED_IP에도 넣는다
```

- 프록시는 `openapi.tossinvest.com:443`만, Lightsail 사설 대역에서 오는 요청만 받는다. SSH 포트는 닫혀 있다.
- 인스턴스를 다시 만들어도 고정 IP는 그대로 붙으므로 토스 등록은 한 번이면 된다.
- Deploy 워크플로는 배포 직후 로그에서 토스 거부(403)·동기화 실패를 찾아 경고한다.

### 8. 첫 배포

main에 머지하면 CI 통과 후 자동 배포된다. 지금 바로 하려면 Actions → Deploy → Run workflow.

## 운영 메모

- 세션은 앱 메모리에 있다. 배포할 때마다(새 컨테이너) 사용자는 다시 로그인해야 하고, 노드를 2개 이상으로 늘리면 세션이 맞지 않는다.
  `container_scale`은 1로 둔다.
- 컨테이너 크기는 `container_power`(기본 small: 0.5 vCPU·1GB, 월 $15)로 바꾼다. DB는 micro_2_0(1GB, 월 $15).
- 컨테이너 로그: `aws lightsail get-container-log --service-name stock-management --container-name app`
- 오래된 이미지는 Lightsail 콘솔 > 컨테이너 서비스 > Images에서 지운다.
- DB 백업: 매일 KST 03:00 자동 백업, 삭제 시 최종 스냅샷(`stock-management-db-final`).
