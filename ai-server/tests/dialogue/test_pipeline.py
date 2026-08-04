"""Tests for stateless T09-T14 dialogue orchestration."""

import json
import logging
from collections import deque

import pytest

from app.dialogue import (
    DialogueTopic,
    DialogueTopicHistoryItem,
    DiarySlots,
    QuestionTarget,
)
from app.dialogue.pipeline import (
    CONVERSATION_CLOSING_MESSAGE,
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
from app.dialogue.policy.termination import (
    DialogueTerminationPolicy,
    DialogueTerminationReason,
)


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


def _pipeline_with_required_three(client: FakeClient) -> DialogueTurnPipeline:
    return DialogueTurnPipeline(
        analysis_client=client,
        response_client=client,
        execution_policy=QuestionGenerationExecutionPolicy(1, 2),
        termination_policy=DialogueTerminationPolicy(
            required_targets=frozenset(
                {
                    QuestionTarget.EVENT,
                    QuestionTarget.EMOTION,
                    QuestionTarget.EMOTION_REASON,
                }
            )
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
            "친구랑 영화를 봤구나! 영화를 볼 때 기분이 어땠어?",
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
        "친구랑 영화를 봤구나! 영화를 볼 때 기분이 어땠어?"
    )
    assert result.assistant_question == "영화를 볼 때 기분이 어땠어?"


@pytest.mark.asyncio
async def test_opening_answer_uses_child_text_when_slot_analysis_fails() -> None:
    analysis_client = FakeClient(
        [
            RuntimeError("temporary slot analysis failure"),
            RuntimeError("temporary relevance failure"),
        ]
    )
    response_client = FakeClient(
        [RuntimeError("temporary question generation failure")]
    )
    request = _request().model_copy(
        update={
            "child_answer": "영어학원에서 게임했어",
            "current_question": "오늘 있었던 일 하나를 말해 줄래?",
            "current_target": QuestionTarget.EVENT,
            "question_count": 1,
        }
    )

    result = await _pipeline_with_clients(
        analysis_client,
        response_client,
    ).process(request)

    assert result.decision is DialogueTurnDecision.CONTINUE
    assert result.updated_slots.event == "영어학원에서 게임했어"
    assert result.assistant_target is QuestionTarget.EMOTION
    assert result.question_generation_status is (
        QuestionGenerationStatus.FALLBACK_USED
    )


@pytest.mark.asyncio
async def test_target_answer_uses_child_text_when_slot_analysis_fails() -> None:
    analysis_client = FakeClient(
        [
            RuntimeError("temporary slot analysis failure"),
            RuntimeError("temporary relevance failure"),
        ]
    )
    response_client = FakeClient(
        [RuntimeError("temporary question generation failure")]
    )
    request = _request().model_copy(
        update={
            "child_answer": "좋았어! 재밌었어",
            "current_question": "그때 마음은 어땠어?",
            "current_target": QuestionTarget.EMOTION,
            "current_slots": DiarySlots(event="영어학원에서 파티했어"),
            "question_count": 2,
        }
    )

    result = await _pipeline_with_clients(
        analysis_client,
        response_client,
    ).process(request)

    assert result.decision is DialogueTurnDecision.CONTINUE
    assert result.updated_slots.event == "영어학원에서 파티했어"
    assert result.updated_slots.emotion == "좋았어! 재밌었어"
    assert result.updated_slots.emotion_reason == "영어학원에서 파티했어"
    assert result.assistant_target is QuestionTarget.MEMORABLE_MOMENT
    assert result.question_generation_status is (
        QuestionGenerationStatus.FALLBACK_USED
    )


@pytest.mark.asyncio
async def test_fun_moment_answer_does_not_trigger_emotion_requestion() -> None:
    analysis_client = FakeClient(
        [
            (
                '{"event":null,"people":[],"place":null,'
                '"emotion":null,"emotionReason":null,'
                '"memorableMoment":null}'
            ),
            (
                '{"intent":"ANSWER","answered":true,'
                '"childQuestion":false,"controlAction":"CONTINUE",'
                '"bridgeResponse":null,"suggestedTarget":null,'
                '"evidenceText":null}'
            ),
        ]
    )
    response_client = FakeClient([])
    request = _request().model_copy(
        update={
            "child_answer": "손으로 내가 과일 자르는 거",
            "current_question": (
                "오늘 과일 자르기 했구나! 어떤 순간이 제일 재미있었어?"
            ),
            "current_target": QuestionTarget.MEMORABLE_MOMENT,
            "current_slots": DiarySlots(event="과일 자르기"),
            "question_count": 2,
        }
    )

    result = await _pipeline_with_clients(
        analysis_client,
        response_client,
    ).process(request)

    assert result.decision is DialogueTurnDecision.CONTINUE_TOPIC
    assert result.updated_slots.emotion == "재미있었어"
    assert result.updated_slots.emotion_reason == "손으로 내가 과일 자르는 거"
    assert result.assistant_question == "오늘 또 나한테 해주고 싶은 이야기 있어?"
    assert "기분" not in result.assistant_message


@pytest.mark.asyncio
async def test_continue_request_after_optional_final_does_not_terminate() -> None:
    analysis_client = FakeClient(
        [
            (
                '{"event":null,"people":[],"place":null,'
                '"emotion":null,"emotionReason":null,'
                '"memorableMoment":null}'
            ),
            (
                '{"intent":"CHILD_QUESTION","answered":false,'
                '"childQuestion":true,"controlAction":"CONTINUE",'
                '"bridgeResponse":null,"suggestedTarget":null,'
                '"evidenceText":null}'
            ),
        ]
    )
    response_client = FakeClient([])
    request = _request().model_copy(
        update={
            "child_answer": "왜 마지막이야? 나랑 더 대화하자",
            "current_question": "마지막으로 나한테 하고 싶은 말 있어?",
            "current_target": QuestionTarget.MEMORABLE_MOMENT,
            "current_slots": DiarySlots(
                event="과일 자르기",
                emotion="재미있었어",
                emotionReason="손으로 과일 자르기",
            ),
            "question_count": 3,
            "recent_questions": (
                "오늘 있었던 일 하나를 말해 줄래?",
                "마지막으로 나한테 하고 싶은 말 있어?",
            ),
        }
    )

    result = await _pipeline_with_clients(
        analysis_client,
        response_client,
    ).process(request)

    assert result.decision is DialogueTurnDecision.CONTINUE_TOPIC
    assert result.assistant_question == "마지막으로 나한테 하고 싶은 말 있어?"
    assert "더 말하고 싶었구나" in result.assistant_message


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
            "블록 놀이를 했구나! 놀 때 기분이 어땠어?",
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
            "블록 놀이를 했구나! 놀 때 기분이 어땠어?",
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
            "블록 놀이를 했구나! 놀 때 기분이 어땠어?",
            '{"valid":true,"issues":[]}',
        ]
    )

    result = await _pipeline(client).process(_request())

    assert result.decision is DialogueTurnDecision.CONTINUE
    assert result.updated_slots.event == "블록 놀이"
    assert result.assistant_question == "놀 때 기분이 어땠어?"
    assert result.assistant_message == "블록 놀이를 했구나! 놀 때 기분이 어땠어?"
    assert result.question_generation_status is (
        QuestionGenerationStatus.GENERATED
    )
    assert result.question_generation_source is QuestionGenerationSource.LLM
    assert client.call_count == 2


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
    assert result.assistant_question == "그때 마음은 어땠어?"


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
async def test_standard_question_uses_response_model() -> None:
    analysis_client = FakeClient(
        [
            (
                '{"event":"블록 놀이","people":[],"place":null,'
                '"emotion":null,"emotionReason":null,'
                '"memorableMoment":null}'
            ),
        ]
    )
    response_client = FakeClient(
        [
            "블록 놀이를 했구나! 놀 때 기분이 어땠어?",
            '{"valid":true,"issues":[]}',
        ]
    )

    result = await _pipeline_with_clients(
        analysis_client,
        response_client,
    ).process(_request())

    assert result.assistant_question == "놀 때 기분이 어땠어?"
    assert result.assistant_message == "블록 놀이를 했구나! 놀 때 기분이 어땠어?"
    assert analysis_client.call_count == 1
    assert response_client.call_count == 1


