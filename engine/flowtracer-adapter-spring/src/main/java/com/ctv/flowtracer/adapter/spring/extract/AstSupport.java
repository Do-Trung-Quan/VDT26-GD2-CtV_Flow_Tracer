package com.ctv.flowtracer.adapter.spring.extract;

import com.ctv.flowtracer.adapter.spring.facts.JavaFileFacts;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.TextBlockLiteralExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import com.github.javaparser.ast.nodeTypes.NodeWithJavadoc;
import com.github.javaparser.ast.type.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;

/** Hàm tiện ích đọc cây cú pháp JavaParser, dùng chung cho các extractor pha 1. */
final class AstSupport {

    private AstSupport() {}

    /** FQN của kiểu khai báo, kể cả kiểu lồng nhau ({@code pkg.Outer.Inner}). */
    static String fqn(TypeDeclaration<?> type) {
        return type.getFullyQualifiedName().orElse(type.getNameAsString());
    }

    /** FQN của kiểu gần nhất bao quanh node. */
    static Optional<String> enclosingTypeFqn(Node node) {
        return node.findAncestor(TypeDeclaration.class).map(t -> fqn((TypeDeclaration<?>) t));
    }

    /** Tên ngắn của các annotation, giữ thứ tự khai báo. */
    static List<String> annotationNames(NodeWithAnnotations<?> node) {
        return node.getAnnotations().stream().map(a -> simpleName(a.getNameAsString())).toList();
    }

    static Optional<AnnotationExpr> annotation(NodeWithAnnotations<?> node, String... simpleNames) {
        List<String> names = List.of(simpleNames);
        return node.getAnnotations().stream().filter(a -> names.contains(simpleName(a.getNameAsString()))).findFirst();
    }

    /**
     * Giá trị của thuộc tính annotation; {@code value} cũng đọc được từ dạng một tham số {@code @A("x")}.
     *
     * @param members tên thuộc tính theo thứ tự ưu tiên (vd {@code path}, {@code value})
     */
    static Optional<Expression> member(AnnotationExpr annotation, String... members) {
        if (annotation instanceof SingleMemberAnnotationExpr single) {
            return List.of(members).contains("value") ? Optional.of(single.getMemberValue()) : Optional.empty();
        }
        if (annotation instanceof NormalAnnotationExpr normal) {
            for (String m : members) {
                Optional<Expression> found = normal.getPairs().stream()
                        .filter(p -> p.getNameAsString().equals(m))
                        .map(p -> p.getValue())
                        .findFirst();
                if (found.isPresent()) {
                    return found;
                }
            }
        }
        return Optional.empty();
    }

    /** Một biểu thức hoặc mảng {@code {a, b}} → danh sách phần tử. */
    static List<Expression> elements(Expression expr) {
        return expr instanceof ArrayInitializerExpr array ? new ArrayList<>(array.getValues()) : List.of(expr);
    }

    /**
     * Đường dẫn trong annotation: literal (hoặc nối các literal) trả nguyên văn; tham chiếu hằng số trả dạng
     * {@code @const:Tên} để pha 2 giải.
     */
    static String pathValue(Expression expr) {
        return stringValue(expr).orElse(JavaFileFacts.CONSTANT_REF_PREFIX + expr.toString());
    }

    /** Giá trị chuỗi nếu biểu thức chỉ gồm literal (kể cả nối bằng +); rỗng nếu có thành phần khác. */
    static Optional<String> stringValue(Expression expr) {
        return stringValue(expr, name -> Optional.empty());
    }

    /**
     * Như {@link #stringValue(Expression)} nhưng cho phép tên hằng số (vd {@code API_V1 + "/x"}), tra qua {@code names}.
     */
    static Optional<String> stringValue(Expression expr, Function<String, Optional<String>> names) {
        if (expr instanceof NameExpr n) {
            return names.apply(n.getNameAsString());
        }
        if (expr instanceof StringLiteralExpr s) {
            return Optional.of(s.asString());
        }
        if (expr instanceof TextBlockLiteralExpr t) {
            return Optional.of(t.asString());
        }
        if (expr instanceof BinaryExpr b && b.getOperator() == BinaryExpr.Operator.PLUS) {
            Optional<String> left = stringValue(b.getLeft(), names);
            Optional<String> right = stringValue(b.getRight(), names);
            return left.isPresent() && right.isPresent() ? Optional.of(left.get() + right.get()) : Optional.empty();
        }
        if (expr.isEnclosedExpr()) {
            return stringValue(expr.asEnclosedExpr().getInner(), names);
        }
        return Optional.empty();
    }

