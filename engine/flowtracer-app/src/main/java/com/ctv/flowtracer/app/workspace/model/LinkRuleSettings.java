package com.ctv.flowtracer.app.workspace.model;

import com.ctv.flowtracer.ir.LinkRule;
import java.util.ArrayList;
import java.util.List;

/**
 * Luật liên kết của Workspace — cho package {@code linking}.
 *
 * @param manual luật người dùng nhập
 * @param detected luật tự phát hiện ở lần quét gần nhất (proxy, context-path…)
 */
public record LinkRuleSettings(List<LinkRule> manual, List<LinkRule> detected) {

    public LinkRuleSettings {
        manual = List.copyOf(manual);
        detected = List.copyOf(detected);
    }

    /** Luật hiệu lực: thủ công trước, tự phát hiện sau. */
    public List<LinkRule> effective() {
        var all = new ArrayList<LinkRule>(manual);
        all.addAll(detected);
        return List.copyOf(all);
    }
}
