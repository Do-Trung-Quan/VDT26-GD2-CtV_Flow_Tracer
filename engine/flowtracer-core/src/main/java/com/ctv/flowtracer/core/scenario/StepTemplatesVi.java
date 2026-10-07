package com.ctv.flowtracer.core.scenario;

import com.ctv.flowtracer.ir.EdgeKind;
import java.util.EnumMap;
import java.util.Map;

/** Mẫu câu tiếng Việt theo loại edge cho tab Kịch bản. {from}, {to}, {label} được thay khi dựng câu. */
public final class StepTemplatesVi {

    private static final Map<EdgeKind, String> TEMPLATES = new EnumMap<>(EdgeKind.class);

    static {
        TEMPLATES.put(EdgeKind.TRIGGERS, "{from} bấm {label} trên {to}.");
        TEMPLATES.put(EdgeKind.CALLS, "{from} gọi {to} để {label}.");
        TEMPLATES.put(EdgeKind.OPENS, "{from} mở {to}.");
        TEMPLATES.put(EdgeKind.NAVIGATES, "{from} chuyển sang {to}.");
        TEMPLATES.put(EdgeKind.SENDS, "{from} chuẩn bị yêu cầu gửi tới máy chủ.");
        TEMPLATES.put(EdgeKind.HTTP_REQUEST, "{from} gửi yêu cầu {label} tới {to}.");
        TEMPLATES.put(EdgeKind.HANDLED_BY, "{from} chuyển yêu cầu cho {to}.");
        TEMPLATES.put(EdgeKind.EXECUTES, "{from} truy vấn dữ liệu qua {to}.");
        TEMPLATES.put(EdgeKind.READS, "{from} đọc dữ liệu từ {to}.");
        TEMPLATES.put(EdgeKind.WRITES, "{from} ghi dữ liệu vào {to}.");
    }

    private StepTemplatesVi() {}

    public static String template(EdgeKind kind) {
        return TEMPLATES.getOrDefault(kind, "{from} → {to}.");
    }

    /** Câu cho chiều trả về. */
    public static String returning() {
        return "{from} trả kết quả về {to}.";
    }
}
