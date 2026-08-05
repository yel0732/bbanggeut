from app.dialogue.response_style import (
    acknowledge_child_answer,
    is_child_invitation,
    safe_child_question_reply,
    tomorrow_closing_message,
)


def test_negative_event_without_emotion_gets_empathy() -> None:
    assert acknowledge_child_answer(
        "놀이터에서 그네 타고 싶은데 언니가 안 나와서 못탔어"
    ) == "많이 아쉬웠겠다."


def test_past_story_with_together_is_not_invitation() -> None:
    answer = "철수랑 같이 시소도 타고 미끄럼틀도 탔어"

    assert not is_child_invitation(answer)
    # 아이가 감정을 말하지 않았으므로 재미있었다고 정해 주지 않는다.
    assert acknowledge_child_answer(answer) == "시소 이야기 해줬구나!"


def test_invitation_requires_inviting_ending() -> None:
    assert is_child_invitation("다음에 나랑 같이 모래성 만들자!")
    assert is_child_invitation("내일 또 만나자!")


def test_affirmation_question_gets_warm_agreement() -> None:
    assert safe_child_question_reply("시소 진짜 재밌지?") == (
        "응, 정말 재미있겠다!"
    )


def test_brag_question_gets_praise_not_ai_limitation() -> None:
    assert safe_child_question_reply("내가 수박 여러 개 잘랐어. 짱이지?") == (
        "응, 정말 대단해!"
    )


def test_food_preference_question_stays_short_and_safe() -> None:
    assert safe_child_question_reply("케이크 좋아해?") == (
        "나는 먹지는 못해. 그래도 맛있는 이야기는 좋아."
    )


def test_mixed_food_story_and_swing_question_answers_swing_question() -> None:
    assert safe_child_question_reply(
        "엄마가 준 간식도 같이 먹었어. 근데 진짜 재밌는데 너도 그네 타본 적 있어?"
    ) == (
        "간식도 같이 먹었구나! "
        "나는 직접 그네를 타보진 못해도, 네 그네 이야기는 좋아."
    )


def test_tomorrow_closing_reflects_child_activity() -> None:
    assert tomorrow_closing_message("내일도 놀이터 가서 그네 탈래") == (
        "좋아! 내일 그네 타고 나서 또 만나자. "
        "오늘 이야기해줘서 고마워. 오늘 이야기를 일기로 남겨볼래?"
    )
