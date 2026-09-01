# GoLe GCP budget relay

Tiny, dependency-free pull subscriber for Google Cloud Billing Budget Pub/Sub
notifications. It posts Korean cost/projection messages to the existing
operations Discord webhook and persists delivery state in `/state`.

## Required environment

| Variable | Example | Meaning |
|---|---|---|
| `GCP_BUDGET_PUBSUB_SUBSCRIPTION` | `gole-billing-budget-discord` | Short name or full `projects/.../subscriptions/...` resource |
| `DISCORD_OPERATIONS_WEBHOOK_URL` | secret | Existing GoLe operations-channel webhook |
| `GCP_CREDIT_AMOUNT_KRW` | `400000` | Budget period 시작 시점의 promotional credit ceiling |
| `GCP_CREDIT_DEADLINE` | `2026-10-28` | Credit expiration date; a date counts through 23:59:59 KST |
| `GCP_FIXED_HOURLY_COST_KRW` | `231.249894200` | Fixed e2-custom-4-8192 + 100 GiB pd-balanced hourly projection |

Optional variables:

- `GCP_PROJECT_ID`: needed only when a short subscription name is used and the
  metadata project ID should not be used.
- `GCP_BUDGET_WARNING_THRESHOLDS`: comma-separated ratios; defaults to
  `0.50,0.75,0.90,1.00`.
- `BUDGET_STATE_DIR`: defaults to `/state`.
- `BUDGET_TIMEZONE_OFFSET_HOURS`: defaults to `9` (KST).
- `PUBSUB_PULL_MAX_MESSAGES`, `PUBSUB_PULL_INTERVAL_SECONDS`,
  `BUDGET_HTTP_TIMEOUT_SECONDS`: pull/runtime tuning.

The VM's attached service account must have
`pubsub.subscriber` on the subscription and its Compute Engine access scope
must include `cloud-platform`. The container calls the metadata server for the
default service account token, so no service-account key is stored.

## Delivery behavior

- Sends at most one daily status per budget period, plus one notification per
  configured actual/forecast threshold.
- Processes the newest item first and suppresses stale out-of-order alerts.
- Persists message IDs, threshold delivery, and daily delivery under `/state`.
- Writes state after a successful Discord response and before Pub/Sub ACK. An
  ACK failure therefore redelivers a message but does not repost it.
- Malformed budget payloads are logged without their contents and ACKed to
  avoid a permanent poison-message loop.
- The Discord webhook URL is never included in application logs.

There is still an unavoidable crash-sized window between Discord accepting a
message and local state being written; Discord webhooks do not expose an
idempotency key. Pub/Sub redelivery in that exact window may duplicate one
alert.

## Local checks

```sh
python -m unittest discover -s tests -v
docker build -t gole-budget-relay .
```
