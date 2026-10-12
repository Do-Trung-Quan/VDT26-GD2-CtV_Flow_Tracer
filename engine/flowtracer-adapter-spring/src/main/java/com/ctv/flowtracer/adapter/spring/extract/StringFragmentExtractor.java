package com.ctv.flowtracer.adapter.spring.extract;

import com.ctv.flowtracer.adapter.spring.facts.JavaFileFacts.StringFragmentFact;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.TextBlockLiteralExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.LocalClassDeclarationStmt;
import com.github.javaparser.ast.stmt.LocalRecordDeclarationStmt;
import com.github.javaparser.ast.stmt.ThrowStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Các mảnh chuỗi trông như câu truy vấn (literal, text block, nối nhiều literal) trong thân method — nguyên liệu để
 * {@code QueryTextAnalyzer} tìm bảng của câu truy vấn ghép động. Chỉ giữ mảnh chứa từ khóa SQL/JPQL; bỏ qua chuỗi nằm
 * trong lệnh {@code throw} (thông báo lỗi).
 */
public final class StringFragmentExtractor {

    private static final Pattern QUERY_KEYWORD = Pattern.compile(
            "(?i)\\b(select|from|join|where|insert\\s+into|update|delete|group\\s+by|order\\s+by|having)\\b");

    public List<StringFragmentFact> extract(CompilationUnit unit) {
        var result = new ArrayList<StringFragmentFact>();
        for (MethodDeclaration m : unit.findAll(MethodDeclaration.class)) {
            m.getBody().ifPresent(body -> collect(m, body, result));
        }
        for (ConstructorDeclaration c : unit.findAll(ConstructorDeclaration.class)) {
            collect(c, c.getBody(), result);
        }
        return result;
    }

    private static void collect(CallableDeclaration<?> callable, BlockStmt body, List<StringFragmentFact> out) {
        if (callable.getParentNode().filter(p -> p instanceof TypeDeclaration<?>).isEmpty()) {
            return;
        }
        String declaringType = AstSupport.enclosingTypeFqn(callable).orElse("");
        String name = callable instanceof ConstructorDeclaration ? MemberExtractor.CONSTRUCTOR : callable.getNameAsString();
        body.accept(new Collector(declaringType + "#" + MemberExtractor.signature(callable, name), out), null);
    }

    private static final class Collector extends VoidVisitorAdapter<Void> {

        private final String method;
        private final List<StringFragmentFact> out;

        Collector(String method, List<StringFragmentFact> out) {
            this.method = method;
            this.out = out;
        }

        @Override
        public void visit(StringLiteralExpr n, Void arg) {
            add(n, n.asString());
        }

        @Override
        public void visit(TextBlockLiteralExpr n, Void arg) {
            add(n, n.asString());
        }

        /** Nối toàn literal thì gộp thành một mảnh; có biến xen vào thì từng literal là một mảnh riêng. */
        @Override
        public void visit(BinaryExpr n, Void arg) {
            Optional<String> merged = AstSupport.stringValue(n);
            if (merged.isPresent()) {
                add(n, merged.get());
            } else {
                super.visit(n, arg);
            }
        }

        @Override
        public void visit(ThrowStmt n, Void arg) {}

        @Override
        public void visit(ClassOrInterfaceDeclaration n, Void arg) {}

        @Override
        public void visit(LocalClassDeclarationStmt n, Void arg) {}

        @Override
        public void visit(LocalRecordDeclarationStmt n, Void arg) {}

        private void add(Expression expression, String text) {
            if (QUERY_KEYWORD.matcher(text).find()) {
                out.add(new StringFragmentFact(
                        method, text.strip(), GuardResolver.guardOf(expression) != null, AstSupport.line(expression)));
            }
        }
    }
}
