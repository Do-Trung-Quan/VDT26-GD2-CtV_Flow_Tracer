package com.ctv.flowtracer.app.workspace.service;

import com.ctv.flowtracer.app.common.error.AppException;
import com.ctv.flowtracer.app.common.error.ErrorCode;
import com.ctv.flowtracer.app.scan.adapter.AdapterRegistry;
import com.ctv.flowtracer.app.scan.service.RunningScanRegistry;
import com.ctv.flowtracer.app.workspace.dto.request.CreateWorkspaceRequest;
import com.ctv.flowtracer.app.workspace.dto.request.NoiseFilterItemRequest;
import com.ctv.flowtracer.app.workspace.dto.request.UpdateNoiseFiltersRequest;
import com.ctv.flowtracer.app.workspace.dto.request.UpdateWorkspaceRequest;
import com.ctv.flowtracer.app.workspace.dto.response.NoiseFilterResponse;
import com.ctv.flowtracer.app.workspace.dto.response.NoiseFiltersResponse;
import com.ctv.flowtracer.app.workspace.dto.response.SourceResponse;
import com.ctv.flowtracer.app.workspace.dto.response.WorkspaceResponse;
import com.ctv.flowtracer.app.workspace.entity.Workspace;
import com.ctv.flowtracer.app.workspace.entity.WorkspaceSettings;
import com.ctv.flowtracer.app.workspace.repository.SourceRepository;
import com.ctv.flowtracer.app.workspace.repository.WorkspaceRepository;
import com.ctv.flowtracer.ir.NoiseFilter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nghiệp vụ Workspace (API A1–A5, I4–I5): tên không trùng, không xóa khi đang quét. Service công khai cho các package
 * khác cần thông tin Workspace (Phụ lục B của project_structure.md).
 */
@Service
public class WorkspaceService {

    private final WorkspaceRepository workspaces;
    private final SourceRepository sources;
    private final SourceService sourceService;
    private final RunningScanRegistry runningScans;
    private final AdapterRegistry adapters;

    public WorkspaceService(
            WorkspaceRepository workspaces,
            SourceRepository sources,
            SourceService sourceService,
            RunningScanRegistry runningScans,
            AdapterRegistry adapters) {
        this.workspaces = workspaces;
        this.sources = sources;
        this.sourceService = sourceService;
        this.runningScans = runningScans;
        this.adapters = adapters;
    }

    /** Mới cập nhật lên đầu. */
    @Transactional(readOnly = true)
    public List<WorkspaceResponse> list() {
        return workspaces.findAll(Sort.by(Sort.Direction.DESC, "updatedAt")).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public WorkspaceResponse get(UUID id) {
        return toResponse(require(id));
    }

    @Transactional
    public WorkspaceResponse create(CreateWorkspaceRequest request) {
        String name = request.name().strip();
        requireNameFree(name);
        Workspace ws = workspaces.save(new Workspace(name, blankToNull(request.description())));
        sourceService.addSources(ws, request.sources());
        return toResponse(ws);
    }

    @Transactional
    public WorkspaceResponse update(UUID id, UpdateWorkspaceRequest request) {
        Workspace ws = require(id);
        if (request.name() != null) {
            String name = request.name().strip();
            if (!name.equals(ws.getName())) {
                requireNameFree(name);
                ws.setName(name);
            }
        }
        if (request.description() != null) {
            ws.setDescription(blankToNull(request.description()));
        }
        workspaces.flush();
        return toResponse(ws);
    }

    /**
     * Xóa Workspace cùng toàn bộ dữ liệu phân tích và dữ liệu người dùng (CSDL tự xóa theo khóa ngoại). Không bao giờ
     * đụng tới mã nguồn.
     *
     * <p>TODO (Giai đoạn 2): xóa cả cache dữ kiện trên ổ đĩa của Workspace.
     */
    @Transactional
    public void delete(UUID id) {
        Workspace ws = require(id);
        if (runningScans.isRunning(id)) {
            throw scanInProgress(id);
        }
        workspaces.delete(ws);
    }

    @Transactional(readOnly = true)
    public NoiseFiltersResponse noiseFilters(UUID id) {
        return toNoiseFiltersResponse(require(id));
    }

    @Transactional
    public NoiseFiltersResponse updateNoiseFilters(UUID id, UpdateNoiseFiltersRequest request) {
        Workspace ws = require(id);
        Map<String, NoiseFilter> manual = new LinkedHashMap<>();
        for (NoiseFilterItemRequest item : request.manual()) {
            String pattern = item.pattern().strip();
            manual.putIfAbsent(pattern, new NoiseFilter(pattern, blankToNull(item.description())));
        }
        WorkspaceSettings s = ws.getSettings();
        ws.setSettings(new WorkspaceSettings(
                s.manualLinkRules(), s.detectedLinkRules(), List.copyOf(manual.values()), s.defaultMaxDepth(), s.specDocGlobs()));
        workspaces.flush();
        return toNoiseFiltersResponse(ws);
    }

    private NoiseFiltersResponse toNoiseFiltersResponse(Workspace ws) {
        return new NoiseFiltersResponse(
                defaultNoiseFilters().stream().map(NoiseFilterResponse::from).toList(),
                ws.getSettings().noiseFilters().stream().map(NoiseFilterResponse::from).toList());
    }

    private List<NoiseFilter> defaultNoiseFilters() {
        return adapters.all().stream()
                .flatMap(a -> a.defaultNoiseFilters().stream())
                .distinct()
                .sorted(Comparator.comparing(NoiseFilter::pattern))
                .toList();
    }

    private WorkspaceResponse toResponse(Workspace ws) {
        List<SourceResponse> list = ws.getId() == null
                ? List.of()
                : sources.findByWorkspaceIdOrderByName(ws.getId()).stream()
                        .sorted(Comparator.comparing(s -> s.getRootPath() + "\u0000" + s.getRole()))
                        .map(SourceResponse::from)
                        .toList();
        return WorkspaceResponse.from(ws, list);
    }

    private Workspace require(UUID id) {
        return workspaces.findById(id).orElseThrow(() -> notFound(id));
    }

    private void requireNameFree(String name) {
        if (workspaces.existsByName(name)) {
            throw new AppException(ErrorCode.WORKSPACE_NAME_TAKEN, "Tên Workspace đã được dùng", Map.of("name", name));
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    static AppException notFound(UUID id) {
        return new AppException(ErrorCode.WORKSPACE_NOT_FOUND, "Không tìm thấy Workspace", Map.of("workspaceId", id.toString()));
    }

    static AppException scanInProgress(UUID id) {
        return new AppException(
                ErrorCode.SCAN_IN_PROGRESS, "Workspace đang được quét, hãy chờ quét xong hoặc hủy lần quét", Map.of("workspaceId", id.toString()));
    }
}
