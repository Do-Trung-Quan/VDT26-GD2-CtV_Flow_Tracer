package com.ctv.flowtracer.adapter.spring.resolve;

import com.ctv.flowtracer.adapter.spring.config.QuerySinkRules;
import com.ctv.flowtracer.adapter.spring.extract.MappingExtractor;
import com.ctv.flowtracer.adapter.spring.facts.JavaFileFacts;
import com.ctv.flowtracer.adapter.spring.facts.JavaFileFacts.CallSiteFact;
import com.ctv.flowtracer.adapter.spring.facts.JavaFileFacts.MethodFact;
import com.ctv.flowtracer.adapter.spring.facts.JavaFileFacts.StringFragmentFact;
import com.ctv.flowtracer.adapter.spring.facts.JavaFileFacts.ThrowFact;
import com.ctv.flowtracer.adapter.spring.resolve.ClassIndex.MethodMatch;
import com.ctv.flowtracer.adapter.spring.resolve.ClassIndex.TypeInfo;
import com.ctv.flowtracer.adapter.spring.resolve.DataAccessResolver.QueryInfo;
import com.ctv.flowtracer.adapter.spring.resolve.DataAccessResolver.SinkKind;
import com.ctv.flowtracer.adapter.spring.resolve.DataAccessResolver.TableRef;
import com.ctv.flowtracer.adapter.spring.resolve.InjectionResolver.Injection;
import com.ctv.flowtracer.ir.Candidate;
import com.ctv.flowtracer.ir.Confidence;
import com.ctv.flowtracer.ir.Diagnostic;
import com.ctv.flowtracer.ir.EdgeKind;
import com.ctv.flowtracer.ir.Guard;
import com.ctv.flowtracer.ir.IrEdge;
import com.ctv.flowtracer.ir.IrNode;
import com.ctv.flowtracer.ir.NodeKeys;
import com.ctv.flowtracer.ir.NodeKind;
import com.ctv.flowtracer.ir.SourceLocation;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Dựng phần xử lý Backend của IR từ các endpoint: {@code HANDLED_BY} → method controller, rồi theo từng lời gọi
 * ({@code CALLS}) tới service / repository, và thao tác dữ liệu ({@code EXECUTES} → {@code READS}/{@code WRITES}).
 *
 * <p>Lời gọi HTTP sang service khác — {@code RestTemplate} / {@code RestOperations}, {@code WebClient} / {@code RestClient}
 * ({@code .get().uri(…)}), interface {@code @FeignClient} — thành node {@code HTTP_CALL} ({@code origin = BE}) với cạnh
 * {@code SENDS}; Linker nối nó với API trong Workspace (BE → BE) hoặc hệ thống ngoài.
 *
 * <p>Chỉ dựng phần ĐI ĐƯỢC TỪ ENDPOINT (kể cả các ứng viên của một lời gọi chưa xác định, để người dùng chốt tay rồi dò
 * tiếp được). Mọi thứ đều suy từ mã nguồn: lời gọi không tìm được hàm đích thì bị bỏ và chỉ đếm vào một chẩn đoán tổng
 * hợp, không bịa cạnh. Duyệt theo hàng đợi và TreeMap nên kết quả tất định.
 */
final class FlowResolver {

    /** Kết quả dựng. */
    record Result(List<IrNode> nodes, List<IrEdge> edges, List<Diagnostic> diagnostics) {}

    /** Method đích của một lời gọi: khai báo trong mã nguồn, hoặc kế thừa từ Spring Data ({@code fact == null}). */
    private record Target(
            TypeInfo type, String name, List<String> paramTypes, List<String> paramNames, MethodFact fact, boolean placeholder) {

        static Target of(TypeInfo type, MethodFact fact) {
            return new Target(type, fact.name(), fact.paramTypes(), fact.paramNames(), fact, false);
        }

        /** Hàm không tìm thấy trong mã nguồn: node giữ chỗ để lời gọi chưa xác định vẫn có cột và nhãn. */
        static Target placeholder(TypeInfo type, String name, int argCount) {
            int n = Math.max(argCount, 1);
            return new Target(type, name, java.util.Collections.nCopies(n, "?"), java.util.Collections.nCopies(n, "…"), null, true);
        }

        String key() {
            return NodeKeys.javaMethod(type.fqn(), name, paramTypes);
        }

        SourceLocation location() {
            return fact != null
                    ? new SourceLocation(type.file().relativePath(), fact.startLine(), fact.endLine())
                    : SourceLocation.of(type.file().relativePath(), type.type().line());
        }
    }

    /** Receiver của một lời gọi: kiểu trong dự án, hoặc tên kiểu thư viện. */
    private record Receiver(
            String projectType, String externalName, String fieldName, String qualifier, boolean fixedType, TypeRef declared) {

        static Receiver project(String type, String fieldName, String qualifier, boolean fixed, TypeRef declared) {
            return new Receiver(type, null, fieldName, qualifier, fixed, declared);
        }

        static Receiver external(String name, String fieldName, boolean fixed, TypeRef declared) {
            return new Receiver(null, name, fieldName, null, fixed, declared);
        }
    }

    /** Kiểu viết trong mã (còn generic) cùng file để giải tên ngắn của nó. */
    private record TypeRef(String text, JavaFileFacts file) {}

    private final ClassIndex index;
    private final LayerClassifier layers;
    private final BusinessNames names;
    private final InjectionResolver injections;
    private final ImplementationResolver implementations;
    private final DataAccessResolver dataAccess;
    private final QuerySinkRules sinkRules;
    private final Map<String, List<CallSiteFact>> callsByMethod = new TreeMap<>();
    private final Map<String, List<ThrowFact>> throwsByMethod = new TreeMap<>();
    private final Map<String, List<StringFragmentFact>> fragmentsByMethod = new TreeMap<>();

