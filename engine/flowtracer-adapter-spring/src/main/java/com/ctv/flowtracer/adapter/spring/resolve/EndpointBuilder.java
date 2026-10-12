package com.ctv.flowtracer.adapter.spring.resolve;

import com.ctv.flowtracer.adapter.spring.config.ApplicationConfigReader;
import com.ctv.flowtracer.adapter.spring.config.PropertyLookup;
import com.ctv.flowtracer.adapter.spring.extract.MappingExtractor;
import com.ctv.flowtracer.adapter.spring.facts.JavaFileFacts;
import com.ctv.flowtracer.adapter.spring.facts.JavaFileFacts.MappingFact;
import com.ctv.flowtracer.adapter.spring.facts.JavaFileFacts.MethodFact;
import com.ctv.flowtracer.adapter.spring.facts.JavaFileFacts.ParamFact;
import com.ctv.flowtracer.ir.Diagnostic;
import com.ctv.flowtracer.ir.IrNode;
import com.ctv.flowtracer.ir.NodeKeys;
import com.ctv.flowtracer.ir.NodeKind;
import com.ctv.flowtracer.ir.SourceLocation;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * API_ENDPOINT: ghép context-path + đường dẫn cấp class + cấp method; giải đường dẫn khai báo bằng hằng số; lấy mô tả
 * từ {@code @Operation} rồi Javadoc; xếp module theo {@link ModuleNameResolver}.
 *
 * <p>Mọi thuộc tính đều đọc trực tiếp từ mã nguồn; thứ gì không xác định được thì ghi rõ trong {@code attrs} và
 * {@link Diagnostic}, không đoán.
 */
public final class EndpointBuilder {

    /** {@code httpMethod} khi {@code @RequestMapping} không khai {@code method}: nhận mọi phương thức. */
    public static final String ANY_METHOD = "ANY";

    private static final Pattern PATH_VARIABLE_REGEX = Pattern.compile("\\{([^}:]+):[^}]*}");
    private static final Pattern RESPONSE_ENTITY = Pattern.compile("^(?:[\\w.]*\\.)?ResponseEntity<(.+)>$");

    private final ModuleNameResolver modules = new ModuleNameResolver();

    /** Kết quả dựng endpoint của một Source. */
    public record Result(List<IrNode> endpoints, List<Diagnostic> diagnostics) {}

    public Result build(List<JavaFileFacts> facts, ApplicationConfigReader.Result config, PropertyLookup properties) {
        var constants = constants(facts);
        var controllers = new TreeMap<String, JavaFileFacts>();
        var classMappings = new TreeMap<String, MappingFact>();
        var methods = new LinkedHashMap<String, MethodFact>();
        for (JavaFileFacts file : facts) {
            file.types().stream()
                    .filter(t -> t.annotations().stream().anyMatch(MappingExtractor.CONTROLLER_ANNOTATIONS::contains))
                    .forEach(t -> controllers.put(t.fqn(), file));
            file.mappings().stream().filter(m -> m.method() == null).forEach(m -> classMappings.put(m.declaringType(), m));
            file.methods().forEach(m -> methods.put(m.declaringType() + "#" + signature(m), m));
        }

        var nodes = new TreeMap<String, IrNode>();
        var diagnostics = new ArrayList<Diagnostic>();
        for (JavaFileFacts file : facts.stream().sorted(Comparator.comparing(JavaFileFacts::relativePath)).toList()) {
            for (MappingFact mapping : file.mappings()) {
                if (mapping.method() == null || !controllers.containsKey(mapping.declaringType())) {
                    continue;
                }
                MappingFact classMapping = classMappings.get(mapping.declaringType());
                MethodFact handler = methods.get(mapping.declaringType() + "#" + mapping.method());
                String contextPath = config.forFile(file.relativePath())
                        .map(ApplicationConfigReader.AppConfig::contextPath)
                        .orElse("");
                for (IrNode node : endpointsOf(file, mapping, classMapping, handler, contextPath, constants, properties, diagnostics)) {
                    IrNode existing = nodes.putIfAbsent(node.key(), node);
                    if (existing != null) {
                        diagnostics.add(new Diagnostic(
                                Diagnostic.Severity.WARNING,
                                file.relativePath(),
                                mapping.line(),
                                "Trùng endpoint " + node.name() + " với " + existing.attrs().get("handler")
                                        + "; chỉ giữ khai báo đầu tiên"));
                    }
                }
            }
        }
        return new Result(List.copyOf(nodes.values()), List.copyOf(diagnostics));
    }

