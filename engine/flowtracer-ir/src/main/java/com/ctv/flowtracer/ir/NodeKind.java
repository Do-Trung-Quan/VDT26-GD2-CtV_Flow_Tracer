package com.ctv.flowtracer.ir;

/**
 * Loại thành phần (node) trên bản đồ IR. Danh sách phải khớp {@code contracts/ir-fragment.schema.json}
 * và §2.2.1 của overview.
 */
public enum NodeKind {
    UI_APP(Side.FRONTEND),
    UI_SCREEN(Side.FRONTEND),
    UI_TAB(Side.FRONTEND),
    UI_DIALOG(Side.FRONTEND),
    UI_FORM(Side.FRONTEND),
    UI_ELEMENT(Side.FRONTEND),
    FE_METHOD(Side.FRONTEND),
    /** Điểm gọi HTTP: phía FE, hoặc phía BE gọi service khác / hệ thống ngoài (xem {@code attrs.origin}). */
    HTTP_CALL(Side.OTHER),
    API_ENDPOINT(Side.BACKEND),
    BE_METHOD(Side.BACKEND),
    DATA_QUERY(Side.BACKEND),
    DB_TABLE(Side.DATA),
    DATA_TYPE(Side.BACKEND),
    EXTERNAL(Side.OTHER);

    /** Phía của thành phần (FE / BE / dữ liệu / khác), dùng khi dựng cây và xếp cột diagram. */
    public enum Side {
        FRONTEND,
        BACKEND,
        DATA,
        OTHER
    }

    private final Side side;

    NodeKind(Side side) {
        this.side = side;
    }

    public Side side() {
        return side;
    }
}
