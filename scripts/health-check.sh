#!/usr/bin/env bash
set -Eeuo pipefail

NAMESPACE="${NAMESPACE:-secondhand}"
HEALTHCHECK_RETRIES="${HEALTHCHECK_RETRIES:-30}"
HEALTHCHECK_INTERVAL="${HEALTHCHECK_INTERVAL:-2}"
BASE_URL="${1:-}"
port_forward_pid=""
port_forward_log=""

cleanup() {
  if [[ -n "$port_forward_pid" ]]; then
    kill "$port_forward_pid" 2>/dev/null || true
    wait "$port_forward_pid" 2>/dev/null || true
  fi
  if [[ -n "$port_forward_log" ]]; then
    rm -f "$port_forward_log"
  fi
}
trap cleanup EXIT

if [[ -z "$BASE_URL" ]]; then
  HEALTHCHECK_PORT="${HEALTHCHECK_PORT:-18080}"
  BASE_URL="http://127.0.0.1:${HEALTHCHECK_PORT}"
  port_forward_log="$(mktemp)"
  kubectl -n "$NAMESPACE" port-forward service/frontend "${HEALTHCHECK_PORT}:80" \
    >"$port_forward_log" 2>&1 &
  port_forward_pid=$!
fi

for ((attempt = 1; attempt <= HEALTHCHECK_RETRIES; attempt++)); do
  frontend_ok=false
  api_ok=false

  if curl --fail --silent --show-error --max-time 5 "${BASE_URL}/" >/dev/null; then
    frontend_ok=true
  fi

  api_response="$(curl --fail --silent --show-error --max-time 5 "${BASE_URL}/api/categories" 2>/dev/null || true)"
  if [[ "$api_response" == *'"success":true'* ]]; then
    api_ok=true
  fi

  if [[ "$frontend_ok" == true && "$api_ok" == true ]]; then
    echo "健康检查通过：前端和后端 API 均可通过 ${BASE_URL} 访问"
    exit 0
  fi

  if [[ -n "$port_forward_pid" ]] && ! kill -0 "$port_forward_pid" 2>/dev/null; then
    echo "kubectl port-forward 意外停止" >&2
    cat "$port_forward_log" >&2
    exit 1
  fi

  echo "健康检查第 ${attempt}/${HEALTHCHECK_RETRIES} 次失败，准备重试……" >&2
  sleep "$HEALTHCHECK_INTERVAL"
done

echo "健康检查失败：${BASE_URL}" >&2
if [[ -n "$port_forward_log" ]]; then
  cat "$port_forward_log" >&2
fi
exit 1
