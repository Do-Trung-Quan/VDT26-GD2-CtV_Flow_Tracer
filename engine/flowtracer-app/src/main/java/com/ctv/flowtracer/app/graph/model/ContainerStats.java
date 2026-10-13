package com.ctv.flowtracer.app.graph.model;

/**
 * Thống kê bên trong một thành phần chứa (màn hình, hộp thoại, ứng dụng…).
 *
 * @param elements số phần tử tương tác
 * @param callingApi số phần tử gọi API (thao tác dẫn tới lời gọi HTTP)
 * @param dialogs số hộp thoại
 */
public record ContainerStats(int elements, int callingApi, int dialogs) {}