    private final Map<String, IrNode> methodNodes = new TreeMap<>();
    private final Map<String, IrNode> queryNodes = new TreeMap<>();
    private final Map<String, IrNode> tableNodes = new TreeMap<>();
    private final Map<String, IrNode> httpNodes = new TreeMap<>();
    private final Map<String, Integer> httpCounters = new TreeMap<>();
    private final ConstantTable constants;
    private final Map<String, String> messages;
    private final Map<String, List<JavaFileFacts.DtoFieldFact>> dtoFieldsByType = new TreeMap<>();
    private final List<IrEdge> edges = new ArrayList<>();
    private final Set<String> seen = new HashSet<>();
    private final Deque<Target> queue = new ArrayDeque<>();
    private final Map<String, Integer> sinkCounters = new TreeMap<>();
    private int skippedCalls;
    private int unresolvedCalls;

    /** @param messages khóa thông báo → câu hiển thị (file messages của dự án), để ghi lời nhắn của lệnh {@code throw} */
    FlowResolver(List<JavaFileFacts> facts, QuerySinkRules sinkRules, Map<String, String> messages) {
        this.messages = messages;
        this.index = new ClassIndex(facts);
        this.layers = new LayerClassifier(index);
        this.names = new BusinessNames(index, layers);
        this.injections = new InjectionResolver(index);
        this.implementations = new ImplementationResolver(index);
        this.constants = new ConstantTable(facts);
        this.dataAccess = new DataAccessResolver(index, new EntityTableResolver(index, constants), constants);
        this.sinkRules = sinkRules;
        for (JavaFileFacts f : facts) {
            f.callSites().forEach(c -> callsByMethod.computeIfAbsent(c.callerMethod(), k -> new ArrayList<>()).add(c));
            f.throwsFacts().forEach(t -> throwsByMethod.computeIfAbsent(t.method(), k -> new ArrayList<>()).add(t));
            f.stringFragments().forEach(s -> fragmentsByMethod.computeIfAbsent(s.method(), k -> new ArrayList<>()).add(s));
            f.dtoFields().forEach(d -> dtoFieldsByType.computeIfAbsent(d.declaringType(), k -> new ArrayList<>()).add(d));
        }
        callsByMethod.values().forEach(l -> l.sort(Comparator.comparingInt(CallSiteFact::callOrder)));
        throwsByMethod.values().forEach(l -> l.sort(Comparator.comparingInt(ThrowFact::order)));
    }

    /** @param endpoints node {@code API_ENDPOINT} đã dựng (thuộc tính {@code controller} và {@code handler} cho biết method xử lý) */
    Result resolve(List<IrNode> endpoints) {
        for (IrNode endpoint : endpoints.stream().sorted(Comparator.comparing(IrNode::key)).toList()) {
            handlerOf(endpoint).ifPresent(handler -> {
                enqueue(handler);
                edges.add(new IrEdge(
                        endpoint.key(), handler.key(), EdgeKind.HANDLED_BY, Confidence.CERTAIN, null, 0, null, List.of(), handler.location()));
            });
        }
        while (!queue.isEmpty()) {
            process(queue.poll());
        }
        var nodes = new ArrayList<IrNode>(methodNodes.values());
        nodes.addAll(queryNodes.values());
        nodes.addAll(tableNodes.values());
        nodes.addAll(httpNodes.values());
        return new Result(nodes, edges, diagnostics());
    }

    private Optional<Target> handlerOf(IrNode endpoint) {
        Object controller = endpoint.attrs().get("controller");
        Object handler = endpoint.attrs().get("handler");
        if (!(controller instanceof String fqn) || !(handler instanceof String text) || !text.contains("#")) {
            return Optional.empty();
        }
        String signature = text.substring(text.indexOf('#') + 1);
        return index.typeInfo(fqn).flatMap(info -> info.methods().stream()
                .filter(m -> m.signature().equals(signature))
                .findFirst()
                .map(m -> Target.of(info, m)));
    }

    private String enqueue(Target target) {
        String key = target.key();
        if (seen.add(key)) {
            methodNodes.put(key, methodNode(target));
            queue.add(target);
        }
        return key;
    }

    private void process(Target target) {
        if (target.fact() == null) {
            attachRepositoryQuery(target);
            return;
        }
        String callerKey = target.type().fqn() + "#" + target.fact().signature();
        Map<Integer, TypeRef> results = new HashMap<>();
        Map<Integer, CallSiteFact> byOrder = new HashMap<>();
        for (CallSiteFact site : callsByMethod.getOrDefault(callerKey, List.of())) {
            byOrder.put(site.callOrder(), site);
            if (!chainedHttpCall(target, site, byOrder, results)) {
                resolveCall(target, site, results);
            }
        }
        if (layers.isSpringData(target.type().fqn())) {
            attachRepositoryQuery(target);
        }
    }

    // ---------------- lời gọi ----------------

    /** {@code Enum.CONSTANT}: khóa thông báo của lệnh {@code throw Enum.CONSTANT.exception()} (xem CallSiteExtractor). */
    private static final java.util.regex.Pattern CONSTANT_REF = java.util.regex.Pattern.compile("[A-Z][A-Za-z0-9_]*\\.[A-Z][A-Z0-9_]+");
    private static final Set<String> OPTIONAL_UNWRAP = Set.of("get", "orElseThrow", "orElse", "orElseGet");

