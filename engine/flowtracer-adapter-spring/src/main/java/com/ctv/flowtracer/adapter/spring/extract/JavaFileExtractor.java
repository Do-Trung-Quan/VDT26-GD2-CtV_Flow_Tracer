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
 * <p>Trích: package, import, kiểu, field, method, mapping HTTP (Giai đoạn 2); lời gọi, throw, mảnh chuỗi truy vấn
 * (Giai đoạn 3); trường DTO / Entity và thành phần của record (Giai đoạn 6). Kiểu của receiver được giải bằng khai báo (biến, tham số, field)
 * và import — không dùng JavaSymbolSolver vì dự án phân tích không có classpath.
 */
public final class JavaFileExtractor {

    /**
     * Phiên bản bộ phân tích Spring: TĂNG khi đổi cách trích dữ kiện HOẶC cách ghép IR ở pha 2 (vd luật phân giải lời gọi).
     * Dữ kiện cache với phiên bản cũ bị bỏ, và "Quét lại" phân tích lại toàn bộ dù không file nào đổi — nếu không IR đã lưu
     * sẽ thiếu những gì bản mới biết.
     */
    public static final String VERSION = "10";

    private static final int MAX_REPORTED_PROBLEMS = 5;

    private final TypeDeclarationExtractor types = new TypeDeclarationExtractor();
    private final MemberExtractor members = new MemberExtractor();
    private final MappingExtractor mappings = new MappingExtractor();
    private final CallSiteExtractor callSites = new CallSiteExtractor();
    private final StringFragmentExtractor fragments = new StringFragmentExtractor();
    private final DtoFieldExtractor dtoFields = new DtoFieldExtractor();

    public JavaFileFacts extract(ExtractContext ctx, SourceFile file) {
        var parser = new JavaParser(new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21));
        ParseResult<CompilationUnit> result = parser.parse(file.content());
        if (!result.isSuccessful() || result.getResult().isEmpty()) {
            return JavaFileFacts.failed(file.relativePath(), file.contentHash(), problems(result.getProblems()));
        }
        CompilationUnit unit = result.getResult().get();
        CallSiteExtractor.Result body = callSites.extract(unit);
        return new JavaFileFacts(
                file.relativePath(),
                file.contentHash(),
                unit.getPackageDeclaration().map(PackageDeclaration::getNameAsString).orElse(""),
                unit.getImports().stream().map(JavaFileExtractor::importText).toList(),
                types.extract(unit),
                members.extractFields(unit),
                members.extract(unit),
                mappings.extract(unit),
                body.callSites(),
                body.throwsFacts(),
                fragments.extract(unit),
                dtoFields.extract(unit),
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
