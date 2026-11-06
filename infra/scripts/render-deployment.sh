#!/usr/bin/env bash
# Lightsail 컨테이너 배포 설정(JSON)을 만든다. 비밀값은 SSM(SecureString)에서 읽고 GitHub 로그에는 가린다.
#   사용: render-deployment.sh <service-name> <image> <parameter-path> <out-file>
set -euo pipefail

service="$1"
image="$2"
path="$3"
out="$4"

params=$(aws ssm get-parameters-by-path --path "$path" --with-decryption --recursive \
  --query 'Parameters[].{k:Name,v:Value}' --output json)

if [ "$(jq 'length' <<<"$params")" -eq 0 ]; then
  echo "SSM 경로 $path 에 값이 없습니다. terraform apply를 먼저 실행하세요." >&2
  exit 1
fi

missing=$(jq -r '.[] | select(.v == "CHANGE_ME") | .k' <<<"$params")
if [ -n "$missing" ]; then
  echo "아직 값을 넣지 않은 SSM 파라미터가 있습니다:" >&2
  echo "$missing" >&2
  exit 1
fi

# GitHub Actions 로그에서 비밀값을 가린다
if [ -n "${GITHUB_ACTIONS:-}" ]; then
  jq -r '.[].v | select(. != "EMPTY")' <<<"$params" | while IFS= read -r value; do
    [ -n "$value" ] && echo "::add-mask::$value"
  done
fi

jq -n \
  --arg service "$service" \
  --arg image "$image" \
  --argjson params "$params" \
  '{
    serviceName: $service,
    containers: {
      app: {
        image: $image,
        ports: { "8080": "HTTP" },
        environment: (
          ($params | map({ key: (.k | split("/") | last), value: (if .v == "EMPTY" then "" else .v end) }) | from_entries)
          + {
              SESSION_COOKIE_SECURE: "true",
              # Lightsail 공개 엔드포인트(TLS 종료) 뒤에서 https·호스트를 올바르게 보도록 X-Forwarded-* 를 따른다
              SERVER_FORWARDHEADERSSTRATEGY: "framework",
              TZ: "Asia/Seoul"
            }
        )
      }
    },
    publicEndpoint: {
      containerName: "app",
      containerPort: 8080,
      healthCheck: {
        path: "/actuator/health",
        successCodes: "200-299",
        intervalSeconds: 15,
        timeoutSeconds: 5,
        healthyThreshold: 2,
        unhealthyThreshold: 10
      }
    }
  }' > "$out"
