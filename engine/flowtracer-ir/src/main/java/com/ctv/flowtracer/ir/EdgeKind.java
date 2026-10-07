package com.ctv.flowtracer.ir;

/** Loại quan hệ (edge) giữa hai node. Khớp §2.2.2 của overview. */
public enum EdgeKind {
    /** App → Screen → Tab/Dialog/Form → Element (dựng cây FE). */
    CONTAINS,
    /** UI_ELEMENT → FE_METHOD: sự kiện gọi handler. */
    TRIGGERS,
    /** Method → Method. */
    CALLS,
    /** FE_METHOD → UI_DIALOG. */
    OPENS,
    /** FE_METHOD → UI_SCREEN. */
    NAVIGATES,
    /** FE_METHOD → HTTP_CALL. */
    SENDS,
    /** HTTP_CALL → API_ENDPOINT (do Linker tạo). */
    HTTP_REQUEST,
    /** API_ENDPOINT → BE_METHOD (controller). */
    HANDLED_BY,
    /** BE_METHOD → DATA_QUERY. */
    EXECUTES,
    /** DATA_QUERY → DB_TABLE (đọc). */
    READS,
    /** DATA_QUERY → DB_TABLE (ghi). */
    WRITES,
    /** Method/Endpoint → DATA_TYPE. */
    USES_TYPE
}
