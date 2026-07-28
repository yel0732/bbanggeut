# AI Server Conventions

FastAPI AI Realtime 서버를 개발할 때 적용하는 공통 규칙입니다.

이 문서는 Codex와 Claude를 포함한 모든 작업자가 `ai-server/`를 같은 구조와
책임으로 구현하도록 만드는 기준입니다. 제품 요구사항과 통신 계약을 대체하지
않으며, 구체적인 동작은 현재 Jira Task와 DIARY 문서를 우선 확인합니다.

## 1. 적용 범위와 기준 문서

적용 범위:

```text
ai-server/
```

작업 전에 다음 순서로 확인합니다.

```text
1. AGENTS.md 또는 CLAUDE.md
2. docs/AI_CONVENTIONS.md
3. docs/diary/README.md
4. 현재 Jira Epic과 Task
5. docs/diary/REQUIREMENTS.md
6. docs/diary/FUNCTIONAL_SPEC.md
7. API·Realtime Event·Data Model 계약
8. 기존 ai-server 코드와 테스트
```

문서가 충돌하면 다음 기준을 적용합니다.

- 개인정보·인증·시스템 소유권은 `ARCHITECTURE.md`와 Accepted ADR을 우선합니다.
- REST, DataChannel, Redis Stream Payload는 해당 계약 문서를 우선합니다.
- 현재 구현 범위는 Jira Task를 우선합니다.
- 코드 스타일과 패키지 배치는 이 문서를 따릅니다.
- 제품 동작이나 계약 충돌은 임의로 해석하지 않고 작업을 멈춘 뒤 보고합니다.

## 2. 기술 스택

- Python 3.12
- FastAPI
- Pydantic v2
- asyncio
- pytest
- aiortc
- Redis

현재 Task에 필요하지 않은 라이브러리는 미리 추가하지 않습니다. Provider SDK,
`pytest-asyncio`, Ruff 같은 도구도 실제 사용 코드나 검증 명령과 함께 추가합니다.

## 3. 시스템 책임

현재 DIARY-1 FastAPI 책임:

- 상태 비저장 Text Dialogue 내부 API
- 일기 슬롯 분석과 기존 슬롯 병합
- 답변 관련성·아이 질문 판정
- 규칙 기반 다음 목표·일반 질문·종료 판단
- 아이 질문에 대한 짧은 LLM 응답
- Timeout·제한적 재시도와 오류 매핑

DIARY-2 이후 FastAPI AI Realtime 서버의 책임:

- Realtime Ticket 검증과 원자적 일회용 소비
- WebSocket Signaling
- 세션별 PeerConnection과 Audio Track 관리
- Streaming STT, VAD, Turn Detection
- 일기 슬롯 분석과 Dialogue Manager
- 검증된 질문 생성 정책과 규칙 기반 Fallback
- Streaming TTS와 AI Audio Queue
- Barge-in 검증과 진행 중 Response 취소
- DataChannel 이벤트 송수신
- 확정된 대화 이벤트의 Redis Stream 발행

FastAPI가 담당하지 않는 책임:

- 사용자 Access Token 검증
- 부모·아이 프로필 CRUD
- MySQL 직접 접근
- 최종 업무 세션 상태의 단독 확정
- 원본 음성·영상 장기 저장
- React UI 상태 관리

금지:

```text
FastAPI → MySQL
React Access Token → FastAPI
Partial STT → 영속 저장
Raw Audio → 파일/Object Storage 저장
FastAPI 내부 객체 → Spring 직접 호출 또는 공유
```

Spring과 FastAPI는 서로 다른 서비스입니다. 같은 작업자가 양쪽을 구현하더라도
DB Entity, 내부 클래스, in-process 호출로 결합하지 않고 REST·Redis·Event 계약만
사용합니다.

## 4. 프로젝트 구조

기능 단위로 먼저 나누고, 필요한 파일만 만듭니다.

