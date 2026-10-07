package com.ctv.flowtracer.app.workspace.repository;

import com.ctv.flowtracer.app.workspace.entity.Workspace;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceRepository extends JpaRepository<Workspace, UUID> {

    Optional<Workspace> findByName(String name);

    boolean existsByName(String name);
}
