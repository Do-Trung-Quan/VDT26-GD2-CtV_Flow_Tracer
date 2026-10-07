package com.ctv.flowtracer.core.detection;

import com.ctv.flowtracer.ir.spi.DetectContext;
import com.ctv.flowtracer.ir.spi.DetectResult;
import com.ctv.flowtracer.ir.spi.StackAdapter;
import java.util.Comparator;
import java.util.List;

/** Chạy detect() của các adapter (do ứng dụng truyền vào) trên một Source và xếp hạng kết quả. */
public final class StackDetector {

    /** Ngưỡng tối thiểu để coi là khớp công nghệ. */
    public static final double MIN_SCORE = 0.5;

    /** Một kết quả nhận diện. */
    public record Detection(StackAdapter adapter, DetectResult result) {}

    /** @return các adapter khớp, điểm cao nhất trước (cùng điểm thì theo id để ổn định) */
    public List<Detection> detect(List<StackAdapter> adapters, DetectContext ctx) {
        return adapters.stream()
                .map(a -> new Detection(a, a.detect(ctx)))
                .filter(d -> d.result().score() >= MIN_SCORE)
                .sorted(Comparator.comparingDouble((Detection d) -> d.result().score())
                        .reversed()
                        .thenComparing(d -> d.adapter().id()))
                .toList();
    }
}
