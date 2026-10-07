package com.ctv.flowtracer.app.linking.repository;

import com.ctv.flowtracer.app.linking.entity.LinkOverride;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LinkOverrideRepository extends JpaRepository<LinkOverride, UUID> {

    List<LinkOverride> findByWorkspaceIdOrderByCreatedAt(UUID workspaceId);
}
