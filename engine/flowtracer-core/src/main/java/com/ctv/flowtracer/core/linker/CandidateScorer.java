package com.ctv.flowtracer.core.linker;

/**
 * Điểm tất định của một cặp (lời gọi HTTP, endpoint) cộng từ các đặc trưng có trọng số. Điểm KHÔNG phải xác suất đúng nên
 * giao diện chỉ hiện ba mức {@link Level}. Trọng số đặt sao cho đoạn cố định thắng đoạn biến: {@code /items/export-excel}
 * khớp {@code /items/export-excel} được 20 điểm, khớp {@code /items/{id}} chỉ được 16.
 */
public final class CandidateScorer {

    /** Hai đoạn literal bằng nhau. */
    static final int LITERAL_LITERAL = 10;
    /** Biến ở lời gọi gặp biến ở endpoint ({@code ${id}} ↔ {@code {id}}). */
    static final int VARIABLE_VARIABLE = 8;
    /** Đoạn trộn literal + biến khớp biểu thức của bên kia. */
    static final int MIXED = 7;
    /** Giá trị cố định ở lời gọi rơi vào biến của endpoint. */
    static final int LITERAL_VARIABLE = 6;
    /** Biến ở lời gọi gặp đoạn cố định ở endpoint: có thể đúng nhưng không chắc. */
    static final int VARIABLE_LITERAL = 3;
    /** Endpoint nhận mọi phương thức ({@code @RequestMapping} không khai phương thức). */
    static final int ANY_METHOD_PENALTY = 4;
    /** Mỗi luật phải áp để khớp. */
    static final int RULE_PENALTY = 1;
    /** Phần đầu URL không tính được, chỉ so khớp phần cuối. */
    static final int SUFFIX_PENALTY = 6;
    /** Mỗi đoạn endpoint mà phần đầu không tính được của lời gọi "nuốt" vào. */
    static final int ABSORBED = 2;
    /** Hai ứng viên cách nhau ít hơn mức này được coi là ngang điểm. */
    public static final int TIE_MARGIN = 2;

    /** Mức khớp hiển thị cho người dùng. */
    public enum Level {
        HIGH("Cao"),
        MEDIUM("Trung bình"),
        LOW("Thấp");

        private final String label;

        Level(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private CandidateScorer() {}

    /** Điểm chuẩn hóa về [0, 1] theo số đoạn dài hơn của hai bên (mỗi đoạn tối đa {@link #LITERAL_LITERAL} điểm). */
    public static double normalize(int score, int segmentCount) {
        int max = Math.max(1, segmentCount) * LITERAL_LITERAL;
        double value = Math.max(0, Math.min(score, max)) / (double) max;
        return Math.round(value * 100) / 100.0;
    }

    /** Mức khớp: Cao ≥ 0,85; Trung bình ≥ 0,6; còn lại Thấp. */
    public static Level level(double normalizedScore) {
        if (normalizedScore >= 0.85) {
            return Level.HIGH;
        }
        return normalizedScore >= 0.6 ? Level.MEDIUM : Level.LOW;
    }
}
