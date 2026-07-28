"""Tests for stateless T09-T14 dialogue orchestration."""

import json
import logging
from collections import deque

import pytest

from app.dialogue import DialogueTopic, DiarySlots, QuestionTarget
from app.dialogue.pipeline import (
    DialogueTurnDecision,
    DialogueTurnPipeline,
    DialogueTurnRequest,
)
from app.dialogue.question.fallback import (
    QuestionGenerationExecutionPolicy,
    QuestionGenerationFailureCode,
    QuestionGenerationSource,
    QuestionGenerationStatus,
)
from app.dialogue.policy.termination import DialogueTerminationPolicy


class FakeClient:
    def __init__(self, outputs) -> None:
        self.outputs = deque(outputs)
        self.call_count = 0
        self.calls = []

    async def complete(self, messages):
        self.call_count += 1
        self.calls.append(messages)
        output = self.outputs.popleft()
        if isinstance(output, BaseException):
            raise output
        return output


def _pipeline(client: FakeClient) -> DialogueTurnPipeline:
    return DialogueTurnPipeline(
        analysis_client=client,
        response_client=client,
        execution_policy=QuestionGenerationExecutionPolicy(1, 2),
        termination_policy=DialogueTerminationPolicy(
            required_targets=frozenset(QuestionTarget)
        ),
        logger=logging.getLogger("test.pipeline"),
    )


def _pipeline_with_clients(
    analysis_client: FakeClient,
    response_client: FakeClient,
) -> DialogueTurnPipeline:
    return DialogueTurnPipeline(
        analysis_client=analysis_client,
        response_client=response_client,
        execution_policy=QuestionGenerationExecutionPolicy(1, 2),
        termination_policy=DialogueTerminationPolicy(
            required_targets=frozenset(QuestionTarget)
        ),
        logger=logging.getLogger("test.pipeline"),
    )


def _request() -> DialogueTurnRequest:
    return DialogueTurnRequest(
        request_id="request-1",
        conversation_id="conversation-1",
        child_answer="합성 답변",
        current_question=None,
        current_slots=DiarySlots(),
        question_count=0,
        max_question_count=6,
    )


@pytest.mark.asyncio
async def test_first_turn_uses_child_text_when_slot_analysis_fails() -> None:
    client = FakeClient(
        [
            RuntimeError("temporary analysis failure"),
            "그때 어떤 기분이었어?",
            '{"valid":true,"issues":[]}',
        ]
    )
    request = _request().model_copy(
        update={"child_answer": "오늘 친구랑 영화를 봤어"}
    )

    result = await _pipeline(client).process(request)

    assert result.decision is DialogueTurnDecision.CONTINUE
    assert result.updated_slots.event == "오늘 친구랑 영화를 봤어"
    assert result.updated_slots.people == ["친구"]
    assert result.assistant_message == (
        "오늘 친구랑 영화를 봤구나. "
        "그때 마음이 어땠는지 말해 줄래?"
    )
    assert result.assistant_question == "그때 마음이 어땠는지 말해 줄래?"


@pytest.mark.asyncio
async def test_stt_confidence_reaches_the_slot_analysis_payload() -> None:
    """실시간 경로가 준 sttConfidence가 분석 프롬프트의 user JSON까지 닿는다."""

    client = FakeClient(
        [
            (
                '{"event":"블록 놀이","people":[],"place":null,'
                '"emotion":null,"emotionReason":null,'
                '"memorableMoment":null}'
            ),
            "그때 어떤 기분이었어?",
            '{"valid":true,"issues":[]}',
        ]
    )
    request = _request().model_copy(update={"stt_confidence": 0.52})

    await _pipeline(client).process(request)

    analysis_user = next(
        message for message in client.calls[0] if message.role == "user"
    )
    assert json.loads(analysis_user.content)["sttConfidence"] == 0.52


@pytest.mark.asyncio
async def test_text_turn_leaves_stt_confidence_null_in_the_payload() -> None:
    client = FakeClient(
        [
            (
                '{"event":"블록 놀이","people":[],"place":null,'
                '"emotion":null,"emotionReason":null,'
                '"memorableMoment":null}'
            ),
            "그때 어떤 기분이었어?",
            '{"valid":true,"issues":[]}',
        ]
    )

    await _pipeline(client).process(_request())

    analysis_user = next(
        message for message in client.calls[0] if message.role == "user"
    )
    assert json.loads(analysis_user.content)["sttConfidence"] is None


