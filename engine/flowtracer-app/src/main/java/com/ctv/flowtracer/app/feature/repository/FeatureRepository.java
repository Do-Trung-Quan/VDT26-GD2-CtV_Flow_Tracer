package com.ctv.flowtracer.app.feature.repository;

import com.ctv.flowtracer.app.feature.entity.Feature;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeatureRepository extends JpaRepository<Feature, UUID> {

    List<Feature> findByWorkspaceIdOrderByName(UUID workspaceId);
}
