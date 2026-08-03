# Child Diary Backend Contract

## Flow

1. Generate a preview from one completed conversation.
2. Show the fixed title `오늘의 일기` and three to five lines to the child.
3. Save only when the child selects `내 일기장에 붙이기`.
4. Do nothing when the child closes or skips the preview.

Preview data is not inserted into the database. The backend returns a signed,
short-lived preview token so the saved text is exactly the text shown on screen.

## APIs

### Preview

```http
POST /api/v1/children/{childId}/conversations/{conversationId}/child-diary/preview
```

```json
{
  "conversationId": "conversation-123",
  "diaryDate": "2026-07-27",
  "title": "오늘의 일기",
  "lines": ["...", "...", "..."],
  "previewToken": "signed-value"
}
```

### Attach to diary book

```http
POST /api/v1/children/{childId}/child-diaries
Content-Type: application/json

{"previewToken":"signed-value"}
```

### Query

```http
GET /api/v1/children/{childId}/child-diaries?page=0&size=20
GET /api/v1/children/{childId}/child-diaries/{diaryId}
```

## Rules

- The caller must own the child profile.
- The conversation must belong to that child and have `COMPLETED` status.
- Only non-empty validated `GAME` and/or `DAILY` slot values are sent to AI.
- The AI response must keep the fixed title, contain three to five unique lines,
  and keep every line at 60 characters or fewer.
- When both topics exist, the result has three to five lines total with at least
  one line per topic, in GAME then DAILY order.
- AI timeout, malformed output, or provider failure uses a rule-based fallback.
- A unique database constraint on `conversation_id` prevents duplicate diaries,
  including concurrent attach requests.

## Production configuration

```text
AI_SERVER_BASE_URL=http://ai-server:8000
CHILD_DIARY_AI_TIMEOUT=PT12S
CHILD_DIARY_PREVIEW_SECRET=<random secret of at least 32 characters>
CHILD_DIARY_PREVIEW_TTL=PT10M
```

`CHILD_DIARY_PREVIEW_SECRET` must be managed as a runtime secret and must not be
committed. When omitted, it falls back to `JWT_SECRET`; setting a separate secret
is recommended so the two credentials can be rotated independently.

Production deployment must apply the `child_diary` DDL in `backend/SCHEMA`
before starting the application because production uses `ddl-auto: validate`.