    private List<IrNode> endpointsOf(
            JavaFileFacts file,
            MappingFact mapping,
            MappingFact classMapping,
            MethodFact handler,
            String contextPath,
            Map<String, String> constants,
            PropertyLookup properties,
            List<Diagnostic> diagnostics) {
        List<String> classPaths = classMapping == null || classMapping.paths().isEmpty() ? List.of("") : classMapping.paths();
        List<String> httpMethods = !mapping.httpMethods().isEmpty()
                ? mapping.httpMethods()
                : classMapping != null && !classMapping.httpMethods().isEmpty() ? classMapping.httpMethods() : List.of(ANY_METHOD);
        String tag = Optional.ofNullable(mapping.openApi().get("tag"))
                .orElse(classMapping == null ? null : classMapping.openApi().get("tag"));
        ModuleNameResolver.Module module = modules.resolve(tag, mapping.declaringType());

        var result = new ArrayList<IrNode>();
        for (String classPath : classPaths) {
            for (String methodPath : mapping.paths()) {
                PathText classPart = resolvePath(classPath, file, mapping.declaringType(), constants, properties);
                PathText methodPart = resolvePath(methodPath, file, mapping.declaringType(), constants, properties);
                PathText pathPart = classPart.then(methodPart);
                String path = normalizePath(contextPath + "/" + classPart.text() + "/" + methodPart.text());
                if (pathPart.issue() != null) {
                    String where = simpleName(mapping.declaringType()) + "#" + mapping.method();
                    diagnostics.add(new Diagnostic(
                            Diagnostic.Severity.WARNING,
                            file.relativePath(),
                            mapping.line(),
                            "Đường dẫn của " + where + " chưa giải được: " + pathPart.reason()
                                    + "; đường dẫn hiển thị giữ nguyên phần chưa giải được"));
                }
                for (String httpMethod : httpMethods) {
                    result.add(endpoint(file, mapping, handler, httpMethod, path, contextPath, pathPart, module));
                }
            }
        }
        return result;
    }

    private static IrNode endpoint(
            JavaFileFacts file,
            MappingFact mapping,
            MethodFact handler,
            String httpMethod,
            String path,
            String contextPath,
            PathText pathPart,
            ModuleNameResolver.Module module) {
        Map<String, Object> attrs = new TreeMap<>();
        attrs.put("httpMethod", httpMethod);
        attrs.put("path", path);
        if (!contextPath.isEmpty()) {
            attrs.put("contextPath", contextPath);
        }
        attrs.put("pathResolved", pathPart.issue() == null);
        if (pathPart.issue() != null) {
            attrs.put("pathIssue", pathPart.issue().name());
            attrs.put("pathIssueReason", pathPart.reason());
        }
        if (!pathPart.sources().isEmpty()) {
            attrs.put("pathSources", pathPart.sources());
        }
        attrs.put("module", module.name());
        attrs.put("moduleSource", module.source().name());
        attrs.put("controller", mapping.declaringType());
        attrs.put("handler", simpleName(mapping.declaringType()) + "#" + mapping.method());
        attrs.put("params", mapping.params().stream()
                .filter(p -> p.source() != ParamFact.Source.OTHER)
                .map(EndpointBuilder::paramAttrs)
                .toList());
        mapping.params().stream()
                .filter(p -> p.source() == ParamFact.Source.BODY)
                .findFirst()
                .ifPresent(p -> attrs.put("requestType", p.type()));
        responseType(handler).ifPresent(t -> attrs.put("responseType", t));
        putIfPresent(attrs, "consumes", mapping.consumes());
        putIfPresent(attrs, "produces", mapping.produces());
        mapping.openApi().forEach((k, v) -> {
            if (!k.equals("tag")) {
                attrs.put(k, k.equals("deprecated") ? Boolean.valueOf(v) : v);
            }
        });
        if (module.source() == ModuleNameResolver.Source.TAG) {
            attrs.put("tag", module.name());
        }
        String summary = mapping.openApi().get("summary");
        String javadoc = handler == null ? null : firstSentence(handler.javadoc());
        if (summary != null) {
            attrs.put("summarySource", "OPERATION");
        } else if (javadoc != null) {
            attrs.put("summary", javadoc);
            attrs.put("summarySource", "JAVADOC");
        }
        SourceLocation location = handler != null
                ? new SourceLocation(file.relativePath(), handler.startLine(), handler.endLine())
                : SourceLocation.of(file.relativePath(), mapping.line());
        return new IrNode(
                NodeKeys.javaEndpoint(httpMethod, path),
                NodeKind.API_ENDPOINT,
                httpMethod + " " + path,
                summary != null ? summary : javadoc,
                location,
                attrs);
    }