    /**
     * @param results kiểu kết quả của các lời gọi đã xử lý trong method này (theo {@code callOrder}), để lời gọi nối chuỗi
     *     {@code a.b().c()} và biến {@code var x = a.b()} biết kiểu của receiver
     */
    private void resolveCall(Target caller, CallSiteFact site, Map<Integer, TypeRef> results) {
        Receiver receiver = receiverOf(caller, site, results);
        if (receiver == null) {
            return;
        }
        if (receiver.projectType() == null) {
            if (restTemplateCall(caller, site, receiver)) {
                return;
            }
            if (OPTIONAL_UNWRAP.contains(site.methodName())
                    && TypeNames.simple(receiver.externalName()).equals("Optional")
                    && receiver.declared() != null) {
                TypeNames.typeArguments(receiver.declared().text()).stream()
                        .findFirst()
                        .ifPresent(arg -> results.put(site.callOrder(), new TypeRef(arg, receiver.declared().file())));
            }
            sinkKind(receiver.externalName(), site.methodName(), caller)
                    .ifPresent(sink -> attachSink(caller, site, sink, TypeNames.simple(receiver.externalName()) + "." + site.methodName()));
            return;
        }
        TypeInfo declared = index.typeInfo(receiver.projectType()).orElseThrow();
        if (declared.hasAnnotation(MappingExtractor.FEIGN_CLIENT)) {
            feignCall(caller, site, declared);
            return;
        }
        boolean springData = layers.isSpringData(declared.fqn());
        ImplementationResolver.Choice choice = null;
        String targetType = declared.fqn();
        if (!receiver.fixedType() && !springData && declared.isAbstract()) {
            choice = implementations.choose(declared.fqn(), receiver.fieldName(), receiver.qualifier());
            if (choice.target() != null) {
                targetType = choice.target();
            }
        }
        List<MethodMatch> matches = index.findMethods(targetType, site.methodName(), site.argCount());
        if (matches.isEmpty() && !targetType.equals(declared.fqn())) {
            matches = index.findMethods(declared.fqn(), site.methodName(), site.argCount());
        }
        List<Target> targets = matches.stream().map(m -> Target.of(m.declaringType(), m.method())).toList();
        if (targets.isEmpty() && springData) {
            targets = inheritedCrud(declared, site).map(List::of).orElse(List.of());
        }
        SourceLocation location = SourceLocation.of(caller.type().file().relativePath(), site.line());
        String from = caller.key();
        if (!targets.isEmpty() && isAccessorOfPlainClass(targets.getFirst())) {
            skippedCalls++; // getter / setter của entity, DTO, tiện ích: không phải bước xử lý
            return;
        }
        if (targets.isEmpty()) {
            if (isBusinessLayer(declared)) {
                unresolvedMethod(from, declared, site, location);
            } else {
                skippedCalls++; // entity / DTO / tiện ích: thường là getter, setter do Lombok sinh
            }
            return;
        }
        if (targets.size() > 1) {
            unresolvedCalls++;
            var candidates = targets.stream().map(t -> new Candidate(enqueue(t), 0.5, "Hàm " + t.name() + "(" + String.join(", ", t.paramTypes()) + ")")).toList();
            edges.add(new IrEdge(
                    from, null, EdgeKind.CALLS, Confidence.UNRESOLVED,
                    targets.size() + " hàm cùng tên " + site.methodName() + " và cùng số tham số; phân tích tĩnh chưa biết hàm nào được gọi",
                    site.callOrder(), site.guard(), candidates, location));
            return;
        }
        Target target = targets.getFirst();
        rememberResult(target, results, site);
        String to = enqueue(target);
        if (choice == null || choice.candidates().isEmpty() && choice.confidence() == Confidence.CERTAIN) {
            edges.add(new IrEdge(
                    from, to, EdgeKind.CALLS, Confidence.CERTAIN, choice == null ? null : choice.reason(),
                    site.callOrder(), site.guard(), List.of(), location));
            return;
        }
        List<Candidate> candidates = candidatesOf(choice, site);
        if (choice.confidence() == Confidence.UNRESOLVED) {
            unresolvedCalls++;
        }
        edges.add(new IrEdge(
                from, to, EdgeKind.CALLS, choice.confidence(), choice.reason(), site.callOrder(), site.guard(), candidates, location));
    }

    /**
     * Getter / setter của lớp không thuộc Controller / Service / Repository. Hàm tên {@code getX()} của một Service hay
     * Repository là nghiệp vụ thật (vd {@code getReaderOptions()}) nên KHÔNG bị bỏ qua.
     */
    private boolean isAccessorOfPlainClass(Target target) {
        return layers.classify(target.type().fqn()).layer() == LayerClassifier.Layer.OTHER
                && ACCESSOR.matcher(target.name()).matches();
    }

    private static final java.util.regex.Pattern ACCESSOR = java.util.regex.Pattern.compile("^(get|set)[A-Z0-9_].*");

    private boolean isBusinessLayer(TypeInfo type) {
        LayerClassifier.Layer layer = layers.classify(type.fqn()).layer();
        return layer != LayerClassifier.Layer.OTHER;
    }

    /**
     * Gọi vào Controller / Service / Repository của dự án nhưng không thấy hàm đích (vd hàm kế thừa từ thư viện): cạnh
     * {@code UNRESOLVED} không có ứng viên tới node giữ chỗ, để diagram vẫn hiện bước này là "chưa xác định".
     */
    private void unresolvedMethod(String from, TypeInfo declared, CallSiteFact site, SourceLocation location) {
        Target placeholder = Target.placeholder(declared, site.methodName(), site.argCount());
        methodNodes.putIfAbsent(placeholder.key(), methodNode(placeholder));
        unresolvedCalls++;
        edges.add(new IrEdge(
                from,
                placeholder.key(),
                EdgeKind.CALLS,
                Confidence.UNRESOLVED,
                "Không tìm thấy hàm " + site.methodName() + " trong " + TypeNames.simple(declared.fqn())
                        + " và các lớp cha trong mã nguồn (có thể kế thừa từ thư viện không có mã nguồn)",
                site.callOrder(),
                site.guard(),
                List.of(),
                location));
    }