@pytest.mark.asyncio
async def test_structured_generation_builds_full_turn_without_semantic_judge() -> None:
    analysis_client = FakeClient(
        [
            (
                '{"event":"과일을 여러 개 자르기","people":[],"place":null,'
                '"emotion":null,"emotionReason":null,'
                '"memorableMoment":null}'
            ),
        ]
    )
    response_client = FakeClient(
        [
            json.dumps(
                    {
                        "acknowledgement": (
                            "여러 개를 한 번에 자르는 게 재미있었구나!"
                        ),
                        "question": "어떤 점 때문에 그렇게 재미있었어?",
                        "target": "emotionReason",
                        "acknowledgementEvidence": {
                            "source": "childAnswer",
                            "text": "여러 개를 한 번에 자르는 게 재미있었어",
                    },
                },
                ensure_ascii=False,
            )
        ]
    )
    request = _request().model_copy(
        update={
            "child_answer": "여러 개를 한 번에 자르는 게 재미있었어",
            "current_topic": DialogueTopic.GAME,
        }
    )

    result = await _pipeline_with_clients(
        analysis_client,
        response_client,
    ).process(request)

    assert result.assistant_message == (
        "여러 개를 한 번에 자르는 게 재미있었구나! "
        "어떤 점 때문에 그렇게 재미있었어?"
    )
    assert result.assistant_question == "어떤 점 때문에 그렇게 재미있었어?"
    assert result.assistant_target is QuestionTarget.EMOTION_REASON
    assert response_client.call_count == 1


@pytest.mark.asyncio
async def test_standard_question_receives_completed_topic_history() -> None:
    analysis_client = FakeClient(
        [
            (
                '{"event":"수박을 한 번에 자름","people":[],"place":null,'
                '"emotion":"신남","emotionReason":null,'
                '"memorableMoment":null}'
            )
        ]
    )
    response_client = FakeClient(
        [
            "정말 신났구나! 어떤 점 때문에 그렇게 신났어?",
            '{"valid":true,"issues":[]}',
        ]
    )
    history = DialogueTopicHistoryItem(
        assistantMessage="수박을 한 번에 잘랐구나! 기분이 어땠어?",
        assistantQuestion="기분이 어땠어?",
        childAnswer="정말 신났어",
    )
    request = _request().model_copy(
        update={"topic_history": (history,)}
    )

    await _pipeline_with_clients(
        analysis_client,
        response_client,
    ).process(request)

    generation_payload = json.loads(response_client.calls[0][1].content)
    assert generation_payload["topicHistory"] == [
        {
            "assistantMessage": history.assistant_message,
            "assistantQuestion": history.assistant_question,
            "childAnswer": history.child_answer,
        }
    ]


@pytest.mark.asyncio
async def test_analysis_and_relevance_receive_completed_topic_history() -> None:
    client = FakeClient(
        [
            (
                '{"event":null,"people":[],"place":null,'
                '"emotion":"재밌었어","emotionReason":null,'
                '"memorableMoment":null}'
            ),
            (
                '{"intent":"ANSWER","answered":true,'
                '"childQuestion":false,"controlAction":"CONTINUE",'
                '"bridgeResponse":null,"suggestedTarget":null,'
                '"evidenceText":null}'
            ),
        ]
    )
    history = DialogueTopicHistoryItem(
        assistantMessage="과일 자르기 이야기부터 들어볼게.",
        assistantQuestion="어떤 일이 생각나?",
        childAnswer="손으로 막 과일 잘랐어!",
    )
    request = _request().model_copy(
        update={
            "current_slots": DiarySlots(event="손으로 과일 자르기"),
            "current_question": "그때 마음은 어땠어?",
            "current_target": QuestionTarget.EMOTION,
            "child_answer": "너무 재밌었어!",
            "topic_history": (history,),
        }
    )

    result = await _pipeline_with_required_three(client).process(request)

    analysis_payload = json.loads(client.calls[0][1].content)
    relevance_payload = json.loads(client.calls[1][1].content)
    assert analysis_payload["topicHistory"][0]["childAnswer"] == (
        "손으로 막 과일 잘랐어!"
    )
    assert relevance_payload["topicHistory"][0]["childAnswer"] == (
        "손으로 막 과일 잘랐어!"
    )
    assert result.updated_slots.emotion_reason == "손으로 과일 자르기"
    assert result.assistant_question == "오늘 또 나한테 해주고 싶은 이야기 있어?"


@pytest.mark.asyncio
async def test_negative_answer_gets_empathy_before_optional_final() -> None:
    client = FakeClient(
        [
            (
                '{"event":null,"people":[],"place":null,'
                '"emotion":"슬펐어","emotionReason":"친구가 블록을 부숴서",'
                '"memorableMoment":null}'
            ),
            (
                '{"intent":"ANSWER","answered":true,'
                '"childQuestion":false,"controlAction":"CONTINUE",'
                '"bridgeResponse":null,"suggestedTarget":null,'
                '"evidenceText":null}'
            ),
        ]
    )
    request = _request().model_copy(
        update={
            "current_slots": DiarySlots(event="블록으로 성 만들기"),
            "current_question": "그때 마음은 어땠어?",
            "current_target": QuestionTarget.EMOTION,
            "child_answer": "친구가 블록 부숴서 슬펐어",
        }
    )

    result = await _pipeline_with_required_three(client).process(request)

    assert result.assistant_message == (
        "많이 속상했겠다. 오늘 또 나한테 해주고 싶은 이야기 있어?"
    )
    assert "그랬구나" not in result.assistant_message


