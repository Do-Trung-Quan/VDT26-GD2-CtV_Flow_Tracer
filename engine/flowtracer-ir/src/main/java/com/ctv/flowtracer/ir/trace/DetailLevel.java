package com.ctv.flowtracer.ir.trace;

/** Mức chi tiết khi hiển thị sequence diagram. */
public enum DetailLevel {
    /** Gộp theo Người dùng / Frontend / Backend / CSDL. */
    LAYER,
    /** Mỗi class là một participant (mặc định). */
    COMPONENT,
    /** Hiện cả lời gọi method nội bộ. */
    DETAIL
}