    private static Map<String, Object> paramAttrs(ParamFact p) {
        Map<String, Object> m = new TreeMap<>();
        m.put("name", p.name());
        m.put("in", p.source().name());
        m.put("type", p.type());
        m.put("required", p.required());
        if (p.defaultValue() != null) {
            m.put("defaultValue", p.defaultValue());
        }
        return m;
    }

    /** Kiểu trả về đã bóc {@code ResponseEntity<T>}; {@code void} → không có. */
    static Optional<String> responseType(MethodFact handler) {
        if (handler == null || handler.returnType() == null || handler.returnType().equals("void")) {
            return Optional.empty();
        }
        var m = RESPONSE_ENTITY.matcher(handler.returnType().replace(" ", ""));
        return Optional.of(m.matches() ? m.group(1) : handler.returnType());
    }

    /** Lý do một đoạn đường dẫn chưa giải được. */
    enum PathIssue {
        /** Tham chiếu hằng số Java không tìm thấy giá trị. */
        CONSTANT,
        /** Thuộc tính cấu hình {@code ${khóa}} chưa giải được (lý do trong {@code reason}). */
        PROPERTY
    }

    /**
     * Một đoạn đường dẫn đã giải hằng số / thuộc tính.
     *
     * @param issue null nếu giải trọn vẹn
     * @param reason lý do bằng tiếng Việt khi chưa giải được
     * @param sources căn cứ các thuộc tính đã giải được
     */
    record PathText(String text, PathIssue issue, String reason, List<String> sources) {

        static PathText plain(String text) {
            return new PathText(text, null, null, List.of());
        }

        /** Gộp hai đoạn: lỗi đầu tiên thắng, căn cứ cộng dồn. */
        PathText then(PathText other) {
            var all = new ArrayList<>(sources);
            all.addAll(other.sources);
            return new PathText(
                    text + "/" + other.text,
                    issue != null ? issue : other.issue,
                    issue != null ? reason : other.reason,
                    List.copyOf(all));
        }
    }

    private static PathText resolvePath(
            String raw,
            JavaFileFacts file,
            String declaringType,
            Map<String, String> constants,
            PropertyLookup properties) {
        if (!raw.startsWith(JavaFileFacts.CONSTANT_REF_PREFIX)) {
            return withProperties(raw, file, properties);
        }
        String ref = raw.substring(JavaFileFacts.CONSTANT_REF_PREFIX.length());
        String value = constants.get(declaringType + "." + ref);
        if (value == null) {
            value = constants.get(ref);
        }
        if (value == null) {
            int dot = ref.lastIndexOf('.');
            String owner = dot < 0 ? "" : ref.substring(0, dot);
            value = importedConstant(file, owner, ref.substring(dot + 1), constants);
        }
        return value == null
                ? new PathText("${" + ref + "}", PathIssue.CONSTANT, "không tìm thấy giá trị của hằng số " + ref, List.of())
                : withProperties(value, file, properties);
    }