@pytest.mark.asyncio
async def test_safety_phrase_redirects_without_llm_or_slot_update() -> None:
    client = FakeClient([])
    request = _request().model_copy(
        update={
            "current_question": "그때 마음은 어땠어?",
            "current_target": QuestionTarget.EMOTION,
            "current_slots": DiarySlots(event="친구와 놀기"),
            "child_answer": "죽고 싶어",
        }
    )

    result = await _pipeline_with_required_three(client).process(request)

    assert result.updated_slots == request.current_slots
    assert result.assistant_question == (
        "지금은 오늘 있었던 다른 일을 말해 줄래?"
    )
    assert "죽고 싶어" not in result.assistant_message
    assert client.call_count == 0


@pytest.mark.asyncio
async def test_retries_when_event_is_claimed_as_unanswered_memorable_moment() -> None:
    analysis_client = FakeClient(
        [
            (
                '{"event":null,"people":[],"place":null,'
                '"emotion":"짜릿했어","emotionReason":null,'
                '"memorableMoment":null}'
            ),
            '{"answered":true,"childQuestion":false,"bridgeResponse":null}',
        ]
    )
    response_client = FakeClient(
        [
            (
                "그 여러 개를 한 번에 자른 순간이 가장 기억에 남았구나. "
                "가장 기억에 남는 순간은 언제였어?"
            ),
            "짜릿했구나! 게임에서 어떤 순간이 제일 기억나?",
            '{"valid":true,"issues":[]}',
        ]
    )
    request = DialogueTurnRequest(
        request_id="request-1",
        conversation_id="conversation-1",
        child_answer="짜릿했어",
        current_question="그때 기분은 어땠어?",
        current_topic=DialogueTopic.GAME,
        current_slots=DiarySlots(
            event="과일을 한 번에 여러 개 자르기",
            emotion_reason="여러 개를 한 번에 자르는 게 재미있었어",
        ),
        question_count=2,
        max_question_count=6,
    )

    result = await _pipeline_with_clients(
        analysis_client,
        response_client,
    ).process(request)

    assert result.assistant_message == (
        "짜릿했구나! 게임에서 어떤 순간이 제일 기억나?"
    )
    assert result.updated_slots.emotion == "짜릿했어"
    assert response_client.call_count == 2
    assert "retryIssues" not in json.loads(
        response_client.calls[0][1].content
    )
    assert json.loads(response_client.calls[1][1].content)["retryIssues"] == [
        "bannedExpression"
    ]


@pytest.mark.asyncio
async def test_game_generation_failure_uses_game_specific_fallback() -> None:
    analysis_client = FakeClient(
        [
            (
                '{"event":"과일 자르기","people":[],"place":null,'
                '"emotion":null,"emotionReason":null,'
                '"memorableMoment":null}'
            )
        ]
    )
    response_client = FakeClient(
        [
            RuntimeError("generation unavailable"),
            RuntimeError("generation unavailable"),
        ]
    )
    request = _request().model_copy(
        update={"current_topic": DialogueTopic.GAME}
    )

    result = await _pipeline_with_clients(
        analysis_client,
        response_client,
    ).process(request)

    assert result.assistant_question == "게임할 때 어떤 기분이었어?"
    assert result.assistant_message == (
        "오늘 그런 일이 있었구나. 게임할 때 어떤 기분이었어?"
    )
    assert result.question_generation_status is (
        QuestionGenerationStatus.FALLBACK_USED
    )
    assert result.question_generation_source is (
        QuestionGenerationSource.RULE_BASED_FALLBACK
    )


@pytest.mark.asyncio
async def test_negative_event_fallback_message_uses_empathy() -> None:
    analysis_client = FakeClient(
        [
            (
                '{"event":"놀이터에서 그네를 못 탐","people":["언니"],'
                '"place":"놀이터","emotion":null,"emotionReason":null,'
                '"memorableMoment":null}'
            ),
            (
                '{"answered":true,"childQuestion":false,'
                '"bridgeResponse":null}'
            ),
        ]
    )
    response_client = FakeClient(
        [
            RuntimeError("generation unavailable"),
            RuntimeError("generation unavailable"),
        ]
    )
    request = _request().model_copy(
        update={
            "current_question": "오늘 있었던 일 하나를 말해 줄래?",
            "current_target": QuestionTarget.EVENT,
            "child_answer": (
                "놀이터에서 그네 타고 싶은데 언니가 안 나와서 못탔어"
            ),
        }
    )

    result = await _pipeline_with_clients(
        analysis_client,
        response_client,
    ).process(request)

    assert result.assistant_question == "그때 마음은 어땠어?"
    assert result.assistant_message == (
        "많이 아쉬웠겠다. 그때 마음은 어땠어?"
    )
    assert "잘 말해줬어" not in result.assistant_message
    assert result.question_generation_status is (
        QuestionGenerationStatus.FALLBACK_USED
    )


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
            "배가 고프구나! 친구와 공놀이할 때는 어떤 기분이었어?",
            '{"valid":true,"issues":[]}',
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
        "배가 고프구나! 친구와 공놀이할 때는 어떤 기분이었어?"
    )
    assert result.assistant_question == "친구와 공놀이할 때는 어떤 기분이었어?"
    assert result.updated_slots.emotion is None
    assert result.assistant_message.count("?") == 1
    assert "그나저나" not in result.assistant_message
    assert len(result.assistant_message) <= 90
    assert relevance_payload["currentQuestion"] == "그때 어떤 기분이었어?"
    assert relevance_payload["childAnswer"] == "그런데 지금은 배가 고파."
    assert client.call_count == 4


@pytest.mark.asyncio
async def test_stop_request_terminates_without_llm_calls() -> None:
    """아이의 종료 의사는 슬롯 분석 없이 즉시 CHILD_REQUESTED_STOP으로 종료한다."""

    client = FakeClient([])
    request = _request().model_copy(
        update={
            "current_slots": DiarySlots(event="과일 게임", emotion="신남"),
            "current_question": "왜 그런 마음이 들었는지 말해 줄래?",
            "child_answer": "이제 그만할래",
        }
    )

    result = await _pipeline(client).process(request)

    assert result.decision is DialogueTurnDecision.TERMINATE_SESSION
    assert result.termination_reason is DialogueTerminationReason.CHILD_REQUESTED_STOP
    assert result.updated_slots == request.current_slots
    assert result.closing_message == CONVERSATION_CLOSING_MESSAGE
    assert client.call_count == 0


