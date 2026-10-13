package com.ctv.flowtracer.core.tracer;

import com.ctv.flowtracer.core.graph.GraphIndex;
import com.ctv.flowtracer.core.graph.Subgraph;
import com.ctv.flowtracer.ir.Confidence;
import com.ctv.flowtracer.ir.EdgeKind;
import com.ctv.flowtracer.ir.Guard;
import com.ctv.flowtracer.ir.IrEdge;
import com.ctv.flowtracer.ir.IrNode;
import com.ctv.flowtracer.ir.NodeKind;
import com.ctv.flowtracer.ir.trace.Trace;
import com.ctv.flowtracer.ir.trace.TraceBlock;
import com.ctv.flowtracer.ir.trace.TraceStep;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Một lần dò vết từ một API hoặc từ một phần tử giao diện có gọi API. Từ API: đi theo {@code HANDLED_BY} → các lời gọi ({@code CALLS}) và truy vấn ({@code EXECUTES}) của
 * từng method theo thứ tự thực thi, phát ra dòng bước + dấu khối rồi giao {@link BlockBuilder} đánh số.
 *
 * <p>Quy tắc dựng bước (§2.2.4 của overview):
 *
 * <ul>
 *   <li>lời gọi sang lớp thuộc Controller / Service / Repository khác cột → bước đi, rồi chiều trả về sau khi method đích
 *       chạy xong;
 *   <li>lời gọi vào cùng cột hoặc vào lớp không thuộc ba tầng (entity, tiện ích…) → gom vào bước tự xử lý của cột đang
 *       chạy, và vẫn đi sâu vào bên trong để thấy các lời gọi thật sự sang cột khác;
 *   <li>lệnh {@code throw} → chiều trả về lỗi từ cột đang chạy lên tới Client; có điều kiện bao quanh thì thành khối
 *       {@code alt} (nhánh còn lại là phần chạy tiếp);
 *   <li>lời gọi chưa xác định đích → bước nét đỏ + trả về "(chưa xác định)", KHÔNG đi sâu và KHÔNG chọn hộ.
 * </ul>
 *
 * <p>Từ phần tử giao diện (luồng End-to-End): bước người dùng thao tác (User → Frontend), rồi đi theo hàm xử lý
 * ({@code TRIGGERS}) và các hàm phía Frontend — tất cả gộp trong cột Frontend, không vẽ bước tự xử lý. Mỗi lời gọi HTTP
 * ({@code SENDS}) đi theo liên kết {@code HTTP_REQUEST} do Linker tạo: tới API thì vẽ bước {@code METHOD path} sang Controller
 * và dò tiếp như luồng bắt đầu từ API; tới hệ thống ngoài thì một cột cho hệ thống đó; liên kết chưa xác định thì bước
 * "chưa xác định", không chọn hộ. Lời gọi chạy khi hộp thoại đóng ({@code guard.branch = afterClosed}) không thuộc luồng của
 * lần bấm. Bước cuối là giao diện phản hồi người dùng (hộp thoại / màn hình được mở, hoặc "giao diện đã cập nhật").
 */
final class TraceRun {

    static final String OTHERWISE = "ngược lại";
    private static final java.util.regex.Pattern VALIDATION_EXCEPTION =
            java.util.regex.Pattern.compile("(?i)validation|invalid|illegalargument|constraintviolation|badrequest");
    static final String UNRESOLVED_RETURN = "(chưa xác định)";
    /** Nhãn cột đích của lời gọi HTTP chưa nối được với API nào. */
    static final String UNKNOWN_API = "API chưa xác định";

    private sealed interface Event permits CallEvent, ThrowEvent {
        int order();

        Guard guard();
    }

    private record CallEvent(int order, Guard guard, IrEdge edge) implements Event {}

    private record ThrowEvent(int order, Guard guard, String exception, String messageKey) implements Event {}

    /** Method đang chạy: cột của nó và cột đã gọi nó (bằng nhau nếu là hàm được gộp vào bước tự xử lý). */
    private record Frame(String participantId, String callerId) {}

