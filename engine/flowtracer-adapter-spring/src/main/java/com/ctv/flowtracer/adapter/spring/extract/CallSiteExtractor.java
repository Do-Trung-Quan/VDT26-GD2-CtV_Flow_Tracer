package com.ctv.flowtracer.adapter.spring.extract;

import com.ctv.flowtracer.adapter.spring.facts.JavaFileFacts;
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
    /** Tên hàm mà đối số đầu là URL: {@code RestTemplate} / {@code RestOperations}, và {@code uri(…)} của WebClient / RestClient. */
    private static final java.util.Set<String> URL_METHODS = java.util.Set.of(
            "getForObject", "getForEntity", "postForObject", "postForEntity", "postForLocation", "put", "delete",
            "patchForObject", "exchange", "execute", "headForHeaders", "optionsForAllow", "uri");
    private static final Pattern HTTP_METHOD = Pattern.compile("(?:HttpMethod\\.)?(GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS)");
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

        /** Đối số HTTP của lời gọi (xem {@link JavaFileFacts.HttpArgs}); null nếu không phải dạng đó. */
        private static JavaFileFacts.HttpArgs httpArgs(Node at, String methodName) {
            if (!(at instanceof MethodCallExpr call) || call.getArguments().isEmpty()) {
                return null;
            }
            List<Expression> args = call.getArguments();
            if (methodName.equals("method")) {
                String method = httpMethodOf(args.get(0));
                return method == null ? null : new JavaFileFacts.HttpArgs(null, null, method);
            }
            if (!URL_METHODS.contains(methodName)) {
                return null;
            }
            Expression url = args.get(0);
            String method = args.size() > 1 && (methodName.equals("exchange") || methodName.equals("execute")) ? httpMethodOf(args.get(1)) : null;
            return new JavaFileFacts.HttpArgs(urlTemplate(url), AstSupport.snippet(url), method);
        }

        private static String httpMethodOf(Expression expr) {
            var m = HTTP_METHOD.matcher(expr.toString().strip());
            return m.matches() ? m.group(1) : null;
        }

        /** URL dựng từ biểu thức: literal giữ nguyên, tên biến / hằng thành {@code {@const:tên}}, phần khác thành {@code {?}}. */
        static String urlTemplate(Expression expr) {
            if (expr instanceof StringLiteralExpr s) {
                return s.asString();
            }
            if (expr instanceof com.github.javaparser.ast.expr.TextBlockLiteralExpr t) {
                return t.asString().strip();
            }
            if (expr instanceof com.github.javaparser.ast.expr.EnclosedExpr e) {
                return urlTemplate(e.getInner());
            }
            if (expr instanceof com.github.javaparser.ast.expr.BinaryExpr b
                    && b.getOperator() == com.github.javaparser.ast.expr.BinaryExpr.Operator.PLUS) {
                return urlTemplate(b.getLeft()) + urlTemplate(b.getRight());
            }
            if (expr instanceof NameExpr n) {
                return "{" + JavaFileFacts.CONSTANT_REF_PREFIX + n.getNameAsString() + "}";
            }
            if (expr instanceof FieldAccessExpr f) {
                String ref = f.getScope() instanceof ThisExpr ? f.getNameAsString() : f.toString();
                return "{" + JavaFileFacts.CONSTANT_REF_PREFIX + ref + "}";
            }
            return "{?}";
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
                if (key == null) {
                    key = enumConstantRef(call);
                }
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
                    AstSupport.line(at),
                    httpArgs(at, methodName),
                    argTypes(at)));
        }

        /** Kiểu đã biết của từng đối số: biến cục bộ / tham số có kiểu khai báo, literal, {@code new X()}, ép kiểu; còn lại null. */
        private List<String> argTypes(Node at) {
            if (!(at instanceof MethodCallExpr call)) {
                return List.of();
            }
            var types = new java.util.ArrayList<String>();
            for (Expression a : call.getArguments()) {
                types.add(argType(a));
            }
            return types;
        }

        private String argType(Expression a) {
            if (a instanceof NameExpr n) {
                String type = locals.get(n.getNameAsString());
                return type == null || type.startsWith(CALL_PREFIX) ? null : type;
            }
            if (a instanceof StringLiteralExpr) {
                return "String";
            }
            if (a instanceof com.github.javaparser.ast.expr.IntegerLiteralExpr) {
                return "int";
            }
            if (a instanceof com.github.javaparser.ast.expr.LongLiteralExpr) {
                return "long";
            }
            if (a instanceof com.github.javaparser.ast.expr.BooleanLiteralExpr) {
                return "boolean";
            }
            if (a instanceof com.github.javaparser.ast.expr.DoubleLiteralExpr) {
                return "double";
            }
            if (a instanceof ObjectCreationExpr created && created.getAnonymousClassBody().isEmpty()) {
                return AstSupport.rawType(created.getType().asString());
            }
            if (a instanceof com.github.javaparser.ast.expr.CastExpr cast) {
                return AstSupport.rawType(AstSupport.typeText(cast.getType()));
            }
            return null;
        }

        /**
         * {@code AppError.NOT_FOUND.exception()}: lỗi dựng từ một hằng enum mã lỗi → "AppError.NOT_FOUND" (bộ ghép sẽ tra khóa
         * thông báo của hằng đó trong enum); null nếu không có dạng này.
         */
        private static String enumConstantRef(MethodCallExpr call) {
            if (call.getScope().orElse(null) instanceof FieldAccessExpr f
                    && CONSTANT_NAME.matcher(f.getNameAsString()).matches()
                    && (f.getScope() instanceof NameExpr || f.getScope() instanceof FieldAccessExpr)) {
                String owner = AstSupport.lastName(f.getScope());
                if (!owner.isEmpty() && Character.isUpperCase(owner.charAt(0))) {
                    return owner + "." + f.getNameAsString();
                }
            }
            return null;
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
