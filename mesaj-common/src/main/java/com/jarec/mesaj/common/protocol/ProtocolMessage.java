package com.jarec.mesaj.common.protocol;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = Login.class, name = "LOGIN"),
        @JsonSubTypes.Type(value = LoginSuccess.class, name = "LOGIN_SUCCESS"),
        @JsonSubTypes.Type(value = GetUsers.class, name = "GET_USERS"),
        @JsonSubTypes.Type(value = UserList.class, name = "USER_LIST"),
        @JsonSubTypes.Type(value = SendMessage.class, name = "SEND_MSG"),
        @JsonSubTypes.Type(value = MessageReceived.class, name = "MESSAGE_RECEIVED"),
        @JsonSubTypes.Type(value = BatchDelivery.class, name = "BATCH_DELIVERY"),
        @JsonSubTypes.Type(value = AckBatch.class, name = "ACK_BATCH"),
        @JsonSubTypes.Type(value = ReadReceiptUpdate.class, name = "READ_RECEIPT_UPDATE")
})
public sealed interface ProtocolMessage permits Login, LoginSuccess, GetUsers, UserList, SendMessage, MessageReceived, BatchDelivery, AckBatch, ReadReceiptUpdate {
    String type();
}
