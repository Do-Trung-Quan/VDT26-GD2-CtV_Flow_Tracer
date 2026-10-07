package com.ctv.flowtracer.app.workspace.repository;

import com.ctv.flowtracer.app.workspace.entity.Source;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SourceRepository extends JpaRepository<Source, UUID> {

    List<Source> findByWorkspaceIdOrderByName(UUID workspaceId);
}
