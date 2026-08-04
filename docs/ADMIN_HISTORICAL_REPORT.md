# Administrator historical report generation

The administrator endpoint creates a report for a past date using the same
generation pipeline as the automatic report job.

## Promote an existing account

Create the account through the normal signup flow, deploy migration V14, and
then promote exactly that account in the production database:

```sql
UPDATE parent
SET role = 'ADMIN'
WHERE email = 'admin@example.com';
```

Log in again after promotion. Access tokens issued before promotion retain the
`PARENT` role and cannot call administrator endpoints.

## Generate a past report

```http
POST /api/admin/children/{childId}/reports/{reportDate}/generate
Authorization: Bearer {adminAccessToken}
```

The date must be earlier than today in the child's parent's configured time
zone. The source period ends five minutes before that parent's configured
delivery time and starts 24 hours earlier. At least one completed game or one
reportable completed conversation must exist in that period.

The endpoint returns `202 Accepted`. A duplicate child/date report returns
`409`, a date that is not in the past returns `400`, and a period without
reportable activity returns `404`.

Every accepted request is recorded in `admin_report_generation_audit` with the
administrator ID, child ID, report ID, and report date.
