package com.ctv.flowtracer.core.explain;

import com.ctv.flowtracer.ir.IrNode;
import java.util.List;
import java.util.Map;

/**
 * Mục đích dự phòng khi AI tắt, lỗi hoặc chưa có bản lưu: {@code @Operation} → Javadoc → câu khung tất định. (Tài liệu đặc tả
 * của dự án — bước thứ ba trong chuỗi — chưa được hỗ trợ: Workspace chưa có nơi khai báo thư mục tài liệu.) Thành phần chứa
 * chỉ có tên, loại và một câu mô tả ngắn.
 */
public final class PurposeRule {

    /** Nguồn của đoạn mục đích; {@code AI} do cổng AI thêm ở Giai đoạn 7. */
    public enum Source {
        OPERATION("Swagger @Operation"),
        JAVADOC("Javadoc"),
        SKELETON("Câu khung");

        private final String label;

        Source(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** Một đoạn mục đích kèm nguồn. */
    public record Purpose(String text, Source source) {}

    /** Mục đích của một API Backend. */
    public Purpose forEndpoint(IrNode endpoint) {
        Map<String, Object> a = endpoint.attrs();
        String summary = text(a.get("summary"));
        String source = text(a.get("summarySource"));
        if (summary != null && "OPERATION".equals(source)) {
            String description = text(a.get("description"));
            return new Purpose(description != null ? withStop(summary) + " " + description : summary, Source.OPERATION);
        }
        if (summary != null && "JAVADOC".equals(source)) {
            return new Purpose(summary, Source.JAVADOC);
        }
        return new Purpose(endpointSkeleton(endpoint), Source.SKELETON);
    }

    /**
     * Mục đích của một phần tử giao diện: câu khung nói phần tử gọi API nào, hoặc chỉ thay đổi giao diện.
     *
     * @param apis API phần tử gọi (đã xác định đích)
     * @param unresolved lời gọi HTTP chưa xác định được API đích — phần tử vẫn gọi máy chủ
     * @param opens hộp thoại / màn hình nó mở (tên hiển thị)
     */
    public Purpose forElement(IrNode element, String label, List<IrNode> apis, List<IrNode> unresolved, List<String> opens) {
        String subject = noun(element) + " “" + label + "”";
        String pending = unresolved.isEmpty()
                ? ""
                : (apis.isEmpty() ? subject + " gọi máy chủ nhưng" : " Ngoài ra còn") + " " + unresolved.size()
                        + " lời gọi chưa xác định được API đích (" + String.join("; ", unresolved.stream().limit(3).map(IrNode::name).toList())
                        + (unresolved.size() > 3 ? "; …" : "") + "); hãy chốt tay liên kết trên diagram.";
        if (!apis.isEmpty()) {
            String list = String.join("; ", apis.stream().limit(3).map(PurposeRule::apiName).toList());
            String more = apis.size() > 3 ? " và " + (apis.size() - 3) + " API khác" : "";
            return new Purpose(subject + " dùng để gọi " + (apis.size() == 1 ? "API " : "các API: ") + list + more + "." + pending, Source.SKELETON);
        }
        if (!unresolved.isEmpty()) {
            return new Purpose(pending, Source.SKELETON);
        }
        if (!opens.isEmpty()) {
            return new Purpose(subject + " chỉ thay đổi giao diện: mở " + String.join(", ", opens) + "; không gọi máy chủ.", Source.SKELETON);
        }
        return new Purpose(subject + " chỉ thay đổi giao diện, không gọi máy chủ.", Source.SKELETON);
    }

    /** Mô tả ngắn của thành phần chứa (màn hình, hộp thoại, ứng dụng, tab, form). */
    public Purpose forContainer(String typeLabel, String name, int elements, int callingApi, int dialogs) {
        var text = new StringBuilder(typeLabel + " " + name);
        text.append(elements == 0 ? " chưa có phần tử tương tác nào" : " gồm " + elements + " phần tử tương tác, trong đó " + callingApi + " gọi API");
        if (dialogs > 0) {
            text.append("; có ").append(dialogs).append(" hộp thoại");
        }
        return new Purpose(text.append('.').toString(), Source.SKELETON);
    }

    private static String endpointSkeleton(IrNode endpoint) {
        Map<String, Object> a = endpoint.attrs();
        var text = new StringBuilder("API " + endpoint.name());
        if (a.get("module") instanceof String module && !module.isBlank()) {
            text.append(" thuộc nhóm “").append(module).append('”');
        }
        List<String> inputs = TypeExprs.maps(a.get("params")).stream()
                .filter(p -> "PATH".equals(p.get("in")) || "QUERY".equals(p.get("in")))
                .map(p -> String.valueOf(p.get("name")))
                .toList();
        String body = a.get("requestType") instanceof String r ? r : null;
        var takes = new java.util.ArrayList<String>(inputs);
        if (body != null) {
            takes.add("dữ liệu " + body);
        }
        text.append(takes.isEmpty() ? ": không nhận tham số" : ": nhận " + String.join(", ", takes));
        text.append(a.get("responseType") instanceof String r ? ", trả về " + r : ", không trả nội dung");
        return text.append('.').toString();
    }

    private static String apiName(IrNode api) {
        String summary = text(api.attrs().get("summary"));
        return summary != null ? "“" + summary + "” (" + api.name() + ")" : api.name();
    }

    private static String noun(IrNode element) {
        return switch (String.valueOf(element.attrs().get("elementType"))) {
            case "button" -> "Nút";
            case "link" -> "Liên kết";
            case "input", "textarea" -> "Ô nhập";
            case "select" -> "Ô chọn";
            case "checkbox", "radio", "toggle" -> "Ô tích chọn";
            case "date" -> "Ô chọn ngày";
            case "table" -> "Bảng";
            case "pagination" -> "Phân trang";
            default -> "Phần tử";
        };
    }

    private static String withStop(String s) {
        return s.endsWith(".") ? s : s + ".";
    }

    private static String text(Object value) {
        return value instanceof String s && !s.isBlank() ? s.strip() : null;
    }
}
