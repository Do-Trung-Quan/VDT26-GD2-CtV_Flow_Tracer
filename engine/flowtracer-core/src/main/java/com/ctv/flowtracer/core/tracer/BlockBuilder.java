package com.ctv.flowtracer.core.tracer;

import com.ctv.flowtracer.ir.trace.TraceBlock;
import com.ctv.flowtracer.ir.trace.TraceStep;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;

/**
 * Đánh số các bước (từ 1) và đổi các dấu mở / chuyển nhánh / đóng khối thành {@link TraceBlock} theo số bước. Khối rỗng
 * (không có bước nào) và nhánh rỗng bị bỏ; khối lồng nhau được giữ nguyên, khối ngoài đứng trước khối trong.
 */
final class BlockBuilder {

    /** Kết quả: bước đã đánh số và các khối. */
    record Built(List<TraceStep> steps, List<TraceBlock> blocks) {}

    /** Khối đang mở: các nhánh theo thứ tự, mỗi nhánh có nhãn và bước đầu (0 nếu chưa có bước nào). */
    private static final class Open {
        final TraceBlock.Type type;
        final List<String> labels = new ArrayList<>();
        final List<Integer> starts = new ArrayList<>();
        int last;

        Open(TraceBlock.Type type, String label) {
            this.type = type;
            labels.add(label);
            starts.add(0);
        }

        void addStep(int index) {
            if (starts.getLast() == 0) {
                starts.set(starts.size() - 1, index);
            }
            last = index;
        }
    }

    Built build(List<TraceItem> items) {
        var steps = new ArrayList<TraceStep>();
        var blocks = new ArrayList<TraceBlock>();
        Deque<Open> open = new ArrayDeque<>();
        var indexById = new java.util.HashMap<Integer, Integer>();
        for (TraceItem item : items) {
            switch (item) {
                case TraceItem.StepItem s -> {
                    TraceStep numbered = s.step().withIndex(steps.size() + 1);
                    if (s.repeatOfId() > 0 && indexById.containsKey(s.repeatOfId())) {
                        numbered = numbered.withRepeatOf(indexById.get(s.repeatOfId()));
                    }
                    indexById.put(s.id(), numbered.index());
                    final TraceStep step = numbered;
                    steps.add(step);
                    open.forEach(o -> o.addStep(step.index()));
                }
                case TraceItem.Open o -> open.push(new Open(o.type(), o.label()));
                case TraceItem.Else e -> {
                    Open top = open.peek();
                    if (top != null) {
                        top.labels.add(e.label());
                        top.starts.add(0);
                    }
                }
                case TraceItem.Close ignored -> close(open.poll(), blocks);
            }
        }
        while (!open.isEmpty()) {
            close(open.poll(), blocks);
        }
        blocks.sort(Comparator.comparingInt(TraceBlock::fromStep).thenComparing(Comparator.comparingInt(TraceBlock::toStep).reversed()));
        return new Built(steps, blocks);
    }

    private static void close(Open block, List<TraceBlock> out) {
        if (block == null) {
            return;
        }
        var labels = new ArrayList<String>();
        var starts = new ArrayList<Integer>();
        for (int i = 0; i < block.labels.size(); i++) {
            if (block.starts.get(i) > 0) {
                labels.add(block.labels.get(i));
                starts.add(block.starts.get(i));
            }
        }
        if (labels.isEmpty()) {
            return;
        }
        var elseBranches = new ArrayList<TraceBlock.Branch>();
        for (int i = 1; i < labels.size(); i++) {
            elseBranches.add(new TraceBlock.Branch(labels.get(i), starts.get(i)));
        }
        out.add(new TraceBlock(block.type, labels.getFirst(), starts.getFirst(), block.last, elseBranches));
    }
}
