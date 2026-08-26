#!/usr/bin/env bash
set -Eeuo pipefail

: "${IMAGE_ROOT:?必须设置 IMAGE_ROOT，例如 ghcr.io/owner/repository}"
: "${IMAGE_TAG:?必须设置 IMAGE_TAG，建议使用 Git commit SHA}"

NAMESPACE="${NAMESPACE:-secondhand}"
MANIFEST="${MANIFEST:-k8s/production.yaml}"
ROLLOUT_TIMEOUT="${ROLLOUT_TIMEOUT:-300s}"
rendered_manifest="$(mktemp)"
trap 'rm -f "$rendered_manifest"' EXIT

sed \
  -e "s|__IMAGE_ROOT__|${IMAGE_ROOT}|g" \
  -e "s|__IMAGE_TAG__|${IMAGE_TAG}|g" \
  "$MANIFEST" > "$rendered_manifest"

kubectl apply -f "$rendered_manifest"
kubectl -n "$NAMESPACE" rollout status deployment/mysql --timeout="$ROLLOUT_TIMEOUT"
kubectl -n "$NAMESPACE" rollout status deployment/backend --timeout="$ROLLOUT_TIMEOUT"
kubectl -n "$NAMESPACE" rollout status deployment/frontend --timeout="$ROLLOUT_TIMEOUT"
kubectl -n "$NAMESPACE" get pods,svc,pvc
