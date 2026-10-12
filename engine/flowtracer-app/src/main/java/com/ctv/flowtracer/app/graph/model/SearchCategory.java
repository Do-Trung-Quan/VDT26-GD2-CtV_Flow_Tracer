package com.ctv.flowtracer.app.graph.model;

import com.ctv.flowtracer.ir.NodeKind;
import java.util.List;

/** Nhóm kết quả tìm kiếm: gom các loại node mà người dùng coi là một loại thành phần. */
public enum SearchCategory {
    API("API Backend", List.of(NodeKind.API_ENDPOINT)),
    SCREEN("Màn hình", List.of(NodeKind.UI_APP, NodeKind.UI_SCREEN, NodeKind.UI_TAB, NodeKind.UI_DIALOG, NodeKind.UI_FORM)),
    ELEMENT("Phần tử giao diện", List.of(NodeKind.UI_ELEMENT)),
    TABLE("Bảng dữ liệu", List.of(NodeKind.DB_TABLE)),
    CODE("Hàm / lớp", List.of(NodeKind.BE_METHOD, NodeKind.FE_METHOD));

    private final String label;
    private final List<NodeKind> kinds;

    SearchCategory(String label, List<NodeKind> kinds) {
        this.label = label;
        this.kinds = kinds;
    }

    public String label() {
        return label;
    }

    public List<NodeKind> kinds() {
        return kinds;
    }

    /** Nhóm của một loại node; null nếu loại đó không tìm kiếm được. */
    public static SearchCategory of(NodeKind kind) {
        for (SearchCategory c : values()) {
            if (c.kinds.contains(kind)) {
                return c;
            }
        }
        return null;
    }
}
