package com.ctv.flowtracer.adapter.spring.resolve;

import com.ctv.flowtracer.adapter.spring.config.ApplicationConfigReader;
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

    public Result build(List<JavaFileFacts> facts, ApplicationConfigReader.Result config) {
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
                for (IrNode node : endpointsOf(file, mapping, classMapping, handler, contextPath, constants, diagnostics)) {
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
                PathText classPart = resolvePath(classPath, file, mapping.declaringType(), constants);
                PathText methodPart = resolvePath(methodPath, file, mapping.declaringType(), constants);
                String path = normalizePath(contextPath + "/" + classPart.text() + "/" + methodPart.text());
                boolean resolved = classPart.resolved() && methodPart.resolved();
                if (!resolved) {
                    diagnostics.add(new Diagnostic(
                            Diagnostic.Severity.WARNING,
                            file.relativePath(),
                            mapping.line(),
                            "Không giải được hằng số trong đường dẫn của " + simpleName(mapping.declaringType()) + "#"
                                    + mapping.method() + "; đường dẫn hiển thị giữ nguyên tên hằng số"));
                }
                for (String httpMethod : httpMethods) {
                    result.add(endpoint(file, mapping, handler, httpMethod, path, contextPath, resolved, module));
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
            boolean pathResolved,
            ModuleNameResolver.Module module) {
        Map<String, Object> attrs = new TreeMap<>();
        attrs.put("httpMethod", httpMethod);
        attrs.put("path", path);
        if (!contextPath.isEmpty()) {
            attrs.put("contextPath", contextPath);
        }
        attrs.put("pathResolved", pathResolved);
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

    /** Một đoạn đường dẫn đã giải hằng số (nếu có). */
    private record PathText(String text, boolean resolved) {}

    private static PathText resolvePath(
            String raw, JavaFileFacts file, String declaringType, Map<String, String> constants) {
        if (!raw.startsWith(JavaFileFacts.CONSTANT_REF_PREFIX)) {
            return new PathText(raw, true);
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
        return value == null ? new PathText("${" + ref + "}", false) : new PathText(value, true);
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