    /** Ghi kiểu trả về khai báo của hàm đích làm kiểu kết quả của lời gọi (cho lời gọi nối chuỗi / biến var). */
    private void rememberResult(Target target, Map<Integer, TypeRef> results, CallSiteFact site) {
        String returnType = target.fact() != null ? target.fact().returnType() : inheritedReturnType(target);
        if (returnType != null && !returnType.equals("void")) {
            results.put(site.callOrder(), new TypeRef(returnType, target.type().file()));
        }
    }

    /** Ứng viên của lời gọi qua interface: method cùng tên của từng lớp hiện thực. */
    private List<Candidate> candidatesOf(ImplementationResolver.Choice choice, CallSiteFact site) {
        var result = new ArrayList<Candidate>();
        for (ImplementationResolver.Candidate c : choice.candidates()) {
            List<MethodMatch> found = index.findMethods(c.type(), site.methodName(), site.argCount());
            if (found.size() == 1) {
                result.add(new Candidate(enqueue(Target.of(found.getFirst().declaringType(), found.getFirst().method())), c.score(), c.reason()));
            }
        }
        return result;
    }

    private Optional<Target> inheritedCrud(TypeInfo repo, CallSiteFact site) {
        int count = site.argCount();
        if (count < 0) {
            for (int i = 0; i <= 2; i++) {
                Optional<Target> t = inheritedCrud(repo, site.methodName(), i);
                if (t.isPresent()) {
                    return t;
                }
            }
            return Optional.empty();
        }
        return inheritedCrud(repo, site.methodName(), count);
    }

    private Optional<Target> inheritedCrud(TypeInfo repo, String name, int argCount) {
        return SpringDataMethods.find(name, argCount).map(c -> new Target(repo, name, c.paramTypes(), c.paramNames(), null, false));
    }

    /** Kiểu trả về của method kế thừa từ Spring Data, theo entity của repository; null nếu có nhiều khả năng. */
    private String inheritedReturnType(Target target) {
        String entity = dataAccess.entityOf(target.type()).map(EntityTableResolver.Table::entity).orElse(null);
        return SpringDataMethods.find(target.name(), target.paramTypes().size()).map(c -> c.returnType(entity)).orElse(null);
    }

    private Receiver receiverOf(Target caller, CallSiteFact site, Map<Integer, TypeRef> results) {
        String callerFqn = caller.type().fqn();
        JavaFileFacts file = caller.type().file();
        String expr = site.receiverExpr();
        // Tên dùng để so khớp tên bean khi chọn lớp hiện thực: chỉ có nghĩa với biến / field thật
        String beanName = expr.startsWith("@") ? null : expr;
        if (expr.equals("this")) {
            return Receiver.project(callerFqn, null, null, true, null);
        }
        if (expr.equals("super")) {
            return index.directSupertypes(callerFqn).stream()
                    .findFirst()
                    .map(s -> Receiver.project(s, null, null, true, null))
                    .orElse(null);
        }
        if (site.receiverCall() != null) {
            TypeRef previous = results.get(site.receiverCall());
            return previous == null ? null : fromTypeRef(previous, beanName);
        }
        if (site.receiverType() != null) {
            return index.resolveSimpleName(site.receiverType(), file)
                    .map(t -> Receiver.project(t, beanName, null, false, null))
                    .orElseGet(() -> Receiver.external(index.qualifiedOrSimple(site.receiverType(), file), beanName, false, null));
        }
        Optional<Injection> injection = injections.find(callerFqn, expr);
        if (injection.isPresent()) {
            Injection i = injection.get();
            TypeRef declared = new TypeRef(i.field().type(), i.owner().file());
            return i.type() != null
                    ? Receiver.project(i.type(), i.field().name(), i.qualifier(), false, declared)
                    : Receiver.external(index.qualifiedOrSimple(i.typeName(), i.owner().file()), expr, false, declared);
        }
        if (Character.isUpperCase(expr.charAt(0))) {
            return index.resolveSimpleName(expr, file)
                    .map(t -> Receiver.project(t, null, null, true, null))
                    .orElseGet(() -> Receiver.external(index.qualifiedOrSimple(expr, file), null, true, null));
        }
        return null;
    }

    private Receiver fromTypeRef(TypeRef type, String beanName) {
        String raw = TypeNames.stripGenerics(type.text());
        return index.resolveSimpleName(raw, type.file())
                .map(t -> Receiver.project(t, beanName, null, false, type))
                .orElseGet(() -> Receiver.external(index.qualifiedOrSimple(raw, type.file()), beanName, false, type));
    }

    // ---------------- lời gọi HTTP sang service khác ----------------