@pytest.mark.asyncio
async def test_updates_slots_and_generates_next_question() -> None:
    client = FakeClient(
        [
            (
                '{"event":"블록 놀이","people":[],"place":null,'
                '"emotion":null,"emotionReason":null,'
                '"memorableMoment":null}'
            ),
            "그때 어떤 기분이었어?",
            '{"valid":true,"issues":[]}',
        ]
    )

    result = await _pipeline(client).process(_request())

    assert result.decision is DialogueTurnDecision.CONTINUE
    assert result.updated_slots.event == "블록 놀이"
    assert result.assistant_question == "그때 마음이 어땠는지 말해 줄래?"
    assert result.assistant_message == (
        "합성 답변구나. 그때 마음이 어땠는지 말해 줄래?"
    )
    assert result.question_generation_status is (
        QuestionGenerationStatus.TEMPLATE_USED
    )
    assert result.question_generation_source is (
        QuestionGenerationSource.RULE_BASED_TEMPLATE
    )
    assert client.call_count == 1


@pytest.mark.asyncio
async def test_first_turn_projects_misclassified_future_slot_to_event() -> None:
    client = FakeClient(
        [
            (
                '{"event":null,"people":[],"place":null,'
                '"emotion":null,"emotionReason":null,'
                '"memorableMoment":"오늘 선생님이랑 영화를 봤어",'
                '"memorableMoment":null}'
            ),
        ]
    )
    request = _request().model_copy(
        update={"child_answer": "오늘 선생님이랑 영화를 봤어"}
    )

    result = await _pipeline(client).process(request)

    assert result.updated_slots.event == "오늘 선생님이랑 영화를 봤어"
    assert result.updated_slots.people == ["선생님"]
    assert result.updated_slots.memorable_moment is None
    assert result.assistant_question == "그때 마음이 어땠는지 말해 줄래?"


@pytest.mark.asyncio
async def test_first_turn_preserves_previously_filled_slots() -> None:
    """직전 질문이 없는 턴이라도 요청에 이미 있던 슬롯 값은 지우지 않는다."""

    client = FakeClient(
        [
            (
                '{"event":"블록 놀이","people":[],"place":null,'
                '"emotion":null,"emotionReason":null,'
                '"memorableMoment":null}'
            ),
            "그때 어떤 기분이었어?",
            '{"valid":true,"issues":[]}',
        ]
    )
    request = _request().model_copy(
        update={
            "current_slots": DiarySlots(
                emotion_reason="친구가 같이 놀아줘서",
                memorable_moment="블록으로 성 만든 순간",
            ),
        }
    )

    result = await _pipeline(client).process(request)

    assert result.updated_slots.event == "블록 놀이"
    assert result.updated_slots.emotion_reason == "친구가 같이 놀아줘서"
    assert result.updated_slots.memorable_moment == "블록으로 성 만든 순간"


@pytest.mark.asyncio
async def test_first_turn_still_discards_misclassified_extraction() -> None:
    """첫 턴 추출이 미래 슬롯으로 오분류한 값은 기존대로 버린다."""

    client = FakeClient(
        [
            (
                '{"event":null,"people":[],"place":null,'
                '"emotion":null,"emotionReason":null,'
                '"memorableMoment":"오늘 영화를 봤어"}'
            ),
            "그때 어떤 기분이었어?",
            '{"valid":true,"issues":[]}',
        ]
    )
    request = _request().model_copy(update={"child_answer": "오늘 영화를 봤어"})

    result = await _pipeline(client).process(request)

    assert result.updated_slots.event == "오늘 영화를 봤어"
    assert result.updated_slots.memorable_moment is None


@pytest.mark.asyncio
async def test_standard_question_does_not_call_response_model() -> None:
    analysis_client = FakeClient(
        [
            (
                '{"event":"블록 놀이","people":[],"place":null,'
                '"emotion":null,"emotionReason":null,'
                '"memorableMoment":null}'
            ),
            '{"valid":true,"issues":[]}',
        ]
    )
    response_client = FakeClient(["그때 어떤 기분이었어?"])

    result = await _pipeline_with_clients(
        analysis_client,
        response_client,
    ).process(_request())

    assert result.assistant_question == "그때 마음이 어땠는지 말해 줄래?"
    assert result.assistant_message == (
        "합성 답변구나. 그때 마음이 어땠는지 말해 줄래?"
    )
    assert analysis_client.call_count == 1
    assert response_client.call_count == 0