    private final Subgraph subgraph;
    private final GraphIndex graph;
    private final TraceRequest request;
    private final NoiseFilterApplier noise = new NoiseFilterApplier();
    private final ParticipantBuilder participants = new ParticipantBuilder();
    private final SelfStepBuilder self = new SelfStepBuilder();
    private final List<TraceItem> items = new ArrayList<>();
    private final Set<String> warnings = new LinkedHashSet<>();
    private final CycleGuard cycleGuard;
    private final Deque<Frame> stack = new ArrayDeque<>();
    /** Sự kiện lời gọi/truy vấn đã phát bước sang cột khác (so sánh theo danh tính, không theo giá trị). */
    private final Set<Event> crossed = Collections.newSetFromMap(new IdentityHashMap<>());
    private int nextStepId = 1;
    /** Hàm đích → mã bước của lần gọi đầu tiên sang cột khác (để đánh dấu lần gọi lặp lại). */
    private final Map<String, Integer> firstCall = new java.util.HashMap<>();
    /** Hàm được gộp vào bước tự xử lý đã được đi sâu một lần. */
    private final Set<String> foldedExpanded = new java.util.HashSet<>();
    private int openDepth;
    private int depthCuts;
    private boolean aborted;

    /**
     * Mức chi tiết của một lần dò.
     *
     * @param errors vẽ nhánh lỗi nghiệp vụ; false = chỉ vẽ đường chạy thành công
     * @param selfSteps vẽ các bước tự xử lý
     * @param collapseRepeats lần gọi lặp lại tới cùng một hàm chỉ vẽ bước đi và bước trả về, không vẽ lại phần bên trong
     * @param depth độ sâu tối đa
     */
    record Options(boolean errors, boolean selfSteps, boolean collapseRepeats, int depth) {}

    private final Options options;
    /** Hiệu ứng trên giao diện (mở hộp thoại, chuyển màn hình) gặp khi dò từ phần tử, theo thứ tự xảy ra. */
    private final List<IrEdge> uiEffects = new ArrayList<>();

    TraceRun(Subgraph subgraph, TraceRequest request, Options options) {
        this.options = options;
        this.subgraph = subgraph;
        this.graph = new GraphIndex(subgraph);
        this.request = request;
        this.cycleGuard = new CycleGuard(options.depth());
    }

    Trace run() {
        IrNode start = graph.node(request.startKey())
                .orElseThrow(() -> new IllegalArgumentException("Không có node xuất phát: " + request.startKey()));
        if (start.kind() == NodeKind.UI_ELEMENT) {
            return runFromElement(start);
        }
        if (start.kind() != NodeKind.API_ENDPOINT) {
            throw new IllegalArgumentException("Dò vết chỉ hỗ trợ node xuất phát là API_ENDPOINT hoặc UI_ELEMENT, nhận " + start.kind());
        }
        String client = participants.client();
        Optional<IrEdge> handledBy = graph.outgoing(start.key()).stream()
                .filter(e -> e.kind() == EdgeKind.HANDLED_BY && e.toKey() != null)
                .findFirst();
        Optional<IrNode> handler = handledBy.flatMap(e -> graph.node(e.toKey()));
        if (handler.isEmpty()) {
            warnings.add("API này chưa có hàm xử lý trong mã nguồn nên không dựng được luồng");
            return finish(start);
        }
        String controller = participants.controller(handler.get());
        step(client, controller, EdgeKind.HANDLED_BY, start.name(), handledBy.get(), false, false, 0);
        emitMethod(handler.get(), controller, client, true);
        if (!aborted) {
            flushSelf();
            step(controller, client, null, responseLabel(start), Confidence.CERTAIN, null, null, true, false, false, 0);
        }
        closeTo(0);
        return finish(start);
    }

    /** Luồng End-to-End từ một phần tử giao diện có gọi API. */
    private Trace runFromElement(IrNode start) {
        String user = participants.user();
        String fe = participants.frontend();
        List<IrEdge> triggers = graph.outgoing(start.key()).stream()
                .filter(e -> e.kind() == EdgeKind.TRIGGERS && e.toKey() != null)
                .toList();
        String firstRef = triggers.isEmpty() ? null : subgraph.edgeRef(triggers.get(0));
        step(user, fe, EdgeKind.TRIGGERS, UiOnlyTracer.userAction(start), Confidence.CERTAIN, null, firstRef, false, false, false, 0);
        graph.outgoing(start.key()).stream()
                .filter(e -> e.kind() == EdgeKind.NAVIGATES && e.toKey() != null)
                .forEach(uiEffects::add);
        stack.push(new Frame(fe, user));
        Set<String> seenTargets = new HashSet<>();
        try {
            for (IrEdge trigger : triggers) {
                if (trigger.toKey() != null && !seenTargets.add(trigger.toKey())) {
                    continue;
                }
                Optional<IrNode> handler = graph.node(trigger.toKey());
                if (handler.isPresent() && !aborted) {
                    emitMethod(handler.get(), fe, fe, false);
                }
            }
        } finally {
            stack.pop();
        }
        if (!aborted) {
            flushSelf();
            step(fe, user, null, reply(), Confidence.CERTAIN, null, null, true, false, false, 0);
        }
        closeTo(0);
        return finish(start);
    }

