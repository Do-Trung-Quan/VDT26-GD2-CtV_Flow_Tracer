package com.ctv.flowtracer.ir;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Một thành phần trên bản đồ IR.
 *
 * @param key định danh ổn định qua các lần quét (xem {@link NodeKeys})
 * @param kind loại thành phần
 * @param name tên kỹ thuật (vd {@code CategoryServiceImpl.create})
 * @param displayName tên thân thiện tiếng Việt (có thể null)
 * @param location vị trí trong mã nguồn (null với thành phần suy ra, vd bảng dữ liệu)
 * @param attrs thuộc tính riêng theo {@code kind}; luôn được sắp xếp theo khóa để kết quả tất định
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record IrNode(
        String key, NodeKind kind, String name, String displayName, SourceLocation location, Map<String, Object> attrs) {

    public IrNode {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(name, "name");
        attrs = attrs == null ? Map.of() : Collections.unmodifiableMap(new TreeMap<>(attrs));
    }
}
