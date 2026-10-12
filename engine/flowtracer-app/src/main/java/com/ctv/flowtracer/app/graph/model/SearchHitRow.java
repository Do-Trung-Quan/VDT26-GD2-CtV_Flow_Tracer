package com.ctv.flowtracer.app.graph.model;

/** Một dòng kết quả tìm kiếm cùng tổng số khớp của nhóm. */
public record SearchHitRow(IrNodeRow row, long categoryTotal) {}