    /**
     * Thay thuộc tính cấu hình Spring ({@code ${khóa}}, {@code ${khóa:mặc định}}) trong đường dẫn theo thứ tự của Spring:
     * giá trị trong file cấu hình, rồi mặc định trong code. Không đoán: không giải được thì giữ nguyên {@code ${khóa}} kèm
     * lý do.
     */
    static PathText withProperties(String text, JavaFileFacts file, PropertyLookup properties) {
        if (!text.contains("${")) {
            return PathText.plain(text);
        }
        PropertyLookup.Resolution r = properties.resolve(text, file.relativePath());
        return r.resolved()
                ? new PathText(r.text(), null, null, r.sources())
                : new PathText(
                        r.text(), PathIssue.PROPERTY, PropertyLookup.describe(r.failure(), r.failedKey()), r.sources());
    }

    /** Tham chiếu {@code Lớp.HẰNG} với lớp được import hoặc cùng package. */
    private static String importedConstant(JavaFileFacts file, String owner, String name, Map<String, String> constants) {
        if (owner.isEmpty()) {
            return null;
        }
        for (String imp : file.imports()) {
            if (imp.endsWith("." + owner)) {
                String value = constants.get(imp + "." + name);
                if (value != null) {
                    return value;
                }
            }
        }
        return constants.get(file.packageName() + "." + owner + "." + name);
    }

    /**
     * Bảng hằng chuỗi: khóa {@code FQN.TÊN}, {@code TênNgắn.TÊN} (khi tên ngắn không trùng giữa các lớp). Chỉ giữ hằng
     * khởi tạo bằng literal.
     */
    static Map<String, String> constants(List<JavaFileFacts> facts) {
        var byFqn = new TreeMap<String, String>();
        var bySimple = new TreeMap<String, List<String>>();
        for (JavaFileFacts file : facts) {
            for (JavaFileFacts.FieldFact f : file.fields()) {
                if (f.constantValue() != null) {
                    byFqn.put(f.declaringType() + "." + f.name(), f.constantValue());
                    bySimple.computeIfAbsent(simpleName(f.declaringType()) + "." + f.name(), k -> new ArrayList<>())
                            .add(f.constantValue());
                }
            }
        }
        var result = new TreeMap<>(byFqn);
        bySimple.forEach((k, values) -> {
            if (values.size() == 1) {
                result.putIfAbsent(k, values.getFirst());
            }
        });
        return result;
    }

    /**
     * Chuẩn hóa đường dẫn: bắt đầu bằng '/', gộp '//' , bỏ '/' cuối, biến đường dẫn có regex {@code {id:\d+}} →
     * {@code {id}}.
     */
    static String normalizePath(String path) {
        String p = PATH_VARIABLE_REGEX.matcher(path).replaceAll("{$1}");
        p = p.replaceAll("/{2,}", "/");
        if (!p.startsWith("/")) {
            p = "/" + p;
        }
        if (p.length() > 1 && p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        return p;
    }

    /** Câu đầu tiên của Javadoc (tới dấu chấm đầu tiên theo sau khoảng trắng hoặc hết dòng). */
    static String firstSentence(String javadoc) {
        if (javadoc == null || javadoc.isBlank()) {
            return null;
        }
        String text = javadoc.strip().replaceAll("\\s+", " ");
        int end = text.indexOf(". ");
        return end < 0 ? text : text.substring(0, end + 1);
    }

    private static String signature(MethodFact m) {
        return m.name() + "(" + String.join(",", m.paramTypes()) + ")";
    }

    private static String simpleName(String fqn) {
        return fqn.substring(fqn.lastIndexOf('.') + 1);
    }

    private static void putIfPresent(Map<String, Object> attrs, String key, List<String> values) {
        if (!values.isEmpty()) {
            attrs.put(key, values);
        }
    }
}
