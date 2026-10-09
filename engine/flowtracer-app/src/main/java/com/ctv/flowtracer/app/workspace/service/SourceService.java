package com.ctv.flowtracer.app.workspace.service;

import com.ctv.flowtracer.app.common.error.AppException;
import com.ctv.flowtracer.app.common.error.ErrorCode;
import com.ctv.flowtracer.app.scan.service.RunningScanRegistry;
import com.ctv.flowtracer.app.workspace.dto.request.CreateSourceRequest;
import com.ctv.flowtracer.app.workspace.dto.request.UpdateSourceRequest;
import com.ctv.flowtracer.app.workspace.dto.response.SourceResponse;
import com.ctv.flowtracer.app.workspace.entity.DetectedStack;
import com.ctv.flowtracer.app.workspace.entity.Source;
import com.ctv.flowtracer.app.workspace.entity.Workspace;
import com.ctv.flowtracer.app.workspace.repository.SourceRepository;
import com.ctv.flowtracer.app.workspace.repository.WorkspaceRepository;
import com.ctv.flowtracer.core.detection.FolderDetection;
import com.ctv.flowtracer.core.detection.FolderDetector;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Thêm / sửa / xóa thư mục mã nguồn (API A7–A9). Vai trò và công nghệ do tool tự nhận diện; thư mục có nhiều gốc dự
 * án được tách thành nhiều Source; Source lồng nhau theo {@link NestedSourceRules}.
 */
@Service
public class SourceService {

    private final WorkspaceRepository workspaces;
    private final SourceRepository sources;
    private final StackDetectionService detection;
    private final RunningScanRegistry runningScans;

    public SourceService(
            WorkspaceRepository workspaces,
            SourceRepository sources,
            StackDetectionService detection,
            RunningScanRegistry runningScans) {
        this.workspaces = workspaces;
        this.sources = sources;
        this.detection = detection;
        this.runningScans = runningScans;
    }

    @Transactional
    public List<SourceResponse> add(UUID workspaceId, CreateSourceRequest request) {
        Workspace ws = requireIdle(workspaceId);
        List<Source> created = addSources(ws, List.of(request));
        if (created.isEmpty()) {
            throw new AppException(
                    ErrorCode.SOURCE_ALREADY_EXISTS,
                    "Thư mục này đã có trong Workspace",
                    Map.of("rootPath", FolderDetector.normalize(request.rootPath())));
        }
        return created.stream().map(SourceResponse::from).toList();
    }

    @Transactional
    public SourceResponse update(UUID workspaceId, UUID sourceId, UpdateSourceRequest request) {
        requireIdle(workspaceId);
        List<Source> all = sources.findByWorkspaceIdOrderByName(workspaceId);
        Source source = find(all, sourceId);
        if (request.name() != null) {
            source.setName(request.name().strip());
        }
        if (request.excludeGlobs() != null) {
            var globs = new LinkedHashSet<String>();
            request.excludeGlobs().forEach(g -> globs.add(g.strip()));
            globs.addAll(NestedSourceRules.requiredExcludes(source, all));
            source.setExcludeGlobs(new ArrayList<>(globs));
        }
        return SourceResponse.from(source);
    }

    @Transactional
    public void delete(UUID workspaceId, UUID sourceId) {
        requireIdle(workspaceId);
        List<Source> all = sources.findByWorkspaceIdOrderByName(workspaceId);
        Source source = find(all, sourceId);
        NestedSourceRules.detach(source, all);
        var rest = new ArrayList<>(all);
        rest.remove(source);
        rest.forEach(s -> {
            if (s.getParent() == source) {
                s.setParent(null);
            }
        });
        sources.delete(source);
        sources.flush();
        NestedSourceRules.apply(rest);
    }

    /**
     * Nhận diện từng thư mục và tạo Source cho mọi gốc dự án tìm thấy; bỏ qua gốc đã có (cùng thư mục, cùng vai trò).
     *
     * @throws AppException {@link ErrorCode#NO_PROJECT_ROOT} khi một thư mục không phải gốc dự án
     */
    List<Source> addSources(Workspace ws, List<CreateSourceRequest> requests) {
        List<Source> all = new ArrayList<>(ws.getId() == null ? List.of() : sources.findByWorkspaceIdOrderByName(ws.getId()));
        var created = new ArrayList<Source>();
        for (CreateSourceRequest request : requests) {
            String rootPath = FolderDetector.normalize(request.rootPath());
            FolderDetection d = detection.detect(rootPath);
            if (d.status() != FolderDetection.Status.VALID) {
                throw noProjectRoot(rootPath, d);
            }
            boolean single = d.roots().size() == 1;
            for (FolderDetection.ProjectRoot root : d.roots()) {
                if (NestedSourceRules.isDuplicate(all, root.path(), root.role())) {
                    continue;
                }
                String name = single && request.name() != null && !request.name().isBlank()
                        ? request.name().strip()
                        : defaultName(root.path());
                var source = new Source(ws, name, root.role(), root.path());
                source.setDetectedStacks(List.of(
                        new DetectedStack(root.adapterId(), root.stackName(), root.version(), root.score())));
                if (root.path().equals(rootPath)) {
                    source.setExcludeGlobs(request.excludeGlobs().stream().map(String::strip).distinct().toList());
                }
                all.add(source);
                created.add(source);
            }
        }
        NestedSourceRules.apply(all);
        sources.saveAll(created);
        return created;
    }

    private Workspace requireIdle(UUID workspaceId) {
        Workspace ws = workspaces.findById(workspaceId).orElseThrow(() -> WorkspaceService.notFound(workspaceId));
        if (runningScans.isRunning(workspaceId)) {
            throw WorkspaceService.scanInProgress(workspaceId);
        }
        return ws;
    }

    private static Source find(List<Source> all, UUID sourceId) {
        return all.stream()
                .filter(s -> s.getId().equals(sourceId))
                .findFirst()
                .orElseThrow(() -> new AppException(
                        ErrorCode.SOURCE_NOT_FOUND, "Không tìm thấy nguồn mã", Map.of("sourceId", sourceId.toString())));
    }

    private static AppException noProjectRoot(String rootPath, FolderDetection d) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("rootPath", rootPath);
        details.put("status", d.status().name());
        String message = "Không tìm thấy dự án Spring Boot hoặc Angular trong thư mục " + rootPath;
        if (d.suggestedRoot() != null) {
            details.put("suggestedRoot", d.suggestedRoot().path());
            message = "Thư mục " + rootPath + " nằm bên trong dự án " + d.suggestedRoot().path() + ", hãy chọn thư mục đó";
        }
        return new AppException(ErrorCode.NO_PROJECT_ROOT, message, details);
    }

    static String defaultName(String rootPath) {
        if (rootPath.isEmpty()) {
            return "source-root";
        }
        return rootPath.substring(rootPath.lastIndexOf('/') + 1);
    }
}
