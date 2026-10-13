package com.ctv.flowtracer.app.graph.model;

import com.ctv.flowtracer.ir.NodeKind;

/** Một thành phần nằm bên trong một thành phần chứa (theo cạnh {@code CONTAINS}, mọi cấp). */
public record DescendantRow(String key, NodeKind kind) {}
