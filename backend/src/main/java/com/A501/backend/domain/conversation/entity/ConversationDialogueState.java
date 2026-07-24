package com.A501.backend.domain.conversation.entity;

import org.springframework.data.domain.Persistable;

import com.A501.backend.common.entity.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "conversation_dialogue_state")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConversationDialogueState extends BaseTimeEntity implements Persistable<String> {

    @Id
    @Column(name = "conversation_id", length = 40)
    private String conversationId;

    @Column(name = "slots_json", nullable = false, columnDefinition = "TEXT")
    private String slotsJson;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    private ConversationDialogueState(String conversationId, String slotsJson) {
        validateText(conversationId, "대화 ID가 필요합니다.");
        validateText(slotsJson, "슬롯 JSON이 필요합니다.");
        this.conversationId = conversationId;
        this.slotsJson = slotsJson;
    }

    public static ConversationDialogueState create(String conversationId, String slotsJson) {
        return new ConversationDialogueState(conversationId, slotsJson);
    }

    public void updateSlots(String slotsJson) {
        validateText(slotsJson, "슬롯 JSON이 필요합니다.");
        this.slotsJson = slotsJson;
    }

    private static void validateText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    @Override
    public String getId() {
        return conversationId;
    }

    @Override
    public boolean isNew() {
        return isNewEntity();
    }
}
