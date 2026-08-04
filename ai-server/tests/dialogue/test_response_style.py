from app.dialogue.response_style import (
    acknowledge_child_answer,
    safe_child_question_reply,
)


def test_negative_event_without_emotion_gets_empathy() -> None:
    assert acknowledge_child_answer(
        "놀이터에서 그네 타고 싶은데 언니가 안 나와서 못탔어"
    ) == "많이 아쉬웠겠다."


def test_affirmation_question_gets_warm_agreement() -> None:
    assert safe_child_question_reply("시소 진짜 재밌지?") == (
        "응, 네가 말해주니까 정말 재미있게 느껴져."
    )


def test_food_preference_question_stays_short_and_safe() -> None:
    assert safe_child_question_reply("케이크 좋아해?") == (
        "나는 먹지는 못해. 그래도 맛있는 이야기는 좋아."
    )
