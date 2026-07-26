# Dialogue package

아이의 답변에서 일기 슬롯을 수집하고, 다음 질문을 결정해 Spring에 반환하는
대화 도메인입니다.

## 구조

```text
dialogue/
├── pipeline.py       # 한 턴의 전체 흐름을 조율하는 진입점
├── schemas.py        # 대화 도메인 요청과 슬롯 계약
├── analysis/         # 답변 관련성 판단, 슬롯 추출·보강·병합
├── question/         # 질문 생성, 검증, 폴백, 아이 질문에 대한 짧은 응답
├── policy/           # 다음 슬롯 선택, 무응답 복구, 종료 조건
└── integration/      # GMS 같은 외부 LLM 연동 어댑터
```

`app.dialogue`의 공개 export는 기존 호출부와의 호환성을 위해 유지합니다.
새 내부 코드는 책임이 드러나는 하위 패키지에서 직접 import합니다.

## 한 턴의 흐름

```text
Spring 요청
  → pipeline
  → analysis: 아이 답변 해석 및 슬롯 갱신
  → policy: 종료 여부와 다음 질문 대상 결정
  → question: 일반 목표는 규칙 템플릿, 아이 질문은 짧은 LLM 응답 후 기존 질문 복귀
  → Spring 응답
```

질문 생성·검증·Fallback 컴포넌트는 `question/`에 보존하지만 현재 일반 질문
경로에서는 `build_fallback_question()`의 검증된 템플릿을 정상 질문으로 사용합니다.
`NO_RESPONSE`와 `STOP_REQUESTED` 정책은 `policy/`에 있으나 현재 Text 요청은 모든
입력을 `ANSWERED`로 처리합니다. `NO_RESPONSE`는 Text 채팅에서 판정하지 않고
DIARY-2 WebRTC/VAD·STT 입력 경계에서 전달합니다.

의존 방향은 `pipeline → analysis/question/policy/integration`을 기준으로 합니다.
하위 패키지에서 `pipeline`을 import하지 않습니다.
