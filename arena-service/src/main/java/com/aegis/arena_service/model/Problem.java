package com.aegis.arena_service.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Document(collection = "problem")
public class Problem {
    @Id
    private String id;

    private String title;
    private String difficulty;


    private String description;

    private List<TestCase> testCases;

    private String startingCodeJava;

    /**
     * Optional per-language starter-code overrides, keyed by judge language id.
     * The API always returns a fully resolved map (see
     * {@code StarterCode.forProblem}); this field only stores explicit
     * recruiter/admin overrides so generated stubs never go stale.
     */
    private Map<String, String> startingCode;

    private int timeLimitMs;
    private int memoryLimitKb;

    private String methodName;
    private String returnType;
    private List<String> parameterTypes;

    @Data
    public static class TestCase{
        private List<String> inputs;
        private String expectedOutput;
        private boolean hidden;

    }
}
