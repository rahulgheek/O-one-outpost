package com.aegis.assessment_service.repository;

import com.aegis.assessment_service.entitiy.CompanyProblem;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface CompanyProblemRepository extends MongoRepository<CompanyProblem,String> {
    List<CompanyProblem> findByCompanyId(String companyId);
}
