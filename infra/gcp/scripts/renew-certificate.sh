#!/usr/bin/env bash
set -Eeuo pipefail

cd "$(dirname "$0")/../../.."
ROOT="$(pwd)"
COMPOSE=(docker compose --env-file /etc/gole/infra.env --env-file /etc/gole/gole.env -f "$ROOT/infra/gcp/docker-compose.yml")

"${COMPOSE[@]}" --profile certificate run --rm certbot renew --quiet
"${COMPOSE[@]}" exec nginx nginx -t
"${COMPOSE[@]}" exec nginx nginx -s reload