```text
ai-server/
├── app/
│   ├── main.py                 # 앱 조립과 lifecycle 등록만
│   ├── config.py               # 환경변수 기반 설정이 생길 때 추가
│   ├── signaling/              # WebSocket 인증, SDP·ICE 메시지
│   ├── webrtc/                 # PeerConnection, Audio Track
│   ├── session/                # 활성 세션 상태, Registry, Cleanup
│   ├── speech/                 # Audio 변환, STT, TTS
│   ├── dialogue/               # 슬롯, Parser, 정책, Prompt, Validator
│   ├── interruption/           # Barge-in 상태와 취소 조정
│   └── events/                 # DataChannel·Redis Stream 계약과 Producer
└── tests/
    ├── signaling/
    ├── webrtc/
    ├── session/
    ├── speech/
    ├── dialogue/
    ├── interruption/
    └── events/
```

규칙:

- 아직 구현하지 않는 기능의 빈 폴더를 미리 만들지 않습니다.
- 테스트 폴더는 `app/` 구조를 따라갑니다.
- `main.py`는 객체 생성, 의존성 연결, FastAPI lifecycle 등록만 담당합니다.
- WebSocket Handler와 HTTP Endpoint에 대화 정책이나 Provider 로직을 두지 않습니다.
- 여러 모듈에서 실제로 사용하는 코드만 공통 위치로 올립니다.
- `utils.py`, `helpers.py`, `common.py`에 의미가 다른 코드를 모으지 않습니다.
- 순환 import를 만들지 않습니다. Transport는 orchestration을 호출하고,
  orchestration은 capability/adapter를 호출하는 방향을 유지합니다.

권장 의존 방향:

```text
signaling / webrtc transport
→ session lifecycle / pipeline orchestration
→ dialogue / speech / interruption capability
→ provider adapter

events
→ 외부 계약 검증·직렬화 경계
```

`dialogue/`의 순수 정책과 Schema는 WebRTC, Redis, FastAPI 객체를 import하지 않습니다.

## 5. Python 코드 스타일

### 기본 형식

- UTF-8, LF, 파일 끝 newline을 사용합니다.
- 들여쓰기는 공백 4칸입니다.
- 한 줄은 기본 100자 이내로 작성합니다.
- 문자열은 자동 포맷터 기준에 맞춰 큰따옴표를 기본으로 합니다.
- 공개 함수와 복잡한 내부 함수에는 타입 힌트를 작성합니다.
- `Any`는 외부 라이브러리 경계처럼 불가피한 곳에만 사용하고 즉시 검증된 타입으로
  변환합니다.
- 와일드카드 import와 상대경로 상위 import를 사용하지 않습니다.
- 모듈 import 시 네트워크 연결, 환경변수 검증, Task 실행 같은 부작용을 만들지 않습니다.

### 네이밍

| 대상 | 규칙 | 예시 |
|---|---|---|
| 모듈·패키지 | `snake_case` | `turn_detector.py` |
| 클래스·예외 | `PascalCase` | `DialogueManager` |
| 함수·변수 | `snake_case` | `select_next_target` |
| 상수 | `UPPER_SNAKE_CASE` | `MAX_RETRY_COUNT` |
| 비공개 구현 | 앞에 `_` | `_discard_late_event` |
| 비동기 함수 | 동작 이름 사용 | `consume_audio`, `close_session` |

`Async`, `Impl`, `Manager`, `Service`, `Util` 같은 접미사는 책임을 실제로 설명할 때만
사용합니다. 비동기 함수라는 이유만으로 클래스명에 `Async`를 붙이지 않습니다.

### 함수와 객체

- 함수 하나는 한 가지 이유로 변경되도록 작게 유지합니다.
- boolean 인자가 여러 개 필요하면 명시적인 모델이나 Enum을 사용합니다.
- 상태 변경은 `start_response()`, `interrupt_response()`, `close()`처럼 의도가 드러나는
  메서드에 모읍니다.
- 외부에서 변경하면 안 되는 값은 불변 모델 또는 읽기 전용 인터페이스로 전달합니다.
- 주석은 코드가 무엇을 하는지 반복하지 않고, 취소 순서나 계약 선택의 이유를 설명합니다.

## 6. Pydantic과 계약 모델

- 외부 입력, Context Snapshot, DataChannel Event, Redis Stream Event는 Pydantic 모델로
  경계에서 검증합니다.
