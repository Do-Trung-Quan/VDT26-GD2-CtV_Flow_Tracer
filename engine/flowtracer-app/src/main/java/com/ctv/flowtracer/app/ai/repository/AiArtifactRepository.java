package com.ctv.flowtracer.app.ai.repository;

import com.ctv.flowtracer.app.ai.entity.AiArtifact;
import com.ctv.flowtracer.app.ai.entity.AiPurpose;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiArtifactRepository extends JpaRepository<AiArtifact, UUID> {

    Optional<AiArtifact> findByWorkspaceIdAndPurposeAndTargetKeyAndInputHash(
            UUID workspaceId, AiPurpose purpose, String targetKey, String inputHash);
}
