variable "aws_account_id" {
  description = "배포 대상 AWS 계정(다른 계정 자격 증명으로는 plan/apply가 거부된다)"
  type        = string
  default     = "378040395734"
}

variable "region" {
  description = "Lightsail 리전"
  type        = string
  default     = "ap-northeast-2"
}

variable "name" {
  description = "리소스 이름 접두사(Lightsail 컨테이너 서비스 이름이 된다)"
  type        = string
  default     = "stock-management"
}

variable "domain_name" {
  description = "서비스 주소(서브도메인). 예: stock.example.com. Route 53 CNAME으로 연결하므로 루트 도메인은 쓸 수 없다."
  type        = string

  validation {
    condition     = length(split(".", var.domain_name)) >= 3
    error_message = "서브도메인을 지정하세요(예: stock.example.com). 루트 도메인은 CNAME을 둘 수 없습니다."
  }
}

variable "hosted_zone_name" {
  description = "domain_name이 속한 Route 53 호스팅 영역(같은 계정). 예: example.com"
  type        = string
}

variable "container_power" {
  description = "컨테이너 노드 크기(서울 월 요금): nano $7(0.25vCPU·0.5GB), micro $10(0.25·1GB), small $15(0.5·1GB), medium $40(1·2GB)"
  type        = string
  default     = "small"
}

variable "container_scale" {
  description = "컨테이너 노드 수. 세션이 메모리에 있으므로 1을 권장한다."
  type        = number
  default     = 1
}

variable "db_blueprint_id" {
  description = "PostgreSQL 엔진 버전. 사용 가능 목록: aws lightsail get-relational-database-blueprints --region ap-northeast-2"
  type        = string
  default     = "postgres_18"
}

variable "db_bundle_id" {
  description = "DB 크기. 목록: aws lightsail get-relational-database-bundles --region ap-northeast-2"
  type        = string
  default     = "micro_2_0"
}

variable "db_availability_zone" {
  type    = string
  default = "ap-northeast-2a"
}

variable "github_repository" {
  description = "배포 워크플로를 실행할 GitHub 저장소(owner/name)"
  type        = string
  default     = "dokkl/stock-management"
}

variable "github_environment" {
  description = "배포 잡의 GitHub Environment. 이 환경의 잡만 배포 역할을 맡을 수 있다."
  type        = string
  default     = "production"
}

variable "create_github_oidc_provider" {
  description = "계정에 GitHub OIDC 공급자(token.actions.githubusercontent.com)가 이미 있으면 false"
  type        = bool
  default     = true
}

variable "egress_proxy_bundle_id" {
  description = "토스 전용 고정 IP 프록시 인스턴스 크기(nano_3_0: 월 $5, 고정 IPv4 포함)"
  type        = string
  default     = "nano_3_0"
}
