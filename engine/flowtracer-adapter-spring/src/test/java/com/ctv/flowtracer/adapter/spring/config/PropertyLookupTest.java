package com.ctv.flowtracer.adapter.spring.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctv.flowtracer.adapter.spring.config.PropertyLookup.Reason;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PropertyLookupTest {

    private static final String YML = "shop-api/src/main/resources/application.yml";
    private static final String SRC = "shop-api/src/main/java/A.java";

    private PropertyLookup lookup(Map<String, String> files) {
        return new PropertyLookup(files.keySet().stream().sorted().toList(), p -> Optional.ofNullable(files.get(p)));
    }

    @Test
    void configWinsOverCodeDefault() {
        var l = lookup(Map.of(YML, "shop:\n  path: /from-config\n"));

        var r = l.resolve("${shop.path:/from-code}", SRC);

        assertThat(r.resolved()).isTrue();
        assertThat(r.text()).isEqualTo("/from-config");
        assertThat(r.sources()).containsExactly("shop.path = /from-config (" + YML + ")");
    }

    @Test
    void codeDefaultIsUsedOnlyWhenConfigHasNoKey() {
        var r = lookup(Map.of(YML, "shop:\n  other: /x\n")).resolve("${shop.path:/from-code}", SRC);

        assertThat(r.text()).isEqualTo("/from-code");
        assertThat(r.sources()).containsExactly("shop.path = /from-code (mặc định trong code)");
    }

    @Test
    void absentKeyWithoutDefaultStaysUnresolvedAndKeepsPlaceholder() {
        var r = lookup(Map.of(YML, "a: b\n")).resolve("/base/${shop.path}", SRC);

        assertThat(r.resolved()).isFalse();
        assertThat(r.failure()).isEqualTo(Reason.ABSENT);
        assertThat(r.failedKey()).isEqualTo("shop.path");
        assertThat(r.text()).isEqualTo("/base/${shop.path}");
    }

    @Test
    void environmentVariableInConfigWithDefaultUsesTheDefaultWithoutDefaultIsUnresolved() {
        var l = lookup(Map.of(YML, "shop:\n  a: ${A_PATH:/a-default}\n  b: ${B_PATH}\n"));

        assertThat(l.resolve("${shop.a}", SRC).text()).isEqualTo("/a-default");
        var b = l.resolve("${shop.b}", SRC);
        assertThat(b.failure()).isEqualTo(Reason.ENVIRONMENT);
        assertThat(b.failedKey()).isEqualTo("B_PATH");
    }

    @Test
    void propertyReferencingAnotherPropertyIsFollowed() {
        var r = lookup(Map.of(YML, "base: /api\nshop:\n  path: ${base}/orders\n")).resolve("${shop.path}", SRC);

        assertThat(r.text()).isEqualTo("/api/orders");
    }

    @Test
    void cyclesAreStopped() {
        var r = lookup(Map.of(YML, "a: ${b}\nb: ${a}\n")).resolve("${a}", SRC);

        assertThat(r.failure()).isEqualTo(Reason.CYCLE);
    }

    @Test
    void sensitiveKeysAreNeverRead() {
        var l = lookup(Map.of(
                YML, "x:\n  api-token: /t\n  userToken: /t\n  my_password: /p\n  keyPath: /k\n  secret-path: abc123\n  jwt-token-path: /token\n"));

        assertThat(l.resolve("${x.api-token}", SRC).failure()).isEqualTo(Reason.BLOCKED);
        assertThat(l.resolve("${x.userToken}", SRC).failure()).isEqualTo(Reason.BLOCKED);
        assertThat(l.resolve("${x.my_password}", SRC).failure()).isEqualTo(Reason.BLOCKED);
        // khóa nhạy cảm nhưng đoạn cuối là "path": chỉ nhận khi giá trị bắt đầu bằng "/"
        assertThat(l.resolve("${x.jwt-token-path}", SRC).text()).isEqualTo("/token");
        assertThat(l.resolve("${x.secret-path}", SRC).failure()).isEqualTo(Reason.BLOCKED);
        // "key" và "auth" không còn nằm trong danh sách chặn: đây là khóa đường dẫn hợp lệ
        assertThat(l.resolve("${x.keyPath}", SRC).text()).isEqualTo("/k");
        assertThat(PropertyLookup.isBlocked("smartshop.auth.login-path")).isFalse();
    }

    @Test
    void valuesThatDoNotLookLikePathsAreRejected() {
        var l = lookup(Map.of(YML, "x:\n  a: hello world\n  b: \"jdbc:mysql://h/db\"\n  c: " + "/p".repeat(150) + "\n"));

        assertThat(l.resolve("${x.a}", SRC).failure()).isEqualTo(Reason.BAD_VALUE);
        assertThat(l.resolve("${x.b}", SRC).failure()).isEqualTo(Reason.BAD_VALUE);
        assertThat(l.resolve("${x.c}", SRC).failure()).isEqualTo(Reason.BAD_VALUE);
    }

    @Test
    void keyOnlyInProfileFileIsNotUsed() {
        var r = lookup(Map.of(YML, "a: b\n", "shop-api/src/main/resources/application-dev.yml", "shop:\n  path: /dev\n"))
                .resolve("${shop.path}", SRC);

        assertThat(r.failure()).isEqualTo(Reason.PROFILE_ONLY);
        assertThat(r.text()).isEqualTo("${shop.path}");
    }

    @Test
    void usesTheConfigOfTheModuleThatOwnsTheSourceFile() {
        var l = lookup(Map.of(
                "a/src/main/resources/application.yml", "p: /from-a\n",
                "b/src/main/resources/application.properties", "p=/from-b\n",
                "src/main/resources/application.yml", "p: /from-root\n"));

        assertThat(l.resolve("${p}", "a/src/main/java/X.java").text()).isEqualTo("/from-a");
        assertThat(l.resolve("${p}", "b/src/main/java/X.java").text()).isEqualTo("/from-b");
        assertThat(l.resolve("${p}", "src/main/java/X.java").text()).isEqualTo("/from-root");
    }

    @Test
    void firstKeyOfAFileWithBomIsFound() {
        var r = lookup(Map.of(YML, "\uFEFFshop:\n    path: /bom\n")).resolve("${shop.path}", SRC);

        assertThat(r.text()).isEqualTo("/bom");
    }

    @Test
    void textWithoutPlaceholderIsReturnedAsIs() {
        var r = lookup(Map.of()).resolve("/plain/path", SRC);

        assertThat(r.resolved()).isTrue();
        assertThat(r.text()).isEqualTo("/plain/path");
        assertThat(r.sources()).isEmpty();
    }
}