@pytest.mark.asyncio
async def test_stop_request_never_switches_to_the_next_topic() -> None:
    """다음 주제가 남아 있어도 종료 의사를 전환으로 바꾸지 않는다."""

    client = FakeClient([])
    request = _request().model_copy(
        update={
            "current_topic": DialogueTopic.GAME,
            "next_topic": DialogueTopic.DAILY,
            "current_question": "게임에서 뭐가 재미있었어?",
            "child_answer": "그만하고 싶어",
        }
    )

    result = await _pipeline(client).process(request)

    assert result.decision is DialogueTurnDecision.TERMINATE_SESSION
    assert result.termination_reason is DialogueTerminationReason.CHILD_REQUESTED_STOP
    assert result.assistant_message is None
    assert result.assistant_question is None


@pytest.mark.asyncio
async def test_semantic_stop_intent_terminates_after_analysis_stage() -> None:
    client = FakeClient(
        [
            (
                '{"event":null,"people":[],"place":null,'
                '"emotion":null,"emotionReason":null,'
                '"memorableMoment":null}'
            ),
            (
                '{"intent":"STOP","answered":false,"childQuestion":false,'
                '"controlAction":"END_SESSION","bridgeResponse":null,'
                '"suggestedTarget":null,'
                '"evidenceText":"더 이야기하고 싶은 마음이 없어"}'
            ),
        ]
    )
    request = _request().model_copy(
        update={
            "current_question": "오늘 어떤 일이 있었어?",
            "current_target": QuestionTarget.EVENT,
            "child_answer": "더 이야기하고 싶은 마음이 없어",
        }
    )

    result = await _pipeline(client).process(request)

    assert result.decision is DialogueTurnDecision.TERMINATE_SESSION
    assert result.termination_reason is (
        DialogueTerminationReason.CHILD_REQUESTED_STOP
    )
    assert client.call_count == 2


@pytest.mark.asyncio
async def test_skip_intent_moves_to_a_different_target() -> None:
    client = FakeClient(
        [
            (
                '{"event":null,"people":[],"place":null,'
                '"emotion":null,"emotionReason":null,'
                '"memorableMoment":null}'
            ),
            (
                '{"intent":"SKIP","answered":false,"childQuestion":false,'
                '"controlAction":"SKIP_CURRENT_TARGET",'
                '"bridgeResponse":"생각나지 않을 수도 있어.",'
                '"suggestedTarget":"memorableMoment",'
                '"evidenceText":"잘 모르겠어"}'
            ),
            (
                "생각나지 않을 수도 있어! "
                "그럼 가장 기억에 남는 순간은 뭐였어?"
            ),
            '{"valid":true,"issues":[]}',
        ]
    )
    request = _request().model_copy(
        update={
            "current_slots": DiarySlots(event="과일 자르기"),
            "current_question": "그때 어떤 기분이었어?",
            "current_target": QuestionTarget.EMOTION,
            "child_answer": "잘 모르겠어",
        }
    )

    result = await _pipeline(client).process(request)

    assert result.assistant_target is QuestionTarget.MEMORABLE_MOMENT
    assert result.updated_skipped_targets == frozenset(
        {QuestionTarget.EMOTION}
    )
    assert "기분이 어땠어" not in result.assistant_question


@pytest.mark.asyncio
async def test_contextual_target_can_override_fixed_slot_priority() -> None:
    client = FakeClient(
        [
            (
                '{"event":"수박이 한 번에 세 개 올라옴",'
                '"people":[],"place":null,"emotion":null,'
                '"emotionReason":null,"memorableMoment":null}'
            ),
            (
                '{"intent":"ANSWER","answered":true,'
                '"childQuestion":false,"controlAction":"CONTINUE",'
                '"bridgeResponse":null,'
                '"suggestedTarget":"memorableMoment",'
                '"evidenceText":null}'
            ),
            (
                '{"acknowledgement":"수박이 세 개나 한꺼번에 나왔구나!",'
                '"question":"그중에서 어떤 순간이 가장 기억에 남아?",'
                '"target":"memorableMoment",'
                '"acknowledgementEvidence":{"source":"childAnswer",'
                '"text":"수박이 한번에 3개 올라왔어"}}'
            ),
        ]
    )
    request = _request().model_copy(
        update={
            "current_question": "게임에서 무슨 일이 있었어?",
            "current_target": QuestionTarget.EVENT,
            "child_answer": "수박이 한번에 3개 올라왔어",
        }
    )

    result = await _pipeline(client).process(request)

    assert result.updated_slots.event == "수박이 한 번에 세 개 올라옴"
    assert result.assistant_target is QuestionTarget.MEMORABLE_MOMENT
    assert client.call_count == 3


@pytest.mark.asyncio
async def test_story_mentioning_stop_words_continues_the_dialogue() -> None:
    """이야기 속에 '그만'이 포함된 발화는 종료로 오탐하지 않는다."""

    client = FakeClient(
        [
            (
                '{"event":"동생과 놀이","people":["동생"],"place":null,'
                '"emotion":"속상함","emotionReason":null,'
                '"memorableMoment":null}'
            ),
            '{"answered":true,"childQuestion":false,"bridgeResponse":null}',
        ]
    )
    request = _request().model_copy(
        update={
            "current_slots": DiarySlots(event="동생과 놀이"),
            "current_question": "그때 어떤 기분이었어?",
            "child_answer": "동생이 그만하라고 해서 속상했어",
        }
    )

    result = await _pipeline(client).process(request)

    assert result.decision is DialogueTurnDecision.CONTINUE
    assert result.updated_slots.emotion == "속상함"


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
    # 다음 질문도 skip된 emotion을 다시 묻지 않고 부드러운 완충 질문으로 진행한다.
    assert result.assistant_question == "오늘 또 나한테 해주고 싶은 이야기 있어?"


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
    assert result.updated_slots.emotion_reason == "친구와 공놀이"
    assert result.assistant_question == "마지막으로 나한테 하고 싶은 말 있어?"


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
            (
                '{"acknowledgement":"나는 트리케라톱스가 든든해 보여서 좋아!",'
                '"bridge":"공룡 이야기도 재미있네.",'
                '"question":"공놀이할 때는 어떤 기분이었어?",'
                '"target":"emotion","acknowledgementEvidence":'
                '{"source":"childAnswer","text":"공룡"}}'
            ),
            '{"valid":true,"issues":[]}',
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
        "공룡 이야기도 재미있네. "
        "공놀이할 때는 어떤 기분이었어?"
    )
    assert result.assistant_question == "공놀이할 때는 어떤 기분이었어?"
    assert result.updated_slots == request.current_slots
    assert result.question_generation_status is (
        QuestionGenerationStatus.GENERATED
    )
    assert result.question_generation_source is QuestionGenerationSource.LLM
    assert client.call_count == 4


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
        "나는 직접 고르긴 어렵지만, 네 이야기를 듣는 게 좋아! "
        "그때 마음은 어땠어?"
    )
    assert result.assistant_question == "그때 마음은 어땠어?"
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
async def test_final_child_question_gets_answered_before_closing() -> None:
    client = FakeClient(
        [
            (
                '{"event":null,"people":[],"place":null,'
                '"emotion":null,"emotionReason":null,'
                '"memorableMoment":null}'
            ),
            (
                '{"intent":"CHILD_QUESTION","answered":false,'
                '"childQuestion":true,"controlAction":"CONTINUE",'
                '"bridgeResponse":"나는 네 이야기를 듣는 게 좋아.",'
                '"suggestedTarget":null,"evidenceText":null}'
            ),
        ]
    )
    request = _request().model_copy(
        update={
            "current_slots": DiarySlots(
                event="블록으로 성 만들기",
                emotion="슬펐어",
                emotion_reason="친구가 블록을 부숴서",
            ),
            "current_question": "마지막으로 나한테 하고 싶은 말 있어?",
            "current_target": QuestionTarget.MEMORABLE_MOMENT,
            "child_answer": "너도 블록 갖고 놀 수 있어?",
            "recent_questions": (
                "마지막으로 나한테 하고 싶은 말 있어?",
            ),
        }
    )

    result = await _pipeline_with_required_three(client).process(request)

    assert result.decision is DialogueTurnDecision.TERMINATE
    assert result.updated_slots.memorable_moment is None
    assert result.closing_message is not None
    assert "이야기를 같이 들을 수 있어" in result.closing_message
    assert "일기로 남겨볼래" in result.closing_message


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
            "정말 신났구나! 어떤 점 때문에 그렇게 신났어?",
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
    assert result.assistant_question == "어떤 점 때문에 그렇게 신났어?"
    assert result.assistant_message == (
        "정말 신났구나! 어떤 점 때문에 그렇게 신났어?"
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
            "영화가 재미있었구나! 어떤 순간이 제일 기억나?",
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
        "영화가 재미있었구나! 어떤 순간이 제일 기억나?"
    )
    assert result.assistant_question == "어떤 순간이 제일 기억나?"
    assert client.call_count == 3