- 내부 Python 이름은 `snake_case`, 외부 JSON 이름은 계약의 `camelCase`를 사용합니다.
- alias를 명시하고 외부 직렬화는 `model_dump(mode="json", by_alias=True)`를 사용합니다.
- 계약 모델은 기본적으로 `extra="forbid"`로 정의되지 않은 필드를 거부합니다.
- mutable 기본값은 직접 선언하지 않고 `Field(default_factory=list)`처럼 factory를 사용합니다.
- 외부 Payload를 `dict[str, Any]` 상태로 내부 로직 깊숙이 전달하지 않습니다.
- Provider SDK 응답 모델을 애플리케이션 계약으로 그대로 노출하지 않습니다.
- Enum은 계약에서 허용 값이 고정된 경우에만 사용합니다.
- 식별자는 내부에서 재해석하지 않는 opaque `str`로 취급합니다.

JSON 계약 변경 시:

- REST 변경: `docs/diary/API_CONTRACT.md`
- WebSocket·DataChannel·Redis Stream 변경:
  `docs/diary/REALTIME_EVENT_CONTRACT.md`
- Redis Key·Context Snapshot 구조 변경: `docs/diary/DATA_MODEL.md`

계약 문서를 먼저 갱신하거나 같은 변경에서 함께 갱신합니다.

## 7. 시간과 식별자

- naive `datetime`을 만들거나 외부로 보내지 않습니다.
- 이벤트 시각은 timezone-aware ISO 8601 문자열로 직렬화합니다.
- 정확한 UTC/KST offset은 기존 계약을 따르며, 새 계약은 팀 합의 없이 임의로 정하지 않습니다.
- Timeout, 지연시간, 침묵 구간 측정에는 wall clock이 아니라 monotonic clock을 사용합니다.
- 시간 의존 로직은 테스트에서 교체할 수 있도록 Clock 함수 또는 의존성으로 주입합니다.

모든 실시간 처리에서 다음 식별자를 명시적으로 유지합니다.

```text
conversationId
realtimeSessionId
sessionEpoch
turnId / turnSeq
responseId
eventId / eventSeq
interruptionId
```

- 연결 경계의 DataChannel 이벤트는 재연결 후 낮은 `sessionEpoch`이면 폐기합니다.
- Redis Stream 영속화 이벤트는 재연결 전에 이미 확정된 Turn을 예외로 보존합니다
  (`docs/diary/REALTIME_EVENT_CONTRACT.md` §7.3).
- 취소된 `responseId`의 Text, Audio, 완료 이벤트를 모두 폐기합니다.
- 이벤트 재전송을 고려해 `eventId` 기반 처리는 멱등해야 합니다.
- `turnSeq`와 `eventSeq`는 같은 개념으로 사용하지 않습니다.

## 8. asyncio와 세션 격리

실시간 상태는 반드시 세션별로 분리합니다.

세션 상태 예시:

```text
Context Snapshot
PeerConnection
STT Buffer
현재 turnId·turnSeq
현재 responseId
LLM·TTS Task
Audio Queue
취소된 responseId
마지막 eventSeq
Cleanup 상태
```

규칙:

- 변경 가능한 세션 상태를 모듈 전역 dict에 직접 노출하지 않습니다.
- Registry 또는 Session 객체가 생성·조회·정리 책임을 소유합니다.
- 세션 간 Audio Buffer, Queue, Task, 식별자를 공유하지 않습니다.
- 동시 상태 전이가 가능한 곳은 `asyncio.Lock` 또는 단일 소유 Task로 직렬화합니다.
- Queue는 무제한으로 만들지 않고 최대 크기와 backpressure 정책을 둡니다.
- `asyncio.create_task()`를 흩어 쓰지 않고 세션이 Task 참조와 이름을 관리합니다.
- 취소 요청 후 Task를 `cancel()`만 하고 버리지 말고 종료를 await합니다.
- Cleanup은 여러 번 호출해도 안전한 멱등 연산이어야 합니다.
- 연결 종료 시 Heartbeat, STT Stream, LLM, TTS, Audio Queue, PeerConnection을 모두 정리합니다.
- blocking SDK를 event loop에서 직접 호출하지 않습니다. 비동기 SDK 또는 제한된 executor를
  사용합니다.

취소 불가능한 Provider 결과는 기다렸다가 사용하지 말고 `responseId`와 `sessionEpoch`를 다시
검증해 폐기합니다.

## 9. Dialogue Manager와 LLM

