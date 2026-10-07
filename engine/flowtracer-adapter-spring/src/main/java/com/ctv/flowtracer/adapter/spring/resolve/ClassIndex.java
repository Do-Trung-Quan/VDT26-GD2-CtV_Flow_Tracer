package com.ctv.flowtracer.adapter.spring.resolve;

import com.ctv.flowtracer.adapter.spring.facts.JavaFileFacts;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** Bảng tra FQN → khai báo; tên ngắn + import → FQN (kiểm lại FQN ghi ở pha 1). */
public final class ClassIndex {

    private final Map<String, JavaFileFacts.TypeFact> byFqn = new TreeMap<>();

    public ClassIndex(List<JavaFileFacts> facts) {
        facts.forEach(f -> f.types().forEach(t -> byFqn.put(t.fqn(), t)));
    }

    public Optional<JavaFileFacts.TypeFact> find(String fqn) {
        return Optional.ofNullable(byFqn.get(fqn));
    }

    /** Giải tên ngắn trong ngữ cảnh một file (theo import và package). */
    public Optional<String> resolveSimpleName(String simpleName, JavaFileFacts context) {
        throw new UnsupportedOperationException("TODO: chưa triển khai");
    }
}