@pytest.mark.asyncio
async def test_explicit_current_target_prevents_slot_based_backfill_guess() -> None:
    analysis_client = FakeClient(
        [
            (
                '{"event":null,"people":[],"place":null,'
                '"emotion":null,"emotionReason":null,'
                '"memorableMoment":null}'
            ),
            '{"answered":true,"childQuestion":false,"bridgeResponse":null}',
        ]
    )
    response_client = FakeClient(
        ["그 장면이 기억에 남는구나! 그때 어떤 기분이었어?"]
    )
    request = DialogueTurnRequest(
        request_id="request-explicit-target",
        conversation_id="conversation-1",
        child_answer="수박 세 개가 한꺼번에 나온 순간",
        current_question="게임에서 어떤 순간이 제일 기억나?",
        current_target=QuestionTarget.MEMORABLE_MOMENT,
        current_topic=DialogueTopic.GAME,
        current_slots=DiarySlots(event="과일 자르기"),
        question_count=2,
        max_question_count=6,
    )

    result = await _pipeline_with_clients(
        analysis_client,
        response_client,
    ).process(request)

    assert result.updated_slots.memorable_moment == (
        "수박 세 개가 한꺼번에 나온 순간"
    )
    assert result.updated_slots.emotion is None
    assert result.assistant_target is QuestionTarget.EMOTION


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
            "영화가 감동적이었구나! 어떤 순간이 제일 기억나?",
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
        "영화가 감동적이었구나! 어떤 순간이 제일 기억나?"
    )
    assert result.assistant_question == "어떤 순간이 제일 기억나?"


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
            (
                "나는 재미있는 모험 영화를 좋아해! "
                "영화 이야기도 재미있지만, 어떤 순간이 제일 기억나?"
            ),
            '{"valid":true,"issues":[]}',
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
        "영화 이야기도 재미있지만, 어떤 순간이 제일 기억나?"
    )
    assert result.assistant_question == (
        "영화 이야기도 재미있지만, 어떤 순간이 제일 기억나?"
    )
    assert client.call_count == 4


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
            "current_question": "마지막으로 나한테 하고 싶은 말 있어?",
            "current_target": QuestionTarget.MEMORABLE_MOMENT,
            "recent_questions": ("마지막으로 나한테 하고 싶은 말 있어?",),
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
async def test_offers_final_prompt_when_required_slots_fill_quickly() -> None:
    client = FakeClient(
        [
            (
                '{"event":null,"people":[],"place":null,'
                '"emotion":"신기했어","emotionReason":null,'
                '"memorableMoment":"소라가 움직인 것"}'
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
                event="바다에서 놀고 소라 보기",
                emotion_reason="바다에서 놀고 소라 보기",
            ),
            "current_question": "그때 어떤 기분이었어?",
            "current_target": QuestionTarget.EMOTION,
            "child_answer": "소라가 막 움직이는 게 신기했어!",
            "question_count": 2,
        }
    )

    result = await _pipeline(client).process(request)

    assert result.decision is DialogueTurnDecision.CONTINUE
    assert result.assistant_target is QuestionTarget.MEMORABLE_MOMENT
    assert result.assistant_question == "오늘 또 나한테 해주고 싶은 이야기 있어?"
    assert "신기" in result.assistant_message


@pytest.mark.asyncio
async def test_contextual_followup_moves_to_tomorrow_question() -> None:
    client = FakeClient(
        [
            (
                '{"event":null,"people":[],"place":null,'
                '"emotion":null,"emotionReason":null,'
                '"memorableMoment":"수박도 자른 것"}'
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
                event="바다에서 놀기",
                emotion="신기했어",
                emotion_reason="소라가 움직여서",
            ),
            "current_question": "오늘 또 나한테 해주고 싶은 이야기 있어?",
            "current_target": QuestionTarget.MEMORABLE_MOMENT,
            "recent_questions": ("오늘 또 나한테 해주고 싶은 이야기 있어?",),
            "child_answer": "수박도 잘랐어",
            "question_count": 3,
        }
    )

    result = await _pipeline_with_required_three(client).process(request)

    assert result.decision is DialogueTurnDecision.CONTINUE
    assert result.updated_slots.memorable_moment == "수박도 자른 것"
    assert result.assistant_question == "내일은 뭐하고 싶어?"


