package com.ctv.flowtracer.core.tracer;

import com.ctv.flowtracer.ir.IrNode;
import com.ctv.flowtracer.ir.trace.Participant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dựng các cột của diagram từ các node trên đường đi (§2.2.4 của overview): Client một cột; mỗi Controller / Service /
 * Repository một cột (lớp {@code ServiceImpl} và interface của nó là CÙNG một cột vì cạnh đã trỏ tới lớp hiện thực); mỗi
 * bảng dữ liệu một cột "Bảng <tên>" (truy vấn không rõ bảng thì dùng cột "Database" chung). Nhãn = vai trò + tên nghiệp vụ ({@code @Tag} → tên class, do bộ phân tích ghi sẵn ở
 * {@code attrs.businessName}). Cột xếp theo vai trò rồi theo thứ tự xuất hiện đầu tiên; id đánh theo thứ tự xuất hiện.
 */
final class ParticipantBuilder {

    static final String CLIENT_ID = "user";
    static final String DATABASE_ID = "db";

    private record Entry(Participant participant, String className, int order) {}

    private final Map<String, Entry> byClass = new LinkedHashMap<>();
    private final Map<Participant.Role, Integer> counters = new EnumMap<>(Participant.Role.class);
    private final List<Entry> special = new ArrayList<>();
    private final Map<String, String> tableIds = new LinkedHashMap<>();
    private int order;

    /** Cột người gọi API (luồng bắt đầu từ Backend). */
    String client() {
        return special("Client", Participant.Role.USER, CLIENT_ID, null);
    }

    /** Cột "Database" chung, chỉ dùng khi truy vấn không xác định được bảng nào. */
    String database() {
        return special("Database", Participant.Role.DATABASE, DATABASE_ID, null);
    }

    /** Cột của một bảng dữ liệu: mỗi bảng một cột, nhãn "Bảng <tên>". */
    String table(String name, String nodeKey) {
        String id = tableIds.computeIfAbsent(name, n -> "t" + (tableIds.size() + 1));
        return special("Bảng " + name, Participant.Role.DATABASE, id, nodeKey);
    }

    private String special(String label, Participant.Role role, String id, String nodeKey) {
        if (special.stream().noneMatch(e -> e.participant().id().equals(id))) {
            special.add(new Entry(new Participant(id, label, role, nodeKey), null, order++));
        }
        return id;
    }

    /**
     * Cột của lớp chứa method.
     *
     * @return id cột; null nếu lớp không thuộc tầng Controller / Service / Repository (hàm của entity, tiện ích… — được gộp
     *     vào bước tự xử lý của cột đang chạy)
     */
    String forMethod(IrNode method) {
        Participant.Role role = roleOf(method.attrs().get("layer"));
        return role == null ? null : register(method, role);
    }

    /** Cột của Controller xử lý API, kể cả khi bộ phân tích không xếp lớp vào tầng Controller. */
    String controller(IrNode handler) {
        return register(handler, Participant.Role.CONTROLLER);
    }

    private String register(IrNode method, Participant.Role role) {
        String className = String.valueOf(method.attrs().getOrDefault("className", method.name()));
        Entry existing = byClass.get(className);
        if (existing != null) {
            return existing.participant().id();
        }
        int n = counters.merge(role, 1, Integer::sum);
        String id = prefix(role) + n;
        String business = String.valueOf(method.attrs().getOrDefault("businessName", simpleName(className)));
        Participant participant = new Participant(id, prefix(role).equals("c") ? "Controller " + business
                : prefix(role).equals("s") ? "Service " + business : "Repository " + business, role, method.key());
        byClass.put(className, new Entry(participant, className, order++));
        return id;
    }

    /** Danh sách cột: xếp theo vai trò rồi thứ tự xuất hiện; hai lớp khác nhau trùng nhãn thì thêm tên lớp vào nhãn. */
    List<Participant> build() {
        var all = new ArrayList<Entry>(special);
        all.addAll(byClass.values());
        Map<String, Long> labelCounts = new LinkedHashMap<>();
        all.forEach(e -> labelCounts.merge(e.participant().label(), 1L, Long::sum));
        return all.stream()
                .sorted(Comparator.comparingInt((Entry e) -> e.participant().role().ordinal()).thenComparingInt(Entry::order))
                .map(e -> labelCounts.get(e.participant().label()) > 1
                        ? new Participant(
                                e.participant().id(),
                                e.participant().label() + " (" + simpleName(e.className()) + ")",
                                e.participant().role(),
                                e.participant().nodeKey())
                        : e.participant())
                .toList();
    }

    private static Participant.Role roleOf(Object layer) {
        return switch (String.valueOf(layer)) {
            case "CONTROLLER" -> Participant.Role.CONTROLLER;
            case "SERVICE" -> Participant.Role.SERVICE;
            case "REPOSITORY" -> Participant.Role.REPOSITORY;
            default -> null;
        };
    }

    private static String prefix(Participant.Role role) {
        return switch (role) {
            case CONTROLLER -> "c";
            case SERVICE -> "s";
            default -> "r";
        };
    }

    private static String simpleName(String fqn) {
        return fqn.substring(fqn.lastIndexOf('.') + 1);
    }
}
