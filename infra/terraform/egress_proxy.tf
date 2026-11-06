# 토스 API 전용 고정 IP 프록시. 토스증권 Open API는 등록한 IP만 허용하는데 컨테이너 서비스의 나가는 IP는 배포마다 바뀐다.
# 앱은 토스 호출만 이 프록시(squid, 사설 IP:3128)로 보내고, 토스에는 고정 IP 하나만 등록한다.
#  - 프록시는 openapi.tossinvest.com:443 CONNECT만 허용한다.
#  - 방화벽은 Lightsail 사설 대역에서 오는 3128만 연다(SSH 포함 그 밖의 포트는 닫는다).
locals {
  proxy_port          = 3128
  lightsail_vpc_cidrs = ["172.26.0.0/16"]
}

resource "aws_lightsail_instance" "egress_proxy" {
  name              = "${var.name}-egress-proxy"
  availability_zone = var.db_availability_zone
  blueprint_id      = "ubuntu_24_04"
  bundle_id         = var.egress_proxy_bundle_id

  # Lightsail은 자체 초기화 스크립트 뒤에 이 내용을 붙여 sh로 실행한다(shebang 무시). POSIX sh 문법만 쓴다.
  user_data = <<-EOT
    set -eux
    export DEBIAN_FRONTEND=noninteractive
    # 첫 부팅 때 자동 업데이트가 apt 잠금을 잡고 있으므로 풀릴 때까지 기다린다
    APT_OPTS="-o DPkg::Lock::Timeout=900 -o Acquire::Retries=5"
    for _ in 1 2 3; do apt-get $APT_OPTS update && break || sleep 20; done
    apt-get $APT_OPTS install -y squid
    cat > /etc/squid/squid.conf <<'CONF'
    http_port ${local.proxy_port}
    acl lightsail src 172.16.0.0/12
    acl toss dstdomain openapi.tossinvest.com
    acl tls_port port 443
    acl CONNECT method CONNECT
    http_access deny !lightsail
    http_access deny !toss
    http_access deny CONNECT !tls_port
    http_access allow CONNECT toss
    http_access deny all
    cache deny all
    forwarded_for delete
    via off
    access_log stdio:/var/log/squid/access.log
    CONF
    squid -k parse
    systemctl enable squid
    systemctl restart squid
    systemctl is-active squid
  EOT
}

resource "aws_lightsail_static_ip" "egress_proxy" {
  name = "${var.name}-egress-ip"
}

resource "aws_lightsail_static_ip_attachment" "egress_proxy" {
  static_ip_name = aws_lightsail_static_ip.egress_proxy.name
  instance_name  = aws_lightsail_instance.egress_proxy.name

  # 인스턴스를 다시 만들면(이름은 같음) 고정 IP를 다시 붙인다
  lifecycle {
    replace_triggered_by = [aws_lightsail_instance.egress_proxy]
  }
}

# 기본으로 열리는 22·80을 닫고 프록시 포트만 Lightsail 내부에 연다.
# admin_ssh_cidrs를 주면 그 IP에만 SSH를 연다(운영자의 DB SSH 터널용).
resource "aws_lightsail_instance_public_ports" "egress_proxy" {
  instance_name = aws_lightsail_instance.egress_proxy.name

  port_info {
    protocol  = "tcp"
    from_port = local.proxy_port
    to_port   = local.proxy_port
    cidrs     = local.lightsail_vpc_cidrs
  }

  dynamic "port_info" {
    for_each = length(var.admin_ssh_cidrs) > 0 ? [1] : []
    content {
      protocol  = "tcp"
      from_port = 22
      to_port   = 22
      cidrs     = var.admin_ssh_cidrs
    }
  }

  # 새 인스턴스는 기본 포트(22·80)가 열려 있으므로 규칙을 다시 적용한다
  lifecycle {
    replace_triggered_by = [aws_lightsail_instance.egress_proxy]
  }
}

resource "aws_ssm_parameter" "toss_proxy_url" {
  name  = "${local.parameter_path}/TOSS_PROXY_URL"
  type  = "SecureString"
  value = "http://${aws_lightsail_instance.egress_proxy.private_ip_address}:${local.proxy_port}"
}