    /** Giao diện phản hồi người dùng: các hộp thoại / màn hình được mở, hoặc "giao diện đã cập nhật". */
    private String reply() {
        var labels = new LinkedHashSet<String>();
        for (IrEdge effect : uiEffects) {
            graph.node(effect.toKey()).ifPresent(target -> labels.add(UiOnlyTracer.effectLabel(effect.kind(), target)));
        }
        return labels.isEmpty() ? UiOnlyTracer.NO_EFFECT : String.join(", ", labels);
    }

    private Trace finish(IrNode start) {
        BlockBuilder.Built built = new BlockBuilder().build(items);
        if (depthCuts > 0) {
            warnings.add(depthCuts + " lời gọi bị bỏ qua phần bên trong vì vượt độ sâu dò vết (tối đa " + options.depth() + " cấp)");
        }
        addStepWarnings(built.steps());
        return new Trace(
                TraceIds.of(request), start.key(), participants.build(), built.steps(), built.blocks(), List.copyOf(warnings));
    }

    /** Cảnh báo kèm số bước, vd "1 lời gọi chưa xác định được đích (xem bước 6)". */
    private void addStepWarnings(List<TraceStep> steps) {
        List<Integer> unresolved = steps.stream()
                .filter(s -> s.confidence() == Confidence.UNRESOLVED && !s.returning() && !s.isSelf())
                .map(TraceStep::index)
                .toList();
        if (!unresolved.isEmpty()) {
            warnings.add(unresolved.size() + " lời gọi chưa xác định được đích (xem bước " + join(unresolved) + ")");
        }
        List<Integer> noTable = steps.stream()
                .filter(s -> s.kind() == EdgeKind.EXECUTES && !s.returning())
                .map(TraceStep::index)
                .toList();
        if (!noTable.isEmpty()) {
            warnings.add(noTable.size() + " truy vấn chưa xác định được bảng (xem bước " + join(noTable) + ")");
        }
    }

    private static String join(List<Integer> indexes) {
        return String.join(", ", indexes.stream().map(String::valueOf).toList());
    }

    // ---------------- method và nhóm sự kiện ----------------

    /** @param level true nếu là cấp nghiệp vụ (cột khác với cột gọi nó); hàm gộp vào bước tự xử lý thì false */
    private void emitMethod(IrNode method, String participantId, String callerId, boolean level) {
        if (!cycleGuard.enter(method.key(), level)) {
            if (cycleGuard.isOnPath(method.key())) {
                warnings.add("Bỏ qua phần bên trong " + method.name() + " vì nó gọi lại chính mình (đệ quy), chỉ hiện lần đầu");
            } else {
                depthCuts++;
            }
            return;
        }
        stack.push(new Frame(participantId, callerId));
        try {
            emitEvents(eventsOf(method));
        } finally {
            stack.pop();
            cycleGuard.exit();
        }
    }

    /** Lời gọi, truy vấn và lệnh throw của method, xếp theo thứ tự thực thi. */
    private List<Event> eventsOf(IrNode method) {
        var events = new ArrayList<Event>();
        for (IrEdge e : graph.outgoing(method.key())) {
            if (e.guard() != null && UiOnlyTracer.AFTER_CLOSED.equals(e.guard().branch())) {
                continue; // chạy khi hộp thoại đóng, không thuộc luồng của lần bấm
            }
            if ((e.kind() == EdgeKind.OPENS || e.kind() == EdgeKind.NAVIGATES) && e.toKey() != null) {
                uiEffects.add(e);
            }
            if (e.kind() == EdgeKind.CALLS || e.kind() == EdgeKind.EXECUTES || e.kind() == EdgeKind.SENDS) {
                events.add(new CallEvent(e.callOrder() == null ? Integer.MAX_VALUE : e.callOrder(), e.guard(), e));
            }
        }
        if (method.attrs().get("throws") instanceof List<?> thrown) {
            for (Object o : thrown) {
                if (o instanceof Map<?, ?> m) {
                    events.add(new ThrowEvent(
                            number(m.get("order")), guardOf(m), String.valueOf(m.get("exception")), (String) m.get("messageKey")));
                }
            }
        }
        events.sort(Comparator.comparingInt(Event::order));
        return events;
    }

