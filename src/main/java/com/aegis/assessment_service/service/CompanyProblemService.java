package com.aegis.assessment_service.service;

import com.aegis.assessment_service.entitiy.CompanyProblem;
import com.aegis.assessment_service.repository.CompanyProblemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CompanyProblemService {

    private final CompanyProblemRepository problemRepository;

    public CompanyProblem createPrivateProblem(String companyId, CompanyProblem problem) {
        // Enforce ownership
        problem.setCompanyId(companyId);
        return problemRepository.save(problem);
    }

    public List<CompanyProblem> getProblemsByCompany(String companyId) {
        return problemRepository.findByCompanyId(companyId);
    }

    public CompanyProblem getProblemById(String problemId) {
        return problemRepository.findById(problemId)
                .orElseThrow(() -> new RuntimeException("Problem not found"));
    }
}