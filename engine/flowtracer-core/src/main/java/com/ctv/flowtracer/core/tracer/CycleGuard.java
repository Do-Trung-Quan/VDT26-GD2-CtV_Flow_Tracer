package com.ctv.flowtracer.core.tracer;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Chặn vòng lặp và giới hạn độ sâu khi dò vết. Độ sâu chỉ tính các cấp mang ý nghĩa nghiệp vụ (Controller / Service /
 * Repository gọi nhau); hàm được gộp vào bước tự xử lý (private, entity, tiện ích) không tốn cấp — nếu không, một chuỗi hàm
 * phụ sẽ cắt mất lời gọi thật sự sang Service / Repository nằm sâu bên trong. Có thêm giới hạn cứng chống chạy vô tận.
 */
public final class CycleGuard {

    /** Hệ số của giới hạn cứng so với số cấp nghiệp vụ tối đa. */
    private static final int HARD_LIMIT_FACTOR = 4;
    private static final int HARD_LIMIT_BASE = 20;

    private record Frame(String key, boolean level) {}

    private final int maxLevels;
    private final int hardLimit;
    private final Deque<Frame> path = new ArrayDeque<>();
    private int levels;

    public CycleGuard(int maxLevels) {
        this.maxLevels = maxLevels;
        this.hardLimit = maxLevels * HARD_LIMIT_FACTOR + HARD_LIMIT_BASE;
    }

    /**
     * @param level true nếu đây là một cấp nghiệp vụ (tính vào độ sâu tối đa)
     * @return false nếu đi vào sẽ tạo vòng lặp hoặc vượt giới hạn
     */
    public boolean enter(String nodeKey, boolean level) {
        if (isOnPath(nodeKey) || path.size() >= hardLimit || level && levels >= maxLevels) {
            return false;
        }
        path.push(new Frame(nodeKey, level));
        if (level) {
            levels++;
        }
        return true;
    }

    /** true nếu node đang nằm trên đường đi hiện tại (tức là vào lại sẽ tạo vòng lặp). */
    public boolean isOnPath(String nodeKey) {
        return path.stream().anyMatch(f -> f.key().equals(nodeKey));
    }

    public void exit() {
        if (path.pop().level()) {
            levels--;
        }
    }
}
