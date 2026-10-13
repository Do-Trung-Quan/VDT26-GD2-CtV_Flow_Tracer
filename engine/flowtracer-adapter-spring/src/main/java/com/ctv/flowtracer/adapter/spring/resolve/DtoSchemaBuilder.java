package com.ctv.flowtracer.adapter.spring.resolve;

import com.ctv.flowtracer.adapter.spring.facts.JavaFileFacts;
import com.ctv.flowtracer.adapter.spring.facts.JavaFileFacts.DtoFieldFact;
import com.ctv.flowtracer.adapter.spring.facts.JavaFileFacts.TypeFact;
import com.ctv.flowtracer.adapter.spring.resolve.ClassIndex.TypeInfo;
import com.ctv.flowtracer.ir.EdgeKind;
import com.ctv.flowtracer.ir.IrEdge;
import com.ctv.flowtracer.ir.IrNode;
import com.ctv.flowtracer.ir.NodeKeys;
import com.ctv.flowtracer.ir.NodeKind;
import com.ctv.flowtracer.ir.SourceLocation;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * Dựng node {@code DATA_TYPE} (DTO / Entity / record: tên trường JSON theo {@code @JsonProperty} hoặc chiến lược đặt tên của
 * Jackson, kiểu, bắt buộc hay không, ràng buộc) cho mọi kiểu của dự án mà một endpoint dùng làm đầu vào / đầu ra, kể cả kiểu
 * lồng bên trong và lớp cha. Đồng thời ghi lên endpoint các <b>biểu thức kiểu</b> ({@code requestTypeExpr},
 * {@code responseTypeExpr}, {@code params[].typeExpr}) để lõi dựng đầu vào / đầu ra mà không phải đọc tên kiểu Java.
 *
 * <p>Biểu thức kiểu là cây {@code Map} với khóa {@code t}: {@code string / integer / number / boolean / date / datetime / uuid /
 * file / any}; {@code enum} (kèm {@code values}); {@code array} ({@code items}); {@code map} ({@code values}); {@code ref}
 * ({@code key} của node {@code DATA_TYPE}, {@code args} là các đối số generic); {@code var} ({@code name}: tham số kiểu như
 * {@code T} của {@code ApiResponse<T>}, được thay khi dựng đầu ra); {@code object} (kiểu thư viện không có mã nguồn, kèm
 * {@code name}). Không đoán: kiểu không rõ là {@code object}.
 */
public final class DtoSchemaBuilder {

    /** Giới hạn số kiểu dựng cho một Source, tránh bùng nổ khi DTO tham chiếu cả mô hình Entity. */
    static final int MAX_TYPES = 600;

    private static final Set<String> STRINGS = Set.of("String", "CharSequence", "char", "Character", "Locale", "URI", "URL", "Currency");
    private static final Set<String> INTEGERS = Set.of("int", "Integer", "long", "Long", "short", "Short", "byte", "Byte", "BigInteger", "AtomicInteger", "AtomicLong");
    private static final Set<String> NUMBERS = Set.of("double", "Double", "float", "Float", "BigDecimal", "Number");
    private static final Set<String> BOOLEANS = Set.of("boolean", "Boolean");
    private static final Set<String> DATES = Set.of("LocalDate", "java.sql.Date");
    private static final Set<String> DATE_TIMES = Set.of("LocalDateTime", "Instant", "OffsetDateTime", "ZonedDateTime", "Date", "Timestamp", "LocalTime", "OffsetTime", "Calendar");
    private static final Set<String> LISTS = Set.of("List", "Set", "Collection", "Iterable", "ArrayList", "LinkedList", "HashSet", "LinkedHashSet", "TreeSet", "Flux", "Stream", "SortedSet", "Queue", "Deque");
    private static final Set<String> MAPS = Set.of("Map", "HashMap", "LinkedHashMap", "TreeMap", "SortedMap", "ConcurrentHashMap");
    private static final Set<String> WRAPPERS = Set.of("Optional", "ResponseEntity", "HttpEntity", "Mono", "CompletableFuture", "DeferredResult", "Callable", "Future", "WebAsyncTask");
    private static final Set<String> FILES = Set.of("MultipartFile", "Resource", "InputStreamResource", "File", "byte[]", "InputStream");

    /** Kết quả: node kiểu, endpoint đã ghi biểu thức kiểu, cạnh {@code USES_TYPE}. */
    public record Result(List<IrNode> dataTypes, List<IrNode> endpoints, List<IrEdge> edges) {}