@pytest.mark.asyncio
async def test_tomorrow_answer_closes_without_slot_update() -> None:
    client = FakeClient(
        [
            (
                '{"event":"내일 놀이터 가기","people":[],"place":"놀이터",'
                '"emotion":"좋아","emotionReason":"그네를 타고 싶어서",'
                '"memorableMoment":"그네 타기"}'
            ),
            (
                '{"answered":true,"childQuestion":false,'
                '"bridgeResponse":null}'
            ),
        ]
    )
    slots = DiarySlots(
        event="놀이터에서 그네 타기",
        emotion="좋았어",
        emotion_reason="그네가 재미있어서",
        memorable_moment="높이 올라간 것",
    )
    request = _request().model_copy(
        update={
            "current_slots": slots,
            "current_question": "내일은 뭐하고 싶어?",
            "current_target": QuestionTarget.MEMORABLE_MOMENT,
            "recent_questions": (
                "놀이터에서 또 뭐 했어?",
                "내일은 뭐하고 싶어?",
            ),
            "child_answer": "내일도 놀이터 가서 그네 탈래",
            "question_count": 5,
        }
    )

    result = await _pipeline_with_required_three(client).process(request)

    assert result.decision is DialogueTurnDecision.TERMINATE
    assert result.updated_slots == slots
    assert result.closing_message is not None
    assert "내일 그네 타고 나서 또 만나자" in result.closing_message
    assert "일기로 남겨볼래" in result.closing_message


@pytest.mark.asyncio
async def test_playground_contextual_followup_gets_warm_ack() -> None:
    client = FakeClient(
        [
            (
                '{"event":null,"people":[],"place":null,'
                '"emotion":null,"emotionReason":null,'
                '"memorableMoment":"시소 타고 논 것"}'
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
                event="놀이터에서 길동이랑 놀기",
                emotion="신났어",
                emotion_reason="길동이랑 놀아서",
            ),
            "current_question": "놀이터에서 또 뭐 했어?",
            "current_target": QuestionTarget.MEMORABLE_MOMENT,
            "recent_questions": ("놀이터에서 또 뭐 했어?",),
            "child_answer": "시소 타고 놀았어",
            "question_count": 3,
        }
    )

    result = await _pipeline_with_required_three(client).process(request)

    assert result.decision is DialogueTurnDecision.CONTINUE
    assert result.assistant_question == "내일은 뭐하고 싶어?"
    assert "시소 타고 놀아서 재미있었겠다" in result.assistant_message


@pytest.mark.asyncio
async def test_together_playground_story_is_not_invitation() -> None:
    client = FakeClient(
        [
            (
                '{"event":null,"people":["철수"],"place":"놀이터",'
                '"emotion":null,"emotionReason":null,'
                '"memorableMoment":"철수랑 시소와 미끄럼틀을 탄 것"}'
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
                event="놀이터에서 놀기",
                emotion="재미있었어",
                emotion_reason="친구랑 놀아서",
            ),
            "current_question": "놀이터에서 또 뭐 했어?",
            "current_target": QuestionTarget.MEMORABLE_MOMENT,
            "recent_questions": ("놀이터에서 또 뭐 했어?",),
            "child_answer": "철수랑 같이 시소도 타고 미끄럼틀도 탔어",
            "question_count": 3,
        }
    )

    result = await _pipeline_with_required_three(client).process(request)

    assert result.decision is DialogueTurnDecision.CONTINUE
    assert result.assistant_question == "내일은 뭐하고 싶어?"
    assert "같이 하자고" not in result.assistant_message
    assert "시소 타고 놀아서 재미있었겠다" in result.assistant_message


@pytest.mark.asyncio
async def test_mixed_snack_story_and_swing_question_answers_swing() -> None:
    client = FakeClient(
        [
            (
                '{"event":null,"people":["철수"],"place":null,'
                '"emotion":null,"emotionReason":null,'
                '"memorableMoment":"철수랑 간식을 같이 먹은 것"}'
            ),
            (
                '{"answered":false,"childQuestion":true,'
                '"intent":"CHILD_QUESTION","controlAction":"CONTINUE",'
                '"bridgeResponse":null,'
                '"suggestedTarget":null,"evidenceText":null}'
            ),
        ]
    )
    request = _request().model_copy(
        update={
            "current_slots": DiarySlots(
                event="놀이터에서 놀기",
                emotion="재미있었어",
                emotion_reason="그네가 재미있어서",
            ),
            "current_question": "놀이터에서 또 뭐 했어?",
            "current_target": QuestionTarget.MEMORABLE_MOMENT,
            "recent_questions": ("놀이터에서 또 뭐 했어?",),
            "child_answer": (
                "엄마가 준 간식도 같이 먹었어. "
                "근데 진짜 재밌는데 너도 그네 타본 적 있어?"
            ),
            "question_count": 3,
        }
    )

    result = await _pipeline_with_required_three(client).process(request)

    assert result.decision is DialogueTurnDecision.CONTINUE
    assert result.assistant_question == "내일은 뭐하고 싶어?"
    assert "간식도 같이 먹었구나" in result.assistant_message
    assert "그네를 타보진 못해도" in result.assistant_message
    assert "먹지는 못해" not in result.assistant_message
    assert "같이 먹자고" not in result.assistant_message


@pytest.mark.asyncio
async def test_brag_question_gets_praise_in_pipeline() -> None:
    analysis_client = FakeClient(
        [
            (
                '{"event":null,"people":[],"place":null,'
                '"emotion":null,"emotionReason":null,'
                '"memorableMoment":"수박을 한 번에 여러 개 자른 것"}'
            ),
            (
                '{"intent":"CHILD_QUESTION","answered":true,'
                '"childQuestion":true,"controlAction":"CONTINUE",'
                '"bridgeResponse":null,"suggestedTarget":null,'
                '"evidenceText":"짱이지"}'
            ),
        ]
    )
    response_client = FakeClient([])
    request = _request().model_copy(
        update={
            "current_topic": DialogueTopic.GAME,
            "current_slots": DiarySlots(
                event="과일 자르기 게임",
                emotion="재미있었어",
                emotion_reason="수박을 많이 잘라서",
            ),
            "current_question": "그 게임에서 어떤 순간이 제일 재미있었어?",
            "current_target": QuestionTarget.MEMORABLE_MOMENT,
            "recent_questions": (
                "그 게임에서 어떤 순간이 제일 재미있었어?",
            ),
            "child_answer": "내가 수박을 한 번에 여러 개 잘랐어. 짱이지?",
            "question_count": 3,
        }
    )

    result = await _pipeline_with_clients(
        analysis_client,
        response_client,
    ).process(request)

    assert result.decision is DialogueTurnDecision.CONTINUE
    assert result.assistant_question == "내일은 뭐하고 싶어?"
    assert "응, 정말 대단해" in result.assistant_message
    assert "직접" not in result.assistant_message


