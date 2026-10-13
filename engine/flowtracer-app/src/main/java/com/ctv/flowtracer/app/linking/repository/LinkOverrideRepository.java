package com.ctv.flowtracer.app.linking.repository;

import com.ctv.flowtracer.app.linking.entity.LinkOverride;
import com.ctv.flowtracer.ir.EdgeKind;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LinkOverrideRepository extends JpaRepository<LinkOverride, UUID> {

    List<LinkOverride> findByWorkspaceIdOrderByCreatedAt(UUID workspaceId);

    Optional<LinkOverride> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    List<LinkOverride> findByWorkspaceIdAndFromNodeKeyAndEdgeKind(UUID workspaceId, String fromNodeKey, EdgeKind edgeKind);
}
