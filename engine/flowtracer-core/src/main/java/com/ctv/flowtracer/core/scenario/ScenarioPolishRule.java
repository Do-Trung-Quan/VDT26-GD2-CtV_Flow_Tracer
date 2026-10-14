package com.ctv.flowtracer.core.scenario;

import com.ctv.flowtracer.ir.scenario.Scenario;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Kiểm tra lời kể do AI viết lại cho tab Kịch bản (CLAUDE.md §3.7): phải GIỮ NGUYÊN số bước, thứ tự và thành phần của khung
 * câu tất định. Lệch bất kỳ điểm nào thì cả bản AI bị bỏ và dùng khung. Quy tắc thuần, test được.
 *
 * <ul>
 *   <li>cùng tập số bước, cùng thứ tự, không thiếu / thừa / trùng;
 *   <li>mỗi câu không rỗng, không quá {@value #MAX_LENGTH} ký tự;
 *   <li>mỗi tên actor xuất hiện trong câu khung của bước đó cũng phải xuất hiện trong câu AI (giữ nguyên thành phần).
 * </ul>
 */
public final class ScenarioPolishRule {

    public static final int MAX_LENGTH = 400;

    /** Lý do không đạt kiểm tra. */
    public static final class Rejected extends RuntimeException {
        public Rejected(String message) {
            super(message);
        }
    }

    /**
     * @param framework kịch bản khung tất định
     * @param polished số bước → câu AI viết (theo thứ tự AI trả về)
     * @return số bước → câu đã làm sạch, theo thứ tự của khung
     * @throws Rejected nếu không đạt
     */
    public Map<Integer, String> apply(Scenario framework, List<Map.Entry<Integer, String>> polished) {
        List<Scenario.Item> steps = framework.items().stream().filter(i -> i.index() != null).toList();
        if (polished.size() != steps.size()) {
            throw new Rejected("Số bước lệch: khung có " + steps.size() + ", AI trả " + polished.size());
        }
        List<String> actors = new ArrayList<>(framework.actors().stream().map(Scenario.Actor::name).toList());
        var result = new LinkedHashMap<Integer, String>();
        for (int i = 0; i < steps.size(); i++) {
            Scenario.Item step = steps.get(i);
            var given = polished.get(i);
            if (!step.index().equals(given.getKey())) {
                throw new Rejected("Thứ tự bước lệch ở vị trí " + (i + 1));
            }
            String text = given.getValue() == null ? "" : given.getValue().strip();
            if (text.isEmpty() || text.length() > MAX_LENGTH) {
                throw new Rejected("Câu của bước " + step.index() + " rỗng hoặc quá dài");
            }
            for (String actor : actors) {
                if (step.text().contains(actor) && !text.contains(actor)) {
                    throw new Rejected("Câu của bước " + step.index() + " bỏ mất thành phần \"" + actor + "\"");
                }
            }
            result.put(step.index(), text);
        }
        return result;
    }
}
