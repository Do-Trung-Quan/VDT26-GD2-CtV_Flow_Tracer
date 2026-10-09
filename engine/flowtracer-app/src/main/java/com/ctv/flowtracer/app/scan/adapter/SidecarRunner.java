package com.ctv.flowtracer.app.scan.adapter;

import com.ctv.flowtracer.app.common.error.AppException;
import com.ctv.flowtracer.app.common.error.ErrorCode;
import com.ctv.flowtracer.app.config.FlowTracerProperties;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Chạy CLI sidecar theo contracts/sidecar-protocol.md: request JSON qua stdin, kết quả qua stdout, log/tiến độ qua
 * stderr. Engine chỉ chạy đúng tiến trình con này (CLAUDE.md §3.4).
 *
 * <p>TODO (Giai đoạn 4): lệnh {@code analyze} — đọc tiến độ NDJSON từ stderr và kiểm tra IrFragment với schema.
 */
@Component
public class SidecarRunner {

    private static final Logger log = LoggerFactory.getLogger(SidecarRunner.class);
    private static final int STDERR_LOG_LIMIT = 2000;

    private final FlowTracerProperties.Sidecar config;

    public SidecarRunner(FlowTracerProperties properties) {
        this.config = properties.sidecar();
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