@pytest.mark.asyncio
async def test_redirects_off_topic_answer_with_brief_acknowledgement() -> None:
    client = FakeClient(
        [
            (
                '{"event":null,"people":[],"place":null,'
                '"emotion":"배가 고파","emotionReason":null,'
                '"memorableMoment":null}'
            ),
            (
                '{"answered":false,"childQuestion":false,'
                '"bridgeResponse":"배가 많이 고픈가 보구나."}'
            ),
        ]
    )
    request = _request().model_copy(
        update={
            "current_slots": DiarySlots(event="친구와 공놀이"),
            "current_question": "그때 어떤 기분이었어?",
            "child_answer": "그런데 지금은 배가 고파.",
        }
    )

    result = await _pipeline(client).process(request)

    relevance_payload = json.loads(client.calls[1][1].content)
    assert result.decision is DialogueTurnDecision.CONTINUE
    assert result.assistant_message == (
        "배가 많이 고픈가 보구나! 그때 어떤 기분이었어?"
    )
    assert result.assistant_question == "그때 어떤 기분이었어?"
    assert result.updated_slots.emotion is None
    assert result.assistant_message.count("?") == 1
    assert "그나저나" not in result.assistant_message
    assert len(result.assistant_message) <= 90
    assert relevance_payload["currentQuestion"] == "그때 어떤 기분이었어?"
    assert relevance_payload["childAnswer"] == "그런데 지금은 배가 고파."
    assert client.call_count == 2


@pytest.mark.asyncio
async def test_skipped_target_is_excluded_from_backfill_and_next_question() -> None:
    """건너뛴 목표는 답변 턴에서도 제외된다 — 오배정·재질문 방지."""

    client = FakeClient(
        [
            (
                '{"event":null,"people":[],"place":null,'
                '"emotion":null,"emotionReason":null,'
                '"memorableMoment":null}'
            ),
            '{"answered":true,"childQuestion":false,"bridgeResponse":null}',
        ]
    )
    request = _request().model_copy(
        update={
            "current_slots": DiarySlots(event="과일 게임"),
            "current_question": "왜 그런 마음이 들었는지 말해 줄래?",
            "child_answer": "이겨서 기분이 좋았거든",
            "skipped_targets": frozenset({QuestionTarget.EMOTION}),
        }
    )

    result = await _pipeline(client).process(request)

    assert result.decision is DialogueTurnDecision.CONTINUE
    # backfill은 skip을 반영한 실제 질문 목표(emotionReason)에 들어가야 하며,
    # skip된 emotion에 오배정되면 안 된다.
    assert result.updated_slots.emotion_reason == "이겨서 기분이 좋았거든"
    assert result.updated_slots.emotion is None
    # 다음 질문도 skip된 emotion을 다시 묻지 않고 남은 목표로 진행한다.
    assert result.assistant_question == "가장 기억나는 순간을 말해 줄래?"


@pytest.mark.asyncio
async def test_relevance_failure_keeps_extracted_slots_and_moves_on() -> None:
    """관련성 판정이 실패(None)해도 정상 추출된 슬롯은 폐기되지 않는다."""

    client = FakeClient(
        [
            (
                '{"event":null,"people":[],"place":null,'
                '"emotion":"즐거웠어","emotionReason":null,'
                '"memorableMoment":null}'
            ),
            RuntimeError("relevance classification unavailable"),
        ]
    )
    request = _request().model_copy(
        update={
            "current_slots": DiarySlots(event="친구와 공놀이"),
            "current_question": "그때 어떤 기분이었어?",
            "child_answer": "정말 즐거웠어!",
        }
    )

    result = await _pipeline(client).process(request)

    assert result.decision is DialogueTurnDecision.CONTINUE
    assert result.updated_slots.emotion == "즐거웠어"
    # 추출로 목표 슬롯이 채워졌으므로 같은 질문을 되묻지 않고 다음 목표로 진행한다.
    assert result.assistant_question == "왜 그런 마음이 들었는지 말해 줄래?"


@pytest.mark.asyncio
async def test_briefly_answers_child_question_before_redirecting() -> None:
    client = FakeClient(
        [
            (
                '{"event":null,"people":[],"place":null,'
                '"emotion":null,"emotionReason":null,'
                '"memorableMoment":null}'
            ),
            (
                '{"answered":false,"childQuestion":true,"bridgeResponse":'
                '"나는 티라노사우루스가 멋진 것 같아."}'
            ),
            "나는 트리케라톱스가 든든해 보여서 좋아!",
        ]
    )
    request = _request().model_copy(
        update={
            "current_slots": DiarySlots(event="친구와 공놀이"),
            "current_question": "친구와 공놀이할 때 마음이 어땠어?",
            "child_answer": "너는 어떤 공룡을 좋아해?",
        }
    )

    result = await _pipeline(client).process(request)

    assert result.assistant_message == (
        "나는 트리케라톱스가 든든해 보여서 좋아! "
        "아까 이야기로 돌아가서, "
        "친구와 공놀이할 때 마음이 어땠어?"
    )
    assert result.assistant_question == (
        "친구와 공놀이할 때 마음이 어땠어?"
    )
    assert result.updated_slots == request.current_slots
    assert result.question_generation_status is (
        QuestionGenerationStatus.GENERATED
    )
    assert result.question_generation_source is QuestionGenerationSource.LLM
    assert client.call_count == 3


