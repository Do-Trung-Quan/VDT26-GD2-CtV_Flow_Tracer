package com.ctv.flowtracer.app.graph.model;

/** Một cạnh chứa-trong của cây giao diện: {@code parentKey} chứa {@code childKey}, {@code position} là thứ tự trong nút cha. */
public record ContainsRow(String parentKey, String childKey, int position) {}
