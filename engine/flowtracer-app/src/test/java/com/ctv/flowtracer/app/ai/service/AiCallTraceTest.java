package com.ctv.flowtracer.app.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.ctv.flowtracer.app.ai.provider.LlmRequest;
import com.ctv.flowtracer.app.ai.provider.LlmResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/** Nhật ký Engine cho mọi lời gọi AI: có mục đích, prompt, kết quả; tắt nội dung thì chỉ còn dòng tóm tắt. */
class AiCallTraceTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger("flowtracer.ai");
    private final ListAppender<ILoggingEvent> lines = new ListAppender<>();

    @BeforeEach
    void attach() {
        lines.start();
        logger.addAppender(lines);
    }

    @AfterEach
    void detach() {
        logger.detachAppender(lines);
    }

    @Test
    void logsPurposePromptAndResult() {
        var trace = new AiCallTrace(true);
        trace.request("MOCK_DATA", "api-1", "gemini", "m", new LlmRequest("hệ thống", "người dùng", null, 10));
        trace.response("MOCK_DATA", new LlmResult("{\"ok\":1}", 12, 3, "m"), 1500);

        assertThat(lines.list).extracting(ILoggingEvent::getFormattedMessage).anySatisfy(m -> assertThat(m).contains("MOCK_DATA", "api-1", "hệ thống", "người dùng"))
                .anySatisfy(m -> assertThat(m).contains("{\"ok\":1}", "1.5 giây", "token vào 12 / ra 3"));
    }

    @Test
    void contentCanBeSwitchedOff() {
        var trace = new AiCallTrace(false);
        trace.request("MOCK_DATA", "api-1", "gemini", "m", new LlmRequest("hệ thống", "mã nguồn bí mật", null, 10));
        trace.response("MOCK_DATA", new LlmResult("kết quả riêng", 1, 1, "m"), 10);

        assertThat(lines.list).extracting(ILoggingEvent::getFormattedMessage).noneMatch(m -> m.contains("mã nguồn bí mật") || m.contains("kết quả riêng"));
    }
}