@pytest.mark.asyncio
async def test_child_question_uses_safe_reply_without_question_fallback() -> None:
    client = FakeClient(
        [
            (
                '{"event":null,"people":[],"place":null,'
                '"emotion":null,"emotionReason":null,'
                '"memorableMoment":null}'
            ),
            (
                '{"answered":false,"childQuestion":true,'
                '"bridgeResponse":"영화 봤을 때 기분을 말해줘."}'
            ),
            "영화 봤을 때 기분을 말해줘.",
            "그때 마음이 어땠는지 말해 줄래?",
        ]
    )
    request = _request().model_copy(
        update={
            "current_slots": DiarySlots(event="친구와 영화 보기"),
            "current_question": "친구랑 영화 봤을 때 기분이 어땠어?",
            "child_answer": "너는 어떤 영화 좋아해?",
        }
    )

    result = await _pipeline(client).process(request)

    assert result.assistant_message == (
        "나는 그 질문에는 딱 하나를 고르기 어려워! "
        "아까 이야기로 돌아가서, "
        "친구랑 영화 봤을 때 기분이 어땠어?"
    )
    assert result.assistant_question == (
        "친구랑 영화 봤을 때 기분이 어땠어?"
    )
    assert result.question_generation_status is (
        QuestionGenerationStatus.FALLBACK_USED
    )
    assert result.question_generation_source is (
        QuestionGenerationSource.RULE_BASED_FALLBACK
    )
    assert result.question_generation_failure_code is (
        QuestionGenerationFailureCode.GENERATED_QUESTION_REJECTED
    )
    assert client.call_count == 4


@pytest.mark.asyncio
async def test_does_not_redirect_when_answer_fills_current_target() -> None:
    client = FakeClient(
        [
            (
                '{"event":null,"people":[],"place":null,'
                '"emotion":"신났어","emotionReason":null,'
                '"memorableMoment":null}'
            ),
            (
                '{"answered":true,"childQuestion":false,'
                '"bridgeResponse":null}'
            ),
            "왜 신났는지 말해 줄래?",
            '{"valid":true,"issues":[]}',
        ]
    )
    request = _request().model_copy(
        update={
            "current_slots": DiarySlots(event="친구와 공놀이"),
            "current_question": "그때 어떤 기분이었어?",
            "child_answer": "정말 신났어.",
        }
    )

    result = await _pipeline(client).process(request)

    assert result.decision is DialogueTurnDecision.CONTINUE
    assert result.updated_slots.emotion == "신났어"
    assert result.assistant_question == "왜 그런 마음이 들었는지 말해 줄래?"
    assert result.assistant_message == (
        "정말 신났구나. 왜 그런 마음이 들었는지 말해 줄래?"
    )


@pytest.mark.asyncio
async def test_backfills_answered_target_when_slot_extraction_misses_it() -> None:
    client = FakeClient(
        [
            (
                '{"event":null,"people":[],"place":null,'
                '"emotion":null,"emotionReason":null,'
                '"memorableMoment":null}'
            ),
            (
                '{"answered":true,"childQuestion":false,'
                '"bridgeResponse":null}'
            ),
            "가장 기억나는 순간은 언제였어?",
            '{"valid":true,"issues":[]}',
        ]
    )
    request = _request().model_copy(
        update={
            "current_slots": DiarySlots(
                event="친구와 영화 보기",
                emotion="좋았음",
            ),
            "current_question": "왜 그런 마음이 들었는지 말해 줄래?",
            "child_answer": "영화가 재미있었어",
        }
    )

    result = await _pipeline(client).process(request)

    assert result.updated_slots.emotion_reason == "영화가 재미있었어"
    assert result.assistant_message == (
        "영화가 재미있었구나. 가장 기억나는 순간을 말해 줄래?"
    )
    assert result.assistant_question == "가장 기억나는 순간을 말해 줄래?"
    assert client.call_count == 2


