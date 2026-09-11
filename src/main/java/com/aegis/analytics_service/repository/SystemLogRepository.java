package com.aegis.analytics_service.repository;

import com.aegis.analytics_service.model.SystemLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SystemLogRepository extends JpaRepository<SystemLog, Long> {
    List<SystemLog> findByServiceNameOrderByTimestampDesc(String serviceName);
    List<SystemLog> findTop100ByOrderByTimestampDesc();
}
