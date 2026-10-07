package com.ctv.flowtracer.app.feature.repository;

import com.ctv.flowtracer.app.feature.entity.FeatureMember;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeatureMemberRepository extends JpaRepository<FeatureMember, FeatureMember.Id> {

    List<FeatureMember> findByFeatureIdOrderByOrderIndex(UUID featureId);
}
