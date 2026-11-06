output "app_url" {
  value = "https://${var.domain_name}"
}

output "kakao_redirect_uri" {
  description = "카카오 개발자 콘솔 > 카카오 로그인 > Redirect URI에 등록한다"
  value       = "https://${var.domain_name}/login/oauth2/code/kakao"
}

output "container_service_name" {
  value = aws_lightsail_container_service.app.name
}

output "container_service_default_url" {
  value = aws_lightsail_container_service.app.url
}

output "deploy_role_arn" {
  description = "GitHub 저장소 Variables의 AWS_DEPLOY_ROLE_ARN에 넣는다"
  value       = aws_iam_role.deploy.arn
}

output "parameter_path" {
  description = "앱 환경 변수(SSM SecureString) 경로"
  value       = local.parameter_path
}

output "operator_parameters_to_fill" {
  value = [for p in local.operator_parameters : "${local.parameter_path}/${p}"]
}
