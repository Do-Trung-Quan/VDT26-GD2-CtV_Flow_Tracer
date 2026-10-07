package com.ctv.flowtracer.app.scan.repository;

import com.ctv.flowtracer.app.scan.entity.ScanRun;
import com.ctv.flowtracer.app.scan.entity.ScanStatus;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScanRunRepository extends JpaRepository<ScanRun, UUID> {

    Page<ScanRun> findByWorkspaceIdOrderByStartedAtDesc(UUID workspaceId, Pageable pageable);

    boolean existsByWorkspaceIdAndStatusIn(UUID workspaceId, Collection<ScanStatus> statuses);
}
