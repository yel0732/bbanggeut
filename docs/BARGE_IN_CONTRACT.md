# Barge-in 계약

아이의 음성이 AI 발화와 겹칠 때 현재 AI 응답을 중단하고 아이 발화를 같은 대화의
다음 턴으로 처리하는 계약이다.

## 자동 감지

- 브라우저는 `echoCancellation`, `noiseSuppression`, `autoGainControl`을 활성화한다.
- FastAPI는 AI 오디오 출력 중에도 VAD를 계속 실행한다.
- 평상시 발화 시작 기준은 `STT_MIN_SPEECH_MS`, AI 출력 중 기준은
  `STT_BARGE_IN_MIN_SPEECH_MS`다. 기본값은 각각 250ms, 400ms다.
- Barge-in으로 확정된 발화는 pre-roll을 포함해 버리지 않고 다음 아이 턴의 STT
  입력으로 사용한다.
- AI 출력 중 침묵은 아이의 무응답 횟수로 계산하지 않는다.

## DataChannel 이벤트

FastAPI에서 React로 보내는 중단 이벤트:

```json
{
  "type": "assistant.audio.interrupted",
  "turnId": "turn_...",
  "turnSeq": 2,
  "responseId": "resp_...",
  "payload": {
    "reason": "CHILD_BARGE_IN",
    "interruptionId": "int_..."
  }
}
```

`reason`은 자동 음성 감지의 `CHILD_BARGE_IN` 또는 명시적 중단의
`USER_REQUESTED`다.

React가 명시적으로 AI 발화를 중단할 때 보내는 이벤트:

```json
{
  "type": "client.interrupt",
  "payload": {
    "reason": "USER_REQUESTED",
    "responseId": "resp_..."
  }
}
```

`responseId`가 현재 재생 중인 응답과 다르면 아무 응답도 중단하지 않는다.

## Redis Stream 이벤트

FastAPI는 `conversation-events`에 다음 확정 이벤트를 발행한다.

```json
{
  "eventType": "AI_TURN_INTERRUPTED",
  "turnId": "turn_...",
  "turnSeq": 2,
  "responseId": "resp_...",
  "payload": {
    "interruptedAt": "2026-08-05T00:00:00Z",
    "reason": "CHILD_BARGE_IN",
    "interruptionId": "int_..."
  }
}
```

Spring은 `AI_TURN_GENERATED`로 생성된 AI 턴을 `INTERRUPTED`로 전환한다. 완료와
중단이 경쟁하면 먼저 반영된 terminal 상태를 유지하고, 동일 중단 이벤트의 재전달은
멱등하게 처리한다. 재연결 전 이미 확정된 중단 이벤트는 낮은 `sessionEpoch`라도
보존한다.

## 취소 순서

```text
현재 responseId 취소 표시
→ 해당 Response Audio Queue flush
→ TTS Provider Task 취소
→ assistant.audio.interrupted 발행
→ AI_TURN_INTERRUPTED 발행
→ 아이 발화 수집과 Final STT 계속
```

취소된 응답의 늦은 Audio chunk와 완료 이벤트는 폐기한다. 원본 음성이나 Partial STT는
Redis Stream 또는 MySQL에 저장하지 않는다.
