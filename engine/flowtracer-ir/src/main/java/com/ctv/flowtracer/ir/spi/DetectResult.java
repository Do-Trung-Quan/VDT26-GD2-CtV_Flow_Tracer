package com.ctv.flowtracer.ir.spi;

/**
 * Kết quả nhận diện của một adapter trên một Source.
 *
 * @param score mức độ phù hợp 0..1 (0 = không phải công nghệ này)
 * @param stackName tên hiển thị, vd "Spring Boot"
 * @param version phiên bản nhận diện được (có thể null)
 * @param reason dấu hiệu đã dùng, vd "pom.xml có spring-boot-starter-parent 3.3.3"
 */
public record DetectResult(double score, String stackName, String version, String reason) {

    public DetectResult {
        if (score < 0 || score > 1) {
            throw new IllegalArgumentException("score phải trong [0, 1]");
        }
    }

    public static DetectResult none() {
        return new DetectResult(0, null, null, null);
    }

    public boolean matched() {
        return score > 0;
    }
}
