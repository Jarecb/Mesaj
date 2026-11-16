package com.jarec.mesaj.mama.message;

public interface SequenceGenerator {
    long next(ConversationId conversationId);
}
