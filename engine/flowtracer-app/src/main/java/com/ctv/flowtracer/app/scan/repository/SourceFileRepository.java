package com.ctv.flowtracer.app.scan.repository;

import com.ctv.flowtracer.app.scan.entity.ParseStatus;
import com.ctv.flowtracer.app.scan.entity.SourceFile;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface SourceFileRepository extends JpaRepository<SourceFile, UUID> {

    List<SourceFile> findBySourceIdOrderByRelPath(UUID sourceId);

    Page<SourceFile> findByLastScanRunIdAndParseStatusIn(UUID scanRunId, Collection<ParseStatus> statuses, Pageable pageable);

    /** Đánh dấu mọi file của các Source thuộc lần quét này (quét lại không có thay đổi). */
    @Modifying
    @Transactional
    @Query("UPDATE SourceFile f SET f.lastScanRunId = :scanRunId WHERE f.sourceId IN :sourceIds")
    int markScanned(@Param("sourceIds") Collection<UUID> sourceIds, @Param("scanRunId") UUID scanRunId);
}
