package com.ctv.flowtracer.adapter.spring.resolve;

import com.ctv.flowtracer.adapter.spring.facts.JavaFileFacts;
import com.ctv.flowtracer.adapter.spring.facts.JavaFileFacts.FieldFact;
import com.ctv.flowtracer.adapter.spring.facts.JavaFileFacts.MethodFact;
import com.ctv.flowtracer.adapter.spring.facts.JavaFileFacts.TypeFact;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Bảng tra của cả Source: FQN → khai báo kiểu (kèm field, method, file chứa nó); tên ngắn + import → FQN; quan hệ kế
 * thừa / hiện thực. Mọi kết quả trả về theo thứ tự ổn định.
 */
public final class ClassIndex {

    /** Một kiểu trong dự án cùng dữ kiện đi kèm. */
    public record TypeInfo(TypeFact type, JavaFileFacts file, List<FieldFact> fields, List<MethodFact> methods) {

        public String fqn() {
            return type.fqn();
        }

        public boolean isInterface() {
            return type.kind() == TypeFact.Kind.INTERFACE;
        }

        public boolean isAbstract() {
            return isInterface() || type.modifiers().contains("abstract");
        }

        public boolean hasAnnotation(String simpleName) {
            return type.annotations().contains(simpleName);
        }
    }

    /** Một method tìm được cùng kiểu khai báo nó. */
    public record MethodMatch(TypeInfo declaringType, MethodFact method) {}

    private final Map<String, TypeInfo> byFqn = new TreeMap<>();
    private final Map<String, List<String>> ancestorsCache = new TreeMap<>();
    private Map<String, List<String>> implementers;

    public ClassIndex(List<JavaFileFacts> facts) {
        for (JavaFileFacts file : facts) {
            for (TypeFact type : file.types()) {
                byFqn.put(type.fqn(), new TypeInfo(
                        type,
                        file,
                        file.fields().stream().filter(f -> f.declaringType().equals(type.fqn())).toList(),
                        file.methods().stream().filter(m -> m.declaringType().equals(type.fqn())).toList()));
            }
        }
    }

    public Optional<TypeFact> find(String fqn) {
        return typeInfo(fqn).map(TypeInfo::type);
    }

    public Optional<TypeInfo> typeInfo(String fqn) {
        return Optional.ofNullable(byFqn.get(fqn));
    }

    public List<TypeInfo> allTypes() {
        return List.copyOf(byFqn.values());
    }

    /**
     * Giải tên ngắn (hoặc tên có dấu chấm của kiểu lồng) trong ngữ cảnh một file theo thứ tự: kiểu khai báo trong chính
     * file, import tường minh, cùng package, import có ký tự đại diện. Chỉ trả về kiểu CÓ trong dự án.
     */
    public Optional<String> resolveSimpleName(String simpleName, JavaFileFacts context) {
        String name = TypeNames.stripGenerics(simpleName);
        if (byFqn.containsKey(name)) {
            return Optional.of(name);
        }
        int dot = name.indexOf('.');
        String head = dot < 0 ? name : name.substring(0, dot);
        String tail = dot < 0 ? "" : name.substring(dot);
        Optional<String> resolvedHead = resolveHead(head, context);
        return resolvedHead.map(h -> h + tail).filter(byFqn::containsKey);
    }

