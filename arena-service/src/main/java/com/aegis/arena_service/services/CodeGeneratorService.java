package com.aegis.arena_service.services;

import com.aegis.arena_service.model.Problem;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class CodeGeneratorService {

    public String generateExecutableCode(String userCode, Problem problem) {
        StringBuilder builder = new StringBuilder();

        // 1. Imports
        builder.append("import java.util.*;\n");
        builder.append("import java.io.*;\n\n");

        // 2. Inject User Solution
        builder.append(userCode).append("\n\n");

        // 3. Begin Main Driver
        builder.append("public class Main {\n");

        // --- UNIVERSAL FORMATTER (Enhanced for deeply nested arrays and nulls) ---
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

        // --- PURE-JAVA INPUT PARSER (Enhanced for String escaping and more types) ---
        builder.append("    public static Object parseInput(String input, String type) {\n");
        builder.append("        input = input.trim();\n");
        builder.append("        if (type.equals(\"int\")) return Integer.parseInt(input);\n");
        builder.append("        if (type.equals(\"long\")) return Long.parseLong(input);\n");
        builder.append("        if (type.equals(\"double\")) return Double.parseDouble(input);\n");
        builder.append("        if (type.equals(\"boolean\")) return Boolean.parseBoolean(input);\n");
        builder.append("        if (type.equals(\"String\")) {\n");
        builder.append("            // Remove surrounding quotes if present from DB\n");
        builder.append("            if (input.startsWith(\"\\\"\") && input.endsWith(\"\\\"\")) {\n");
        builder.append("                input = input.substring(1, input.length() - 1);\n");
        builder.append("            }\n");
        builder.append("            return input;\n");
        builder.append("        }\n");
        builder.append("        if (type.equals(\"int[]\")) {\n");
        builder.append("            String clean = input.replaceAll(\"\\\\[|\\\\]\", \"\").trim();\n");
        builder.append("            if (clean.isEmpty()) return new int[0];\n");
        builder.append("            String[] parts = clean.split(\"[,\\\\s]+\");\n");
        builder.append("            int[] arr = new int[parts.length];\n");
        builder.append("            for(int i=0; i<parts.length; i++) arr[i] = Integer.parseInt(parts[i]);\n");
        builder.append("            return arr;\n");
        builder.append("        }\n");
        builder.append("        return input;\n");
        builder.append("    }\n\n");

        // --- MAIN EXECUTION LOOP (runs ALL tests, reports progress) ---
        builder.append("    public static void main(String[] args) {\n");
        builder.append("        Solution solution = new Solution();\n");

        List<String> paramTypes = problem.getParameterTypes();
        List<Problem.TestCase> testCases = problem.getTestCases();
        int totalTests = testCases.size();

        builder.append("        int passedTests = 0;\n");
        builder.append("        int totalTests = ").append(totalTests).append(";\n");
        builder.append("        String firstError = null;\n\n");

        for (int t = 0; t < totalTests; t++) {
            Problem.TestCase tc = testCases.get(t);
            List<String> inputs = tc.getInputs();

            builder.append("        // --- Test Case ").append(t + 1).append(" ---\n");
            builder.append("        try {\n");

            // GENERATE PARSED ARGUMENTS
            for (int i = 0; i < paramTypes.size(); i++) {
                // Escape quotes and backslashes for the Java string literal
                String escapedInput = inputs.get(i).replace("\\", "\\\\").replace("\"", "\\\"");

                builder.append("            Object arg_").append(t).append("_").append(i)
                        .append(" = parseInput(\"").append(escapedInput).append("\", \"")
                        .append(paramTypes.get(i)).append("\");\n");
            }

            // CALL METHOD WITH CORRECT CASTING
            builder.append("            ").append(problem.getReturnType()).append(" result_").append(t)
                    .append(" = solution.").append(problem.getMethodName()).append("(");

            for (int i = 0; i < paramTypes.size(); i++) {
                builder.append("(").append(paramTypes.get(i)).append(") arg_").append(t).append("_").append(i);
                if (i < paramTypes.size() - 1) builder.append(", ");
            }
            builder.append(");\n");

            // VALIDATE RESULT — count pass or record first error
            String cleanExpected = tc.getExpectedOutput().replaceAll("\\s+", "");
            builder.append("            String actual_").append(t).append(" = formatOutput(result_").append(t).append(");\n");
            builder.append("            if (actual_").append(t).append(".equals(\"").append(cleanExpected).append("\")) {\n");
            builder.append("                passedTests++;\n");
            builder.append("            } else if (firstError == null) {\n");
            builder.append("                firstError = \"WRONG_ANSWER|Test Case ").append(t + 1)
                    .append(" Failed. Expected: ").append(cleanExpected).append(", Got: \" + actual_").append(t).append(";\n");
            builder.append("            }\n");

            builder.append("        } catch (Exception e) {\n");
            builder.append("            if (firstError == null) {\n");
            builder.append("                firstError = \"RUNTIME_ERROR|Test Case ").append(t + 1)
                    .append(": \" + e.getClass().getSimpleName() + \": \" + e.getMessage();\n");
            builder.append("            }\n");
            builder.append("        }\n\n");
        }

        // PROGRESS OUTPUT + FINAL VERDICT
        builder.append("        System.out.println(\"PROGRESS|\" + passedTests + \"/\" + totalTests);\n");
        builder.append("        if (firstError != null) {\n");
        builder.append("            System.out.println(firstError);\n");
        builder.append("        } else {\n");
        builder.append("            System.out.println(\"ALL_TESTS_PASSED\");\n");
        builder.append("        }\n");

        builder.append("    }\n");
        builder.append("}\n");

        return builder.toString();
    }
}