    private static int number(Object value) {
        return value instanceof Number n ? n.intValue() : Integer.MAX_VALUE;
    }

    private static Guard guardOf(Map<?, ?> m) {
        if (!(m.get("guardType") instanceof String type)) {
            return null;
        }
        return new Guard(Guard.Type.valueOf(type), (String) m.get("guardText"), (String) m.get("guardBranch"));
    }

    private void emitEvents(List<Event> events) {
        int i = 0;
        while (i < events.size() && !aborted) {
            Guard guard = events.get(i).guard();
            if (guard == null) {
                handle(events.get(i));
                i++;
            } else {
                i = emitGroup(events, i);
            }
        }
    }

    private void emitList(List<Event> events) {
        for (Event event : events) {
            if (aborted) {
                return;
            }
            handle(event);
        }
    }

    /** Dãy sự kiện liên tiếp có cùng điều kiện bao quanh bắt đầu tại {@code from}; trả về chỉ số ngay sau dãy. */
    private static int groupEnd(List<Event> events, int from) {
        Guard guard = events.get(from).guard();
        int end = from + 1;
        while (end < events.size() && Objects.equals(events.get(end).guard(), guard)) {
            end++;
        }
        return end;
    }

    private int emitGroup(List<Event> events, int from) {
        Guard guard = events.get(from).guard();
        return switch (guard.type()) {
            case IF, TERNARY -> emitIf(events, from, guard);
            case SWITCH -> emitSwitch(events, from, guard);
            case ELSE -> emitSimple(events, from, TraceBlock.Type.OPT, OTHERWISE + ": " + guard.text());
            case CATCH -> emitSimple(events, from, TraceBlock.Type.OPT, "catch " + guard.text());
            case LOOP -> emitSimple(events, from, TraceBlock.Type.LOOP, guard.text());
            case TRY -> emitPlain(events, from);
        };
    }

    private int emitPlain(List<Event> events, int from) {
        int end = groupEnd(events, from);
        List<Event> group = visible(events, from, end, events.get(from).guard());
        if (!group.isEmpty()) {
            emitList(group);
        }
        return end;
    }

    private int emitSimple(List<Event> events, int from, TraceBlock.Type type, String label) {
        int end = groupEnd(events, from);
        List<Event> group = visible(events, from, end, events.get(from).guard());
        if (group.isEmpty()) {
            return end;
        }
        int mark = openDepth;
        open(type, label);
        boolean before = aborted;
        emitList(group);
        aborted = before; // lỗi không điều kiện chỉ kết thúc nhánh này
        closeTo(mark);
        return end;
    }

    /**
     * Các sự kiện của nhóm [from, end) sẽ được vẽ. Lệnh {@code throw} chỉ được vẽ (thành nhánh lỗi) khi điều kiện bao quanh
     * PHỤ THUỘC vào kết quả một lời gọi — như {@code existsByCode(code)} hay {@code count > 0}: đó là ràng buộc nghiệp vụ
     * của luồng. Kiểm tra đầu vào thuần túy ({@code if (x == null) throw …}) không vẽ, vì một hàm có thể có hàng chục kiểm
     * tra như vậy; chúng được liệt kê ở mục "Ràng buộc" của tab Diễn giải.
     */
    private List<Event> visible(List<Event> events, int from, int end, Guard guard) {
        List<Event> group = events.subList(from, end);
        boolean hasThrow = group.stream().anyMatch(e -> e instanceof ThrowEvent);
        if (!hasThrow) {
            return group;
        }
        boolean business = options.errors() && dependsOnCall(events, from, guard);
        return group.stream().filter(e -> !(e instanceof ThrowEvent t) || business && isBusinessException(t)).toList();
    }

    /** Ngoại lệ kiểm tra dữ liệu đầu vào (tên chứa validation / invalid / illegalargument…) không phải ngoại lệ nghiệp vụ. */
    private static boolean isBusinessException(ThrowEvent thrown) {
        return !VALIDATION_EXCEPTION.matcher(thrown.exception()).find();
    }

