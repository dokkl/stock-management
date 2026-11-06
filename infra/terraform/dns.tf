data "aws_route53_zone" "main" {
  name         = var.hosted_zone_name
  private_zone = false
}

locals {
  # url 예: https://stock-management.abc123.ap-northeast-2.cs.amazonlightsail.com/
  container_host = trimsuffix(trimprefix(aws_lightsail_container_service.app.url, "https://"), "/")
}

resource "aws_route53_record" "app" {
  zone_id = data.aws_route53_zone.main.zone_id
  name    = var.domain_name
  type    = "CNAME"
  ttl     = 300
  records = [local.container_host]
}
