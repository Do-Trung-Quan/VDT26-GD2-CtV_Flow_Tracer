package com.ctv.flowtracer.app.graph.dto.response;

import java.util.List;

/**
 * Cây thành phần Frontend (API C2). Số liệu đếm trực tiếp từ IR đã lưu của lần quét gần nhất; mỗi thành phần chỉ đếm một
 * lần dù xuất hiện ở nhiều màn hình.
 *
 * @param appCount số ứng dụng
 * @param screenCount số màn hình
 * @param elementCount số phần tử tương tác
 * @param apps các ứng dụng, sắp theo tên
 */
public record FrontendTreeResponse(int appCount, int screenCount, int elementCount, List<UiTreeNodeResponse> apps) {

    /** Bản có tên chuẩn do AI đặt cho các phần tử (key phần tử → tên). */
    public FrontendTreeResponse withAiLabels(java.util.Map<String, String> names) {
        return new FrontendTreeResponse(appCount, screenCount, elementCount, apps.stream().map(a -> a.withAiLabels(names)).toList());
    }
}
