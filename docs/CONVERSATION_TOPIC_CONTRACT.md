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

## 수동 종료

사용자가 종료하면 다음 주제로 넘어가지 않는다. 현재 주제와 전체 세션을 동일한 종료 사유와 시각으로 완료한다. 아직 시작하지 않은 다음 주제는 `PENDING`으로 남는다.
