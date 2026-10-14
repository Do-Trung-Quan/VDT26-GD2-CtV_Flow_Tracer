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

    /**
     * Khai báo kiểu (class/interface/enum/record).
     *
     * @param annotationValues giá trị chuỗi/enum/boolean của annotation, khóa {@code TênAnnotation.thuộc_tính}
     *     (vd {@code Table.name}, {@code Qualifier.value}); thuộc tính không phải hằng đơn giản bị bỏ
     * @param modifiers {@code abstract} nếu là lớp trừu tượng
     * @param typeParameters tên tham số kiểu của lớp generic ({@code T} của {@code ApiResponse<T>})
     * @param enumConstants các hằng của enum, theo thứ tự khai báo; rỗng với kiểu khác
     * @param enumMessageKeys hằng của enum → đối số chuỗi đầu tiên của nó (vd khóa thông báo của enum mã lỗi
     *     {@code NOT_FOUND(404, "msg.not.found")}); hằng không có đối số chuỗi thì vắng mặt
     */
    public record TypeFact(
            String fqn,
            Kind kind,
            List<String> annotations,
            String superclass,
            List<String> interfaces,
            String javadoc,
            int line,
            Map<String, String> annotationValues,
            List<String> modifiers,
            List<String> typeParameters,
            List<String> enumConstants,
            Map<String, String> enumMessageKeys) {

        public TypeFact {
            annotations = annotations == null ? List.of() : List.copyOf(annotations);
            interfaces = interfaces == null ? List.of() : List.copyOf(interfaces);
            annotationValues = annotationValues == null ? Map.of() : Map.copyOf(annotationValues);
            modifiers = modifiers == null ? List.of() : List.copyOf(modifiers);
            typeParameters = typeParameters == null ? List.of() : List.copyOf(typeParameters);
            enumConstants = enumConstants == null ? List.of() : List.copyOf(enumConstants);
            enumMessageKeys = enumMessageKeys == null ? Map.of() : Map.copyOf(enumMessageKeys);
        }

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
     * @param annotationValues như {@link TypeFact#annotationValues()}
     */
    public record FieldFact(
            String declaringType,
            String name,
            String type,
            boolean isFinal,
            List<String> annotations,
            String constantValue,
            int line,
            boolean isStatic,
            Map<String, String> annotationValues) {

        public FieldFact {
            annotations = annotations == null ? List.of() : List.copyOf(annotations);
            annotationValues = annotationValues == null ? Map.of() : Map.copyOf(annotationValues);
        }
    }

    /**
     * Constructor hoặc method. {@code name = "<init>"} với constructor.
     *
     * @param paramNames tên tham số, cùng thứ tự {@code paramTypes}
     * @param paramQualifiers giá trị {@code @Qualifier} của từng tham số, "" nếu không có; cùng thứ tự {@code paramTypes}
     * @param annotationValues như {@link TypeFact#annotationValues()} (vd {@code Query.value}, {@code Query.nativeQuery})
     * @param hasBody false với method trừu tượng / khai báo trong interface
     */
    public record MethodFact(
            String declaringType,
            String name,
            List<String> paramTypes,
            String returnType,
            List<String> annotations,
            String javadoc,
            int startLine,
            int endLine,
            List<String> paramNames,
            List<String> paramQualifiers,
            Map<String, String> annotationValues,
            boolean hasBody) {

        public MethodFact {
            paramTypes = paramTypes == null ? List.of() : List.copyOf(paramTypes);
            annotations = annotations == null ? List.of() : List.copyOf(annotations);
            paramNames = paramNames == null ? List.of() : List.copyOf(paramNames);
            paramQualifiers = paramQualifiers == null ? List.of() : List.copyOf(paramQualifiers);
            annotationValues = annotationValues == null ? Map.of() : Map.copyOf(annotationValues);
        }

        /** Chữ ký {@code tên(kiểu,...)} — cùng dạng với {@link MappingFact#method()}. */
        public String signature() {
            return name + "(" + String.join(",", paramTypes) + ")";
        }
    }

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
     * Một lời gọi trong thân method. Chỉ ghi lời gọi có receiver đơn giản (tên biến / field / kiểu / {@code this} /
     * {@code super}); lời gọi nối chuỗi như {@code a.b().c()} chỉ ghi {@code a.b()}.
     *
     * @param callerMethod method chứa lời gọi, dạng {@code FQN#tên(kiểu,...)}
     * @param receiverType kiểu khai báo của receiver nếu là biến cục bộ / tham số (tên ngắn như viết trong mã, đã bỏ
     *     generic); null nếu receiver là field hoặc tên kiểu — pha 2 tra theo field của lớp
     * @param receiverExpr tên receiver: biến, field, tên kiểu (gọi static), {@code this}, {@code super}; hoặc
     *     {@code @call} (receiver là kết quả một lời gọi khác), {@code @new} ({@code new X().m()}), {@code @cast}
     * @param receiverCall {@code callOrder} của lời gọi tạo ra receiver (nối chuỗi {@code a.b().c()} hoặc biến {@code var});
     *     null nếu không có
     * @param argCount số đối số; -1 với tham chiếu hàm ({@code mapper::toResponse}) không rõ số đối số
     * @param callOrder thứ tự trong method, chung dãy số với {@link ThrowFact#order()}
     * @param http đối số của lời gọi có thể là lời gọi HTTP ({@code RestTemplate.getForObject(url…)}, {@code .uri(url)},
     *     {@code .method(HttpMethod.POST)}); null với lời gọi khác
     */
    public record CallSiteFact(
            String callerMethod,
            String receiverType,
            String receiverExpr,
            Integer receiverCall,
            String methodName,
            int argCount,
            int callOrder,
            Guard guard,
            int line,
            HttpArgs http,
            List<String> argTypes) {

        /** @param argTypes kiểu đã biết của từng đối số (tên ngắn, bỏ generic); phần tử null nếu chưa biết kiểu — dùng chọn hàm nạp chồng */
        public CallSiteFact {
            argTypes = argTypes == null ? List.of() : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(argTypes));
        }

        public CallSiteFact(
                String callerMethod,
                String receiverType,
                String receiverExpr,
                Integer receiverCall,
                String methodName,
                int argCount,
                int callOrder,
                Guard guard,
                int line) {
            this(callerMethod, receiverType, receiverExpr, receiverCall, methodName, argCount, callOrder, guard, line, null, null);
        }
    }

    /**
     * Đối số liên quan tới HTTP của một lời gọi.
     *
     * @param urlTemplate URL dựng từ đối số: phần literal giữ nguyên, tham chiếu tên {@code {@const:TÊN}} (pha 2 tra bảng
     *     hằng), phần không tính được {@code {?}}; null nếu lời gọi không có đối số URL
     * @param urlText văn bản rút gọn của biểu thức URL (để hiển thị)
     * @param httpMethod phương thức lấy từ đối số {@code HttpMethod.X} (vd {@code exchange(url, HttpMethod.GET, …)}); null
     *     nếu không có
     */
    public record HttpArgs(String urlTemplate, String urlText, String httpMethod) {}

    /**
     * Câu lệnh throw.
     *
     * @param method method chứa lệnh throw, dạng {@code FQN#tên(kiểu,...)}
     * @param exceptionType tên ngắn của exception ({@code new X(...)}); biểu thức khác thì văn bản rút gọn của nó
     * @param messageKey khóa/chuỗi thông báo nếu là literal hoặc hằng số (đối số đầu tiên có dạng đó)
     * @param order thứ tự trong method, chung dãy số với {@link CallSiteFact#callOrder()}
     */
    public record ThrowFact(String method, String exceptionType, String messageKey, Guard guard, int line, int order) {}

    /**
     * Một mảnh chuỗi trông như câu truy vấn (literal, text block, nối literal) trong một method — nguyên liệu phân tích
     * truy vấn ghép động. Chỉ giữ mảnh chứa từ khóa SQL/JPQL.
     *
     * @param method method chứa mảnh chuỗi, dạng {@code FQN#tên(kiểu,...)}
     * @param conditional true nếu mảnh nằm trong if/else, switch, toán tử ba ngôi hoặc vòng lặp
     */
    public record StringFragmentFact(String method, String text, boolean conditional, int line) {}

    /**
     * Trường của DTO/Entity (kể cả thành phần của record) kèm tên JSON và ràng buộc.
     *
     * @param jsonName tên do {@code @JsonProperty} đặt; null nếu theo tên trường (và chiến lược đặt tên của Jackson)
     * @param javaType kiểu viết trong mã nguồn, còn generic ({@code List<CategoryResponse>})
     * @param constraints annotation ràng buộc dạng {@code Tên} hoặc {@code Tên(khóa=giá trị, …)}: {@code NotNull},
     *     {@code Size(min=1, max=50)}, {@code Min(value=1)}, {@code Pattern(regexp=…)}, {@code Column(length=50, unique=true)}…
     * @param description câu giải thích từ {@code @Schema(description)} hoặc Javadoc của trường; null nếu không có
     */
    public record DtoFieldFact(
            String declaringType,
            String name,
            String jsonName,
            String javaType,
            List<String> constraints,
            int line,
            String description) {

        public DtoFieldFact {
            constraints = constraints == null ? List.of() : List.copyOf(constraints);
        }
    }
}
