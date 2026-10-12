package com.ctv.flowtracer.app.graph.model;

/** Một phần tử giao diện, qua hàm xử lý, mở một hộp thoại hoặc chuyển tới một màn hình ({@code targetKey}). */
public record UiLinkRow(String elementKey, String targetKey) {}
