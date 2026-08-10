"""에코 판정 텍스트 유사도 테스트.

에코 조각은 중단된 문장의 **서두**다: barge-in은 출력 시작 약 400ms 뒤에
확정되고 즉시 소리가 사라지므로, 잡히는 전사는 pre-roll을 포함한 문장 앞부분
조각이다. 아이가 질문 꼬리 단어를 되받는 정상 대답은 에코가 아니어야 한다.
"""

from app.session.echo_guard import is_probable_echo

_QUESTION = "오늘은 어떤 놀이가 제일 재미있었어?"


class TestEcho:
    def test_prefix_fragment_is_an_echo(self) -> None:
        assert is_probable_echo("오늘은 어떤 놀이", _QUESTION)

    def test_fragment_with_punctuation_and_spacing_noise_is_an_echo(self) -> None:
        assert is_probable_echo("오늘은, 어떤 놀이가!", _QUESTION)

    def test_fragment_missing_the_first_syllable_is_an_echo(self) -> None:
        """전사가 문장 첫 음절을 놓쳐도 margin 안에서 잡는다."""

        assert is_probable_echo("늘은 어떤 놀", _QUESTION)

    def test_a_short_full_utterance_echo_is_an_echo(self) -> None:
        assert is_probable_echo("그랬구나", "그랬구나!")


class TestGenuineSpeech:
    def test_an_answer_reusing_the_question_tail_is_not_an_echo(self) -> None:
        """한국어 대답은 질문의 꼬리 단어를 자주 되받는다 — prefix 창 밖이다."""

        assert not is_probable_echo("재미있었어", _QUESTION)

    def test_an_unrelated_answer_is_not_an_echo(self) -> None:
        assert not is_probable_echo("블록 놀이 했어", _QUESTION)

    def test_an_interjection_over_the_question_is_not_an_echo(self) -> None:
        assert not is_probable_echo("아니야 아니야", _QUESTION)

    def test_too_short_to_judge_passes_through(self) -> None:
        """판정 근거가 부족한 짧은 전사는 진짜 발화로 취급한다."""

        assert not is_probable_echo("응", _QUESTION)

    def test_empty_source_never_matches(self) -> None:
        assert not is_probable_echo("오늘은 어떤 놀이", "")
