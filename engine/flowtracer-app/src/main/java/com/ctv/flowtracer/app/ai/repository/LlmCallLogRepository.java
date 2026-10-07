package com.ctv.flowtracer.app.ai.repository;

import com.ctv.flowtracer.app.ai.entity.LlmCallLog;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LlmCallLogRepository extends JpaRepository<LlmCallLog, UUID> {

    List<LlmCallLog> findByCreatedAtBetweenOrderByCreatedAt(Instant from, Instant to);
}