@pytest.mark.asyncio
async def test_reason_answer_does_not_overwrite_existing_emotion() -> None:
    client = FakeClient(
        [
            (
                '{"event":null,"people":[],"place":null,'
                '"emotion":"감동적이었어","emotionReason":null,'
                '"memorableMoment":null}'
            ),
            (
                '{"answered":true,"childQuestion":false,'
                '"bridgeResponse":null}'
            ),
            "가장 기억나는 순간은 언제였어?",
            '{"valid":true,"issues":[]}',
        ]
    )
    request = _request().model_copy(
        update={
            "current_slots": DiarySlots(
                event="선생님과 영화 보기",
                people=["선생님"],
                emotion="슬펐어",
            ),
            "current_question": "왜 그런 마음이 들었어?",
            "child_answer": "영화가 감동적이었어",
        }
    )

    result = await _pipeline(client).process(request)

    assert result.updated_slots.emotion == "슬펐어"
    assert result.updated_slots.emotion_reason == "영화가 감동적이었어"
    assert result.assistant_message == (
        "영화가 감동적이었구나. 가장 기억나는 순간을 말해 줄래?"
    )
    assert result.assistant_question == "가장 기억나는 순간을 말해 줄래?"


@pytest.mark.asyncio
async def test_recognizes_child_question_without_question_mark() -> None:
    client = FakeClient(
        [
            (
                '{"event":null,"people":[],"place":null,'
                '"emotion":null,"emotionReason":null,'
                '"memorableMoment":null}'
            ),
            (
                '{"answered":false,"childQuestion":false,'
                '"bridgeResponse":"영화가 궁금하구나."}'
            ),
            "나는 재미있는 모험 영화를 좋아해!",
        ]
    )
    request = _request().model_copy(
        update={
            "current_slots": DiarySlots(
                event="선생님과 영화 보기",
                people=["선생님"],
                emotion="슬펐어",
                emotion_reason="영화가 감동적이었어",
            ),
            "current_question": "가장 기억나는 순간은 언제였어?",
            "child_answer": "너는 어떤 영화를 좋아해",
        }
    )

    result = await _pipeline(client).process(request)

    assert result.updated_slots == request.current_slots
    assert result.assistant_message == (
        "나는 재미있는 모험 영화를 좋아해! "
        "아까 이야기로 돌아가서, 가장 기억나는 순간은 언제였어?"
    )
    assert result.assistant_question == "가장 기억나는 순간은 언제였어?"
    assert client.call_count == 3


@pytest.mark.asyncio
async def test_terminates_without_question_when_required_slots_are_filled() -> None:
    client = FakeClient(
        [
            (
                '{"event":null,"people":[],"place":null,'
                '"emotion":null,"emotionReason":null,'
                '"memorableMoment":"함께 정리했어"}'
            ),
            (
                '{"answered":true,"childQuestion":false,'
                '"bridgeResponse":null}'
            ),
        ]
    )
    request = _request().model_copy(
        update={
            "current_slots": DiarySlots(
                event="놀이",
                emotion="기쁨",
                emotion_reason="함께해서",
            ),
            "current_question": "마지막에는 어떻게 됐어?",
            "child_answer": "함께 정리했어",
        }
    )

    result = await _pipeline(client).process(request)

    assert result.decision is DialogueTurnDecision.TERMINATE
    assert result.termination_reason is not None
    assert result.assistant_question is None
    assert result.assistant_message is None
    assert client.call_count == 2


@pytest.mark.asyncio
async def test_terminates_at_question_limit_after_slot_analysis() -> None:
    client = FakeClient(
        [
            (
                '{"event":"놀이","people":[],"place":null,'
                '"emotion":null,"emotionReason":null,'
                '"memorableMoment":null}'
            )
        ]
    )
    request = _request().model_copy(
        update={"question_count": 6},
    )

    result = await _pipeline(client).process(request)

    assert result.decision is DialogueTurnDecision.TERMINATE
    assert result.termination_reason is not None
    assert client.call_count == 1


@pytest.mark.asyncio
async def test_switches_from_game_to_daily_at_topic_limit() -> None:
    client = FakeClient(
        [
            (
                '{"event":"게임","people":[],"place":null,'
                '"emotion":null,"emotionReason":null,'
                '"memorableMoment":null}'
            )
        ]
    )
    request = _request().model_copy(
        update={
            "current_topic": DialogueTopic.GAME,
            "next_topic": DialogueTopic.DAILY,
            "question_count": 6,
        }
    )

    result = await _pipeline(client).process(request)

    assert result.decision is DialogueTurnDecision.SWITCH_TOPIC
    assert result.termination_reason is not None
    assert result.assistant_question == (
        "이번에는 오늘 있었던 다른 일도 이야기해 줄래?"
    )
    assert result.assistant_message == (
        "게임 이야기를 들려줘서 고마워! "
        "이번에는 오늘 있었던 다른 일도 이야기해 줄래?"
    )
