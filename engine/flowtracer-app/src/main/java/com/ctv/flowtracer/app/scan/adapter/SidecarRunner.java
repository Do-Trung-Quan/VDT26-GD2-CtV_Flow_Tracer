package com.ctv.flowtracer.app.scan.adapter;

import com.ctv.flowtracer.app.common.error.AppException;
import com.ctv.flowtracer.app.common.error.ErrorCode;
import com.ctv.flowtracer.app.config.FlowTracerProperties;
import com.ctv.flowtracer.core.mock.SchemaValidator;
import com.networknt.schema.Schema;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Chạy CLI sidecar theo contracts/sidecar-protocol.md: request JSON qua stdin, kết quả qua stdout, log/tiến độ qua
 * stderr. Engine chỉ chạy đúng tiến trình con này (CLAUDE.md §3.4).
 *
 * <p>Kết quả lệnh {@code analyze} luôn được kiểm tra với {@code contracts/ir-fragment.schema.json} (đóng gói vào classpath
 * khi build) trước khi dùng. Tiến độ NDJSON trên stderr hiện chỉ được ghi log khi sidecar lỗi.
 */
@Component
public class SidecarRunner {

    private static final Logger log = LoggerFactory.getLogger(SidecarRunner.class);
    private static final int STDERR_LOG_LIMIT = 2000;

    private static final String FRAGMENT_SCHEMA = "/contracts/ir-fragment.schema.json";

    private final FlowTracerProperties properties;
    private final FlowTracerProperties.Sidecar config;
    private final SchemaValidator validator = new SchemaValidator();
    private volatile Schema fragmentSchema;

    public SidecarRunner(FlowTracerProperties properties) {
        this.properties = properties;
        this.config = properties.sidecar();
    }

    /** Thời gian tối đa cho một lần phân tích một Source ({@code flowtracer.sidecar.timeout}). */
    public Duration analyzeTimeout() {
        return config.timeout();
    }

    /**
     * Thư mục tuyệt đối của Source, luôn nằm trong {@code SOURCE_ROOT}.
     *
     * @param rootPath đường dẫn của Source tương đối so với SOURCE_ROOT (rỗng = chính SOURCE_ROOT)
     */
    public Path sourceDirectory(String rootPath) {
        Path base = Path.of(properties.sourceRoot()).toAbsolutePath().normalize();
        Path dir = base.resolve(rootPath == null ? "" : rootPath).normalize();
        if (!dir.startsWith(base)) {
            throw failed("Đường dẫn Source nằm ngoài thư mục mã nguồn");
        }
        return dir;
    }

    /**
     * Thư mục cache dữ kiện của sidecar: nằm trong cây cache của Workspace nên bị xóa cùng Workspace
     * ({@code FactsCache.evictWorkspace}); sidecar chỉ được ghi vào đây.
     */
    public Path cacheDirectory(UUID workspaceId, String adapterId, String version, String sourceId) {
        return Path.of(properties.dataDir())
                .toAbsolutePath()
                .normalize()
                .resolve("facts")
                .resolve(workspaceId.toString())
                .resolve("sidecar-" + adapterId + "-v" + version)
                .resolve(sourceId);
    }

    /** Lỗi theo schema của IrFragment; rỗng nghĩa là hợp lệ. */
    public List<String> validateFragment(String fragmentJson) {
        Schema schema = fragmentSchema;
        if (schema == null) {
            try (InputStream in = SidecarRunner.class.getResourceAsStream(FRAGMENT_SCHEMA)) {
                if (in == null) {
                    throw failed("Thiếu schema hợp đồng " + FRAGMENT_SCHEMA + " trong gói ứng dụng");
                }
                schema = validator.compile(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw failed("Không đọc được schema hợp đồng của sidecar");
            }
            fragmentSchema = schema;
        }
        return validator.validate(schema, fragmentJson);
    }

    /**
     * @param command {@code detect} hoặc {@code analyze}
     * @return nội dung stdout khi mã thoát là 0
     * @throws AppException {@link ErrorCode#SIDECAR_FAILED} khi chưa build sidecar, quá thời gian hoặc mã thoát khác 0
     */
    public String run(String command, String requestJson, Duration timeout) {
        Path cli = Path.of(config.cliPath()).toAbsolutePath().normalize();
        if (!cli.toFile().isFile()) {
            throw failed("Chưa build sidecar (không thấy " + cli + "). Chạy: npm --prefix analyzer-node run build");
        }
        var pb = new ProcessBuilder(List.of(
                config.nodeCommand(), "--max-old-space-size=" + config.maxOldSpaceMb(), cli.toString(), command));
        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            throw failed("Không chạy được Node.js ('" + config.nodeCommand() + "')");
        }
        CompletableFuture<String> stdout = CompletableFuture.supplyAsync(() -> readAll(process.getInputStream()));
        CompletableFuture<String> stderr = CompletableFuture.supplyAsync(() -> readAll(process.getErrorStream()));
        try (OutputStream in = process.getOutputStream()) {
            in.write(requestJson.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            process.destroyForcibly();
            throw failed("Không gửi được yêu cầu cho sidecar");
        }
        try {
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw failed("Sidecar chạy quá thời gian cho phép (" + timeout.toSeconds() + " giây)");
            }
            int exit = process.exitValue();
            if (exit != 0) {
                String err = stderr.join();
                log.warn("Sidecar '{}' thoát với mã {}: {}", command, exit, abbreviate(err));
                throw failed("Sidecar báo lỗi (mã " + exit + ")");
            }
            return stdout.join();
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw failed("Lời gọi sidecar bị ngắt");
        }
    }

    private static String readAll(InputStream stream) {
        try (stream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String abbreviate(String s) {
        return s.length() <= STDERR_LOG_LIMIT ? s : s.substring(0, STDERR_LOG_LIMIT) + "…";
    }

    private static AppException failed(String message) {
        return new AppException(ErrorCode.SIDECAR_FAILED, message);
    }
}
