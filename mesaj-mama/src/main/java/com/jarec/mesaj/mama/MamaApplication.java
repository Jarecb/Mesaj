package com.jarec.mesaj.mama;

import com.jarec.mesaj.mama.auth.AuthService;
import com.jarec.mesaj.mama.auth.MongoUserRepository;
import com.jarec.mesaj.mama.server.ClientConnectionHandler;
import com.jarec.mesaj.mama.server.MamaConfiguration;
import com.jarec.mesaj.mama.server.TlsContextFactory;
import com.jarec.mesaj.mama.server.TlsSocketServer;
import com.jarec.mesaj.mama.session.SessionRegistry;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;

import javax.net.ssl.SSLContext;

public final class MamaApplication {
    private MamaApplication() {
    }

    static void main() throws Exception {
        MamaConfiguration configuration = MamaConfiguration.fromEnvironment(System.getenv());
        SSLContext sslContext = TlsContextFactory.fromPkcs12(
                configuration.tlsKeyStore(), configuration.tlsKeyStorePassword());
        try (MongoClient mongoClient = MongoClients.create(configuration.mongoUri())) {
            MongoDatabase database = mongoClient.getDatabase("mesaj");
            AuthService authService = new AuthService(new MongoUserRepository(database));
            SessionRegistry sessionRegistry = new SessionRegistry();
            ClientConnectionHandler connectionHandler =
                    new ClientConnectionHandler(authService, sessionRegistry);
            try (TlsSocketServer server = new TlsSocketServer(
                    sslContext.getServerSocketFactory(), configuration.port(), connectionHandler)) {
                Runtime.getRuntime().addShutdownHook(new Thread(() -> closeServer(server), "mama-shutdown"));
                server.start();
            }
        }
    }

    private static void closeServer(TlsSocketServer server) {
        try {
            server.close();
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Failed to stop Mama TLS server", exception);
        }
    }
}
