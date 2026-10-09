package com.ctv.flowtracer.app.scan.controller;

import com.ctv.flowtracer.app.common.web.ApiResponse;
import com.ctv.flowtracer.app.common.web.PageResponse;
import com.ctv.flowtracer.app.scan.dto.request.StartScanRequest;
import com.ctv.flowtracer.app.scan.dto.response.ScanFileErrorResponse;
import com.ctv.flowtracer.app.scan.dto.response.ScanRunResponse;
import com.ctv.flowtracer.app.scan.service.ScanService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** API B1, B3–B6: bắt đầu quét, xem trạng thái, hủy, lịch sử, danh sách file lỗi. */
@RestController
public class ScanController {

    private final ScanService scanService;

    public ScanController(ScanService scanService) {
        this.scanService = scanService;
    }

    /** B1 — trả ngay lần quét ở trạng thái PENDING; tiến độ theo dõi qua B2. */
    @PostMapping("/workspaces/{id}/scans")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<ScanRunResponse> start(@PathVariable UUID id, @Valid @RequestBody StartScanRequest request) {
        return ApiResponse.accepted(scanService.start(id, request), "Đã bắt đầu quét");
    }

    /** B3 */
    @GetMapping("/scans/{scanRunId}")
    public ApiResponse<ScanRunResponse> get(@PathVariable UUID scanRunId) {
        return ApiResponse.ok(scanService.get(scanRunId));
    }

    /** B4 — chờ lần quét dừng hẳn (tối đa vài giây) rồi trả trạng thái. */
    @PostMapping("/scans/{scanRunId}/cancel")
    public ApiResponse<ScanRunResponse> cancel(@PathVariable UUID scanRunId) {
        return ApiResponse.ok(scanService.cancel(scanRunId), "Đã gửi yêu cầu hủy");
    }

    /** B5 — mới nhất lên đầu. */
    @GetMapping("/workspaces/{id}/scans")
    public ApiResponse<PageResponse<ScanRunResponse>> history(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "Trang bắt đầu từ 1") int page,
            @RequestParam(defaultValue = "20")
                    @Min(value = 1, message = "Số dòng mỗi trang tối thiểu 1")
                    @Max(value = 100, message = "Số dòng mỗi trang tối đa 100")
                    int limit) {
        return ApiResponse.ok(scanService.history(id, page, limit));
    }

    /** B6 — file không phân tích được của lần quét. */
    @GetMapping("/scans/{scanRunId}/errors")
    public ApiResponse<PageResponse<ScanFileErrorResponse>> errors(
            @PathVariable UUID scanRunId,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "Trang bắt đầu từ 1") int page,
            @RequestParam(defaultValue = "50")
                    @Min(value = 1, message = "Số dòng mỗi trang tối thiểu 1")
                    @Max(value = 200, message = "Số dòng mỗi trang tối đa 200")
                    int limit) {
        return ApiResponse.ok(scanService.errors(scanRunId, page, limit));
    }
}