LLM은 정책 결정자가 아닙니다. 현재 Text MVP의 일반 질문은 결정된 목표에 대응하는
검증된 규칙 기반 템플릿으로 표현합니다. LLM은 슬롯 분석, 답변 관련성 판정,
아이가 AI에게 한 질문의 짧은 응답에 사용합니다.

처리 순서:

```text
아이 Text 또는 Final STT
→ LLM 슬롯 추출 결과 검증
→ 기존 슬롯과 명시적으로 확인된 값만 병합
→ 현재 질문 답변 관련성 판정
→ 규칙 기반 다음 목표 선택
→ 규칙 기반 일반 질문 템플릿 선택
→ 아이가 AI에게 질문한 경우에만 짧은 LLM 응답 후 기존 질문 복귀
```

질문 생성 Prompt, Semantic Validator, Timeout·Fallback 모듈은 재사용 가능한
컴포넌트와 단위 테스트로 유지합니다. 일반 LLM 질문 생성 경로를 다시 연결할 때는
대화 품질 회귀 테스트를 먼저 통과해야 합니다.

### 슬롯

- 미확인 단일 슬롯은 `None`, 미확인 목록은 빈 배열로 유지합니다.
- 아이가 말하지 않은 사건, 사람, 장소, 감정을 추측해 채우지 않습니다.
- Parser 결과에 없는 값으로 기존 슬롯을 지우지 않습니다.
- 감정 표현을 임의로 진단명이나 고정 분류로 바꾸지 않습니다.
- 필수 슬롯은 DEC-03이 확정되기 전까지 하드코딩하지 않습니다.

### 질문

- 한 번에 질문 하나만 생성합니다.
- 짧고 쉬운 한국어를 사용합니다.
- 추궁, 평가, 유도, 복수 질문을 금지합니다.
- 이미 채워진 슬롯이나 직전에 답한 내용을 반복 질문하지 않습니다.
- 아이의 종료 의사를 최대 질문 수보다 우선 존중합니다.
- 진단, 성격 단정, 감정 확정 표현을 생성하지 않습니다.

### Prompt와 Parser

- Prompt, 출력 Schema, Parser, 정책을 한 함수에 섞지 않습니다.
- Prompt 변경은 별도 상수 또는 template 파일로 관리하고 테스트 가능한 입력을 갖게 합니다.
- Prompt에 전체 과거 원문 대신 필요한 최소 Context만 포함합니다.
- 구조화 출력은 반드시 Pydantic 검증 후 사용합니다.
- 비정상 JSON을 문자열 치환으로 무리하게 복구하지 않습니다.
- 재시도 횟수는 제한하고, 같은 잘못된 요청을 무한 반복하지 않습니다.
- Prompt/Provider를 바꿔도 deterministic 정책 테스트가 영향을 받지 않게 분리합니다.

Character 이름, 말투, 진단·정서 해석 정책처럼 제품 성격을 바꾸는 Prompt 변경은 사용자 또는
팀의 명시적 결정을 받습니다.

## 10. Provider 연동

STT, LLM, TTS Provider는 애플리케이션 로직과 분리합니다.

- 실제 구현체가 둘 이상이거나 테스트 대체가 필요한 시점에 `Protocol` 또는 추상 경계를 둡니다.
- Provider SDK 타입, 오류, Stream 객체를 다른 모듈로 전파하지 않습니다.
- Provider 오류를 STT/LLM/TTS별 내부 오류로 변환합니다.
- 모든 외부 호출에 Timeout과 취소 경로를 둡니다.
- 재시도는 일시적 오류에만 제한적으로 적용합니다.
- 재시도 횟수와 Timeout은 환경설정으로 관리하고 코드 여러 곳에 하드코딩하지 않습니다.
- Provider 교체가 Dialogue, Session, WebRTC 코드를 수정하게 만들지 않습니다.
- Unit Test에서 실제 Provider나 네트워크를 호출하지 않습니다.

API Key, Private Key, Ticket은 환경변수로만 주입합니다. `.env.example`에는 변수 이름과 안전한
예시만 두고 실제 Secret을 넣지 않습니다.

## 11. Audio·STT·TTS