@pytest.mark.asyncio
async def test_game_fruit_story_gets_game_contextual_followup() -> None:
    client = FakeClient(
        [
            (
                '{"event":null,"people":[],"place":null,'
                '"emotion":"좋았어","emotionReason":"1000점 받아서",'
                '"memorableMoment":null}'
            ),
            (
                '{"answered":true,"childQuestion":false,'
                '"bridgeResponse":null}'
            ),
        ]
    )
    request = _request().model_copy(
        update={
            "current_topic": DialogueTopic.GAME,
            "current_slots": DiarySlots(event="과일 자르기 게임에서 1000점 받기"),
            "current_question": "그때 마음은 어땠어?",
            "current_target": QuestionTarget.EMOTION,
            "child_answer": "1000점 받아서 좋았어",
            "question_count": 2,
        }
    )

    result = await _pipeline_with_required_three(client).process(request)

    assert result.decision is DialogueTurnDecision.CONTINUE
    assert result.assistant_question == "그 게임에서 어떤 순간이 제일 재미있었어?"


@pytest.mark.asyncio
async def test_daily_fruit_story_uses_general_contextual_followup() -> None:
    client = FakeClient(
        [
            (
                '{"event":null,"people":[],"place":null,'
                '"emotion":"신기했어","emotionReason":"과일이 잘려서",'
                '"memorableMoment":null}'
            ),
            (
                '{"answered":true,"childQuestion":false,'
                '"bridgeResponse":null}'
            ),
        ]
    )
    request = _request().model_copy(
        update={
            "current_topic": DialogueTopic.DAILY,
            "current_slots": DiarySlots(event="과일을 잘라 보기"),
            "current_question": "그때 마음은 어땠어?",
            "current_target": QuestionTarget.EMOTION,
            "child_answer": "과일이 잘려서 신기했어",
            "question_count": 2,
        }
    )

    result = await _pipeline_with_required_three(client).process(request)

    assert result.decision is DialogueTurnDecision.CONTINUE
    assert result.assistant_question == "오늘 또 나한테 해주고 싶은 이야기 있어?"


@pytest.mark.asyncio
async def test_closing_replies_to_final_invitation() -> None:
    client = FakeClient(
        [
            (
                '{"event":null,"people":[],"place":null,'
                '"emotion":null,"emotionReason":null,'
                '"memorableMoment":"같이 만들자고 함"}'
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
                event="바다에서 모래성 만들기",
                emotion="좋았어",
                emotion_reason="이모가 만들어줘서",
            ),
            "current_question": "마지막으로 나한테 하고 싶은 말 있어?",
            "current_target": QuestionTarget.MEMORABLE_MOMENT,
            "recent_questions": ("마지막으로 나한테 하고 싶은 말 있어?",),
            "child_answer": "다음에 나랑 같이 모래성 만들자!",
            "question_count": 3,
        }
    )

    result = await _pipeline(client).process(request)

    assert result.decision is DialogueTurnDecision.TERMINATE
    assert result.closing_message is not None
    assert "같이 만들자고 말해줘서 고마워" in result.closing_message
    assert "일기로 남겨볼래" in result.closing_message


@pytest.mark.asyncio
async def test_closing_replies_to_final_child_question() -> None:
    client = FakeClient(
        [
            (
                '{"event":null,"people":[],"place":null,'
                '"emotion":null,"emotionReason":null,'
                '"memorableMoment":"케이크 질문"}'
            ),
            (
                '{"answered":false,"childQuestion":true,'
                '"intent":"CHILD_QUESTION","controlAction":"CONTINUE",'
                '"bridgeResponse":"나는 먹지는 못하지만 이야기는 좋아.",'
                '"suggestedTarget":null,"evidenceText":null}'
            ),
        ]
    )
    request = _request().model_copy(
        update={
            "current_slots": DiarySlots(
                event="할머니 생일에 케이크 먹기",
                emotion="행복했어",
                emotion_reason="케이크를 먹어서",
            ),
            "current_question": "마지막으로 나한테 하고 싶은 말 있어?",
            "current_target": QuestionTarget.MEMORABLE_MOMENT,
            "recent_questions": ("마지막으로 나한테 하고 싶은 말 있어?",),
            "child_answer": "너도 케이크 먹어봤어?",
            "question_count": 3,
        }
    )

    result = await _pipeline(client).process(request)

    assert result.decision is DialogueTurnDecision.TERMINATE
    assert result.closing_message is not None
    assert "먹지는 못해" in result.closing_message
    assert "일기로 남겨볼래" in result.closing_message


@pytest.mark.asyncio
async def test_soft_continue_child_question_closes_without_repeating_prompt() -> None:
    client = FakeClient(
        [
            (
                '{"event":null,"people":[],"place":null,'
                '"emotion":null,"emotionReason":null,'
                '"memorableMoment":null}'
            ),
            (
                '{"answered":false,"childQuestion":true,'
                '"intent":"CHILD_QUESTION","controlAction":"CONTINUE",'
                '"bridgeResponse":null,'
                '"suggestedTarget":null,"evidenceText":null}'
            ),
        ]
    )
    request = _request().model_copy(
        update={
            "current_slots": DiarySlots(
                event="과일 자르기",
                emotion="신기했어",
                emotion_reason="과일이 잘려서",
            ),
            "current_question": "그 이야기에서 하나만 더 들려줄래?",
            "current_target": QuestionTarget.MEMORABLE_MOMENT,
            "recent_questions": ("그 이야기에서 하나만 더 들려줄래?",),
            "child_answer": "너도 해본 적 있어? 이 게임?",
            "question_count": 4,
        }
    )

    result = await _pipeline(client).process(request)

    assert result.decision is DialogueTurnDecision.TERMINATE
    assert result.closing_message is not None
    assert "직접 게임하진 못하지만" in result.closing_message
    assert "그 이야기에서 하나만 더" not in result.closing_message


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
    assert result.assistant_question == "오늘 무슨 일이 있었어?"
    assert result.assistant_message == (
        "그래, 그럼 다른 얘기하자! 오늘 무슨 일이 있었어?"
    )


@pytest.mark.asyncio
async def test_game_refusal_carries_daily_story_into_next_topic() -> None:
    client = FakeClient(
        [
            (
                '{"event":null,"people":[],"place":null,'
                '"emotion":null,"emotionReason":null,'
                '"memorableMoment":null}'
            ),
            (
                '{"intent":"REFUSAL","answered":false,'
                '"childQuestion":false,"controlAction":"SWITCH_TOPIC",'
                '"bridgeResponse":"게임 말고 놀이터 이야기를 들을게.",'
                '"suggestedTarget":null,'
                '"evidenceText":"게임 얘기 안 할래"}'
            ),
        ]
    )
    request = _request().model_copy(
        update={
            "current_topic": DialogueTopic.GAME,
            "next_topic": DialogueTopic.DAILY,
            "current_question": "게임에서 어떤 일이 있었어?",
            "current_target": QuestionTarget.EVENT,
            "child_answer": (
                "게임 얘기 안 할래. 놀이터에서 그네 못 타서 속상했어"
            ),
            "question_count": 2,
        }
    )

    result = await _pipeline_with_required_three(client).process(request)

    assert result.decision is DialogueTurnDecision.SWITCH_TOPIC
    assert result.next_topic_slots is not None
    assert result.next_topic_slots.event == "놀이터에서 그네 못 타서 속상했어"
    assert result.next_topic_slots.emotion == "속상했어"
    assert result.next_topic_slots.emotion_reason == "놀이터에서 그네 못 타서"
    assert "그래, 그럼 다른 얘기하자" in result.assistant_message
    assert "속상" in result.assistant_message
    assert result.assistant_question == "마지막으로 나한테 하고 싶은 말 있어?"


