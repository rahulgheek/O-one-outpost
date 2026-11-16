package com.aegis.assessment_service.repository;

import com.aegis.assessment_service.entitiy.Assessment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AssessmentRepository extends MongoRepository<Assessment,String> {
    Optional<Assessment> findByTestCode(String testCode);

    boolean existsByTestCode(String testCode);

    Page<Assessment> findByTitleContainingIgnoreCase(String title, Pageable pageable);
    Page<Assessment> findByEndTimeAfterOrderByStartTimeAsc(LocalDateTime currentTime, Pageable pageable);
    Page<Assessment> findByTitleContainingIgnoreCaseAndEndTimeAfterOrderByStartTimeAsc(String title, LocalDateTime currentTime, Pageable pageable);

    List<Assessment> findByCompanyId(String companyId);
}
