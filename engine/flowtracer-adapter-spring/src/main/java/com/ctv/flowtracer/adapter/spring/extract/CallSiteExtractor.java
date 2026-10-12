package com.ctv.flowtracer.adapter.spring.extract;

import com.ctv.flowtracer.adapter.spring.facts.JavaFileFacts.CallSiteFact;
import com.ctv.flowtracer.adapter.spring.facts.JavaFileFacts.ThrowFact;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.SuperExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.expr.TypeExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.LocalClassDeclarationStmt;
import com.github.javaparser.ast.stmt.LocalRecordDeclarationStmt;
import com.github.javaparser.ast.stmt.ThrowStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Lời gọi và lệnh {@code throw} trong thân từng method / constructor, đánh số theo THỨ TỰ THỰC THI (đối số và receiver
 * chạy trước lời gọi bao ngoài) và chung một dãy số cho cả hai — để pha 2 xếp được "kiểm tra rồi ném lỗi" lẫn "gọi
 * repository" đúng trình tự.
 *
 * <p>Chỉ ghi lời gọi có receiver đơn giản (xem {@link CallSiteFact}). Receiver là biến cục bộ / tham số lambda không rõ kiểu
 * thì bỏ qua, để không bị nhầm với field cùng tên. Thân lớp ẩn danh và lớp cục bộ không được duyệt.
 */
public final class CallSiteExtractor {

    private static final Pattern CONSTANT_NAME = Pattern.compile("[A-Z][A-Z0-9_]+");
    private static final int MAX_KEY_LENGTH = 80;

    /** Kết quả duyệt một file. */
    public record Result(List<CallSiteFact> callSites, List<ThrowFact> throwsFacts) {}

    public Result extract(CompilationUnit unit) {
        var calls = new ArrayList<CallSiteFact>();
        var throwsFacts = new ArrayList<ThrowFact>();
        for (MethodDeclaration m : unit.findAll(MethodDeclaration.class)) {
            m.getBody().ifPresent(body -> walk(m, body, calls, throwsFacts));
        }
        for (ConstructorDeclaration c : unit.findAll(ConstructorDeclaration.class)) {
            walk(c, c.getBody(), calls, throwsFacts);
        }
        return new Result(calls, throwsFacts);
    }

    private static void walk(
            CallableDeclaration<?> callable, BlockStmt body, List<CallSiteFact> calls, List<ThrowFact> throwsFacts) {
        // Bỏ method của lớp ẩn danh: cha trực tiếp không phải khai báo kiểu có tên
        if (callable.getParentNode().filter(p -> p instanceof TypeDeclaration<?>).isEmpty()) {
            return;
        }
        String declaringType = AstSupport.enclosingTypeFqn(callable).orElse("");
        String name = callable instanceof ConstructorDeclaration ? MemberExtractor.CONSTRUCTOR : callable.getNameAsString();
        String caller = declaringType + "#" + MemberExtractor.signature(callable, name);
        var walker = new Walker(caller, calls, throwsFacts);
        for (Parameter p : callable.getParameters()) {
            walker.declare(p.getNameAsString(), AstSupport.rawType(AstSupport.typeText(p.getType())));
        }
        body.accept(walker, null);
    }

    /** Duyệt thân method theo thứ tự thực thi, ghi lời gọi và throw. */
    private static final class Walker extends VoidVisitorAdapter<Void> {

        private final String caller;
        private final List<CallSiteFact> calls;
        private final List<ThrowFact> throwsFacts;
        /** Tên → kiểu khai báo (tên ngắn); null nghĩa là biến cục bộ không rõ kiểu. */
        private final Map<String, String> locals = new HashMap<>();

        private int order;
        /** Lời gọi đã ghi → số thứ tự của nó, để lời gọi nối chuỗi / biến {@code var} tham chiếu tới. */
        private final Map<Node, Integer> recorded = new java.util.IdentityHashMap<>();

        Walker(String caller, List<CallSiteFact> calls, List<ThrowFact> throwsFacts) {
            this.caller = caller;
            this.calls = calls;
            this.throwsFacts = throwsFacts;
        }

        void declare(String name, String type) {
            locals.put(name, type);
        }

        @Override
        public void visit(MethodCallExpr n, Void arg) {
            n.getScope().ifPresent(s -> s.accept(this, arg));
            n.getArguments().forEach(a -> a.accept(this, arg));
            record(n.getScope().orElse(null), n.getNameAsString(), n.getArguments().size(), n);
        }

        private static final String CALL_PREFIX = "@call:";

        @Override
        public void visit(MethodReferenceExpr n, Void arg) {
            n.getScope().accept(this, arg);
            record(n.getScope(), n.getIdentifier(), -1, n);
        }

        @Override
        public void visit(ObjectCreationExpr n, Void arg) {
            n.getScope().ifPresent(s -> s.accept(this, arg));
            n.getArguments().forEach(a -> a.accept(this, arg));
        }

        @Override
        public void visit(ClassOrInterfaceDeclaration n, Void arg) {}