    /** Phương thức HTTP của từng hàm {@code RestTemplate} có đối số đầu là URL ({@code exchange}/{@code execute}: theo đối số). */
    private static final Map<String, String> REST_TEMPLATE_METHODS = Map.ofEntries(
            Map.entry("getForObject", "GET"),
            Map.entry("getForEntity", "GET"),
            Map.entry("headForHeaders", "HEAD"),
            Map.entry("optionsForAllow", "OPTIONS"),
            Map.entry("postForObject", "POST"),
            Map.entry("postForEntity", "POST"),
            Map.entry("postForLocation", "POST"),
            Map.entry("put", "PUT"),
            Map.entry("patchForObject", "PATCH"),
            Map.entry("delete", "DELETE"),
            Map.entry("exchange", ""),
            Map.entry("execute", ""));
    private static final Set<String> REST_TEMPLATE_TYPES = Set.of("RestTemplate", "RestOperations", "TestRestTemplate");
    private static final Set<String> FLUENT_CLIENT_TYPES = Set.of("WebClient", "RestClient");
    private static final Set<String> FLUENT_METHODS = Set.of("get", "post", "put", "patch", "delete", "head", "options");
    private static final java.util.regex.Pattern CONST_MARKER =
            java.util.regex.Pattern.compile("\\{" + java.util.regex.Pattern.quote(JavaFileFacts.CONSTANT_REF_PREFIX) + "([^}]+)}");

    /** {@code restTemplate.getForObject(url, …)}: lời gọi HTTP; trả true nếu đã xử lý. */
    private boolean restTemplateCall(Target caller, CallSiteFact site, Receiver receiver) {
        String method = REST_TEMPLATE_METHODS.get(site.methodName());
        if (method == null || site.http() == null || !REST_TEMPLATE_TYPES.contains(TypeNames.simple(receiver.externalName()))) {
            return false;
        }
        String httpMethod = method.isEmpty() ? site.http().httpMethod() : method;
        addHttpCall(caller, site, httpMethod, site.http().urlTemplate(), site.http().urlText(), null);
        return true;
    }

    /**
     * {@code webClient.get().uri(url)} / {@code restClient.method(HttpMethod.POST).uri(url)}: lời gọi {@code uri} có receiver
     * là kết quả của {@code get()} / {@code method(…)} trên một {@code WebClient} / {@code RestClient}.
     *
     * @return true nếu đã xử lý
     */
    private boolean chainedHttpCall(Target caller, CallSiteFact site, Map<Integer, CallSiteFact> byOrder, Map<Integer, TypeRef> results) {
        if (!site.methodName().equals("uri") || site.receiverCall() == null || site.http() == null) {
            return false;
        }
        CallSiteFact verb = byOrder.get(site.receiverCall());
        if (verb == null || !FLUENT_METHODS.contains(verb.methodName()) && !verb.methodName().equals("method")) {
            return false;
        }
        Receiver client = receiverOf(caller, verb, results);
        if (client == null || client.projectType() != null || !FLUENT_CLIENT_TYPES.contains(TypeNames.simple(client.externalName()))) {
            return false;
        }
        String httpMethod = verb.methodName().equals("method")
                ? verb.http() == null ? null : verb.http().httpMethod()
                : verb.methodName().toUpperCase(java.util.Locale.ROOT);
        addHttpCall(caller, site, httpMethod, site.http().urlTemplate(), site.http().urlText(), null);
        return true;
    }

    /** Gọi hàm của interface {@code @FeignClient}: URL = path của client + mapping của interface + mapping của hàm. */
    private void feignCall(Target caller, CallSiteFact site, TypeInfo client) {
        List<MethodMatch> matches = index.findMethods(client.fqn(), site.methodName(), site.argCount());
        if (matches.size() != 1) {
            skippedCalls++;
            return;
        }
        MethodFact method = matches.getFirst().method();
        TypeInfo owner = matches.getFirst().declaringType();
        JavaFileFacts.MappingFact mapping = owner.file().mappings().stream()
                .filter(m -> owner.fqn().equals(m.declaringType()) && method.signature().equals(m.method()))
                .findFirst()
                .orElse(null);
        if (mapping == null) {
            skippedCalls++; // hàm default / không có mapping: không phải lời gọi HTTP
            return;
        }
        String classPath = owner.file().mappings().stream()
                .filter(m -> owner.fqn().equals(m.declaringType()) && m.method() == null)
                .flatMap(m -> m.paths().stream())
                .findFirst()
                .orElse("");
        Map<String, String> values = client.type().annotationValues();
        String base = feignValue(values, "path", client);
        String name = feignValue(values, "name", client);
        if (name.isEmpty()) {
            name = feignValue(values, "value", client);
        }
        String url = feignValue(values, "url", client);
        String host = !url.isEmpty() && !url.contains("${") ? url.replaceFirst("^(?i)https?://", "").replaceFirst("/.*$", "") : name;
        String template = joinPath(base, constantOrLiteral(classPath, owner), constantOrLiteral(mapping.paths().isEmpty() ? "" : mapping.paths().getFirst(), owner));
        String httpMethod = mapping.httpMethods().isEmpty() ? null : mapping.httpMethods().getFirst();
        // đường dẫn của Feign là hằng trong annotation: hiện như một chuỗi hằng
        addHttpCall(caller, site, httpMethod, template, "\"" + template + "\"", host.isEmpty() ? null : host);
    }

    private String feignValue(Map<String, String> values, String attr, TypeInfo client) {
        String raw = values.get(MappingExtractor.FEIGN_CLIENT + "." + attr);
        return raw == null ? "" : constants.resolve(raw, client.file(), client.fqn()).orElse("").strip();
    }

    private String constantOrLiteral(String path, TypeInfo owner) {
        return path.startsWith(JavaFileFacts.CONSTANT_REF_PREFIX)
                ? constants.resolve(path, owner.file(), owner.fqn()).orElse("{" + path.substring(path.lastIndexOf('.') + 1) + "}")
                : path;
    }

    private static String joinPath(String... parts) {
        var out = new StringBuilder();
        for (String p : parts) {
            String v = p == null ? "" : p.strip();
            if (v.isEmpty()) {
                continue;
            }
            out.append(v.startsWith("/") ? "" : "/").append(v.endsWith("/") ? v.substring(0, v.length() - 1) : v);
        }
        return out.toString();
    }

