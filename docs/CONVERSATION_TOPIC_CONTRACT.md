# 대화 주제 계약

이 문서는 주제 전환 규칙을 빠르게 확인하기 위한 요약이다. 전체 외부·내부 API
필드는 `docs/diary/API_CONTRACT.md`, DB 필드는 `docs/diary/DATA_MODEL.md`를
기준으로 한다.

## 세션 생성

```http
POST /api/v1/children/{childId}/conversations
```

요청 본문은 보내지 않는다. Spring이 세션 생성 시점의 KST 날짜를 기준으로 완료된 게임 기록을 조회하고 대화 주제를 결정한다.

- 완료 게임 없음: `DAILY`
- 완료 게임 1개 이상: `GAME` → `DAILY`
- 같은 날의 게임이 여러 개여도 `GAME` 주제는 하나이며, 모든 게임 세션을 생성 시점의 컨텍스트로 고정한다.
- 주제별 최대 질문 수는 프론트 입력이 아니라 서버 설정으로 관리한다.

기본 설정:

```yaml
conversation:
  topic:
    game-max-question-count: 6
    daily-max-question-count: 6
```

## 질문 수

질문 수는 AI가 아이에게 전달한 질문을 기준으로 주제별 집계한다.

- 아이의 답변 자체는 질문 수를 증가시키지 않는다.
- `GAME`에서 `DAILY`로 전환하면 `DAILY.questionCount`는 `0`에서 시작한다.
- 전환 응답에 포함된 첫 `DAILY` 질문을 저장하면 `DAILY.questionCount`는 `1`이 된다.
- 세션의 `totalQuestionCount`는 모든 주제 질문 수의 합계다.

## 주제 상태

```text
PENDING → IN_PROGRESS → COMPLETED
```

`ConversationSession.currentTopic`이 현재 진행 중인 주제를 가리킨다. 턴에는 `topicType`을 저장하여 GAME 슬롯과 DAILY 슬롯 및 질문 이력이 섞이지 않도록 한다.

## Spring ↔ FastAPI 결정

- `CONTINUE_TOPIC`: 현재 주제에서 질문을 계속한다.
- `SWITCH_TOPIC`: 현재 주제를 완료하고 다음 주제를 시작한다.
- `TERMINATE_SESSION`: 마지막 주제와 전체 세션을 완료한다.

`SWITCH_TOPIC` 응답의 AI 턴은 다음 주제에 속하며, 해당 주제의 첫 질문으로 계산한다.

### 전환 슬롯 인계

`SWITCH_TOPIC` 응답은 `nextTopicSlots`를 함께 보낼 수 있다. 아이가 전환을 유발한
답변에서 이미 다음 주제의 내용을 말했다면 그 내용을 추출해 담은 슬롯이다.
예를 들어 게임 이야기를 그만하자며 "오늘 할머니 생일이었어"라고 답하면 그 사건이
`DAILY` 슬롯으로 넘어간다.

- `SWITCH_TOPIC`이 아닌 결정에는 포함하지 않는다. FastAPI가 계약 위반으로 거절한다.
- 넘길 내용이 없으면 `null`이거나 모든 필드가 빈 슬롯이다.
- Spring은 이 값을 다음 주제 `slotsJson`의 초기값으로 저장한다. 저장하지 않으면
  아이가 이미 답한 내용을 첫 질문에서 다시 묻게 된다.
- 슬롯이 채워지면 전환 질문의 `assistantTarget`은 `event`가 아닐 수 있다.
  남은 목표를 계산한 결과이므로 응답이 준 값을 그대로 저장한다.

## 질문 목표 저장

AI 질문은 문장과 함께 정책이 선택한 질문 목표를 저장한다.

- FastAPI 요청: `currentTarget`
- FastAPI 응답: `assistantTarget`
- `conversation_turn.question_target`: 해당 AI 턴 질문의 목표
- Realtime Stream: 기존 `targetSlot` 필드를 같은 값으로 사용
- Context Snapshot: `topics[].currentTarget`

새로 생성하는 목표의 허용 값은 `event`, `emotion`, `emotionReason`,
`memorableMoment`다. 기존 WebRTC 이벤트의 `place` 같은 레거시 `targetSlot`은
이력 보존을 위해 저장할 수 있지만 FastAPI 요청과 Context Snapshot에서는 `null`로
정규화한다. 목표가 `null`이면 현재 슬롯으로 추정하는 하위 호환 경로를 사용하며,
새로 생성되는 오프닝·일반·주제 전환 질문은 목표를 명시한다.

현재 필수 질문 목표는 `event`, `emotion`, `emotionReason` 3개다.
`memorableMoment`는 선택 목표이며, 필수 목표가 채워지고 해당 주제의 질문 수가
남아 있을 때 자연스러운 마무리 질문으로 한 번만 사용할 수 있다.

## 수동 종료

사용자가 종료하면 다음 주제로 넘어가지 않는다. 현재 주제와 전체 세션을 동일한 종료 사유와 시각으로 완료한다. 아직 시작하지 않은 다음 주제는 `PENDING`으로 남는다.
