package com.aegis.arena_service.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

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