    /**
     * Node {@code HTTP_CALL} ({@code origin = BE}) + cạnh {@code SENDS}. URL: thay {@code {@const:TÊN}} bằng giá trị hằng
     * số (không có thì giữ {@code {TÊN}} và ghi lý do), tách host của URL tuyệt đối, bỏ query string.
     *
     * @param host tên service / host đã biết (Feign); null thì lấy từ URL tuyệt đối nếu có
     */
    private void addHttpCall(Target caller, CallSiteFact site, String httpMethod, String template, String urlText, String host) {
        var issues = new java.util.LinkedHashSet<String>();
        String raw = template == null ? "{?}" : template;
        var matcher = CONST_MARKER.matcher(raw);
        var resolved = new StringBuilder();
        while (matcher.find()) {
            String ref = matcher.group(1);
            String value = constants.resolve(JavaFileFacts.CONSTANT_REF_PREFIX + ref, caller.type().file(), caller.type().fqn()).orElse(null);
            if (value == null) {
                issues.add("Không tìm thấy giá trị của " + ref);
                value = "{" + ref.substring(ref.lastIndexOf('.') + 1) + "}";
            }
            matcher.appendReplacement(resolved, java.util.regex.Matcher.quoteReplacement(value));
        }
        matcher.appendTail(resolved);
        String url = resolved.toString().strip();
        if (url.contains("{?}")) {
            issues.add("Biểu thức URL không tính được: " + urlText);
        }
        var absolute = java.util.regex.Pattern.compile("^(?i)(?:https?:)?//([^/?#{]+)(.*)$").matcher(url);
        if (absolute.matches()) {
            host = host != null ? host : absolute.group(1);
            url = absolute.group(2);
        }
        url = url.replaceFirst("[?#].*$", "").replaceAll("/{2,}", "/");
        if (!url.isEmpty() && !url.startsWith("/") && !url.startsWith("{")) {
            url = "/" + url;
        }
        boolean onlyUnknown = url.replace("{?}", "").replace("/", "").isEmpty();
        String methodKey = caller.key();
        int index = httpCounters.merge(methodKey, 1, Integer::sum) - 1;
        String key = NodeKeys.javaHttpCall(methodKey, index);
        String method = httpMethod == null ? "GET" : httpMethod;
        if (httpMethod == null) {
            issues.add("Không rõ phương thức HTTP, coi là GET");
        }
        var attrs = new TreeMap<String, Object>();
        attrs.put("httpMethod", method);
        attrs.put("urlExpr", urlText == null ? url : urlText);
        if (!onlyUnknown) {
            attrs.put("urlPattern", url);
        }
        attrs.put("urlResolved", issues.isEmpty() && !onlyUnknown);
        if (!issues.isEmpty()) {
            attrs.put("urlIssue", String.join("; ", issues));
        }
        if (host != null && !host.isBlank()) {
            attrs.put("urlHost", host);
        }
        attrs.put("origin", "BE");
        SourceLocation location = SourceLocation.of(caller.type().file().relativePath(), site.line());
        httpNodes.put(key, new IrNode(key, NodeKind.HTTP_CALL, method + " " + (onlyUnknown ? attrs.get("urlExpr") : url), null, location, attrs));
        edges.add(new IrEdge(caller.key(), key, EdgeKind.SENDS, Confidence.CERTAIN, null, site.callOrder(), site.guard(), List.of(), location));
    }

    // ---------------- truy vấn dữ liệu ----------------

    /** Tên kiểu thư viện trông như lớp truy cập dữ liệu. */
    private static final java.util.regex.Pattern DATA_ACCESS_TYPE =
            java.util.regex.Pattern.compile("(?i)(repository|dao|template|jdbc|session|executor)");

    private Optional<SinkKind> sinkKind(String qualifiedType, String method, Target caller) {
        if (sinkRules.isQuerySink(qualifiedType + "#" + method)) {
            return Optional.of(SinkKind.LIB_CALL);
        }
        Optional<SinkKind> known = knownSink(qualifiedType, method);
        if (known.isPresent()) {
            return known;
        }
        boolean fromRepository = layers.classify(caller.type().fqn()).layer() == LayerClassifier.Layer.REPOSITORY;
        return fromRepository && DATA_ACCESS_TYPE.matcher(TypeNames.simple(qualifiedType)).find()
                ? Optional.of(SinkKind.GUESSED)
                : Optional.empty();
    }

    private Optional<SinkKind> knownSink(String qualifiedType, String method) {
        return switch (TypeNames.simple(qualifiedType)) {
            case "EntityManager" -> switch (method) {
                case "createQuery" -> Optional.of(SinkKind.JPQL);
                case "createNativeQuery" -> Optional.of(SinkKind.NATIVE);
                default -> Optional.empty();
            };
            case "JdbcTemplate", "NamedParameterJdbcTemplate", "JdbcOperations", "NamedParameterJdbcOperations" ->
                Set.of("query", "queryForObject", "queryForList", "queryForMap", "queryForRowSet", "queryForStream", "update", "batchUpdate", "execute")
                                .contains(method)
                        ? Optional.of(SinkKind.NATIVE)
                        : Optional.empty();
            case "JdbcClient" -> method.equals("sql") ? Optional.of(SinkKind.NATIVE) : Optional.empty();
            default -> Optional.empty();
        };
    }

