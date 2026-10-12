package com.ctv.flowtracer.app.scan.adapter;

import com.ctv.flowtracer.app.common.error.AppException;
import com.ctv.flowtracer.app.common.error.ErrorCode;
import com.ctv.flowtracer.ir.IrFragment;
import com.ctv.flowtracer.ir.SourceRole;
import com.ctv.flowtracer.ir.spi.DetectContext;
import com.ctv.flowtracer.ir.spi.DetectResult;
import com.ctv.flowtracer.ir.spi.ExtractContext;
import com.ctv.flowtracer.ir.spi.FileFacts;
import com.ctv.flowtracer.ir.spi.ResolveContext;
import com.ctv.flowtracer.ir.spi.SourceFile;
import com.ctv.flowtracer.ir.spi.StackAdapter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import tools.jackson.databind.json.JsonMapper;

/**
 * Adapter "đại diện" cho công nghệ chạy trong sidecar Node.js (Angular…): bề ngoài là {@link StackAdapter}, bên
 * trong gọi CLI sidecar. Logic nhận diện/phân tích nằm trong {@code analyzer-node/src/adapters/<id>}; ở đây chỉ khai
 * báo file đặc trưng, file nào được phân tích, và chuyển kết quả sang IR.
 *
 * <p>Sidecar tự chạy cả hai pha trong một lần gọi {@code analyze} (cache dữ kiện theo mã băm nằm ở thư mục riêng của
 * sidecar), nên {@link #extract} chỉ ghi nhận file và {@link #resolve} mới gọi sidecar. IrFragment sidecar trả về luôn
 * được kiểm tra với {@code contracts/ir-fragment.schema.json} trước khi dùng (CLAUDE.md §3.2c).
 */
public final class SidecarStackAdapter implements StackAdapter {

    static final Duration DETECT_TIMEOUT = Duration.ofSeconds(30);
    private static final int PROTOCOL_VERSION = 1;
    private static final int MAX_REPORTED_ERRORS = 5;

    private final String id;
    private final Set<SourceRole> roles;
    private final List<String> rootMarkers;
    private final List<String> detectFiles;
    private final Predicate<String> analyzes;
    private final String analysisVersion;
    private final SidecarRunner runner;
    private final JsonMapper json;

    /**
     * @param rootMarkers file đặc trưng ở gốc dự án, vd {@code angular.json}
     * @param detectFiles file gửi kèm (nội dung) cho lệnh {@code detect}, vd {@code angular.json}, {@code package.json}
     * @param analyzes file nào của Source được đưa vào lần phân tích (mã nguồn, template, file cấu hình cần đọc)
     * @param analysisVersion phiên bản bộ phân tích: tăng khi cách trích dữ kiện hoặc luật ghép của sidecar đổi, để
     *     "Quét lại" phân tích lại thay vì giữ IR cũ
     */
    public SidecarStackAdapter(
            String id,
            Set<SourceRole> roles,
            List<String> rootMarkers,
            List<String> detectFiles,
            Predicate<String> analyzes,
            String analysisVersion,
            SidecarRunner runner,
            JsonMapper json) {
        this.id = id;
        this.roles = Set.copyOf(roles);
        this.rootMarkers = List.copyOf(rootMarkers);
        this.detectFiles = List.copyOf(detectFiles);
        this.analyzes = analyzes;
        this.analysisVersion = analysisVersion;
        this.runner = runner;
        this.json = json;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public Set<SourceRole> roles() {
        return roles;
    }

    @Override
    public boolean analyzes(String relativePath) {
        return analyzes.test(relativePath);
    }

    @Override
    public String factsVersion() {
        return analysisVersion;
    }

    @Override
    public List<String> rootMarkers() {
        return rootMarkers;
    }

    @Override
    public Class<? extends FileFacts> factsType() {
        return SidecarFileFacts.class;
    }

    /** Gửi nội dung các file đặc trưng cho lệnh {@code detect}; kết quả được kiểm tra bởi record {@link DetectResult}. */
    @Override
    public DetectResult detect(DetectContext ctx) {
        Map<String, String> files = new LinkedHashMap<>();
        for (String file : detectFiles) {
            if (ctx.relativePaths().contains(file)) {
                ctx.loader().load(file).ifPresent(content -> files.put(file, content));
            }
        }
        var request = new LinkedHashMap<String, Object>();
        request.put("protocolVersion", PROTOCOL_VERSION);
        request.put("adapter", id);
        request.put("files", files);
        String out = runner.run("detect", json.writeValueAsString(request), DETECT_TIMEOUT);
        return json.readValue(out, DetectResult.class);
    }

    /** Chưa đọc gì: sidecar tự đọc file (trong {@code sourceRoot}, chỉ các file được cấp) ở bước {@link #resolve}. */
    @Override
    public FileFacts extract(ExtractContext ctx, SourceFile file) {
        return new SidecarFileFacts(file.relativePath(), file.contentHash());
    }

    /**
     * Gọi {@code analyze} một lần cho cả Source.
     *
     * <p>Cần hai tùy chọn do bước RESOLVE cấp: {@code workspaceId} (để đặt cache sidecar trong cây cache của Workspace) và
     * {@code sourceRootPath} (đường dẫn Source tương đối so với SOURCE_ROOT).
     */
    @Override
    public IrFragment resolve(ResolveContext ctx, List<FileFacts> facts) {
        UUID workspaceId = UUID.fromString(require(ctx.options(), OPTION_WORKSPACE_ID));
        String rootPath = ctx.options().getOrDefault(OPTION_SOURCE_ROOT_PATH, "");

        List<Map<String, String>> files = new ArrayList<>();
        facts.stream()
                .sorted(Comparator.comparing(FileFacts::relativePath))
                .forEach(f -> files.add(Map.of("path", f.relativePath(), "hash", f.contentHash())));

        var request = new LinkedHashMap<String, Object>();
        request.put("protocolVersion", PROTOCOL_VERSION);
        request.put("adapter", id);
        request.put("sourceId", ctx.sourceId());
        request.put("sourceRoot", runner.sourceDirectory(rootPath).toString());
        request.put("files", files);
        request.put("cacheDir", runner.cacheDirectory(workspaceId, id, analysisVersion, ctx.sourceId()).toString());

        String out = runner.run("analyze", json.writeValueAsString(request), runner.analyzeTimeout());
        List<String> errors = runner.validateFragment(out);
        if (!errors.isEmpty()) {
            int shown = Math.min(errors.size(), MAX_REPORTED_ERRORS);
            throw new AppException(
                    ErrorCode.SIDECAR_FAILED,
                    "Kết quả của sidecar không đúng hợp đồng ir-fragment (" + errors.size() + " lỗi): "
                            + String.join("; ", errors.subList(0, shown)));
        }
        return json.readValue(out, IrFragment.class).sorted();
    }

    /** Tùy chọn do bước RESOLVE cấp cho adapter chạy trong sidecar. */
    public static final String OPTION_WORKSPACE_ID = "workspaceId";

    public static final String OPTION_SOURCE_ROOT_PATH = "sourceRootPath";

    private static String require(Map<String, String> options, String key) {
        String value = options.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Thiếu tùy chọn '" + key + "' cho adapter chạy trong sidecar");
        }
        return value;
    }
}
