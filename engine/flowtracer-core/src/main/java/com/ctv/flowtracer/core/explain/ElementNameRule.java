package com.ctv.flowtracer.core.explain;

import java.util.regex.Pattern;

/**
 * Kiểm tra tên AI đặt cho một thành phần giao diện: ngắn, là chữ người đọc được (không phải mã định danh) và không lặp lại tiền
 * tố loại ("Nút", "Ô"…) vì giao diện tự thêm tiền tố đó. Quy tắc thuần, test được.
 */
public final class ElementNameRule {

    public static final int MAX_LENGTH = 60;

    private static final Pattern TYPE_PREFIX =
            Pattern.compile("^(?:Nút|Ô nhập|Ô chọn ngày|Ô ngày|Ô chọn|Ô|Liên kết|Hộp chọn|Công tắc|Bảng|Phân trang|Phần tử)\\s+(?=\\S)", Pattern.CASE_INSENSITIVE);
    /** camelCase / snake_case / có dấu chấm hoặc ngoặc: trông như tên biến hay hàm. */
    private static final Pattern IDENTIFIER_LIKE =
            Pattern.compile("^[a-z]+[A-Z]\\w*$|^\\w+_\\w+$|[(){}<>=;]|\\w\\.\\w");

    /** @return tên đã làm sạch; null nếu không đạt (người gọi giữ tên cũ) */
    public String clean(String label) {
        if (label == null) {
            return null;
        }
        String text = label.strip().replaceAll("\\s+", " ");
        text = text.replaceAll("^[\"“”']+|[\"“”']+$", "").strip();
        String stripped = TYPE_PREFIX.matcher(text).replaceFirst("");
        if (!stripped.isBlank()) {
            text = stripped;
        }
        if (text.length() < 2 || text.length() > MAX_LENGTH || IDENTIFIER_LIKE.matcher(text).find()) {
            return null;
        }
        return text.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + text.substring(1);
    }
}
