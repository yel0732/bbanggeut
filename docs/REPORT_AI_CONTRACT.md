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
  "promptVersion": "daily-report-v3"
}
```

`dailyStory`는 보호자에게 전하는 서술이며 모든 문장을 `-요`로 끝낸다. 선생님이 부모에게
알림장을 쓰듯 담백하게 쓰고, 한국어에서 생략되는 주어는 생략한다.

`notableQuotes`와 `emotionDetails`는 아이가 한 말을 그대로 두므로 이 규칙을 적용하지
않는다. 같은 표현이 `dailyStory`에도 나올 때는 원문을 옮기지 않고 `-요`체로 다시 쓴다.

`speaker`가 `AI`인 발화는 아이의 답이 어떤 맥락에서 나왔는지 파악하는 데만 쓴다.
AI가 한 공감·요약·질문은 서술의 근거가 아니며 그대로 옮겨 적지 않는다.

AI 서버는 생성 결과가 이 규칙을 지키는지 검사하고, 어긋나면 같은 입력으로 다시 생성한다.
`maxAttempts`를 모두 쓰고도 어긋나면 503으로 응답해 백엔드가 결정론적 요약으로 넘어간다.

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
