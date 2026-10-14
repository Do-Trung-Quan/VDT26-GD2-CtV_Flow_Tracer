package com.ctv.flowtracer.app.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctv.flowtracer.app.ai.service.SecretRedactor;
import org.junit.jupiter.api.Test;

class SecretRedactorTest {

    private final SecretRedactor redactor = new SecretRedactor();

    @Test
    void masksValuesOfSensitiveKeysButKeepsTheNames() {
        String code = """
                spring.datasource.password=Sup3rS3cret
                String apiKey = "abc123XYZ";
                jwt.secret: topsecretvalue
                {"access_token": "eyJhbGciOi"}
                String keyword = "tìm kiếm";
                """;

        String out = redactor.redact(code);

        assertThat(out).contains("spring.datasource.password=***", "String apiKey = \"***\"", "jwt.secret: ***", "\"access_token\": \"***\"")
                .doesNotContain("Sup3rS3cret", "abc123XYZ", "topsecretvalue", "eyJhbGciOi");
        assertThat(out).contains("String keyword = \"tìm kiếm\""); // "keyword" không phải khóa nhạy cảm
    }

    @Test
    void masksConnectionStringsPrivateKeysAndKnownTokens() {
        String text = "url = jdbc:postgresql://db.internal:5432/app?user=a&password=b\n"
                + "-----BEGIN RSA PRIVATE KEY-----\nMIIEow\nxyz\n-----END RSA PRIVATE KEY-----\n"
                + "Authorization: Bearer abcdefghijklmnop12345\n"
                + "gemini=AIzaSyA1234567890abcdefghijklmnopqrstuv";

        String out = redactor.redact(text);

        assertThat(out).doesNotContain("db.internal", "MIIEow", "abcdefghijklmnop12345", "AIzaSyA1234567890");
        assertThat(out).contains("jdbc:postgresql://***");
    }

    @Test
    void leavesOrdinaryCodeUntouchedAndHandlesNull() {
        String code = "public Page<Category> search(String keyword, int page) { return repo.findAll(); }";

        assertThat(redactor.redact(code)).isEqualTo(code);
        assertThat(redactor.redact(null)).isNull();
    }

    @Test
    void neverSendsEnvKeystoreOrDataFiles() {
        assertThat(SecretRedactor.isSendable("src/main/java/A.java")).isTrue();
        assertThat(SecretRedactor.isSendable(".env")).isFalse();
        assertThat(SecretRedactor.isSendable("config/.env.production")).isFalse();
        assertThat(SecretRedactor.isSendable("certs/server.jks")).isFalse();
        assertThat(SecretRedactor.isSendable("data/export.csv")).isFalse();
    }
}
