package com.jarec.mesaj.mama.server;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

public final class TlsContextFactory {
    private TlsContextFactory() {
    }

    public static SSLContext fromPkcs12(Path keyStorePath, char[] password)
            throws IOException, GeneralSecurityException {
        if (keyStorePath == null || password == null || password.length == 0) {
            throw new IllegalArgumentException("keyStorePath and non-empty password are required");
        }
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream input = Files.newInputStream(keyStorePath)) {
            keyStore.load(input, password);
        }
        KeyManagerFactory keyManagerFactory =
                KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, password);
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(keyManagerFactory.getKeyManagers(), null, null);
        return sslContext;
    }
}