    private void attachSink(Target caller, CallSiteFact site, SinkKind sink, String sinkName) {
        String methodKey = caller.key();
        int order = sinkCounters.merge(methodKey, 1, Integer::sum) - 1;
        List<StringFragmentFact> fragments = DataAccessResolver.inOrder(queryFragmentsOf(caller));
        QueryInfo info = dataAccess.sinkQuery(sink, fragments, sinkName);
        addQuery(methodKey, order, info, site.callOrder(), SourceLocation.of(caller.type().file().relativePath(), site.line()), site.guard());
    }

    /**
     * Mảnh chuỗi truy vấn của hàm gọi điểm thực thi: của chính hàm, của các hàm cùng lớp mà nó gọi (thường là hàm dựng câu
     * truy vấn như {@code buildSelect()}), và hằng chuỗi của lớp chỉ chứa mệnh đề (WHERE / ORDER BY…). Hằng chuỗi là cả một
     * câu lệnh chỉ dùng khi hàm không có mảnh nào và lớp chỉ có đúng một hằng như vậy — nếu không không biết hằng nào là của hàm.
     */
    private List<StringFragmentFact> queryFragmentsOf(Target caller) {
        var result = new ArrayList<StringFragmentFact>();
        var visited = new HashSet<String>();
        var pending = new ArrayDeque<String>();
        pending.add(caller.type().fqn() + "#" + caller.fact().signature());
        while (!pending.isEmpty()) {
            String method = pending.poll();
            if (!visited.add(method)) {
                continue;
            }
            result.addAll(fragmentsByMethod.getOrDefault(method, List.of()));
            for (CallSiteFact call : callsByMethod.getOrDefault(method, List.of())) {
                if (call.receiverExpr().equals("this")) {
                    index.findMethods(caller.type().fqn(), call.methodName(), call.argCount())
                            .forEach(m -> pending.add(m.declaringType().fqn() + "#" + m.method().signature()));
                }
            }
        }
        var constants = caller.type().fields().stream()
                .filter(f -> f.constantValue() != null)
                .map(f -> new StringFragmentFact(caller.key(), f.constantValue(), false, f.line()))
                .filter(f -> QueryTextHints.looksLikeQuery(f.text()))
                .toList();
        constants.stream().filter(c -> !DataAccessResolver.isStatement(c.text())).forEach(result::add);
        var statements = constants.stream().filter(c -> DataAccessResolver.isStatement(c.text())).toList();
        if (result.isEmpty() && statements.size() == 1) {
            result.addAll(statements);
        }
        return result;
    }

    private void attachRepositoryQuery(Target repoMethod) {
        dataAccess
                .repositoryQuery(repoMethod.type(), repoMethod.name(), repoMethod.paramTypes().size(), repoMethod.fact())
                .ifPresent(info -> addQuery(repoMethod.key(), 0, info, 0, repoMethod.location(), null));
    }

    private void addQuery(String methodKey, int index, QueryInfo info, int order, SourceLocation location, Guard guard) {
        String key = NodeKeys.javaQuery(methodKey, index);
        List<String> tableNames = info.tables().stream().map(TableRef::name).toList();
        String verb = info.verb() == null ? "QUERY" : info.verb();
        Map<String, Object> attrs = new TreeMap<>();
        attrs.put("queryKind", info.kind());
        attrs.put("verb", verb);
        if (info.operation() != null) {
            attrs.put("operation", info.operation());
        }
        if (info.text() != null) {
            attrs.put("text", info.text());
        }
        attrs.put("tables", tableNames);
        queryNodes.put(key, new IrNode(key, NodeKind.DATA_QUERY, tableNames.isEmpty() ? verb : verb + " " + String.join(", ", tableNames), null, location, attrs));
        edges.add(new IrEdge(methodKey, key, EdgeKind.EXECUTES, info.confidence(), info.reason(), order, guard, List.of(), location));
        for (TableRef table : info.tables()) {
            String tableKey = ensureTable(table);
            boolean unknownOperation = info.operation() == null;
            edges.add(new IrEdge(
                    key,
                    tableKey,
                    "READ".equals(info.operation()) || unknownOperation ? EdgeKind.READS : EdgeKind.WRITES,
                    unknownOperation ? Confidence.INFERRED : table.confidence(),
                    unknownOperation ? "Không nhận ra câu lệnh là đọc hay ghi" : table.reason(),
                    null,
                    null,
                    List.of(),
                    location));
        }
    }

    private String ensureTable(TableRef table) {
        String key = NodeKeys.javaTable(table.name());
        Map<String, Object> attrs = new TreeMap<>();
        attrs.put("name", table.name());
        if (table.entity() != null) {
            attrs.put("entity", table.entity());
        } else {
            attrs.put("reason", "Suy ra từ văn bản truy vấn, không có entity tương ứng trong mã nguồn");
        }
        List<String> unique = uniqueColumns(table.entity());
        if (!unique.isEmpty()) {
            attrs.put("uniqueColumns", unique);
        }
        IrNode node = new IrNode(key, NodeKind.DB_TABLE, table.name(), null, table.location(), attrs);
        // Cùng một bảng có thể gặp từ nhiều truy vấn: giữ bản có entity (đủ thông tin hơn), độc lập với thứ tự gặp
        tableNodes.merge(key, node, (old, fresh) -> old.attrs().containsKey("entity") ? old : fresh);
        return key;
    }

    private static final java.util.regex.Pattern COLUMN_NAME = java.util.regex.Pattern.compile("name=([^,)]+)");

