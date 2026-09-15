#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
INFRA_DIR="$PROJECT_ROOT/examples/_infra"

LGTM=false
CLEAN=false
for arg in "$@"; do
  case "$arg" in
    --lgtm)  LGTM=true ;;
    --clean) CLEAN=true ;;
  esac
done

if $CLEAN; then
  echo "==> Cleaning existing stack and volumes..."
  podman-compose -p eip -f "$INFRA_DIR/compose.yaml" down -v 2>/dev/null || true
  for vol in eip-kafka-data eip-pulsar-data eip-redis-data eip-postgres-data; do
    podman volume rm "$vol" 2>/dev/null || true
  done
  echo "    Volumes removed."
fi

# Pulsar's embedded BookKeeper corrupts its ledger data across dirty shutdowns.
# If Pulsar exited with an error, wipe its volume before restarting.
pulsar_status=$(podman inspect --format='{{.State.Status}}' eip-pulsar 2>/dev/null || echo "absent")
if [[ "$pulsar_status" == "exited" ]] || [[ "$pulsar_status" == "dead" ]]; then
  echo "==> Detected crashed Pulsar container — cleaning volume to prevent ledger corruption..."
  podman rm eip-pulsar 2>/dev/null || true
  podman volume rm eip-pulsar-data 2>/dev/null || true
  echo "    Pulsar volume cleaned."
fi

echo "==> Starting EIP base stack (Kafka, Pulsar, Redis, PostgreSQL, Apicurio)..."
podman-compose -p eip -f "$INFRA_DIR/compose.yaml" up -d

echo "==> Waiting for base services to become healthy..."
wait_healthy() {
  local svc=$1
  local svc_timeout=${2:-120}
  printf "    %-20s " "$svc"
  while ! podman inspect --format='{{.State.Health.Status}}' "$svc" 2>/dev/null | grep -q healthy; do
    sleep 2
    svc_timeout=$((svc_timeout - 2))
    if [[ $svc_timeout -le 0 ]]; then
      echo "TIMEOUT"
      echo "ERROR: $svc did not become healthy within the timeout"
      exit 1
    fi
  done
  echo "healthy"
}

# The LGTM images (Loki, Tempo, Mimir, OTel Collector) are distroless — they
# carry no shell, wget or curl, so a container healthcheck cannot run inside
# them. Poll their HTTP readiness endpoints from the host instead.
wait_ready() {
  local svc=$1
  local url=$2
  local svc_timeout=${3:-120}
  printf "    %-20s " "$svc"
  while ! curl -sf -m 3 -o /dev/null "$url"; do
    if ! podman inspect --format='{{.State.Status}}' "$svc" 2>/dev/null | grep -q running; then
      echo "EXITED"
      echo "ERROR: $svc is not running. Logs:"
      podman logs --tail 20 "$svc" 2>&1 | sed 's/^/      /'
      exit 1
    fi
    sleep 2
    svc_timeout=$((svc_timeout - 2))
    if [[ $svc_timeout -le 0 ]]; then
      echo "TIMEOUT"
      echo "ERROR: $svc did not become ready within the timeout"
      podman logs --tail 20 "$svc" 2>&1 | sed 's/^/      /'
      exit 1
    fi
  done
  echo "ready"
}

wait_healthy eip-kafka    120
wait_healthy eip-redis    120
wait_healthy eip-postgres 120
wait_healthy eip-apicurio 120
wait_healthy eip-pulsar   180

echo "==> Base stack ready."
echo "    Kafka UI:    http://localhost:8090"
echo "    Pulsar Admin: http://localhost:8080"
echo "    PostgreSQL:   psql -h localhost -U eipuser -d eipdb"
echo "    Apicurio:     http://localhost:8081"
echo "    Redis:        redis-cli -h localhost"

if $LGTM; then
  echo ""
  echo "==> Starting LGTM observability stack..."
  podman-compose -p eip -f "$INFRA_DIR/compose.yaml" -f "$INFRA_DIR/compose.lgtm.yaml" up -d

  echo "==> Waiting for LGTM services..."
  wait_ready eip-loki           http://localhost:3100/ready        120
  wait_ready eip-tempo          http://localhost:3200/ready        120
  wait_ready eip-mimir          http://localhost:9009/ready        150
  wait_ready eip-otel-collector http://localhost:13133/            60
  wait_healthy eip-grafana      120

  echo "==> LGTM stack ready."
  echo "    Grafana:        http://localhost:3000"
  echo "    Loki:           http://localhost:3100"
  echo "    Tempo:          http://localhost:3200"
  echo "    Mimir:          http://localhost:9009"
  echo "    OTel Collector: localhost:4317 (gRPC) / localhost:4318 (HTTP)"
fi

echo ""
echo "==> All services up. Run your Camel examples against this stack."
