package com.ctv.flowtracer.app.workspace.service;

import com.ctv.flowtracer.app.common.error.AppException;
import com.ctv.flowtracer.app.common.error.ErrorCode;
import com.ctv.flowtracer.app.scan.adapter.AdapterRegistry;
import com.ctv.flowtracer.app.scan.dto.response.LastScanResponse;
import com.ctv.flowtracer.app.scan.service.FactsCache;
import com.ctv.flowtracer.app.scan.service.RunningScanRegistry;
import com.ctv.flowtracer.app.scan.service.ScanQueryService;
import com.ctv.flowtracer.app.workspace.dto.request.CreateWorkspaceRequest;
import com.ctv.flowtracer.app.workspace.dto.request.NoiseFilterItemRequest;
import com.ctv.flowtracer.app.workspace.dto.request.UpdateNoiseFiltersRequest;
import com.ctv.flowtracer.app.workspace.dto.request.UpdateWorkspaceRequest;
import com.ctv.flowtracer.app.workspace.dto.response.NoiseFilterResponse;
import com.ctv.flowtracer.app.workspace.dto.response.NoiseFiltersResponse;
import com.ctv.flowtracer.app.workspace.dto.response.SourceResponse;
import com.ctv.flowtracer.app.workspace.dto.response.WorkspaceResponse;
import com.ctv.flowtracer.app.workspace.entity.Source;
import com.ctv.flowtracer.app.workspace.entity.Workspace;
import com.ctv.flowtracer.app.workspace.entity.WorkspaceSettings;
import com.ctv.flowtracer.app.workspace.model.LinkRuleSettings;
import com.ctv.flowtracer.app.workspace.model.SourceScanTarget;
import com.ctv.flowtracer.app.workspace.model.TraceSettings;
import com.ctv.flowtracer.app.workspace.repository.SourceRepository;
import com.ctv.flowtracer.app.workspace.repository.WorkspaceRepository;
import com.ctv.flowtracer.ir.LinkRule;
import com.ctv.flowtracer.ir.NoiseFilter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    /** Thứ tự ổn định của Source: theo thư mục, cùng thư mục thì theo vai trò. */
    private static final Comparator<Source> SOURCE_ORDER =
            Comparator.comparing(Source::getRootPath).thenComparing(Source::getRole);

    private final WorkspaceRepository workspaces;
    private final SourceRepository sources;
    private final SourceService sourceService;
    private final RunningScanRegistry runningScans;
    private final AdapterRegistry adapters;
    private final ScanQueryService scanQueries;
    private final FactsCache factsCache;

    public WorkspaceService(
            WorkspaceRepository workspaces,
            SourceRepository sources,
            SourceService sourceService,
            RunningScanRegistry runningScans,
            AdapterRegistry adapters,
            ScanQueryService scanQueries,
            FactsCache factsCache) {
        this.workspaces = workspaces;
        this.sources = sources;
        this.sourceService = sourceService;
        this.runningScans = runningScans;
        this.adapters = adapters;
        this.scanQueries = scanQueries;
        this.factsCache = factsCache;
    }

    /** Mới cập nhật lên đầu. */
    @Transactional(readOnly = true)
    public List<WorkspaceResponse> list() {
        List<Workspace> all = workspaces.findAll(Sort.by(Sort.Direction.DESC, "updatedAt"));
        Map<UUID, LastScanResponse> lastScans = scanQueries.summaries(all.stream()
                .map(Workspace::getLastScanRunId)
                .filter(Objects::nonNull)
                .toList());
        return all.stream().map(ws -> toResponse(ws, lastScans.get(ws.getLastScanRunId()))).toList();
    }

    /**
     * Các Source cần quét của Workspace, theo đường dẫn — cho package {@code scan}.
     *
     * @throws AppException {@link ErrorCode#WORKSPACE_NOT_FOUND}
     */
    @Transactional(readOnly = true)
    public List<SourceScanTarget> scanTargets(UUID id) {
        require(id);
        return sources.findByWorkspaceIdOrderByName(id).stream()
                .sorted(SOURCE_ORDER)
                .map(s -> new SourceScanTarget(
                        s.getId(),
                        s.getName(),
                        s.getRole(),
                        s.getRootPath(),
                        s.getDetectedStacks().isEmpty() ? null : s.getDetectedStacks().getFirst().adapter(),
                        s.getIncludeGlobs(),
                        s.getExcludeGlobs()))
                .toList();
    }

    /**
     * Kiểm tra Workspace tồn tại — cho package khác.
     *
     * @throws AppException {@link ErrorCode#WORKSPACE_NOT_FOUND}
     */
    @Transactional(readOnly = true)
    public void requireExists(UUID id) {
        require(id);
    }

    /**
     * Thiết lập dò vết của Workspace — cho package {@code tracing}.
     *
     * @throws AppException {@link ErrorCode#WORKSPACE_NOT_FOUND}
     */
    @Transactional(readOnly = true)
    public TraceSettings traceSettings(UUID id) {
        Workspace ws = require(id);
        List<NoiseFilter> filters = new ArrayList<>(defaultNoiseFilters());
        ws.getSettings().noiseFilters().stream().filter(f -> !filters.contains(f)).forEach(filters::add);
        filters.sort(Comparator.comparing(NoiseFilter::pattern));
        return new TraceSettings(List.copyOf(filters), ws.getSettings().defaultMaxDepth(), ws.getLastScanRunId());
    }

    /** Ghi nhận lần quét gần nhất của Workspace — package {@code scan} gọi khi bắt đầu quét. */
    @Transactional
    public void recordLastScan(UUID id, UUID scanRunId) {
        require(id).setLastScanRunId(scanRunId);
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
     */
    @Transactional
    public void delete(UUID id) {
        Workspace ws = require(id);
        if (runningScans.isRunning(id)) {
            throw scanInProgress(id);
        }
        workspaces.delete(ws);
        factsCache.evictWorkspace(id);
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

    /**
     * Luật liên kết của Workspace — cho package {@code linking}.
     *
     * @throws AppException {@link ErrorCode#WORKSPACE_NOT_FOUND}
     */
    @Transactional(readOnly = true)
    public LinkRuleSettings linkRules(UUID id) {
        WorkspaceSettings s = require(id).getSettings();
        return new LinkRuleSettings(s.manualLinkRules(), s.detectedLinkRules());
    }

    /** Thay toàn bộ luật liên kết thủ công — package {@code linking} gọi sau khi đã kiểm tra luật. */
    @Transactional
    public LinkRuleSettings updateManualLinkRules(UUID id, List<LinkRule> manual) {
        Workspace ws = require(id);
        WorkspaceSettings s = ws.getSettings();
        ws.setSettings(new WorkspaceSettings(manual, s.detectedLinkRules(), s.noiseFilters(), s.defaultMaxDepth(), s.specDocGlobs()));
        workspaces.flush();
        return new LinkRuleSettings(manual, s.detectedLinkRules());
    }

    /** Workspace đang có lần quét chạy không — cho package {@code linking} (sửa luật khi đang quét thì để lần quét tự nối). */
    public boolean scanRunning(UUID id) {
        return runningScans.isRunning(id);
    }

    /** Ghi luật tự phát hiện của lần quét vừa xong — package {@code linking} gọi ở bước LINK. */
    @Transactional
    public void updateDetectedLinkRules(UUID id, List<LinkRule> detected) {
        Workspace ws = require(id);
        WorkspaceSettings s = ws.getSettings();
        if (s.detectedLinkRules().equals(detected)) {
            return;
        }
        ws.setSettings(new WorkspaceSettings(s.manualLinkRules(), detected, s.noiseFilters(), s.defaultMaxDepth(), s.specDocGlobs()));
        workspaces.flush();
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
        LastScanResponse lastScan = ws.getLastScanRunId() == null
                ? null
                : scanQueries.summaries(List.of(ws.getLastScanRunId())).get(ws.getLastScanRunId());
        return toResponse(ws, lastScan);
    }

    private WorkspaceResponse toResponse(Workspace ws, LastScanResponse lastScan) {
        List<SourceResponse> list = ws.getId() == null
                ? List.of()
                : sources.findByWorkspaceIdOrderByName(ws.getId()).stream()
                        .sorted(SOURCE_ORDER)
                        .map(SourceResponse::from)
                        .toList();
        return WorkspaceResponse.from(ws, list, lastScan);
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
