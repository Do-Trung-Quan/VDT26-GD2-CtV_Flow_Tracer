package com.ctv.flowtracer.adapter.spring.facts;

import com.ctv.flowtracer.ir.Guard;
import com.ctv.flowtracer.ir.spi.FileFacts;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Dữ kiện pha 1 của một file Java. Chỉ chứa thông tin đọc được từ chính file đó (kèm FQN đã giải được nhờ
 * JavaSymbolSolver). Serialize JSON để cache theo mã băm file.
 */
public record JavaFileFacts(
        String relativePath,
        String contentHash,
        String packageName,
        List<String> imports,
        List<TypeFact> types,
        List<FieldFact> fields,
        List<MethodFact> methods,
        List<MappingFact> mappings,
        List<CallSiteFact> callSites,
        List<ThrowFact> throwsFacts,
        List<StringFragmentFact> stringFragments,
        List<DtoFieldFact> dtoFields,
        List<String> parseErrors)
        implements FileFacts {

    public JavaFileFacts {
        Objects.requireNonNull(relativePath, "relativePath");
        Objects.requireNonNull(contentHash, "contentHash");
        imports = copy(imports);
        types = copy(types);
        fields = copy(fields);
        methods = copy(methods);
        mappings = copy(mappings);
        callSites = copy(callSites);
        throwsFacts = copy(throwsFacts);
        stringFragments = copy(stringFragments);
        dtoFields = copy(dtoFields);
        parseErrors = copy(parseErrors);
    }

    /** Dữ kiện rỗng cho file không parse được (chỉ giữ lỗi). */
    public static JavaFileFacts failed(String relativePath, String contentHash, List<String> errors) {
        return new JavaFileFacts(relativePath, contentHash, null, null, null, null, null, null, null, null, null, null, errors);
    }

    private static <T> List<T> copy(List<T> list) {
        return list == null ? List.of() : List.copyOf(list);
    }

    /** Khai báo kiểu (class/interface/enum/record). */
    public record TypeFact(
            String fqn, Kind kind, List<String> annotations, String superclass, List<String> interfaces, String javadoc, int line) {

        public enum Kind {
            CLASS,
            INTERFACE,
            ENUM,
            RECORD,
            ANNOTATION
        }
    }

    /**
     * Field của một kiểu. {@code type} là FQN nếu giải được, ngược lại là tên ngắn.
     *
     * @param constantValue giá trị nếu là hằng chuỗi {@code static final String} khởi tạo bằng literal (hoặc nối các
     *     literal); null nếu không phải
     */
    public record FieldFact(
            String declaringType,
            String name,
            String type,
            boolean isFinal,
            List<String> annotations,
            String constantValue,
            int line) {}

    /** Constructor hoặc method. {@code name = "<init>"} với constructor. */
    public record MethodFact(
            String declaringType,
            String name,
            List<String> paramTypes,
            String returnType,
            List<String> annotations,
            String javadoc,
            int startLine,
            int endLine) {}

    /** Tiền tố đánh dấu một đường dẫn mapping là tham chiếu hằng số (vd {@code @const:ApiPaths.CATEGORY}), cần giải ở pha 2. */
    public static final String CONSTANT_REF_PREFIX = "@const:";

    /**
     * Một mapping HTTP. {@code method = null} nghĩa là mapping cấp class ({@code @RequestMapping} trên class).
     *
     * @param method chữ ký handler {@code tên(kiểu tham số,...)}, null với mapping cấp class
     * @param paths đường dẫn: literal giữ nguyên; tham chiếu hằng số có tiền tố {@link #CONSTANT_REF_PREFIX}
     * @param httpMethods GET/POST…; rỗng nghĩa là mọi phương thức ({@code @RequestMapping} không khai {@code method})
     * @param openApi summary/description/tag lấy từ {@code @Operation}, {@code @Tag}
     */
    public record MappingFact(
            String declaringType,
            String method,
            List<String> paths,
            List<String> httpMethods,
            List<String> consumes,
            List<String> produces,
            List<ParamFact> params,
            Map<String, String> openApi,
            int line) {}

    /** Tham số của handler method. */
    public record ParamFact(String name, String type, Source source, boolean required, String defaultValue) {

        public enum Source {
            PATH,
            QUERY,
            BODY,
            HEADER,
            OTHER
        }
    }

    /**
     * Một lời gọi trong thân method.
     *
     * @param callerMethod chữ ký method chứa lời gọi ({@code Type#name(params)})
     * @param receiverType FQN kiểu của receiver nếu giải được (null nếu không)
     * @param receiverExpr biểu thức receiver gốc (vd {@code categoryRepository}, {@code this})
     * @param resolvedTarget chữ ký method đích nếu SymbolSolver giải được
     */
    public record CallSiteFact(
            String callerMethod,
            String receiverType,
            String receiverExpr,
            String methodName,
            int argCount,
            String resolvedTarget,
            int callOrder,
            Guard guard,
            int line) {}

    /** Câu lệnh throw: loại exception và khóa/chuỗi thông báo (nếu là literal). */
    public record ThrowFact(String method, String exceptionType, String messageKey, Guard guard, int line) {}

    /**
     * Các mảnh chuỗi trong một method (literal, text block, StringBuilder.append) — nguyên liệu phân tích truy vấn
     * ghép động.
     *
     * @param conditional true nếu có mảnh được nối bên trong if/else
     */
    public record StringFragmentFact(String method, List<String> fragments, boolean conditional, int line) {}

    /** Trường của DTO/Entity kèm tên JSON và ràng buộc validation. */
    public record DtoFieldFact(
            String declaringType, String name, String jsonName, String javaType, List<String> constraints, int line) {}
}