    /** Tên cột có {@code @Column(unique = true)} trong entity (tên cột theo {@code name} hoặc tên trường đổi sang snake_case). */
    private List<String> uniqueColumns(String entitySimpleName) {
        if (entitySimpleName == null) {
            return List.of();
        }
        var columns = new java.util.TreeSet<String>();
        for (TypeInfo info : index.allTypes()) {
            if (!TypeNames.simple(info.fqn()).equals(entitySimpleName) || !EntityTableResolver.isEntity(info)) {
                continue;
            }
            for (JavaFileFacts.DtoFieldFact f : dtoFieldsByType.getOrDefault(info.fqn(), List.of())) {
                for (String c : f.constraints()) {
                    if (c.startsWith("Column(") && c.contains("unique=true")) {
                        var m = COLUMN_NAME.matcher(c);
                        columns.add(m.find() ? m.group(1).strip() : EntityTableResolver.snakeCase(f.name()));
                    }
                }
            }
        }
        return List.copyOf(columns);
    }

    // ---------------- node method ----------------

    private IrNode methodNode(Target target) {
        TypeInfo type = target.type();
        LayerClassifier.Classification layer = layers.classify(type.fqn());
        BusinessNames.Name business = names.of(type.fqn());
        MethodFact fact = target.fact();
        Map<String, Object> attrs = new TreeMap<>();
        attrs.put("layer", layer.layer().name());
        attrs.put("layerBasis", layer.basis().name());
        attrs.put("className", type.fqn());
        attrs.put("signature", target.name() + "(" + String.join(",", target.paramTypes()) + ")");
        if (!target.paramNames().isEmpty()) {
            attrs.put("paramNames", target.paramNames());
        }
        if (EntityTableResolver.isEntity(type) || type.hasAnnotation("Embeddable") || type.hasAnnotation("MappedSuperclass")) {
            attrs.put("domainModel", true);
        }
        attrs.put("businessName", business.text());
        attrs.put("businessNameSource", business.source().name());
        if (target.placeholder()) {
            attrs.put("unresolvedMethod", true);
            attrs.put("abstract", true);
        } else if (fact == null) {
            attrs.put("synthetic", true);
            attrs.put("inheritedFrom", "Spring Data");
            String returnType = inheritedReturnType(target);
            if (returnType != null) {
                attrs.put("returnType", returnType);
            }
        } else {
            if (fact.returnType() != null) {
                attrs.put("returnType", fact.returnType());
            }
            if (fact.annotations().contains("private")) {
                attrs.put("private", true);
            }
            if (fact.annotations().contains("static")) {
                attrs.put("static", true);
            }
            if (!fact.hasBody()) {
                attrs.put("abstract", true);
            }
            if (fact.annotations().contains("Transactional") || type.hasAnnotation("Transactional")) {
                attrs.put("transactional", true);
            }
            String security = fact.annotationValues().containsKey("PreAuthorize.value")
                    ? fact.annotationValues().get("PreAuthorize.value")
                    : type.type().annotationValues().get("PreAuthorize.value");
            if (security != null && !security.isBlank() && !security.startsWith(JavaFileFacts.CONSTANT_REF_PREFIX)) {
                attrs.put("preAuthorize", security);
            }
            List<Map<String, Object>> thrown = throwsByMethod
                    .getOrDefault(type.fqn() + "#" + fact.signature(), List.of())
                    .stream()
                    .map(this::throwAttrs)
                    .toList();
            if (!thrown.isEmpty()) {
                attrs.put("throws", thrown);
            }
        }
        return new IrNode(target.key(), NodeKind.BE_METHOD, TypeNames.simple(type.fqn()) + "." + target.name(), null, target.location(), attrs);
    }

    private Map<String, Object> throwAttrs(ThrowFact t) {
        Map<String, Object> m = new TreeMap<>();
        m.put("order", t.order());
        m.put("exception", t.exceptionType());
        if (t.messageKey() != null) {
            String key = t.messageKey();
            String text;
            int dot = key.indexOf('.');
            if (dot > 0 && CONSTANT_REF.matcher(key).matches()) {
                // AppError.NOT_FOUND → khóa thông báo khai báo trong enum → câu hiển thị; nhãn là tên hằng
                String constant = key.substring(dot + 1);
                text = enumMessage(key.substring(0, dot), constant);
                key = constant;
            } else {
                text = messages.get(key);
            }
            m.put("messageKey", key);
            if (text != null) {
                m.put("message", text);
            }
        }
        if (t.guard() != null) {
            m.put("guardType", t.guard().type().name());
            m.put("guardText", t.guard().text());
            if (t.guard().branch() != null) {
                m.put("guardBranch", t.guard().branch());
            }
        }
        m.put("line", t.line());
        return m;
    }

    /** Câu hiển thị của hằng enum mã lỗi: đối số chuỗi đầu tiên của hằng là khóa trong file thông báo. */
    private String enumMessage(String enumSimpleName, String constant) {
        return index.allTypes().stream()
                .filter(t -> t.type().kind() == JavaFileFacts.TypeFact.Kind.ENUM && TypeNames.simple(t.fqn()).equals(enumSimpleName))
                .map(t -> t.type().enumMessageKeys().get(constant))
                .filter(Objects::nonNull)
                .map(messages::get)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private List<Diagnostic> diagnostics() {
        var result = new ArrayList<Diagnostic>();
        if (skippedCalls > 0) {
            result.add(new Diagnostic(
                    Diagnostic.Severity.INFO, null, null,
                    skippedCalls + " lời gọi tới kiểu trong dự án không tìm thấy hàm đích (thường là getter/setter do Lombok sinh "
                            + "hoặc hàm kế thừa từ thư viện); đã bỏ qua, không tạo cạnh"));
        }
        if (unresolvedCalls > 0) {
            result.add(new Diagnostic(
                    Diagnostic.Severity.INFO, null, null,
                    unresolvedCalls + " lời gọi chưa xác định được đích (cạnh UNRESOLVED, kèm danh sách ứng viên để chốt tay)"));
        }
        return result;
    }
}
