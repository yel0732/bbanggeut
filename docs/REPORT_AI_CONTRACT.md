# Daily Report AI Contract

`POST /internal/v1/reports/daily-summary` is an internal backend-to-AI-server
API. It accepts validated structured facts and a bounded set of completed DAILY
conversation turns. Access tokens, child names, and provider credentials are prohibited.

Request fields use camelCase:

```json
{
  "reportDate": "2026-07-27",
  "events": ["친구와 축구함"],
  "emotions": ["즐거움"],
  "interests": ["축구"],
  "conversations": [
    {"speaker": "CHILD", "content": "친구와 축구해서 즐거웠어."},
    {"speaker": "AI", "content": "어떤 순간이 가장 즐거웠어?"}
  ],
  "games": [
    {"gameName": "도블", "playDurationSeconds": 180, "score": 3500}
  ]
}
```

Successful response:

```json
{
  "title": "즐거운 축구 이야기",
  "dailyStory": "오늘은 친구와 축구한 이야기를 즐겁게 나누었어요.",
  "emotionDetails": [{"emotion": "즐거움", "reason": "친구와 축구해서"}],
  "interests": ["축구"],
  "notableQuotes": ["친구와 축구해서 즐거웠어."],
  "conversationGuide": {
    "description": "친구와 함께한 축구 경험에 관심을 보여주세요.",
    "questions": ["어떤 순간이 가장 즐거웠어?"]
  },
  "modelVersion": "gpt-5-mini",
  "promptVersion": "daily-report-v2"
}
```

The AI may edit expression only. The backend preserves the original event,
emotion, interest, and game snapshots as authoritative facts. Provider failure,
timeout, malformed JSON, or contract violation returns HTTP 503; the backend
then uses its deterministic fallback. A no-activity report does not call this
API and does not create a completion notification.

Runtime configuration:

```text
GMS_KEY=<set directly in the runtime secret store>
GMS_REPORT_MODEL=gpt-5-mini
```

The real key must never be committed to the repository or included in logs.