    private final ClassIndex index;
    private final Map<String, List<DtoFieldFact>> fieldsByType = new HashMap<>();
    private final Function<String, String> namingOf;
    private final Deque<String> queue = new ArrayDeque<>();
    private final Set<String> seen = new TreeSet<>();
    private final Map<String, IrNode> nodes = new TreeMap<>();

    /**
     * @param namingOf đường dẫn file → chiến lược đặt tên JSON của module chứa file (null nếu không khai báo)
     */
    public DtoSchemaBuilder(ClassIndex index, List<JavaFileFacts> facts, Function<String, String> namingOf) {
        this.index = index;
        this.namingOf = namingOf;
        for (JavaFileFacts f : facts) {
            for (DtoFieldFact field : f.dtoFields()) {
                fieldsByType.computeIfAbsent(field.declaringType(), k -> new ArrayList<>()).add(field);
            }
        }
    }

    public Result build(List<IrNode> endpoints) {
        var enriched = new ArrayList<IrNode>();
        var edges = new ArrayList<IrEdge>();
        for (IrNode endpoint : endpoints) {
            enriched.add(enrich(endpoint, edges));
        }
        while (!queue.isEmpty() && nodes.size() < MAX_TYPES) {
            String fqn = queue.poll();
            buildType(fqn).ifPresent(n -> nodes.put(n.key(), n));
        }
        return new Result(List.copyOf(nodes.values()), enriched, edges);
    }

    // ---------------- endpoint ----------------

    private IrNode enrich(IrNode endpoint, List<IrEdge> edges) {
        Optional<TypeInfo> controller = endpoint.attrs().get("controller") instanceof String c ? index.typeInfo(c) : Optional.empty();
        if (controller.isEmpty()) {
            return endpoint;
        }
        JavaFileFacts context = controller.get().file();
        Map<String, Object> attrs = new TreeMap<>(endpoint.attrs());
        List<String> requestKeys = new ArrayList<>();
        List<String> responseKeys = new ArrayList<>();
        if (endpoint.attrs().get("requestType") instanceof String text) {
            Map<String, Object> expr = parse(text, context, Set.of());
            attrs.put("requestTypeExpr", expr);
            collectRefs(expr, requestKeys);
        }
        if (endpoint.attrs().get("responseType") instanceof String text) {
            Map<String, Object> expr = parse(text, context, Set.of());
            attrs.put("responseTypeExpr", expr);
            collectRefs(expr, responseKeys);
        }
        if (endpoint.attrs().get("params") instanceof List<?> params) {
            var withExpr = new ArrayList<Map<String, Object>>();
            for (Object p : params) {
                if (p instanceof Map<?, ?> m) {
                    Map<String, Object> copy = new TreeMap<>();
                    m.forEach((k, v) -> copy.put(String.valueOf(k), v));
                    if (copy.get("type") instanceof String type) {
                        Map<String, Object> expr = parse(type, context, Set.of());
                        copy.put("typeExpr", expr);
                        if (!"BODY".equals(copy.get("in"))) {
                            collectRefs(expr, requestKeys);
                        }
                    }
                    withExpr.add(copy);
                }
            }
            attrs.put("params", withExpr);
        }
        SourceLocation location = endpoint.location();
        int order = 0;
        for (String key : new LinkedHashSet<>(requestKeys)) {
            edges.add(new IrEdge(endpoint.key(), key, EdgeKind.USES_TYPE, com.ctv.flowtracer.ir.Confidence.CERTAIN,
                    "Kiểu dữ liệu đầu vào của API", order++, null, List.of(), location));
        }
        for (String key : new LinkedHashSet<>(responseKeys)) {
            edges.add(new IrEdge(endpoint.key(), key, EdgeKind.USES_TYPE, com.ctv.flowtracer.ir.Confidence.CERTAIN,
                    "Kiểu dữ liệu đầu ra của API", order++, null, List.of(), location));
        }
        return new IrNode(endpoint.key(), endpoint.kind(), endpoint.name(), endpoint.displayName(), endpoint.location(), attrs);
    }

    // ---------------- node kiểu ----------------

