package com.ctv.flowtracer.app.graph.controller;

import com.ctv.flowtracer.app.common.web.ApiResponse;
import com.ctv.flowtracer.app.common.web.NodeKeyCodec;
import com.ctv.flowtracer.app.graph.dto.response.NodeDetailResponse;
import com.ctv.flowtracer.app.graph.service.NodeDetailService;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API C4: chi tiết thành phần, kèm "Được gọi từ" / "Gọi tới". {@code nodeKey} mã hóa bằng {@link NodeKeyCodec} rồi mã hóa URL thêm một lần. */
@RestController
@RequestMapping("/workspaces/{id}/nodes/{nodeKey}")
public class NodeController {

    private final NodeDetailService nodeDetailService;

    public NodeController(NodeDetailService nodeDetailService) {
        this.nodeDetailService = nodeDetailService;
    }

    /** C4 */
    @GetMapping
    public ApiResponse<NodeDetailResponse> detail(@PathVariable UUID id, @PathVariable String nodeKey) {
        return ApiResponse.ok(nodeDetailService.detail(id, NodeKeyCodec.decode(nodeKey)));
    }
}