    private Optional<String> resolveHead(String head, JavaFileFacts context) {
        for (TypeFact t : context.types()) {
            if (t.fqn().equals(head) || t.fqn().endsWith("." + head)) {
                return Optional.of(t.fqn());
            }
        }
        for (String imp : context.imports()) {
            if (!imp.startsWith("static ") && !imp.endsWith(".*") && imp.endsWith("." + head) && byFqn.containsKey(imp)) {
                return Optional.of(imp);
            }
        }
        if (context.packageName() != null) {
            String samePackage = context.packageName().isEmpty() ? head : context.packageName() + "." + head;
            if (byFqn.containsKey(samePackage)) {
                return Optional.of(samePackage);
            }
        }
        for (String imp : context.imports()) {
            if (!imp.startsWith("static ") && imp.endsWith(".*")) {
                String candidate = imp.substring(0, imp.length() - 1) + head;
                if (byFqn.containsKey(candidate)) {
                    return Optional.of(candidate);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Tên đầy đủ của kiểu ngay cả khi KHÔNG có trong dự án (thư viện): import tường minh nếu có, ngược lại tên ngắn. Dùng
     * để khớp luật QUERY_SINK với kiểu nằm trong jar.
     */
    public String qualifiedOrSimple(String simpleName, JavaFileFacts context) {
        Optional<String> inProject = resolveSimpleName(simpleName, context);
        if (inProject.isPresent()) {
            return inProject.get();
        }
        String name = TypeNames.stripGenerics(simpleName);
        for (String imp : context.imports()) {
            if (!imp.startsWith("static ") && !imp.endsWith(".*") && imp.endsWith("." + name)) {
                return imp;
            }
        }
        return name;
    }

    /**
     * Tổ tiên trong dự án (lớp cha rồi các interface, theo chiều rộng), không gồm chính nó. Bỏ qua kiểu thư viện — để biết
     * tên thư viện thì dùng {@link #rawSupertypeNames(String)}.
     */
    public List<String> ancestors(String fqn) {
        List<String> cached = ancestorsCache.get(fqn);
        if (cached != null) {
            return cached;
        }
        var result = new LinkedHashSet<String>();
        var queue = new ArrayList<String>(directSupertypes(fqn));
        for (int i = 0; i < queue.size(); i++) {
            String next = queue.get(i);
            if (!next.equals(fqn) && result.add(next)) {
                queue.addAll(directSupertypes(next));
            }
        }
        List<String> list = List.copyOf(result);
        ancestorsCache.put(fqn, list);
        return list;
    }

    /** Lớp cha và interface trực tiếp có trong dự án; lớp cha trước. */
    public List<String> directSupertypes(String fqn) {
        TypeInfo info = byFqn.get(fqn);
        if (info == null) {
            return List.of();
        }
        var result = new ArrayList<String>();
        if (info.type().superclass() != null) {
            resolveSimpleName(info.type().superclass(), info.file()).ifPresent(result::add);
        }
        for (String i : info.type().interfaces()) {
            resolveSimpleName(i, info.file()).ifPresent(result::add);
        }
        return result;
    }

    /**
     * Mọi tên kiểu cha viết trong mã (cả kiểu thư viện, đã bỏ generic và tên gói), của kiểu này và các tổ tiên trong dự án.
     * Dùng để nhận ra {@code JpaRepository}, {@code CrudRepository}…
     */
    public Set<String> rawSupertypeNames(String fqn) {
        var names = new LinkedHashSet<String>();
        var all = new ArrayList<String>();
        all.add(fqn);
        all.addAll(ancestors(fqn));
        for (String type : all) {
            TypeInfo info = byFqn.get(type);
            if (info == null) {
                continue;
            }
            if (info.type().superclass() != null) {
                names.add(TypeNames.simple(TypeNames.stripGenerics(info.type().superclass())));
            }
            info.type().interfaces().forEach(i -> names.add(TypeNames.simple(TypeNames.stripGenerics(i))));
        }
        return names;
    }

    /**
     * Các kiểu cụ thể (không phải interface / lớp trừu tượng) kế thừa hoặc hiện thực {@code fqn}, trực tiếp hay gián tiếp,
     * sắp theo FQN.
     */
    public List<String> concreteSubtypes(String fqn) {
        if (implementers == null) {
            var map = new TreeMap<String, List<String>>();
            for (TypeInfo info : byFqn.values()) {
                if (info.isAbstract() || info.type().kind() == TypeFact.Kind.ANNOTATION) {
                    continue;
                }
                for (String ancestor : ancestors(info.fqn())) {
                    map.computeIfAbsent(ancestor, k -> new ArrayList<>()).add(info.fqn());
                }
            }
            implementers = map;
        }
        return implementers.getOrDefault(fqn, List.of());
    }

    /** Field theo tên của kiểu hoặc lớp cha (không tìm trong interface). */
    public Optional<FieldFact> findField(String typeFqn, String name) {
        var chain = new ArrayList<String>();
        chain.add(typeFqn);
        chain.addAll(ancestors(typeFqn));
        for (String fqn : chain) {
            TypeInfo info = byFqn.get(fqn);
            if (info == null) {
                continue;
            }
            Optional<FieldFact> found = info.fields().stream().filter(f -> f.name().equals(name)).findFirst();
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    /**
     * Method theo tên và số đối số ({@code -1}: không rõ số đối số) tại kiểu GẦN NHẤT khai báo nó (chính kiểu, rồi lớp
     * cha, rồi interface). Có thể trả về nhiều method nếu nạp chồng cùng số tham số.
     */
    public List<MethodMatch> findMethods(String typeFqn, String name, int argCount) {
        var chain = new ArrayList<String>();
        chain.add(typeFqn);
        chain.addAll(ancestors(typeFqn));
        for (String fqn : chain) {
            TypeInfo info = byFqn.get(fqn);
            if (info == null) {
                continue;
            }
            List<MethodMatch> matches = info.methods().stream()
                    .filter(m -> m.name().equals(name) && (argCount < 0 || acceptsArgCount(m, argCount)))
                    .map(m -> new MethodMatch(info, m))
                    .toList();
            if (!matches.isEmpty()) {
                return matches;
            }
        }
        return List.of();
    }

    private static boolean acceptsArgCount(MethodFact m, int argCount) {
        int params = m.paramTypes().size();
        boolean varargs = !m.paramTypes().isEmpty() && m.paramTypes().getLast().endsWith("...");
        return params == argCount || varargs && argCount >= params - 1;
    }
}