    private Optional<IrNode> buildType(String fqn) {
        Optional<TypeInfo> found = index.typeInfo(fqn);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        TypeInfo info = found.get();
        TypeFact type = info.type();
        String naming = namingOf.apply(info.file().relativePath());
        Map<String, Object> attrs = new TreeMap<>();
        attrs.put("kind", type.kind().name());
        if (!type.typeParameters().isEmpty()) {
            attrs.put("typeParameters", type.typeParameters());
        }
        if (naming != null && !naming.isBlank()) {
            attrs.put("jsonNaming", naming);
        }
        attrs.put("fields", fieldsOf(info, naming, new LinkedHashSet<>(), Map.of()));
        String doc = type.javadoc() == null ? null : firstSentence(type.javadoc());
        return Optional.of(new IrNode(
                NodeKeys.javaType(fqn),
                NodeKind.DATA_TYPE,
                simple(fqn),
                doc,
                SourceLocation.of(info.file().relativePath(), type.line()),
                attrs));
    }

    /**
     * Trường của một kiểu: lớp cha trước (như Jackson), rồi trường của chính nó. Tham số kiểu của lớp cha được thay bằng đối số
     * khai báo ở {@code extends Base<Foo>}.
     */
    private List<Map<String, Object>> fieldsOf(TypeInfo info, String naming, Set<String> visiting, Map<String, Map<String, Object>> substitution) {
        var result = new ArrayList<Map<String, Object>>();
        if (!visiting.add(info.fqn())) {
            return result;
        }
        TypeFact type = info.type();
        Set<String> vars = new TreeSet<>(type.typeParameters());
        if (type.superclass() != null) {
            String raw = TypeNames.stripGenerics(type.superclass());
            Optional<TypeInfo> parent = index.resolveSimpleName(raw, info.file()).flatMap(index::typeInfo);
            if (parent.isPresent() && fieldsByType.containsKey(parent.get().fqn())) {
                var args = new HashMap<String, Map<String, Object>>();
                List<String> given = TypeNames.typeArguments(type.superclass());
                List<String> names = parent.get().type().typeParameters();
                for (int i = 0; i < names.size() && i < given.size(); i++) {
                    args.put(names.get(i), substitute(parse(given.get(i), info.file(), vars), substitution));
                }
                result.addAll(fieldsOf(parent.get(), naming, visiting, args));
            }
        }
        for (DtoFieldFact f : fieldsByType.getOrDefault(info.fqn(), List.of())) {
            Map<String, Object> field = new TreeMap<>();
            field.put("name", f.jsonName() != null ? f.jsonName() : applyNaming(f.name(), naming));
            field.put("type", substitute(parse(f.javaType(), info.file(), vars), substitution));
            boolean required = false;
            var constraints = new ArrayList<Map<String, Object>>();
            for (String c : f.constraints()) {
                required |= requiredBy(c);
                constraint(c).ifPresent(constraints::add);
            }
            if (required) {
                field.put("required", true);
            }
            if (!constraints.isEmpty()) {
                field.put("constraints", constraints);
            }
            if (f.description() != null) {
                field.put("description", f.description());
            }
            result.add(field);
        }
        visiting.remove(info.fqn());
        return result;
    }

    // ---------------- biểu thức kiểu ----------------

