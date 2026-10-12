package com.ctv.flowtracer.app.tracing.model;

import com.ctv.flowtracer.app.common.error.AppException;
import com.ctv.flowtracer.app.common.error.ErrorCode;
import java.util.Locale;

/** Định dạng xuất diagram (API D2). Ảnh PNG do giao diện chuyển từ SVG ngay trên trình duyệt. */
public enum ExportFormat {
    PLANTUML("puml", "text/vnd.plantuml"),
    SVG("svg", "image/svg+xml");

    private final String extension;
    private final String mediaType;

    ExportFormat(String extension, String mediaType) {
        this.extension = extension;
        this.mediaType = mediaType;
    }

    public String extension() {
        return extension;
    }

    public String mediaType() {
        return mediaType;
    }

    /**
     * @param text {@code plantuml} hoặc {@code svg}, không phân biệt hoa thường
     * @throws AppException {@code VALIDATION_FAILED} nếu không nhận ra
     */
    public static ExportFormat parse(String text) {
        try {
            return valueOf(text.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Định dạng xuất phải là plantuml hoặc svg");
        }
    }
}
