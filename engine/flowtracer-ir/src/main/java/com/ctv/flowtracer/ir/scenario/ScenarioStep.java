package com.ctv.flowtracer.ir.scenario;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Objects;

/**
 * Một bước trong tab Kịch bản, theo mẫu "A + hành động + tới C".
 *
 * @param number số thứ tự hiển thị: "1", "2", "3a"…
 * @param text câu mô tả bằng lời (tiếng Việt)
 * @param stepIndex chỉ số {@code TraceStep} tương ứng để đồng bộ highlight (null với bước con chỉ mô tả)
 * @param technical chi tiết kỹ thuật, chỉ hiện khi bật công tắc
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ScenarioStep(String number, String text, Integer stepIndex, TechnicalDetail technical) {

    public ScenarioStep {
        Objects.requireNonNull(number, "number");
        Objects.requireNonNull(text, "text");
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TechnicalDetail(String method, String httpPath, String filePath, Integer line) {}
}