    /** Phân tích kiểu Java viết trong mã thành biểu thức kiểu; kiểu của dự án được xếp vào hàng đợi để dựng node. */
    Map<String, Object> parse(String javaType, JavaFileFacts context, Set<String> typeVars) {
        String text = javaType == null ? "" : javaType.strip();
        if (text.endsWith("[]") && !text.equals("byte[]")) {
            return expr("array", "items", parse(text.substring(0, text.length() - 2), context, typeVars));
        }
        if (text.endsWith("...")) {
            return expr("array", "items", parse(text.substring(0, text.length() - 3), context, typeVars));
        }
        String raw = TypeNames.simple(TypeNames.stripGenerics(text));
        List<String> args = TypeNames.typeArguments(text);
        if (text.startsWith("?")) {
            return Map.of("t", "any");
        }
        if (typeVars.contains(raw) && args.isEmpty()) {
            return expr("var", "name", raw);
        }
        if (STRINGS.contains(raw)) {
            return Map.of("t", "string");
        }
        if (INTEGERS.contains(raw)) {
            return Map.of("t", "integer");
        }
        if (NUMBERS.contains(raw)) {
            return Map.of("t", "number");
        }
        if (BOOLEANS.contains(raw)) {
            return Map.of("t", "boolean");
        }
        if (DATES.contains(raw)) {
            return Map.of("t", "date");
        }
        if (DATE_TIMES.contains(raw)) {
            return Map.of("t", "datetime");
        }
        if (raw.equals("UUID")) {
            return Map.of("t", "uuid");
        }
        if (raw.equals("Object") || raw.equals("JsonNode") || raw.equals("void") || raw.equals("Void")) {
            return Map.of("t", "any");
        }
        if (FILES.contains(raw) || text.equals("byte[]")) {
            return Map.of("t", "file");
        }
        if (LISTS.contains(raw)) {
            return expr("array", "items", args.isEmpty() ? Map.of("t", "any") : parse(args.get(0), context, typeVars));
        }
        if (MAPS.contains(raw)) {
            return expr("map", "values", args.size() < 2 ? Map.of("t", "any") : parse(args.get(1), context, typeVars));
        }
        if (WRAPPERS.contains(raw)) {
            return args.isEmpty() ? Map.of("t", "any") : parse(args.get(0), context, typeVars);
        }
        Optional<String> fqn = index.resolveSimpleName(TypeNames.stripGenerics(text), context);
        Optional<TypeInfo> info = fqn.flatMap(index::typeInfo);
        if (info.isPresent()) {
            TypeFact t = info.get().type();
            if (t.kind() == TypeFact.Kind.ENUM) {
                return expr("enum", "values", t.enumConstants());
            }
            if (t.kind() == TypeFact.Kind.CLASS || t.kind() == TypeFact.Kind.RECORD) {
                if (seen.add(info.get().fqn())) {
                    queue.add(info.get().fqn());
                }
                Map<String, Object> ref = new TreeMap<>();
                ref.put("t", "ref");
                ref.put("key", NodeKeys.javaType(info.get().fqn()));
                if (!args.isEmpty()) {
                    ref.put("args", args.stream().map(a -> (Object) parse(a, context, typeVars)).toList());
                }
                return ref;
            }
        }
        Map<String, Object> unknown = new TreeMap<>();
        unknown.put("t", "object");
        unknown.put("name", raw);
        if (!args.isEmpty()) {
            unknown.put("args", args.stream().map(a -> (Object) parse(a, context, typeVars)).toList());
        }
        return unknown;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> substitute(Map<String, Object> expr, Map<String, Map<String, Object>> substitution) {
        if (substitution.isEmpty()) {
            return expr;
        }
        if ("var".equals(expr.get("t"))) {
            return substitution.getOrDefault(String.valueOf(expr.get("name")), expr);
        }
        Map<String, Object> copy = new TreeMap<>();
        expr.forEach((k, v) -> {
            if (v instanceof Map<?, ?> m) {
                copy.put(k, substitute((Map<String, Object>) m, substitution));
            } else if (v instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map) {
                copy.put(k, list.stream().map(x -> (Object) substitute((Map<String, Object>) x, substitution)).toList());
            } else {
                copy.put(k, v);
            }
        });
        return copy;
    }

    @SuppressWarnings("unchecked")
    private static void collectRefs(Map<String, Object> expr, List<String> out) {
        if ("ref".equals(expr.get("t"))) {
            out.add(String.valueOf(expr.get("key")));
        }
        for (Object v : expr.values()) {
            if (v instanceof Map<?, ?> m) {
                collectRefs((Map<String, Object>) m, out);
            } else if (v instanceof List<?> list) {
                list.stream().filter(x -> x instanceof Map).forEach(x -> collectRefs((Map<String, Object>) x, out));
            }
        }
    }

    private static Map<String, Object> expr(String t, String key, Object value) {
        Map<String, Object> m = new TreeMap<>();
        m.put("t", t);
        m.put(key, value);
        return m;
    }

    // ---------------- ràng buộc ----------------

    private static boolean requiredBy(String constraint) {
        String name = constraintName(constraint);
        return name.equals("NotNull") || name.equals("NotBlank") || name.equals("NotEmpty")
                || name.equals("Column") && constraint.contains("nullable=false");
    }

    /** Chuyển {@code Size(min=1, max=50)} thành ràng buộc có cấu trúc; null nếu không có ý nghĩa với người đọc. */
    private static Optional<Map<String, Object>> constraint(String text) {
        String name = constraintName(text);
        Map<String, String> args = constraintArgs(text);
        Map<String, Object> c = new TreeMap<>();
        switch (name) {
            case "Size", "Length", "Range" -> {
                c.put("kind", "size");
                putNumber(c, "min", args.get("min"));
                putNumber(c, "max", args.get("max"));
            }
            case "Min", "DecimalMin" -> {
                c.put("kind", "min");
                putNumber(c, "value", args.get("value"));
            }
            case "Max", "DecimalMax" -> {
                c.put("kind", "max");
                putNumber(c, "value", args.get("value"));
            }
            case "Pattern" -> {
                c.put("kind", "pattern");
                if (args.get("regexp") != null) {
                    c.put("value", args.get("regexp"));
                }
            }
            case "NotBlank", "NotEmpty" -> c.put("kind", "notBlank");
            case "Email" -> c.put("kind", "email");
            case "Positive" -> c.put("kind", "positive");
            case "PositiveOrZero" -> c.put("kind", "nonNegative");
            case "Past", "PastOrPresent" -> c.put("kind", "past");
            case "Future", "FutureOrPresent" -> c.put("kind", "future");
            case "Column" -> {
                if ("true".equals(args.get("unique"))) {
                    c.put("kind", "unique");
                } else if (args.get("length") != null) {
                    c.put("kind", "size");
                    putNumber(c, "max", args.get("length"));
                } else {
                    return Optional.empty();
                }
            }
            default -> {
                return Optional.empty();
            }
        }
        return c.size() == 1 && c.containsKey("kind") && ("size".equals(c.get("kind")) || "min".equals(c.get("kind")) || "max".equals(c.get("kind")))
                ? Optional.empty()
                : Optional.of(c);
    }

    private static void putNumber(Map<String, Object> target, String key, String value) {
        if (value == null) {
            return;
        }
        try {
            target.put(key, value.contains(".") ? (Object) Double.valueOf(value) : (Object) Long.valueOf(value));
        } catch (NumberFormatException e) {
            // giá trị là tên hằng số hoặc biểu thức: không tính được thì bỏ, không đoán
        }
    }

    private static String constraintName(String text) {
        int paren = text.indexOf('(');
        return paren < 0 ? text : text.substring(0, paren);
    }

    private static Map<String, String> constraintArgs(String text) {
        Map<String, String> args = new TreeMap<>();
        int open = text.indexOf('(');
        int close = text.lastIndexOf(')');
        if (open < 0 || close < open) {
            return args;
        }
        String body = text.substring(open + 1, close);
        int start = 0;
        for (int i = 0; i <= body.length(); i++) {
            if (i == body.length() || body.startsWith(", ", i) && nextIsKey(body, i + 2)) {
                String part = body.substring(start, i);
                int eq = part.indexOf('=');
                if (eq > 0) {
                    args.put(part.substring(0, eq).strip(), part.substring(eq + 1).strip());
                }
                start = i + 2;
            }
        }
        return args;
    }

    /** Sau ", " là một "khóa=" (không phải dấu phẩy nằm trong giá trị chuỗi như mẫu regex). */
    private static boolean nextIsKey(String body, int from) {
        int eq = body.indexOf('=', from);
        if (eq < 0) {
            return false;
        }
        String key = body.substring(from, eq);
        return !key.isEmpty() && key.chars().allMatch(Character::isLetterOrDigit);
    }

    // ---------------- tên ----------------

    /** Áp chiến lược đặt tên của Jackson ({@code spring.jackson.property-naming-strategy}); không khai báo thì giữ tên trường. */
    static String applyNaming(String name, String strategy) {
        if (strategy == null || strategy.isBlank()) {
            return name;
        }
        String s = strategy.toUpperCase(Locale.ROOT).replace("-", "_");
        if (s.endsWith("SNAKE_CASE")) {
            return words(name, "_", false);
        }
        if (s.endsWith("KEBAB_CASE")) {
            return words(name, "-", false);
        }
        if (s.endsWith("UPPER_CAMEL_CASE") || s.endsWith("PASCAL_CASE")) {
            return name.isEmpty() ? name : Character.toUpperCase(name.charAt(0)) + name.substring(1);
        }
        if (s.endsWith("LOWER_CASE")) {
            return name.toLowerCase(Locale.ROOT);
        }
        return name;
    }

    private static String words(String name, String separator, boolean upper) {
        var out = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c) && i > 0) {
                out.append(separator);
            }
            out.append(upper ? Character.toUpperCase(c) : Character.toLowerCase(c));
        }
        return out.toString();
    }

    private static String simple(String fqn) {
        return fqn.substring(fqn.lastIndexOf('.') + 1);
    }

    private static String firstSentence(String javadoc) {
        String flat = javadoc.replaceAll("\\s+", " ").strip();
        int dot = flat.indexOf(". ");
        return dot < 0 ? flat : flat.substring(0, dot + 1);
    }
}
