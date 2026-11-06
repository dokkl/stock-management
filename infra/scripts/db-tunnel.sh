#!/usr/bin/env bash
# 운영 DB(Lightsail 관리형 PostgreSQL, 공개 모드 꺼짐)에 맥북에서 접속하기 위한 SSH 터널.
# 프록시 인스턴스(고정 IP)를 경유한다. Terraform 변수 admin_ssh_cidrs에 내 IP가 있어야 SSH가 열린다.
#
#   AWS_PROFILE=starter infra/scripts/db-tunnel.sh [로컬 포트, 기본 15432]
#
# 터널이 열린 동안 DB 클라이언트에서: host=localhost port=15432 db=stock user=stock sslmode=require
# 비밀번호: aws ssm get-parameter --name /stock-management/prod/SPRING_DATASOURCE_PASSWORD --with-decryption
#   --query Parameter.Value --output text
# Ctrl+C로 닫는다. SSH 키는 Lightsail이 발급하는 임시 인증서(수 분 유효)를 쓰고 끝나면 지운다.
set -euo pipefail

local_port="${1:-15432}"
region="${AWS_REGION:-ap-northeast-2}"
instance="stock-management-egress-proxy"
database="stock-management-db"

db_host=$(aws lightsail get-relational-database --region "$region" --relational-database-name "$database" \
  --query 'relationalDatabase.masterEndpoint.address' --output text)

workdir=$(mktemp -d)
trap 'rm -rf "$workdir"' EXIT
chmod 700 "$workdir"

details=$(aws lightsail get-instance-access-details --region "$region" --instance-name "$instance" \
  --protocol ssh --output json)
jq -r '.accessDetails.privateKey' <<<"$details" > "$workdir/key"
jq -r '.accessDetails.certKey' <<<"$details" > "$workdir/key-cert.pub"
chmod 600 "$workdir/key"
user=$(jq -r '.accessDetails.username' <<<"$details")
ip=$(jq -r '.accessDetails.ipAddress' <<<"$details")

echo "터널: localhost:${local_port} → ${db_host}:5432 (경유 ${ip}). Ctrl+C로 닫습니다."
ssh -N \
  -i "$workdir/key" -o CertificateFile="$workdir/key-cert.pub" \
  -o StrictHostKeyChecking=accept-new -o UserKnownHostsFile="$HOME/.ssh/known_hosts_stock_management" \
  -o ExitOnForwardFailure=yes -o ServerAliveInterval=30 \
  -L "127.0.0.1:${local_port}:${db_host}:5432" \
  "${user}@${ip}"
