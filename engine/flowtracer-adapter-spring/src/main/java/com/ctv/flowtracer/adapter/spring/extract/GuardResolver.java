package com.ctv.flowtracer.adapter.spring.extract;

import com.ctv.flowtracer.ir.Guard;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.SwitchExpr;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.stmt.WhileStmt;
import java.util.stream.Collectors;

/**
 * Điều kiện bao quanh một node trong thân method. Chỉ lấy cấu trúc điều khiển GẦN NHẤT mà node nằm trong nhánh có điều
 * kiện (then / else / case / catch / thân vòng lặp); phần điều kiện của chính cấu trúc đó chạy vô điều kiện nên không
 * tính. Cấu trúc bao ngoài hơn bị bỏ — IR chỉ giữ một {@link Guard} cho mỗi lời gọi.
 */
final class GuardResolver {

    private GuardResolver() {}

    /** @return null nếu node chạy vô điều kiện trong method */
    static Guard guardOf(Node node) {
        Node child = node;
        Node parent = node.getParentNode().orElse(null);
        while (parent != null && !(parent instanceof CallableDeclaration<?>) && !(parent instanceof TypeDeclaration<?>)) {
            Guard guard = guardAt(parent, child);
            if (guard != null) {
                return guard;
            }
            child = parent;
            parent = child.getParentNode().orElse(null);
        }
        return null;
    }

    private static Guard guardAt(Node parent, Node child) {
        if (parent instanceof IfStmt s) {
            if (child == s.getThenStmt()) {
                return new Guard(Guard.Type.IF, text(s.getCondition()), "then");
            }
            if (s.getElseStmt().filter(e -> e == child).isPresent()) {
                return new Guard(Guard.Type.ELSE, text(s.getCondition()), "else");
            }
        } else if (parent instanceof ConditionalExpr c) {
            if (child == c.getThenExpr()) {
                return new Guard(Guard.Type.TERNARY, text(c.getCondition()), "then");
            }
            if (child == c.getElseExpr()) {
                return new Guard(Guard.Type.TERNARY, text(c.getCondition()), "else");
            }
        } else if (parent instanceof SwitchEntry e) {
            if (e.getStatements().stream().anyMatch(st -> st == child)) {
                return new Guard(Guard.Type.SWITCH, selector(e), label(e));
            }
        } else if (parent instanceof CatchClause c) {
            if (child == c.getBody()) {
                String type = c.getParameter().getType().asString();
                return new Guard(Guard.Type.CATCH, type, "catch:" + type);
            }
        } else if (parent instanceof ForEachStmt f) {
            if (child == f.getBody()) {
                return new Guard(Guard.Type.LOOP, text(f.getIterable()), "loop");
            }
        } else if (parent instanceof ForStmt f) {
            if (child == f.getBody()) {
                return new Guard(Guard.Type.LOOP, f.getCompare().map(GuardResolver::text).orElse("for"), "loop");
            }
        } else if (parent instanceof WhileStmt w) {
            if (child == w.getBody()) {
                return new Guard(Guard.Type.LOOP, text(w.getCondition()), "loop");
            }
        } else if (parent instanceof DoStmt d) {
            if (child == d.getBody()) {
                return new Guard(Guard.Type.LOOP, text(d.getCondition()), "loop");
            }
        } else if (parent instanceof LambdaExpr l && child == l.getBody()) {
            return lambdaGuard(l);
        }
        return null;
    }

    /**
     * Thân lambda chỉ chạy khi hàm nhận nó gọi tới. Với các hàm quen thuộc điều kiện chạy đã rõ: {@code ifPresent(x -> …)} chạy khi có
     * giá trị, {@code orElseGet(() -> …)} khi rỗng, {@code forEach(x -> …)} lặp. Quan trọng nhất cho {@code throw}: {@code
     * opt.ifPresent(e -> { throw …; })} là nhánh lỗi CÓ ĐIỀU KIỆN — không được coi là kết thúc vô điều kiện của cả hàm (mọi bước sau
     * đó sẽ biến mất khỏi luồng). Lambda của hàm khác giữ nguyên như trước (không gắn điều kiện).
     */
    private static Guard lambdaGuard(LambdaExpr lambda) {
        if (!(lambda.getParentNode().orElse(null) instanceof MethodCallExpr call) || !call.getArguments().contains(lambda)) {
            return null;
        }
        String scope = call.getScope().map(GuardResolver::text).orElse(call.getNameAsString());
        return switch (call.getNameAsString()) {
            case "ifPresent" -> new Guard(Guard.Type.IF, scope + ".isPresent()", "then");
            case "ifPresentOrElse" -> call.getArguments().indexOf(lambda) == 0
                    ? new Guard(Guard.Type.IF, scope + ".isPresent()", "then")
                    : new Guard(Guard.Type.ELSE, scope + ".isPresent()", "else");
            case "orElseGet" -> new Guard(Guard.Type.ELSE, scope + ".isPresent()", "else");
            case "forEach", "forEachOrdered" -> new Guard(Guard.Type.LOOP, scope, "loop");
            default -> null;
        };
    }

    private static String selector(SwitchEntry entry) {
        Node owner = entry.getParentNode().orElse(null);
        if (owner instanceof SwitchStmt s) {
            return text(s.getSelector());
        }
        return owner instanceof SwitchExpr s ? text(s.getSelector()) : "switch";
    }

    private static String label(SwitchEntry entry) {
        return entry.getLabels().isEmpty()
                ? "default"
                : "case " + entry.getLabels().stream().map(GuardResolver::text).collect(Collectors.joining(", "));
    }

    private static String text(Expression expression) {
        return AstSupport.snippet(expression);
    }
}
