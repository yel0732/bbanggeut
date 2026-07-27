package com.A501.backend.domain.conversation.dto.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.RecordComponent;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.A501.backend.domain.conversation.dto.internal.DialogueGameContextPayload;
import com.A501.backend.domain.conversation.dto.internal.DialogueSlotsPayload;
import com.A501.backend.domain.conversation.entity.ConversationGoal;
import com.A501.backend.domain.conversation.entity.ConversationTopic;

class ContextSnapshotTest {

    private static final List<Class<?>> REALTIME_CONTEXT_TYPES = List.of(
            ContextSnapshot.class,
            RealtimeChildContext.class,
            RealtimeConversationContext.class,
            RealtimeTopicSnapshot.class,
            RealtimePolicySnapshot.class);

    private static final List<String> FORBIDDEN_FIELD_KEYWORDS = List.of(
            "birthdate", "birthday", "email", "fulltranscript", "rawaudio",
            "rawvideo", "accesstoken", "password", "ssn");

    @Test
    void Context_Snapshot을_생성한다() {
        ContextSnapshot snapshot = validSnapshot();

        assertThat(snapshot.contextVersion()).isEqualTo(ContextSnapshot.CURRENT_VERSION);
        assertThat(snapshot.conversationId()).isEqualTo("conv_context_test");
        assertThat(snapshot.child().displayName()).isEqualTo("하준");
        assertThat(snapshot.topics()).hasSize(2);
    }

    @Test
    void 대화_ID가_없으면_거부한다() {
        assertThatThrownBy(() -> ContextSnapshot.create(
                " ",
                childContext(),
                conversationContext(),
                null,
                topics(),
                RealtimePolicySnapshot.defaultPolicy()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 아이_Context가_없으면_거부한다() {
        assertThatThrownBy(() -> ContextSnapshot.create(
                "conv_context_test",
                null,
                conversationContext(),
                null,
                topics(),
                RealtimePolicySnapshot.defaultPolicy()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 대화_Context가_없으면_거부한다() {
        assertThatThrownBy(() -> ContextSnapshot.create(
                "conv_context_test",
                childContext(),
                null,
                null,
                topics(),
                RealtimePolicySnapshot.defaultPolicy()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 정책이_없으면_거부한다() {
        assertThatThrownBy(() -> ContextSnapshot.create(
                "conv_context_test",
                childContext(),
                conversationContext(),
                null,
                topics(),
                null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 버전이_1_미만이면_거부한다() {
        assertThatThrownBy(() -> new ContextSnapshot(
                0,
                "conv_context_test",
                childContext(),
                conversationContext(),
                null,
                topics(),
                RealtimePolicySnapshot.defaultPolicy()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void topics_목록은_외부에서_변경할_수_없다() {
        List<RealtimeTopicSnapshot> mutableTopics = new ArrayList<>(topics());
        ContextSnapshot snapshot = ContextSnapshot.create(
                "conv_context_test",
                childContext(),
                conversationContext(),
                null,
                mutableTopics,
                RealtimePolicySnapshot.defaultPolicy());

        mutableTopics.clear();

        assertThat(snapshot.topics()).hasSize(2);
        assertThatThrownBy(() -> snapshot.topics().add(
                RealtimeTopicSnapshot.withoutGames(ConversationTopic.DAILY, 3, 6)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void games가_null이면_빈_목록으로_대체한다() {
        RealtimeTopicSnapshot topic = new RealtimeTopicSnapshot(
                ConversationTopic.GAME, 1, 6, null, DialogueSlotsPayload.empty());

        assertThat(topic.games()).isEmpty();
    }

    @Test
    void 정확한_생년월일이나_이메일_등_금지된_개인정보_필드는_존재하지_않는다() {
        for (Class<?> type : REALTIME_CONTEXT_TYPES) {
            for (RecordComponent component : type.getRecordComponents()) {
                String name = component.getName().toLowerCase(Locale.ROOT);
                assertThat(FORBIDDEN_FIELD_KEYWORDS)
                        .as("%s.%s 필드는 금지된 개인정보를 포함해서는 안 됩니다.",
                                type.getSimpleName(), component.getName())
                        .noneMatch(name::contains);
            }
        }
    }

    private ContextSnapshot validSnapshot() {
        return ContextSnapshot.create(
                "conv_context_test",
                childContext(),
                conversationContext(),
                null,
                topics(),
                RealtimePolicySnapshot.defaultPolicy());
    }

    private RealtimeChildContext childContext() {
        return new RealtimeChildContext("하준");
    }

    private RealtimeConversationContext conversationContext() {
        return new RealtimeConversationContext(
                ConversationGoal.DIARY, "ko-KR", ConversationTopic.GAME, 12);
    }

    private List<RealtimeTopicSnapshot> topics() {
        return List.of(
                new RealtimeTopicSnapshot(
                        ConversationTopic.GAME,
                        1,
                        6,
                        List.of(new DialogueGameContextPayload(
                                UUID.randomUUID(),
                                1L,
                                820,
                                180,
                                LocalDateTime.of(2026, 7, 27, 10, 0))),
                        DialogueSlotsPayload.empty()),
                RealtimeTopicSnapshot.withoutGames(ConversationTopic.DAILY, 2, 6));
    }
}
