package com.ctv.flowtracer.adapter.spring.extract;

import com.ctv.flowtracer.adapter.spring.facts.JavaFileFacts;
import com.ctv.flowtracer.ir.spi.ExtractContext;
import com.ctv.flowtracer.ir.spi.SourceFile;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.Problem;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.PackageDeclaration;
import java.util.List;

/**
 * PHA 1: parse một file (chịu lỗi cú pháp) bằng JavaParser và gom dữ kiện.
 *
 * <p>Giai đoạn 2 trích: package, import, kiểu, field, method, mapping HTTP. Lời gọi, throw, mảnh chuỗi, trường DTO và
 * giải kiểu bằng JavaSymbolSolver làm ở Giai đoạn 3 (để rỗng).
 */
public final class JavaFileExtractor {

    /** Tăng khi đổi cách trích dữ kiện — dữ kiện đã cache với phiên bản cũ sẽ bị bỏ qua. */
    public static final String VERSION = "1";

    private static final int MAX_REPORTED_PROBLEMS = 5;

    private final TypeDeclarationExtractor types = new TypeDeclarationExtractor();
    private final MemberExtractor members = new MemberExtractor();
    private final MappingExtractor mappings = new MappingExtractor();

    public JavaFileFacts extract(ExtractContext ctx, SourceFile file) {
        var parser = new JavaParser(new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21));
        ParseResult<CompilationUnit> result = parser.parse(file.content());
        if (!result.isSuccessful() || result.getResult().isEmpty()) {
            return JavaFileFacts.failed(file.relativePath(), file.contentHash(), problems(result.getProblems()));
        }
        CompilationUnit unit = result.getResult().get();
        return new JavaFileFacts(
                file.relativePath(),
                file.contentHash(),
                unit.getPackageDeclaration().map(PackageDeclaration::getNameAsString).orElse(""),
                unit.getImports().stream().map(JavaFileExtractor::importText).toList(),
                types.extract(unit),
                members.extractFields(unit),
                members.extract(unit),
                mappings.extract(unit),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }

    /** Thông báo lỗi cú pháp ngắn gọn kèm dòng; tối đa {@value #MAX_REPORTED_PROBLEMS} lỗi. */
    private static List<String> problems(List<Problem> problems) {
        if (problems.isEmpty()) {
            return List.of("Không phân tích được file Java");
        }
        return problems.stream()
                .limit(MAX_REPORTED_PROBLEMS)
                .map(p -> p.getLocation()
                                .flatMap(l -> l.getBegin().getRange())
                                .map(r -> "Dòng " + r.begin.line + ": ")
                                .orElse("")
                        + shortMessage(p.getMessage()))
                .toList();
    }

    /** Dòng đầu của thông báo, bỏ danh sách token mong đợi dài dòng của JavaParser. */
    static String shortMessage(String message) {
        int newline = message.indexOf('\n');
        String first = newline < 0 ? message : message.substring(0, newline);
        int expected = first.indexOf(", expected");
        String trimmed = expected < 0 ? first : first.substring(0, expected);
        return trimmed.replace("Parse error.", "Lỗi cú pháp.").strip();
    }

    /** Dạng văn bản của import: {@code a.b.C}, {@code a.b.*}, {@code static a.b.C.m}. */
    private static String importText(ImportDeclaration imp) {
        return (imp.isStatic() ? "static " : "") + imp.getNameAsString() + (imp.isAsterisk() ? ".*" : "");
    }
}