- 원본 Audio를 파일, DB, 로그에 저장하지 않습니다.
- 테스트 Audio는 합성 신호나 개인정보가 없는 짧은 fixture만 사용합니다.
- Audio format은 sample rate, channel, sample width를 타입 또는 명시적 모델로 함께 전달합니다.
- 변환 단계마다 format 가정을 숨기지 않습니다.
- Partial STT는 UI용 이벤트로만 전송하고 Redis Stream에 발행하지 않습니다.
- Final STT는 턴당 한 번만 확정하고 같은 Final Event로 질문을 중복 생성하지 않습니다.
- TTS Audio Queue는 `responseId`별로 구분합니다.
- First Audio 지연은 monotonic clock으로 측정합니다.
- 중단된 Response의 Queue는 flush하고 늦게 도착한 chunk도 폐기합니다.

## 12. Event와 Redis

- DataChannel은 `conversation-events`, `ordered: true` 계약을 유지합니다.
- 공통 Envelope를 이벤트마다 임의로 축약하거나 필드 이름을 바꾸지 않습니다.
- Payload별 Pydantic 모델과 Envelope 모델을 분리합니다.
- 외부에서 받은 이벤트는 식별자·epoch·sequence·취소 상태 검증 후 처리합니다.
- Redis Stream은 최소 한 번 전달될 수 있다고 가정합니다.
- 확정된 상태만 Redis Stream에 발행합니다.
- 발행 성공을 MySQL 저장 완료로 간주하지 않습니다.
- Redis 장애 시 이벤트를 조용히 버리지 않고 Task 계약에 맞는 실패·복구 상태로 전환합니다.
- 전체 Transcript나 Context Snapshot을 Redis key 이름 또는 로그 필드에 포함하지 않습니다.

## 13. 오류 처리와 로깅

오류는 다음 범주를 구분합니다.

```text
계약 검증 오류
인증·Ticket 오류
세션·Epoch 오류
Provider Timeout·실패
취소
복구 가능한 실시간 오류
복구 불가능한 세션 오류
```

- `except Exception: pass`를 사용하지 않습니다.
- 취소를 일반 Provider 실패로 기록하지 않습니다.
- 클라이언트 오류 메시지에 Stack Trace, SDK 응답, Secret을 포함하지 않습니다.
- 복구 가능한 오류인지 명시적으로 구분합니다.
- 로그는 구조화된 식별자와 상태 전이를 중심으로 남깁니다.

허용 로그 예시:

```text
conversation_id
realtime_session_id
session_epoch
turn_id
response_id
event_id
provider 이름
오류 코드
처리 시간
```

기본적으로 로그에 남기지 않는 값:

```text
Access Token / Realtime Ticket / API Key
아이 이름·생년월일
전체 STT 원문
전체 Prompt·LLM 응답
Context Snapshot 전체
SDP 전문
Audio bytes
```

문제 분석에 텍스트가 꼭 필요하면 실제 사용자 데이터가 아닌 로컬 합성 fixture로 재현합니다.

## 14. 테스트

테스트는 pytest를 사용하고 `tests/`가 `app/` 구조를 따라가게 합니다.

### Unit Test

- 기본 테스트 종류입니다.
- 네트워크, Redis, WebRTC Provider를 Fake 또는 Mock으로 대체합니다.
- 슬롯 병합, 목표 선택, Validator, 상태 전이, 이벤트 폐기 규칙을 deterministic하게 검증합니다.

### Integration Test

- Redis, WebSocket, aiortc 또는 여러 모듈 연결이 실제로 필요할 때만 작성합니다.
- 외부 Provider는 기본적으로 Fake를 사용합니다.
- 실제 Provider 테스트는 명시적인 marker와 환경변수가 있을 때만 실행합니다.

### E2E Test

- Epic의 전 구간 Acceptance Criteria를 검증하는 Task에서만 작성합니다.
- 실제 아이 음성이나 일기 원문을 fixture로 사용하지 않습니다.
- 세션 종료 후 Task·Queue·Connection이 남지 않는지도 확인합니다.

테스트 규칙:

