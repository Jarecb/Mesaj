package com.jarec.mesaj.mama.server;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class MamaConfigurationTest {
    @Test
    void loadsRequiredSettingsAndProtectsPasswordArray() {
        Path keyStore = Path.of("certs/mama.p12");
        MamaConfiguration configuration = MamaConfiguration.fromEnvironment(Map.of(
                "MONGODB_URI", "mongodb://localhost/mesaj",
                "MAMA_TLS_KEYSTORE_PATH", "certs/mama.p12",
                "MAMA_TLS_KEYSTORE_PASSWORD", "secret"));
        char[] returnedPassword = configuration.tlsKeyStorePassword();
        returnedPassword[0] = 'x';

        assertThat(configuration)
                .returns(9090, MamaConfiguration::port)
                .returns("mongodb://localhost/mesaj", MamaConfiguration::mongoUri)
                .returns(keyStore, MamaConfiguration::tlsKeyStore);
        assertThat(configuration.tlsKeyStorePassword()).containsExactly("secret".toCharArray());
        MamaConfiguration equivalent = new MamaConfiguration(9090, "mongodb://localhost/mesaj",
                keyStore, "secret".toCharArray());
        assertThat(configuration).isEqualTo(equivalent).hasSameHashCodeAs(equivalent);
        assertThat(configuration.toString()).contains("tlsKeyStorePassword=[redacted]")
                .doesNotContain("secret")
                .doesNotContain("mongodb://localhost/mesaj");
        assertThat(configuration).isNotEqualTo(new MamaConfiguration(9090, "mongodb://localhost/mesaj",
                keyStore, "different".toCharArray()));
    }

    @Test
    @SuppressWarnings("DataFlowIssue")
    void rejectsMissingAndInvalidConfiguration() {
        assertThatIllegalArgumentException().isThrownBy(() -> MamaConfiguration.fromEnvironment(null));
        assertThatIllegalArgumentException().isThrownBy(() -> MamaConfiguration.fromEnvironment(Map.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> MamaConfiguration.fromEnvironment(Map.of(
                "MONGODB_URI", "mongodb://localhost/mesaj",
                "MAMA_TLS_KEYSTORE_PATH", "certs/mama.p12",
                "MAMA_TLS_KEYSTORE_PASSWORD", "secret",
                "MAMA_PORT", "not-a-port")));
        assertThatIllegalArgumentException().isThrownBy(() -> new MamaConfiguration(0,
                "mongodb://localhost/mesaj", Path.of("cert.p12"), "secret".toCharArray()));
    }
}
