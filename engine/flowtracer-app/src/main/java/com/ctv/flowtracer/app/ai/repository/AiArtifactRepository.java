package com.ctv.flowtracer.app.ai.repository;

import com.ctv.flowtracer.app.ai.entity.AiArtifact;
import com.ctv.flowtracer.app.ai.model.AiPurpose;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiArtifactRepository extends JpaRepository<AiArtifact, UUID> {

    Optional<AiArtifact> findByWorkspaceIdAndPurposeAndTargetKeyAndInputHash(
            UUID workspaceId, AiPurpose purpose, String targetKey, String inputHash);

    /** Bản mới nhất của một đối tượng, bất kể đầu vào đã đổi hay chưa. */
    Optional<AiArtifact> findFirstByWorkspaceIdAndPurposeAndTargetKeyOrderByCreatedAtDesc(
            UUID workspaceId, AiPurpose purpose, String targetKey);

    List<AiArtifact> findByWorkspaceIdAndPurposeOrderByCreatedAtAsc(UUID workspaceId, AiPurpose purpose);

    @org.springframework.transaction.annotation.Transactional
    void deleteByWorkspaceIdAndPurposeAndTargetKey(UUID workspaceId, AiPurpose purpose, String targetKey);
}
