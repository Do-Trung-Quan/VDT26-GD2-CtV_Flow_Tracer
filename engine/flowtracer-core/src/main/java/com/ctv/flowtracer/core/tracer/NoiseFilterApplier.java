package com.ctv.flowtracer.core.tracer;

import com.ctv.flowtracer.ir.IrNode;
import com.ctv.flowtracer.ir.NoiseFilter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Nhận ra thành phần là "nhiễu" (logger, mapper, getter…) để bỏ khỏi trace. Mẫu là glob trên tên đầy đủ
 * {@code className#signature} của method (vd {@code *#get*()}, {@code *Mapper#*}) hoặc tên node nếu không phải method;
 * {@code *} khớp mọi ký tự.
 */
public final class NoiseFilterApplier {

    private static final Map<String, Pattern> COMPILED = new ConcurrentHashMap<>();

    public boolean isNoise(IrNode node, List<NoiseFilter> filters) {
        if (filters.isEmpty()) {
            return false;
        }
        String name = fullName(node);
        return filters.stream().anyMatch(f -> COMPILED.computeIfAbsent(f.pattern(), NoiseFilterApplier::toRegex).matcher(name).matches());
    }

    /** {@code className#signature} với method Backend, ngược lại là tên node. */
    static String fullName(IrNode node) {
        Object className = node.attrs().get("className");
        Object signature = node.attrs().get("signature");
        return className instanceof String c && signature instanceof String s ? c + "#" + s : node.name();
    }

    private static Pattern toRegex(String glob) {
        var regex = new StringBuilder();
        String[] parts = glob.split("\\*", -1);
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                regex.append(".*");
            }
            regex.append(Pattern.quote(parts[i]));
        }
        return Pattern.compile(regex.toString());
    }
}
