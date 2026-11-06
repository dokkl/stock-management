# 앱 컨테이너 서비스. 이미지 푸시와 배포(deployment)는 GitHub Actions(.github/workflows/deploy.yml)가 한다.
resource "aws_lightsail_certificate" "app" {
  name        = "${var.name}-cert"
  domain_name = var.domain_name
}

# 인증서 검증용 CNAME
# for_each 키는 plan 때 알아야 하므로 변수(domain_name)로 정하고, 검증 값은 apply 때 채운다.
resource "aws_route53_record" "cert_validation" {
  for_each = toset([var.domain_name])

  zone_id = data.aws_route53_zone.main.zone_id
  name    = local.cert_validation[each.key].resource_record_name
  type    = local.cert_validation[each.key].resource_record_type
  records = [local.cert_validation[each.key].resource_record_value]
  ttl     = 300
}

locals {
  cert_validation = {
    for o in aws_lightsail_certificate.app.domain_validation_options : o.domain_name => o
  }
}

# 인증서가 ISSUED가 될 때까지 기다린다(검증 전 인증서는 컨테이너 서비스에 붙일 수 없다). aws CLI가 필요하다.
resource "terraform_data" "cert_issued" {
  triggers_replace = [aws_lightsail_certificate.app.arn]

  provisioner "local-exec" {
    command = <<-EOT
      for _ in $(seq 1 60); do
        status=$(aws lightsail get-certificates --region ${var.region} --certificate-name ${aws_lightsail_certificate.app.name} \
          --query 'certificates[0].certificateDetail.status' --output text)
        echo "certificate status: $status"
        [ "$status" = "ISSUED" ] && exit 0
        sleep 30
      done
      echo "인증서가 30분 안에 발급되지 않았습니다" >&2
      exit 1
    EOT
  }

  depends_on = [aws_route53_record.cert_validation]
}

resource "aws_lightsail_container_service" "app" {
  name  = var.name
  power = var.container_power
  scale = var.container_scale

  public_domain_names {
    certificate {
      certificate_name = aws_lightsail_certificate.app.name
      domain_names     = [var.domain_name]
    }
  }

  depends_on = [terraform_data.cert_issued]
}