        @Override
        public void visit(LocalClassDeclarationStmt n, Void arg) {}

        @Override
        public void visit(LocalRecordDeclarationStmt n, Void arg) {}

        @Override
        public void visit(VariableDeclarator n, Void arg) {
            n.getInitializer().ifPresent(i -> i.accept(this, arg));
            String type = null;
            if (!n.getType().isVarType()) {
                type = AstSupport.rawType(AstSupport.typeText(n.getType()));
            } else if (n.getInitializer().orElse(null) instanceof ObjectCreationExpr created) {
                type = created.getType().getNameAsString();
            } else if (recorded.containsKey(n.getInitializer().orElse(null))) {
                type = CALL_PREFIX + recorded.get(n.getInitializer().get()); // var x = a.b(); → kiểu = kiểu trả về của b
            }
            locals.put(n.getNameAsString(), type);
        }

        @Override
        public void visit(Parameter n, Void arg) {
            boolean unknown = n.getType().isUnknownType() || n.getType().isVarType();
            locals.put(n.getNameAsString(), unknown ? null : AstSupport.rawType(AstSupport.typeText(n.getType())));
        }

        @Override
        public void visit(ThrowStmt n, Void arg) {
            n.getExpression().accept(this, arg);
            Expression e = n.getExpression();
            String type;
            String key = null;
            if (e instanceof ObjectCreationExpr created) {
                type = created.getType().getNameAsString();
                key = messageKey(created.getArguments());
            } else if (e instanceof MethodCallExpr call) {
                type = call.getScope().map(s -> AstSupport.snippet(s) + ".").orElse("") + call.getNameAsString();
                key = messageKey(call.getArguments());
            } else if (e instanceof NameExpr name) {
                type = Optional.ofNullable(locals.get(name.getNameAsString())).orElse(name.getNameAsString());
            } else {
                type = AstSupport.snippet(e);
            }
            throwsFacts.add(new ThrowFact(caller, type, key, GuardResolver.guardOf(n), AstSupport.line(n), order++));
        }

        private void record(Expression scope, String methodName, int argCount, Node at) {
            String receiverExpr;
            String receiverType = null;
            Integer receiverCall = null;
            if (scope instanceof MethodCallExpr inner && recorded.containsKey(inner)) {
                receiverExpr = "@call";
                receiverCall = recorded.get(inner);
            } else if (scope instanceof ObjectCreationExpr created && created.getAnonymousClassBody().isEmpty()) {
                receiverExpr = "@new";
                receiverType = AstSupport.rawType(created.getType().asString());
            } else if (scope instanceof com.github.javaparser.ast.expr.EnclosedExpr enclosed
                    && enclosed.getInner() instanceof com.github.javaparser.ast.expr.CastExpr cast) {
                receiverExpr = "@cast";
                receiverType = AstSupport.rawType(AstSupport.typeText(cast.getType()));
            } else if (scope == null || scope instanceof ThisExpr t && t.getTypeName().isEmpty()) {
                receiverExpr = "this";
            } else if (scope instanceof SuperExpr) {
                receiverExpr = "super";
            } else if (scope instanceof NameExpr n) {
                receiverExpr = n.getNameAsString();
                if (locals.containsKey(receiverExpr)) {
                    receiverType = locals.get(receiverExpr);
                    if (receiverType == null) {
                        return;
                    }
                    if (receiverType.startsWith(CALL_PREFIX)) {
                        receiverCall = Integer.valueOf(receiverType.substring(CALL_PREFIX.length()));
                        receiverType = null;
                    }
                }
            } else if (scope instanceof FieldAccessExpr f && f.getScope() instanceof ThisExpr) {
                receiverExpr = f.getNameAsString();
            } else if (scope instanceof TypeExpr t) {
                receiverExpr = AstSupport.rawType(AstSupport.typeText(t.getType()));
            } else {
                return;
            }
            recorded.put(at, order);
            calls.add(new CallSiteFact(
                    caller,
                    receiverType,
                    receiverExpr,
                    receiverCall,
                    methodName,
                    argCount,
                    order++,
                    GuardResolver.guardOf(at),
                    AstSupport.line(at)));
        }

        /** Đối số đầu tiên (trong 2 đối số đầu) là chuỗi literal hoặc tên hằng {@code UPPER_SNAKE}; null nếu không có. */
        private static String messageKey(List<Expression> arguments) {
            for (Expression a : arguments.subList(0, Math.min(2, arguments.size()))) {
                if (a instanceof StringLiteralExpr s) {
                    String text = s.asString();
                    return text.length() <= MAX_KEY_LENGTH ? text : text.substring(0, MAX_KEY_LENGTH - 1) + "…";
                }
                if (a instanceof NameExpr || a instanceof FieldAccessExpr) {
                    String name = AstSupport.lastName(a);
                    if (CONSTANT_NAME.matcher(name).matches()) {
                        return name;
                    }
                }
            }
            return null;
        }
    }
}
