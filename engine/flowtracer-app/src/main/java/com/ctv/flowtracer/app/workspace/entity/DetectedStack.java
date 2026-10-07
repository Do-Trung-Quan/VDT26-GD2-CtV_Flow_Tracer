package com.ctv.flowtracer.app.workspace.entity;

/** Một công nghệ nhận diện được trên Source (lưu trong {@code sources.detected_stacks}). */
public record DetectedStack(String adapter, String stackName, String version, double score) {}
