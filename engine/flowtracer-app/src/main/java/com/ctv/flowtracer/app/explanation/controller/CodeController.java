package com.ctv.flowtracer.app.explanation.controller;

import com.ctv.flowtracer.app.common.web.ApiResponse;
import com.ctv.flowtracer.app.common.web.NodeKeyCodec;
import com.ctv.flowtracer.app.explanation.dto.response.CodeTabResponse;
import com.ctv.flowtracer.app.explanation.service.CodeSnippetService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API C5: đoạn mã nguồn theo từng lớp trong luồng của một thành phần, cho popup "Xem code". */
@RestController
@RequestMapping("/workspaces/{id}/nodes/{nodeKey}/source")
public class CodeController {

    private final CodeSnippetService codeSnippetService;

    public CodeController(CodeSnippetService codeSnippetService) {
        this.codeSnippetService = codeSnippetService;
    }

    /** C5 — chỉ đọc; không trả file nằm ngoài Source. */
    @GetMapping
    public ApiResponse<List<CodeTabResponse>> source(
            @PathVariable UUID id,
            @PathVariable String nodeKey,
            @RequestParam(name = "context_lines", defaultValue = "5")
                    @Min(value = 0, message = "Số dòng ngữ cảnh tối thiểu là 0")
                    @Max(value = 50, message = "Số dòng ngữ cảnh tối đa là 50")
                    int contextLines) {
        return ApiResponse.ok(codeSnippetService.snippets(id, NodeKeyCodec.decode(nodeKey), contextLines));
    }
}