    /** Giá trị boolean literal; rỗng nếu không phải literal. */
    static Optional<Boolean> booleanValue(Expression expr) {
        return expr instanceof BooleanLiteralExpr b ? Optional.of(b.getValue()) : Optional.empty();
    }

    /** Tên hằng enum hoặc tên cuối của tham chiếu (vd {@code RequestMethod.GET} → {@code GET}). */
    static String lastName(Expression expr) {
        if (expr instanceof FieldAccessExpr f) {
            return f.getNameAsString();
        }
        if (expr instanceof NameExpr n) {
            return n.getNameAsString();
        }
        return stringValue(expr).orElse(expr.toString());
    }

    /** Kiểu viết trong mã nguồn, bỏ chú thích kiểu (annotation) nếu có. */
    static String typeText(Type type) {
        Type copy = type.clone();
        copy.getAnnotations().clear();
        return copy.asString();
    }

    /** Nội dung Javadoc (đã bỏ dấu *), null nếu không có. */
    static String javadoc(NodeWithJavadoc<?> node) {
        return node.getJavadoc()
                .map(j -> j.getDescription().toText().strip())
                .filter(t -> !t.isEmpty())
                .orElse(null);
    }

    static int line(Node node) {
        return node.getBegin().map(p -> p.line).orElse(0);
    }

    static int endLine(Node node) {
        return node.getEnd().map(p -> p.line).orElse(line(node));
    }

    static String simpleName(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(dot + 1);
    }

    /**
     * Thuộc tính annotation có giá trị hằng đơn giản (chuỗi, text block, nối literal, boolean, tên enum), khóa
     * {@code TênAnnotation.thuộc_tính}; dạng một tham số {@code @A("x")} có khóa {@code A.value}. Tham chiếu tên
     * (hằng số, enum) giữ dạng {@code @const:Tên} để pha 2 giải; giá trị là mảng hoặc biểu thức phức tạp bị bỏ.
     */
    static Map<String, String> annotationValues(NodeWithAnnotations<?> node) {
        Map<String, String> result = new TreeMap<>();
        for (AnnotationExpr a : node.getAnnotations()) {
            String name = simpleName(a.getNameAsString());
            if (a instanceof SingleMemberAnnotationExpr single) {
                simpleValue(single.getMemberValue()).ifPresent(v -> result.put(name + ".value", v));
            } else if (a instanceof NormalAnnotationExpr normal) {
                normal.getPairs().forEach(p ->
                        simpleValue(p.getValue()).ifPresent(v -> result.put(name + "." + p.getNameAsString(), v)));
            }
        }
        return result;
    }

    private static Optional<String> simpleValue(Expression expr) {
        Optional<String> text = stringValue(expr);
        if (text.isPresent()) {
            return text;
        }
        if (expr instanceof BooleanLiteralExpr b) {
            return Optional.of(String.valueOf(b.getValue()));
        }
        if (expr instanceof FieldAccessExpr || expr instanceof NameExpr) {
            return Optional.of(JavaFileFacts.CONSTANT_REF_PREFIX + expr);
        }
        return Optional.empty();
    }

    /** Tên kiểu đã bỏ generic và mảng: {@code List<String>} → {@code List}, {@code a.b.Foo[]} → {@code a.b.Foo}. */
    static String rawType(String typeText) {
        int generic = typeText.indexOf('<');
        String raw = generic < 0 ? typeText : typeText.substring(0, generic);
        return raw.replace("[]", "").replace("...", "").strip();
    }

    /** Văn bản rút gọn của biểu thức để làm nhãn: một dòng, tối đa {@value #MAX_SNIPPET} ký tự. */
    static String snippet(Expression expr) {
        String text = expr.toString().replaceAll("\\s+", " ").strip();
        return text.length() <= MAX_SNIPPET ? text : text.substring(0, MAX_SNIPPET - 1) + "…";
    }

    static final int MAX_SNIPPET = 60;
}