- async 코드가 생기면 `pytest-asyncio`를 추가하고 async test marker를 명시합니다.
- `sleep()`으로 타이밍을 맞추는 테스트를 피하고 Event, Queue, 주입 Clock을 사용합니다.
- Timeout, 취소, 중복, 지연, 낮은 epoch, 잘못된 sequence를 실패 경로로 검증합니다.
- 랜덤 ID가 필요하면 생성기를 주입하거나 결과 형식만 검증합니다.
- 테스트 간 전역 상태와 이벤트 루프 상태를 공유하지 않습니다.
- Cleanup 누락 경고를 테스트 실패로 취급합니다.

기본 검증:

```powershell
cd ai-server
.\.venv\Scripts\python.exe -m pytest
.\.venv\Scripts\python.exe -m compileall -q app tests
```

Ruff를 도입한 이후에는 다음 검증을 필수로 추가합니다.

```powershell
.\.venv\Scripts\python.exe -m ruff format --check .
.\.venv\Scripts\python.exe -m ruff check .
```

## 15. 설정과 의존성

- 설정값은 한 설정 객체에서 환경변수로 읽습니다.
- 모듈 곳곳에서 `os.getenv()`를 직접 호출하지 않습니다.
- 설정 객체를 import-time 전역 Singleton으로 강제하지 않고 앱 조립 단계에서 주입합니다.
- Timeout, Queue 크기, VAD 기준, Lease 주기는 의미 있는 이름의 설정으로 관리합니다.
- 민감하지 않은 개발 기본값만 코드에 둘 수 있습니다.
- 새 의존성은 사용 이유, 사용 위치, 검증 명령이 함께 드러나야 합니다.
- 호환 가능한 major 범위를 명시하고, 배포 단계에서는 별도 lock 전략을 확정합니다.
- 사용하지 않는 Provider SDK나 미래용 프레임워크를 미리 추가하지 않습니다.

## 16. 문서와 Task 범위

- 한 브랜치와 Merge Request는 Jira Task 하나에 대응합니다.
- Task 범위 밖 모듈을 함께 구현하지 않습니다.
- 다른 시스템 변경이 필요하면 계약 변경과 담당 파일을 먼저 제시합니다.
- API·Event·Redis Schema 변경은 관련 계약 문서와 같은 변경에 포함합니다.
- 구현 후 계획 문서에 실제 변경 파일, 테스트 결과, 남은 연동 사항을 기록합니다.

커밋은 목적별로 나눕니다.

```text
chore: AI 서버 프로젝트 설정
feat: 일기 정보 슬롯 Schema 구현
feat: 다음 질문 목표 선택 정책 구현
fix: 취소된 responseId 지연 이벤트 폐기
test: Dialogue Manager 정책 테스트 추가
docs: AI 이벤트 계약 갱신
```

## 17. AI 코딩 에이전트 작업 규칙

Codex와 Claude는 다음 규칙을 공통으로 따릅니다.

- 작업 전 `git status --short --branch`를 확인합니다.
- 기존 변경과 `.env`, `.venv`, 사용자 설정 파일을 되돌리거나 커밋하지 않습니다.
- 현재 Task와 관련 문서를 먼저 읽고 변경 예정 파일을 제시합니다.
- 구현은 `ai-server/` 안에서 진행합니다.
- 계약 변경이 아니면 Spring·Frontend 파일을 수정하지 않습니다.
- 테스트가 실패하면 원인을 해결하거나 미검증 상태를 명확히 보고합니다.
- Secret, 실제 아동 데이터, 전체 대화 원문을 코드·fixture·로그에 작성하지 않습니다.
- 패키지를 추가하면 이유와 실제 사용 위치를 설명합니다.
- 범위 밖 리팩터링과 미래 기능용 추상화를 만들지 않습니다.

## 18. 완료 체크리스트

```text
[ ] 현재 Jira Task 범위만 구현했다.
[ ] 시스템 책임과 서비스 경계를 지켰다.
[ ] 계약 모델과 문서가 일치한다.
[ ] 세션별 상태와 비동기 Task가 격리된다.
[ ] Timeout·취소·지연 결과 처리 경로가 있다.
[ ] 민감 데이터가 저장·로그·fixture에 포함되지 않는다.
[ ] Unit Test와 필요한 Integration Test가 통과한다.
[ ] Compile·Lint·Format 검증이 통과한다.
[ ] Cleanup 후 Task·Queue·Connection이 남지 않는다.
[ ] 변경 파일과 남은 위험을 보고했다.
```
