package com.aegis.assessment_service.service;


import com.aegis.assessment_service.entitiy.CompanyProblem;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AssessmentCodeGeneratorService {

    public String generateExecutableCode(String userCode, CompanyProblem problem) {
        StringBuilder builder = new StringBuilder();

        // 1. Imports
        builder.append("import java.util.*;\n");
        builder.append("import java.io.*;\n\n");

        // 2. Inject User Solution
        builder.append(userCode).append("\n\n");

        // 3. Begin Main Driver
        builder.append("public class Main {\n");

        // --- UNIVERSAL FORMATTER ---
        builder.append("    public static String formatOutput(Object obj) {\n");
        builder.append("        if (obj == null) return \"null\";\n");
        builder.append("        if (obj instanceof int[]) return Arrays.toString((int[]) obj).replaceAll(\"\\\\s+\", \"\");\n");
        builder.append("        if (obj instanceof long[]) return Arrays.toString((long[]) obj).replaceAll(\"\\\\s+\", \"\");\n");
        builder.append("        if (obj instanceof double[]) return Arrays.toString((double[]) obj).replaceAll(\"\\\\s+\", \"\");\n");
        builder.append("        if (obj instanceof boolean[]) return Arrays.toString((boolean[]) obj).replaceAll(\"\\\\s+\", \"\");\n");
        builder.append("        if (obj instanceof String[]) return Arrays.toString((String[]) obj).replaceAll(\"\\\\s+\", \"\");\n");
        builder.append("        if (obj instanceof Object[]) return Arrays.deepToString((Object[]) obj).replaceAll(\"\\\\s+\", \"\");\n");
        builder.append("        return String.valueOf(obj).replaceAll(\"\\\\s+\", \"\");\n");
        builder.append("    }\n\n");

        // --- PURE-JAVA INPUT PARSER ---
        builder.append("    public static Object parseInput(String input, String type) {\n");
        builder.append("        input = input.trim();\n");
        builder.append("        if (type.equals(\"int\")) return Integer.parseInt(input);\n");
        builder.append("        if (type.equals(\"long\")) return Long.parseLong(input);\n");
        builder.append("        if (type.equals(\"double\")) return Double.parseDouble(input);\n");
        builder.append("        if (type.equals(\"boolean\")) return Boolean.parseBoolean(input);\n");
        builder.append("        if (type.equals(\"String\")) {\n");
        builder.append("            if (input.startsWith(\"\\\"\") && input.endsWith(\"\\\"\")) {\n");
        builder.append("                input = input.substring(1, input.length() - 1);\n");
        builder.append("            }\n");
        builder.append("            return input;\n");
        builder.append("        }\n");
        builder.append("        if (type.equals(\"int[]\")) {\n");
        builder.append("            String clean = input.replaceAll(\"\\\\[|\\\\]|\\\\s\", \"\");\n");
        builder.append("            if (clean.isEmpty()) return new int[0];\n");
        builder.append("            String[] parts = clean.split(\",\");\n");
        builder.append("            int[] arr = new int[parts.length];\n");
        builder.append("            for(int i=0; i<parts.length; i++) arr[i] = Integer.parseInt(parts[i]);\n");
        builder.append("            return arr;\n");
        builder.append("        }\n");
        builder.append("        return input;\n");
        builder.append("    }\n\n");

        // --- MAIN EXECUTION LOOP ---
        builder.append("    public static void main(String[] args) {\n");
        builder.append("        Solution solution = new Solution();\n");

        // NEW: Track the score!
        builder.append("        int passedCount = 0;\n");
        builder.append("        int totalCount = ").append(problem.getTestCases().size()).append(";\n\n");

        List<String> paramTypes = problem.getParameterTypes();
        List<CompanyProblem.TestCase> testCases = problem.getTestCases();

        for (int t = 0; t < testCases.size(); t++) {
            CompanyProblem.TestCase tc = testCases.get(t);
            List<String> inputs = tc.getInputs();

            builder.append("        // --- Test Case ").append(t + 1).append(" ---\n");

            // NEW: Isolate each test case so a crash doesn't ruin the whole assessment
            builder.append("        try {\n");

            // 4. GENERATE PARSED ARGUMENTS
            for (int i = 0; i < paramTypes.size(); i++) {
                String escapedInput = inputs.get(i).replace("\\", "\\\\").replace("\"", "\\\"");
                builder.append("            Object arg_").append(t).append("_").append(i)
                        .append(" = parseInput(\"").append(escapedInput).append("\", \"")
                        .append(paramTypes.get(i)).append("\");\n");
            }

            // 5. CALL METHOD
            builder.append("            ").append(problem.getReturnType()).append(" result_").append(t)
                    .append(" = solution.").append(problem.getMethodName()).append("(");

            for (int i = 0; i < paramTypes.size(); i++) {
                builder.append("(").append(paramTypes.get(i)).append(") arg_").append(t).append("_").append(i);
                if (i < paramTypes.size() - 1) builder.append(", ");
            }
            builder.append(");\n");

            // 6. VALIDATE RESULT
            String cleanExpected = tc.getExpectedOutput().replaceAll("\\s+", "");
            builder.append("            String actual_").append(t).append(" = formatOutput(result_").append(t).append(");\n");
            builder.append("            if (actual_").append(t).append(".equals(\"").append(cleanExpected).append("\")) {\n");
            builder.append("                passedCount++;\n"); // Partial credit awarded!
            builder.append("            } else {\n");
            builder.append("                System.out.println(\"WRONG_ANSWER|Test Case ").append(t + 1).append(" Failed.\");\n");
            builder.append("            }\n");

            builder.append("        } catch (Exception e) {\n");
            builder.append("            System.out.println(\"RUNTIME_ERROR|Test Case ").append(t + 1).append(" crashed: \" + e.getMessage());\n");
            builder.append("        }\n\n");
        }

        // 7. FINAL SCORE PAYLOAD
        // We output a clean string that our Spring Boot service can easily parse
        builder.append("        System.out.println(\"\\nASSESSMENT_RESULT|\" + passedCount + \"|\" + totalCount);\n");

        builder.append("    }\n");
        builder.append("}\n");

        return builder.toString();
    }
}