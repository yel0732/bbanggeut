# Report Operations

## Required production configuration

The `prod` profile enables the daily report schedule and recovery worker by
default. Override these values only during a controlled maintenance window.

| Environment variable | Default | Purpose |
| --- | --- | --- |
| `REPORT_SCHEDULE_ENABLED` | `true` | Runs the 19:00 KST candidate batch. |
| `REPORT_DAILY_CRON` | `0 0 19 * * *` | Daily candidate batch cron. |
| `REPORT_RETRY_MAX_ATTEMPTS` | `3` | Attempts per generation execution. |
| `REPORT_RETRY_INTERVAL` | `PT5S` | Delay between attempts. |
| `REPORT_RECOVERY_ENABLED` | `true` | Re-dispatches pending and interrupted work. |
| `REPORT_RECOVERY_INTERVAL` | `PT1M` | Recovery scan interval. |
| `REPORT_STALE_PROCESSING_THRESHOLD` | `PT10M` | Age after which processing is treated as interrupted. |
| `REPORT_RECOVERY_BATCH_SIZE` | `100` | Maximum reports recovered per scan. |
| `REPORT_AI_ENABLED` | `true` | Uses AI to edit validated activity facts into a narrative. |
| `AI_SERVER_BASE_URL` | `http://127.0.0.1:8000` | Internal FastAPI server URL. |
| `REPORT_AI_TIMEOUT` | `PT12S` | Backend timeout for one report AI request. |

`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `GOOGLE_CLIENT_ID`, and
`CORS_ALLOWED_ORIGINS` are also mandatory for the `prod` profile.

## Child ownership schema contract

Production report authorization and notification delivery use the authoritative
`child` table through `JdbcReportChildOwnershipAdapter`. The child domain schema
must provide these columns:

```sql
child.child_id   BIGINT PRIMARY KEY
child.parent_id  BIGINT NOT NULL
child.deleted_at DATETIME(6) NULL
```

An active child must have exactly one parent owner. Soft-deleted children are
excluded from report access and notification delivery. Deployment must stop if
the actual child-domain migration uses different table or column names; update
the adapter and this contract together rather than inferring ownership from
game, conversation, or report rows.

## Database deployment

1. Apply all application DDL in `SCHEMA` to MySQL.
2. Apply Spring Batch's matching `schema-mysql.sql` before application startup.
3. Start the application with `ddl-auto=validate` and stop deployment if schema
   validation fails.
4. Confirm the index `idx_daily_report_status_processing` exists. Recovery
   scans depend on it.

## Runtime flow

The scheduled batch registers one `PENDING` row per child and report date.
Registration and manual regeneration publish an event in the same transaction.
Generation is dispatched only after commit. The recovery worker re-dispatches
`PENDING` rows and resets `PROCESSING` rows older than the configured threshold.

All active children from the authoritative `child` table are registered even
when they have no game or conversation activity in the reporting period. A
no-activity report is completed with no snapshots and does not create a
`REPORT_COMPLETED` notification. Failures still create `REPORT_FAILED`
notifications.

Conversation report text is built deterministically from the validated diary
slots already returned by the AI dialogue server and persisted by Spring. When
report AI is enabled, only those structured facts and game aggregates are sent
to `/internal/v1/reports/daily-summary`; stored child transcripts are never
sent. Invalid, timed-out, or unavailable AI responses automatically use the
deterministic summary and still complete the report.

## Monitoring

Export Actuator metrics to the production monitoring system and alert on:

- `report.generation.failed` increasing continuously;
- `report.generation.duplicate` increasing sharply;
- `PENDING` reports older than five minutes;
- `PROCESSING` reports older than `REPORT_STALE_PROCESSING_THRESHOLD`;
- generation duration exceeding the daily operating window;
- SSE notification failure rows increasing.

Useful database checks:

```sql
SELECT generation_status, COUNT(*)
FROM daily_report
GROUP BY generation_status;

SELECT report_id, child_id, processing_started_at
FROM daily_report
WHERE generation_status = 'PROCESSING'
  AND processing_started_at < UTC_TIMESTAMP(6) - INTERVAL 10 MINUTE;

SELECT notification_type, COUNT(*)
FROM report_notification_failure
GROUP BY notification_type;
```

## Data retention

Daily reports and their immutable source snapshots are product records and must
follow the child-data retention policy. Attempt, notification, and failure rows
are operational history; archive or delete them only after the product owner
defines a retention period and audit requirements. Never delete source report
data merely to clear a failed generation attempt.