@pytest.mark.asyncio
async def test_game_refusal_daily_cake_story_gets_clear_transition() -> None:
    client = FakeClient(
        [
            (
                '{"event":null,"people":[],"place":null,'
                '"emotion":null,"emotionReason":null,'
                '"memorableMoment":null}'
            ),
            (
                '{"intent":"REFUSAL","answered":false,'
                '"childQuestion":false,"controlAction":"SWITCH_TOPIC",'
                '"bridgeResponse":null,"suggestedTarget":null,'
                '"evidenceText":"게임 얘기 안 할래"}'
            ),
        ]
    )
    request = _request().model_copy(
        update={
            "current_topic": DialogueTopic.GAME,
            "next_topic": DialogueTopic.DAILY,
            "current_question": "게임에서 어떤 일이 있었어?",
            "current_target": QuestionTarget.EVENT,
            "child_answer": "게임 얘기 안 할래. 오늘 케이크 먹었어!",
            "question_count": 2,
        }
    )

    result = await _pipeline_with_required_three(client).process(request)

    assert result.decision is DialogueTurnDecision.SWITCH_TOPIC
    assert result.next_topic_slots is not None
    assert result.next_topic_slots.event == "오늘 케이크 먹었어"
    assert "그래, 그럼 다른 얘기하자" in result.assistant_message
    assert "케이크" in result.assistant_message
    assert "그 일이 기억에 남았구나" not in result.assistant_message
    assert "하나만 더" not in result.assistant_message
    assert result.assistant_question == "그때 마음은 어땠어?"


@pytest.mark.asyncio
async def test_safety_redirect_from_game_switches_to_daily_question() -> None:
    client = FakeClient([])
    request = _request().model_copy(
        update={
            "current_topic": DialogueTopic.GAME,
            "next_topic": DialogueTopic.DAILY,
            "current_question": "게임에서 어떤 일이 있었어?",
            "current_target": QuestionTarget.EVENT,
            "child_answer": "죽고 싶어",
            "question_count": 1,
        }
    )

    result = await _pipeline_with_required_three(client).process(request)

    assert result.decision is DialogueTurnDecision.SWITCH_TOPIC
    assert result.assistant_question == "지금은 오늘 있었던 다른 일을 말해 줄래?"
    assert "게임" not in result.assistant_question
    assert "죽고 싶어" not in result.assistant_message
    assert client.call_count == 0


@pytest.mark.asyncio
async def test_negative_daily_event_asks_emotion_not_another_story() -> None:
    analysis_client = FakeClient(
        [
            (
                '{"event":"친구가 놀림","people":["친구"],"place":null,'
                '"emotion":null,"emotionReason":null,'
                '"memorableMoment":null}'
            ),
            (
                '{"intent":"ANSWER","answered":true,'
                '"childQuestion":false,"controlAction":"CONTINUE",'
                '"bridgeResponse":null,"suggestedTarget":null,'
                '"evidenceText":null}'
            ),
        ]
    )
    response_client = FakeClient(
        [
            RuntimeError("generation unavailable"),
            RuntimeError("generation unavailable"),
        ]
    )
    request = _request().model_copy(
        update={
            "current_topic": DialogueTopic.DAILY,
            "current_question": "오늘 무슨 일이 있었어?",
            "current_target": QuestionTarget.EVENT,
            "child_answer": "친구가 놀렸어",
            "question_count": 1,
        }
    )

    result = await _pipeline_with_clients(
        analysis_client,
        response_client,
    ).process(request)

    assert result.assistant_target is QuestionTarget.EMOTION
    assert result.assistant_question == "그때 마음은 어땠어?"
    assert "마음이 안 좋을 수 있어" in result.assistant_message
    assert "하나만 더" not in result.assistant_message


@pytest.mark.asyncio
async def test_child_question_and_game_event_are_both_preserved() -> None:
    analysis_client = FakeClient(
        [
            (
                '{"event":null,"people":[],"place":null,'
                '"emotion":null,"emotionReason":null,'
                '"memorableMoment":null}'
            ),
            (
                '{"intent":"CHILD_QUESTION","answered":false,'
                '"childQuestion":true,"controlAction":"CONTINUE",'
                '"bridgeResponse":"나는 직접 게임하진 못해.",'
                '"suggestedTarget":"emotion","evidenceText":null}'
            ),
        ]
    )
    response_client = FakeClient([])
    request = _request().model_copy(
        update={
            "current_topic": DialogueTopic.GAME,
            "current_question": "게임에서 어떤 일이 있었어?",
            "current_target": QuestionTarget.EVENT,
            "child_answer": "너도 게임 해봤어? 오늘 1000점 맞았어",
            "question_count": 2,
        }
    )

    result = await _pipeline_with_clients(
        analysis_client,
        response_client,
    ).process(request)

    assert result.decision is DialogueTurnDecision.CONTINUE
    assert result.updated_slots.event == "오늘 1000점 맞았어"
    assert "직접 게임하진 못하지만" in result.assistant_message
    assert result.assistant_target is QuestionTarget.EMOTION


@pytest.mark.asyncio
async def test_optional_final_negative_disclosure_continues_once() -> None:
    client = FakeClient(
        [
            (
                '{"event":null,"people":[],"place":null,'
                '"emotion":"속상했어","emotionReason":"그네를 못 타서",'
                '"memorableMoment":null}'
            ),
            (
                '{"intent":"ANSWER","answered":true,'
                '"childQuestion":false,"controlAction":"CONTINUE",'
                '"bridgeResponse":null,"suggestedTarget":null,'
                '"evidenceText":null}'
            ),
        ]
    )
    request = _request().model_copy(
        update={
            "current_slots": DiarySlots(
                event="놀이터에서 놀기",
                emotion="아쉬웠어",
                emotion_reason="그네를 못 타서",
            ),
            "current_question": "마지막으로 나한테 하고 싶은 말 있어?",
            "current_target": QuestionTarget.MEMORABLE_MOMENT,
            "recent_questions": ("마지막으로 나한테 하고 싶은 말 있어?",),
            "child_answer": "그네 못 타서 속상해",
            "question_count": 4,
        }
    )

    result = await _pipeline_with_required_three(client).process(request)

    assert result.decision is DialogueTurnDecision.TERMINATE
    assert result.closing_message is not None
    assert "속상" in result.closing_message
    assert "일기로 남겨볼래" in result.closing_message
