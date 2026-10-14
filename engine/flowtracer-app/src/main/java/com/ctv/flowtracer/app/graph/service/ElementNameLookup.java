package com.ctv.flowtracer.app.graph.service;

import com.ctv.flowtracer.app.ai.model.AiPurpose;
import com.ctv.flowtracer.app.ai.service.AiGateway;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Tên chuẩn do AI đặt cho phần tử giao diện (mục đích {@code ELEMENT_NAMING}), gom từ mọi bản đã lưu của Workspace: key phần tử →
 * tên. Chỉ đọc bản đã lưu, không gọi AI; mục đích bị tắt thì rỗng (giao diện quay về tên lấy từ mã nguồn).
 */
@Component
public class ElementNameLookup {

    private final AiGateway gateway;

    public ElementNameLookup(AiGateway gateway) {
        this.gateway = gateway;
    }

    @SuppressWarnings("unchecked")
    public Map<String, String> forWorkspace(UUID workspaceId) {
        Map<String, String> names = new HashMap<>();
        for (Map<String, Object> content : gateway.latestByTarget(workspaceId, AiPurpose.ELEMENT_NAMING).values()) {
            if (content.get("names") instanceof List<?> list) {
                for (Object o : list) {
                    if (o instanceof Map<?, ?> m && m.get("nodeKey") instanceof String key && m.get("label") instanceof String label) {
                        names.put(key, label);
                    }
                }
            }
        }
        return names;
    }
}
