package com.aegis.assessment_service.entitiy;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "company_problems") // Totally separate from the public Arena!
public class CompanyProblem {
    @Id
    private String id;

    private String companyId; // Locks this problem so ONLY this recruiter can use it

    private String title;
    private String difficulty;
    private String description;

    private List<TestCase> testCases;
    private String startingCodeJava;

    /**
     * Optional per-language starter-code overrides, keyed by judge language id.
     * Candidate-facing responses always carry a fully resolved map.
     */
    private Map<String, String> startingCode;

    private int timeLimitMs;
    private int memoryLimitKb;

    private String methodName;
    private String returnType;
    private List<String> parameterTypes;

    @Data
    public static class TestCase {
        private List<String> inputs;
        private String expectedOutput;
        private boolean hidden;
    }
}