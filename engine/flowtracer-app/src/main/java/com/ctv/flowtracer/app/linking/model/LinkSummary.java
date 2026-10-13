package com.ctv.flowtracer.app.linking.model;

/**
 * Kết quả một lần nối lời gọi HTTP với API đích.
 *
 * @param calls số lời gọi HTTP trong Workspace
 * @param certain số liên kết chắc chắn
 * @param inferred số liên kết suy luận (kể cả tới hệ thống ngoài)
 * @param unresolved số lời gọi chưa xác định được đích
 * @param external số lời gọi tới hệ thống ngoài Workspace
 * @param changed số lời gọi có đích hoặc mức tin cậy khác lần nối trước
 */
public record LinkSummary(int calls, int certain, int inferred, int unresolved, int external, int changed) {}
