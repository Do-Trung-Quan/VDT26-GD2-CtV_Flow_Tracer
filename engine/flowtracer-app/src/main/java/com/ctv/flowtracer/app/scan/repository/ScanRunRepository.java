package com.ctv.flowtracer.app.scan.repository;

import com.ctv.flowtracer.app.scan.entity.ScanRun;
import com.ctv.flowtracer.app.scan.entity.ScanStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScanRunRepository extends JpaRepository<ScanRun, UUID> {

    Page<ScanRun> findByWorkspaceIdOrderByStartedAtDesc(UUID workspaceId, Pageable pageable);

    boolean existsByWorkspaceIdAndStatusIn(UUID workspaceId, Collection<ScanStatus> statuses);

    List<ScanRun> findByStatusIn(Collection<ScanStatus> statuses);

    /** Lần quét đã xong gần nhất (khác lần quét đang chạy) — để mang kết quả phân tích sang khi không có thay đổi. */
    Optional<ScanRun> findFirstByWorkspaceIdAndStatusInAndIdNotOrderByStartedAtDesc(
            UUID workspaceId, Collection<ScanStatus> statuses, UUID excludedId);
}
