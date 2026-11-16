package com.jarec.mesaj.mama.message;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ConversationIdTest {
    @Test
    void createsTheSameIdRegardlessOfParticipantOrder() {
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");

        assertThat(ConversationId.between(first, second))
                .isEqualTo(ConversationId.between(second, first))
                .returns(first + "_" + second, ConversationId::value);
    }

    @Test
    @SuppressWarnings("DataFlowIssue")
    void rejectsMissingParticipantsAndBlankIds() {
        UUID participant = UUID.randomUUID();

        assertThatIllegalArgumentException().isThrownBy(() -> ConversationId.between(null, participant));
        assertThatIllegalArgumentException().isThrownBy(() -> new ConversationId(" "));
    }
}
