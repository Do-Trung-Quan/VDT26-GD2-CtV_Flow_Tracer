package com.ctv.flowtracer.app.scan.repository;

import com.ctv.flowtracer.app.scan.entity.ParseStatus;
import com.ctv.flowtracer.app.scan.entity.SourceFile;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SourceFileRepository extends JpaRepository<SourceFile, UUID> {

    List<SourceFile> findBySourceIdOrderByRelPath(UUID sourceId);

    Page<SourceFile> findByLastScanRunIdAndParseStatusIn(UUID scanRunId, Collection<ParseStatus> statuses, Pageable pageable);
}
