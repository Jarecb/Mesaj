package com.jarec.mesaj.mama.message;

import java.io.IOException;

public interface MessagePublisher {
    void publish(StoredMessage message) throws IOException;
}