    /**
     * Điều kiện của nhóm bắt đầu tại {@code from} có phụ thuộc dữ liệu lấy từ Service / Repository không: sự kiện ngay trước
     * nó là lời gọi đã phát bước sang cột khác, hoặc điều kiện chứa tên một lời gọi như vậy (hay một hàm của entity, vd
     * {@code order.isPending()}) nằm ngay trước đó. Gọi hàm tiện ích / validator không tính.
     */
    private boolean dependsOnCall(List<Event> events, int from, Guard guard) {
        if (from > 0 && crossed.contains(events.get(from - 1))) {
            return true;
        }
        String text = guard.text() == null ? "" : guard.text();
        for (int i = from - 1; i >= 0 && events.get(i) instanceof CallEvent call; i--) {
            Optional<IrNode> target = targetOf(call.edge());
            if (target.isPresent()
                    && !noise.isNoise(target.get(), request.noiseFilters())
                    && (crossed.contains(call) || Boolean.TRUE.equals(target.get().attrs().get("domainModel")))
                    && text.contains(simpleName(target.get()) + "(")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Nhánh then, kèm nhánh else nếu có. Nhánh then kết thúc bằng {@code throw} (kiểu "kiểm tra rồi ném lỗi") thì phần
     * còn lại của luồng chính là nhánh else: khối {@code alt} được giữ mở tới hết khối bao ngoài hoặc hết luồng.
     */
    private int emitIf(List<Event> events, int from, Guard guard) {
        int thenEnd = groupEnd(events, from);
        List<Event> thenGroup = visible(events, from, thenEnd, guard);
        int next = thenEnd;
        List<Event> elseGroup = List.of();
        if (next < events.size() && isElseOf(events.get(next).guard(), guard)) {
            int elseEnd = groupEnd(events, next);
            elseGroup = visible(events, next, elseEnd, events.get(next).guard());
            next = elseEnd;
        }
        if (thenGroup.isEmpty()) {
            if (!elseGroup.isEmpty()) {
                int mark = openDepth;
                open(TraceBlock.Type.OPT, OTHERWISE + ": " + guard.text());
                emitList(elseGroup);
                closeTo(mark);
            }
            return next;
        }
        boolean terminal = guard.type() == Guard.Type.IF && thenGroup.getLast() instanceof ThrowEvent;
        boolean alt = terminal || !elseGroup.isEmpty();
        int mark = openDepth;
        open(alt ? TraceBlock.Type.ALT : TraceBlock.Type.OPT, guard.text());
        // Lệnh throw không điều kiện chỉ kết thúc nhánh chứa nó: nhánh else (hoặc phần còn lại của luồng) vẫn chạy được
        boolean before = aborted;
        emitList(thenGroup);
        boolean thenAborted = aborted;
        aborted = before;
        if (alt) {
            elseBranch(OTHERWISE);
        }
        emitList(elseGroup);
        aborted = before || thenAborted && !elseGroup.isEmpty() && aborted;
        if (!terminal) {
            closeTo(mark);
        }
        return next;
    }

    private static boolean isElseOf(Guard candidate, Guard thenGuard) {
        if (candidate == null || !Objects.equals(candidate.text(), thenGuard.text())) {
            return false;
        }
        return thenGuard.type() == Guard.Type.IF
                ? candidate.type() == Guard.Type.ELSE
                : candidate.type() == Guard.Type.TERNARY && "else".equals(candidate.branch());
    }

    /** Các {@code case} liên tiếp của cùng một switch thành một khối {@code alt} nhiều nhánh. */
    private int emitSwitch(List<Event> events, int from, Guard guard) {
        int mark = openDepth;
        int next = from;
        boolean first = true;
        while (next < events.size()
                && events.get(next).guard() != null
                && events.get(next).guard().type() == Guard.Type.SWITCH
                && Objects.equals(events.get(next).guard().text(), guard.text())) {
            int end = groupEnd(events, next);
            List<Event> branch = visible(events, next, end, events.get(next).guard());
            String label = guard.text() + ": " + events.get(next).guard().branch();
            if (!branch.isEmpty()) {
                if (first) {
                    open(TraceBlock.Type.ALT, label);
                    first = false;
                } else {
                    elseBranch(label);
                }
                boolean before = aborted;
                emitList(branch);
                aborted = before;
            }
            next = end;
        }
        closeTo(mark);
        return next;
    }

    private void handle(Event event) {
        switch (event) {
            case CallEvent c -> {
                boolean cross = switch (c.edge().kind()) {
                    case EXECUTES -> handleQuery(c.edge());
                    case SENDS -> handleHttp(c.edge());
                    default -> handleCall(c.edge());
                };
                if (cross) {
                    crossed.add(c);
                }
            }
            case ThrowEvent t -> handleThrow(t);
        }
    }

    // ---------------- lời gọi, truy vấn, lỗi ----------------

    /** @return true nếu lời gọi đã phát bước sang một cột khác */
    private boolean handleCall(IrEdge edge) {
        Optional<IrNode> found = targetOf(edge);
        if (found.isEmpty()) {
            warnings.add("Một lời gọi từ " + edge.fromKey() + " không có node đích trong đồ thị nên bị bỏ qua");
            return false;
        }
        IrNode target = found.get();
        if (noise.isNoise(target, request.noiseFilters())) {
            return false;
        }
        Frame current = stack.peek();
        if (target.kind() == NodeKind.FE_METHOD && edge.confidence() == Confidence.UNRESOLVED) {
            warnings.add("Lời gọi " + simpleName(target) + "() phía Frontend chưa xác định được hàm đích nên luồng có thể thiếu bước");
        }
        String targetId = participants.forMethod(target);
        boolean unresolved = edge.confidence() == Confidence.UNRESOLVED;
        if (targetId == null || targetId.equals(current.participantId())) {
            if (options.selfSteps() && isBusinessSelfStep(target, targetId)) {
                self.add(current.participantId(), simpleName(target) + "()", edge.confidence(), edge.reason(), subgraph.edgeRef(edge), subgraph.isPinned(edge));
            }
            if (!unresolved && (!options.collapseRepeats() || foldedExpanded.add(target.key()))) {
                emitMethod(target, current.participantId(), current.participantId(), false);
            }
            return false;
        }
        flushSelf();
        Integer first = unresolved || !options.collapseRepeats() ? null : firstCall.get(target.key());
        int callId = step(current.participantId(), targetId, EdgeKind.CALLS, label(target), edge, subgraph.isPinned(edge), false, first == null ? 0 : first);
        if (unresolved) {
            step(targetId, current.participantId(), null, UNRESOLVED_RETURN, Confidence.UNRESOLVED, null, null, true, false, false, 0);
            return true;
        }
        if (first == null) {
            firstCall.put(target.key(), callId);
            emitMethod(target, targetId, current.participantId(), true);
        }
        if (aborted) {
            return true;
        }
        flushSelf();
        step(targetId, current.participantId(), null, returnLabel(target), edge.confidence(), null, null, true, false, false, 0);
        return true;
    }

    /**
     * Có đưa lời gọi vào bước tự xử lý không: hàm cùng lớp (private / dùng chung) hoặc hàm của entity (đối tượng nghiệp vụ).
     * Lớp tiện ích (mapper, parser, converter…) và hàm kiểm tra dữ liệu đầu vào ({@code validate…}) không mang ý nghĩa nghiệp
     * vụ nên không vẽ — nhưng vẫn đi sâu vào bên trong để thấy lời gọi thật sự sang Service / Repository.
     */
    private static boolean isBusinessSelfStep(IrNode target, String targetId) {
        if (simpleName(target).toLowerCase(java.util.Locale.ROOT).startsWith("validate")) {
            return false;
        }
        return targetId != null || Boolean.TRUE.equals(target.attrs().get("domainModel"));
    }

    /** Đích của lời gọi; chưa xác định đích thì lấy ứng viên đầu tiên chỉ để biết cột (không đi sâu vào ứng viên). */
    private Optional<IrNode> targetOf(IrEdge edge) {
        if (edge.toKey() != null) {
            return graph.node(edge.toKey());
        }
        return edge.candidates().stream().findFirst().flatMap(c -> graph.node(c.nodeKey()));
    }

    /**
     * Lời gọi HTTP: đi theo liên kết {@code HTTP_REQUEST} của Linker (đã áp chốt tay). Tới API → bước {@code METHOD path}
     * sang Controller, dò tiếp hàm xử lý, rồi chiều trả về {@code 200 <kiểu>}; tới hệ thống ngoài → bước sang cột của hệ
     * thống đó; chưa nối được → bước "chưa xác định" tới cột "API chưa xác định", không chọn hộ ứng viên.
     *
     * @return true nếu đã phát bước sang cột khác
     */
    private boolean handleHttp(IrEdge sends) {
        Optional<IrNode> found = graph.node(sends.toKey());
        if (found.isEmpty()) {
            return false;
        }
        IrNode http = found.get();
        String pid = stack.peek().participantId();
        flushSelf();
        Optional<IrEdge> link = graph.outgoing(http.key()).stream().filter(e -> e.kind() == EdgeKind.HTTP_REQUEST).findFirst();
        if (link.isEmpty() || link.get().toKey() == null) {
            String lane = participants.external(UNKNOWN_API, null);
            if (link.isEmpty()) {
                step(pid, lane, EdgeKind.HTTP_REQUEST, http.name(), Confidence.UNRESOLVED,
                        "Lời gọi chưa được nối với API nào (chưa chạy bước liên kết, hoặc liên kết đã bị loại bỏ)",
                        null, false, false, false, 0);
            } else {
                step(pid, lane, EdgeKind.HTTP_REQUEST, http.name(), link.get(), false, false, 0);
            }
            step(lane, pid, null, UNRESOLVED_RETURN, Confidence.UNRESOLVED, null, null, true, false, false, 0);
            return true;
        }
        IrEdge edge = link.get();
        Optional<IrNode> target = graph.node(edge.toKey());
        if (target.isEmpty()) {
            warnings.add("Lời gọi " + http.name() + " trỏ tới thành phần không có trong đồ thị nên bị bỏ qua");
            return false;
        }
        IrNode endpoint = target.get();
        boolean pinned = subgraph.isPinned(edge);
        if (endpoint.kind() != NodeKind.API_ENDPOINT) {
            String name = endpoint.displayName() != null ? endpoint.displayName() : endpoint.name();
            String lane = participants.external(name, endpoint.key());
            step(pid, lane, EdgeKind.HTTP_REQUEST, http.name(), edge, pinned, false, 0);
            step(lane, pid, null, "kết quả", edge.confidence(), null, null, true, false, false, 0);
            return true;
        }
        Optional<IrNode> handler = graph.outgoing(endpoint.key()).stream()
                .filter(e -> e.kind() == EdgeKind.HANDLED_BY && e.toKey() != null)
                .findFirst()
                .flatMap(e -> graph.node(e.toKey()));
        if (handler.isEmpty()) {
            String lane = participants.external("API " + endpoint.name(), endpoint.key());
            step(pid, lane, EdgeKind.HTTP_REQUEST, endpoint.name(), edge, pinned, false, 0);
            step(lane, pid, null, responseLabel(endpoint), edge.confidence(), null, null, true, false, false, 0);
            warnings.add("API " + endpoint.name() + " chưa có hàm xử lý trong mã nguồn nên không dò tiếp được");
            return true;
        }
        String controller = participants.controller(handler.get());
        Integer first = (options.collapseRepeats() || firstCall.containsKey(handler.get().key()))
                ? firstCall.get(handler.get().key())
                : null;
        int callId = step(pid, controller, EdgeKind.HTTP_REQUEST, endpoint.name(), edge, pinned, false, first == null ? 0 : first);
        if (first == null) {
            firstCall.put(handler.get().key(), callId);
            emitMethod(handler.get(), controller, pid, true);
        }
        if (aborted) {
            return true;
        }
        flushSelf();
        step(controller, pid, null, responseLabel(endpoint), edge.confidence(), null, null, true, false, false, 0);
        return true;
    }

    /**
     * Truy vấn: bước từ cột đang chạy tới cột của TỪNG bảng nó đụng tới (truy vấn nhiều bảng thì mỗi bảng một cặp bước đi và
     * trả về), nhãn là câu lệnh ({@code SELECT}, {@code INSERT/UPDATE}…). Mức tin cậy của bước = mức thấp nhất của cạnh
     * method → truy vấn và cạnh truy vấn → bảng đó. Không xác định được bảng thì tới cột "Database" chung.
     */
    private boolean handleQuery(IrEdge execute) {
        Optional<IrNode> found = graph.node(execute.toKey());
        if (found.isEmpty()) {
            return false;
        }
        IrNode query = found.get();
        List<IrEdge> tables = graph.outgoing(query.key()).stream()
                .filter(e -> e.kind() == EdgeKind.READS || e.kind() == EdgeKind.WRITES)
                .toList();
        String pid = stack.peek().participantId();
        String verb = String.valueOf(query.attrs().getOrDefault("verb", query.name()));
        flushSelf();
        if (tables.isEmpty()) {
            boolean writes = "WRITE".equals(query.attrs().get("operation")) || "DELETE".equals(query.attrs().get("operation"));
            String db = participants.database();
            step(pid, db, EdgeKind.EXECUTES, query.name(), execute, false, false, 0);
            step(db, pid, null, writes ? "OK" : "kết quả", execute.confidence(), null, null, true, false, false, 0);
            return true;
        }
        for (IrEdge table : tables) {
            IrEdge weakest = table.confidence().rank() < execute.confidence().rank() ? table : execute;
            Optional<IrNode> node = table.toKey() == null ? Optional.empty() : graph.node(table.toKey());
            String name = node.map(n -> String.valueOf(n.attrs().getOrDefault("name", n.name()))).orElse("?");
            String lane = participants.table(name, table.toKey());
            step(pid, lane, table.kind(), verb, weakest, false, false, 0);
            step(lane, pid, null, table.kind() == EdgeKind.WRITES ? "OK" : "kết quả", weakest.confidence(), null, null, true, false, false, 0);
        }
        return true;
    }

    /** Chiều trả về lỗi từ cột đang chạy lên tới Client; nhãn đầu là khóa thông báo (hoặc tên exception), các cấp trên là tên exception. */
    private void handleThrow(ThrowEvent thrown) {
        if (!options.errors() || !isBusinessException(thrown)) {
            return;
        }
        flushSelf();
        String exception = stripPackage(thrown.exception());
        String label = thrown.messageKey() != null ? thrown.messageKey() : exception;
        for (Frame frame : stack) {
            if (frame.participantId().equals(frame.callerId())) {
                continue;
            }
            step(frame.participantId(), frame.callerId(), null, label, Confidence.CERTAIN, null, null, true, false, true, 0);
            label = exception;
        }
        if (thrown.guard() == null) {
            aborted = true;
        }
    }

    // ---------------- phát bước và khối ----------------

    private int step(String from, String to, EdgeKind kind, String label, IrEdge edge, boolean pinned, boolean error, int repeatOfId) {
        return step(from, to, kind, label, edge.confidence(), edge.reason(), subgraph.edgeRef(edge), false, pinned, error, repeatOfId);
    }

    /** @return mã tạm của bước vừa phát */
    private int step(
            String from,
            String to,
            EdgeKind kind,
            String label,
            Confidence confidence,
            String reason,
            String edgeRef,
            boolean returning,
            boolean pinned,
            boolean error,
            int repeatOfId) {
        int id = nextStepId++;
        items.add(new TraceItem.StepItem(
                new TraceStep(0, from, to, kind, label, confidence, reason, edgeRef, returning, pinned, error, null), id, repeatOfId));
        return id;
    }

    private void flushSelf() {
        self.flush().ifPresent(s -> items.add(new TraceItem.StepItem(s, nextStepId++, 0)));
    }

    private void open(TraceBlock.Type type, String label) {
        flushSelf();
        items.add(new TraceItem.Open(type, label));
        openDepth++;
    }

    private void elseBranch(String label) {
        flushSelf();
        items.add(new TraceItem.Else(label));
    }

    private void closeTo(int depth) {
        flushSelf();
        while (openDepth > depth) {
            items.add(new TraceItem.Close());
            openDepth--;
        }
    }

    // ---------------- nhãn ----------------

    /** Tên method, bỏ phần tham số của chữ ký {@code name(types)}. */
    private static String simpleName(IrNode method) {
        String signature = String.valueOf(method.attrs().getOrDefault("signature", method.name()));
        int paren = signature.indexOf('(');
        return paren < 0 ? signature : signature.substring(0, paren);
    }

    /** {@code existsByCode(code)}: tên method + tên tham số khai báo ở method đích. */
    private static String label(IrNode method) {
        String name = simpleName(method);
        if (method.attrs().get("paramNames") instanceof List<?> names) {
            return name + "(" + String.join(", ", names.stream().map(String::valueOf).toList()) + ")";
        }
        String signature = String.valueOf(method.attrs().getOrDefault("signature", ""));
        return name + (signature.endsWith("()") || signature.isEmpty() ? "()" : "(…)");
    }

    /** Nhãn chiều trả về theo kiểu trả về khai báo của method đích. */
    static String returnLabel(IrNode method) {
        Object type = method.attrs().get("returnType");
        if (!(type instanceof String text) || text.isBlank()) {
            return "kết quả";
        }
        return switch (text.strip()) {
            case "void", "Void" -> "OK";
            case "boolean", "Boolean" -> "true / false";
            default -> stripPackage(text.strip());
        };
    }

    /** {@code 200 ApiResponse<CategoryResponse>}; hàm không trả về gì thì {@code 200 OK}. */
    static String responseLabel(IrNode endpoint) {
        Object type = endpoint.attrs().get("responseType");
        return type instanceof String text && !text.isBlank() ? "200 " + stripPackage(text.strip()) : "200 OK";
    }

    /** Bỏ tên gói: {@code java.util.List<com.x.Foo>} → {@code List<Foo>}. */
    static String stripPackage(String type) {
        return type.replaceAll("(?:\\b[a-z_][\\w]*\\.)+(?=[A-Z])", "");
    }
}
