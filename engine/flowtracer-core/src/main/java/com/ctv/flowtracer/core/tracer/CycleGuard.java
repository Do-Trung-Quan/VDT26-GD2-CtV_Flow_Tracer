package com.ctv.flowtracer.core.tracer;

import java.util.ArrayDeque;
import java.util.Deque;

/** Chặn vòng lặp và giới hạn độ sâu khi dò vết. */
public final class CycleGuard {

    private final int maxDepth;
    private final Deque<String> path = new ArrayDeque<>();

    public CycleGuard(int maxDepth) {
        this.maxDepth = maxDepth;
    }

    /** @return false nếu đi vào sẽ tạo vòng lặp hoặc vượt độ sâu */
    public boolean enter(String nodeKey) {
        if (path.size() >= maxDepth || path.contains(nodeKey)) {
            return false;
        }
        path.push(nodeKey);
        return true;
    }

    public void exit() {
        path.pop();
    }
}
