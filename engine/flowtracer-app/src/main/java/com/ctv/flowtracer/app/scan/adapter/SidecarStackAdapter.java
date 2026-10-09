package com.ctv.flowtracer.app.scan.adapter;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.json.JsonMapper;

/**
 * Adapter "đại diện" cho công nghệ chạy trong sidecar Node.js (Angular…): bề ngoài là {@link StackAdapter}, bên
 * trong gọi CLI sidecar. Logic nhận diện/phân tích nằm trong {@code analyzer-node/src/adapters/<id>}; ở đây chỉ khai
 * báo file đặc trưng và file cần gửi kèm.
 *
 * <p>TODO (Giai đoạn 4): {@code extract}/{@code resolve} qua lệnh {@code analyze} (sidecar tự chạy cả hai pha).
 */
public final class SidecarStackAdapter implements StackAdapter {

    static final Duration DETECT_TIMEOUT = Duration.ofSeconds(30);
    private static final int PROTOCOL_VERSION = 1;

    private final String id;
    private final Set<SourceRole> roles;
    private final List<String> rootMarkers;
    private final List<String> detectFiles;
    private final SidecarRunner runner;
    private final JsonMapper json;

    /**
     * @param rootMarkers file đặc trưng ở gốc dự án, vd {@code angular.json}
     * @param detectFiles file gửi kèm (nội dung) cho lệnh {@code detect}, vd {@code angular.json}, {@code package.json}
     */
    public SidecarStackAdapter(
            String id,
            Set<SourceRole> roles,
            List<String> rootMarkers,
            List<String> detectFiles,
            SidecarRunner runner,
            JsonMapper json) {
        this.id = id;
        this.roles = Set.copyOf(roles);
        this.rootMarkers = List.copyOf(rootMarkers);
        this.detectFiles = List.copyOf(detectFiles);
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

    /** TODO (Giai đoạn 4): phân tích qua lệnh {@code analyze}; tới lúc đó chưa phân tích file nào của Source này. */
    @Override
    public boolean analyzes(String relativePath) {
        return false;
    }

    @Override
    public List<String> rootMarkers() {
        return rootMarkers;
    }

    @Override
    public Class<? extends FileFacts> factsType() {
        return FileFacts.class;
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

    @Override
    public FileFacts extract(ExtractContext ctx, SourceFile file) {
        throw new UnsupportedOperationException("TODO (Giai đoạn 4): phân tích qua sidecar");
    }

    @Override
    public IrFragment resolve(ResolveContext ctx, List<FileFacts> facts) {
        throw new UnsupportedOperationException("TODO (Giai đoạn 4): phân tích qua sidecar");
    }
}
