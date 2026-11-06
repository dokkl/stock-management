# 앱 환경 변수(비밀값). 배포 워크플로가 이 경로 아래 값을 모두 읽어 컨테이너 환경 변수로 넣는다.
#  - DB 접속 정보는 Terraform이 채운다.
#  - 나머지는 CHANGE_ME로 만들어 두고 운영자가 직접 넣는다(Terraform은 이후 값을 덮어쓰지 않는다):
#      aws ssm put-parameter --overwrite --type SecureString --name /stock-management/prod/KAKAO_CLIENT_ID --value '...'
#    CHANGE_ME가 남아 있으면 배포가 실패한다. 빈 값으로 두려면 EMPTY를 넣는다(SSM은 빈 문자열을 저장할 수 없다).
locals {
  parameter_path = "/${var.name}/prod"

  managed_parameters = {
    SPRING_DATASOURCE_URL      = "jdbc:postgresql://${aws_lightsail_database.main.master_endpoint_address}:${aws_lightsail_database.main.master_endpoint_port}/stock?sslmode=require"
    SPRING_DATASOURCE_USERNAME = aws_lightsail_database.main.master_username
    SPRING_DATASOURCE_PASSWORD = random_password.db.result
  }

  operator_parameters = [
    "APP_CRYPTO_KEY",      # AI 키 암호화 마스터 키: openssl rand -base64 32 (바꾸면 저장된 AI 키를 못 읽는다)
    "KAKAO_CLIENT_ID",     # 카카오 REST API 키
    "KAKAO_CLIENT_SECRET", # 카카오 Client Secret(콘솔에서 켜지 않았으면 EMPTY)
    "KAKAO_ADMIN_KEY",     # 카카오 어드민 키(탈퇴 시 연결 해제)
    "TOSS_CLIENT_ID",
    "TOSS_CLIENT_SECRET",
    "ADMIN_KAKAO_IDS", # 관리자 카카오 회원번호(쉼표 구분). 첫 배포(V11 마이그레이션) 전에 넣는다.
  ]
}

resource "aws_ssm_parameter" "managed" {
  for_each = local.managed_parameters

  name  = "${local.parameter_path}/${each.key}"
  type  = "SecureString"
  value = each.value
}

resource "aws_ssm_parameter" "operator" {
  for_each = toset(local.operator_parameters)

  name  = "${local.parameter_path}/${each.key}"
  type  = "SecureString"
  value = "CHANGE_ME"

  lifecycle {
    ignore_changes = [value]
  }
}
